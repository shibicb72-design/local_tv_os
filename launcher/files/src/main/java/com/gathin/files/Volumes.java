package com.gathin.files;

import android.content.Context;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * StorageManager side of the roots list (F6). No polling: {@link #start} registers one mount callback,
 * {@link #stop} (call from onStop) unregisters it.
 */
final class Volumes {

    interface Listener {
        /** Some volume changed state; call {@link #roots} again. Runs on the main thread. */
        void onVolumesChanged();
    }

    private final Context ctx;
    private final StorageManager sm;
    private StorageManager.StorageVolumeCallback callback;

    Volumes(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.sm = (StorageManager) ctx.getSystemService(Context.STORAGE_SERVICE);
    }

    /** Current roots, USB first. Does a statvfs per volume: call off the main thread when browsing is busy. */
    List<VolumeModel.Root> roots() {
        List<VolumeModel.Raw> raw = new ArrayList<>();
        for (StorageVolume v : sm.getStorageVolumes()) {
            File dir = v.getDirectory();
            String path = dir == null ? null : dir.getAbsolutePath();
            raw.add(new VolumeModel.Raw(v.getUuid(), v.getDescription(ctx), path, v.isRemovable(),
                    v.isPrimary(), v.getState(), path != null && isReadOnly(path)));
        }
        return VolumeModel.roots(raw);
    }

    /** True when the mount carries ST_RDONLY (an NTFS stick mounted read-only, for example). */
    static boolean isReadOnly(String path) {
        try {
            return (Os.statvfs(path).f_flag & OsConstants.ST_RDONLY) != 0;
        } catch (ErrnoException e) {
            return false;
        }
    }

    void start(final Listener listener) {
        if (callback != null) return;
        callback = new StorageManager.StorageVolumeCallback() {
            @Override
            public void onStateChanged(StorageVolume volume) {
                listener.onVolumesChanged();
            }
        };
        sm.registerStorageVolumeCallback(ctx.getMainExecutor(), callback);
    }

    void stop() {
        if (callback == null) return;
        sm.unregisterStorageVolumeCallback(callback);
        callback = null;
    }
}
