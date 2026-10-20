package com.gathin.tvhome;

/**
 * The numbers and strings behind the Roman screensaver ({@link RomanDream}); pure Java so they can be unit
 * tested.
 */
final class RomanDreamMath {

    /** Full component name for {@code settings put secure screensaver_components}. */
    static final String COMPONENT = "com.gathin.tvhome/.RomanDream";
    /** The stone is drawn this much larger than the screen, so panning never shows an edge. */
    static final float SCALE = 1.12f;
    /** One way across the pan; the animator reverses at the end. */
    static final long PAN_MS = 90_000L;

    /** Pan translations are snapped to this many px, so the wall is redrawn only when it really moves. */
    static final float PAN_STEP_PX = 0.5f;

    private RomanDreamMath() {
    }

    /** Largest translation (px) that keeps a view of {@code size} px, scaled by {@code scale}, covering the screen. */
    static float travel(float size, float scale) {
        return Math.max(0f, size * (scale - 1f) / 2f);
    }

    /** Translation for an animation fraction 0..1 (clamped): from {@code -travel} to {@code +travel}. */
    static float offset(float fraction, float travel) {
        float f = Math.min(1f, Math.max(0f, fraction));
        return (2f * f - 1f) * travel;
    }

    /** {@code value} rounded to a multiple of {@code step}; unchanged when {@code step <= 0}. */
    static float snap(float value, float step) {
        return step <= 0f ? value : Math.round(value / step) * step;
    }

    /** Fraction 0..1 of a ping-pong animation that goes 0 to 1 and back, one way taking {@code legMs}. */
    static float pingPong(long elapsedMs, long legMs) {
        if (legMs <= 0) return 0f;
        long t = Math.floorMod(elapsedMs, 2 * legMs);
        return t <= legMs ? (float) t / legMs : (float) (2 * legMs - t) / legMs;
    }

    /** "10:45" for 24-hour clocks (zero padded hour), "10:45" or "9:05" for 12-hour ones. */
    static String time(int hour, int minute, boolean is24) {
        int h = is24 ? hour : (hour % 12 == 0 ? 12 : hour % 12);
        return (is24 && h < 10 ? "0" : "") + h + ":" + (minute < 10 ? "0" : "") + minute;
    }

    /** "AM" or "PM" for a 12-hour clock, "" for a 24-hour one. */
    static String ampm(int hour, boolean is24) {
        return is24 ? "" : (hour < 12 ? "AM" : "PM");
    }
}
