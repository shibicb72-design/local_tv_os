package com.gathin.tvhome;

/** Where the glass highlight sits on a focused tile; pure so it can be unit tested. */
final class FocusGeometry {

    private FocusGeometry() {
    }

    /**
     * The glass rect {x, y, w, h} around a tile whose layout rect is given: the tile grown by {@code scale}
     * plus {@code pad} on every side, centred on the tile and moved up by {@code lift} (0 = the old look).
     */
    static float[] glassRect(float cardLeft, float cardTop, float cardW, float cardH,
                             float scale, float pad, float lift) {
        float w = cardW * scale + 2 * pad;
        float h = cardH * scale + 2 * pad;
        float cx = cardLeft + cardW / 2f;
        float cy = cardTop + cardH / 2f;
        return new float[]{cx - w / 2f, cy - h / 2f - lift, w, h};
    }
}
