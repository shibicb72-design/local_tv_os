package com.gathin.files;

import java.util.ArrayList;
import java.util.List;

/**
 * The roots list (pure, F6): mounted USB volumes first, then "Internal storage". The Android side
 * ({@link Volumes}) fills {@link Raw} from StorageManager and passes the statvfs read-only flag in.
 */
final class VolumeModel {

    static final String STATE_MOUNTED = "mounted";
    static final String STATE_MOUNTED_RO = "mounted_ro";
    static final String INTERNAL_LABEL = "Internal storage";

    /** What StorageManager reports about one volume. */
    static final class Raw {
        final String uuid;
        final String description;
        final String path;
        final boolean removable;
        final boolean primary;
        final String state;
        /** MNT_RDONLY seen by the Android side (statvfs). */
        final boolean readOnly;

        Raw(String uuid, String description, String path, boolean removable, boolean primary,
            String state, boolean readOnly) {
            this.uuid = uuid;
            this.description = description;
            this.path = path;
            this.removable = removable;
            this.primary = primary;
            this.state = state;
            this.readOnly = readOnly;
        }
    }

    /** One entry on the roots screen. */
    static final class Root {
        final String label;
        final String path;
        final boolean removable;
        final boolean readOnly;

        Root(String label, String path, boolean removable, boolean readOnly) {
            this.label = label;
            this.path = path;
            this.removable = removable;
            this.readOnly = readOnly;
        }
    }

    private VolumeModel() {
    }

    static boolean isMounted(String state) {
        return STATE_MOUNTED.equals(state) || STATE_MOUNTED_RO.equals(state);
    }

    /** Unmounted volumes and volumes without a path are dropped; input order is kept inside each group. */
    static List<Root> roots(List<Raw> volumes) {
        List<Root> usb = new ArrayList<>();
        List<Root> internal = new ArrayList<>();
        for (Raw v : volumes) {
            if (v == null || v.path == null || !isMounted(v.state)) continue;
            boolean ro = v.readOnly || STATE_MOUNTED_RO.equals(v.state);
            if (v.removable) {
                usb.add(new Root(usbLabel(v), v.path, true, ro));
            } else {
                String label = v.primary ? INTERNAL_LABEL : nonEmpty(v.description, INTERNAL_LABEL);
                Root r = new Root(label, v.path, false, ro);
                if (v.primary) internal.add(0, r); else internal.add(r);
            }
        }
        List<Root> out = new ArrayList<>(usb);
        out.addAll(internal);
        return out;
    }

    private static String usbLabel(Raw v) {
        if (v.description != null && !v.description.trim().isEmpty()) return v.description.trim();
        if (v.uuid != null && !v.uuid.isEmpty()) return "USB " + v.uuid;
        return "USB";
    }

    private static String nonEmpty(String s, String fallback) {
        return s == null || s.trim().isEmpty() ? fallback : s.trim();
    }
}
