package com.gathin.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SubtitleFinderTest {

    private static List<String> names(List<SubtitleFinder.Candidate> found) {
        List<String> out = new ArrayList<>();
        for (SubtitleFinder.Candidate c : found) out.add(c.name);
        return out;
    }

    @Test
    public void languageTaggedFileMatchesVideo() {
        List<SubtitleFinder.Candidate> found = SubtitleFinder.find("Movie.mkv", Arrays.asList("Movie.fa.srt"), "en");
        assertEquals(1, found.size());
        assertEquals("fa", found.get(0).language);
        assertFalse(found.get(0).exact);
    }

    @Test
    public void otherVideoNamesDoNotMatch() {
        List<String> folder = Arrays.asList("Movie2.srt", "Movie.2.srt", "Movie.final.srt", "Other.srt", "Movie.txt",
                "Movie.mkv");
        assertTrue(SubtitleFinder.find("Movie.mkv", folder, "en").isEmpty());
    }

    @Test
    public void matchingIsCaseInsensitive() {
        assertEquals(Arrays.asList("MOVIE.EN.SRT"),
                names(SubtitleFinder.find("movie.mp4", Arrays.asList("MOVIE.EN.SRT"), "en")));
    }

    @Test
    public void forcedAndSdhTagsAreRecognised() {
        SubtitleFinder.Candidate c =
                SubtitleFinder.find("Movie.mkv", Arrays.asList("Movie.eng.forced.srt"), "en").get(0);
        assertEquals("en", c.language);
        assertTrue(c.forced);
        SubtitleFinder.Candidate s = SubtitleFinder.find("Movie.mkv", Arrays.asList("Movie.en.sdh.srt"), "en").get(0);
        assertTrue(s.sdh);
        assertFalse(s.forced);
    }

    @Test
    public void orderIsExactThenSystemThenEnglishThenOthers() {
        List<String> folder = Arrays.asList("Movie.de.srt", "Movie.en.srt", "Movie.fa.forced.srt", "Movie.fa.srt",
                "Movie.srt");
        assertEquals(Arrays.asList("Movie.srt", "Movie.fa.srt", "Movie.fa.forced.srt", "Movie.en.srt", "Movie.de.srt"),
                names(SubtitleFinder.find("Movie.mkv", folder, "fa")));
    }

    @Test
    public void allFourExtensionsAreSubtitles() {
        for (String ext : new String[]{"srt", "ass", "ssa", "vtt", "SRT"}) {
            assertTrue(ext, SubtitleFinder.isSubtitleFile("a." + ext));
        }
        assertFalse(SubtitleFinder.isSubtitleFile("a.sub"));
        assertFalse(SubtitleFinder.isSubtitleFile("srt"));
    }

    @Test
    public void mimeTypesFollowTheExtension() {
        assertEquals("application/x-subrip", SubtitleFinder.mimeFor("a.srt"));
        assertEquals("text/vtt", SubtitleFinder.mimeFor("a.VTT"));
        assertEquals("text/x-ssa", SubtitleFinder.mimeFor("a.ass"));
        assertEquals("text/x-ssa", SubtitleFinder.mimeFor("a.ssa"));
        assertNull(SubtitleFinder.mimeFor("a.mkv"));
    }

    @Test
    public void normalizeLangHandlesTagsAndThreeLetterCodes() {
        assertEquals("fa", SubtitleFinder.normalizeLang("fa-IR"));
        assertEquals("fa", SubtitleFinder.normalizeLang("fas"));
        assertEquals("fa", SubtitleFinder.normalizeLang("per"));
        assertEquals("en", SubtitleFinder.normalizeLang("eng"));
        assertEquals("de", SubtitleFinder.normalizeLang("ger"));
        assertNull(SubtitleFinder.normalizeLang("und"));
        assertNull(SubtitleFinder.normalizeLang(""));
        assertNull(SubtitleFinder.normalizeLang(null));
    }
}
