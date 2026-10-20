package com.gathin.files;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gathin.roman.RomanLook;
import com.gathin.roman.RomanRows;
import com.gathin.roman.RomanStyle;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * The browser (F7): roots screen, then folder listings. Listing runs on one background executor; a newer
 * request cancels the older one. BACK goes up, at a volume root to the roots, then exits.
 */
public final class FilesActivity extends Activity implements EntryAdapter.OnRowClick, Volumes.Listener {

    private static final String PREFS = "files";
    private static final String PREF_SORT = "sort";

    private final RomanStyle style = RomanStyle.defaults();
    private final FocusMemory memory = new FocusMemory();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private Volumes volumes;
    private List<VolumeModel.Root> roots = new ArrayList<>();
    private EntryAdapter adapter;
    private RecyclerView list;
    private LinearLayoutManager layout;
    private TextView title;
    private TextView sortPill;
    private TextView status;
    private View browser;           // the browser content view; null while the permission gate is shown

    private FileSort.Key sortKey = FileSort.Key.NAME;
    private String rootPath;        // volume root being browsed, null on the roots screen
    private String rootLabel;
    private String currentPath;     // folder shown, null on the roots screen
    private int generation;         // bumped per request; stale results are dropped
    private Future<?> pending;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        volumes = new Volumes(this);
        int saved = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(PREF_SORT, 0);
        FileSort.Key[] keys = FileSort.Key.values();
        sortKey = keys[Math.max(0, Math.min(saved, keys.length - 1))];
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (PermissionGate.granted()) volumes.start(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!PermissionGate.granted()) {
            browser = null;
            setContentView(PermissionGate.build(this));
            return;
        }
        if (browser == null) {
            buildBrowser();
            volumes.start(this);
            showRoots();
        }
    }

    @Override
    protected void onStop() {
        volumes.stop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (pending != null) pending.cancel(true);
        io.shutdownNow();
        super.onDestroy();
    }

    private void buildBrowser() {
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(RomanStyle.STONE_INK);
        int pad = (int) (48 * dp);
        root.setPadding(pad, (int) (32 * dp), pad, pad / 2);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        title = new TextView(this);
        RomanRows.title(title, style);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        head.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        sortPill = new TextView(this);
        RomanLook.pill(sortPill, style);
        sortPill.setGravity(Gravity.CENTER);
        sortPill.setFocusable(true);
        sortPill.setClickable(true);
        int h = (int) (10 * dp);
        sortPill.setPadding((int) (24 * dp), h, (int) (24 * dp), h);
        sortPill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cycleSort();
            }
        });
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pp.leftMargin = (int) (24 * dp);
        head.addView(sortPill, pp);
        root.addView(head, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        View rule = new View(this);
        RomanRows.rule(rule, style);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(dp)));
        rp.topMargin = (int) (12 * dp);
        rp.bottomMargin = (int) (8 * dp);
        root.addView(rule, rp);

        status = new TextView(this);
        RomanRows.rowText(status, style);
        status.setTextColor(RomanStyle.IVORY);
        status.setPadding((int) (16 * dp), (int) (24 * dp), 0, 0);
        status.setVisibility(View.GONE);
        root.addView(status);

        adapter = new EntryAdapter(this);
        layout = new LinearLayoutManager(this);
        list = new RecyclerView(this);
        list.setLayoutManager(layout);
        list.setAdapter(adapter);
        list.setItemAnimator(null);
        list.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        browser = root;
        setContentView(root);
        updateSortPill();
    }

    // ---- navigation -------------------------------------------------------------------------------------

    private void showRoots() {
        rememberFocus();
        rootPath = null;
        rootLabel = null;
        currentPath = null;
        title.setText(FilesInfo.TITLE);
        sortPill.setVisibility(View.GONE);
        loadRoots(true);
    }

    private void open(String path, boolean restoreFocus) {
        currentPath = path;
        sortPill.setVisibility(View.VISIBLE);
        title.setText(breadcrumb());
        loadDir(path, restoreFocus);
    }

    private String breadcrumb() {
        String rel = currentPath.length() > rootPath.length() ? currentPath.substring(rootPath.length()) : "";
        return (rootLabel + rel).toUpperCase();
    }

    @Override
    public void onRowClick(EntryAdapter.Row row) {
        if (rootPath == null) {
            rememberFocus();
            rootPath = row.path;
            rootLabel = row.name;
            open(row.path, true);
        } else if (row.isDir) {
            rememberFocus();
            open(row.path, true);
        }
        // Opening a file is F8.
    }

    @Override
    public void onBackPressed() {
        if (rootPath == null) {
            super.onBackPressed();
        } else if (currentPath.equals(rootPath)) {
            showRoots();
        } else {
            File parent = new File(currentPath).getParentFile();
            rememberFocus();
            open(parent == null ? rootPath : parent.getAbsolutePath(), true);
        }
    }

    @Override
    public void onVolumesChanged() {
        if (browser == null) return;
        if (rootPath == null) {
            loadRoots(false);
            return;
        }
        final String watching = rootPath;
        io.execute(new Runnable() {
            @Override
            public void run() {
                final List<VolumeModel.Root> now = volumes.roots();
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        roots = now;
                        if (watching.equals(rootPath) && !hasRoot(now, watching)) {
                            Toast.makeText(FilesActivity.this, rootLabel + " was removed", Toast.LENGTH_LONG).show();
                            showRoots();
                        }
                    }
                });
            }
        });
    }

    private static boolean hasRoot(List<VolumeModel.Root> roots, String path) {
        for (VolumeModel.Root r : roots) if (r.path.equals(path)) return true;
        return false;
    }

    // ---- sort -------------------------------------------------------------------------------------------

    private void cycleSort() {
        FileSort.Key[] keys = FileSort.Key.values();
        sortKey = keys[(sortKey.ordinal() + 1) % keys.length];
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(PREF_SORT, sortKey.ordinal()).apply();
        updateSortPill();
        if (currentPath != null) loadDir(currentPath, false);
    }

    private void updateSortPill() {
        String name = sortKey == FileSort.Key.NAME ? "NAME" : sortKey == FileSort.Key.DATE ? "DATE" : "SIZE";
        sortPill.setText("SORT: " + name);
    }

    /** Name runs A to Z, date and size run newest / largest first. */
    private boolean ascending() {
        return sortKey == FileSort.Key.NAME;
    }

    // ---- loading (background executor, stale results dropped) -------------------------------------------

    private void loadRoots(final boolean restoreFocus) {
        final int gen = begin();
        pending = io.submit(new Runnable() {
            @Override
            public void run() {
                final List<VolumeModel.Root> now = volumes.roots();
                final List<EntryAdapter.Row> rows = EntryAdapter.rootRows(now);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != generation) return;
                        roots = now;
                        show(rows, rows.isEmpty() ? "Nothing is mounted" : null, restoreFocus, null);
                    }
                });
            }
        });
    }

    private void loadDir(final String path, final boolean restoreFocus) {
        final int gen = begin();
        final Context ctx = this;
        final FileSort.Key key = sortKey;
        final boolean asc = ascending();
        pending = io.submit(new Runnable() {
            @Override
            public void run() {
                DirLister lister = new DirLister();   // one per request: a cancelled run must not touch the next one's flag
                List<Entry> entries = lister.list(new File(path), false);
                final boolean denied = lister.denied();
                Collections.sort(entries, FileSort.comparator(key, asc));
                final List<EntryAdapter.Row> rows = EntryAdapter.rowsOf(ctx, entries);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != generation) return;
                        String msg = denied ? "This folder cannot be read"
                                : rows.isEmpty() ? "This folder is empty" : null;
                        show(rows, msg, restoreFocus, path);
                    }
                });
            }
        });
    }

    private int begin() {
        if (pending != null) pending.cancel(true);
        return ++generation;
    }

    /** Main thread: swap the rows, then put focus back on the remembered row (or the sort pill when empty). */
    private void show(List<EntryAdapter.Row> rows, String message, boolean restoreFocus, String path) {
        adapter.submit(rows);
        status.setVisibility(message == null ? View.GONE : View.VISIBLE);
        if (message != null) status.setText(message);
        if (!restoreFocus) return;
        if (rows.isEmpty()) {
            (sortPill.getVisibility() == View.VISIBLE ? sortPill : list).requestFocus();
            return;
        }
        FocusMemory.Spot spot = memory.recall(path == null ? "" : path);
        final int pos = Math.min(spot.position, rows.size() - 1);
        layout.scrollToPositionWithOffset(pos, spot.offset);
        focusPosition(pos, 3);
    }

    /** The row view may not exist until the layout pass after the scroll; retry a few times. */
    private void focusPosition(final int pos, final int tries) {
        list.post(new Runnable() {
            @Override
            public void run() {
                RecyclerView.ViewHolder vh = list.findViewHolderForAdapterPosition(pos);
                if (vh != null) {
                    vh.itemView.requestFocus();
                } else if (tries > 0) {
                    focusPosition(pos, tries - 1);
                } else {
                    list.requestFocus();
                }
            }
        });
    }

    private void rememberFocus() {
        if (list == null) return;
        View focused = list.getFocusedChild();
        if (focused == null) return;
        int pos = list.getChildAdapterPosition(focused);
        if (pos < 0) return;
        memory.remember(currentPath == null ? "" : currentPath, pos, focused.getTop() - list.getPaddingTop());
    }
}
