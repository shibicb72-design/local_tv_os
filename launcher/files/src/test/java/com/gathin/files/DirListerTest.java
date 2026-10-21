package com.gathin.files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DirListerTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void listsFilesAndFolders() throws IOException {
        File f = tmp.newFile("Movie.MKV");
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(new byte[10]);
        }
        tmp.newFolder("sub");
        DirLister l = new DirLister();
        List<Entry> out = l.list(tmp.getRoot(), false);
        assertFalse(l.denied());
        assertEquals(2, out.size());
        for (Entry e : out) {
            if (e.isDir) {
                assertEquals("sub", e.name);
            } else {
                assertEquals(10, e.size);
                assertEquals(MimeKinds.Kind.VIDEO, e.kind);
                assertEquals(f.getAbsolutePath(), e.path);
            }
        }
    }

    @Test
    public void hiddenSkippedUnlessAsked() throws IOException {
        tmp.newFile(".hidden");
        tmp.newFile("shown.txt");
        DirLister l = new DirLister();
        assertEquals(1, l.list(tmp.getRoot(), false).size());
        assertEquals(2, l.list(tmp.getRoot(), true).size());
    }

    @Test
    public void unreadableGivesEmptyAndDenied() throws IOException {
        DirLister l = new DirLister();
        // A regular file and a missing path both make listFiles() return null.
        assertTrue(l.list(tmp.newFile("plain"), false).isEmpty());
        assertTrue(l.denied());
        assertTrue(l.list(new File(tmp.getRoot(), "missing"), false).isEmpty());
        assertTrue(l.denied());
        assertTrue(l.list(null, false).isEmpty());
        assertTrue(l.denied());
        l.list(tmp.getRoot(), false);
        assertFalse(l.denied());
    }
}
