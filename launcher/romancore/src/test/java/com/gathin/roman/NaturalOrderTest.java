package com.gathin.roman;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class NaturalOrderTest {
    private static int cmp(String a, String b) {
        return NaturalOrder.INSTANCE.compare(a, b);
    }

    @Test
    public void digitRunsAreNumeric() {
        assertTrue(cmp("Ep 2", "Ep 10") < 0);
        assertTrue(cmp("Ep 10", "Ep 2") > 0);
        assertTrue(cmp("a99999999999999999999", "a100000000000000000000") < 0);
    }

    @Test
    public void caseInsensitiveWithStableTieBreak() {
        assertTrue(cmp("apple", "Banana") < 0);
        assertTrue(cmp("a", "A") > 0); // equal ignoring case, raw order: 'A' < 'a'
        assertTrue(cmp("A", "a") < 0);
        assertTrue(cmp("a01", "a1") != 0);
        assertEquals(0, cmp("same", "same"));
    }

    @Test
    public void prefixSortsFirst() {
        assertTrue(cmp("ab", "abc") < 0);
        assertTrue(cmp("", "a") < 0);
    }

    @Test
    public void sortsList() {
        List<String> l = new ArrayList<>(Arrays.asList("Ep 10", "ep 2", "Ep 1"));
        Collections.sort(l, NaturalOrder.INSTANCE);
        assertEquals(Arrays.asList("Ep 1", "ep 2", "Ep 10"), l);
    }
}
