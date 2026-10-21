package com.gathin.tvhome;

import java.util.Random;

/**
 * Shuffled order of the screensaver quotes: every index once per pass in random order, a fresh shuffle after each
 * full pass, and never the same index twice in a row (also across the pass boundary). Pure Java; the
 * {@link Random} is injected so tests can seed it.
 */
final class QuoteOrder {

    private final Random random;
    private final int[] order;
    private int pos;
    private int last = -1;

    QuoteOrder(int size, Random random) {
        this.random = random;
        order = new int[Math.max(0, size)];
        shuffle();
    }

    /** The next quote index, or -1 when there are no quotes. */
    int next() {
        if (order.length == 0) return -1;
        if (pos >= order.length) shuffle();
        last = order[pos++];
        return last;
    }

    private void shuffle() {
        int n = order.length;
        for (int i = 0; i < n; i++) order[i] = i;
        for (int i = n - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int t = order[i];
            order[i] = order[j];
            order[j] = t;
        }
        // A new pass must not start with the quote that ended the previous one.
        if (n > 1 && order[0] == last) {
            int j = 1 + random.nextInt(n - 1);
            int t = order[0];
            order[0] = order[j];
            order[j] = t;
        }
        pos = 0;
    }
}
