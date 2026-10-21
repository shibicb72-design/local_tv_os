package com.gathin.player;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SlideTimerTest {

    @Test
    public void startsPausedAndNeverDue() {
        SlideTimer t = new SlideTimer(8000);
        assertFalse(t.isRunning());
        assertEquals(-1, t.remaining(1000));
        assertFalse(t.isDue(1_000_000));
    }

    @Test
    public void dueAfterTheInterval() {
        SlideTimer t = new SlideTimer(8000);
        t.resume(1000);
        assertTrue(t.isRunning());
        assertEquals(8000, t.remaining(1000));
        assertFalse(t.isDue(8999));
        assertTrue(t.isDue(9000));
        assertEquals(0, t.remaining(20_000));
    }

    @Test
    public void restartGivesTheFullIntervalAgain() {
        SlideTimer t = new SlideTimer(8000);
        t.resume(0);
        t.restart(5000);
        assertFalse(t.isDue(12_999));
        assertTrue(t.isDue(13_000));
    }

    @Test
    public void pauseKeepsTheTimeLeftAndResumeContinuesFromIt() {
        SlideTimer t = new SlideTimer(8000);
        t.resume(0);
        t.pause(3000);
        assertFalse(t.isRunning());
        assertFalse(t.isDue(100_000));
        t.resume(50_000);
        assertEquals(5000, t.remaining(50_000));
        assertTrue(t.isDue(55_000));
    }

    @Test
    public void restartWhilePausedStaysPausedWithAFullInterval() {
        SlideTimer t = new SlideTimer(8000);
        t.resume(0);
        t.pause(3000);
        t.restart(4000);
        assertFalse(t.isRunning());
        t.resume(10_000);
        assertEquals(8000, t.remaining(10_000));
    }

    @Test
    public void pauseAndResumeAreIdempotent() {
        SlideTimer t = new SlideTimer(8000);
        t.pause(0);
        t.resume(0);
        t.resume(4000);
        assertEquals(4000, t.remaining(4000));
        t.pause(5000);
        t.pause(7000);
        t.resume(9000);
        assertEquals(3000, t.remaining(9000));
    }

    @Test
    public void fitKeepsAspectAndNeverEnlarges() {
        assertArrayEquals(new int[]{1920, 1080}, SlideFit.fit(4000, 2250, 1920, 1080));
        assertArrayEquals(new int[]{810, 1080}, SlideFit.fit(3000, 4000, 1920, 1080));
        assertArrayEquals(new int[]{800, 600}, SlideFit.fit(800, 600, 1920, 1080));
        assertArrayEquals(new int[]{1920, 1080}, SlideFit.fit(0, 0, 1920, 1080));
        assertArrayEquals(new int[]{1920, 1}, SlideFit.fit(20000, 10, 1920, 1080));
    }
}
