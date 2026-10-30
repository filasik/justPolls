package me.skerik.justpolls.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.skerik.justpolls.config.Settings;
import me.skerik.justpolls.model.Poll;
import me.skerik.justpolls.model.PollResult;
import me.skerik.justpolls.util.Durations;

/**
 * Builds the Discord embeds. Pure functions (no network, no Bukkit) so the layout is unit-tested against Discord's
 * limits: title 256, description 4096, 25 fields (name 256, value 1024), footer 2048, 6000 characters in total.
 * The results message holds totals and percentages only, never who voted.
 */
public final class WebhookMessages {

    /** Text lookup: key plus alternating placeholder names and values ({@code "n", "5"}). */
    @FunctionalInterface
    public interface Texts {
        String get(String key, String... placeholders);
    }

    private static final int TITLE = 256;
    private static final int FIELD_NAME = 256;
    private static final int FIELD_VALUE = 1024;
    private static final int DESCRIPTION = 4096;
    private static final int FOOTER = 2048;

    private final Settings.Discord cfg;
    private final Texts texts;
    private final NumberFormat percent;

    public WebhookMessages(Settings.Discord cfg, Texts texts, Locale locale) {
        this.cfg = cfg;
        this.texts = texts;
        this.percent = NumberFormat.getNumberInstance(locale);
        this.percent.setMinimumFractionDigits(1);
        this.percent.setMaximumFractionDigits(1);
    }

    /** The results of a finished poll. */
    public JsonObject results(PollResult r) {
        Poll p = r.poll();
        JsonObject embed = new JsonObject();
        embed.addProperty("title", cut("📊 " + p.title, TITLE));
        embed.addProperty("description", cut(quote(p.description), DESCRIPTION));
        embed.addProperty("color", cfg.color());

        JsonArray fields = new JsonArray();
        List<Integer> winners = r.winners();
        for (int i = 0; i < p.options.size(); i++) {
            String name = (winners.contains(i) ? "🏆 " : "▫️ ") + p.options.get(i);
            String value = "`" + bar(r.percent(i)) + "` **" + percent.format(r.percent(i)) + " %** · " + votes(r.counts()[i]);
            fields.add(field(cut(name, FIELD_NAME), cut(value, FIELD_VALUE), false));
        }
        fields.add(field(texts.get("webhook.total"), votes(r.total()), true));
        fields.add(field(texts.get("webhook.winner"), winnerText(r), true));
        fields.add(field(texts.get("webhook.duration"), Durations.format(p.endAt() - p.startAt()), true));
        embed.add("fields", fields);

        embed.add("footer", footer(p));
        embed.addProperty("timestamp", Instant.ofEpochMilli(p.endAt()).toString());
        return message(embed, true);
    }

    /** "A new poll has started" with the options and an auto-updating end time. */
    public JsonObject started(Poll p) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", cut("🗳️ " + texts.get("webhook.started", "title", p.title), TITLE));
        embed.addProperty("description", cut(quote(p.description), DESCRIPTION));
        embed.addProperty("color", cfg.color());
        StringBuilder options = new StringBuilder();
        for (String option : p.options) options.append("▫️ ").append(option).append('\n');
        JsonArray fields = new JsonArray();
        fields.add(field(texts.get("webhook.options"), cut(options.toString().strip(), FIELD_VALUE), false));
        long end = p.endAt() / 1000;
        fields.add(field(texts.get("webhook.ends"), "<t:" + end + ":R> (<t:" + end + ":f>)", false));
        embed.add("fields", fields);
        embed.add("footer", footer(p));
        embed.addProperty("timestamp", Instant.ofEpochMilli(p.startAt()).toString());
        return message(embed, false);
    }

    /** What /justpolls webhook-test sends. */
    public JsonObject test() {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "✅ JustPolls");
        embed.addProperty("description", texts.get("webhook.test"));
        embed.addProperty("color", cfg.color());
        return message(embed, false);
    }

    private JsonObject message(JsonObject embed, boolean mention) {
        JsonObject body = new JsonObject();
        if (!cfg.username().isEmpty()) body.addProperty("username", cut(cfg.username(), 80));
        if (!cfg.avatarUrl().isEmpty()) body.addProperty("avatar_url", cfg.avatarUrl());
        JsonObject allowed = new JsonObject();
        JsonArray parse = new JsonArray();
        if (mention && !cfg.roleId().isEmpty()) {
            body.addProperty("content", "<@&" + cfg.roleId() + ">");
            JsonArray roles = new JsonArray();
            roles.add(cfg.roleId());
            allowed.add("roles", roles);
        }
        allowed.add("parse", parse); // never @everyone or user mentions out of poll texts
        body.add("allowed_mentions", allowed);
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        body.add("embeds", embeds);
        return body;
    }

    private JsonObject footer(Poll p) {
        String text = "JustPolls · " + texts.get("webhook.poll-id", "id", Long.toString(p.id))
                + (cfg.serverName().isEmpty() ? "" : " · " + cfg.serverName());
        JsonObject footer = new JsonObject();
        footer.addProperty("text", cut(text, FOOTER));
        return footer;
    }

    private String winnerText(PollResult r) {
        List<Integer> winners = r.winners();
        if (winners.isEmpty()) return texts.get("webhook.no-votes");
        List<String> names = new ArrayList<>();
        for (int w : winners) names.add(r.poll().options.get(w));
        String joined = String.join(", ", names);
        return cut(winners.size() > 1 ? texts.get("webhook.tie", "options", joined) : "**" + joined + "**", FIELD_VALUE);
    }

    /** {@code █████░░░░░} for a percentage. */
    String bar(double pct) {
        int filled = (int) Math.round(pct / 100.0 * cfg.barWidth());
        filled = Math.max(0, Math.min(cfg.barWidth(), filled));
        return "█".repeat(filled) + "░".repeat(cfg.barWidth() - filled);
    }

    private String votes(int n) {
        String key = n == 1 ? "webhook.votes-one" : n >= 2 && n <= 4 ? "webhook.votes-few" : "webhook.votes-many";
        return texts.get(key, "n", Integer.toString(n));
    }

    /** Poll text as a Discord block quote; empty stays empty. Mentions are neutralised by allowed_mentions. */
    private static String quote(String text) {
        if (text.isBlank()) return "";
        return text.strip().lines().map(l -> "> " + l).reduce((a, b) -> a + "\n" + b).orElse("");
    }

    private static JsonObject field(String name, String value, boolean inline) {
        JsonObject f = new JsonObject();
        f.addProperty("name", name.isBlank() ? "​" : name);
        f.addProperty("value", value.isBlank() ? "​" : value);
        f.addProperty("inline", inline);
        return f;
    }

    static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
