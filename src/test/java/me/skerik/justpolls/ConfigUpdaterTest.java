package me.skerik.justpolls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import me.skerik.justpolls.config.ConfigUpdater;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** An old config.yml must gain the new keys (with comments) and keep every value and comment the admin wrote. */
class ConfigUpdaterTest {

    /** A config from "version 0": only what the admin changed, plus their own comment. Extend it when you add keys. */
    private static final String OLD = """
            # my own note
            locale: cs   # czech
            update-check: false
            """;

    private static YamlConfiguration bundled() throws Exception {
        try (InputStream in = ConfigUpdaterTest.class.getResourceAsStream("/config.yml")) {
            YamlConfiguration y = new YamlConfiguration();
            y.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return y;
        }
    }

    @Test
    void addsMissingKeysKeepsUserValuesAndComments() throws Exception {
        YamlConfiguration user = new YamlConfiguration();
        user.loadFromString(OLD);
        YamlConfiguration defaults = bundled();
        assertFalse(user.contains("voting.allow-change", true));

        int added = ConfigUpdater.merge(user, defaults);

        assertTrue(added > 0);
        assertEquals("cs", user.getString("locale"));
        assertFalse(user.getBoolean("update-check"));
        assertTrue(user.contains("voting.allow-change", true));
        assertEquals(defaults.getInt(ConfigUpdater.VERSION_KEY), user.getInt(ConfigUpdater.VERSION_KEY));

        String out = user.saveToString();
        assertTrue(out.contains("# my own note"), out);
        assertTrue(out.contains("a player votes once"), out); // the comment of a new key came along

        // a second pass changes nothing
        YamlConfiguration again = new YamlConfiguration();
        again.loadFromString(out);
        assertEquals(0, ConfigUpdater.merge(again, defaults));
        assertEquals(out, again.saveToString());
    }

    @Test
    void bundledConfigDeclaresAVersion() throws Exception {
        assertTrue(bundled().getInt(ConfigUpdater.VERSION_KEY, 0) >= 1);
    }
}
