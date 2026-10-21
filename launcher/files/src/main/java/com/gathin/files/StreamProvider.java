package com.gathin.files;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only access to files on the mounted volumes (F8): {@code content://com.gathin.files.stream/<base64 path>[/name]}.
 * Not exported; callers get a per-URI read grant from {@link Opener}. Every call canonicalises the path and
 * requires it to be inside a currently mounted volume root.
 */
public final class StreamProvider extends ContentProvider {

    static Uri uriFor(String path) {
        Uri.Builder b = new Uri.Builder().scheme("content").authority(UriPaths.AUTHORITY)
                .appendPath(UriPaths.encode(path));
        b.appendPath(new File(path).getName());
        return b.build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        List<String> segments = uri.getPathSegments();
        if (segments.isEmpty()) throw new FileNotFoundException("no path");
        File f = UriPaths.resolve(segments.get(0), rootPaths());
        if (f == null) throw new FileNotFoundException("not available");
        return f;
    }

    private List<String> rootPaths() {
        List<String> out = new ArrayList<>();
        for (VolumeModel.Root r : new Volumes(getContext()).roots()) {
            try {
                out.add(new File(r.path).getCanonicalPath());
            } catch (java.io.IOException e) {
                // a root that cannot be resolved grants nothing
            }
        }
        return out;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("read only");
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        try {
            return MimeKinds.mimeOf(resolve(uri).getName());
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File f;
        try {
            f = resolve(uri);
        } catch (FileNotFoundException e) {
            return null;
        }
        String[] cols = projection != null ? projection
                : new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = f.getName();
            else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = f.length();
        }
        MatrixCursor c = new MatrixCursor(cols, 1);
        c.addRow(row);
        return c;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read only");
    }
}
