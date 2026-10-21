package com.gathin.player;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class VideoFitTest {

    @Test
    public void sameAspectFillsTheContainer() {
        assertArrayEquals(new int[]{1920, 1080}, VideoFit.fit(1280, 720, 1f, 1920, 1080));
    }

    @Test
    public void widerVideoIsLetterboxed() {
        assertArrayEquals(new int[]{1920, 810}, VideoFit.fit(2560, 1080, 1f, 1920, 1080));
    }

    @Test
    public void tallerVideoIsPillarboxed() {
        assertArrayEquals(new int[]{810, 1080}, VideoFit.fit(1080, 1440, 1f, 1920, 1080));
    }

    @Test
    public void pixelRatioWidensTheDisplayAspect() {
        // 720x576 PAL 16:9 anamorphic: 720 * 1.4222 / 576 = 16:9
        assertArrayEquals(new int[]{1920, 1080}, VideoFit.fit(720, 576, 64f / 45f, 1920, 1080));
    }

    @Test
    public void unknownVideoSizeKeepsTheContainer() {
        assertArrayEquals(new int[]{1920, 1080}, VideoFit.fit(0, 0, 1f, 1920, 1080));
    }

    @Test
    public void zeroPixelRatioIsTreatedAsSquare() {
        assertArrayEquals(new int[]{1920, 1080}, VideoFit.fit(1280, 720, 0f, 1920, 1080));
    }
}
