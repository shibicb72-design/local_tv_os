package com.gathin.player;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class OverlayMathTest {

    @Test
    public void fillWidthIsProportional() {
        assertEquals(500, OverlayMath.fillWidth(30_000, 60_000, 1000));
        assertEquals(250, OverlayMath.fillWidth(15_000, 60_000, 1000));
    }

    @Test
    public void fillWidthClamps() {
        assertEquals(0, OverlayMath.fillWidth(-5, 60_000, 1000));
        assertEquals(1000, OverlayMath.fillWidth(90_000, 60_000, 1000));
    }

    @Test
    public void fillWidthUnknownDurationOrWidthIsZero() {
        assertEquals(0, OverlayMath.fillWidth(10_000, 0, 1000));
        assertEquals(0, OverlayMath.fillWidth(10_000, -1, 1000));
        assertEquals(0, OverlayMath.fillWidth(10_000, 60_000, 0));
    }

    @Test
    public void fillWidthDoesNotOverflowOnLongFiles() {
        long fourHours = 4L * 3600 * 1000;
        assertEquals(960, OverlayMath.fillWidth(fourHours / 2, fourHours, 1920));
    }

    @Test
    public void resumeLabelUsesTimeFormat() {
        assertEquals("Resume from 1:02:13", OverlayMath.resumeLabel(3_733_000L));
        assertEquals("Resume from 1:05", OverlayMath.resumeLabel(65_000L));
    }
}
