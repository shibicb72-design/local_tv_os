package com.gathin.files;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class FileSortTest {
    private static Entry file(String n, long size, long mtime) {
        return new Entry(n, "/x/" + n, false, size, mtime, MimeKinds.kindOf(n));
    }

    private static Entry dir(String n, long mtime) {
        return new Entry(n, "/x/" + n, true, 0, mtime, MimeKinds.Kind.OTHER);
    }

    private static String order(FileSort.Key key, boolean asc) {
        List<Entry> l = new ArrayList<>();
        l.add(file("b10.mkv", 5, 300));
        l.add(dir("zdir", 100));
        l.add(file("b2.mkv", 9, 100));
        l.add(dir("adir", 900));
        l.add(file("a.mkv", 1, 200));
        Collections.sort(l, FileSort.comparator(key, asc));
        StringBuilder sb = new StringBuilder();
        for (Entry e : l) sb.append(e.name).append(' ');
        return sb.toString().trim();
    }

    @Test
    public void nameAscendingNaturalFoldersFirst() {
        assertEquals("adir zdir a.mkv b2.mkv b10.mkv", order(FileSort.Key.NAME, true));
    }

    @Test
    public void nameDescendingKeepsFoldersFirst() {
        assertEquals("zdir adir b10.mkv b2.mkv a.mkv", order(FileSort.Key.NAME, false));
    }

    @Test
    public void dateSort() {
        assertEquals("zdir adir b2.mkv a.mkv b10.mkv", order(FileSort.Key.DATE, true));
        assertEquals("adir zdir b10.mkv a.mkv b2.mkv", order(FileSort.Key.DATE, false));
    }

    @Test
    public void sizeSortFoldersTieOnName() {
        assertEquals("adir zdir a.mkv b10.mkv b2.mkv", order(FileSort.Key.SIZE, true));
        assertEquals("adir zdir b2.mkv b10.mkv a.mkv", order(FileSort.Key.SIZE, false));
    }
}
