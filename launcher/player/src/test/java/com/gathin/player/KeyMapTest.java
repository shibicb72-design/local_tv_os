package com.gathin.player;

import static com.gathin.player.KeyMap.Action;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class KeyMapTest {

    private static Action map(int key) {
        return KeyMap.map(key, 0, false, false);
    }

    @Test
    public void okAndPlayKeysTogglePlay() {
        int[] keys = {KeyMap.KEYCODE_DPAD_CENTER, KeyMap.KEYCODE_ENTER, KeyMap.KEYCODE_NUMPAD_ENTER,
                KeyMap.KEYCODE_MEDIA_PLAY_PAUSE, KeyMap.KEYCODE_MEDIA_PLAY, KeyMap.KEYCODE_MEDIA_PAUSE};
        for (int k : keys) assertEquals(Action.TOGGLE_PLAY, map(k));
    }

    @Test
    public void leftRightSeekTenSecondsThenThirtyWhenHeld() {
        assertEquals(Action.SEEK_BACK_SHORT, KeyMap.map(KeyMap.KEYCODE_DPAD_LEFT, 0, false, false));
        assertEquals(Action.SEEK_FORWARD_SHORT, KeyMap.map(KeyMap.KEYCODE_DPAD_RIGHT, 0, true, false));
        assertEquals(Action.SEEK_BACK_LONG, KeyMap.map(KeyMap.KEYCODE_DPAD_LEFT, 1, false, false));
        assertEquals(Action.SEEK_FORWARD_LONG, KeyMap.map(KeyMap.KEYCODE_DPAD_RIGHT, 5, true, false));
    }

    @Test
    public void rewindAndFastForwardAlwaysSeekThirty() {
        assertEquals(Action.SEEK_BACK_LONG, map(KeyMap.KEYCODE_MEDIA_REWIND));
        assertEquals(Action.SEEK_FORWARD_LONG, KeyMap.map(KeyMap.KEYCODE_MEDIA_FAST_FORWARD, 3, false, false));
    }

    @Test
    public void seekDeltas() {
        assertEquals(-10_000L, KeyMap.seekDelta(Action.SEEK_BACK_SHORT));
        assertEquals(10_000L, KeyMap.seekDelta(Action.SEEK_FORWARD_SHORT));
        assertEquals(-30_000L, KeyMap.seekDelta(Action.SEEK_BACK_LONG));
        assertEquals(30_000L, KeyMap.seekDelta(Action.SEEK_FORWARD_LONG));
        assertEquals(0L, KeyMap.seekDelta(Action.TOGGLE_PLAY));
    }

    @Test
    public void downAndMenuOpenTracks() {
        assertEquals(Action.OPEN_TRACKS, map(KeyMap.KEYCODE_DPAD_DOWN));
        assertEquals(Action.OPEN_TRACKS, map(KeyMap.KEYCODE_MENU));
    }

    @Test
    public void upShowsOverlay() {
        assertEquals(Action.SHOW_OVERLAY, map(KeyMap.KEYCODE_DPAD_UP));
    }

    @Test
    public void nextAndPrevious() {
        assertEquals(Action.NEXT, map(KeyMap.KEYCODE_MEDIA_NEXT));
        assertEquals(Action.PREVIOUS, map(KeyMap.KEYCODE_MEDIA_PREVIOUS));
    }

    @Test
    public void backClosesMenuThenHidesOverlayThenExits() {
        assertEquals(Action.CLOSE_MENU, KeyMap.map(KeyMap.KEYCODE_BACK, 0, true, true));
        assertEquals(Action.CLOSE_MENU, KeyMap.map(KeyMap.KEYCODE_BACK, 0, false, true));
        assertEquals(Action.HIDE_OVERLAY, KeyMap.map(KeyMap.KEYCODE_BACK, 0, true, false));
        assertEquals(Action.SAVE_AND_EXIT, KeyMap.map(KeyMap.KEYCODE_BACK, 0, false, false));
    }

    @Test
    public void menuOpenLeavesOtherKeysToTheMenu() {
        assertEquals(Action.NONE, KeyMap.map(KeyMap.KEYCODE_DPAD_CENTER, 0, true, true));
        assertEquals(Action.NONE, KeyMap.map(KeyMap.KEYCODE_DPAD_LEFT, 0, true, true));
        assertEquals(Action.NONE, KeyMap.map(KeyMap.KEYCODE_MEDIA_NEXT, 0, true, true));
    }

    @Test
    public void unknownKeyDoesNothing() {
        assertEquals(Action.NONE, map(29)); // KEYCODE_A
    }
}
