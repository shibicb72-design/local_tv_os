package com.gathin.roman;

import java.util.Calendar;
import java.util.Locale;

/** Roman numerals and a Roman-style date line, pure Java so it can be unit tested. */
public final class Roman {

    private static final int[] VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] SYMBOLS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

    /** Latin month names in the genitive, as in "XX Octobris". */
    private static final String[] MONTHS = {
            "Ianuarii", "Februarii", "Martii", "Aprilis", "Maii", "Iunii",
            "Iulii", "Augusti", "Septembris", "Octobris", "Novembris", "Decembris"};

    private Roman() {
    }

    /** The numeral for 1 to 3999, or null outside that range. */
    public static String numeral(int n) {
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
    public static String date(int year, int month, int day) {
        String y = numeral(year);
        return numeral(day) + " " + MONTHS[month - 1] + " " + (y != null ? y : String.valueOf(year));
    }

    /**
     * Text as carved in stone: upper case, U written V and J written I, only A-Z, 0-9, space, middle dot,
     * full stop and hyphen kept, spaces collapsed and trimmed, cut to {@code max} characters. null gives "".
     */
    public static String inscribe(String s, int max) {
        if (s == null) return "";
        String up = s.toUpperCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < up.length(); i++) {
            char c = up.charAt(i);
            if (c == 'U') c = 'V';
            else if (c == 'J') c = 'I';
            boolean keep = (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '·' || c == '.' || c == '-';
            if (keep) {
                sb.append(c);
            } else if (c == ' ' && sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') {
                sb.append(' ');
            }
        }
        String out = sb.toString().trim();
        if (out.length() > max) out = out.substring(0, Math.max(0, max)).trim();
        return out;
    }

    public static String today() {
        Calendar c = Calendar.getInstance();
        return date(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }
}
