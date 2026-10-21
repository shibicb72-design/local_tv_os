package com.gathin.player;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.gathin.roman.RomanLook;
import com.gathin.roman.RomanRows;
import com.gathin.roman.RomanStyle;

import java.util.List;

/**
 * Roman side panel for the tracks menu: a Cinzel title, gold rules, and a D-pad list of rows on the right edge.
 * It only draws rows and reports the chosen id; what a row means belongs to the activity. The panel is added to
 * the root on {@link #show} and removed on {@link #hide}, so no focusable view exists while it is closed and the
 * keys keep going to the activity.
 */
final class TracksMenu {

    interface Listener {
        void onChosen(String id);
    }

    /** One line of the panel: a section header, or an option with an id. */
    static final class Row {
        final String id;   // null for a header
        final String text;
        final boolean selected;
        final boolean enabled;

        private Row(String id, String text, boolean selected, boolean enabled) {
            this.id = id;
            this.text = text;
            this.selected = selected;
            this.enabled = enabled;
        }

        static Row header(String text) {
            return new Row(null, text, false, false);
        }

        static Row option(String id, String text, boolean selected, boolean enabled) {
            return new Row(id, text, selected, enabled);
        }
    }

    private static final int WIDTH_DP = 380;

    private final Context ctx;
    private final FrameLayout root;
    private final Listener listener;
    private final RomanStyle style = RomanStyle.defaults();
    private View panel;

    TracksMenu(Context ctx, FrameLayout root, Listener listener) {
        this.ctx = ctx;
        this.root = root;
        this.listener = listener;
    }

    boolean isShowing() {
        return panel != null;
    }

    /** Replaces the panel (if any) with one listing {@code rows}; focus goes to the selected option. */
    void show(String title, List<Row> rows) {
        hide();
        float d = ctx.getResources().getDisplayMetrics().density;
        int pad = Math.round(20 * d);

        LinearLayout column = new LinearLayout(ctx);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(pad, pad, pad, pad);

        TextView heading = new TextView(ctx);
        heading.setText(title);
        RomanRows.title(heading, style);
        column.addView(heading);
        column.addView(rule(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, Math.round(d))));

        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        View firstFocus = null;
        View selectedFocus = null;
        for (Row row : rows) {
            TextView tv = new TextView(ctx);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (row.id == null) {
                tv.setText(row.text);
                RomanLook.text(tv, 14f, 0.12f, style.gold);
                tv.setPadding(0, Math.round(16 * d), 0, Math.round(4 * d));
            } else {
                tv.setText(row.selected ? "✓  " + row.text : " " + row.text);
                RomanRows.rowText(tv, style);
                tv.setPadding(Math.round(12 * d), Math.round(10 * d), Math.round(12 * d), Math.round(10 * d));
                tv.setBackground(RomanRows.rowBackground(ctx, style));
                if (row.enabled) {
                    tv.setFocusable(true);
                    tv.setClickable(true);
                    final String id = row.id;
                    tv.setOnClickListener(v -> listener.onChosen(id));
                    if (firstFocus == null) firstFocus = tv;
                    if (row.selected && selectedFocus == null) selectedFocus = tv;
                } else {
                    tv.setAlpha(0.5f);
                }
            }
            list.addView(tv, lp);
        }
        ScrollView scroll = new ScrollView(ctx);
        scroll.setFocusable(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(list);
        column.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF21A1511);   // stone ink at 95%: video stays readable behind but never competes
        bg.setStroke(Math.max(1, Math.round(d)), RomanStyle.withAlpha(style.gold, 0x99));
        column.setBackground(bg);

        panel = column;
        root.addView(column, new FrameLayout.LayoutParams(Math.round(WIDTH_DP * d),
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END));
        View target = selectedFocus != null ? selectedFocus : firstFocus;
        if (target != null) target.requestFocus();
    }

    void hide() {
        if (panel == null) return;
        root.removeView(panel);
        panel = null;
    }

    private View rule() {
        View v = new View(ctx);
        RomanRows.rule(v, style);
        return v;
    }
}
