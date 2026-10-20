package com.gathin.files;

/** One row of a folder listing: a small immutable value. */
public final class Entry {
    public final String name;
    public final String path;
    public final boolean isDir;
    public final long size;
    public final long mtime;
    public final MimeKinds.Kind kind;

    public Entry(String name, String path, boolean isDir, long size, long mtime, MimeKinds.Kind kind) {
        this.name = name;
        this.path = path;
        this.isDir = isDir;
        this.size = size;
        this.mtime = mtime;
        this.kind = kind;
    }
}
