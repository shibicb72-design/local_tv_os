package com.gathin.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Before;
import org.junit.Test;

public class ResumeStoreTest {

    /** In-memory test double. */
    private static final class MemStore implements ResumeStore.Store {
        String data;

        @Override public String load() { return data; }

        @Override public void save(String d) { data = d; }
    }

    private static final long DUR = 3_600_000L;
    private static final String KEY = ResumeStore.key("/storage/AAAA-BBBB/Movie.mkv", 1000L, 42L);

    private MemStore mem;
    private ResumeStore store;

    @Before
    public void setUp() {
        mem = new MemStore();
        store = new ResumeStore(mem);
    }

    @Test
    public void savesPositionBetweenOneMinuteAndNinetyFivePercent() {
        store.savePosition(KEY, 61_000L, DUR);
        assertEquals(61_000L, store.resumePosition(KEY));
        store.savePosition(KEY, 3_419_999L, DUR); // just under 95%
        assertEquals(3_419_999L, store.resumePosition(KEY));
    }

    @Test
    public void doesNotSaveFirstMinute() {
        store.savePosition(KEY, 60_000L, DUR);
        assertEquals(0L, store.resumePosition(KEY));
        assertNull(store.get(KEY));
    }

    @Test
    public void passingTheMinuteLaterAfterRewindingResetsPosition() {
        store.savePosition(KEY, 600_000L, DUR);
        store.savePosition(KEY, 10_000L, DUR);
        assertEquals(0L, store.resumePosition(KEY));
    }

    @Test
    public void finishingClearsTheEntry() {
        store.savePosition(KEY, 600_000L, DUR);
        store.savePosition(KEY, 3_420_000L, DUR); // exactly 95%
        assertNull(store.get(KEY));
        assertEquals(0L, store.resumePosition(KEY));
    }

    @Test
    public void unknownDurationSavesNothing() {
        store.savePosition(KEY, 600_000L, 0L);
        assertEquals(0L, store.resumePosition(KEY));
    }

    @Test
    public void changedMtimeOrSizeDoesNotResume() {
        store.savePosition(KEY, 600_000L, DUR);
        String touched = ResumeStore.key("/storage/AAAA-BBBB/Movie.mkv", 1000L, 43L);
        String resized = ResumeStore.key("/storage/AAAA-BBBB/Movie.mkv", 1001L, 42L);
        assertEquals(0L, store.resumePosition(touched));
        assertEquals(0L, store.resumePosition(resized));
        assertEquals(600_000L, store.resumePosition(KEY));
    }

    @Test
    public void remembersTracksAndKeepsThemWhenPositionChanges() {
        store.saveTracks(KEY, "audio:1", "sub:2");
        store.savePosition(KEY, 600_000L, DUR);
        ResumeStore.Entry e = store.get(KEY);
        assertNotNull(e);
        assertEquals("audio:1", e.audioId);
        assertEquals("sub:2", e.subtitleId);
        assertEquals(600_000L, e.positionMs);
    }

    @Test
    public void tracksKeepPositionAndNullMeansNoChoice() {
        store.savePosition(KEY, 600_000L, DUR);
        store.saveTracks(KEY, null, "sub:off");
        ResumeStore.Entry e = store.get(KEY);
        assertEquals(600_000L, e.positionMs);
        assertNull(e.audioId);
        assertEquals("sub:off", e.subtitleId);
    }

    @Test
    public void survivesAReloadFromTheSameStore() {
        store.savePosition(KEY, 600_000L, DUR);
        assertEquals(600_000L, new ResumeStore(mem).resumePosition(KEY));
    }

    @Test
    public void clearRemovesTheEntry() {
        store.savePosition(KEY, 600_000L, DUR);
        store.clear(KEY);
        assertNull(store.get(KEY));
    }

    @Test
    public void keepsOnlyTwoHundredMostRecentlyUsed() {
        for (int i = 0; i < 205; i++) {
            store.savePosition(ResumeStore.key("/m/" + i + ".mkv", 1L, 1L), 600_000L, DUR);
        }
        assertNull(store.get(ResumeStore.key("/m/0.mkv", 1L, 1L)));
        assertNull(store.get(ResumeStore.key("/m/4.mkv", 1L, 1L)));
        assertNotNull(store.get(ResumeStore.key("/m/5.mkv", 1L, 1L)));
        assertNotNull(store.get(ResumeStore.key("/m/204.mkv", 1L, 1L)));
    }

    @Test
    public void savingAgainRefreshesLruOrder() {
        String first = ResumeStore.key("/m/0.mkv", 1L, 1L);
        store.savePosition(first, 600_000L, DUR);
        for (int i = 1; i < 200; i++) {
            store.savePosition(ResumeStore.key("/m/" + i + ".mkv", 1L, 1L), 600_000L, DUR);
        }
        store.savePosition(first, 700_000L, DUR); // touch the oldest
        store.savePosition(ResumeStore.key("/m/200.mkv", 1L, 1L), 600_000L, DUR); // evicts /m/1
        assertNotNull(store.get(first));
        assertNull(store.get(ResumeStore.key("/m/1.mkv", 1L, 1L)));
    }

    @Test
    public void pathWithTabOrNewlineDoesNotCorruptTheStore() {
        String odd = ResumeStore.key("/m/a\tb\nc.mkv", 1L, 1L);
        store.savePosition(odd, 600_000L, DUR);
        store.savePosition(KEY, 700_000L, DUR);
        assertEquals(600_000L, store.resumePosition(odd));
        assertEquals(700_000L, store.resumePosition(KEY));
    }
}
