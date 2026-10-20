package com.gathin.files;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.gathin.roman.RomanRows;
import com.gathin.roman.RomanStyle;

import java.util.List;

/**
 * A list of focusable rows in a Roman dialog (F10): the action menu, the paste menu and the
 * Replace / Keep both / Skip choice. A disabled item stays focusable and shows its reason when chosen.
 */
final class ActionMenu {

    static final class Item {
        final String label;
        /** Null when enabled, otherwise the reason shown next to the label and in a toast. */
        final String disabledReason;
        final Runnable run;

        Item(String label, String disabledReason, Runnable run) {
            this.label = label;
            this.disabledReason = disabledReason;
            this.run = run;
        }
    }

    private ActionMenu() {
    }

    /** BACK dismisses. Initial focus goes to {@code focusIndex} (clamped). Call show() on the result. */
    static Dialog build(final Context ctx, String title, List<Item> items, int focusIndex) {
        final RomanStyle s = RomanStyle.defaults();
        float dp = ctx.getResources().getDisplayMetrics().density;
        final Dialog d = new Dialog(ctx);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setCancelable(true);

        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(24 * dp);
        box.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(RomanStyle.STONE_INK);
        bg.setCornerRadius(12 * dp);
        bg.setStroke(Math.round(dp), RomanStyle.withAlpha(s.gold, 0x80));
        box.setBackground(bg);
        box.setMinimumWidth(Math.round(420 * dp));

        TextView t = new TextView(ctx);
        t.setText(title);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        RomanRows.title(t, s);
        box.addView(t);
        View rule = new View(ctx);
        RomanRows.rule(rule, s);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, Math.round(dp)));
        rp.topMargin = Math.round(8 * dp);
        rp.bottomMargin = Math.round(8 * dp);
        box.addView(rule, rp);

        LinearLayout rows = new LinearLayout(ctx);
        rows.setOrientation(LinearLayout.VERTICAL);
        View toFocus = null;
        for (int i = 0; i < items.size(); i++) {
            final Item item = items.get(i);
            TextView row = new TextView(ctx);
            boolean enabled = item.disabledReason == null;
            row.setText(enabled ? item.label : item.label + "  (" + item.disabledReason + ")");
            RomanRows.rowText(row, s);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setSingleLine(true);
            row.setAlpha(enabled ? 1f : 0.5f);
            row.setBackground(RomanRows.rowBackground(ctx, s));
            row.setPadding(Math.round(16 * dp), 0, Math.round(16 * dp), 0);
            row.setFocusable(true);
            row.setClickable(true);
            row.setOnClickListener(v -> {
                if (item.disabledReason != null) {
                    Toast.makeText(ctx, capitalise(item.disabledReason), Toast.LENGTH_SHORT).show();
                    return;
                }
                d.dismiss();
                if (item.run != null) item.run.run();
            });
            rows.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    Math.round(48 * dp)));
            if (i == Math.max(0, Math.min(focusIndex, items.size() - 1))) toFocus = row;
        }
        ScrollView scroll = new ScrollView(ctx);
        scroll.addView(rows);
        box.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        d.setContentView(box);
        if (toFocus != null) toFocus.requestFocus();
        return d;
    }

    private static String capitalise(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
