package com.gathin.roman;

import java.util.Arrays;

/**
 * The Greek key (meander) line of the Roman frieze (docs/plans/roman-theme-v2.md, section 1.5). Pure Java so
 * the geometry can be unit tested.
 */
public final class Meander {

    /** One module in grid units (x, y pairs; y down, origin top-left, the module is 5 wide and 5 tall). */
    private static final int[] UNIT = {0, 5, 0, 0, 4, 0, 4, 3, 2, 3, 2, 2, 3, 2, 3, 1, 1, 1, 1, 5, 5, 5};

    private Meander() {
    }

    /**
     * The line as x,y pairs: a flat lead-in at {@code y = top + 5g} from {@code left}, {@code floor(width /
     * 5g)} modules centred in the width, and a flat lead-out to {@code left + width}. One continuous line with
     * no crossings; when the width holds no whole module it is only the flat line.
     */
    public static float[] polyline(float left, float top, float width, float g) {
        float base = top + 5 * g;
        int n = (int) Math.floor(width / (5 * g));
        float[] out = new float[2 * (2 + n * (UNIT.length / 2 - 1) + 1) + 2];
        int len = 0;
        out[len++] = left;
        out[len++] = base;
        if (n > 0) {
            float x0 = left + (width - n * 5 * g) / 2;
            for (int m = 0; m < n; m++) {
                // The first point of a module is the last point of the one before; skip it.
                for (int k = m == 0 ? 0 : 1; k < UNIT.length / 2; k++) {
                    float x = x0 + (m * 5 + UNIT[2 * k]) * g;
                    float y = top + UNIT[2 * k + 1] * g;
                    if (x == out[len - 2] && y == out[len - 1]) continue;
                    out[len++] = x;
                    out[len++] = y;
                }
            }
        }
        float end = left + width;
        if (end != out[len - 2]) {
            out[len++] = end;
            out[len++] = base;
        }
        return Arrays.copyOf(out, len);
    }
}
