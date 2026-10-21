package com.gathin.player;

/** Decode size for a photo: fit inside a box, keep the aspect, never enlarge. Pure Java. */
final class SlideFit {

    private SlideFit() {
    }

    /** @return {width, height}, each at least 1 */
    static int[] fit(int srcW, int srcH, int maxW, int maxH) {
        if (srcW <= 0 || srcH <= 0) return new int[]{Math.max(1, maxW), Math.max(1, maxH)};
        if (srcW <= maxW && srcH <= maxH) return new int[]{srcW, srcH};
        double scale = Math.min((double) maxW / srcW, (double) maxH / srcH);
        return new int[]{Math.max(1, (int) Math.round(srcW * scale)), Math.max(1, (int) Math.round(srcH * scale))};
    }
}
