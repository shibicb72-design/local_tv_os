package com.gathin.player;

import java.util.Locale;

/**
 * Pure display text for the tracks menu: language in the system locale, channel layout, short codec name,
 * "Forced" and "(not supported)". Uses {@code java.util.Locale} only, so it runs on the JVM.
 */
final class TrackLabels {

    static final String UNKNOWN = "Unknown";
    static final String NOT_SUPPORTED = "(not supported)";
    private static final String SEPARATOR = " · ";

    private TrackLabels() {}

    /** Language name in {@code display}'s language; {@link #UNKNOWN} for null, empty or {@code und}. */
    static String language(String tag, Locale display) {
        if (tag == null) return UNKNOWN;
        String t = tag.trim();
        if (t.isEmpty() || t.equalsIgnoreCase("und")) return UNKNOWN;
        String name = Locale.forLanguageTag(toTwoLetter(t)).getDisplayLanguage(display);
        return name.isEmpty() ? t : name;
    }

    /** Java's display names are keyed by the 2-letter code; map a 3-letter one ({@code fas}) to it ({@code fa}). */
    private static String toTwoLetter(String tag) {
        if (tag.length() != 3) return tag;
        String lower = tag.toLowerCase(Locale.ROOT);
        for (String iso : Locale.getISOLanguages()) {
            try {
                if (new Locale(iso).getISO3Language().equals(lower)) return iso;
            } catch (java.util.MissingResourceException e) {
                // no 3-letter form for this code; keep looking
            }
        }
        return tag;
    }

    /** {@code Mono}, {@code Stereo}, {@code 5.1}, {@code 7.1}, else {@code N ch}; null when unknown. */
    static String channels(int count) {
        switch (count) {
            case 1: return "Mono";
            case 2: return "Stereo";
            case 6: return "5.1";
            case 8: return "7.1";
            default: return count > 0 ? count + " ch" : null;
        }
    }

    /** Short codec name from an audio MIME type; null when the MIME type is missing or not worth showing. */
    static String codec(String mime) {
        if (mime == null) return null;
        switch (mime.toLowerCase(Locale.ROOT)) {
            case "audio/ac3": return "AC-3";
            case "audio/eac3":
            case "audio/eac3-joc": return "E-AC-3";
            case "audio/vnd.dts": return "DTS";
            case "audio/vnd.dts.hd": return "DTS-HD";
            case "audio/vnd.dts.uhd": return "DTS:X";
            case "audio/mp4a-latm": return "AAC";
            case "audio/true-hd": return "TrueHD";
            case "audio/mpeg": return "MP3";
            case "audio/mpeg-l2": return "MP2";
            case "audio/opus": return "Opus";
            case "audio/vorbis": return "Vorbis";
            case "audio/flac": return "FLAC";
            case "audio/alac": return "ALAC";
            case "audio/raw": return "PCM";
            default: return null;
        }
    }

    /** {@code Persian · AC-3 · 5.1}; unsupported tracks get {@link #NOT_SUPPORTED} at the end. */
    static String audio(String language, String mime, int channelCount, boolean supported, Locale display) {
        StringBuilder sb = new StringBuilder(language(language, display));
        append(sb, codec(mime));
        append(sb, channels(channelCount));
        if (!supported) append(sb, NOT_SUPPORTED);
        return sb.toString();
    }

    /**
     * {@code English · Forced}. When the language is unknown the track's own {@code label} (a sidecar's file
     * name, a container title) is used instead of {@link #UNKNOWN} if there is one.
     */
    static String subtitle(String language, String label, boolean forced, boolean supported, Locale display) {
        String name = language(language, display);
        if (name.equals(UNKNOWN) && label != null && !label.trim().isEmpty()) name = label.trim();
        StringBuilder sb = new StringBuilder(name);
        if (forced) append(sb, "Forced");
        if (!supported) append(sb, NOT_SUPPORTED);
        return sb.toString();
    }

    private static void append(StringBuilder sb, String part) {
        if (part != null) sb.append(SEPARATOR).append(part);
    }
}
