package com.gathin.files;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MimeKindsTest {
    @Test
    public void extensionIsCaseInsensitive() {
        assertEquals(MimeKinds.Kind.VIDEO, MimeKinds.kindOf("a.MKV"));
        assertEquals("video/x-matroska", MimeKinds.mimeOf("a.MKV"));
    }

    @Test
    public void kinds() {
        assertEquals(MimeKinds.Kind.AUDIO, MimeKinds.kindOf("x.flac"));
        assertEquals(MimeKinds.Kind.IMAGE, MimeKinds.kindOf("x.heic"));
        assertEquals(MimeKinds.Kind.SUBTITLE, MimeKinds.kindOf("x.srt"));
        assertEquals(MimeKinds.Kind.VIDEO, MimeKinds.kindOf("x.m2ts"));
    }

    @Test
    public void unknownIsOther() {
        assertEquals(MimeKinds.Kind.OTHER, MimeKinds.kindOf("x.zip"));
        assertEquals("application/octet-stream", MimeKinds.mimeOf("x.zip"));
        assertEquals(MimeKinds.Kind.OTHER, MimeKinds.kindOf("noext"));
        assertEquals(MimeKinds.Kind.OTHER, MimeKinds.kindOf(".mkv")); // dot-file, no extension
        assertEquals(MimeKinds.Kind.OTHER, MimeKinds.kindOf("trailing."));
        assertEquals(MimeKinds.Kind.OTHER, MimeKinds.kindOf(null));
    }
}
