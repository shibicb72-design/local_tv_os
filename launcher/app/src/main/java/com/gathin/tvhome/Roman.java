package com.gathin.tvhome;

import java.util.Calendar;

/** Roman numerals and a Roman-style date line, pure Java so it can be unit tested. */
final class Roman {

    private static final int[] VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] SYMBOLS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

    /** Latin month names in the genitive, as in "XX Octobris". */
    private static final String[] MONTHS = {
            "Ianuarii", "Februarii", "Martii", "Aprilis", "Maii", "Iunii",
            "Iulii", "Augusti", "Septembris", "Octobris", "Novembris", "Decembris"};

    private Roman() {
    }

    /** The numeral for 1 to 3999, or null outside that range. */
    static String numeral(int n) {
        if (n < 1 || n > 3999) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < VALUES.length; i++) {
            while (n >= VALUES[i]) {
                sb.append(SYMBOLS[i]);
                n -= VALUES[i];
            }
        }
        return sb.toString();
    }

    /** e.g. "XX Octobris MMXXVI"; month is 1 to 12. Falls back to Arabic digits for a year outside 1 to 3999. */
    static String date(int year, int month, int day) {
        String y = numeral(year);
        return numeral(day) + " " + MONTHS[month - 1] + " " + (y != null ? y : String.valueOf(year));
    }

    static String today() {
        Calendar c = Calendar.getInstance();
        return date(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }
}
