package com.gathin.player;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure resume logic. Key = path + size + mtime, so an edited or replaced file never resumes. Position is saved
 * when it is past 60 s and before 95% of the duration; a finished file is forgotten. At most 200 entries, least
 * recently used dropped first. Also remembers the chosen audio and subtitle track ids.
 * Persistence goes through {@link Store}: one text blob, so the Android side is a single SharedPreferences string.
 */
final class ResumeStore {

    /** One persisted string; null when nothing is saved yet. */
    interface Store {
        String load();

        void save(String data);
    }

    static final long MIN_POSITION_MS = 60_000L;
    static final int MAX_ENTRIES = 200;
    /** Finished when position * 100 >= duration * 95. */
    private static final int FINISHED_PERCENT = 95;

    static final class Entry {
        final long positionMs;
        /** Null when no choice was made. */
        final String audioId;
        final String subtitleId;

        Entry(long positionMs, String audioId, String subtitleId) {
            this.positionMs = positionMs;
            this.audioId = audioId;
            this.subtitleId = subtitleId;
        }
    }

    private final Store store;

    ResumeStore(Store store) {
        this.store = store;
    }

    static String key(String path, long size, long mtime) {
        return clean(path) + "|" + size + "|" + mtime;
    }

    /** The saved entry, or null. Only saves refresh the LRU order. */
    Entry get(String key) {
        return read().get(key);
    }

    /** Resume position in ms; 0 when there is nothing to resume. */
    long resumePosition(String key) {
        Entry e = get(key);
        return e == null ? 0L : e.positionMs;
    }

    /** Saves, keeps or clears the position according to the thresholds. Track choices are kept. */
    void savePosition(String key, long positionMs, long durationMs) {
        Map<String, Entry> map = read();
        Entry old = map.remove(key);
        if (durationMs > 0 && positionMs * 100 >= durationMs * FINISHED_PERCENT) {
            write(map); // finished: forget the file
            return;
        }
        long pos = (durationMs > 0 && positionMs > MIN_POSITION_MS) ? positionMs : 0L;
        put(map, key, pos, old == null ? null : old.audioId, old == null ? null : old.subtitleId);
    }

    /** Remembers the track choice (null = none made); the saved position is kept. */
    void saveTracks(String key, String audioId, String subtitleId) {
        Map<String, Entry> map = read();
        Entry old = map.remove(key);
        put(map, key, old == null ? 0L : old.positionMs, audioId, subtitleId);
    }

    void clear(String key) {
        Map<String, Entry> map = read();
        if (map.remove(key) != null) write(map);
    }

    private void put(Map<String, Entry> map, String key, long pos, String audioId, String subtitleId) {
        if (pos > 0 || audioId != null || subtitleId != null) {
            map.put(key, new Entry(pos, audioId == null ? null : clean(audioId),
                    subtitleId == null ? null : clean(subtitleId)));
        }
        write(map);
    }

    private void write(Map<String, Entry> map) {
        Iterator<String> it = map.keySet().iterator();
        int excess = map.size() - MAX_ENTRIES;
        while (excess-- > 0 && it.hasNext()) {
            it.next();
            it.remove();
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Entry> me : map.entrySet()) {
            Entry e = me.getValue();
            sb.append(me.getKey()).append('\t').append(e.positionMs).append('\t')
                    .append(e.audioId == null ? "" : e.audioId).append('\t')
                    .append(e.subtitleId == null ? "" : e.subtitleId).append('\n');
        }
        store.save(sb.toString());
    }

    /** Oldest first (insertion order), so the last line is the most recently used. */
    private Map<String, Entry> read() {
        Map<String, Entry> map = new LinkedHashMap<>();
        String data = store.load();
        if (data == null || data.isEmpty()) return map;
        for (String line : data.split("\n")) {
            String[] f = line.split("\t", -1);
            if (f.length != 4) continue;
            long pos;
            try {
                pos = Long.parseLong(f[1]);
            } catch (NumberFormatException e) {
                continue;
            }
            map.put(f[0], new Entry(pos, f[2].isEmpty() ? null : f[2], f[3].isEmpty() ? null : f[3]));
        }
        return map;
    }

    /** Tabs and newlines would break the line format; track ids and paths never need them. */
    private static String clean(String s) {
        if (s == null) return "";
        return s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
