package com.gathin.files;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * Path handling for {@link StreamProvider} (F8, pure Java). A path travels in a content URI as one segment of
 * URL-safe Base64 (UTF-8, no padding), optionally followed by a second, cosmetic file-name segment.
 */
public final class UriPaths {

    public static final String AUTHORITY = "com.gathin.files.stream";

    private UriPaths() {}

    public static String encode(String path) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(path.getBytes(StandardCharsets.UTF_8));
    }

    /** The decoded path, or null when the segment is not valid Base64 or the result is not a safe absolute path. */
    public static String decode(String segment) {
        if (segment == null || segment.isEmpty()) return null;
        String path;
        try {
            path = new String(Base64.getUrlDecoder().decode(segment), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
        return isSafe(path) ? path : null;
    }

    /** Absolute, no NUL, no ".." component. */
    public static boolean isSafe(String path) {
        if (path == null || path.isEmpty() || path.charAt(0) != '/') return false;
        if (path.indexOf('\0') >= 0) return false;
        for (String part : path.split("/")) {
            if (part.equals("..")) return false;
        }
        return true;
    }

    /** True when {@code canonicalPath} is a root or lies below one. Roots are canonical paths without a trailing slash. */
    public static boolean isInside(String canonicalPath, List<String> roots) {
        if (canonicalPath == null || roots == null) return false;
        for (String root : roots) {
            if (root == null || root.isEmpty()) continue;
            if (canonicalPath.equals(root)) return true;
            String prefix = root.endsWith("/") ? root : root + "/";
            if (canonicalPath.startsWith(prefix)) return true;
        }
        return false;
    }

    /**
     * Decode a URI segment and resolve it to a regular file inside {@code roots}. The canonical path is checked
     * (so a symlink pointing out of the volume is refused). Returns null when anything is wrong; never throws.
     */
    public static File resolve(String segment, List<String> roots) {
        String path = decode(segment);
        if (path == null) return null;
        try {
            File f = new File(path).getCanonicalFile();
            if (!isInside(f.getPath(), roots)) return null;
            return f.isFile() ? f : null;
        } catch (IOException | SecurityException e) {
            return null;
        }
    }
}
