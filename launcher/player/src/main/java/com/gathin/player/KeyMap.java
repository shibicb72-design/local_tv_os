package com.gathin.player;

/**
 * Pure remote-key mapping. Key codes are the values of {@code android.view.KeyEvent} (copied here so the class
 * runs on the JVM). The activity shows the overlay on any key by itself; this only says what the key does.
 */
final class KeyMap {

    enum Action {
        NONE,
        TOGGLE_PLAY,
        PLAY,
        PAUSE,
        SEEK_BACK_SHORT,
        SEEK_FORWARD_SHORT,
        SEEK_BACK_LONG,
        SEEK_FORWARD_LONG,
        OPEN_TRACKS,
        SHOW_OVERLAY,
        NEXT,
        PREVIOUS,
        CLOSE_MENU,
        HIDE_OVERLAY,
        SAVE_AND_EXIT
    }

    static final int KEYCODE_BACK = 4;
    static final int KEYCODE_DPAD_UP = 19;
    static final int KEYCODE_DPAD_DOWN = 20;
    static final int KEYCODE_DPAD_LEFT = 21;
    static final int KEYCODE_DPAD_RIGHT = 22;
    static final int KEYCODE_DPAD_CENTER = 23;
    static final int KEYCODE_ENTER = 66;
    static final int KEYCODE_MENU = 82;
    static final int KEYCODE_MEDIA_PLAY_PAUSE = 85;
    static final int KEYCODE_MEDIA_NEXT = 87;
    static final int KEYCODE_MEDIA_PREVIOUS = 88;
    static final int KEYCODE_MEDIA_REWIND = 89;
    static final int KEYCODE_MEDIA_FAST_FORWARD = 90;
    static final int KEYCODE_NUMPAD_ENTER = 160;
    static final int KEYCODE_MEDIA_PLAY = 126;
    static final int KEYCODE_MEDIA_PAUSE = 127;

    private KeyMap() {}

    /**
     * @param repeatCount 0 for the first press, greater than 0 while the key is held
     * @param menuOpen    the tracks menu is showing; it handles its own keys, only BACK is ours
     */
    static Action map(int keyCode, int repeatCount, boolean overlayVisible, boolean menuOpen) {
        if (keyCode == KEYCODE_BACK) {
            if (menuOpen) return Action.CLOSE_MENU;
            if (overlayVisible) return Action.HIDE_OVERLAY;
            return Action.SAVE_AND_EXIT;
        }
        if (menuOpen) return Action.NONE;
        boolean held = repeatCount > 0;
        switch (keyCode) {
            case KEYCODE_DPAD_CENTER:
            case KEYCODE_ENTER:
            case KEYCODE_NUMPAD_ENTER:
            case KEYCODE_MEDIA_PLAY_PAUSE:
                return Action.TOGGLE_PLAY;
            case KEYCODE_MEDIA_PLAY:
                return Action.PLAY;
            case KEYCODE_MEDIA_PAUSE:
                return Action.PAUSE;
            case KEYCODE_DPAD_LEFT:
                return held ? Action.SEEK_BACK_LONG : Action.SEEK_BACK_SHORT;
            case KEYCODE_DPAD_RIGHT:
                return held ? Action.SEEK_FORWARD_LONG : Action.SEEK_FORWARD_SHORT;
            case KEYCODE_MEDIA_REWIND:
                return Action.SEEK_BACK_LONG;
            case KEYCODE_MEDIA_FAST_FORWARD:
                return Action.SEEK_FORWARD_LONG;
            case KEYCODE_DPAD_DOWN:
            case KEYCODE_MENU:
                return Action.OPEN_TRACKS;
            case KEYCODE_DPAD_UP:
                return Action.SHOW_OVERLAY;
            case KEYCODE_MEDIA_NEXT:
                return Action.NEXT;
            case KEYCODE_MEDIA_PREVIOUS:
                return Action.PREVIOUS;
            default:
                return Action.NONE;
        }
    }

    /** Seek step in ms for a seek action (negative = back); 0 for any other action. */
    static long seekDelta(Action a) {
        switch (a) {
            case SEEK_BACK_SHORT: return -SeekMath.SHORT_STEP_MS;
            case SEEK_FORWARD_SHORT: return SeekMath.SHORT_STEP_MS;
            case SEEK_BACK_LONG: return -SeekMath.LONG_STEP_MS;
            case SEEK_FORWARD_LONG: return SeekMath.LONG_STEP_MS;
            default: return 0L;
        }
    }
}
