package me.skerik.justpolls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import me.skerik.justpolls.config.Settings;
import me.skerik.justpolls.discord.WebhookMessages;
import me.skerik.justpolls.discord.WebhookSender;
import me.skerik.justpolls.model.Poll;
import me.skerik.justpolls.model.PollResult;
import me.skerik.justpolls.model.PollStatus;
import org.junit.jupiter.api.Test;

class WebhookTest {

    private static final Settings.Discord CFG = new Settings.Discord(true, "https://discord.com/api/webhooks/1/x", "JustPolls",
            "", "123456789012345678", false, 0x5865F2, 20, true, "My Server");

    private static String text(String key, String... kv) {
        String t = switch (key) {
            case "webhook.votes-one" -> "{n} vote";
            case "webhook.votes-few", "webhook.votes-many" -> "{n} votes";
            case "webhook.tie" -> "Tie: {options}";
            case "webhook.poll-id" -> "Poll #{id}";
            case "webhook.started" -> "New poll: {title}";
            default -> key;
        };
        for (int i = 0; i + 1 < kv.length; i += 2) t = t.replace("{" + kv[i] + "}", kv[i + 1]);
        return t;
    }

    private static PollResult result(String title, String description, List<String> options, int... counts) {
        Poll p = new Poll(12, title, description, options, PollStatus.ENDED, 1_700_000_000_000L, 1_700_003_600_000L,
                1_700_000_000_000L, "tester", true, true, -1, "", null, 0, true);
        return new PollResult(p, counts);
    }

    private static WebhookMessages messages() {
        return new WebhookMessages(CFG, WebhookTest::text, Locale.ENGLISH);
    }

    @Test
    void acceptsOnlyRealDiscordWebhookUrls() {
        assertTrue(WebhookSender.isValidUrl("https://discord.com/api/webhooks/123456789/abc-DEF_123"));
        assertTrue(WebhookSender.isValidUrl("https://discordapp.com/api/v10/webhooks/123456789/abc"));
        assertTrue(WebhookSender.isValidUrl("https://ptb.discord.com/api/webhooks/1/a"));
        for (String bad : new String[]{"", "http://discord.com/api/webhooks/1/a", "https://evil.example/api/webhooks/1/a",
                "https://discord.com.evil.example/api/webhooks/1/a", "https://discord.com/api/webhooks/abc/a",
                "https://discord.com/api/webhooks/1/a?x=1 http://evil", "file:///etc/passwd", "https://127.0.0.1:8080/api/webhooks/1/a"}) {
            assertFalse(WebhookSender.isValidUrl(bad), bad);
        }
    }

    @Test
    void resultsEmbedShowsPercentagesTotalsAndWinnerButNoPlayers() {
        JsonObject msg = messages().results(result("Best colour?", "Pick one.\nSecond line", List.of("Red", "Green", "Blue"), 6, 3, 1));
        JsonObject embed = msg.getAsJsonArray("embeds").get(0).getAsJsonObject();
        assertEquals("📊 Best colour?", embed.get("title").getAsString());
        assertEquals("> Pick one.\n> Second line", embed.get("description").getAsString());
        JsonArray fields = embed.getAsJsonArray("fields");
        assertEquals(6, fields.size());
        assertEquals("🏆 Red", fields.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("`████████████░░░░░░░░` **60.0 %** · 6 votes", fields.get(0).getAsJsonObject().get("value").getAsString());
        assertEquals("▫️ Green", fields.get(1).getAsJsonObject().get("name").getAsString());
        assertEquals("`██████░░░░░░░░░░░░░░` **30.0 %** · 3 votes", fields.get(1).getAsJsonObject().get("value").getAsString());
        assertEquals("`██░░░░░░░░░░░░░░░░░░` **10.0 %** · 1 vote", fields.get(2).getAsJsonObject().get("value").getAsString());
        assertEquals("10 votes", fields.get(3).getAsJsonObject().get("value").getAsString());
        assertEquals("**Red**", fields.get(4).getAsJsonObject().get("value").getAsString());
        assertEquals("1h", fields.get(5).getAsJsonObject().get("value").getAsString());
        assertEquals("JustPolls · Poll #12 · My Server", embed.getAsJsonObject("footer").get("text").getAsString());
        assertEquals("2023-11-14T23:13:20Z", embed.get("timestamp").getAsString().replaceAll("\\.\\d+Z", "Z"));
        assertEquals("<@&123456789012345678>", msg.get("content").getAsString());
        assertEquals(0, msg.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").size(), "poll texts can never ping @everyone");
    }

    @Test
    void tieAndNoVotes() {
        JsonObject tie = messages().results(result("T", "", List.of("A", "B"), 2, 2));
        JsonArray f = tie.getAsJsonArray("embeds").get(0).getAsJsonObject().getAsJsonArray("fields");
        assertEquals("Tie: A, B", f.get(3).getAsJsonObject().get("value").getAsString());
        assertEquals("🏆 A", f.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("🏆 B", f.get(1).getAsJsonObject().get("name").getAsString());

        JsonObject none = messages().results(result("N", "", List.of("A", "B"), 0, 0));
        JsonObject embed = none.getAsJsonArray("embeds").get(0).getAsJsonObject();
        assertEquals("", embed.get("description").getAsString());
        JsonArray nf = embed.getAsJsonArray("fields");
        assertEquals("`░░░░░░░░░░░░░░░░░░░░` **0.0 %** · 0 votes", nf.get(0).getAsJsonObject().get("value").getAsString());
        assertEquals("webhook.no-votes", nf.get(3).getAsJsonObject().get("value").getAsString());
        assertEquals("▫️ A", nf.get(0).getAsJsonObject().get("name").getAsString());
    }

    @Test
    void respectsDiscordLimitsEvenForHugePollTexts() {
        String longText = "x".repeat(2000);
        List<String> options = new ArrayList<>();
        int[] counts = new int[10];
        for (int i = 0; i < 10; i++) {
            options.add(longText);
            counts[i] = i;
        }
        JsonObject embed = messages().results(result(longText, longText, options, counts))
                .getAsJsonArray("embeds").get(0).getAsJsonObject();
        assertTrue(embed.get("title").getAsString().length() <= 256);
        assertTrue(embed.get("description").getAsString().length() <= 4096);
        for (var f : embed.getAsJsonArray("fields")) {
            JsonObject field = f.getAsJsonObject();
            assertTrue(field.get("name").getAsString().length() <= 256);
            assertTrue(field.get("value").getAsString().length() <= 1024);
            assertFalse(field.get("name").getAsString().isEmpty());
            assertFalse(field.get("value").getAsString().isEmpty());
        }
        assertTrue(embed.getAsJsonArray("fields").size() <= 25);
    }

    @Test
    void largestAllowedPollStaysUnderTheSixThousandCharacterEmbedLimit() {
        List<String> options = new ArrayList<>();
        int[] counts = new int[Settings.HARD_MAX_OPTIONS];
        for (int i = 0; i < counts.length; i++) {
            options.add("o".repeat(Settings.OPTION_MAX));
            counts[i] = 1000 + i;
        }
        JsonObject embed = messages().results(result("t".repeat(Settings.TITLE_MAX), "d".repeat(Settings.DESCRIPTION_MAX), options, counts))
                .getAsJsonArray("embeds").get(0).getAsJsonObject();
        int total = embed.get("title").getAsString().length() + embed.get("description").getAsString().length()
                + embed.getAsJsonObject("footer").get("text").getAsString().length();
        for (var f : embed.getAsJsonArray("fields")) {
            total += f.getAsJsonObject().get("name").getAsString().length() + f.getAsJsonObject().get("value").getAsString().length();
        }
        assertTrue(total <= 6000, "embed total " + total);
    }

    @Test
    void startedEmbedUsesDiscordTimestamps() {
        Poll p = result("Q", "d", List.of("A", "B"), 0, 0).poll();
        JsonObject embed = messages().started(p).getAsJsonArray("embeds").get(0).getAsJsonObject();
        assertEquals("🗳️ New poll: Q", embed.get("title").getAsString());
        String ends = embed.getAsJsonArray("fields").get(1).getAsJsonObject().get("value").getAsString();
        assertEquals("<t:1700003600:R> (<t:1700003600:f>)", ends);
    }

    @Test
    void senderPostsJsonAndRetriesAfterRateLimit() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        List<String> bodies = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", ex -> {
            bodies.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (calls.incrementAndGet() == 1) {
                byte[] body = "{\"message\":\"rate limited\",\"retry_after\":0.6,\"global\":false}".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(429, body.length);
                ex.getResponseBody().write(body);
            } else {
                ex.sendResponseHeaders(204, -1);
            }
            ex.close();
        });
        server.start();
        try {
            JsonObject payload = messages().test();
            long start = System.nanoTime();
            assertTrue(new WebhookSender(Logger.getLogger("test")).send("http://127.0.0.1:" + server.getAddress().getPort() + "/hook", payload));
            assertEquals(2, calls.get());
            assertTrue((System.nanoTime() - start) / 1_000_000 >= 600, "waited for retry_after");
            assertEquals(payload, JsonParser.parseString(bodies.getLast()));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void senderReportsFailureOnClientErrors() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", ex -> {
            byte[] body = "{\"message\":\"Unknown Webhook\"}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(404, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
        try {
            assertFalse(new WebhookSender(Logger.getLogger("test")).send("http://127.0.0.1:" + server.getAddress().getPort() + "/hook", messages().test()));
        } finally {
            server.stop(0);
        }
    }
}
