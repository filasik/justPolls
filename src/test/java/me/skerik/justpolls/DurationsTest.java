package me.skerik.justpolls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.skerik.justpolls.util.Durations;
import org.junit.jupiter.api.Test;

class DurationsTest {

    @Test
    void parsesUnitsAndCombinations() {
        assertEquals(10 * 60_000L, Durations.parse("10m").getAsLong());
        assertEquals(3_600_000L, Durations.parse("1h").getAsLong());
        assertEquals(86_400_000L + 12 * 3_600_000L, Durations.parse("1d12h").getAsLong());
        assertEquals(7 * 86_400_000L, Durations.parse("1w").getAsLong());
        assertEquals(7 * 86_400_000L, Durations.parse(" 7D ").getAsLong());
        assertEquals(30_000L, Durations.parse("30s").getAsLong());
        assertEquals(5 * 60_000L, Durations.parse("5").getAsLong(), "a bare number is minutes");
    }

    @Test
    void rejectsGarbageZeroAndAbsurdValues() {
        for (String bad : new String[]{"", "  ", "abc", "0", "0m", "-5m", "10x", "m10", "1.5h", "99999999999999d", "1h 30"}) {
            assertTrue(Durations.parse(bad).isEmpty(), "should reject: " + bad);
        }
    }

    @Test
    void formatsShortAndRoundsUp() {
        assertEquals("10m", Durations.format(600_000));
        assertEquals("1d 2h", Durations.format(86_400_000L + 2 * 3_600_000L + 5 * 60_000L));
        assertEquals("1d", Durations.format(86_400_000L + 5 * 60_000L), "a zero part ends the text");
        assertEquals("5m 30s", Durations.format(330_000));
        assertEquals("1s", Durations.format(1), "never shows 0 for something that still has time left");
        assertEquals("0s", Durations.format(-5));
    }
}
