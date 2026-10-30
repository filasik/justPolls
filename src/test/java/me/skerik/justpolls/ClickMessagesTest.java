package me.skerik.justpolls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import me.skerik.justpolls.config.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** A clickable chat line must run the real command, not the text "/<label> vote <id>" (seen live). */
class ClickMessagesTest {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static String raw(String locale, String key) throws Exception {
        try (InputStream in = ClickMessagesTest.class.getResourceAsStream("/messages_" + locale + ".yml")) {
            YamlConfiguration y = new YamlConfiguration();
            y.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return y.isList(key) ? String.join("\n", y.getStringList(key)) : y.getString(key);
        }
    }

    private static List<String> commands(Component c) {
        List<String> out = new ArrayList<>();
        collect(c, out);
        return out;
    }

    private static void collect(Component c, List<String> out) {
        ClickEvent<?> e = c.style().clickEvent();
        if (e != null && e.payload() instanceof ClickEvent.Payload.Text t) out.add(t.value());
        c.children().forEach(child -> collect(child, out));
    }

    @Test
    void chatLinksRunTheRealCommand() throws Exception {
        for (String locale : Messages.BUNDLED_LOCALES) {
            TagResolver r = TagResolver.resolver(Messages.arg("label", "poll"), Messages.arg("id", "7"),
                    Messages.text("title", "Evil <click:run_command:'/op me'> title"), Messages.text("time", "1h"),
                    Messages.text("prefix", ""));
            for (String key : List.of("announce.started", "poll.list-line", "poll.list-line-voted", "poll.history-line")) {
                List<String> cmds = commands(MM.deserialize(raw(locale, key), r));
                assertFalse(cmds.isEmpty(), locale + " " + key + " has a click");
                for (String cmd : cmds) {
                    assertTrue(cmd.matches("/poll (vote|results) 7"), locale + " " + key + ": " + cmd);
                }
            }
        }
    }

    @Test
    void argPlaceholdersCannotInjectTags() {
        TagResolver r = Messages.arg("label", "poll'><click:run_command:'/op me'>");
        Component c = MM.deserialize("<click:run_command:'/<label> vote 1'>x</click>", r);
        List<String> cmds = commands(c);
        assertEquals(1, cmds.size(), "no second click event can be injected");
        assertFalse(cmds.getFirst().contains("<") || cmds.getFirst().contains("'"));
    }
}
