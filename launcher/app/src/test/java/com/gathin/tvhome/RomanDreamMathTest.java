package com.gathin.tvhome;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RomanDreamMathTest {

    @Test
    public void travelKeepsTheScreenCovered() {
        assertEquals(115.2f, RomanDreamMath.travel(1920f, 1.12f), 0.01f);
        assertEquals(0f, RomanDreamMath.travel(1920f, 1f), 0f);
        assertEquals(0f, RomanDreamMath.travel(1920f, 0.5f), 0f);
    }

    @Test
    public void snapRoundsToTheStepAndIgnoresNoStep() {
        assertEquals(1.5f, RomanDreamMath.snap(1.6f, 0.5f), 1e-6f);
        assertEquals(-1.0f, RomanDreamMath.snap(-1.2f, 0.5f), 1e-6f);
        assertEquals(0f, RomanDreamMath.snap(0.2f, 0.5f), 1e-6f);
        assertEquals(1.23f, RomanDreamMath.snap(1.23f, 0f), 0f);
        // one 90 s leg across +-115 px changes the snapped value ~460 times, not ~5400 (60 fps)
        int changes = 0;
        float prev = Float.NaN;
        for (int f = 0; f <= 5400; f++) {
            float v = RomanDreamMath.snap(RomanDreamMath.offset(f / 5400f, 115.2f), RomanDreamMath.PAN_STEP_PX);
            if (v != prev) changes++;
            prev = v;
        }
        assertTrue(changes < 500);
    }

    @Test
    public void offsetRunsFromMinusToPlusTravelAndClamps() {
        assertEquals(-100f, RomanDreamMath.offset(0f, 100f), 1e-4f);
        assertEquals(0f, RomanDreamMath.offset(0.5f, 100f), 1e-4f);
        assertEquals(100f, RomanDreamMath.offset(1f, 100f), 1e-4f);
        assertEquals(-100f, RomanDreamMath.offset(-3f, 100f), 1e-4f);
        assertEquals(100f, RomanDreamMath.offset(7f, 100f), 1e-4f);
    }

    @Test
    public void pingPongBouncesBetweenZeroAndOne() {
        long leg = 1000;
        assertEquals(0f, RomanDreamMath.pingPong(0, leg), 1e-6f);
        assertEquals(0.5f, RomanDreamMath.pingPong(500, leg), 1e-6f);
        assertEquals(1f, RomanDreamMath.pingPong(1000, leg), 1e-6f);
        assertEquals(0.25f, RomanDreamMath.pingPong(1750, leg), 1e-6f);
        assertEquals(0f, RomanDreamMath.pingPong(2000, leg), 1e-6f);
        assertEquals(0.5f, RomanDreamMath.pingPong(2500, leg), 1e-6f);
        assertEquals(0f, RomanDreamMath.pingPong(5000, 0), 0f);
        for (long t = -3000; t < 6000; t += 137) {
            float f = RomanDreamMath.pingPong(t, leg);
            assertTrue(f >= 0f && f <= 1f);
        }
    }

    @Test
    public void time24Hour() {
        assertEquals("00:05", RomanDreamMath.time(0, 5, true));
        assertEquals("09:30", RomanDreamMath.time(9, 30, true));
        assertEquals("23:59", RomanDreamMath.time(23, 59, true));
        assertEquals("", RomanDreamMath.ampm(23, true));
    }

    @Test
    public void time12Hour() {
        assertEquals("12:05", RomanDreamMath.time(0, 5, false));
        assertEquals("12:00", RomanDreamMath.time(12, 0, false));
        assertEquals("9:05", RomanDreamMath.time(9, 5, false));
        assertEquals("10:45", RomanDreamMath.time(22, 45, false));
        assertEquals("AM", RomanDreamMath.ampm(0, false));
        assertEquals("AM", RomanDreamMath.ampm(11, false));
        assertEquals("PM", RomanDreamMath.ampm(12, false));
        assertEquals("PM", RomanDreamMath.ampm(23, false));
    }

    @Test
    public void dateLineIsInscribed() {
        assertEquals("XX OCTOBRIS MMXXVI", Roman.inscribe(Roman.date(2026, 10, 20), 40));
    }

    @Test
    public void legionLegendIsInscribedUnchanged() {
        assertEquals("LEG XII FVLMINATA", RomanDreamMath.LEGION);
        assertEquals(RomanDreamMath.LEGION, Roman.inscribe(RomanDreamMath.LEGION, 24));
        // the longer alternative still fits the inscription limit
        assertEquals("LEG XXII PRIMIGENIA", Roman.inscribe("LEG XXII PRIMIGENIA", 24));
    }

    @Test
    public void gregorianDateIsLocalized() {
        assertEquals("20 October 2026", RomanDreamMath.gregorian(2026, 10, 20, Locale.UK));
        assertEquals("October 20, 2026", RomanDreamMath.gregorian(2026, 10, 20, Locale.US));
    }

    @Test
    public void fitTextSizeFillsTheWidthWithinTheClamp() {
        assertEquals(10f, RomanDreamMath.fitTextSize(100f, 10f, 1f, 50f), 1e-4f);
        assertEquals(5f, RomanDreamMath.fitTextSize(10f, 10f, 5f, 50f), 0f);      // too wide: minimum
        assertEquals(50f, RomanDreamMath.fitTextSize(1000f, 10f, 1f, 50f), 0f);   // lots of room: maximum
        assertEquals(50f, RomanDreamMath.fitTextSize(100f, 0f, 1f, 50f), 0f);     // nothing to measure
        for (float at1 = 1f; at1 < 40f; at1 += 0.7f) {
            float size = RomanDreamMath.fitTextSize(160f, at1, 4f, 30f);
            assertTrue(size >= 4f && size <= 30f);
            if (size > 4f) assertTrue(size * at1 <= 160f + 1e-3f);
        }
    }

    @Test
    public void componentNameMatchesTheManifestEntry() throws IOException {
        assertEquals("com.gathin.tvhome/.RomanDream", RomanDreamMath.COMPONENT);
        Path file = Paths.get("src/main/AndroidManifest.xml");
        if (!Files.exists(file)) file = Paths.get("app/src/main/AndroidManifest.xml");
        String xml = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        int at = xml.indexOf("android:name=\".RomanDream\"");
        assertTrue("RomanDream is declared", at >= 0);
        int end = xml.indexOf("</service>", at);
        String entry = xml.substring(at, end);
        assertTrue(entry.contains("android:exported=\"true\""));
        assertTrue(entry.contains("android:permission=\"android.permission.BIND_DREAM_SERVICE\""));
        assertTrue(entry.contains("android.service.dreams.DreamService"));
    }
}
