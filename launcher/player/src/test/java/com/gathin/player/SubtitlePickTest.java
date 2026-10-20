package com.gathin.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class SubtitlePickTest {

    private static final SubtitlePick.Track EMB_EN = new SubtitlePick.Track("emb:2", "en", false);
    private static final SubtitlePick.Track EMB_FA_FORCED = new SubtitlePick.Track("emb:3", "fa", true);
    private static final SubtitlePick.Track EXT_DE = new SubtitlePick.Track("ext:Movie.de.srt", "de", false);
    private static final SubtitlePick.Track EXT_FA = new SubtitlePick.Track("ext:Movie.fa.srt", "fa", false);

    private static List<SubtitlePick.Track> list(SubtitlePick.Track... t) {
        List<SubtitlePick.Track> out = new ArrayList<>();
        for (SubtitlePick.Track x : t) out.add(x);
        return out;
    }

    @Test
    public void rememberedChoiceWins() {
        assertEquals("emb:2", SubtitlePick.pick(list(EMB_EN, EMB_FA_FORCED, EXT_DE), "emb:2", "fa"));
    }

    @Test
    public void rememberedOffMeansNoSubtitles() {
        assertNull(SubtitlePick.pick(list(EMB_EN, EXT_DE), SubtitlePick.OFF, "en"));
    }

    @Test
    public void unknownRememberedIdFallsThrough() {
        assertEquals("ext:Movie.de.srt", SubtitlePick.pick(list(EMB_EN, EXT_DE), "emb:99", "en"));
    }

    @Test
    public void forcedTrackInSystemLanguageBeatsSidecar() {
        assertEquals("emb:3", SubtitlePick.pick(list(EMB_EN, EXT_DE, EMB_FA_FORCED), null, "fa"));
    }

    @Test
    public void forcedTrackInAnotherLanguageIsIgnored() {
        assertEquals("ext:Movie.de.srt", SubtitlePick.pick(list(EMB_FA_FORCED, EXT_DE), null, "en"));
    }

    @Test
    public void firstSidecarWhenNothingElseApplies() {
        assertEquals("ext:Movie.fa.srt", SubtitlePick.pick(list(EMB_EN, EXT_FA, EXT_DE), null, "en"));
    }

    @Test
    public void embeddedOnlyDefaultsToOff() {
        assertNull(SubtitlePick.pick(list(EMB_EN), null, "en"));
        assertNull(SubtitlePick.pick(list(), null, "en"));
    }
}
