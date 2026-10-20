package com.gathin.player;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TimeFormatTest {

    @Test
    public void underAnHourIsMinutesSeconds() {
        assertEquals("0:00", TimeFormat.format(0));
        assertEquals("0:09", TimeFormat.format(9_999));
        assertEquals("1:05", TimeFormat.format(65_000));
        assertEquals("59:59", TimeFormat.format(3_599_999));
    }

    @Test
    public void anHourOrMoreIsHoursMinutesSeconds() {
        assertEquals("1:00:00", TimeFormat.format(3_600_000));
        assertEquals("1:02:13", TimeFormat.format(3_733_000));
        assertEquals("10:00:01", TimeFormat.format(36_001_000));
    }

    @Test
    public void negativeIsZero() {
        assertEquals("0:00", TimeFormat.format(-5_000));
    }
}
