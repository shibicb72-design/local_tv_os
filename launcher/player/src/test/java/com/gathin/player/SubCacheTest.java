package com.gathin.player;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class SubCacheTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void writeStoresTheBytesUnderAHashedName() throws IOException {
        File dir = new File(tmp.getRoot(), "subs");
        byte[] data = {1, 2, 3};
        File out = SubCache.write(dir, "/a/Movie.srt|10|5", "srt", data);
        assertTrue(out.getName().endsWith(".srt"));
        assertArrayEquals(data, Files.readAllBytes(out.toPath()));
        assertEquals(out.getName(), SubCache.write(dir, "/a/Movie.srt|10|5", "srt", data).getName());
    }

    @Test
    public void changedSourceGetsANewName() {
        assertNotEquals(SubCache.hash("/a/Movie.srt|10|5"), SubCache.hash("/a/Movie.srt|10|6"));
    }

    @Test
    public void pruneKeepsTheNewestFiles() throws IOException {
        File dir = tmp.newFolder("subs2");
        for (int i = 0; i < 25; i++) {
            File f = new File(dir, "f" + i + ".srt");
            Files.write(f.toPath(), new byte[]{1});
            assertTrue(f.setLastModified(1_000_000L + i * 1000L));
        }
        SubCache.prune(dir, SubCache.MAX_FILES);
        assertEquals(SubCache.MAX_FILES, dir.list().length);
        assertTrue(new File(dir, "f24.srt").exists());
        assertTrue(new File(dir, "f5.srt").exists());
        assertFalse(new File(dir, "f4.srt").exists());
    }

    @Test
    public void writeNeverLeavesMoreThanTheCap() throws IOException {
        File dir = new File(tmp.getRoot(), "subs3");
        for (int i = 0; i < 30; i++) SubCache.write(dir, "k" + i, "srt", new byte[]{1});
        assertTrue(dir.list().length <= SubCache.MAX_FILES);
    }

    @Test
    public void pruneOnAMissingFolderIsHarmless() {
        SubCache.prune(new File(tmp.getRoot(), "missing"), 5);
    }
}
