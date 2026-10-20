package com.gathin.player;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gathin.roman.RomanRows;
import com.gathin.roman.RomanStyle;

/**
 * Playback overlay: Cinzel title, gold progress bar, times and a state glyph at the bottom of the screen.
 * Shown on any key, hidden after 4 s. The only callbacks it ever posts are the hide timer and the 1 Hz tick,
 * and both are removed on hide and in {@link #release()}, so nothing runs while it is hidden.
 */
final class Overlay {

    /** Playback state read by the overlay. */
    interface Source {
        long positionMs();

        long durationMs();

        boolean playing();
    }

    private static final long HIDE_AFTER_MS = 4000L;
    private static final long TICK_MS = 1000L;
    private static final long FADE_MS = 250L;

    private final LinearLayout box;
    private final TextView title;
    private final TextView glyph;
    private final TextView times;
    private final ProgressBarView bar;
    private final Source source;
    private boolean visible;

    private final Runnable hideTask = this::hide;
    private final Runnable tickTask = new Runnable() {
        @Override
        public void run() {
            if (!visible) return;
            refresh();
            box.postDelayed(this, TICK_MS);
        }
    };

    Overlay(Context ctx, FrameLayout parent, Source source) {
        this.source = source;
        RomanStyle s = RomanStyle.defaults();
        float density = ctx.getResources().getDisplayMetrics().density;
        int side = Math.round(48 * density);
        int vert = Math.round(24 * density);

        box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(side, vert, side, vert);
        box.setBackgroundColor(RomanStyle.withAlpha(RomanStyle.STONE_INK, 0xC0));
        box.setVisibility(View.GONE);
        box.setAlpha(0f);

        title = new TextView(ctx);
        RomanRows.title(title, s);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        box.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = Math.round(12 * density);
        box.addView(row, rowLp);

        // System sans: Cinzel has no pause/play glyphs.
        glyph = new TextView(ctx);
        glyph.setTypeface(Typeface.SANS_SERIF);
        glyph.setTextColor(s.gold);
        glyph.setTextSize(20f);
        glyph.setMinWidth(Math.round(32 * density));
        row.addView(glyph);

        bar = new ProgressBarView(ctx, s.gold, RomanStyle.withAlpha(s.gold, 0x4D));
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(0, Math.round(6 * density), 1f);
        barLp.leftMargin = Math.round(12 * density);
        barLp.rightMargin = Math.round(16 * density);
        row.addView(bar, barLp);

        times = new TextView(ctx);
        times.setTypeface(Typeface.SANS_SERIF);
        times.setTextColor(RomanStyle.IVORY);
        times.setTextSize(16f);
        row.addView(times);

        parent.addView(box, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
    }

    void setTitle(String text) {
        title.setText(text == null ? "" : text);
    }

    boolean isVisible() {
        return visible;
    }

    /** Shows (or keeps showing) the overlay and restarts the 4 s hide timer. */
    void show() {
        box.removeCallbacks(hideTask);
        box.removeCallbacks(tickTask);
        refresh();
        if (!visible) {
            visible = true;
            box.animate().cancel();
            box.setVisibility(View.VISIBLE);
            if (ValueAnimator.areAnimatorsEnabled()) {
                box.animate().alpha(1f).setDuration(FADE_MS).start();
            } else {
                box.setAlpha(1f);
            }
        }
        box.postDelayed(tickTask, TICK_MS);
        box.postDelayed(hideTask, HIDE_AFTER_MS);
    }

    void hide() {
        box.removeCallbacks(hideTask);
        box.removeCallbacks(tickTask);
        if (!visible) return;
        visible = false;
        box.animate().cancel();
        if (ValueAnimator.areAnimatorsEnabled()) {
            // The flag guards the end action: a show() during the fade must not end up GONE.
            box.animate().alpha(0f).setDuration(FADE_MS).withEndAction(() -> {
                if (!visible) box.setVisibility(View.GONE);
            }).start();
        } else {
            box.setAlpha(0f);
            box.setVisibility(View.GONE);
        }
    }

    /** Updates the glyph, bar and times from the source. Cheap; called on show and each tick. */
    void refresh() {
        long pos = Math.max(0L, source.positionMs());
        long dur = Math.max(0L, source.durationMs());
        glyph.setText(source.playing() ? "▶" : "❚❚");
        bar.set(pos, dur);
        times.setText(dur > 0 ? TimeFormat.format(pos) + " / " + TimeFormat.format(dur) : TimeFormat.format(pos));
    }

    /** Stops every callback and animation; call from onStop. */
    void release() {
        box.removeCallbacks(hideTask);
        box.removeCallbacks(tickTask);
        box.animate().cancel();
        visible = false;
        box.setAlpha(0f);
        box.setVisibility(View.GONE);
    }

    /** Two rectangles: the track and the filled part. Nothing is allocated in onDraw. */
    private static final class ProgressBarView extends View {
        private final Paint trackPaint = new Paint();
        private final Paint fillPaint = new Paint();
        private long position;
        private long duration;

        ProgressBarView(Context ctx, int fill, int track) {
            super(ctx);
            fillPaint.setColor(fill);
            trackPaint.setColor(track);
        }

        void set(long positionMs, long durationMs) {
            if (positionMs == position && durationMs == duration) return;
            position = positionMs;
            duration = durationMs;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth();
            int h = getHeight();
            c.drawRect(0, 0, w, h, trackPaint);
            int fill = OverlayMath.fillWidth(position, duration, w);
            if (fill > 0) c.drawRect(0, 0, fill, h, fillPaint);
        }
    }
}
