package com.gathin.tvhome;

/**
 * Geometry of one fluted pilaster (docs/plans/roman-theme-v2.md, section 1.5). Pure Java so it can be tested.
 * Rects are {left, top, right, bottom}; all lengths in px, with {@code dp} the px per dp.
 */
final class Pilaster {

    static final int FLUTES = 5;

    final float[] abacus, echinus, astragal, shaft, torus, plinth;
    final float[] fluteX = new float[FLUTES];
    final float fluteTop, fluteBottom;

    private Pilaster(float[] abacus, float[] echinus, float[] astragal, float[] shaft, float[] torus,
                     float[] plinth, float fluteTop, float fluteBottom) {
        this.abacus = abacus;
        this.echinus = echinus;
        this.astragal = astragal;
        this.shaft = shaft;
        this.torus = torus;
        this.plinth = plinth;
        this.fluteTop = fluteTop;
        this.fluteBottom = fluteBottom;
    }

    /** Lays out a pilaster with shaft width {@code w}; null when it is shorter than 3w. */
    static Pilaster layout(float shaftLeft, float top, float bottom, float w, float dp) {
        if (bottom - top < 3 * w) return null;
        float cx = shaftLeft + w / 2;
        float shaftTop = top + 12 * dp, shaftBottom = bottom - 12 * dp;
        Pilaster p = new Pilaster(
                centred(cx, w + 8 * dp, top, top + 5 * dp),
                centred(cx, w + 4 * dp, top + 5 * dp, top + 11 * dp),
                centred(cx, w, top + 11 * dp, shaftTop),
                new float[]{shaftLeft, shaftTop, shaftLeft + w, shaftBottom},
                centred(cx, w + 4 * dp, shaftBottom, shaftBottom + 5 * dp),
                centred(cx, w + 8 * dp, shaftBottom + 5 * dp, bottom),
                shaftTop + 6 * dp, shaftBottom - 6 * dp);
        for (int i = 0; i < FLUTES; i++) p.fluteX[i] = shaftLeft + w * (i + 1) / 6;
        return p;
    }

    private static float[] centred(float cx, float width, float top, float bottom) {
        return new float[]{cx - width / 2, top, cx + width / 2, bottom};
    }
}
