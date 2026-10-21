package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;

/**
 * Reader for {@code assets/quotes.json} ({@code {"version":1,"quotes":[{"text","author","source"}]}}). A small
 * scanner instead of org.json, which does not work in JVM unit tests. Never throws: bad input gives an empty list.
 */
final class Quotes {

    static final int TEXT = 0, AUTHOR = 1, SOURCE = 2;
    private static final String DASH = " — ";   // in the Cinzel subset

    private final String s;
    private int i;

    private Quotes(String s) {
        this.s = s;
    }

    /** All quotes as {text, author, source}; entries without a text or an author are skipped. */
    static List<String[]> parse(String json) {
        List<String[]> out = new ArrayList<>();
        if (json == null) return out;
        try {
            new Quotes(json).root(out);
        } catch (RuntimeException e) {
            out.clear();   // truncated or malformed: no half lists
        }
        return out;
    }

    /** "text — author", the line that is shown. */
    static String display(String[] q) {
        return q[TEXT] + DASH + q[AUTHOR];
    }

    /** Index in {@link #display} where the author starts. */
    static int authorStart(String[] q) {
        return q[TEXT].length() + DASH.length();
    }

    private void root(List<String[]> out) {
        ws();
        expect('{');
        ws();
        while (peek() != '}') {
            String key = string();
            ws();
            expect(':');
            ws();
            if ("quotes".equals(key) && peek() == '[') {
                array(out);
            } else {
                skip();
            }
            ws();
            if (peek() == ',') i++;
            ws();
        }
    }

    private void array(List<String[]> out) {
        expect('[');
        ws();
        while (peek() != ']') {
            if (peek() == '{') {
                String[] q = object();
                if (!q[TEXT].isEmpty() && !q[AUTHOR].isEmpty()) out.add(q);
            } else {
                skip();
            }
            ws();
            if (peek() == ',') i++;
            ws();
        }
        i++;
    }

    private String[] object() {
        String[] q = {"", "", ""};
        expect('{');
        ws();
        while (peek() != '}') {
            String key = string();
            ws();
            expect(':');
            ws();
            if (peek() == '"') {
                String v = string();
                if ("text".equals(key)) q[TEXT] = v;
                else if ("author".equals(key)) q[AUTHOR] = v;
                else if ("source".equals(key)) q[SOURCE] = v;
            } else {
                skip();
            }
            ws();
            if (peek() == ',') i++;
            ws();
        }
        i++;
        return q;
    }

    /** Skips one value of any kind. */
    private void skip() {
        char c = peek();
        if (c == '"') {
            string();
        } else if (c == '{' || c == '[') {
            int depth = 0;
            do {
                c = peek();
                if (c == '"') {
                    string();
                    continue;
                }
                if (c == '{' || c == '[') depth++;
                else if (c == '}' || c == ']') depth--;
                i++;
            } while (depth > 0);
        } else {
            int from = i;
            while (i < s.length() && ",}] \t\r\n".indexOf(s.charAt(i)) < 0) i++;
            if (i == from) throw new IllegalStateException("unexpected " + c);
        }
    }

    private String string() {
        expect('"');
        StringBuilder b = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') return b.toString();
            if (c != '\\') {
                b.append(c);
                continue;
            }
            char e = next();
            switch (e) {
                case 'n': b.append('\n'); break;
                case 't': b.append('\t'); break;
                case 'r': b.append('\r'); break;
                case 'b': b.append('\b'); break;
                case 'f': b.append('\f'); break;
                case 'u':
                    if (i + 4 > s.length()) throw new IllegalStateException("short \\u");
                    b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    i += 4;
                    break;
                default: b.append(e);   // \" \\ \/
            }
        }
    }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private char peek() {
        if (i >= s.length()) throw new IllegalStateException("end of input");
        return s.charAt(i);
    }

    private char next() {
        char c = peek();
        i++;
        return c;
    }

    private void expect(char c) {
        if (next() != c) throw new IllegalStateException("expected " + c);
    }
}
