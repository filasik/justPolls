package me.skerik.justpolls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.skerik.justpolls.gui.ClickData;
import org.junit.jupiter.api.Test;

class ClickDataTest {

    @Test
    void readsOurButtonPayloads() {
        ClickData d = ClickData.parse("{p:42L,o:3}");
        assertEquals(42, d.number("p", -1));
        assertEquals(3, d.number("o", -1));
        ClickData a = ClickData.parse("{a:\"extend\",p:7L,n:-3600000L}");
        assertEquals("extend", a.text("a"));
        assertEquals(-3_600_000L, a.number("n", 0));
    }

    @Test
    void readsFormSubmissions() {
        ClickData d = ClickData.parse("{title:\"Best \\\"map\\\"?\",description:'it\\'s fine\\nline 2',option1:\"A\",duration:\"1h\","
                + "discord:1b,auto_open:0b,custom_duration:\"\"}");
        assertEquals("Best \"map\"?", d.text("title"));
        assertEquals("it's fine\nline 2", d.text("description"));
        assertEquals("A", d.text("option1"));
        assertEquals("", d.text("custom_duration"));
        assertTrue(d.bool("discord"));
        assertFalse(d.bool("auto_open"));
        assertFalse(d.bool("missing"));
    }

    @Test
    void anythingElseIsAHarmlessEmptyPayload() {
        for (String bad : new String[]{null, "", "   ", "not a compound", "{p:", "{p:1L", "{:1}", "[1,2]"}) {
            ClickData d = ClickData.parse(bad);
            assertEquals(-1, d.number("p", -1), "payload: " + bad);
            assertEquals("", d.text("a"));
        }
    }

    @Test
    void handlesUnicodeAndBooleanWords() {
        ClickData d = ClickData.parse("{t:\"Příliš žluťoučký kůň \\u00e9\",flag:true}");
        assertEquals("Příliš žluťoučký kůň é", d.text("t"));
        assertTrue(d.bool("flag"));
    }
}
