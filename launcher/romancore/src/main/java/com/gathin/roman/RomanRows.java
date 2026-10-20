package com.gathin.roman;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

/**
 * List rows for Files and Player (docs/plans/next-features.md, F4): gold focus row, plain sans file names,
 * Cinzel titles and gold rules. Colours come from {@link RomanStyle} only.
 */
public final class RomanRows {

    private RomanRows() {
    }

    /** Focused: gold gradient, 8 dp corners. At rest: transparent with a 1 dp gold-30% hairline at the bottom. */
    public static StateListDrawable rowBackground(Context ctx, RomanStyle s) {
        float density = ctx.getResources().getDisplayMetrics().density;
        GradientDrawable lit = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{s.goldLight, s.gold});
        lit.setCornerRadius(8 * density);

        int hair = Math.max(1, Math.round(density));
        GradientDrawable line = new GradientDrawable();
        line.setColor(RomanStyle.withAlpha(s.gold, 0x4D));   // 30%
        LayerDrawable rest = new LayerDrawable(new GradientDrawable[]{line});
        rest.setLayerGravity(0, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL);
        rest.setLayerHeight(0, hair);

        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, lit);
        bg.addState(new int[]{}, rest);
        return bg;
    }

    /**
     * A file name or other row text: system sans 16 sp (Cinzel's lowercase are small caps and would hide the
     * case of a file name), ivory at rest and stone ink on the focused row.
     */
    public static void rowText(TextView tv, RomanStyle s) {
        tv.setFontVariationSettings(null);
        tv.setTypeface(Typeface.SANS_SERIF);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        tv.setLetterSpacing(0f);
        tv.setTextColor(new ColorStateList(
                new int[][]{{android.R.attr.state_focused}, {}},
                new int[]{RomanStyle.STONE_INK, RomanStyle.IVORY}));
    }

    /** A screen title: Cinzel 22 sp, letter spacing 0.16, gilded. */
    public static void title(TextView tv, RomanStyle s) {
        RomanLook.text(tv, 22f, 0.16f, s.gold);
        RomanLook.gild(tv, s);
    }

    /** A horizontal rule: gold at 60%. The view should be 1 dp high. */
    public static void rule(View v, RomanStyle s) {
        v.setBackgroundColor(RomanStyle.withAlpha(s.gold, 0x99));   // 60%
    }
}
