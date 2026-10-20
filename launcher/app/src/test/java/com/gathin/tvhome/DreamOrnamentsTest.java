package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DreamOrnamentsTest {

    private static final int[][] SCREENS = {{1920, 1080}, {1280, 720}};

    /** px per dp at a screen width (xhdpi at 1920x1080 is 2.0). */
    private static float dp(int w) {
        return w / 960f;
    }

    @Test
    public void thirteenLeavesPerSideAndFourBerries() {
        for (int[] s : SCREENS) {
            for (boolean right : new boolean[]{false, true}) {
                assertEquals(13, DreamOrnaments.spray(s[0], s[1], dp(s[0]), right).length);
                assertEquals(12, DreamOrnaments.sprayBerries(s[0], s[1], dp(s[0]), right).length);
            }
        }
    }

    @Test
    public void rightSprayMirrorsTheLeftOne() {
        for (int[] s : SCREENS) {
            float w = s[0], h = s[1], d = dp(s[0]);
            Laurel.Leaf[] l = DreamOrnaments.spray(w, h, d, false);
            Laurel.Leaf[] r = DreamOrnaments.spray(w, h, d, true);
            for (int i = 0; i < l.length; i++) {
                assertEquals(w - l[i].x, r[i].x, 1e-3f);
                assertEquals(l[i].y, r[i].y, 1e-3f);
                assertEquals(180f - l[i].angle, r[i].angle, 1e-3f);
                assertEquals(l[i].length, r[i].length, 1e-3f);
                assertEquals(l[i].width, r[i].width, 1e-3f);
            }
            float[] bl = DreamOrnaments.sprayBerries(w, h, d, false);
            float[] br = DreamOrnaments.sprayBerries(w, h, d, true);
            for (int i = 0; i < bl.length; i += 3) {
                assertEquals(w - bl[i], br[i], 1e-3f);
                assertEquals(bl[i + 1], br[i + 1], 1e-3f);
                assertEquals(bl[i + 2], br[i + 2], 1e-3f);
            }
        }
    }

    @Test
    public void leavesStayAtTheSidesAndInsideTheHeightBand() {
        for (int[] s : SCREENS) {
            float w = s[0], h = s[1], d = dp(s[0]);
            for (Laurel.Leaf l : DreamOrnaments.spray(w, h, d, false)) {
                assertTrue(l.x < 0.14f * w);
                assertTrue(l.y >= 0.14f * h && l.y <= 0.88f * h);
            }
            for (Laurel.Leaf l : DreamOrnaments.spray(w, h, d, true)) {
                assertTrue(l.x > 0.86f * w);
                assertTrue(l.y >= 0.14f * h && l.y <= 0.88f * h);
            }
            float[] b = DreamOrnaments.sprayBerries(w, h, d, false);
            for (int i = 0; i < b.length; i += 3) assertTrue(b[i] < 0.14f * w);
        }
    }

    @Test
    public void noLeafTouchesTheStandardOrTheTime() {
        for (int[] s : SCREENS) {
            float w = s[0], h = s[1], d = dp(s[0]);
            float[] st = DreamOrnaments.standard(w, h, d);
            float cx = w / 2f;
            // standard: crossbar width by spear tip .. fringe; time: 96 dp high, about half the width wide
            float sl = cx - st[DreamOrnaments.BAR_HALF], sr = cx + st[DreamOrnaments.BAR_HALF];
            float st0 = st[DreamOrnaments.POLE_TOP] - 16 * d, st1 = st[DreamOrnaments.CLOTH_B] + 7 * d;
            float tl = cx - 0.25f * w, tr = cx + 0.25f * w;
            float tt = 0.60f * h - 96 * d, tb = 0.60f * h + 24 * d;
            for (boolean right : new boolean[]{false, true}) {
                for (Laurel.Leaf l : DreamOrnaments.spray(w, h, d, right)) {
                    float m = l.length;   // the leaf reaches at most its length from its centre
                    assertTrue(!overlaps(l.x, l.y, m, sl, st0, sr, st1));
                    assertTrue(!overlaps(l.x, l.y, m, tl, tt, tr, tb));
                }
            }
        }
    }

    private static boolean overlaps(float x, float y, float m, float l, float t, float r, float b) {
        return x + m > l && x - m < r && y + m > t && y - m < b;
    }

    @Test
    public void standardClothIsCentredAndThePoleEndsAboveTheTime() {
        for (int[] s : SCREENS) {
            float w = s[0], h = s[1], d = dp(s[0]);
            float[] st = DreamOrnaments.standard(w, h, d);
            assertEquals(w / 2f, (st[DreamOrnaments.CLOTH_L] + st[DreamOrnaments.CLOTH_R]) / 2f, 1e-3f);
            assertEquals(180 * d, st[DreamOrnaments.CLOTH_R] - st[DreamOrnaments.CLOTH_L], 1e-3f);
            assertEquals(100 * d, st[DreamOrnaments.CLOTH_B] - st[DreamOrnaments.CLOTH_T], 1e-3f);
            assertEquals(0.05f * h, st[DreamOrnaments.POLE_TOP], 1e-3f);
            assertEquals(0.40f * h, st[DreamOrnaments.POLE_BOTTOM], 1e-3f);
            assertTrue(st[DreamOrnaments.BAR_Y] > st[DreamOrnaments.POLE_TOP]);
            assertTrue(st[DreamOrnaments.CLOTH_B] < st[DreamOrnaments.POLE_BOTTOM]);
            float timeTop = 0.60f * h - 96 * d * 0.75f;   // cap height of the 96 dp time
            assertTrue(st[DreamOrnaments.POLE_BOTTOM] < timeTop);
        }
    }

    @Test
    public void fulmenIsSymmetricAndInsideTheCloth() {
        for (int[] s : SCREENS) {
            float w = s[0], h = s[1], d = dp(s[0]);
            float[] st = DreamOrnaments.standard(w, h, d);
            float cx = w / 2f;
            float height = st[DreamOrnaments.CLOTH_B] - st[DreamOrnaments.CLOTH_T];
            float[] f = DreamOrnaments.fulmen(cx, st[DreamOrnaments.CLOTH_T] + 0.4f * height, 0.3f * height);
            assertTrue(f.length >= 20 && f.length % 2 == 0);
            for (int i = 0; i < f.length; i += 2) {
                assertTrue(f[i] > st[DreamOrnaments.CLOTH_L] && f[i] < st[DreamOrnaments.CLOTH_R]);
                assertTrue(f[i + 1] > st[DreamOrnaments.CLOTH_T] && f[i + 1] < st[DreamOrnaments.CLOTH_B]);
                boolean mirrored = false;
                for (int j = 0; j < f.length && !mirrored; j += 2) {
                    mirrored = Math.abs(f[j] - (2 * cx - f[i])) < 1e-3f && Math.abs(f[j + 1] - f[i + 1]) < 1e-3f;
                }
                assertTrue("point " + i / 2 + " has a mirror", mirrored);
            }
        }
    }
}
