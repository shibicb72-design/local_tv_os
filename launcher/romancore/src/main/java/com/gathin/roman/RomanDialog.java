package com.gathin.roman;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Confirm and rename dialogs in the Roman look: a plain {@link Dialog}, Cinzel title, message, two pills.
 * BACK cancels. A destructive confirm starts with the focus on Cancel.
 */
public final class RomanDialog {

    /** Result of the rename dialog. */
    public interface InputListener {
        void onInput(String text);
    }

    private RomanDialog() {
    }

    public static Dialog confirm(Context ctx, String title, String message, String okLabel,
                                 String cancelLabel, boolean destructive, Runnable onOk, Runnable onCancel) {
        RomanStyle s = RomanStyle.defaults();
        Dialog d = base(ctx);
        LinearLayout box = box(ctx, s, title);
        TextView msg = new TextView(ctx);
        msg.setText(message);
        msg.setTextColor(RomanStyle.IVORY);
        msg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        box.addView(msg, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView ok = pill(ctx, s, okLabel);
        TextView cancel = pill(ctx, s, cancelLabel);
        box.addView(buttons(ctx, cancel, ok));
        ok.setOnClickListener(v -> {
            d.dismiss();
            if (onOk != null) onOk.run();
        });
        cancel.setOnClickListener(v -> d.cancel());
        d.setOnCancelListener(x -> {
            if (onCancel != null) onCancel.run();
        });
        d.setContentView(box);
        (destructive ? cancel : ok).requestFocus();
        return d;
    }

    /** Rename: an EditText (system keyboard) holding {@code initial}; OK is also the keyboard's Done key. */
    public static Dialog input(Context ctx, String title, String initial, String okLabel,
                               String cancelLabel, InputListener onInput) {
        RomanStyle s = RomanStyle.defaults();
        Dialog d = base(ctx);
        LinearLayout box = box(ctx, s, title);
        EditText edit = new EditText(ctx);
        edit.setSingleLine(true);
        edit.setInputType(InputType.TYPE_CLASS_TEXT);
        edit.setImeOptions(EditorInfo.IME_ACTION_DONE);
        edit.setText(initial);
        edit.setSelection(initial == null ? 0 : initial.length());
        edit.setTextColor(RomanStyle.IVORY);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        box.addView(edit, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView ok = pill(ctx, s, okLabel);
        TextView cancel = pill(ctx, s, cancelLabel);
        box.addView(buttons(ctx, cancel, ok));
        Runnable accept = () -> {
            d.dismiss();
            if (onInput != null) onInput.onInput(edit.getText().toString());
        };
        ok.setOnClickListener(v -> accept.run());
        cancel.setOnClickListener(v -> d.cancel());
        edit.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_DONE) return false;
            accept.run();
            return true;
        });
        d.setContentView(box);
        edit.requestFocus();
        return d;
    }

    private static Dialog base(Context ctx) {
        Dialog d = new Dialog(ctx);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setCancelable(true);
        return d;
    }

    private static LinearLayout box(Context ctx, RomanStyle s, String title) {
        float density = ctx.getResources().getDisplayMetrics().density;
        int pad = Math.round(24 * density);
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(RomanStyle.STONE_INK);
        bg.setCornerRadius(12 * density);
        bg.setStroke(Math.round(density), RomanStyle.withAlpha(s.gold, 0x80));
        box.setBackground(bg);
        box.setMinimumWidth(Math.round(420 * density));

        TextView t = new TextView(ctx);
        t.setText(title);
        RomanRows.title(t, s);
        box.addView(t);
        View rule = new View(ctx);
        RomanRows.rule(rule, s);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, Math.round(density)));
        rp.topMargin = Math.round(8 * density);
        rp.bottomMargin = Math.round(16 * density);
        box.addView(rule, rp);
        return box;
    }

    private static TextView pill(Context ctx, RomanStyle s, String label) {
        float density = ctx.getResources().getDisplayMetrics().density;
        TextView p = new TextView(ctx);
        p.setText(label);
        p.setGravity(Gravity.CENTER);
        p.setFocusable(true);
        p.setClickable(true);
        p.setPadding(Math.round(24 * density), Math.round(10 * density),
                Math.round(24 * density), Math.round(10 * density));
        RomanLook.pill(p, s);
        return p;
    }

    private static LinearLayout buttons(Context ctx, View cancel, View ok) {
        float density = ctx.getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Math.round(12 * density);
        lp.topMargin = Math.round(20 * density);
        row.addView(cancel, lp);
        row.addView(ok, lp);
        return row;
    }
}
