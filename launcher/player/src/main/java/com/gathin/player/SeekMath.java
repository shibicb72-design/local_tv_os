package com.gathin.player;

/** Pure seek arithmetic: fixed steps and clamping to [0, duration]. */
final class SeekMath {

    static final long SHORT_STEP_MS = 10_000L;
    static final long LONG_STEP_MS = 30_000L;

    private SeekMath() {}

    /** Clamps to [0, duration]; an unknown duration (<= 0) only clamps at 0. */
    static long clamp(long positionMs, long durationMs) {
        if (positionMs < 0) return 0;
        if (durationMs > 0 && positionMs > durationMs) return durationMs;
        return positionMs;
    }

    /** The new position after moving by deltaMs (negative = back), clamped. */
    static long seek(long positionMs, long deltaMs, long durationMs) {
        return clamp(positionMs + deltaMs, durationMs);
    }
}
