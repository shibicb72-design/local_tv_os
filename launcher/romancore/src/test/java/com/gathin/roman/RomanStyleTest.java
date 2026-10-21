package com.gathin.roman;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RomanStyleTest {

    private static RomanStyle of(String style, String stone, int brightness, int seed, int vignette) {
        return RomanStyle.of(style, stone, brightness, seed, vignette, true, true, true, "SALVE", 0);
    }

    @Test
    public void dreamColoursAreOpaqueAndRed() {
        for (int c : new int[]{RomanStyle.ROMAN_RED, RomanStyle.CRIMSON, RomanStyle.PORPHYRY,
                RomanStyle.PORPHYRY_DEEP}) {
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            assertEquals(0xFF, c >>> 24);
            assertTrue(r > g && r > b);
        }
        assertTrue(sum(RomanStyle.PORPHYRY_DEEP) < sum(RomanStyle.PORPHYRY));
    }

    private static int sum(int c) {
        return ((c >> 16) & 0xFF) + ((c >> 8) & 0xFF) + (c & 0xFF);
    }

    @Test
    public void defaultsIsTravertineWithTheDefaultGold() {
        RomanStyle d = RomanStyle.defaults();
        assertNotNull(d);
        assertEquals(RomanStyle.STONE_TRAVERTINE, d.stone);
        assertEquals(100, d.brightness);
        assertEquals(7, d.seed);
        assertEquals(70, d.vignette);
        assertTrue(d.meander && d.pilasters && d.crest);
        assertEquals("", d.inscription);
        assertEquals(0xFFC9A24B, d.gold);
        assertEquals(0xFFDCC38A, d.goldLight);
        assertEquals(0xFF896E33, d.goldDeep);
    }

    @Test
    public void goldRampOfTheDefaultGold() {
        assertArrayEquals(new int[]{0xFFDCC38A, 0xFFC9A24B, 0xFF896E33}, RomanStyle.goldRamp(0xFFC9A24B));
    }

    @Test
    public void goldRampIsAlwaysOpaque() {
        for (int c : new int[]{0x00000000, 0x80FFFFFF, 0x00C9A24B, 0xFF123456}) {
            for (int v : RomanStyle.goldRamp(c)) assertEquals(0xFF, v >>> 24);
        }
        assertArrayEquals(new int[]{0xFFFFFFFF, 0xFFFFFFFF, 0xFFADADAD}, RomanStyle.goldRamp(0xFFFFFFFF));
    }

    @Test
    public void onlyRomanSwitchesTheStyleOn() {
        assertNull(of("", "travertine", 100, 7, 70));
        assertNull(of(null, "travertine", 100, 7, 70));
        assertNull(of("greek", "travertine", 100, 7, 70));
        assertNotNull(of(" Roman ", "travertine", 100, 7, 70));
        assertNotNull(of("ROMAN", "travertine", 100, 7, 70));
    }

    @Test
    public void stoneNameIsNormalised() {
        assertEquals("marble", of("roman", "MARBLE", 100, 7, 70).stone);
        assertEquals("travertine", of("roman", "granite", 100, 7, 70).stone);
        assertEquals("travertine", of("roman", null, 100, 7, 70).stone);
        assertEquals("none", of("roman", "none", 100, 7, 70).stone);
    }

    @Test
    public void goldComesFromTheAccent() {
        RomanStyle def = of("roman", "travertine", 100, 7, 70);
        assertEquals(0xFFC9A24B, def.gold);
        assertEquals(0xFFDCC38A, def.goldLight);
        assertEquals(0xFF896E33, def.goldDeep);
        RomanStyle red = RomanStyle.of("roman", "travertine", 100, 7, 70, true, true, true, "", 0xFFFF0000);
        assertEquals(0xFFFF0000, red.gold);
    }

    @Test
    public void inscriptionIsCarvedAndCut() {
        assertEquals("SALVE", RomanStyle.of("roman", "", 100, 7, 70, true, true, true, "salve", 0).inscription);
        assertEquals("AVGVSTVS", RomanStyle.of("roman", "", 100, 7, 70, true, true, true, "Augustus Caesar", 0).inscription);
    }

    @Test
    public void stoneKeyChangesWithEveryInput() {
        String base = of("roman", "travertine", 100, 7, 70).stoneKey();
        assertEquals(base, of("roman", "travertine", 100, 7, 70).stoneKey());
        assertNotEquals(base, of("roman", "marble", 100, 7, 70).stoneKey());
        assertNotEquals(base, of("roman", "travertine", 101, 7, 70).stoneKey());
        assertNotEquals(base, of("roman", "travertine", 100, 8, 70).stoneKey());
        assertNotEquals(base, of("roman", "travertine", 100, 7, 71).stoneKey());
    }
}
