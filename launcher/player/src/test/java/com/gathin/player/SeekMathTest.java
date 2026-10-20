package com.gathin.player;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SeekMathTest {

    @Test
    public void forwardAndBackSteps() {
        assertEquals(70_000L, SeekMath.seek(60_000L, 10_000L, 600_000L));
        assertEquals(30_000L, SeekMath.seek(60_000L, -30_000L, 600_000L));
    }

    @Test
    public void clampsAtZero() {
        assertEquals(0L, SeekMath.seek(5_000L, -10_000L, 600_000L));
        assertEquals(0L, SeekMath.clamp(-1L, 600_000L));
    }

    @Test
    public void clampsAtDuration() {
        assertEquals(600_000L, SeekMath.seek(595_000L, 30_000L, 600_000L));
        assertEquals(600_000L, SeekMath.clamp(700_000L, 600_000L));
    }

    @Test
    public void unknownDurationOnlyClampsAtZero() {
        assertEquals(40_000L, SeekMath.seek(10_000L, 30_000L, 0L));
        assertEquals(0L, SeekMath.seek(10_000L, -30_000L, -1L));
    }
}
