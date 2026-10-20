package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RomanTest {

    @Test
    public void numerals() {
        assertEquals("I", Roman.numeral(1));
        assertEquals("IV", Roman.numeral(4));
        assertEquals("IX", Roman.numeral(9));
        assertEquals("XIV", Roman.numeral(14));
        assertEquals("XLII", Roman.numeral(42));
        assertEquals("XC", Roman.numeral(90));
        assertEquals("CDXLIV", Roman.numeral(444));
        assertEquals("MCMXCIV", Roman.numeral(1994));
        assertEquals("MMXXVI", Roman.numeral(2026));
        assertEquals("MMMCMXCIX", Roman.numeral(3999));
    }

    @Test
    public void outOfRange() {
        assertNull(Roman.numeral(0));
        assertNull(Roman.numeral(-5));
        assertNull(Roman.numeral(4000));
    }

    @Test
    public void dateLine() {
        assertEquals("XX Octobris MMXXVI", Roman.date(2026, 10, 20));
        assertEquals("I Ianuarii MM", Roman.date(2000, 1, 1));
        assertEquals("XXXI Decembris MCMXCIX", Roman.date(1999, 12, 31));
    }

    @Test
    public void dateLineYearOutOfRange() {
        assertEquals("V Martii 4500", Roman.date(4500, 3, 5));
    }
}
