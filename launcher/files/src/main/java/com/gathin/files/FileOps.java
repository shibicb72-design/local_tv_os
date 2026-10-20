package com.gathin.files;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Copy, move, delete and rename on java.io.File. Pure Java, meant for one worker thread; make one instance per
 * operation. cancel() may be called from any thread and is checked once per 256 KB buffer (and per file).
 */
public final class FileOps {
    /** What to do when the target name exists. */
    public enum Conflict { REPLACE, KEEP_BOTH, SKIP }

    public interface Progress {
        void onProgress(long doneBytes, long totalBytes, String currentName);
    }

    public static final class Result {
        /** Top-level items finished. */
        public int done;
        /** Top-level items left alone (SKIP, or a move onto itself). */
        public int skipped;
        /** Absolute paths that could not be handled. */
        public final List<String> failed = new ArrayList<>();
        public boolean cancelled;

        public boolean ok() {
            return failed.isEmpty() && !cancelled;
        }
    }

    private static final class Cancelled extends RuntimeException {
        Cancelled() {
            super(null, null, false, false);
        }
    }

    static final int BUFFER = 256 * 1024;

    private final Progress progress;
    private volatile boolean cancel;
    private long doneBytes;
    private long totalBytes;
    private Result result;

    public FileOps(Progress progress) {
        this.progress = progress;
    }

    public void cancel() {
        cancel = true;
    }

    public Result copy(OpPlan plan, Conflict conflict) {
        return transfer(plan, conflict, false);
    }

    public Result move(OpPlan plan, Conflict conflict) {
        return transfer(plan, conflict, true);
    }

    public Result delete(OpPlan plan) {
        begin(plan);
        try {
            for (File s : plan.sources) {
                checkCancel();
                if (deleteTree(s)) result.done++;
            }
        } catch (Cancelled c) {
            result.cancelled = true;
        }
        return result;
    }

    /** Renames inside the same folder. Throws IOException with a short reason; never overwrites. */
    public static File rename(File f, String newName) throws IOException {
        String n = newName == null ? "" : newName.trim();
        if (n.isEmpty() || n.equals(".") || n.equals("..") || n.indexOf('/') >= 0 || n.indexOf('\\') >= 0
                || n.indexOf('\0') >= 0) {
            throw new IOException("Invalid name");
        }
        File parent = f.getAbsoluteFile().getParentFile();
        File target = new File(parent, n);
        if (target.equals(f.getAbsoluteFile())) return f;
        if (exists(target)) throw new IOException("Already exists");
        if (!f.renameTo(target)) throw new IOException("Rename failed");
        return target;
    }

    private void begin(OpPlan plan) {
        doneBytes = 0;
        totalBytes = plan.bytes;
        result = new Result();
    }

    private Result transfer(OpPlan plan, Conflict conflict, boolean move) {
        begin(plan);
        try {
            for (File src : plan.sources) {
                checkCancel();
                transferOne(src, plan.destDir, conflict, move);
            }
        } catch (Cancelled c) {
            result.cancelled = true;
        }
        return result;
    }

    private void transferOne(File src, File destDir, Conflict conflict, boolean move) {
        boolean isDir = src.isDirectory();
        File target = new File(destDir, src.getName());
        boolean self = sameFile(src, target);
        if (self && move) {
            result.skipped++;
            return;
        }
        if (self || (exists(target) && conflict == Conflict.KEEP_BOTH)) {
            target = new File(destDir, ConflictNamer.next(src.getName(), isDir, n -> exists(new File(destDir, n))));
        } else if (exists(target)) {
            if (conflict == Conflict.SKIP) {
                result.skipped++;
                return;
            }
            if (OpRules.sourceInside(src, target)) {   // replacing would delete the source itself
                result.failed.add(src.getAbsolutePath());
                return;
            }
            if (!deleteTree(target)) {
                result.failed.add(target.getAbsolutePath());
                return;
            }
        }
        if (move) {
            moveOne(src, target);
        } else if (copyTree(src, target)) {
            result.done++;
        }
    }

    private void moveOne(File src, File target) {
        long size = OpPlan.measure(java.util.Collections.singletonList(src))[1];
        if (src.renameTo(target)) {
            advance(size, src.getName());
            result.done++;
            return;
        }
        boolean copied;
        try {
            copied = copyTree(src, target) && verifyTree(src, target);
        } catch (Cancelled c) {
            deleteQuietly(target);
            throw c;
        }
        if (!copied) {
            deleteQuietly(target);
            if (!result.failed.contains(src.getAbsolutePath())) result.failed.add(src.getAbsolutePath());
            return;
        }
        if (deleteTree(src)) {
            result.done++;
        }
    }

    /** Copies src to dst (dst must not exist). Records failures; returns true when everything was copied. */
    private boolean copyTree(File src, File dst) {
        checkCancel();
        if (src.isDirectory()) {
            if (Files.isSymbolicLink(src.toPath())) {
                result.skipped++;
                return true;
            }
            if (!dst.mkdirs() && !dst.isDirectory()) {
                result.failed.add(src.getAbsolutePath());
                return false;
            }
            File[] kids = src.listFiles();
            if (kids == null) {
                result.failed.add(src.getAbsolutePath());
                return false;
            }
            boolean ok = true;
            for (File k : kids) ok &= copyTree(k, new File(dst, k.getName()));
            return ok;
        }
        try {
            copyFile(src, dst);
            return true;
        } catch (IOException e) {
            result.failed.add(src.getAbsolutePath());
            return false;
        }
    }

    private void copyFile(File src, File dst) throws IOException {
        try {
            try (InputStream in = new FileInputStream(src); FileOutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[BUFFER];
                int n;
                while ((n = in.read(buf)) > 0) {
                    checkCancel();
                    out.write(buf, 0, n);
                    advance(n, src.getName());
                }
                out.flush();
                out.getFD().sync();
            }
            if (dst.length() != src.length()) throw new IOException("Size mismatch");
            dst.setLastModified(src.lastModified());
        } catch (IOException | RuntimeException e) {
            dst.delete();
            throw e;
        }
    }

    /** Same names and sizes on both sides. */
    private static boolean verifyTree(File a, File b) {
        if (a.isDirectory()) {
            if (Files.isSymbolicLink(a.toPath())) return true;
            File[] kids = a.listFiles();
            if (kids == null || !b.isDirectory()) return false;
            for (File k : kids) {
                if (!verifyTree(k, new File(b, k.getName()))) return false;
            }
            return true;
        }
        return b.isFile() && a.length() == b.length();
    }

    private boolean deleteTree(File f) {
        checkCancel();
        boolean ok = true;
        if (f.isDirectory() && !Files.isSymbolicLink(f.toPath())) {
            File[] kids = f.listFiles();
            if (kids != null) {
                for (File k : kids) ok &= deleteTree(k);
            }
            if (ok && !f.delete()) ok = fail(f);
            return ok;
        }
        long size = f.length();
        if (!f.delete()) return fail(f);
        advance(size, f.getName());
        return ok;
    }

    private boolean fail(File f) {
        result.failed.add(f.getAbsolutePath());
        return false;
    }

    private void deleteQuietly(File f) {
        if (f.isDirectory() && !Files.isSymbolicLink(f.toPath())) {
            File[] kids = f.listFiles();
            if (kids != null) {
                for (File k : kids) deleteQuietly(k);
            }
        }
        f.delete();
    }

    private void advance(long bytes, String name) {
        doneBytes += bytes;
        if (progress != null) progress.onProgress(doneBytes, totalBytes, name);
    }

    private void checkCancel() {
        if (cancel) throw new Cancelled();
    }

    private static boolean exists(File f) {
        return f.exists() || Files.isSymbolicLink(f.toPath());
    }

    private static boolean sameFile(File a, File b) {
        try {
            return a.getCanonicalFile().equals(b.getCanonicalFile());
        } catch (IOException e) {
            return a.getAbsoluteFile().equals(b.getAbsoluteFile());
        }
    }
}
