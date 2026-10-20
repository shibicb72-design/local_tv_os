package com.gathin.tvhome;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class QuotesTest {

    @Test
    public void theShippedFileHasOneHundredCleanQuotes() throws IOException {
        Path file = Paths.get("src/main/assets/quotes.json");
        if (!Files.exists(file)) file = Paths.get("app/src/main/assets/quotes.json");
        String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertTrue(json.replace(" ", "").contains("\"version\":1"));
        List<String[]> all = Quotes.parse(json);
        assertEquals(100, all.size());
        Set<String> texts = new HashSet<>();
        for (String[] q : all) {
            assertTrue(q[Quotes.TEXT], !q[Quotes.TEXT].isEmpty() && q[Quotes.TEXT].length() <= 110);
            assertTrue(q[Quotes.TEXT], !q[Quotes.AUTHOR].isEmpty() && !q[Quotes.SOURCE].isEmpty());
            assertTrue("duplicate: " + q[Quotes.TEXT], texts.add(q[Quotes.TEXT]));
            for (String part : q) {
                for (int i = 0; i < part.length(); i++) {
                    char c = part.charAt(i);
                    assertTrue("non-ASCII in: " + part, c >= 0x20 && c <= 0x7E);
                }
            }
        }
    }

    @Test
    public void escapesAreDecoded() {
        String json = "{\"version\":1,\"quotes\":[{\"text\":\"a \\\"b\\\" \\\\ c\\nd \\u0041\",\"author\":\"X\",\"source\":\"S\"}]}";
        List<String[]> q = Quotes.parse(json);
        assertEquals(1, q.size());
        assertEquals("a \"b\" \\ c\nd A", q.get(0)[Quotes.TEXT]);
        assertEquals("X", q.get(0)[Quotes.AUTHOR]);
    }

    @Test
    public void unknownKeysAreIgnored() {
        String json = "{\"extra\":[1,{\"a\":\"}\"}],\"quotes\":[{\"id\":7,\"text\":\"t\",\"mood\":{\"x\":[1,2]},"
                + "\"author\":\"A\",\"source\":\"S\"}],\"tail\":null}";
        List<String[]> q = Quotes.parse(json);
        assertEquals(1, q.size());
        assertEquals("t", q.get(0)[Quotes.TEXT]);
        assertEquals("S", q.get(0)[Quotes.SOURCE]);
    }

    @Test(timeout = 1000)
    public void badInputGivesAnEmptyList() {
        assertTrue(Quotes.parse(null).isEmpty());
        assertTrue(Quotes.parse("").isEmpty());
        assertTrue(Quotes.parse("not json").isEmpty());
        assertTrue(Quotes.parse("{\"quotes\":[{\"text\":\"a\",\"author\":\"b\"},{\"text\":\"c").isEmpty());
        assertTrue(Quotes.parse("{\"quotes\":[{\"text\":\"a\\u12\"").isEmpty());
        assertTrue(Quotes.parse("{\"quotes\":[}]}").isEmpty());
        assertTrue(Quotes.parse("{\"quotes\":[1}]}").isEmpty());
    }

    @Test
    public void displayJoinsTextAndAuthorWithAnEmDash() {
        String[] q = {"Veni", "Caesar", "src"};
        assertEquals("Veni \u2014 Caesar", Quotes.display(q));
        assertEquals("Caesar", Quotes.display(q).substring(Quotes.authorStart(q)));
    }
}
