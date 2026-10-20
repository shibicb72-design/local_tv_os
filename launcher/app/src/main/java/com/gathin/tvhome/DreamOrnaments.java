package com.gathin.tvhome;

import com.gathin.roman.Laurel;

/**
 * The shapes of RomanDream v2 (docs/plans/roman-theme-v2.md, section 9.1): the two laurel sprays, the legion
 * standard and its thunderbolt. Pure Java with plain floats (no RectF, no Path) so the geometry can be unit
 * tested on the JVM; it runs once per layout, so Math.sin and Math.cos are fine here.
 */
final class DreamOrnaments {

    static final int LEAVES_PER_SPRAY = 13;
    static final int BERRIES_PER_SPRAY = 4;
    /** Leaf shape scale against the crest laurel (R3). */
    static final float LEAF_SCALE = 1.4f;

    /** Indexes into the array returned by {@link #standard}. */
    static final int POLE_TOP = 0, POLE_BOTTOM = 1, BAR_Y = 2, BAR_HALF = 3,
            CLOTH_L = 4, CLOTH_T = 5, CLOTH_R = 6, CLOTH_B = 7;

    private static final float STEM_TOP = 0.16f;
    private static final float STEM_BOTTOM = 0.86f;
    private static final float STEM_X = 0.08f;
    private static final float STEM_BULGE = 0.025f;
    /** Berry positions along the stem, as leaf-index fractions (between leaves 2/3, 5/6, 8/9, 10/11). */
    private static final float[] BERRY_AT = {2.5f, 5.5f, 8.5f, 10.5f};

    /** Right half of the thunderbolt outline, top to bottom, in units of its size (x to the right, y down). */
    private static final float[] FULMEN_HALF = {
            0.00f, -0.90f, 0.10f, -0.45f, 0.35f, -0.55f, 0.30f, -0.25f, 0.75f, -0.35f, 0.45f, -0.05f,
            0.80f, 0.10f, 0.38f, 0.12f, 0.50f, 0.45f, 0.15f, 0.30f, 0.08f, 0.80f, 0.00f, 0.90f};

    private DreamOrnaments() {
    }

    /** Stem of the left spray at fraction {@code s} (0 top .. 1 bottom); it bulges gently towards the centre. */
    private static float stemX(float w, float s) {
        return w * (STEM_X + STEM_BULGE * (float) Math.sin(Math.PI * s));
    }

    private static float stemY(float h, float s) {
        return h * (STEM_TOP + (STEM_BOTTOM - STEM_TOP) * s);
    }

    /**
     * The 13 leaves of one spray, ordered from the top; {@code right} mirrors the left spray about w/2, leaf for
     * leaf. {@code dp} is px per dp. Leaves alternate sides of the stem, point up the stem and lean 30 degrees out.
     */
    static Laurel.Leaf[] spray(float w, float h, float dp, boolean right) {
        Laurel.Leaf[] out = new Laurel.Leaf[LEAVES_PER_SPRAY];
        float k = dp * LEAF_SCALE;
        int last = LEAVES_PER_SPRAY - 1;
        for (int i = 0; i < LEAVES_PER_SPRAY; i++) {
            float s = (float) i / last;
            float dx = w * STEM_BULGE * (float) Math.PI * (float) Math.cos(Math.PI * s);   // per unit s
            float dy = h * (STEM_BOTTOM - STEM_TOP);
            float side = i % 2 == 0 ? 1f : -1f;   // +1 outward (to the screen edge), -1 inward
            // Direction up the stem, then tilted 30 degrees towards the leaf's own side (screen y points down).
            double up = Math.toDegrees(Math.atan2(-dy, -dx));
            float axis = (float) (up + side * 30.0);
            float x = stemX(w, s) - side * 4 * k;
            float y = stemY(h, s);
            float length = 10 * k * (1 - 0.3f * i / last);
            if (right) {
                out[i] = new Laurel.Leaf(w - x, y, 180f - axis, length, 0.4f * length);
            } else {
                out[i] = new Laurel.Leaf(x, y, axis, length, 0.4f * length);
            }
        }
        return out;
    }

    /** The berries of one spray as x, y, radius triples (4 of them). */
    static float[] sprayBerries(float w, float h, float dp, boolean right) {
        float[] out = new float[BERRIES_PER_SPRAY * 3];
        for (int j = 0; j < BERRIES_PER_SPRAY; j++) {
            float s = BERRY_AT[j] / (LEAVES_PER_SPRAY - 1);
            float x = stemX(w, s);
            out[3 * j] = right ? w - x : x;
            out[3 * j + 1] = stemY(h, s);
            out[3 * j + 2] = 1.3f * dp * LEAF_SCALE;
        }
        return out;
    }

    /**
     * The thunderbolt as a closed polyline of x, y pairs, symmetric about {@code cx}, at most {@code 0.8 * size}
     * wide to each side and {@code 0.9 * size} high above and below {@code cy}.
     */
    static float[] fulmen(float cx, float cy, float size) {
        int n = FULMEN_HALF.length / 2;
        float[] out = new float[(n + n - 2) * 2];
        int o = 0;
        for (int i = 0; i < n; i++) {   // right half, top to bottom
            out[o++] = cx + FULMEN_HALF[2 * i] * size;
            out[o++] = cy + FULMEN_HALF[2 * i + 1] * size;
        }
        for (int i = n - 2; i >= 1; i--) {   // left half, bottom to top
            out[o++] = cx - FULMEN_HALF[2 * i] * size;
            out[o++] = cy + FULMEN_HALF[2 * i + 1] * size;
        }
        return out;
    }

    /**
     * The standard on the centre line: {poleTop, poleBottom, barY, barHalf, clothL, clothT, clothR, clothB} in
     * px (index constants above). Pole from 5% to 40% of the height, crossbar 200 dp wide, cloth 180x100 dp
     * hanging from the bar and centred on w/2.
     */
    static float[] standard(float w, float h, float dp) {
        float poleTop = 0.05f * h;
        float barY = poleTop + 24 * dp;
        float clothT = barY + 4 * dp;
        return new float[]{poleTop, 0.40f * h, barY, 100 * dp,
                w / 2f - 90 * dp, clothT, w / 2f + 90 * dp, clothT + 100 * dp};
    }
}
