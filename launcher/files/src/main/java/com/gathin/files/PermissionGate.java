package com.gathin.files;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gathin.roman.RomanLook;
import com.gathin.roman.RomanRows;
import com.gathin.roman.RomanStyle;

/**
 * "All files access" gate (F6). Path 1: the settings screen, offered only if the intent resolves on this
 * TV. Path 2: the operator command. Which one this projector has is Op-F0 (pending), so both are built and
 * the choice is made at runtime.
 */
final class PermissionGate {

    private PermissionGate() {
    }

    static boolean granted() {
        return Environment.isExternalStorageManager();
    }

    /** The settings intent for this app, or null when no activity on the device handles it. */
    static Intent settingsIntent(Activity a) {
        Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + a.getPackageName()));
        return i.resolveActivity(a.getPackageManager()) != null ? i : null;
    }

    /** The screen shown while the permission is missing. Call again after onResume to re-check {@link #granted}. */
    static View build(final Activity a) {
        RomanStyle style = RomanStyle.defaults();
        float dp = a.getResources().getDisplayMetrics().density;
        int pad = (int) (48 * dp);

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(RomanStyle.STONE_INK);
        col.setPadding(pad, (int) (32 * dp), pad, pad / 2);

        TextView title = new TextView(a);
        RomanRows.title(title, style);
        title.setText("FILES NEEDS ALL FILES ACCESS");
        col.addView(title);

        View rule = new View(a);
        RomanRows.rule(rule, style);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(dp)));
        rp.topMargin = (int) (12 * dp);
        rp.bottomMargin = (int) (16 * dp);
        col.addView(rule, rp);

        final Intent settings = settingsIntent(a);
        TextView body = new TextView(a);
        RomanRows.rowText(body, style);
        body.setTextColor(RomanStyle.IVORY);
        body.setText(settings != null
                ? "Allow \"All files access\" for this app in Settings, then come back."
                : "This TV has no settings screen for it. Ask whoever runs the projector to run this on the PC:\n\n"
                        + FilesInfo.APPOPS_COMMAND + "\n\nThen reopen Files.");
        col.addView(body);

        if (settings != null) {
            TextView open = new TextView(a);
            RomanLook.pill(open, style);
            open.setText("OPEN SETTINGS");
            open.setGravity(Gravity.CENTER);
            open.setFocusable(true);
            int h = (int) (12 * dp);
            open.setPadding((int) (28 * dp), h, (int) (28 * dp), h);
            open.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.startActivity(settings);
                }
            });
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            bp.topMargin = (int) (24 * dp);
            col.addView(open, bp);
            open.requestFocus();
        }
        return col;
    }
}
