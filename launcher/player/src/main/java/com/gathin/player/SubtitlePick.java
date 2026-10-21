package com.gathin.player;

import java.util.List;

/**
 * Pure default subtitle choice: remembered choice, else a forced track in the system language, else the first
 * sidecar, else off. Track ids are {@code ext:<file name>} for sidecars and {@code emb:<format id>} for tracks
 * inside the container; {@link #OFF} is the remembered "subtitles off".
 */
final class SubtitlePick {

    static final String OFF = "off";
    static final String SIDECAR_PREFIX = "ext:";
    static final String EMBEDDED_PREFIX = "emb:";

    static final class Track {
        final String id;
        /** Two-letter code or null. */
        final String language;
        final boolean forced;

        Track(String id, String language, boolean forced) {
            this.id = id;
            this.language = language;
            this.forced = forced;
        }

        boolean sidecar() {
            return id.startsWith(SIDECAR_PREFIX);
        }
    }

    private SubtitlePick() {}

    /**
     * @param tracks     playable text tracks in display order (sidecars in {@link SubtitleFinder} order)
     * @param remembered the saved id, {@link #OFF}, or null when nothing was chosen
     * @param systemLang two-letter system language or null
     * @return the track id to show, or null for no subtitles
     */
    static String pick(List<Track> tracks, String remembered, String systemLang) {
        if (OFF.equals(remembered)) return null;
        if (remembered != null) {
            for (Track t : tracks) {
                if (t.id.equals(remembered)) return t.id;
            }
        }
        if (systemLang != null) {
            for (Track t : tracks) {
                if (t.forced && systemLang.equals(t.language)) return t.id;
            }
        }
        for (Track t : tracks) {
            if (t.sidecar()) return t.id;
        }
        return null;
    }
}
