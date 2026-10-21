package com.gathin.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class PlaylistTest {
    private static final List<String> FOLDER = Arrays.asList(
            "Ep 10.mkv", "Ep 2.mkv", "Ep 1.MKV", "cover.jpg", "notes.txt", "Ep 2.srt", "song.mp3");

    @Test
    public void siblingsOfTheSameKindInNaturalOrder() {
        Playlist p = Playlist.of(FOLDER, "Ep 2.mkv", Playlist.Kind.VIDEO);
        assertEquals(3, p.size());
        assertEquals(1, p.index());
        assertEquals("Ep 2.mkv", p.current());
        assertEquals("Ep 10.mkv", p.next());
        assertEquals("Ep 2.mkv", p.previous());
        assertEquals("Ep 1.MKV", p.previous());
    }

    @Test
    public void noWrapAround() {
        Playlist p = Playlist.of(FOLDER, "Ep 1.MKV", Playlist.Kind.VIDEO);
        assertFalse(p.hasPrevious());
        assertNull(p.previous());
        assertEquals("Ep 1.MKV", p.current());
        p.next();
        p.next();
        assertFalse(p.hasNext());
        assertNull(p.next());
        assertEquals("Ep 10.mkv", p.current());
    }

    @Test
    public void otherKindsAreIgnored() {
        Playlist audio = Playlist.of(FOLDER, "song.mp3", Playlist.Kind.AUDIO);
        assertEquals(1, audio.size());
        assertFalse(audio.hasNext());
        Playlist images = Playlist.of(FOLDER, "cover.jpg", Playlist.Kind.IMAGE);
        assertEquals(1, images.size());
    }

    @Test
    public void currentIsIncludedEvenWhenNotListed() {
        Playlist p = Playlist.of(Arrays.asList("b.mp3"), "a.mp3", Playlist.Kind.AUDIO);
        assertEquals(2, p.size());
        assertEquals(0, p.index());
        assertEquals("b.mp3", p.next());
    }

    @Test
    public void emptyListHasNoCurrent() {
        Playlist p = Playlist.of(Arrays.asList("cover.jpg", "notes.txt"), null, Playlist.Kind.VIDEO);
        assertEquals(0, p.size());
        assertEquals(-1, p.index());
        assertNull(p.current());
        assertNull(p.next());
    }

    @Test
    public void kindFromExtension() {
        assertEquals(Playlist.Kind.AUDIO, Playlist.kindOf("A.FLAC"));
        assertEquals(Playlist.Kind.VIDEO, Playlist.kindOf("x.ts"));
        assertEquals(Playlist.Kind.IMAGE, Playlist.kindOf("p.HEIC"));
        assertEquals(Playlist.Kind.OTHER, Playlist.kindOf("x.srt"));
        assertEquals(Playlist.Kind.OTHER, Playlist.kindOf(".mp3"));
        assertEquals(Playlist.Kind.OTHER, Playlist.kindOf("noext"));
        assertTrue(Playlist.kindOf(null) == Playlist.Kind.OTHER);
    }
}
