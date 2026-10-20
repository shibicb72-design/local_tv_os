package com.gathin.tvhome;

import com.gathin.roman.Laurel;
import com.gathin.roman.Meander;
import com.gathin.roman.RomanLook;
import com.gathin.roman.RomanStyle;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * The Roman frieze (docs/plans/roman-theme-v2.md, section 1.5): a stone-ink slab with gold rules and a Greek
 * key across the screen, broken in the middle by the laurel crest. Not focusable and never uses a layer; all
 * Paths, Paints and Shaders are built when the size or the frieze position changes, never in onDraw.
 */
public final class RomanFrameView extends View {

    private static final float SIDE_DP = 14f;
    private static final float SLAB_DP = 24f;
    /** Half of the gap the crest leaves in the frieze. */
    private static final float GAP_DP = 40f;
    private static final float KEY_GRID_DP = 3f;
    private static final float SHADOW_PX = 2f;
    private static final float PILASTER_W_DP = 28f;
    private static final float PILASTER_SIDE_DP = 14f;
    /** Bases end this far above the bottom edge. */
    private static final float PILASTER_FOOT_DP = 12f;

    private RomanStyle style;
    private int friezeTop = -1;
    private float dp;

    private final Paint slab = new Paint();
    private final Paint rule = new Paint();
    private final Paint relief = new Paint();
    private final Paint keyStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint keyShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crestFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crestStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crestShadowFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crestShadowStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pFill = new Paint();
    private final Paint pLine = new Paint();
    private final Paint pGroove = new Paint();
    private final Paint pLight = new Paint();

    private final Path keyPath = new Path();
    private final Path stems = new Path();
    private final Path leafPath = new Path();
    private final Path berryPath = new Path();
    private final Path tie = new Path();
    private final RectF slabRect = new RectF();
    private final RectF[] ruleRects = new RectF[8];
    private int ruleCount;
    private final RectF arc = new RectF();
    private final RectF tmp = new RectF();
    private Pilaster pilasterL, pilasterR;
    private float crestX, crestY;
    private boolean crestOn;
    private String inscription = "";

    public RomanFrameView(Context context) {
        this(context, null);
    }

    public RomanFrameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        dp = context.getResources().getDisplayMetrics().density;
        for (int i = 0; i < ruleRects.length; i++) ruleRects[i] = new RectF();
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        keyStroke.setStyle(Paint.Style.STROKE);
        keyShadow.setStyle(Paint.Style.STROKE);
        for (Paint p : new Paint[]{keyStroke, keyShadow}) {
            p.setStrokeJoin(Paint.Join.MITER);
            p.setStrokeCap(Paint.Cap.SQUARE);
            p.setStrokeWidth(1.5f * dp);
        }
        crestStroke.setStyle(Paint.Style.STROKE);
        crestShadowStroke.setStyle(Paint.Style.STROKE);
        crestStroke.setStrokeWidth(1.5f * dp);
        crestShadowStroke.setStrokeWidth(1.5f * dp);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(10 * context.getResources().getDisplayMetrics().scaledDensity);
        text.setLetterSpacing(0.12f);
        pLine.setStyle(Paint.Style.STROKE);
        pLine.setStrokeWidth(dp);
        pGroove.setColor(0x47000000);
        pGroove.setStrokeWidth(2 * dp);
        pLight.setColor(0x1AFFF5E0);
        pLight.setStrokeWidth(dp);
    }

    /** Turns the frieze on with this style (colours, meander and crest flags). */
    public void setStyle(RomanStyle s) {
        style = s;
        if (s == null) return;
        slab.setColor(RomanStyle.withAlpha(RomanStyle.STONE_INK, 0x99));
        rule.setColor(s.gold);
        relief.setColor(RomanStyle.RELIEF_SHADOW);
        keyShadow.setColor(RomanStyle.RELIEF_SHADOW);
        crestShadowFill.setColor(RomanStyle.RELIEF_SHADOW);
        crestShadowStroke.setColor(RomanStyle.RELIEF_SHADOW);
        text.setColor(s.goldLight);
        pLine.setColor(s.gold);
        text.setTypeface(RomanLook.typeface(getContext()));
        inscription = s.inscription;
        rebuild();
        invalidate();
    }

    /** Top edge of the frieze slab in px; the same value again does nothing. */
    public void setFriezeTop(int px) {
        if (px == friezeTop) return;
        friezeTop = px;
        rebuild();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        rebuild();
    }

    /** Capital, base and shaft of both pilasters; the shaft is a lighter slab with five carved flutes. */
    private void drawPilasters(Canvas c) {
        drawPilaster(c, pilasterL);
        drawPilaster(c, pilasterR);
    }

    private void drawPilaster(Canvas c, Pilaster p) {
        if (p == null) return;
        pFill.setColor(0x0FF3EBDD);
        rect(c, p.shaft, pFill);
        pFill.setColor(0x33000000);
        c.drawRect(p.shaft[0], p.shaft[1], p.shaft[0] + dp, p.shaft[3], pFill);
        c.drawRect(p.shaft[2] - dp, p.shaft[1], p.shaft[2], p.shaft[3], pFill);
        for (float x : p.fluteX) {
            c.drawLine(x, p.fluteTop, x, p.fluteBottom, pGroove);
            c.drawLine(x + 2 * dp, p.fluteTop, x + 2 * dp, p.fluteBottom, pLight);
        }
        pFill.setColor(RomanStyle.withAlpha(RomanStyle.STONE_INK, 0x80));
        rect(c, p.abacus, pFill);
        rect(c, p.plinth, pFill);
        pFill.setColor(RomanStyle.withAlpha(RomanStyle.STONE_INK, 0x59));
        rect(c, p.echinus, pFill);
        tmp.set(p.torus[0], p.torus[1], p.torus[2], p.torus[3]);
        c.drawRoundRect(tmp, 2.5f * dp, 2.5f * dp, pFill);
        // Gold outline on the abacus, gold top lines on echinus, astragal, torus and plinth.
        tmp.set(p.abacus[0], p.abacus[1], p.abacus[2], p.abacus[3]);
        c.drawRect(tmp, pLine);
        topLine(c, p.echinus);
        topLine(c, p.astragal);
        topLine(c, p.torus);
        topLine(c, p.plinth);
    }

    private void topLine(Canvas c, float[] r) {
        c.drawLine(r[0], r[1], r[2], r[1], pLine);
    }

    private void rect(Canvas c, float[] r, Paint paint) {
        c.drawRect(r[0], r[1], r[2], r[3], paint);
    }

    private void rebuild() {
        int w = getWidth();
        if (style == null || w <= 0 || friezeTop < 0) return;
        float left = SIDE_DP * dp, right = w - left;
        float top = friezeTop, h = SLAB_DP * dp, bottom = top + h;
        float mid = top + h / 2;
        crestOn = style.crest;
        crestX = w / 2f;
        crestY = mid;
        slabRect.set(left, top, right, bottom);

        // Pilasters run from under the frieze (past its relief line) to just above the bottom edge.
        pilasterL = pilasterR = null;
        if (style.pilasters) {
            float pw = PILASTER_W_DP * dp, pTop = bottom + SHADOW_PX, pBottom = getHeight() - PILASTER_FOOT_DP * dp;
            pilasterL = Pilaster.layout(PILASTER_SIDE_DP * dp, pTop, pBottom, pw, dp);
            pilasterR = Pilaster.layout(w - PILASTER_SIDE_DP * dp - pw, pTop, pBottom, pw, dp);
        }

        // Rules and key stop short of the crest on both sides; without the crest they run through.
        float gap = GAP_DP * dp;
        float[] from = crestOn ? new float[]{left, crestX + gap} : new float[]{left};
        float[] to = crestOn ? new float[]{crestX - gap, right} : new float[]{right};
        ruleCount = 0;
        keyPath.rewind();
        float g = KEY_GRID_DP * dp;
        float keyTop = mid - 2.5f * g;
        for (int k = 0; k < from.length; k++) {
            ruleRects[ruleCount++].set(from[k], top, to[k], top + 2);
            ruleRects[ruleCount++].set(from[k], bottom - 2, to[k], bottom);
            ruleRects[ruleCount++].set(from[k], bottom, to[k], bottom + 2);
            if (style.meander) {
                float[] pts = Meander.polyline(from[k], keyTop, to[k] - from[k], g);
                keyPath.moveTo(pts[0], pts[1]);
                for (int i = 2; i < pts.length; i += 2) keyPath.lineTo(pts[i], pts[i + 1]);
            }
        }
        keyStroke.setShader(new LinearGradient(0, keyTop, 0, keyTop + 5 * g,
                style.goldLight, style.goldDeep, Shader.TileMode.CLAMP));

        stems.rewind();
        leafPath.rewind();
        berryPath.rewind();
        tie.rewind();
        if (crestOn) buildCrest();
    }

    private void buildCrest() {
        float r = Laurel.STEM_DP * dp;
        Shader gold = new LinearGradient(0, crestY - r, 0, crestY + r, style.goldLight, style.goldDeep,
                Shader.TileMode.CLAMP);
        crestFill.setShader(gold);
        crestStroke.setShader(gold);

        arc.set(crestX - r, crestY - r, crestX + r, crestY + r);
        stems.addArc(arc, Laurel.START_DEG, Laurel.END_DEG - Laurel.START_DEG);
        stems.addArc(arc, 180f - Laurel.END_DEG, Laurel.END_DEG - Laurel.START_DEG);

        for (Laurel.Leaf l : Laurel.leaves(crestX, crestY, r, dp)) {
            double a = Math.toRadians(l.angle);
            float ax = (float) Math.cos(a), ay = (float) Math.sin(a);
            float half = l.length / 2;
            float bx = l.x - ax * half, by = l.y - ay * half;
            float tx = l.x + ax * half, ty = l.y + ay * half;
            // A quad's belly is half its control offset, so the offset is the full leaf width.
            leafPath.moveTo(bx, by);
            leafPath.quadTo(l.x - ay * l.width, l.y + ax * l.width, tx, ty);
            leafPath.quadTo(l.x + ay * l.width, l.y - ax * l.width, bx, by);
            leafPath.close();
        }
        float[] b = Laurel.berries(crestX, crestY, r, dp);
        for (int i = 0; i < b.length; i += 3) berryPath.addCircle(b[i], b[i + 1], b[i + 2], Path.Direction.CW);
        tie.addRoundRect(crestX - 3 * dp, crestY + r - 1.5f * dp, crestX + 3 * dp, crestY + r + 1.5f * dp,
                1.5f * dp, 1.5f * dp, Path.Direction.CW);

        // The inscription must fit inside the stem circle.
        float maxW = 2 * (r - 5 * dp);
        float measured = text.measureText(inscription);
        if (measured > maxW) text.setTextSize(text.getTextSize() * maxW / measured);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (style == null || friezeTop < 0) return;
        c.drawRect(slabRect, slab);
        for (int i = 0; i < ruleCount; i++) c.drawRect(ruleRects[i], i % 3 == 2 ? relief : rule);
        if (style.meander) {
            c.save();
            c.translate(0, SHADOW_PX);
            c.drawPath(keyPath, keyShadow);
            c.restore();
            c.drawPath(keyPath, keyStroke);
        }
        if (crestOn) {
            c.save();
            c.translate(0, SHADOW_PX);
            c.drawPath(stems, crestShadowStroke);
            c.drawPath(leafPath, crestShadowFill);
            c.drawPath(berryPath, crestShadowFill);
            c.drawPath(tie, crestShadowFill);
            c.restore();
            c.drawPath(stems, crestStroke);
            c.drawPath(leafPath, crestFill);
            c.drawPath(berryPath, crestFill);
            c.drawPath(tie, crestFill);
            if (!inscription.isEmpty()) c.drawText(inscription, crestX, crestY + 3.5f * dp, text);
        }
        drawPilasters(c);
    }
}
