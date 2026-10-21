package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Random;

public class QuoteOrderTest {

    private static int[] take(QuoteOrder o, int count) {
        int[] r = new int[count];
        for (int i = 0; i < count; i++) r[i] = o.next();
        return r;
    }

    @Test
    public void fullPassCoversEveryIndexExactlyOnce() {
        for (long seed = 0; seed < 20; seed++) {
            int[] pass = take(new QuoteOrder(100, new Random(seed)), 100);
            int[] count = new int[100];
            for (int i : pass) count[i]++;
            for (int c : count) assertEquals(1, c);
        }
    }

    @Test
    public void everyPassCoversEveryIndexAndNeverRepeatsInARow() {
        for (long seed = 0; seed < 50; seed++) {
            int[] seq = take(new QuoteOrder(100, new Random(seed)), 100 * 30);
            for (int p = 0; p < 30; p++) {
                boolean[] seen = new boolean[100];
                for (int i = 0; i < 100; i++) {
                    int v = seq[p * 100 + i];
                    assertFalse(seen[v]);
                    seen[v] = true;
                }
            }
            for (int i = 1; i < seq.length; i++) assertNotEquals(seq[i - 1], seq[i]);
        }
    }

    @Test
    public void differentSeedsGiveDifferentOrders() {
        int[] a = take(new QuoteOrder(100, new Random(1)), 100);
        int[] b = take(new QuoteOrder(100, new Random(2)), 100);
        int same = 0;
        for (int i = 0; i < 100; i++) if (a[i] == b[i]) same++;
        assertTrue(same < 50);
    }

    @Test
    public void sameSeedGivesSameOrder() {
        assertEquals(java.util.Arrays.toString(take(new QuoteOrder(100, new Random(5)), 250)),
                java.util.Arrays.toString(take(new QuoteOrder(100, new Random(5)), 250)));
    }

    @Test
    public void emptyGivesMinusOne() {
        QuoteOrder o = new QuoteOrder(0, new Random(1));
        assertEquals(-1, o.next());
        assertEquals(-1, o.next());
        assertEquals(-1, new QuoteOrder(-3, new Random(1)).next());
    }

    @Test
    public void singleQuoteRepeatsBecauseItMust() {
        QuoteOrder o = new QuoteOrder(1, new Random(1));
        for (int i = 0; i < 5; i++) assertEquals(0, o.next());
    }

    @Test
    public void twoQuotesAlternate() {
        for (long seed = 0; seed < 30; seed++) {
            int[] seq = take(new QuoteOrder(2, new Random(seed)), 40);
            for (int i = 1; i < seq.length; i++) assertNotEquals(seq[i - 1], seq[i]);
        }
    }
}
