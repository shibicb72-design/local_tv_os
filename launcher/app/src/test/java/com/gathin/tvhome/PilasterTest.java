package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PilasterTest {

    private static final float DP = 2f, W = 56f, EPS = 1e-3f;

    private static Pilaster make() {
        return Pilaster.layout(28, 320, 1000, W, DP);
    }

    @Test
    public void capitalAndBaseAre12dpAndStackWithoutGaps() {
        Pilaster p = make();
        assertEquals(320, p.abacus[1], EPS);
        assertEquals(p.abacus[3], p.echinus[1], EPS);
        assertEquals(p.echinus[3], p.astragal[1], EPS);
        assertEquals(12 * DP, p.astragal[3] - p.abacus[1], EPS);
        assertEquals(p.astragal[3], p.shaft[1], EPS);
        assertEquals(p.shaft[3], p.torus[1], EPS);
        assertEquals(p.torus[3], p.plinth[1], EPS);
        assertEquals(12 * DP, p.plinth[3] - p.torus[1], EPS);
        assertEquals(1000, p.plinth[3], EPS);
    }

    @Test
    public void abacusAndPlinthAreWider8dpAndCentred() {
        Pilaster p = make();
        float cx = 28 + W / 2;
        for (float[] r : new float[][]{p.abacus, p.plinth}) {
            assertEquals(W + 8 * DP, r[2] - r[0], EPS);
            assertEquals(cx, (r[0] + r[2]) / 2, EPS);
        }
    }

    @Test
    public void flutesAreEvenAndInsideTheShaft() {
        Pilaster p = make();
        assertEquals(5, p.fluteX.length);
        for (int i = 0; i < 5; i++) {
            assertTrue(p.fluteX[i] > p.shaft[0] && p.fluteX[i] < p.shaft[2]);
            if (i > 0) assertEquals(W / 6, p.fluteX[i] - p.fluteX[i - 1], 1e-4f);
        }
        assertEquals(p.astragal[3] + 6 * DP, p.fluteTop, EPS);
        assertEquals(p.torus[1] - 6 * DP, p.fluteBottom, EPS);
    }

    @Test
    public void tooShortIsNullAndExactly3wIsNot() {
        assertNull(Pilaster.layout(28, 0, 2.9f * W, W, DP));
        assertNotNull(Pilaster.layout(28, 0, 3 * W, W, DP));
    }

    @Test
    public void rightPilasterMirrorsTheLeft() {
        float screen = 1920, left = 28;
        Pilaster l = Pilaster.layout(left, 320, 1000, W, DP);
        Pilaster r = Pilaster.layout(screen - left - W, 320, 1000, W, DP);
        assertEquals(screen - l.abacus[2], r.abacus[0], EPS);
        assertEquals(screen - l.shaft[0], r.shaft[2], EPS);
        for (int i = 0; i < 5; i++) assertEquals(screen - l.fluteX[i], r.fluteX[4 - i], EPS);
    }
}
