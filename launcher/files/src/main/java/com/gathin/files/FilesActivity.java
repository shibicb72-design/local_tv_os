package com.gathin.files;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gathin.roman.RomanDialog;
import com.gathin.roman.RomanLook;
import com.gathin.roman.RomanRows;
import com.gathin.roman.RomanStyle;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The browser (F7): roots screen, then folder listings. Listing runs on one background executor; a newer
 * request cancels the older one. BACK goes up, at a volume root to the roots, then exits.
 * F10 adds the action menu (MENU key or OK held), paste mode for copy/move, and the worker thread that runs
 * FileOps behind a progress dialog. Every deletion goes through a confirm dialog first.
 */
public final class FilesActivity extends Activity implements EntryAdapter.OnRowClick, Volumes.Listener {

    private static final String PREFS = "files";
    private static final String PREF_SORT = "sort";
    private static final String PREF_HIDDEN = "hidden";

    /** One operation at a time, even if the activity is recreated while a worker runs. */
    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    /** The work of one operation, run on the worker thread. */
    private interface Task {
        FileOps.Result run(FileOps ops);
    }

    private final RomanStyle style = RomanStyle.defaults();
    private final FocusMemory memory = new FocusMemory();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();   // file operations only

    private boolean showHidden;
    private Dialog activeDialog;
    private OpProgress progress;
    private volatile FileOps currentOp;
    private List<File> pasteSources;    // non-null while in paste mode
    private boolean pasteMove;
    private View pasteBar;
    private TextView pasteText;
    private TextView pasteGo;

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
        showHidden = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(PREF_HIDDEN, false);
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
        worker.shutdown();      // a running operation finishes (or is cancelled below); queued ones still run
        if (isFinishing() && currentOp != null) currentOp.cancel();
        if (progress != null) progress.dismiss();
        if (activeDialog != null) activeDialog.dismiss();
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
                cycleSort(false);
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

        root.addView(buildPasteBar(dp), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

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
        } else {
            Opener.open(this, row.path);
        }
    }

    @Override
    public void onRowLongClick(EntryAdapter.Row row) {
        showMenu(row);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU && browser != null) {
            if (event.getRepeatCount() == 0) showMenu(focusedRow());
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (rootPath == null) {
            if (pasteSources != null) {
                endPaste();
                return;
            }
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

    private void cycleSort(boolean restoreFocus) {
        FileSort.Key[] keys = FileSort.Key.values();
        sortKey = keys[(sortKey.ordinal() + 1) % keys.length];
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(PREF_SORT, sortKey.ordinal()).apply();
        updateSortPill();
        if (currentPath != null) {
            if (restoreFocus) rememberFocus();
            loadDir(currentPath, restoreFocus);
        }
    }

    private void toggleHidden() {
        showHidden = !showHidden;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(PREF_HIDDEN, showHidden).apply();
        if (currentPath != null) {
            rememberFocus();
            loadDir(currentPath, true);
        }
    }

    private String sortName() {
        return sortKey == FileSort.Key.NAME ? "name" : sortKey == FileSort.Key.DATE ? "date" : "size";
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
        final boolean hidden = showHidden;
        pending = io.submit(new Runnable() {
            @Override
            public void run() {
                DirLister lister = new DirLister();   // one per request: a cancelled run must not touch the next one's flag
                List<Entry> entries = lister.list(new File(path), hidden);
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

    // ---- action menu (F10) ------------------------------------------------------------------------------

    private EntryAdapter.Row focusedRow() {
        if (list == null) return null;
        View focused = list.getFocusedChild();
        if (focused == null) return null;
        int pos = list.getChildAdapterPosition(focused);
        return pos < 0 ? null : adapter.get(pos);
    }

    private void present(Dialog d) {
        if (isDestroyed()) return;
        if (activeDialog != null) activeDialog.dismiss();
        activeDialog = d;
        d.setOnDismissListener(x -> {
            if (activeDialog == d) activeDialog = null;
        });
        d.show();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private static String plainName(EntryAdapter.Row row) {
        return new File(row.path).getName();
    }

    private boolean isReadOnly(String path) {
        List<String> paths = new ArrayList<>();
        for (VolumeModel.Root r : roots) paths.add(r.path);
        String root = OpRules.rootOf(path, paths);
        if (root == null) return true;      // not on a known volume: do not write to it
        for (VolumeModel.Root r : roots) if (r.path.equals(root)) return r.readOnly;
        return true;
    }

    private ActionMenu.Item item(String label, OpRules.Action a, EntryAdapter.Row row, Runnable run) {
        boolean onRoots = rootPath == null;
        String reason = OpRules.disabledReason(a, onRoots, row != null, row != null && row.isDir,
                !onRoots && isReadOnly(rootPath));
        return new ActionMenu.Item(label, reason, run);
    }

    private void showMenu(final EntryAdapter.Row row) {
        if (browser == null) return;
        if (RUNNING.get()) {
            toast("An operation is still running");
            return;
        }
        if (pasteSources != null) {
            showPasteMenu();
            return;
        }
        List<ActionMenu.Item> items = new ArrayList<>();
        items.add(item("Open", OpRules.Action.OPEN, row, () -> onRowClick(row)));
        items.add(item("Open with...", OpRules.Action.OPEN_WITH, row, () -> Opener.openWith(this, row.path)));
        items.add(item("Rename", OpRules.Action.RENAME, row, () -> askRename(row)));
        items.add(item("Copy", OpRules.Action.COPY, row, () -> startPaste(row, false)));
        items.add(item("Move", OpRules.Action.MOVE, row, () -> startPaste(row, true)));
        items.add(item("Delete", OpRules.Action.DELETE, row, () -> askDelete(row)));
        items.add(item("Sort: " + sortName(), OpRules.Action.SORT, row, () -> cycleSort(true)));
        items.add(item(showHidden ? "Hide hidden files" : "Show hidden files", OpRules.Action.HIDDEN, row,
                this::toggleHidden));
        String title = row == null ? FilesInfo.TITLE : plainName(row);
        present(ActionMenu.build(this, title, items, 0));
    }

    private void showPasteMenu() {
        List<ActionMenu.Item> items = new ArrayList<>();
        items.add(new ActionMenu.Item("Paste here", pasteBlocker(), this::pasteHere));
        items.add(new ActionMenu.Item("Cancel " + (pasteMove ? "move" : "copy"), null, this::endPaste));
        present(ActionMenu.build(this, pasteMove ? "MOVE" : "COPY", items, 0));
    }

    /** Why "Paste here" cannot work in the current folder, or null. */
    private String pasteBlocker() {
        if (currentPath == null) return "open a folder first";
        if (isReadOnly(currentPath)) return "read-only volume";
        return null;
    }

    // ---- rename -----------------------------------------------------------------------------------------

    private void askRename(final EntryAdapter.Row row) {
        final File f = new File(row.path);
        present(RomanDialog.input(this, "Rename", f.getName(), "Rename", "Cancel", new RomanDialog.InputListener() {
            @Override
            public void onInput(final String text) {
                if (!tryBegin()) return;
                worker.execute(new Runnable() {
                    @Override
                    public void run() {
                        String error = null;
                        try {
                            FileOps.rename(f, text);
                        } catch (IOException e) {
                            error = e.getMessage();
                        }
                        final String err = error;
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                RUNNING.set(false);
                                if (isDestroyed()) return;
                                if (err != null) toast("Rename failed: " + err);
                                refresh();
                            }
                        });
                    }
                });
            }
        }));
    }

    // ---- delete (always confirmed) ----------------------------------------------------------------------

    private void askDelete(final EntryAdapter.Row row) {
        if (!tryBegin()) return;
        final File f = new File(row.path);
        worker.execute(new Runnable() {
            @Override
            public void run() {
                final OpPlan plan = OpPlan.forDelete(Collections.singletonList(f));
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        RUNNING.set(false);
                        if (isDestroyed()) return;
                        String msg = "Delete \"" + f.getName() + "\"?\n" + plan.files
                                + (plan.files == 1 ? " file, " : " files, ") + OpProgress.Sizes.format(plan.bytes)
                                + ". This cannot be undone.";
                        present(RomanDialog.confirm(FilesActivity.this, "Delete", msg, "Delete", "Cancel", true,
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        runDelete(plan);
                                    }
                                }, null));
                    }
                });
            }
        });
    }

    private void runDelete(final OpPlan plan) {
        if (!tryBegin()) return;
        runOp("Deleting", new Task() {
            @Override
            public FileOps.Result run(FileOps ops) {
                return ops.delete(plan);
            }
        });
    }

    // ---- copy / move: paste mode ------------------------------------------------------------------------

    private View buildPasteBar(float dp) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int pad = (int) (12 * dp);
        bar.setPadding(pad * 2, pad, pad * 2, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(RomanStyle.withAlpha(style.gold, 0x33));
        bg.setCornerRadius(10 * dp);
        bg.setStroke(Math.max(1, Math.round(dp)), style.gold);
        bar.setBackground(bg);
        bar.setVisibility(View.GONE);

        pasteText = new TextView(this);
        RomanRows.rowText(pasteText, style);
        pasteText.setTextColor(RomanStyle.IVORY);
        pasteText.setSingleLine(true);
        pasteText.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        bar.addView(pasteText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        pasteGo = pastePill("Paste here", dp, new Runnable() {
            @Override
            public void run() {
                pasteHere();
            }
        });
        TextView cancel = pastePill("Cancel", dp, new Runnable() {
            @Override
            public void run() {
                endPaste();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = (int) (12 * dp);
        bar.addView(pasteGo, lp);
        bar.addView(cancel, lp);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.topMargin = (int) (8 * dp);
        bar.setLayoutParams(bp);
        pasteBar = bar;
        return bar;
    }

    private TextView pastePill(String label, float dp, final Runnable action) {
        TextView p = new TextView(this);
        p.setText(label);
        RomanLook.pill(p, style);
        p.setGravity(Gravity.CENTER);
        p.setFocusable(true);
        p.setClickable(true);
        p.setPadding((int) (20 * dp), (int) (8 * dp), (int) (20 * dp), (int) (8 * dp));
        p.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        return p;
    }

    private void startPaste(EntryAdapter.Row row, boolean move) {
        pasteSources = Collections.singletonList(new File(row.path));
        pasteMove = move;
        pasteText.setText((move ? "Move " : "Copy ") + "\"" + plainName(row) + "\": open the target folder, then Paste here");
        pasteBar.setVisibility(View.VISIBLE);
    }

    private void endPaste() {
        pasteSources = null;
        if (pasteBar != null) pasteBar.setVisibility(View.GONE);
    }

    private void pasteHere() {
        if (pasteSources == null) return;
        String blocker = pasteBlocker();
        if (blocker != null) {
            toast("Cannot paste here: " + blocker);
            return;
        }
        if (!tryBegin()) return;
        final List<File> sources = pasteSources;
        final boolean move = pasteMove;
        final File dest = new File(currentPath);
        List<String> rootPaths = new ArrayList<>();
        for (VolumeModel.Root r : roots) rootPaths.add(r.path);
        final String destRoot = OpRules.rootOf(dest.getAbsolutePath(), rootPaths);
        final String srcRoot = OpRules.rootOf(sources.get(0).getAbsolutePath(), rootPaths);
        // A move inside one volume is a rename and needs no space; everything else writes new bytes.
        final boolean needsSpace = !move || destRoot == null || !destRoot.equals(srcRoot);
        worker.execute(new Runnable() {
            @Override
            public void run() {
                OpPlan made = null;
                String refusal = null;
                try {
                    made = OpPlan.forTransfer(sources, dest, needsSpace, new java.util.function.LongSupplier() {
                        @Override
                        public long getAsLong() {
                            return new StatFs(dest.getPath()).getAvailableBytes();
                        }
                    });
                } catch (OpPlan.Refused e) {
                    refusal = e.getMessage();
                }
                final OpPlan plan = made;
                final String refused = refusal;
                final int conflicts = refusal == null ? OpRules.conflicts(sources, dest) : 0;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        RUNNING.set(false);
                        if (isDestroyed()) return;
                        if (plan == null) {
                            toast(refused);
                        } else if (conflicts == 0) {
                            beginTransfer(plan, move, FileOps.Conflict.KEEP_BOTH);   // never overwrites
                        } else {
                            askConflict(plan, move, conflicts);
                        }
                    }
                });
            }
        });
    }

    /** Replace / Keep both / Skip, applied to every clash in this paste. Initial focus is on Keep both. */
    private void askConflict(final OpPlan plan, final boolean move, int count) {
        List<ActionMenu.Item> items = new ArrayList<>();
        items.add(new ActionMenu.Item("Replace (overwrites the existing)", null, new Runnable() {
            @Override
            public void run() {
                beginTransfer(plan, move, FileOps.Conflict.REPLACE);
            }
        }));
        items.add(new ActionMenu.Item("Keep both", null, new Runnable() {
            @Override
            public void run() {
                beginTransfer(plan, move, FileOps.Conflict.KEEP_BOTH);
            }
        }));
        items.add(new ActionMenu.Item("Skip", null, new Runnable() {
            @Override
            public void run() {
                beginTransfer(plan, move, FileOps.Conflict.SKIP);
            }
        }));
        items.add(new ActionMenu.Item("Cancel", null, null));
        String title = count + (count == 1 ? " name exists here" : " names exist here");
        present(ActionMenu.build(this, title, items, 1));
    }

    private void beginTransfer(final OpPlan plan, final boolean move, final FileOps.Conflict conflict) {
        if (!tryBegin()) return;
        runOp(move ? "Moving" : "Copying", new Task() {
            @Override
            public FileOps.Result run(FileOps ops) {
                return move ? ops.move(plan, conflict) : ops.copy(plan, conflict);
            }
        });
    }

    // ---- running an operation (worker thread, progress dialog) ------------------------------------------

    /** Claims the single operation slot; the caller must then call runOp or release RUNNING itself. */
    private boolean tryBegin() {
        if (RUNNING.compareAndSet(false, true)) return true;
        toast("An operation is still running");
        return false;
    }

    /** Main thread. RUNNING must already be held. The worker never touches a view: it posts to {@code main}. */
    private void runOp(String title, final Task task) {
        final OpProgress pd = new OpProgress(this, title, new Runnable() {
            @Override
            public void run() {
                FileOps f = currentOp;
                if (f != null) f.cancel();
            }
        });
        final long[] lastPost = new long[1];
        final FileOps fo = new FileOps(new FileOps.Progress() {
            @Override
            public void onProgress(final long done, final long total, final String name) {
                long now = SystemClock.uptimeMillis();
                if (now - lastPost[0] < 100) return;
                lastPost[0] = now;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        pd.update(done, total, name);
                    }
                });
            }
        });
        currentOp = fo;
        progress = pd;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        pd.show();
        worker.execute(new Runnable() {
            @Override
            public void run() {
                FileOps.Result r = null;
                try {
                    r = task.run(fo);
                } catch (RuntimeException e) {
                    r = null;
                }
                final FileOps.Result res = r;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        finishOp(pd, res);
                    }
                });
            }
        });
    }

    private void finishOp(OpProgress pd, FileOps.Result r) {
        RUNNING.set(false);
        currentOp = null;
        if (isDestroyed()) return;
        pd.dismiss();
        if (progress == pd) progress = null;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        endPaste();
        if (r == null) {
            toast("The operation failed");
        } else if (r.cancelled) {
            toast("Cancelled after " + r.done + (r.done == 1 ? " item" : " items"));
        } else if (!r.failed.isEmpty()) {
            toast(r.failed.size() + " failed (first: " + new File(r.failed.get(0)).getName() + "), "
                    + r.done + " done");
        } else if (r.skipped > 0) {
            toast("Done, " + r.skipped + " skipped");
        } else {
            toast("Done");
        }
        refresh();
    }

    /** Reload what is on screen and put focus back where it was. */
    private void refresh() {
        rememberFocus();
        if (currentPath != null) loadDir(currentPath, true); else loadRoots(true);
    }
}
