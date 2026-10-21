package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FocusGeometryTest {

    /** The formula MainActivity.onTileFocused used before R5 (Rect.exactCenterX/Y = (a + b) * 0.5). */
    private static float[] reference(int left, int top, int w, int h, float scale, float pad) {
        float gw = w * scale + 2 * pad;
        float gh = h * scale + 2 * pad;
        float cx = (left + (left + w)) * 0.5f;
        float cy = (top + (top + h)) * 0.5f;
        return new float[]{cx - gw / 2f, cy - gh / 2f, gw, gh};
    }

    @Test
    public void liftZeroMatchesOldFormula() {
        int[][] cards = {{144, 692, 360, 202}, {72, 31, 301, 169}, {501, -17, 80, 45}, {0, 0, 1, 1}};
        for (int[] c : cards) {
            float[] want = reference(c[0], c[1], c[2], c[3], AppAdapter.FOCUS_SCALE, 12f);
            float[] got = FocusGeometry.glassRect(c[0], c[1], c[2], c[3], AppAdapter.FOCUS_SCALE, 12f, 0f);
            for (int i = 0; i < 4; i++) assertEquals(want[i], got[i], 1e-4f);
        }
    }

    @Test
    public void liftMovesUpOnly() {
        float[] a = FocusGeometry.glassRect(144, 692, 360, 202, 1.10f, 12f, 0f);
        float[] b = FocusGeometry.glassRect(144, 692, 360, 202, 1.10f, 12f, 8f);
        assertEquals(a[0], b[0], 1e-4f);
        assertEquals(a[1] - 8f, b[1], 1e-4f);
        assertEquals(a[2], b[2], 1e-4f);
        assertEquals(a[3], b[3], 1e-4f);
    }

    @Test
    public void widthIsScaledCardPlusPad() {
        float dp = 2f;
        float[] r = FocusGeometry.glassRect(0, 0, 180 * dp, 101 * dp, 1.10f, 6 * dp, 0f);
        assertEquals(180 * dp * 1.1f + 12 * dp, r[2], 1e-3f);
    }
}
