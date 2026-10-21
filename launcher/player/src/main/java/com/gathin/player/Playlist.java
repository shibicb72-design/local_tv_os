package com.gathin.player;

import com.gathin.roman.NaturalOrder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The files of one folder that share the kind of the file being played (audio, video or image), in natural
 * order ("Ep 2" before "Ep 10"), with a cursor. No wrap-around: past the last file {@link #next()} is null.
 * Pure Java: the caller lists the folder and passes the names.
 */
final class Playlist {

    enum Kind { VIDEO, AUDIO, IMAGE, OTHER }

    private final List<String> names;
    private int index;

    private Playlist(List<String> names, int index) {
        this.names = names;
        this.index = index;
    }

    /**
     * @param folderNames file names in the folder (any order, any kind)
     * @param current     the file being played; it is part of the list even when {@code folderNames} lacks it
     * @param kind        which files count as siblings
     */
    static Playlist of(Collection<String> folderNames, String current, Kind kind) {
        List<String> list = new ArrayList<>();
        for (String n : folderNames) {
            if (n != null && kindOf(n) == kind && !n.equals(current)) list.add(n);
        }
        if (current != null) list.add(current);
        Collections.sort(list, NaturalOrder.INSTANCE);
        return new Playlist(list, current == null ? 0 : list.indexOf(current));
    }

    /** The kind of a file from its extension, case-insensitive. */
    static Kind kindOf(String name) {
        if (name == null) return Kind.OTHER;
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) return Kind.OTHER;
        switch (name.substring(dot + 1).toLowerCase(Locale.ROOT)) {
            case "mkv": case "mp4": case "m4v": case "mov": case "avi":
            case "ts": case "m2ts": case "webm": case "wmv": case "mpg":
                return Kind.VIDEO;
            case "mp3": case "flac": case "m4a": case "aac": case "ogg": case "opus": case "wav":
                return Kind.AUDIO;
            case "jpg": case "jpeg": case "png": case "webp": case "heic": case "gif": case "bmp":
                return Kind.IMAGE;
            default:
                return Kind.OTHER;
        }
    }

    int size() {
        return names.size();
    }

    /** Position of the current file, or -1 when the list is empty. */
    int index() {
        return names.isEmpty() ? -1 : index;
    }

    String current() {
        return names.isEmpty() ? null : names.get(index);
    }

    boolean hasNext() {
        return index + 1 < names.size();
    }

    boolean hasPrevious() {
        return index > 0;
    }

    /** Moves to and returns the next file, or null (cursor unchanged) at the end. */
    String next() {
        if (!hasNext()) return null;
        return names.get(++index);
    }

    /** Moves to and returns the previous file, or null (cursor unchanged) at the start. */
    String previous() {
        if (!hasPrevious()) return null;
        return names.get(--index);
    }
}
