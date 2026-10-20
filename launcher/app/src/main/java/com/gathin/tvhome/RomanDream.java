package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.service.dreams.DreamService;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.Calendar;

/**
 * Roman screensaver (docs/plans/roman-theme-v2.md, section 8): the cached stone wall panning very slowly behind
 * a gold laurel, thin gold rules, the time in Cinzel and the Roman date, under a soft vignette. Select it with
 * {@code settings put secure screensaver_components} {@link RomanDreamMath#COMPONENT}. One bitmap (the shared
 * 960x540 stone), no wake lock, one slow animator, and a minute-tick receiver for the clock. If the stone
 * cannot be loaded the dark stone colour stays.
 */
public class RomanDream extends DreamService {

    private static final String TAG = "RomanDream";
    /** Same value as {@link RomanDreamMath#COMPONENT}, for callers that already import this class. */
    public static final String COMPONENT = RomanDreamMath.COMPONENT;

    private ImageView stone;
    private Overlay overlay;
    private ValueAnimator pan;
    private boolean receiverOn;
    private boolean dead;

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (overlay != null) overlay.refresh();
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(false);

        RomanStyle style = null;
        try {
            style = Config.load(this).roman;
        } catch (Exception e) {
            Log.w(TAG, "config unreadable, default stone", e);
        }
        if (style == null) {
            style = RomanStyle.of("roman", RomanStyle.STONE_TRAVERTINE, 100, 7, 70, true, true, true, "", 0);
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(RomanStyle.TRAVERTINE_DEEP);   // what stays if the stone fails
        stone = new ImageView(this);
        stone.setScaleType(ImageView.ScaleType.FIT_XY);
        stone.setScaleX(RomanDreamMath.SCALE);
        stone.setScaleY(RomanDreamMath.SCALE);
        stone.setAlpha(0f);
        root.addView(stone, new FrameLayout.LayoutParams(-1, -1));
        overlay = new Overlay(this, style);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        if (!RomanStyle.STONE_NONE.equals(style.stone)) loadStone(style);
    }

    private void loadStone(RomanStyle style) {
        try {
            StoneCache.load(this, style, bmp -> {
                if (dead || stone == null || bmp == null || bmp.isRecycled()) return;
                stone.setImageBitmap(bmp);
                stone.animate().alpha(1f).setDuration(1500).start();
                stone.post(this::startPan);
            });
        } catch (Exception | OutOfMemoryError e) {
            Log.w(TAG, "stone failed, dark colour stays", e);
        }
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        overlay.refresh();
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(tick, f);
        receiverOn = true;
    }

    /** One animator for the whole dream: the stone moves from one corner to the other and back, over minutes. */
    private void startPan() {
        if (dead || stone == null || pan != null) return;
        final float tx = RomanDreamMath.travel(stone.getWidth(), RomanDreamMath.SCALE);
        final float ty = RomanDreamMath.travel(stone.getHeight(), RomanDreamMath.SCALE);
        pan = ValueAnimator.ofFloat(0f, 1f);
        pan.setDuration(RomanDreamMath.PAN_MS);
        pan.setInterpolator(new LinearInterpolator());
        pan.setRepeatCount(ValueAnimator.INFINITE);
        pan.setRepeatMode(ValueAnimator.REVERSE);
        pan.addUpdateListener(a -> {
            float fr = (float) a.getAnimatedValue();
            stone.setTranslationX(RomanDreamMath.offset(fr, tx));
            stone.setTranslationY(RomanDreamMath.offset(fr, ty));
        });
        pan.start();
    }

    @Override
    public void onDreamingStopped() {
        teardown();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        teardown();
        super.onDetachedFromWindow();
    }

    /**
     * Stops the animator and the receiver and lets go of the stone. The bitmap itself is the launcher's shared
     * cached one, so it is only dereferenced here, not recycled: Home may be showing the same bitmap.
     */
    private void teardown() {
        dead = true;
        if (receiverOn) {
            receiverOn = false;
            try {
                unregisterReceiver(tick);
            } catch (IllegalArgumentException ignored) {
                // already gone
            }
        }
        if (pan != null) {
            pan.cancel();
            pan = null;
        }
        if (stone != null) {
            stone.animate().cancel();
            stone.setImageDrawable(null);
        }
    }

    /** Vignette, gold laurel and rules, the time and the Roman date. All shapes are built in onSizeChanged. */
    private static final class Overlay extends View {

        private final RomanStyle style;
        private final float dp;
        private final Paint vignette = new Paint();
        private final Paint gold = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint goldStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shadowStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rule = new Paint();
        private final Paint time = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint date = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path stems = new Path();
        private final Path leaves = new Path();
        private final Path berries = new Path();
        private final RectF arc = new RectF();
        private float cx, ruleY, ruleHalf, timeY, dateY;
        private String timeText = "", ampmText = "", dateText = "";

        Overlay(Context ctx, RomanStyle style) {
            super(ctx);
            this.style = style;
            dp = ctx.getResources().getDisplayMetrics().density;
            setFocusable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            android.graphics.Typeface face = null;
            try {
                face = RomanLook.typeface(ctx);
            } catch (Exception e) {
                Log.w(TAG, "Cinzel unavailable, system font", e);
            }
            for (Paint p : new Paint[]{time, small, date}) {
                if (face != null) p.setTypeface(face);
                p.setTextAlign(Paint.Align.CENTER);
            }
            time.setTextSize(96 * dp);
            time.setLetterSpacing(0.04f);
            time.setColor(RomanStyle.IVORY);
            time.setShadowLayer(4 * dp, 0, 2 * dp, RomanStyle.TEXT_SHADOW);
            small.setTextSize(24 * dp);
            small.setLetterSpacing(0.12f);
            small.setColor(RomanStyle.IVORY_DIM_AMPM);
            small.setTextAlign(Paint.Align.LEFT);
            date.setTextSize(22 * dp);
            date.setLetterSpacing(0.16f);
            date.setColor(style.goldLight);
            shadow.setColor(RomanStyle.RELIEF_SHADOW);
            shadowStroke.setColor(RomanStyle.RELIEF_SHADOW);
            for (Paint p : new Paint[]{goldStroke, shadowStroke}) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.5f * dp);
            }
        }

        /** Reads the clock again; called at start and on every minute tick. */
        void refresh() {
            Calendar c = Calendar.getInstance();
            boolean is24 = DateFormat.is24HourFormat(getContext());
            int h = c.get(Calendar.HOUR_OF_DAY), m = c.get(Calendar.MINUTE);
            timeText = RomanDreamMath.time(h, m, is24);
            ampmText = RomanDreamMath.ampm(h, is24);
            dateText = Roman.inscribe(Roman.today(), 40);
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            if (w <= 0 || h <= 0) return;
            cx = w / 2f;
            float r = Laurel.STEM_DP * 1.7f * dp;
            float cy = h * 0.27f;
            timeY = h * 0.60f;
            ruleY = h * 0.66f;
            dateY = h * 0.74f;
            ruleHalf = w * 0.18f;

            vignette.setShader(new RadialGradient(cx, h / 2f, Math.max(w, h) * 0.75f,
                    new int[]{0x00000000, 0x00000000, 0x99000000}, new float[]{0f, 0.55f, 1f},
                    Shader.TileMode.CLAMP));
            Shader g = new LinearGradient(0, cy - r, 0, cy + r, style.goldLight, style.goldDeep,
                    Shader.TileMode.CLAMP);
            gold.setShader(g);
            goldStroke.setShader(g);
            rule.setShader(new LinearGradient(cx - ruleHalf, 0, cx + ruleHalf, 0,
                    new int[]{RomanStyle.withAlpha(style.gold, 0), style.gold, RomanStyle.withAlpha(style.gold, 0)},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));

            stems.rewind();
            leaves.rewind();
            berries.rewind();
            arc.set(cx - r, cy - r, cx + r, cy + r);
            stems.addArc(arc, Laurel.START_DEG, Laurel.END_DEG - Laurel.START_DEG);
            stems.addArc(arc, 180f - Laurel.END_DEG, Laurel.END_DEG - Laurel.START_DEG);
            for (Laurel.Leaf l : Laurel.leaves(cx, cy, r, dp * 1.7f)) {
                double a = Math.toRadians(l.angle);
                float ax = (float) Math.cos(a), ay = (float) Math.sin(a);
                float half = l.length / 2;
                float bx = l.x - ax * half, by = l.y - ay * half;
                leaves.moveTo(bx, by);
                leaves.quadTo(l.x - ay * l.width, l.y + ax * l.width, l.x + ax * half, l.y + ay * half);
                leaves.quadTo(l.x + ay * l.width, l.y - ax * l.width, bx, by);
                leaves.close();
            }
            float[] b = Laurel.berries(cx, cy, r, dp * 1.7f);
            for (int i = 0; i < b.length; i += 3) berries.addCircle(b[i], b[i + 1], b[i + 2], Path.Direction.CW);
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            c.drawRect(0, 0, w, h, vignette);
            c.save();
            c.translate(0, 2 * dp);
            c.drawPath(stems, shadowStroke);
            c.drawPath(leaves, shadow);
            c.drawPath(berries, shadow);
            c.restore();
            c.drawPath(stems, goldStroke);
            c.drawPath(leaves, gold);
            c.drawPath(berries, gold);

            c.drawText(timeText, cx, timeY, time);
            if (!ampmText.isEmpty()) {
                float x = cx + time.measureText(timeText) / 2f + 12 * dp;
                c.drawText(ampmText, x, timeY, small);
            }
            c.drawRect(cx - ruleHalf, ruleY, cx + ruleHalf, ruleY + 2 * dp, rule);
            c.drawRect(cx - ruleHalf * 0.6f, ruleY + 8 * dp, cx + ruleHalf * 0.6f, ruleY + 8 * dp + dp, rule);
            c.drawText(dateText, cx, dateY + 8 * dp, date);
        }
    }
}
