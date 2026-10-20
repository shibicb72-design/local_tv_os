package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LaurelTest {

    private static final float CX = 960, CY = 296, DP = 2f, R = Laurel.STEM_DP * DP;

    private static float norm(double deg) {
        double d = deg % 360;
        return (float) (d < 0 ? d + 360 : d);
    }

    @Test
    public void eighteenLeavesMirrored() {
        Laurel.Leaf[] l = Laurel.leaves(CX, CY, R, DP);
        assertEquals(18, l.length);
        for (int i = 0; i < 9; i++) {
            Laurel.Leaf a = l[i], b = l[9 + i];
            assertEquals(a.x, 2 * CX - b.x, 1e-3);
            assertEquals(a.y, b.y, 1e-3);
            assertEquals(norm(a.angle), norm(180 - b.angle), 1e-3);
            assertEquals(a.length, b.length, 1e-6);
        }
    }

    @Test
    public void topStaysOpenAndTipsStayClose() {
        for (Laurel.Leaf l : Laurel.leaves(CX, CY, R, DP)) {
            float pos = norm(Math.toDegrees(Math.atan2(l.y - CY, l.x - CX)));
            assertTrue("leaf at " + pos, pos <= 255 || pos >= 285);
            double a = Math.toRadians(l.angle);
            double tx = l.x + Math.cos(a) * l.length / 2, ty = l.y + Math.sin(a) * l.length / 2;
            assertTrue(Math.hypot(tx - CX, ty - CY) <= R + 8 * DP + 1e-3);
        }
    }

    @Test
    public void lengthsShrinkTowardTheTop() {
        Laurel.Leaf[] l = Laurel.leaves(CX, CY, R, DP);
        for (int i = 1; i < 9; i++) assertTrue(l[i].length < l[i - 1].length);
        assertEquals(10 * DP, l[0].length, 1e-4);
        assertEquals(0.4f * l[3].length, l[3].width, 1e-4);
    }

    @Test
    public void sixBerriesMirrored() {
        float[] b = Laurel.berries(CX, CY, R, DP);
        assertEquals(18, b.length);
        for (int j = 0; j < 3; j++) {
            assertEquals(b[3 * j], 2 * CX - b[9 + 3 * j], 1e-3);
            assertEquals(b[3 * j + 1], b[9 + 3 * j + 1], 1e-3);
            assertEquals(1.3f * DP, b[3 * j + 2], 1e-6);
        }
    }
}
