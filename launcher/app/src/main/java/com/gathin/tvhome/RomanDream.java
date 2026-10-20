package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.LightingColorFilter;
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
import java.util.Locale;

/**
 * Roman screensaver (docs/plans/roman-theme-v2.md, sections 8 and 9): the cached stone wall, warmed to red,
 * panning very slowly behind gold laurel sprays, a legion standard, thin gold rules, the time in Cinzel and the
 * Roman and Gregorian dates, under a porphyry vignette. Select it with
 * {@code settings put secure screensaver_components} {@link RomanDreamMath#COMPONENT}. One bitmap (the shared
 * 960x540 stone), no wake lock, one slow animator, and a minute-tick receiver for the clock. If the stone
 * cannot be loaded the dark porphyry colour stays.
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

        RomanStyle style = StoneCache.lastStyle();   // the launcher already parsed the config for the shared stone
        try {
            if (style == null) style = Config.load(this).roman;
        } catch (Exception e) {
            Log.w(TAG, "config unreadable, default stone", e);
        }
        if (style == null) {
            style = RomanStyle.of("roman", RomanStyle.STONE_TRAVERTINE, 100, 7, 70, true, true, true, "", 0);
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(RomanStyle.PORPHYRY_DEEP);   // what stays if the stone fails
        stone = new ImageView(this);
        // Warms the wall towards red on the GPU, once; no new bitmap, works on the hardware bitmap too.
        stone.setColorFilter(new LightingColorFilter(0xFFC07A6C, 0x00140000));
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
        if (overlay != null) overlay.refresh();
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
            // The wall moves about 1 px a second; snapping to half pixels redraws it a few times a second, not 60.
            if (stone == null) return;
            float fr = (float) a.getAnimatedValue();
            stone.setTranslationX(RomanDreamMath.snap(RomanDreamMath.offset(fr, tx), RomanDreamMath.PAN_STEP_PX));
            stone.setTranslationY(RomanDreamMath.snap(RomanDreamMath.offset(fr, ty), RomanDreamMath.PAN_STEP_PX));
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
        // Nothing of the dream stays reachable from this service (the system keeps the instance a while).
        stone = null;
        overlay = null;
        // Heap garbage from the dream goes now rather than at the next trim.
        Runtime.getRuntime().gc();
    }

    /**
     * Porphyry vignette, gold laurel sprays, the legion standard, the time and the two dates. All shapes are
     * built in onSizeChanged and the strings in refresh(); nothing is allocated in onDraw.
     */
    private static final class Overlay extends View {

        private final RomanStyle style;
        private final float dp;
        private final Paint vignette = new Paint();
        private final Paint gold = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint goldStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint poleStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint cloth = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint clothShade = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rule = new Paint();
        private final Paint time = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint timeShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint date = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint gregorian = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint legend = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path leaves = new Path();
        private final Path berries = new Path();
        private final Path tip = new Path();
        private final Path fringe = new Path();
        private final Path fulmen = new Path();
        private final RectF clothRect = new RectF();
        private final String legendText = Roman.inscribe(RomanDreamMath.LEGION, 24);
        private float cx, ruleY, ruleHalf, timeY, dateY, gregY, legendY;
        private float poleTop, poleBottom, barY, barHalf;
        private String timeText = "", ampmText = "", dateText = "", gregText = "";

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
            for (Paint p : new Paint[]{time, timeShadow, small, date, gregorian, legend}) {
                if (face != null) p.setTypeface(face);
                p.setTextAlign(Paint.Align.CENTER);
            }
            time.setTextSize(96 * dp);
            time.setLetterSpacing(0.04f);
            time.setColor(RomanStyle.IVORY);
            // A hard shadow (the same text, 2 dp lower, drawn first): a blur would be re-rasterised every frame.
            timeShadow.setTextSize(96 * dp);
            timeShadow.setLetterSpacing(0.04f);
            timeShadow.setColor(RomanStyle.TEXT_SHADOW);
            small.setTextSize(24 * dp);
            small.setLetterSpacing(0.12f);
            small.setColor(RomanStyle.IVORY_DIM_AMPM);
            small.setTextAlign(Paint.Align.LEFT);
            date.setTextSize(22 * dp);
            date.setLetterSpacing(0.16f);
            date.setColor(style.gold);
            gregorian.setTextSize(14 * dp);
            gregorian.setLetterSpacing(0.12f);
            gregorian.setColor(RomanStyle.IVORY_DIM);
            legend.setLetterSpacing(0.08f);
            legend.setColor(style.goldLight);
            shadow.setColor(RomanStyle.RELIEF_SHADOW);
            goldStroke.setStyle(Paint.Style.STROKE);
            goldStroke.setStrokeWidth(1.5f * dp);
            poleStroke.setStyle(Paint.Style.STROKE);
            poleStroke.setStrokeWidth(3 * dp);
            clothShade.setColor(RomanStyle.PORPHYRY);
        }

        /** Reads the clock again; called at start and on every minute tick. */
        void refresh() {
            Calendar c = Calendar.getInstance();
            boolean is24 = DateFormat.is24HourFormat(getContext());
            int h = c.get(Calendar.HOUR_OF_DAY), m = c.get(Calendar.MINUTE);
            timeText = RomanDreamMath.time(h, m, is24);
            ampmText = RomanDreamMath.ampm(h, is24);
            dateText = Roman.inscribe(Roman.today(), 40);
            gregText = RomanDreamMath.gregorian(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1,
                    c.get(Calendar.DAY_OF_MONTH), Locale.getDefault());
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            if (w <= 0 || h <= 0) return;
            cx = w / 2f;
            timeY = h * 0.60f;
            ruleY = h * 0.64f;
            dateY = h * 0.70f;
            gregY = h * 0.75f;
            ruleHalf = w * 0.18f;

            int edge = RomanStyle.withAlpha(RomanStyle.PORPHYRY_DEEP, 0xB0);
            int clear = RomanStyle.withAlpha(RomanStyle.PORPHYRY_DEEP, 0);
            vignette.setShader(new RadialGradient(cx, h / 2f, Math.max(w, h) * 0.75f,
                    new int[]{clear, clear, edge}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            Shader g = new LinearGradient(0, h * 0.05f, 0, h * 0.86f, style.goldLight, style.goldDeep,
                    Shader.TileMode.CLAMP);
            gold.setShader(g);
            goldStroke.setShader(g);
            poleStroke.setShader(g);
            rule.setShader(new LinearGradient(cx - ruleHalf, 0, cx + ruleHalf, 0,
                    new int[]{RomanStyle.withAlpha(style.gold, 0), style.gold, RomanStyle.withAlpha(style.gold, 0)},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));

            // Laurel sprays, left and right: one Path for all the leaves, one for the berries.
            leaves.rewind();
            berries.rewind();
            for (int side = 0; side < 2; side++) {
                boolean right = side == 1;
                for (Laurel.Leaf l : DreamOrnaments.spray(w, h, dp, right)) {
                    double a = Math.toRadians(l.angle);
                    float ax = (float) Math.cos(a), ay = (float) Math.sin(a);
                    float half = l.length / 2;
                    float bx = l.x - ax * half, by = l.y - ay * half;
                    leaves.moveTo(bx, by);
                    leaves.quadTo(l.x - ay * l.width, l.y + ax * l.width, l.x + ax * half, l.y + ay * half);
                    leaves.quadTo(l.x + ay * l.width, l.y - ax * l.width, bx, by);
                    leaves.close();
                }
                float[] b = DreamOrnaments.sprayBerries(w, h, dp, right);
                for (int i = 0; i < b.length; i += 3) {
                    berries.addCircle(b[i], b[i + 1], b[i + 2], Path.Direction.CW);
                }
            }

            // The standard: pole, crossbar, cloth with fringe, spear tip, thunderbolt and legend.
            float[] s = DreamOrnaments.standard(w, h, dp);
            poleTop = s[DreamOrnaments.POLE_TOP];
            poleBottom = s[DreamOrnaments.POLE_BOTTOM];
            barY = s[DreamOrnaments.BAR_Y];
            barHalf = s[DreamOrnaments.BAR_HALF];
            clothRect.set(s[DreamOrnaments.CLOTH_L], s[DreamOrnaments.CLOTH_T],
                    s[DreamOrnaments.CLOTH_R], s[DreamOrnaments.CLOTH_B]);
            cloth.setShader(new LinearGradient(0, clothRect.top, 0, clothRect.bottom,
                    RomanStyle.CRIMSON, RomanStyle.ROMAN_RED, Shader.TileMode.CLAMP));
            tip.rewind();
            tip.moveTo(cx, poleTop - 16 * dp);
            tip.lineTo(cx + 5 * dp, poleTop - 4 * dp);
            tip.lineTo(cx, poleTop + 2 * dp);
            tip.lineTo(cx - 5 * dp, poleTop - 4 * dp);
            tip.close();
            fringe.rewind();
            for (float x = clothRect.left + 4 * dp; x <= clothRect.right - 3 * dp; x += 6 * dp) {
                fringe.moveTo(x, clothRect.bottom);
                fringe.lineTo(x, clothRect.bottom + 7 * dp);
            }
            float[] f = DreamOrnaments.fulmen(cx, clothRect.top + 0.4f * clothRect.height(),
                    0.3f * clothRect.height());
            fulmen.rewind();
            fulmen.moveTo(f[0], f[1]);
            for (int i = 2; i < f.length; i += 2) fulmen.lineTo(f[i], f[i + 1]);
            fulmen.close();
            // Shrink the legend once to fit the cloth (10..14 dp).
            legend.setTextSize(100f);
            float at1 = legend.measureText(legendText) / 100f;
            legend.setTextSize(RomanDreamMath.fitTextSize(clothRect.width() - 14 * dp, at1, 10 * dp, 14 * dp));
            legendY = clothRect.bottom - 10 * dp;
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            c.drawRect(0, 0, w, h, vignette);
            c.save();
            c.translate(0, 2 * dp);
            c.drawPath(leaves, shadow);
            c.drawPath(berries, shadow);
            c.restore();
            c.drawPath(leaves, gold);
            c.drawPath(berries, gold);

            c.drawLine(cx, poleTop, cx, poleBottom, poleStroke);
            c.drawLine(cx - barHalf, barY, cx + barHalf, barY, poleStroke);
            c.drawPath(tip, gold);
            c.drawRect(clothRect.left + dp, clothRect.top + 2 * dp, clothRect.right + dp, clothRect.bottom + 2 * dp,
                    clothShade);
            c.drawRect(clothRect, cloth);
            c.drawRect(clothRect, goldStroke);
            c.drawPath(fringe, goldStroke);
            c.drawPath(fulmen, gold);
            c.drawText(legendText, cx, legendY, legend);

            c.drawText(timeText, cx, timeY + 2 * dp, timeShadow);
            c.drawText(timeText, cx, timeY, time);
            if (!ampmText.isEmpty()) {
                float x = cx + time.measureText(timeText) / 2f + 12 * dp;
                c.drawText(ampmText, x, timeY, small);
            }
            c.drawRect(cx - ruleHalf, ruleY, cx + ruleHalf, ruleY + 2 * dp, rule);
            c.drawRect(cx - ruleHalf * 0.6f, ruleY + 8 * dp, cx + ruleHalf * 0.6f, ruleY + 8 * dp + dp, rule);
            c.drawText(dateText, cx, dateY, date);
            c.drawText(gregText, cx, gregY, gregorian);
        }
    }
}
