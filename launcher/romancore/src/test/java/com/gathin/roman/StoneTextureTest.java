package com.gathin.roman;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StoneTextureTest {

    private static final String[] STONES = {"travertine", "marble"};

    private static short[][] render(String stone, int seed, int brightness, int vignette, int w, int h) {
        StoneTexture t = new StoneTexture(stone, seed, brightness, vignette, w, h);
        short[][] rows = new short[h][w];
        for (int y = 0; y < h; y++) t.renderRow(y, rows[y]);
        return rows;
    }

    private static double lin(double c) {
        c /= 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double lum(int r, int g, int b) {
        return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b);
    }

    /** Mean of the three 8-bit channels over a block. */
    private static double mean(short[][] px, int x0, int y0, int bw, int bh) {
        double sum = 0;
        for (int y = y0; y < y0 + bh; y++) {
            for (int x = x0; x < x0 + bw; x++) {
                short p = px[y][x];
                sum += StoneTexture.unpackR(p) + StoneTexture.unpackG(p) + StoneTexture.unpackB(p);
            }
        }
        return sum / (3.0 * bw * bh);
    }

    @Test
    public void sameArgumentsGiveIdenticalRows() {
        for (String stone : STONES) {
            short[][] a = render(stone, 7, 100, 70, 96, 54);
            short[][] b = render(stone, 7, 100, 70, 96, 54);
            for (int y = 0; y < 54; y++) assertArrayEquals(a[y], b[y]);
        }
    }

    @Test
    public void anotherSeedChangesMostPixels() {
        for (String stone : STONES) {
            short[][] a = render(stone, 7, 100, 70, 96, 54);
            short[][] b = render(stone, 8, 100, 70, 96, 54);
            int differ = 0;
            for (int y = 0; y < 54; y++) for (int x = 0; x < 96; x++) if (a[y][x] != b[y][x]) differ++;
            assertTrue(stone + " differ " + differ, differ > 96 * 54 / 2);
        }
    }

    @Test
    public void channelsStayWithinThePalette() {
        // Upper bound everywhere at the brightest setting; lower bound in the middle, where neither the
        // header shade nor the vignette applies (in the corners dithering may legitimately reach 0).
        int w = 96, h = 54;
        for (String stone : STONES) {
            short[][] px = render(stone, 7, 150, 70, w, h);
            short[][] mid = render(stone, 7, 100, 70, w, h);
            int lo = (int) (0x14 * 0.3);
            int hi = (int) (0xA3 * 1.5) + 8;   // brightest palette channel, plus one 5-bit step of dither
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    short p = px[y][x];
                    assertTrue(StoneTexture.unpackR(p) <= hi && StoneTexture.unpackG(p) <= hi && StoneTexture.unpackB(p) <= hi);
                }
            }
            for (int y = h * 4 / 10; y < h * 9 / 10; y++) {
                for (int x = w / 5; x < w * 4 / 5; x++) {
                    short p = mid[y][x];
                    assertTrue(StoneTexture.unpackR(p) >= lo && StoneTexture.unpackG(p) >= lo && StoneTexture.unpackB(p) >= lo);
                }
            }
        }
    }

    @Test
    public void flatGreyDithersBackToItself() {
        for (int level : new int[]{64, 128, 200}) {
            double r = 0, g = 0, b = 0;
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++) {
                    short p = (short) StoneTexture.ditherPack(level, level, level, x, y);
                    r += StoneTexture.unpackR(p);
                    g += StoneTexture.unpackG(p);
                    b += StoneTexture.unpackB(p);
                }
            }
            assertEquals(level, r / 16, 2);
            assertEquals(level, g / 16, 2);
            assertEquals(level, b / 16, 2);
        }
    }

    @Test
    public void ditherKeepsBlackAndWhite() {
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                assertEquals(0, StoneTexture.ditherPack(0, 0, 0, x, y));
                assertEquals(0xFFFF, StoneTexture.ditherPack(255, 255, 255, x, y));
            }
        }
    }

    @Test
    public void redIsInTheHighBits() {
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                assertEquals(0xF800, StoneTexture.ditherPack(255, 0, 0, x, y));
                assertEquals(0x07E0, StoneTexture.ditherPack(0, 255, 0, x, y));
                assertEquals(0x001F, StoneTexture.ditherPack(0, 0, 255, x, y));
            }
        }
    }

    @Test
    public void vignetteDarkensTheCornersButNotTheCentre() {
        // The stone itself varies from corner to corner, so compare each image with the same stone
        // without a vignette: only the vignette is left in the ratio.
        for (String stone : STONES) {
            short[][] with = render(stone, 7, 100, 70, 960, 540);
            short[][] without = render(stone, 7, 100, 0, 960, 540);
            double cornersWith = (mean(with, 0, 508, 32, 32) + mean(with, 928, 508, 32, 32)) / 2;
            double cornersWithout = (mean(without, 0, 508, 32, 32) + mean(without, 928, 508, 32, 32)) / 2;
            double centreWith = mean(with, 464, 254, 32, 32);
            double centreWithout = mean(without, 464, 254, 32, 32);
            assertTrue(stone + " corners", cornersWith / cornersWithout < 0.7);
            assertTrue(stone + " centre", centreWith / centreWithout > 0.95);
        }
    }

    @Test
    public void brightnessScalesTheStone() {
        for (String stone : STONES) {
            double m50 = mean(render(stone, 7, 50, 70, 96, 54), 0, 0, 96, 54);
            double m100 = mean(render(stone, 7, 100, 70, 96, 54), 0, 0, 96, 54);
            double m150 = mean(render(stone, 7, 150, 70, 96, 54), 0, 0, 96, 54);
            assertTrue(m50 < m100 && m100 < m150);
        }
    }

    @Test
    public void headerKeepsTheTextReadable() {
        for (String stone : STONES) {
            short[][] px = render(stone, 7, 100, 70, 960, 540);
            double brightest = 0, brightestLeft = 0;
            for (int y = 0; y < 108; y++) {
                for (int x = 0; x < 960; x++) {
                    short p = px[y][x];
                    double l = lum(StoneTexture.unpackR(p), StoneTexture.unpackG(p), StoneTexture.unpackB(p));
                    brightest = Math.max(brightest, l);
                    if (x < 480) brightestLeft = Math.max(brightestLeft, l);
                }
            }
            double ivory = lum(0xF3, 0xEB, 0xDD);
            double gold = lum(0xC9, 0xA2, 0x4B);
            assertTrue(stone + " ivory " + (ivory + 0.05) / (brightest + 0.05), (ivory + 0.05) / (brightest + 0.05) >= 4.5);
            assertTrue(stone + " gold " + (gold + 0.05) / (brightestLeft + 0.05), (gold + 0.05) / (brightestLeft + 0.05) >= 3.0);
        }
    }

    @Test
    public void travertineHasPores() {
        short[][] px = render("travertine", 7, 100, 0, 960, 540);
        int pit = RomanStyle.TRAVERTINE_PIT;
        // Pore colour plus 10%, plus one 5-bit step of dither; rows below the header shade, no vignette.
        double r = ((pit >> 16) & 0xFF) * 1.1 + 8, g = ((pit >> 8) & 0xFF) * 1.1 + 8, b = (pit & 0xFF) * 1.1 + 8;
        int dark = 0, total = 0;
        for (int y = 216; y < 540; y++) {
            for (int x = 0; x < 960; x++) {
                short p = px[y][x];
                total++;
                if (StoneTexture.unpackR(p) <= r && StoneTexture.unpackG(p) <= g && StoneTexture.unpackB(p) <= b) dark++;
            }
        }
        double percent = 100.0 * dark / total;
        assertTrue("pores " + percent + "%", percent >= 0.5 && percent <= 4.0);
    }

    @Test
    public void generatorUsesOnlyExactArithmetic() throws IOException {
        // Same pixels on the JVM and on ART: no function whose last bit may differ between them.
        Path file = Paths.get("src/main/java/com/gathin/roman/StoneTexture.java");
        if (!Files.exists(file)) file = Paths.get("romancore/src/main/java/com/gathin/roman/StoneTexture.java");
        String src = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        for (String banned : new String[]{"Math.sin", "Math.cos", "Math.pow", "Math.exp", "Math.random", "new Random"}) {
            assertFalse(banned, src.contains(banned));
        }
    }
}
