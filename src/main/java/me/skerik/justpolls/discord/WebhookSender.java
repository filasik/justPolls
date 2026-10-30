package me.skerik.justpolls.discord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Posts a JSON payload to a Discord webhook. Blocking: call it from an async task. Honours Discord's rate limit
 * (HTTP 429 with {@code retry_after}) and never logs the URL (it contains the secret token).
 */
public final class WebhookSender {

    private static final Pattern URL = Pattern.compile(
            "https://(?:(?:canary|ptb)\\.)?(?:discord|discordapp)\\.com/api(?:/v\\d+)?/webhooks/\\d+/[\\w-]+");
    private static final int MAX_ATTEMPTS = 4;
    private static final long MAX_WAIT_MS = 30_000;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final Logger log;

    public WebhookSender(Logger log) {
        this.log = log;
    }

    /** Only real Discord webhook URLs are accepted, so a typo (or a hostile config) cannot make the server call elsewhere. */
    public static boolean isValidUrl(String url) {
        return URL.matcher(url).matches();
    }

    /** @return true when Discord accepted the message */
    public boolean send(String url, JsonObject payload) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("User-Agent", "JustPolls (https://github.com/filasik/justPolls)")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status >= 200 && status < 300) return true;
                if (status == 429 && attempt < MAX_ATTEMPTS) {
                    Thread.sleep(retryAfterMs(response.body()));
                    continue;
                }
                if (status >= 500 && attempt < MAX_ATTEMPTS) {
                    Thread.sleep(2_000L * attempt);
                    continue;
                }
                log.warning("Discord webhook answered HTTP " + status + ": " + abbreviate(response.body()));
                return false;
            } catch (IOException e) {
                if (attempt == MAX_ATTEMPTS) {
                    log.warning("Cannot reach the Discord webhook: " + e.getMessage());
                    return false;
                }
                try {
                    Thread.sleep(2_000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    static long retryAfterMs(String body) {
        try {
            double seconds = JsonParser.parseString(body).getAsJsonObject().get("retry_after").getAsDouble();
            return Math.min(MAX_WAIT_MS, Math.max(500, (long) (seconds * 1000) + 100));
        } catch (RuntimeException e) {
            return 2_000;
        }
    }

    private static String abbreviate(String s) {
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }
}
