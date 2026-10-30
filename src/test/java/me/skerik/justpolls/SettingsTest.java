package me.skerik.justpolls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import me.skerik.justpolls.config.Messages;
import me.skerik.justpolls.config.Settings;
import me.skerik.justpolls.gui.CreateForm;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class SettingsTest {

    private final List<String> warnings = new ArrayList<>();
    private final Logger log = Logger.getAnonymousLogger();

    SettingsTest() {
        log.setUseParentHandlers(false);
        log.addHandler(new Handler() {
            @Override
            public void publish(LogRecord r) {
                warnings.add(r.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
    }

    private Settings load(String yaml) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(yaml);
        return Settings.load(y, log);
    }

    @Test
    void bundledConfigLoadsWithoutWarnings() throws Exception {
        try (InputStream in = SettingsTest.class.getResourceAsStream("/config.yml")) {
            Settings s = load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            assertEquals(List.of(), warnings);
            assertEquals("en", s.locale());
            assertFalse(s.voting().allowChange());
            assertEquals(Settings.ResultsVisibility.AFTER_VOTE, s.voting().results());
            assertEquals(List.of("poll", "polls"), s.aliases());
            assertEquals(5_000, s.autoOpen().onStartDelayMs());
            assertEquals(0, s.autoOpen().reminderMs());
            assertFalse(s.discord().enabled());
            assertEquals(0x5865F2, s.discord().color());
            assertTrue(s.configPolls().isEmpty());
            assertEquals("sqlite", s.storage().type());
        }
    }

    @Test
    void invalidValuesFallBackWithWarnings() throws Exception {
        Settings s = load("""
                voting: {show-results: sometimes}
                creation: {min-duration: banana, max-options: 99, duration-presets: [10m, nope, 9999d]}
                discord: {enabled: true, webhook-url: "https://evil.example/x", mention-role-id: abc}
                storage: {type: oracle, sync-interval-seconds: 1}
                """);
        assertEquals(Settings.ResultsVisibility.AFTER_VOTE, s.voting().results());
        assertEquals(60_000, s.creation().minDurationMs());
        assertEquals(6, s.creation().maxOptions());
        assertEquals(List.of("10m"), s.creation().presets());
        assertFalse(s.discord().enabled(), "a URL that is not a Discord webhook switches Discord off");
        assertEquals("", s.discord().roleId());
        assertEquals("sqlite", s.storage().type());
        assertEquals(30, s.storage().syncSeconds());
        assertTrue(warnings.size() >= 7, warnings.toString());
    }

    @Test
    void configPollsAreValidated() throws Exception {
        Settings s = load("""
                discord: {send-by-default: false}
                polls:
                  weekly:
                    title: Event?
                    description: Pick
                    options: [A, B, C]
                    duration: 3d
                    start: "2030-12-24 18:00"
                    repeat: 7d
                    open-delay: 30s
                    permission: group.vip
                  off: {enabled: false, title: x, options: [a, b], duration: 1h}
                  one-option: {title: x, options: [a], duration: 1h}
                  no-duration: {title: x, options: [a, b]}
                  "bad key!": {title: x, options: [a, b], duration: 1h}
                """);
        assertEquals(1, s.configPolls().size());
        var cp = s.configPolls().getFirst();
        assertEquals("weekly", cp.key());
        assertEquals(List.of("A", "B", "C"), cp.template().options());
        assertEquals(3 * 86_400_000L, cp.template().durationMs());
        assertEquals(7 * 86_400_000L, cp.template().repeatMs());
        assertEquals(30_000, cp.template().openDelayMs());
        assertEquals("group.vip", cp.template().permission());
        assertFalse(cp.template().discord());
        assertTrue(cp.template().startAt() > System.currentTimeMillis());
        assertEquals(3, warnings.size(), warnings.toString());
    }

    @Test
    void quickSyntaxAndFormValidation() throws Exception {
        Settings s = load("{}");
        CreateForm f = CreateForm.parseQuick("1h Best season? | Summer | Winter | Autumn", s);
        assertEquals("Best season?", f.title());
        assertEquals(List.of("Summer", "Winter", "Autumn"), f.options());
        assertNull(CreateForm.parseQuick("1h only a question", s));
        assertNull(CreateForm.parseQuick("1h Q | just one", s));
        assertNull(CreateForm.parseQuick("noduration", s));

        var ok = f.validate(s, null, "tester");
        assertEquals(3_600_000, ok.poll().durationMs());
        assertEquals("tester", ok.poll().createdBy());
    }
}
