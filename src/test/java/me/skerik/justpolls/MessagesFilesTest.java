package me.skerik.justpolls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.TreeSet;
import me.skerik.justpolls.config.Messages;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** Every bundled language must have exactly the keys of the English file (no missing translations, no leftovers). */
class MessagesFilesTest {

    private static Set<String> keys(String locale) throws Exception {
        try (InputStream in = MessagesFilesTest.class.getResourceAsStream("/messages_" + locale + ".yml")) {
            assertTrue(in != null, "messages_" + locale + ".yml missing");
            YamlConfiguration y = new YamlConfiguration();
            y.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            Set<String> leaves = new TreeSet<>();
            for (String key : y.getKeys(true)) {
                if (!y.isConfigurationSection(key)) leaves.add(key);
            }
            return leaves;
        }
    }

    @Test
    void allLocalesHaveTheSameKeysAsEnglish() throws Exception {
        Set<String> english = keys("en");
        assertTrue(english.contains("prefix"));
        for (String locale : Messages.BUNDLED_LOCALES) {
            assertEquals(english, keys(locale), "keys of messages_" + locale + ".yml differ from messages_en.yml");
        }
    }

    /** YAML 1.1 reads the keys yes/no/on/off/true/false as booleans, which silently breaks the lookup of that key. */
    @Test
    void noKeyIsAYamlBoolean() throws Exception {
        for (String locale : Messages.BUNDLED_LOCALES) {
            for (String key : keys(locale)) {
                for (String part : key.split("\\.")) {
                    assertTrue(!part.equals("true") && !part.equals("false"), "boolean-like key in messages_" + locale + ".yml: " + key
                            + " (rename it, e.g. yes -> accept)");
                }
            }
        }
    }

    /** Every message key written in the code must exist in the English file (catches typos before a player sees [key]). */
    @Test
    void everyKeyUsedInTheCodeExists() throws Exception {
        Set<String> english = keys("en");
        Pattern literal = Pattern.compile("\"((?:error|command|poll|announce|vote|create|manage|autoopen|gui|update|webhook|join)\\.[a-z0-9.\\-]*)\"");
        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java") && !f.endsWith("Settings.java")).toList()) {
                Matcher m = literal.matcher(Files.readString(file));
                while (m.find()) {
                    String key = m.group(1);
                    boolean ok = key.endsWith(".") ? english.stream().anyMatch(k -> k.startsWith(key)) : english.contains(key);
                    if (!ok) missing.add(file.getFileName() + ": " + key);
                }
            }
        }
        assertTrue(missing.isEmpty(), "message keys used in code but missing from messages_en.yml: " + missing);
    }

    /** Keys that exist in the file but are never used: a leftover after a refactoring. */
    @Test
    void noUnusedKeys() throws Exception {
        StringBuilder code = new StringBuilder();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java") && !f.endsWith("Settings.java")).toList()) code.append(Files.readString(file));
        }
        List<String> unused = new ArrayList<>();
        for (String key : keys("en")) {
            if (key.equals("prefix")) continue;
            String parent = key.contains(".") ? key.substring(0, key.lastIndexOf('.') + 1) : key;
            if (!code.toString().contains("\"" + key + "\"") && !code.toString().contains("\"" + parent + "\"")) unused.add(key);
        }
        assertTrue(unused.isEmpty(), "unused message keys: " + unused);
    }
}
