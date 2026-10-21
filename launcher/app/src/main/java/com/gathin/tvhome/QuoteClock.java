package com.gathin.tvhome;

/** Timing of the quote line in the Roman screensaver: one quote per period, faded in and out. Pure Java. */
final class QuoteClock {

    static final long PERIOD_MS = 6000L;
    static final long FADE_MS = 800L;

    private QuoteClock() {
    }

    /** Index of the quote shown {@code t} ms after the dream started, for a list of {@code n} and a first index. */
    static int index(long t, int start, int n) {
        if (n <= 0) return 0;
        long step = Math.floorDiv(t, PERIOD_MS);
        return (int) Math.floorMod(start + step, (long) n);
    }

    /** Opacity 0..1: fade in, hold, fade out, within each period. */
    static float alpha(long t) {
        long m = Math.floorMod(t, PERIOD_MS);
        if (m < FADE_MS) return (float) m / FADE_MS;
        if (m > PERIOD_MS - FADE_MS) return (float) (PERIOD_MS - m) / FADE_MS;
        return 1f;
    }
}
