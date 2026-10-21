package com.gathin.player;

/** Pure time text: {@code m:ss} under an hour, else {@code h:mm:ss}. Negative input is shown as zero. */
final class TimeFormat {

    private TimeFormat() {}

    static String format(long ms) {
        long total = Math.max(0L, ms) / 1000L;
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        StringBuilder sb = new StringBuilder(8);
        if (h > 0) {
            sb.append(h).append(':');
            if (m < 10) sb.append('0');
        }
        sb.append(m).append(':');
        if (s < 10) sb.append('0');
        sb.append(s);
        return sb.toString();
    }
}
