package com.gathin.files;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/** Which menu actions are allowed, and the checks made before a paste starts (F10). Pure. */
final class OpRules {

    enum Action { OPEN, OPEN_WITH, RENAME, COPY, MOVE, DELETE, SORT, HIDDEN }

    private OpRules() {
    }

    /** Null when the action is allowed, otherwise the short reason shown next to the greyed-out item. */
    static String disabledReason(Action a, boolean onRoots, boolean hasSelection, boolean selectionIsDir,
                                 boolean readOnlyVolume) {
        switch (a) {
            case SORT:
            case HIDDEN:
                return onRoots ? "open a volume first" : null;
            case OPEN:
                return hasSelection ? null : "nothing selected";
            default:
                break;
        }
        if (onRoots) return "open a volume first";
        if (!hasSelection) return "nothing selected";
        if (a == Action.OPEN_WITH && selectionIsDir) return "folders only open here";
        if ((a == Action.RENAME || a == Action.MOVE || a == Action.DELETE) && readOnlyVolume) {
            return "read-only volume";
        }
        return null;
    }

    /** The volume root containing {@code path} (longest match), or null. */
    static String rootOf(String path, List<String> roots) {
        String best = null;
        for (String r : roots) {
            if (path.equals(r) || path.startsWith(r.endsWith("/") ? r : r + "/")) {
                if (best == null || r.length() > best.length()) best = r;
            }
        }
        return best;
    }

    /**
     * How many sources would land on a name that already exists in {@code destDir}. A source that is itself
     * the target (copy or move into its own folder) is not a conflict: FileOps names it "(2)" or skips it.
     */
    static int conflicts(List<File> sources, File destDir) {
        int n = 0;
        for (File s : sources) {
            File target = new File(destDir, s.getName());
            if (!target.exists() && !Files.isSymbolicLink(target.toPath())) continue;
            if (!sameFile(s, target)) n++;
        }
        return n;
    }

    private static boolean sameFile(File a, File b) {
        try {
            return a.getCanonicalFile().equals(b.getCanonicalFile());
        } catch (IOException e) {
            return a.getAbsoluteFile().equals(b.getAbsoluteFile());
        }
    }
}
