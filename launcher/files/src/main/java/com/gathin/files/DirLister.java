package com.gathin.files;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Lists one folder with java.io. Never throws; an unreadable folder gives an empty list and denied(). */
public final class DirLister {
    private boolean denied;

    /** True when the last list() call could not read the folder. */
    public boolean denied() {
        return denied;
    }

    public List<Entry> list(File dir, boolean showHidden) {
        List<Entry> out = new ArrayList<>();
        denied = false;
        File[] files;
        try {
            files = dir == null ? null : dir.listFiles();
        } catch (RuntimeException e) {
            files = null;
        }
        if (files == null) {
            denied = true;
            return out;
        }
        for (File f : files) {
            String name = f.getName();
            if (!showHidden && name.startsWith(".")) continue;
            boolean isDir = f.isDirectory();
            out.add(new Entry(name, f.getAbsolutePath(), isDir, isDir ? 0 : f.length(), f.lastModified(),
                    isDir ? MimeKinds.Kind.OTHER : MimeKinds.kindOf(name)));
        }
        return out;
    }
}
