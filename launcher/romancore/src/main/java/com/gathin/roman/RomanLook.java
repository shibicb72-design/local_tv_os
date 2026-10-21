package com.gathin.roman;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

/**
 * Roman look v2 on the views (docs/plans/roman-theme-v2.md, section 1.4): carved Cinzel lettering in the
 * palette of {@link RomanStyle}. Only called when {@code style} is "roman".
 */
public final class RomanLook {

    private static Typeface cinzel;

    private RomanLook() {
    }

    /** The bundled Cinzel subset, loaded once. */
    public static Typeface typeface(Context ctx) {
        if (cinzel == null) cinzel = ctx.getResources().getFont(com.gathin.roman.R.font.cinzel_roman);
        return cinzel;
    }

    /** Sets face, size, spacing and colour of one text; the variable-font axes of Outfit must not leak in. */
    public static void text(TextView tv, float sp, float spacing, int color) {
        tv.setFontVariationSettings(null);
        tv.setTypeface(typeface(tv.getContext()));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setLetterSpacing(spacing);
        tv.setTextColor(color);
    }

    /**
     * Gold lettering: a vertical gradient (light, gold at 55%, deep) over the text height. The shader is
     * rebuilt only when the view's height changes, never per draw.
     */
    public static void gild(TextView tv, RomanStyle s) {
        tv.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            private int height = -1;

            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                int h = b - t;
                if (h == height || h <= 0) return;
                height = h;
                tv.getPaint().setShader(new LinearGradient(0, 0, 0, h,
                        new int[]{s.goldLight, s.gold, s.goldDeep}, new float[]{0f, 0.55f, 1f},
                        Shader.TileMode.CLAMP));
                tv.invalidate();
            }
        });
    }

    /**
     * Gold pill (plan 1.6): dark stone with a gold hairline normally, gold gradient with stone-ink text and
     * icon when focused. Replaces the XML background, text colour and icon tint.
     */
    public static void pill(TextView tv, RomanStyle s) {
        float density = tv.getResources().getDisplayMetrics().density;
        float radius = 21 * density;
        GradientDrawable rest = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x66241D17, 0x4D1A1511});
        rest.setCornerRadius(radius);
        rest.setStroke(Math.round(density), RomanStyle.withAlpha(s.gold, 0x80));
        GradientDrawable lit = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{s.goldLight, s.gold});
        lit.setCornerRadius(radius);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, lit);
        bg.addState(new int[]{}, rest);
        tv.setBackground(bg);

        ColorStateList ink = new ColorStateList(
                new int[][]{{android.R.attr.state_focused}, {}},
                new int[]{RomanStyle.STONE_INK, RomanStyle.IVORY});
        text(tv, 14f, 0.06f, RomanStyle.IVORY);
        tv.setTextColor(ink);
        tv.setCompoundDrawableTintList(ink);
    }

    /** Icon-tile background (HDMI etc.): warm dark stone with a faint gold outline, same radius as the tile. */
    public static GradientDrawable iconTile(RomanStyle s, float radiusPx, float density) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(RomanStyle.ICON_TILE);
        d.setCornerRadius(radiusPx);
        d.setStroke(Math.round(density), RomanStyle.withAlpha(s.gold, 0x59));   // 35%
        return d;
    }
}
