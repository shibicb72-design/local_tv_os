package com.gathin.tvhome;

import com.gathin.roman.RomanStyle;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** Draws the tvOS style lighter band behind the first (dock) row and adds a gap below it. */
public class DockDecoration extends RecyclerView.ItemDecoration {

    private final int dockSize;
    private final float density;
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    /** Roman look v2, or null for the old band. */
    private final RomanStyle roman;

    DockDecoration(int dockSize, float density, RomanStyle roman) {
        this.dockSize = dockSize;
        this.density = density;
        this.roman = roman;
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(density);
        if (roman != null) {
            band.setColor(RomanStyle.withAlpha(RomanStyle.STONE_INK, 0xB3));   // 70%
            edge.setStyle(Paint.Style.FILL);
        } else {
            band.setColor(0xFF141416);
            edge.setColor(0x1FFFFFFF);
        }
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos >= 0 && pos < dockSize) {
            outRect.top = (int) (6 * density);
            outRect.bottom = (int) (12 * density);
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        boolean any = false;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos < 0 || pos >= dockSize) continue;
            View card = child.findViewById(R.id.card);
            if (card == null) continue;
            float l = child.getLeft() + card.getLeft();
            float t = child.getTop() + card.getTop() + child.getTranslationY();
            float r = l + card.getWidth();
            float b = t + card.getHeight();
            if (!any) {
                rect.set(l, t, r, b);
                any = true;
            } else {
                rect.union(l, t, r, b);
            }
        }
        if (!any) return;
        float pad = 14 * density;
        rect.inset(-pad, -pad);
        if (roman != null) {
            drawRomanBand(c);
            return;
        }
        float radius = 26 * density;
        c.drawRoundRect(rect, radius, radius, band);
        c.drawRoundRect(rect, radius, radius, edge);
    }

    /** Stone ink band with a gold double rule (1 dp on the edge, a fainter one 3 dp inside) top and bottom. */
    private void drawRomanBand(Canvas c) {
        float radius = 8 * density;
        c.drawRoundRect(rect, radius, radius, band);
        float x0 = rect.left + 8 * density, x1 = rect.right - 8 * density;
        float thin = density;
        for (int i = 0; i < 2; i++) {
            edge.setColor(RomanStyle.withAlpha(roman.gold, i == 0 ? 0xB3 : 0x4D));   // 70%, 30%
            float inset = i == 0 ? 0 : 3 * density;
            c.drawRect(x0, rect.top + inset, x1, rect.top + inset + thin, edge);
            c.drawRect(x0, rect.bottom - inset - thin, x1, rect.bottom - inset, edge);
        }
    }
}
