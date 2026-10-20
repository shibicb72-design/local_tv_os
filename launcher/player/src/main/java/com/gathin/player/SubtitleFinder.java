package com.gathin.player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure sidecar subtitle lookup. A subtitle file belongs to a video when its name is the video's base name
 * (case-insensitive) optionally followed by dot-separated tags: a 2/3-letter language code, {@code forced},
 * {@code sdh} (also {@code cc}, {@code hi}). {@code Movie.srt}, {@code Movie.fa.srt}, {@code Movie.eng.forced.srt}
 * match {@code Movie.mkv}; {@code Movie2.srt} and {@code Movie.2.srt} do not.
 * Order: the exact-name file first, then tagged files by language (system language, English, others), forced last
 * within a language, then by name.
 */
final class SubtitleFinder {

    static final class Candidate {
        final String name;
        /** Two-letter code, or null when untagged or unknown. */
        final String language;
        final boolean forced;
        final boolean sdh;
        /** The file carries no tags at all ({@code Movie.srt}). */
        final boolean exact;

        Candidate(String name, String language, boolean forced, boolean sdh, boolean exact) {
            this.name = name;
            this.language = language;
            this.forced = forced;
            this.sdh = sdh;
            this.exact = exact;
        }
    }

    private static final String[] EXTENSIONS = {"srt", "ass", "ssa", "vtt"};
    private static final Map<String, String> ISO3_TO_2 = buildIso3();

    private SubtitleFinder() {}

    static boolean isSubtitleFile(String name) {
        return extension(name) != null;
    }

    /** MIME type ExoPlayer expects for the extension, or null when it is not a subtitle file. */
    static String mimeFor(String name) {
        String ext = extension(name);
        if (ext == null) return null;
        switch (ext) {
            case "srt":
                return "application/x-subrip";
            case "vtt":
                return "text/vtt";
            default:
                return "text/x-ssa";   // ass and ssa share one parser; ASS styling is dropped
        }
    }

    /** Sidecar candidates for {@code videoName} among {@code folderNames} (file names, not paths). */
    static List<Candidate> find(String videoName, Collection<String> folderNames, String systemLang) {
        String base = stripExtension(videoName);
        String sys = normalizeLang(systemLang);
        List<Candidate> out = new ArrayList<>();
        for (String name : folderNames) {
            if (!isSubtitleFile(name)) continue;
            Candidate c = parse(base, name);
            if (c != null) out.add(c);
        }
        Collections.sort(out, new Comparator<Candidate>() {
            @Override
            public int compare(Candidate a, Candidate b) {
                int d = Boolean.compare(!a.exact, !b.exact);
                if (d != 0) return d;
                d = rank(a.language, sys) - rank(b.language, sys);
                if (d != 0) return d;
                d = Boolean.compare(a.forced, b.forced);
                if (d != 0) return d;
                return a.name.toLowerCase(Locale.ROOT).compareTo(b.name.toLowerCase(Locale.ROOT));
            }
        });
        return out;
    }

    /**
     * Two-letter lower-case language from a BCP-47 tag or ISO 639-2 code ({@code eng}, {@code fas}, {@code per},
     * {@code fa-IR}); null for empty, {@code und} or an unknown code.
     */
    static String normalizeLang(String tag) {
        if (tag == null) return null;
        String t = tag.trim().toLowerCase(Locale.ROOT);
        int dash = t.indexOf('-');
        if (dash < 0) dash = t.indexOf('_');
        if (dash >= 0) t = t.substring(0, dash);
        if (t.isEmpty() || t.equals("und")) return null;
        if (t.length() == 2) return t;
        return ISO3_TO_2.get(t);
    }

    private static Candidate parse(String base, String name) {
        String stem = stripExtension(name);
        if (stem.length() < base.length() || !stem.regionMatches(true, 0, base, 0, base.length())) return null;
        String rest = stem.substring(base.length());
        if (rest.isEmpty()) return new Candidate(name, null, false, false, true);
        if (rest.charAt(0) != '.') return null;
        String lang = null;
        boolean forced = false;
        boolean sdh = false;
        for (String tok : rest.substring(1).split("\\.", -1)) {
            String t = tok.toLowerCase(Locale.ROOT);
            if (t.equals("forced")) {
                forced = true;
            } else if (t.equals("sdh") || t.equals("cc") || t.equals("hi")) {
                sdh = true;
            } else if (isLangToken(t)) {
                if (lang == null) lang = normalizeLang(t);
            } else {
                return null;   // "Movie.2.srt", "Movie.final.srt": not tags
            }
        }
        return new Candidate(name, lang, forced, sdh, false);
    }

    private static boolean isLangToken(String t) {
        if (t.length() < 2 || t.length() > 3) return false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c < 'a' || c > 'z') return false;
        }
        return true;
    }

    private static int rank(String lang, String sys) {
        if (lang == null) return 2;
        if (lang.equals(sys)) return 0;
        return lang.equals("en") ? 1 : 2;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) return null;
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        for (String e : EXTENSIONS) {
            if (e.equals(ext)) return ext;
        }
        return null;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }

    private static Map<String, String> buildIso3() {
        Map<String, String> m = new HashMap<>();
        for (String two : Locale.getISOLanguages()) {
            try {
                m.put(Locale.forLanguageTag(two).getISO3Language(), two);
            } catch (RuntimeException ignored) {
                // a language without ISO 3 data: skip
            }
        }
        // ISO 639-2/B (bibliographic) codes that subtitle sites still use
        String[] alias = {"ger", "de", "fre", "fr", "per", "fa", "dut", "nl", "chi", "zh", "gre", "el", "cze", "cs",
                "slo", "sk", "rum", "ro", "mac", "mk", "may", "ms", "alb", "sq", "arm", "hy", "baq", "eu", "bur",
                "my", "geo", "ka", "ice", "is", "wel", "cy"};
        for (int i = 0; i < alias.length; i += 2) m.put(alias[i], alias[i + 1]);
        return m;
    }
}
