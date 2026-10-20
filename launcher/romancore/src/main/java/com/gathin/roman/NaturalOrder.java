package com.gathin.roman;

import java.util.Comparator;

/** Case-insensitive name order where digit runs compare as numbers ("Ep 2" before "Ep 10"). */
public final class NaturalOrder implements Comparator<String> {
    public static final NaturalOrder INSTANCE = new NaturalOrder();

    private NaturalOrder() {}

    @Override
    public int compare(String a, String b) {
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i), cb = b.charAt(j);
            if (isDigit(ca) && isDigit(cb)) {
                int ei = runEnd(a, i), ej = runEnd(b, j);
                int c = compareRuns(a, i, ei, b, j, ej);
                if (c != 0) return c;
                i = ei;
                j = ej;
            } else {
                int c = Character.compare(Character.toLowerCase(ca), Character.toLowerCase(cb));
                if (c != 0) return c;
                i++;
                j++;
            }
        }
        int rest = (a.length() - i) - (b.length() - j);
        if (rest != 0) return rest < 0 ? -1 : 1;
        return a.compareTo(b);
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static int runEnd(String s, int from) {
        int e = from;
        while (e < s.length() && isDigit(s.charAt(e))) e++;
        return e;
    }

    /** Numeric compare of two digit runs of any length, ignoring leading zeros. */
    private static int compareRuns(String a, int sa, int ea, String b, int sb, int eb) {
        while (sa < ea - 1 && a.charAt(sa) == '0') sa++;
        while (sb < eb - 1 && b.charAt(sb) == '0') sb++;
        int la = ea - sa, lb = eb - sb;
        if (la != lb) return la < lb ? -1 : 1;
        for (int k = 0; k < la; k++) {
            int c = Character.compare(a.charAt(sa + k), b.charAt(sb + k));
            if (c != 0) return c;
        }
        return 0;
    }
}
