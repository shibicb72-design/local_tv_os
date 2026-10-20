package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure parsing and decisions for dock_apps entries (no Android types, so JUnit can run it): a package
 * name is an app, "input:&lt;inputId&gt;" or "input:hdmi1" is a TV input. An app that is not installed
 * stays in its slot as a "Not installed" tile; an input that is not registered is skipped.
 */
final class DockSpec {

    static final String INPUT_PREFIX = "input:";

    /** Looks an input argument up; returns the TV input id or null when it is not registered. */
    interface InputResolver {
        String resolve(String arg);
    }

    static final class Slot {
        static final int APP = 0, MISSING = 1, INPUT = 2;

        final int kind;
        /** The package (APP, MISSING) or the TV input id (INPUT). */
        final String key;

        Slot(int kind, String key) {
            this.kind = kind;
            this.key = key;
        }
    }

    private DockSpec() {
    }

    static boolean isInput(String entry) {
        return entry != null && entry.trim().toLowerCase(Locale.ROOT).startsWith(INPUT_PREFIX);
    }

    /** The part after "input:", trimmed; "" for anything that is not an input entry. */
    static String inputArg(String entry) {
        return isInput(entry) ? entry.trim().substring(INPUT_PREFIX.length()).trim() : "";
    }

    /**
     * Resolves an input argument against the passthrough inputs. "hdmiN" is the Nth HDMI input
     * (hdmiIds, in port order); anything else must equal a registered input id exactly.
     */
    static String resolveInput(String arg, List<String> hdmiIds, List<String> allIds) {
        if (arg == null || arg.isEmpty()) return null;
        String lower = arg.toLowerCase(Locale.ROOT);
        if (lower.startsWith("hdmi") && lower.length() > 4) {
            String n = lower.substring(4);
            for (int i = 0; i < n.length(); i++) {
                if (n.charAt(i) < '0' || n.charAt(i) > '9') return exact(arg, allIds);
            }
            if (n.length() > 3) return null;
            int index = Integer.parseInt(n);
            return index >= 1 && index <= hdmiIds.size() ? hdmiIds.get(index - 1) : null;
        }
        return exact(arg, allIds);
    }

    private static String exact(String arg, List<String> allIds) {
        return allIds.contains(arg) ? arg : null;
    }

    /** One slot per dock entry, in order: blanks and duplicates dropped, unresolved inputs skipped. */
    static List<Slot> plan(List<String> dock, Set<String> installedPkgs, InputResolver inputs) {
        List<Slot> out = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (String raw : dock) {
            if (raw == null) continue;
            String entry = raw.trim();
            if (entry.isEmpty() || seen.contains(entry)) continue;
            seen.add(entry);
            if (isInput(entry)) {
                String id = inputs.resolve(inputArg(entry));
                if (id != null) out.add(new Slot(Slot.INPUT, id));
            } else {
                out.add(new Slot(installedPkgs.contains(entry) ? Slot.APP : Slot.MISSING, entry));
            }
        }
        return out;
    }

    /** Name for a "Not installed" tile: a few well-known apps, otherwise the package itself. */
    static String friendlyName(String pkg) {
        switch (pkg) {
            case "com.netflix.ninja": return "Netflix";
            case "com.amazon.amazonvideo.livingroom": return "Prime Video";
            case "com.google.android.youtube.tv": return "YouTube";
            case "in.startv.hotstar": return "Hotstar";
            default: return pkg;
        }
    }
}
