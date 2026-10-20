package com.gathin.files;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.LongSupplier;

/** What an operation will touch (files, bytes), and the checks that must pass before a byte moves. Pure. */
public final class OpPlan {
    /** Why a plan was refused. */
    public enum Reason { INTO_ITSELF, NO_SPACE }

    public static final class Refused extends IOException {
        public final Reason reason;

        Refused(Reason reason, String message) {
            super(message);
            this.reason = reason;
        }
    }

    public final List<File> sources;
    /** Target folder; null for delete. */
    public final File destDir;
    public final int files;
    public final long bytes;

    private OpPlan(List<File> sources, File destDir, int files, long bytes) {
        this.sources = sources;
        this.destDir = destDir;
        this.files = files;
        this.bytes = bytes;
    }

    /** Totals for a delete. */
    public static OpPlan forDelete(List<File> sources) {
        long[] t = measure(sources);
        return new OpPlan(Collections.unmodifiableList(new ArrayList<>(sources)), null, (int) t[0], t[1]);
    }

    /**
     * Plan for copy or move into {@code destDir}. Refuses a folder into itself or a descendant, and (when
     * {@code needsSpace}) a total larger than {@code freeBytes}. Same-volume moves pass needsSpace=false.
     */
    public static OpPlan forTransfer(List<File> sources, File destDir, boolean needsSpace, LongSupplier freeBytes)
            throws Refused {
        File dest = canonical(destDir);
        for (File s : sources) {
            if (!s.isDirectory() || Files.isSymbolicLink(s.toPath())) continue;
            File src = canonical(s);
            if (isSameOrInside(dest, src)) {
                throw new Refused(Reason.INTO_ITSELF, "Cannot put " + s.getName() + " inside itself");
            }
        }
        long[] t = measure(sources);
        if (needsSpace && t[1] > freeBytes.getAsLong()) {
            throw new Refused(Reason.NO_SPACE, "Not enough free space");
        }
        return new OpPlan(Collections.unmodifiableList(new ArrayList<>(sources)), destDir, (int) t[0], t[1]);
    }

    /** {files, bytes} under the given roots; symlinked folders are not followed. */
    static long[] measure(List<File> roots) {
        long[] t = new long[2];
        for (File r : roots) measure(r, t);
        return t;
    }

    private static void measure(File f, long[] t) {
        if (f.isDirectory() && !Files.isSymbolicLink(f.toPath())) {
            File[] kids = f.listFiles();
            if (kids == null) return;
            for (File k : kids) measure(k, t);
        } else {
            t[0]++;
            t[1] += f.length();
        }
    }

    private static File canonical(File f) {
        try {
            return f.getCanonicalFile();
        } catch (IOException e) {
            return f.getAbsoluteFile();
        }
    }

    private static boolean isSameOrInside(File child, File parent) {
        for (File c = child; c != null; c = c.getParentFile()) {
            if (c.equals(parent)) return true;
        }
        return false;
    }
}
