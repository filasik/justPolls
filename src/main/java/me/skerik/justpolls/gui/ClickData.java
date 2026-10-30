package me.skerik.justpolls.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The payload of a dialog click, read from the SNBT text the client sends back ({@code {p:5L,o:2}} for our buttons,
 * {@code {title:"x",auto_open:1b}} for a submitted form). Paper hands the payload over as a string and ships no NBT
 * library, so this is a small reader for exactly what dialogs produce: a compound of strings, numbers and booleans.
 * Anything it does not understand yields an empty payload, which every handler treats as "nothing to do".
 */
public final class ClickData {

    private static final ClickData EMPTY = new ClickData(Map.of());

    private final Map<String, Object> values;

    private ClickData(Map<String, Object> values) {
        this.values = values;
    }

    public static ClickData empty() {
        return EMPTY;
    }

    public static ClickData parse(@Nullable String snbt) {
        if (snbt == null || snbt.isBlank()) return EMPTY;
        try {
            Reader reader = new Reader(snbt);
            Map<String, Object> compound = reader.compound();
            return new ClickData(compound);
        } catch (RuntimeException e) {
            return EMPTY;
        }
    }

    public String text(String key) {
        Object v = values.get(key);
        if (v instanceof String s) return s;
        return v == null ? "" : String.valueOf(v);
    }

    public long number(String key, long fallback) {
        Object v = values.get(key);
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            try {
                return Long.parseLong(s.strip());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }

    public boolean bool(String key) {
        Object v = values.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.longValue() != 0;
        return v instanceof String s && Boolean.parseBoolean(s.strip());
    }

    /** Recursive-descent reader for the subset of SNBT described above. */
    private static final class Reader {
        private final String s;
        private int i;

        Reader(String s) {
            this.s = s;
        }

        Map<String, Object> compound() {
            skipSpace();
            expect('{');
            Map<String, Object> out = new HashMap<>();
            skipSpace();
            if (peek() == '}') {
                i++;
                return out;
            }
            while (true) {
                String key = token();
                skipSpace();
                expect(':');
                out.put(key, value());
                skipSpace();
                char c = next();
                if (c == '}') return out;
                if (c != ',') throw new IllegalArgumentException("expected , or }");
            }
        }

        private Object value() {
            skipSpace();
            char c = peek();
            if (c == '{') return compound();
            if (c == '[') return list();
            if (c == '"' || c == '\'') return quoted();
            String word = bare();
            if (word.equals("true")) return Boolean.TRUE;
            if (word.equals("false")) return Boolean.FALSE;
            if (word.matches("-?\\d+[bBsSlL]?")) {
                String digits = Character.isDigit(word.charAt(word.length() - 1)) ? word : word.substring(0, word.length() - 1);
                return Long.parseLong(digits);
            }
            if (word.matches("-?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?[fFdD]?")) {
                char last = word.charAt(word.length() - 1);
                String num = "fFdD".indexOf(last) >= 0 ? word.substring(0, word.length() - 1) : word;
                return Double.parseDouble(num);
            }
            return word;
        }

        private List<Object> list() {
            expect('[');
            List<Object> out = new ArrayList<>();
            skipSpace();
            if (peek() == ']') {
                i++;
                return out;
            }
            while (true) {
                out.add(value());
                skipSpace();
                char c = next();
                if (c == ']') return out;
                if (c != ',') throw new IllegalArgumentException("expected , or ]");
            }
        }

        /** A key or a value that is either quoted or a bare word. */
        private String token() {
            skipSpace();
            char c = peek();
            return c == '"' || c == '\'' ? quoted() : bare();
        }

        private String bare() {
            int start = i;
            while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || "_-.+".indexOf(s.charAt(i)) >= 0)) i++;
            if (i == start) throw new IllegalArgumentException("unexpected character at " + i);
            return s.substring(start, i);
        }

        private String quoted() {
            char quote = next();
            StringBuilder out = new StringBuilder();
            while (true) {
                char c = next();
                if (c == quote) return out.toString();
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                char e = next();
                switch (e) {
                    case 'n' -> out.append('\n');
                    case 't' -> out.append('\t');
                    case 'r' -> out.append('\r');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 's' -> out.append(' ');
                    case 'u' -> {
                        out.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> out.append(e); // \\ \" \' and anything else: the character itself
                }
            }
        }

        private void skipSpace() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        private char peek() {
            if (i >= s.length()) throw new IllegalArgumentException("unexpected end");
            return s.charAt(i);
        }

        private char next() {
            char c = peek();
            i++;
            return c;
        }

        private void expect(char c) {
            if (next() != c) throw new IllegalArgumentException("expected " + c);
        }
    }
}
