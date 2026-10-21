package com.gathin.files;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FocusMemoryTest {

    @Test
    public void recallsAfterGoingDownAndBackUp() {
        FocusMemory m = new FocusMemory();
        m.remember("/storage/A", 7, 120);
        m.remember("/storage/A/sub", 2, 0);
        FocusMemory.Spot a = m.recall("/storage/A");
        assertEquals(7, a.position);
        assertEquals(120, a.offset);
        assertEquals(2, m.recall("/storage/A/sub").position);
    }

    @Test
    public void unknownPathIsZero() {
        FocusMemory m = new FocusMemory();
        assertEquals(0, m.recall("/nope").position);
        assertEquals(0, m.recall("/nope").offset);
        assertEquals(0, m.recall(null).position);
    }

    @Test
    public void rememberingAgainReplaces() {
        FocusMemory m = new FocusMemory();
        m.remember("/a", 3, 10);
        m.remember("/a", 5, 20);
        assertEquals(5, m.recall("/a").position);
        assertEquals(20, m.recall("/a").offset);
    }

    @Test
    public void evictsLeastRecentlyUsedBeyond64() {
        FocusMemory m = new FocusMemory();
        for (int i = 0; i < FocusMemory.CAPACITY; i++) m.remember("/d" + i, i + 1, 0);
        m.recall("/d0");                         // touch: /d1 is now the oldest
        m.remember("/extra", 99, 0);
        assertEquals(1, m.recall("/d0").position);
        assertEquals(0, m.recall("/d1").position);   // evicted
        assertEquals(99, m.recall("/extra").position);
        assertEquals(64, m.recall("/d63").position);
    }
}
