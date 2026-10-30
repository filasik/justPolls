package me.skerik.justpolls.util;

import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsing and formatting of durations like {@code 10m}, {@code 1h30m}, {@code 7d}, {@code 2w}. */
public final class Durations {

    private static final Pattern WHOLE = Pattern.compile("(?:\\d{1,9}[smhdw])+", Pattern.CASE_INSENSITIVE);
    private static final Pattern PART = Pattern.compile("(\\d+)([smhdw])", Pattern.CASE_INSENSITIVE);
    private static final long MAX_MS = 10L * 365 * 24 * 3600 * 1000;

    private Durations() {
    }

    /**
     * @param text units s, m, h, d, w (combinable: {@code 1h30m}); a bare number means minutes
     * @return milliseconds, empty for blank, zero or malformed input
     */
    public static OptionalLong parse(String text) {
        String s = text.strip().replace(" ", "").toLowerCase(java.util.Locale.ROOT);
        if (s.isEmpty()) return OptionalLong.empty();
        long total = 0;
        if (s.matches("\\d{1,9}")) {
            total = Long.parseLong(s) * 60_000L;
        } else if (WHOLE.matcher(s).matches()) {
            Matcher m = PART.matcher(s);
            while (m.find()) {
                total += Long.parseLong(m.group(1)) * unit(m.group(2).charAt(0));
                if (total > MAX_MS) return OptionalLong.empty();
            }
        } else {
            return OptionalLong.empty();
        }
        return total <= 0 || total > MAX_MS ? OptionalLong.empty() : OptionalLong.of(total);
    }

    private static long unit(char c) {
        return switch (c) {
            case 's' -> 1_000L;
            case 'm' -> 60_000L;
            case 'h' -> 3_600_000L;
            case 'd' -> 86_400_000L;
            default -> 7 * 86_400_000L;
        };
    }

    /** Short text with at most two parts, e.g. {@code 1d 2h}, {@code 5m 30s}; "0s" for zero or less. */
    public static String format(long ms) {
        long seconds = Math.max(0, (ms + 999) / 1000);
        if (seconds == 0) return "0s";
        long d = seconds / 86_400;
        long h = seconds % 86_400 / 3_600;
        long m = seconds % 3_600 / 60;
        long s = seconds % 60;
        StringBuilder out = new StringBuilder();
        int parts = 0;
        long[] values = {d, h, m, s};
        String[] names = {"d", "h", "m", "s"};
        for (int i = 0; i < values.length && parts < 2; i++) {
            if (values[i] == 0 && parts == 0) continue;
            if (values[i] == 0) break; // 1d 0h 5m is shown as 1d
            if (parts > 0) out.append(' ');
            out.append(values[i]).append(names[i]);
            parts++;
        }
        return out.toString();
    }
}
