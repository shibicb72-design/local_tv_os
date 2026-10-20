package com.gathin.files;

import java.util.Comparator;

/** Entry comparators for the sort pill. Folders always come first, in both directions. */
public final class FileSort {
    public enum Key { NAME, DATE, SIZE }

    private FileSort() {}

    public static Comparator<Entry> comparator(final Key key, final boolean ascending) {
        return new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                if (a.isDir != b.isDir) return a.isDir ? -1 : 1;
                int c = 0;
                if (key == Key.DATE) c = Long.compare(a.mtime, b.mtime);
                else if (key == Key.SIZE && !a.isDir) c = Long.compare(a.size, b.size);
                if (!ascending) c = -c;
                if (c != 0) return c;
                // Ties (and the whole NAME sort) fall back to natural name order.
                int n = NaturalOrder.INSTANCE.compare(a.name, b.name);
                return key == Key.NAME && !ascending ? -n : n;
            }
        };
    }
}
