package com.gathin.player;

/** Pure arithmetic and text for the overlay and the resume prompt. */
final class OverlayMath {

    private OverlayMath() {}

    /** Filled width in px of a progress bar: 0 when the duration is unknown, clamped to [0, widthPx]. */
    static int fillWidth(long positionMs, long durationMs, int widthPx) {
        if (durationMs <= 0 || widthPx <= 0 || positionMs <= 0) return 0;
        if (positionMs >= durationMs) return widthPx;
        return (int) ((long) widthPx * positionMs / durationMs);
    }

    /** Label of the default button of the resume prompt. */
    static String resumeLabel(long positionMs) {
        return "Resume from " + TimeFormat.format(positionMs);
    }
}
