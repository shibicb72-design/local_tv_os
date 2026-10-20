package com.gathin.player;

/**
 * State of the slideshow clock: running or paused, and when the current slide is due to change. Pure Java:
 * the caller passes the time (uptime millis) and does the posting. Pausing keeps what was left of the
 * slide's time, resuming continues from it.
 */
final class SlideTimer {

    static final long DEFAULT_INTERVAL_MS = 8000;

    private final long intervalMs;
    private boolean running;
    private long dueAt;
    private long leftWhenPaused;

    SlideTimer(long intervalMs) {
        this.intervalMs = intervalMs;
        this.leftWhenPaused = intervalMs;
    }

    boolean isRunning() {
        return running;
    }

    /** A new slide is on screen: it gets the full interval. Runs the clock if it is running; if paused, stays paused. */
    void restart(long now) {
        leftWhenPaused = intervalMs;
        dueAt = now + intervalMs;
    }

    /** Starts (or continues, after {@link #pause}) the clock. */
    void resume(long now) {
        if (running) return;
        running = true;
        dueAt = now + leftWhenPaused;
    }

    void pause(long now) {
        if (!running) return;
        running = false;
        leftWhenPaused = Math.max(0, dueAt - now);
    }

    /** Milliseconds until the slide changes, or -1 while paused. */
    long remaining(long now) {
        return running ? Math.max(0, dueAt - now) : -1;
    }

    boolean isDue(long now) {
        return running && now >= dueAt;
    }
}
