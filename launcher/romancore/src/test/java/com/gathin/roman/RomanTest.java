package com.gathin.roman;

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
    public void inscribeCarvesLetters() {
        assertEquals("AVGVSTVS", Roman.inscribe("Augustus", 8));
        assertEquals("IVLII", Roman.inscribe("Iulii", 40));
        assertEquals("SALVE", Roman.inscribe("  salve  ", 8));
        assertEquals("AVE · ROMA", Roman.inscribe("Ave · Roma!", 20));
        assertEquals("", Roman.inscribe("ДОМ", 8));
        assertEquals("", Roman.inscribe(null, 8));
        assertEquals("A B", Roman.inscribe("a    b", 8));
        assertEquals("IVLIVS", Roman.inscribe("Julius", 8));
    }

    @Test
    public void inscribeCutsAtMax() {
        assertEquals("ABCDEFGH", Roman.inscribe("abcdefghij", 8));
        assertEquals("ABC", Roman.inscribe("abc defgh", 4));   // the cut leaves no trailing space
    }

    @Test
    public void inscribeDateLine() {
        assertEquals("XX OCTOBRIS MMXXVI", Roman.inscribe(Roman.date(2026, 10, 20), 40));
        assertEquals("V IVLII MMXXVI", Roman.inscribe(Roman.date(2026, 7, 5), 40));
        assertEquals("I AVGVSTI MMXXVI", Roman.inscribe(Roman.date(2026, 8, 1), 40));
    }

    @Test
    public void dateLineYearOutOfRange() {
        assertEquals("V Martii 4500", Roman.date(4500, 3, 5));
    }
}
