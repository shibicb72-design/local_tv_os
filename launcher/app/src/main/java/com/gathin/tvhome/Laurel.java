package com.gathin.tvhome;

/**
 * The laurel wreath of the crest (docs/plans/roman-theme-v2.md, section 1.5): two branches of 9 leaves and 3
 * berries each around a stem circle, open at the top. Pure Java; only runs once per layout, so Math.sin and
 * Math.cos are fine here.
 */
final class Laurel {

    static final int LEAVES_PER_SIDE = 9;
    /** Stem radius in dp. */
    static final float STEM_DP = 30f;
    /** First and last position angle of the left branch, in degrees (0 = +x, 90 = down). */
    static final float START_DEG = 100f;
    static final float END_DEG = 250f;

    /** One leaf: its centre, axis angle in degrees (screen coordinates), length and width. */
    static final class Leaf {
        final float x, y, angle, length, width;

        Leaf(float x, float y, float angle, float length, float width) {
            this.x = x;
            this.y = y;
            this.angle = angle;
            this.length = length;
            this.width = width;
        }
    }

    private Laurel() {
    }

    /** Position angle of leaf {@code i} on the left branch. */
    static float positionDeg(int i) {
        return START_DEG + i * 150f / 8.5f;
    }

    /**
     * The 18 leaves, the 9 of the left branch first, then their mirror images about {@code cx}. {@code r} is
     * the stem radius in px and {@code dp} the px per dp (leaf sizes and offsets scale with it).
     */
    static Leaf[] leaves(float cx, float cy, float r, float dp) {
        Leaf[] out = new Leaf[2 * LEAVES_PER_SIDE];
        for (int i = 0; i < LEAVES_PER_SIDE; i++) {
            float theta = positionDeg(i);
            float rad = i % 2 == 0 ? r + 3 * dp : r - 3 * dp;
            double t = Math.toRadians(theta);
            float x = cx + rad * (float) Math.cos(t);
            float y = cy + rad * (float) Math.sin(t);
            // Tangent towards the top, tilted 30 degrees out towards the branch's own side.
            float axis = theta + 60f;
            float length = 10 * dp * (1 - 0.3f * i / 8);
            out[i] = new Leaf(x, y, axis, length, 0.4f * length);
            out[LEAVES_PER_SIDE + i] = new Leaf(2 * cx - x, y, 180f - axis, length, 0.4f * length);
        }
        return out;
    }

    /** Berries as x, y, radius triples: 3 on the left, then their mirror images (6 in all). */
    static float[] berries(float cx, float cy, float r, float dp) {
        float[] out = new float[6 * 3];
        float rad = r - 5 * dp;
        for (int j = 0; j < 3; j++) {
            // Between leaves 2/3, 4/5 and 6/7.
            double t = Math.toRadians((positionDeg(2 + 2 * j) + positionDeg(3 + 2 * j)) / 2);
            float x = cx + rad * (float) Math.cos(t);
            float y = cy + rad * (float) Math.sin(t);
            out[3 * j] = x;
            out[3 * j + 1] = y;
            out[3 * j + 2] = 1.3f * dp;
            out[9 + 3 * j] = 2 * cx - x;
            out[9 + 3 * j + 1] = y;
            out[9 + 3 * j + 2] = 1.3f * dp;
        }
        return out;
    }
}
