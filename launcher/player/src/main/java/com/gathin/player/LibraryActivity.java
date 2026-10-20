package com.gathin.player;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.gathin.roman.RomanLook;
import com.gathin.roman.RomanRows;
import com.gathin.roman.RomanStyle;

import java.io.File;
import java.util.List;

/**
 * Launcher entry: "Browse USB" (opens Files) and "Continue watching" from the {@link ResumeStore}, newest first,
 * at most 20, only files that still exist. Text rows only, no thumbnails.
 */
public class LibraryActivity extends Activity {

    private static final String FILES_PACKAGE = "com.gathin.files";
    private static final int MAX_RECENT = 20;

    private final RomanStyle style = RomanStyle.defaults();
    private LinearLayout list;
    private float density;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        density = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(RomanStyle.STONE_INK);
        root.setPadding(px(64), px(40), px(64), px(24));

        TextView title = new TextView(this);
        title.setText("PLAYER");
        RomanRows.title(title, style);
        root.addView(title);

        View rule = new View(this);
        RomanRows.rule(rule, style);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, px(1)));
        rp.topMargin = px(12);
        rp.bottomMargin = px(12);
        root.addView(rule, rp);

        ScrollView scroll = new ScrollView(this);
        scroll.setFocusable(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    /** Rebuilt on every start: playback elsewhere changes the resume list. */
    @Override
    protected void onStart() {
        super.onStart();
        populate();
    }

    private void populate() {
        list.removeAllViews();
        TextView browse = row("Browse USB", null);
        browse.setOnClickListener(v -> openFiles());
        list.addView(browse);

        TextView heading = new TextView(this);
        heading.setText("CONTINUE WATCHING");
        RomanLook.text(heading, 14f, 0.12f, style.gold);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hp.topMargin = px(24);
        hp.bottomMargin = px(8);
        list.addView(heading, hp);

        List<ResumeStore.Recent> recent = new ResumeStore(PlayerActivity.prefsStore(this))
                .recent(p -> new File(p).isFile(), MAX_RECENT);
        if (recent.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Nothing to continue yet. Open a video or song from Files.");
            RomanRows.rowText(empty, style);
            empty.setTextColor(RomanStyle.IVORY_DIM);
            list.addView(empty);
            return;
        }
        for (ResumeStore.Recent r : recent) {
            File f = new File(r.path);
            TextView row = row(f.getName(), TimeFormat.format(r.positionMs));
            row.setOnClickListener(v -> play(f));
            list.addView(row);
        }
    }

    /** A focusable gold-focus row; {@code right} (may be null) is appended after the name. */
    private TextView row(String name, String right) {
        TextView tv = new TextView(this);
        tv.setText(right == null ? name : name + "   " + right);
        tv.setSingleLine(true);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        RomanRows.rowText(tv, style);
        tv.setBackground(RomanRows.rowBackground(this, style));
        tv.setPadding(px(16), px(10), px(16), px(10));
        tv.setFocusable(true);
        tv.setClickable(true);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return tv;
    }

    private void play(File f) {
        // Explicit intent into this app: the file:// URI never leaves the process. PlayerActivity prefers the path.
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.fromFile(f))
                .setClass(this, PlayerActivity.class)
                .putExtra(PlayerActivity.EXTRA_PATH, f.getAbsolutePath());
        startActivity(i);
    }

    private void openFiles() {
        try {
            startActivity(new Intent(Intent.ACTION_MAIN)
                    .setComponent(new ComponentName(FILES_PACKAGE, FILES_PACKAGE + ".FilesActivity"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {   // ActivityNotFoundException: Files is not installed
            Toast.makeText(this, "Files is not installed", Toast.LENGTH_SHORT).show();
        }
    }

    private int px(int dp) {
        return Math.round(dp * density);
    }
}
