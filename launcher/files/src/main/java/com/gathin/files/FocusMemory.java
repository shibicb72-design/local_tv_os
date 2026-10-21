package com.gathin.files;

import java.util.LinkedHashMap;
import java.util.Map;

/** Remembers the focused row of each folder (pure, F7): path to (position, scroll offset), LRU of 64 folders. */
final class FocusMemory {

    static final int CAPACITY = 64;

    /** A remembered spot: adapter position and the row's top offset inside the list, in px. */
    static final class Spot {
        final int position;
        final int offset;

        Spot(int position, int offset) {
            this.position = position;
            this.offset = offset;
        }
    }

    private static final Spot START = new Spot(0, 0);

    private final Map<String, Spot> map = new LinkedHashMap<String, Spot>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Spot> eldest) {
            return size() > CAPACITY;
        }
    };

    void remember(String path, int position, int offset) {
        if (path == null || position < 0) return;
        map.put(path, new Spot(position, offset));
    }

    /** The remembered spot, or position 0 / offset 0 for a folder never seen (or evicted). */
    Spot recall(String path) {
        Spot s = path == null ? null : map.get(path);
        return s == null ? START : s;
    }
}
