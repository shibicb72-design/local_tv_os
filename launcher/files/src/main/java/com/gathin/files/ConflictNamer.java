package com.gathin.files;

import java.util.function.Predicate;

/** Picks "name (2).ext", "name (3).ext" (folders: "name (2)") for "Keep both". Pure. */
public final class ConflictNamer {
    private ConflictNamer() {}

    /** First free name for {@code name}; {@code taken} says whether a candidate already exists. */
    public static String next(String name, boolean isDir, Predicate<String> taken) {
        int dot = isDir ? -1 : name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        base = base.replaceFirst(" \\(\\d+\\)$", "");
        for (int n = 2; ; n++) {
            String candidate = base + " (" + n + ")" + ext;
            if (!taken.test(candidate)) return candidate;
        }
    }
}
