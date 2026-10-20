package com.gathin.roman;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MeanderTest {

    private static final float G = 6f;
    private static final float EPS = 0.001f;

    private static boolean touch(float[] p, int a, int b) {
        float ax1 = Math.min(p[2 * a], p[2 * a + 2]), ax2 = Math.max(p[2 * a], p[2 * a + 2]);
        float ay1 = Math.min(p[2 * a + 1], p[2 * a + 3]), ay2 = Math.max(p[2 * a + 1], p[2 * a + 3]);
        float bx1 = Math.min(p[2 * b], p[2 * b + 2]), bx2 = Math.max(p[2 * b], p[2 * b + 2]);
        float by1 = Math.min(p[2 * b + 1], p[2 * b + 3]), by2 = Math.max(p[2 * b + 1], p[2 * b + 3]);
        return ax1 <= bx2 && bx1 <= ax2 && ay1 <= by2 && by1 <= ay2;
    }

    @Test
    public void segmentsAreAxisAlignedAndPointsDiffer() {
        float[] p = Meander.polyline(10, 20, 200 * G, G);
        for (int i = 0; i + 3 < p.length; i += 2) {
            boolean horizontal = p[i + 1] == p[i + 3], vertical = p[i] == p[i + 2];
            assertTrue(horizontal || vertical);
            assertFalse(horizontal && vertical);
        }
    }

    @Test
    public void noNonAdjacentSegmentsTouch() {
        float[] p = Meander.polyline(0, 0, 200 * G, G);
        int segs = p.length / 2 - 1;
        for (int a = 0; a < segs; a++)
            for (int b = a + 2; b < segs; b++)
                assertFalse("segments " + a + " and " + b, touch(p, a, b));
    }

    @Test
    public void staysInsideTheBandAndWidth() {
        float left = 40, top = 12, width = 333;
        float[] p = Meander.polyline(left, top, width, G);
        for (int i = 0; i < p.length; i += 2) {
            assertTrue(p[i] >= left - EPS && p[i] <= left + width + EPS);
            assertTrue(p[i + 1] >= top - EPS && p[i + 1] <= top + 5 * G + EPS);
        }
        assertEquals(left, p[0], EPS);
        assertEquals(left + width, p[p.length - 2], EPS);
    }

    @Test
    public void moduleCountAndCentring() {
        float left = 0, width = 11.5f * 5 * G;   // 11 modules and a half-module of slack
        float[] p = Meander.polyline(left, 0, width, G);
        int tops = 0;
        float firstTopX = Float.MAX_VALUE, lastBottomX = -1;
        for (int i = 0; i + 3 < p.length; i += 2) {
            // Each module has exactly one segment along the top edge from its (0,0) to its (4,0).
            if (p[i + 1] == 0 && p[i + 3] == 0) {
                tops++;
                firstTopX = Math.min(firstTopX, Math.min(p[i], p[i + 2]));
            }
        }
        assertEquals(11, tops);
        float slack = width - 11 * 5 * G;
        assertEquals(slack / 2, firstTopX, EPS);
        // The last module ends at 5g from its origin; the lead-out covers the rest of the margin.
        for (int i = 0; i < p.length; i += 2) if (p[i + 1] == 5 * G) lastBottomX = Math.max(lastBottomX, p[i]);
        assertEquals(left + width, lastBottomX, EPS);
        assertEquals(slack / 2, p[2] - left, EPS);   // lead-in ends where the first module starts
        assertEquals(slack / 2, left + width - p[p.length - 4], EPS);
    }

    @Test
    public void narrowWidthIsOnlyTheFlatLine() {
        float[] p = Meander.polyline(5, 7, 4.9f * G, G);
        assertEquals(4, p.length);
        assertEquals(7 + 5 * G, p[1], EPS);
        assertEquals(p[1], p[3], EPS);
        assertEquals(5 + 4.9f * G, p[2], EPS);
    }
}
