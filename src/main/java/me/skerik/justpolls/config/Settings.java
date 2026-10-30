package me.skerik.justpolls.config;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.logging.Logger;
import me.skerik.justpolls.model.NewPoll;
import me.skerik.justpolls.service.PollService.ConfigPoll;
import me.skerik.justpolls.storage.DbConfig;
import me.skerik.justpolls.util.Durations;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Immutable snapshot of config.yml. Rules: every option is read here and nowhere else, every value is validated,
 * an invalid value falls back to the default and is logged (the plugin never refuses to start over a typo).
 */
public record Settings(
        String locale, boolean updateCheck, boolean metrics, List<String> aliases,
        Voting voting, Creation creation, AutoOpen autoOpen, Announce announce, Discord discord,
        Storage storage, List<ConfigPoll> configPolls
) {

    /** Hard limits of texts typed into polls (they must fit dialogs, Discord embeds and database columns). */
    public static final int TITLE_MAX = 100;
    public static final int DESCRIPTION_MAX = 500;
    public static final int OPTION_MAX = 60;
    public static final int HARD_MAX_OPTIONS = 10;

    public enum ResultsVisibility { LIVE, AFTER_VOTE, AFTER_END }

    public record Voting(boolean allowChange, ResultsVisibility results, List<String> rewardCommands) {
    }

    public record Creation(long minDurationMs, long maxDurationMs, int maxOptions, int maxActive,
                           List<String> presets, String defaultPreset) {
    }

    public record AutoOpen(boolean enabled, long onStartDelayMs, long onJoinDelayMs, long reminderMs, int maxOpens) {
    }

    public record Announce(boolean chat, String sound) {
    }

    public record Discord(boolean enabled, String url, String username, String avatarUrl, String roleId,
                          boolean announceStart, int color, int barWidth, boolean defaultSend, String serverName) {
    }

    public record Storage(String type, String sqliteFile, int sqlitePool, String host, int port, String database,
                          String user, String password, int poolSize, String sslMode, int syncSeconds) {

        public DbConfig dbConfig(Path dataFolder) {
            if (type.equals("mariadb")) {
                return new DbConfig(DbConfig.Dialect.MARIADB, null, host, port, database, user, password, poolSize, sslMode);
            }
            return DbConfig.sqlite(dataFolder.resolve(sqliteFile), sqlitePool);
        }
    }

    public static Settings load(FileConfiguration c, Logger log) {
        Creation creation = creation(c, log);
        Discord discord = discord(c, log);
        return new Settings(
                c.getString("locale", "en").toLowerCase(Locale.ROOT).strip(),
                c.getBoolean("update-check", true),
                c.getBoolean("metrics", true),
                c.getStringList("command.aliases").stream().map(String::strip).filter(a -> !a.isEmpty()).toList(),
                voting(c, log),
                creation,
                autoOpen(c, log),
                new Announce(c.getBoolean("announce.chat", true), c.getString("announce.sound", "block.note_block.pling").strip()),
                discord,
                storage(c, log),
                configPolls(c, log, creation, discord));
    }

    private static Voting voting(FileConfiguration c, Logger log) {
        String results = c.getString("voting.show-results", "after-vote").toLowerCase(Locale.ROOT).strip();
        ResultsVisibility visibility = switch (results) {
            case "live" -> ResultsVisibility.LIVE;
            case "after-end" -> ResultsVisibility.AFTER_END;
            case "after-vote" -> ResultsVisibility.AFTER_VOTE;
            default -> {
                log.warning("voting.show-results must be live, after-vote or after-end; using after-vote");
                yield ResultsVisibility.AFTER_VOTE;
            }
        };
        return new Voting(c.getBoolean("voting.allow-change", false), visibility,
                c.getStringList("voting.reward-commands").stream().map(String::strip).filter(s -> !s.isEmpty()).toList());
    }

    private static Creation creation(FileConfiguration c, Logger log) {
        long min = duration(c, "creation.min-duration", "1m", log);
        long max = duration(c, "creation.max-duration", "30d", log);
        if (max < min) {
            log.warning("creation.max-duration is shorter than creation.min-duration; using 30d");
            max = Math.max(min, 30L * 86_400_000L);
        }
        List<String> presets = new ArrayList<>();
        for (String p : c.getStringList("creation.duration-presets")) {
            OptionalLong ms = Durations.parse(p);
            if (ms.isPresent() && ms.getAsLong() >= min && ms.getAsLong() <= max) presets.add(p.strip().replace(" ", ""));
            else log.warning("creation.duration-presets: ignoring \"" + p + "\" (invalid or outside min/max duration)");
        }
        if (presets.isEmpty()) presets = List.of("10m", "1h", "1d", "7d");
        String def = c.getString("creation.default-duration", "1h").strip().replace(" ", "");
        if (!presets.contains(def)) def = presets.getFirst();
        return new Creation(min, max, bounded(c, "creation.max-options", 2, Settings.HARD_MAX_OPTIONS, 6, log),
                bounded(c, "creation.max-active-polls", 1, 100, 5, log), List.copyOf(presets), def);
    }

    private static AutoOpen autoOpen(FileConfiguration c, Logger log) {
        return new AutoOpen(c.getBoolean("auto-open.enabled", true),
                durationOrZero(c, "auto-open.on-start-delay", "5s", log),
                durationOrZero(c, "auto-open.on-join-delay", "15s", log),
                durationOrZero(c, "auto-open.reminder-interval", "0", log),
                bounded(c, "auto-open.max-opens", 1, 50, 3, log));
    }

    private static Discord discord(FileConfiguration c, Logger log) {
        String url = c.getString("discord.webhook-url", "").strip();
        boolean enabled = c.getBoolean("discord.enabled", false);
        if (enabled && !me.skerik.justpolls.discord.WebhookSender.isValidUrl(url)) {
            log.warning("discord.webhook-url is not a Discord webhook URL (https://discord.com/api/webhooks/<id>/<token>); "
                    + "Discord results are switched off");
            enabled = false;
        }
        int color = 0x5865F2;
        String hex = c.getString("discord.color", "#5865F2").strip().replaceFirst("^#", "");
        try {
            color = Integer.parseInt(hex, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            log.warning("discord.color must be a hex colour like #5865F2; using that");
        }
        String role = c.getString("discord.mention-role-id", "").strip();
        if (!role.isEmpty() && !role.matches("\\d{5,25}")) {
            log.warning("discord.mention-role-id must be the numeric id of a role; no mention will be sent");
            role = "";
        }
        return new Discord(enabled, url, c.getString("discord.username", "JustPolls").strip(),
                c.getString("discord.avatar-url", "").strip(), role, c.getBoolean("discord.announce-start", false), color,
                bounded(c, "discord.bar-width", 5, 30, 20, log), c.getBoolean("discord.send-by-default", true),
                c.getString("discord.server-name", "").strip());
    }

    private static Storage storage(FileConfiguration c, Logger log) {
        String type = c.getString("storage.type", "sqlite").toLowerCase(Locale.ROOT).strip();
        if (type.equals("mysql")) type = "mariadb"; // same driver and dialect
        if (!type.equals("sqlite") && !type.equals("mariadb")) {
            log.warning("storage.type must be sqlite or mariadb; using sqlite");
            type = "sqlite";
        }
        ConfigurationSection m = c.getConfigurationSection("storage.mariadb");
        return new Storage(type,
                c.getString("storage.sqlite.file", "justpolls.db"),
                bounded(c, "storage.sqlite.pool-size", 1, 32, 3, log),
                m == null ? "localhost" : m.getString("host", "localhost"),
                m == null ? 3306 : bounded(c, "storage.mariadb.port", 1, 65535, 3306, log),
                m == null ? "justpolls" : m.getString("database", "justpolls"),
                m == null ? "justpolls" : m.getString("user", "justpolls"),
                m == null ? "" : m.getString("password", ""),
                m == null ? 6 : bounded(c, "storage.mariadb.pool-size", 1, 64, 6, log),
                m == null ? "" : m.getString("ssl-mode", "").strip(),
                bounded(c, "storage.sync-interval-seconds", 5, 3600, 30, log));
    }

    private static List<ConfigPoll> configPolls(FileConfiguration c, Logger log, Creation creation, Discord discord) {
        List<ConfigPoll> result = new ArrayList<>();
        ConfigurationSection section = c.getConfigurationSection("polls");
        if (section == null) return result;
        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null || !s.getBoolean("enabled", true)) continue;
            String where = "polls." + key;
            if (!key.matches("[A-Za-z0-9_-]{1,64}")) {
                log.warning(where + ": the key may only contain letters, digits, - and _ (max 64); skipped");
                continue;
            }
            String title = s.getString("title", "").strip();
            List<String> options = s.getStringList("options").stream().map(String::strip).filter(o -> !o.isEmpty()).toList();
            OptionalLong duration = Durations.parse(s.getString("duration", ""));
            if (title.isEmpty() || title.length() > TITLE_MAX) {
                log.warning(where + ".title is required (max " + TITLE_MAX + " characters); skipped");
                continue;
            }
            if (options.size() < 2 || options.size() > HARD_MAX_OPTIONS || options.stream().anyMatch(o -> o.length() > OPTION_MAX)) {
                log.warning(where + ".options needs 2 to " + HARD_MAX_OPTIONS + " entries of at most " + OPTION_MAX + " characters; skipped");
                continue;
            }
            if (duration.isEmpty()) {
                log.warning(where + ".duration is missing or invalid (examples: 10m, 1h, 7d); skipped");
                continue;
            }
            long start = 0;
            String startText = s.getString("start", "").strip();
            if (!startText.isEmpty()) {
                try {
                    start = LocalDateTime.parse(startText.replace(' ', 'T')).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                } catch (DateTimeParseException e) {
                    log.warning(where + ".start must look like 2026-12-24 18:00 (server time); starting at once");
                }
            }
            String repeatText = s.getString("repeat", "").strip();
            long repeat = repeatText.isEmpty() || repeatText.equals("0") ? 0 : Durations.parse(repeatText).orElse(0);
            if (!repeatText.isEmpty() && !repeatText.equals("0") && repeat == 0) {
                log.warning(where + ".repeat is invalid (examples: 7d, 30d); the poll will not repeat");
            }
            long openDelay = s.contains("open-delay") ? Durations.parse(s.getString("open-delay", "0")).orElse(-1) : -1;
            result.add(new ConfigPoll(key, new NewPoll(title, s.getString("description", "").strip(), options,
                    duration.getAsLong(), start, s.getBoolean("discord", discord.defaultSend()),
                    s.getBoolean("auto-open", true), openDelay, s.getString("permission", "").strip(), key, repeat, "config")));
        }
        return result;
    }

    private static long duration(FileConfiguration c, String path, String fallback, Logger log) {
        OptionalLong ms = Durations.parse(c.getString(path, fallback));
        if (ms.isEmpty()) {
            log.warning(path + " must look like 10m, 1h or 7d; using " + fallback);
            return Durations.parse(fallback).orElseThrow();
        }
        return ms.getAsLong();
    }

    /** A duration option where 0 means "off". */
    private static long durationOrZero(FileConfiguration c, String path, String fallback, Logger log) {
        String text = c.getString(path, fallback).strip();
        if (text.equals("0")) return 0;
        return duration(c, path, fallback, log);
    }

    /** An int option inside [min, max]; anything else is logged and replaced by the default. */
    static int bounded(FileConfiguration c, String path, int min, int max, int fallback, Logger log) {
        int value = c.getInt(path, fallback);
        if (value < min || value > max) {
            log.warning(path + " must be between " + min + " and " + max + "; using " + fallback);
            return fallback;
        }
        return value;
    }
}
