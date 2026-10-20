package com.gathin.player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;

/**
 * UTF-8 copies of sidecar subtitles in {@code cacheDir/subs}. Named by a hash of the source (path, size, mtime), so
 * an edited file gets a new copy. Bounded: after every write, and once at start, the oldest copies beyond
 * {@link #MAX_FILES} are deleted.
 */
final class SubCache {

    static final int MAX_FILES = 20;
    private static final String DIR = "subs";

    private SubCache() {}

    static File dir(File cacheDir) {
        return new File(cacheDir, DIR);
    }

    /** Writes {@code <hash>.<ext>} and trims the folder; returns the copy. */
    static File write(File dir, String sourceKey, String ext, byte[] utf8) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        File out = new File(dir, hash(sourceKey) + "." + ext);
        Files.write(out.toPath(), utf8);
        out.setLastModified(System.currentTimeMillis());
        prune(dir, MAX_FILES);
        return out;
    }

    /** Deletes the oldest files so that at most {@code max} remain. */
    static void prune(File dir, int max) {
        File[] files = dir.listFiles();
        if (files == null || files.length <= max) return;
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                int d = Long.compare(a.lastModified(), b.lastModified());
                return d != 0 ? d : a.getName().compareTo(b.getName());
            }
        });
        for (int i = 0; i < files.length - max; i++) files[i].delete();
    }

    static String hash(String key) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", d[i] & 0xFF));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);   // SHA-1 exists on every JVM and Android
        }
    }
}
