package com.gathin.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Locale;

public class TrackLabelsTest {

    @Test
    public void persianNameInAnEnglishLocale() {
        assertEquals("Persian", TrackLabels.language("fa", Locale.ENGLISH));
        assertEquals("Persian", TrackLabels.language("fas", Locale.ENGLISH));
    }

    @Test
    public void undAndMissingAreUnknown() {
        assertEquals("Unknown", TrackLabels.language("und", Locale.ENGLISH));
        assertEquals("Unknown", TrackLabels.language(null, Locale.ENGLISH));
        assertEquals("Unknown", TrackLabels.language(" ", Locale.ENGLISH));
    }

    @Test
    public void channelLayouts() {
        assertEquals("Mono", TrackLabels.channels(1));
        assertEquals("Stereo", TrackLabels.channels(2));
        assertEquals("5.1", TrackLabels.channels(6));
        assertEquals("7.1", TrackLabels.channels(8));
        assertEquals("4 ch", TrackLabels.channels(4));
        assertNull(TrackLabels.channels(-1));
    }

    @Test
    public void codecShortNames() {
        assertEquals("AC-3", TrackLabels.codec("audio/ac3"));
        assertEquals("E-AC-3", TrackLabels.codec("audio/eac3"));
        assertEquals("DTS", TrackLabels.codec("audio/vnd.dts"));
        assertEquals("AAC", TrackLabels.codec("audio/mp4a-latm"));
        assertEquals("TrueHD", TrackLabels.codec("audio/true-hd"));
        assertNull(TrackLabels.codec("audio/x-unknown"));
        assertNull(TrackLabels.codec(null));
    }

    @Test
    public void audioLabelJoinsParts() {
        assertEquals("English · AC-3 · 5.1",
                TrackLabels.audio("en", "audio/ac3", 6, true, Locale.ENGLISH));
        assertEquals("Unknown · DTS · (not supported)",
                TrackLabels.audio("und", "audio/vnd.dts", 0, false, Locale.ENGLISH));
    }

    @Test
    public void subtitleLabelMarksForcedAndUnsupported() {
        assertEquals("English · Forced", TrackLabels.subtitle("en", null, true, true, Locale.ENGLISH));
        assertEquals("Movie.srt", TrackLabels.subtitle(null, "Movie.srt", false, true, Locale.ENGLISH));
        assertEquals("Unknown · (not supported)", TrackLabels.subtitle("und", " ", false, false, Locale.ENGLISH));
    }
}
