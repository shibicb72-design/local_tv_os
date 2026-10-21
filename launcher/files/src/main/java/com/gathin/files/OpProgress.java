package com.gathin.files;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.gathin.roman.RomanLook;
import com.gathin.roman.RomanRows;
import com.gathin.roman.RomanStyle;

/** The progress dialog of a running operation (F10). Main thread only; Cancel and BACK both cancel. */
final class OpProgress {

    private final Dialog dialog;
    private final TextView name;
    private final TextView count;
    private final ProgressBar bar;
    private final TextView cancel;

    OpProgress(Context ctx, String title, final Runnable onCancel) {
        RomanStyle s = RomanStyle.defaults();
        float dp = ctx.getResources().getDisplayMetrics().density;
        dialog = new Dialog(ctx);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(24 * dp);
        box.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(RomanStyle.STONE_INK);
        bg.setCornerRadius(12 * dp);
        bg.setStroke(Math.round(dp), RomanStyle.withAlpha(s.gold, 0x80));
        box.setBackground(bg);
        box.setMinimumWidth(Math.round(480 * dp));

        TextView t = new TextView(ctx);
        t.setText(title);
        RomanRows.title(t, s);
        box.addView(t);
        View rule = new View(ctx);
        RomanRows.rule(rule, s);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, Math.round(dp)));
        rp.topMargin = Math.round(8 * dp);
        rp.bottomMargin = Math.round(16 * dp);
        box.addView(rule, rp);

        name = new TextView(ctx);
        name.setTextColor(RomanStyle.IVORY);
        name.setTextSize(16f);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        box.addView(name);

        bar = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setProgressTintList(ColorStateList.valueOf(s.gold));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = Math.round(12 * dp);
        box.addView(bar, bp);

        count = new TextView(ctx);
        count.setTextColor(RomanStyle.IVORY);
        count.setAlpha(0.8f);
        count.setTextSize(14f);
        box.addView(count);

        cancel = new TextView(ctx);
        cancel.setText("Cancel");
        cancel.setGravity(Gravity.CENTER);
        cancel.setFocusable(true);
        cancel.setClickable(true);
        cancel.setPadding(Math.round(24 * dp), Math.round(10 * dp), Math.round(24 * dp), Math.round(10 * dp));
        RomanLook.pill(cancel, s);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.gravity = Gravity.END;
        cp.topMargin = Math.round(20 * dp);
        box.addView(cancel, cp);

        cancel.setOnClickListener(v -> dialog.cancel());
        dialog.setOnCancelListener(x -> {
            cancel.setText("Cancelling...");
            cancel.setEnabled(false);
            if (onCancel != null) onCancel.run();
            dialog.show();      // stays up until the worker has actually stopped
        });
        dialog.setContentView(box);
        cancel.requestFocus();
    }

    void show() {
        dialog.show();
    }

    void dismiss() {
        dialog.setOnCancelListener(null);
        if (dialog.isShowing()) dialog.dismiss();
    }

    void update(long done, long total, String currentName) {
        name.setText(currentName);
        bar.setProgress(total <= 0 ? 0 : (int) Math.min(1000L, done * 1000L / total));
        count.setText(Sizes.format(done) + " of " + Sizes.format(total));
    }

    /** Small byte formatter with no Android dependency. */
    static final class Sizes {
        private Sizes() {
        }

        static String format(long bytes) {
            if (bytes < 1024) return bytes + " B";
            double v = bytes / 1024.0;
            String[] unit = {"KB", "MB", "GB", "TB"};
            int i = 0;
            while (v >= 1024 && i < unit.length - 1) {
                v /= 1024;
                i++;
            }
            return String.format(java.util.Locale.US, "%.1f %s", v, unit[i]);
        }
    }
}
