package com.gathin.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

import androidx.media3.common.text.Cue;
import androidx.media3.common.util.UnstableApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Draws the current subtitle cues over the video. Text cues: ivory text with a dark outline (a stroke layout drawn
 * first, the fill layout on top), centred, bottom margin 6% of the height, stacked upwards. Bitmap cues (PGS,
 * VobSub, DVB): scaled to the cue's own position and size. All layouts and rectangles are built when the cues or
 * the size change, so {@link #onDraw} allocates nothing. Text styling (ASS colours, italics) is dropped on purpose.
 * Invisible (still laid out, so it knows its size) while there is nothing to draw.
 */
@UnstableApi
final class CueView extends View {

    private static final int IVORY = 0xFFF2E8D0;
    private static final int OUTLINE = 0xFF14100C;
    private static final float OUTLINE_DP = 2f;
    private static final float TEXT_HEIGHT_FRACTION = 0.052f;
    private static final float BOTTOM_MARGIN_FRACTION = 0.06f;
    private static final float TEXT_WIDTH_FRACTION = 0.8f;

    private final float density;
    private final TextPaint fillPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint strokePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    private List<Cue> cues = Collections.emptyList();
    private final List<StaticLayout> fillLayouts = new ArrayList<>();
    private final List<StaticLayout> strokeLayouts = new ArrayList<>();
    private final List<Float> textTops = new ArrayList<>();
    private final List<Bitmap> bitmaps = new ArrayList<>();
    private final List<Rect> bitmapRects = new ArrayList<>();

    CueView(Context ctx) {
        super(ctx);
        density = ctx.getResources().getDisplayMetrics().density;
        setFocusable(false);
        setClickable(false);
        setVisibility(INVISIBLE);
        fillPaint.setColor(IVORY);
        strokePaint.setColor(OUTLINE);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        // The stroke is centred on the glyph edge, so double the width for 2 dp showing outside.
        strokePaint.setStrokeWidth(OUTLINE_DP * 2f * density);
    }

    void setCues(List<Cue> newCues) {
        cues = newCues == null ? Collections.<Cue>emptyList() : newCues;
        rebuild();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuild();
    }

    private void rebuild() {
        fillLayouts.clear();
        strokeLayouts.clear();
        textTops.clear();
        bitmaps.clear();
        bitmapRects.clear();
        int w = getWidth();
        int h = getHeight();
        if (w > 0 && h > 0) {
            float textSize = h * TEXT_HEIGHT_FRACTION;
            fillPaint.setTextSize(textSize);
            strokePaint.setTextSize(textSize);
            int textWidth = Math.round(w * TEXT_WIDTH_FRACTION);
            float bottom = h - h * BOTTOM_MARGIN_FRACTION;
            // Stack upwards from the bottom margin: the last cue is lowest, the first one on top.
            for (int i = cues.size() - 1; i >= 0; i--) {
                Cue cue = cues.get(i);
                if (cue.bitmap != null) {
                    bitmaps.add(cue.bitmap);
                    bitmapRects.add(bitmapRect(cue, w, h));
                } else if (cue.text != null && cue.text.length() > 0) {
                    String text = cue.text.toString().trim();
                    if (text.isEmpty()) continue;
                    StaticLayout fill = layout(text, fillPaint, textWidth);
                    StaticLayout stroke = layout(text, strokePaint, textWidth);
                    bottom -= fill.getHeight();
                    fillLayouts.add(fill);
                    strokeLayouts.add(stroke);
                    textTops.add(bottom);
                }
            }
        }
        setVisibility(fillLayouts.isEmpty() && bitmaps.isEmpty() ? INVISIBLE : VISIBLE);
        invalidate();
    }

    private static StaticLayout layout(String text, TextPaint paint, int width) {
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .build();
    }

    /** Position, line and size are fractions of the view; PGS/DVB cues set all of them, others fall back to centre-bottom. */
    private static Rect bitmapRect(Cue cue, int w, int h) {
        Bitmap b = cue.bitmap;
        float width = cue.size != Cue.DIMEN_UNSET ? cue.size * w : b.getWidth();
        float height = cue.bitmapHeight != Cue.DIMEN_UNSET
                ? cue.bitmapHeight * h
                : width * b.getHeight() / b.getWidth();
        float left = cue.position != Cue.DIMEN_UNSET ? cue.position * w : (w - width) / 2f;
        if (cue.position != Cue.DIMEN_UNSET) {
            if (cue.positionAnchor == Cue.ANCHOR_TYPE_MIDDLE) left -= width / 2f;
            else if (cue.positionAnchor == Cue.ANCHOR_TYPE_END) left -= width;
        }
        float top = h - height - h * BOTTOM_MARGIN_FRACTION;
        if (cue.line != Cue.DIMEN_UNSET && cue.lineType == Cue.LINE_TYPE_FRACTION) {
            top = cue.line * h;
            if (cue.lineAnchor == Cue.ANCHOR_TYPE_MIDDLE) top -= height / 2f;
            else if (cue.lineAnchor == Cue.ANCHOR_TYPE_END) top -= height;
        }
        return new Rect(Math.round(left), Math.round(top), Math.round(left + width), Math.round(top + height));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float left = (getWidth() - getWidth() * TEXT_WIDTH_FRACTION) / 2f;
        for (int i = 0; i < fillLayouts.size(); i++) {
            canvas.save();
            canvas.translate(left, textTops.get(i));
            strokeLayouts.get(i).draw(canvas);
            fillLayouts.get(i).draw(canvas);
            canvas.restore();
        }
        for (int i = 0; i < bitmaps.size(); i++) {
            canvas.drawBitmap(bitmaps.get(i), null, bitmapRects.get(i), bitmapPaint);
        }
    }
}
