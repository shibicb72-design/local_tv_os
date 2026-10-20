package com.gathin.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the sources, like StoneTextureTest: the APK budget depends on never pulling in the default factory. */
public class PlayerExtractorsTest {

    private static final String[] EXPECTED = {
            "MatroskaExtractor", "Mp4Extractor", "FragmentedMp4Extractor", "TsExtractor", "Mp3Extractor",
            "FlacExtractor", "OggExtractor", "WavExtractor", "AviExtractor"};

    private static String source(String name) throws IOException {
        Path file = Paths.get("src/main/java/com/gathin/player/" + name + ".java");
        if (!Files.exists(file)) file = Paths.get("player/src/main/java/com/gathin/player/" + name + ".java");
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    @Test
    public void createsExactlyTheNineExtractors() throws IOException {
        String src = source("PlayerExtractors");
        Matcher m = Pattern.compile("new (\\w+Extractor)\\(").matcher(src);
        java.util.List<String> found = new java.util.ArrayList<>();
        while (m.find()) found.add(m.group(1));
        assertEquals(java.util.Arrays.asList(EXPECTED), found);
    }

    @Test
    public void defaultExtractorsFactoryIsNeverReferenced() throws IOException {
        for (String cls : new String[]{"PlayerExtractors", "PlayerFactory", "PlayerActivity"}) {
            assertFalse(cls, source(cls).contains("DefaultExtractorsFactory"));
        }
    }

    @Test
    public void playerIsBuiltWithTheCustomFactoryOnly() throws IOException {
        String src = source("PlayerFactory");
        assertTrue(src.contains("new PlayerExtractors()"));
        assertFalse(src.contains("Builder(ctx)"));
        assertFalse(src.contains("Builder(app)"));
        assertFalse(src.contains("Builder(context)"));
    }

    @Test
    public void noMedia3UiAndPlayerReleasedInOnStop() throws IOException {
        for (String cls : new String[]{"PlayerExtractors", "PlayerFactory", "PlayerActivity"}) {
            assertFalse(cls, source(cls).contains("androidx.media3.ui"));
        }
        String activity = source("PlayerActivity");
        int onStop = activity.indexOf("protected void onStop()");
        assertTrue(onStop > 0);
        assertTrue(activity.indexOf("player.release()", onStop) > onStop);
    }
}
