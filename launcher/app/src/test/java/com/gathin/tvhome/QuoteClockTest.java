package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class QuoteClockTest {

    @Test
    public void alphaFadesInHoldsAndFadesOut() {
        assertEquals(0f, QuoteClock.alpha(0), 0f);
        assertEquals(0.5f, QuoteClock.alpha(400), 1e-6f);
        assertEquals(1f, QuoteClock.alpha(800), 0f);
        assertEquals(1f, QuoteClock.alpha(3000), 0f);
        assertEquals(1f, QuoteClock.alpha(5200), 0f);
        assertEquals(0.5f, QuoteClock.alpha(5600), 1e-6f);
        assertEquals(0f, QuoteClock.alpha(6000), 0f);
        assertEquals(0f, QuoteClock.alpha(12000), 0f);
    }

    @Test
    public void alphaIsMonotonicInEachFade() {
        float prev = 0f;
        for (long t = 0; t <= 800; t += 20) {
            float a = QuoteClock.alpha(t);
            assertTrue(a >= prev);
            prev = a;
        }
        prev = 1f;
        for (long t = 5200; t <= 6000; t += 20) {
            float a = QuoteClock.alpha(t);
            assertTrue(a <= prev);
            prev = a;
        }
    }

    @Test
    public void indexStepsOncePerPeriodAndWraps() {
        assertEquals(0, QuoteClock.index(0, 0, 100));
        assertEquals(0, QuoteClock.index(5999, 0, 100));
        assertEquals(1, QuoteClock.index(6000, 0, 100));
        assertEquals(99, QuoteClock.index(99 * 6000L, 0, 100));
        assertEquals(0, QuoteClock.index(100 * 6000L, 0, 100));
        assertEquals(1, QuoteClock.index(101 * 6000L + 17, 0, 100));
    }

    @Test
    public void startOffsetIsRespected() {
        assertEquals(42, QuoteClock.index(0, 42, 100));
        assertEquals(43, QuoteClock.index(6000, 42, 100));
        assertEquals(1, QuoteClock.index(3 * 6000L, 98, 100));
        assertEquals(0, QuoteClock.index(12345, 5, 0));
    }

    @Test
    public void everyQuoteComesBeforeARepeat() {
        boolean[] seen = new boolean[100];
        for (int k = 0; k < 100; k++) seen[QuoteClock.index(k * 6000L, 37, 100)] = true;
        for (boolean b : seen) assertTrue(b);
    }
}
