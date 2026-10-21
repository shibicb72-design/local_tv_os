package com.gathin.files;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Maps a file name's extension to a kind and a MIME type. Pure Java. */
public final class MimeKinds {
    public enum Kind { VIDEO, AUDIO, IMAGE, SUBTITLE, OTHER }

    public static final String UNKNOWN_MIME = "application/octet-stream";

    private static final Map<String, Kind> KINDS = new HashMap<>();
    private static final Map<String, String> MIMES = new HashMap<>();

    private static void add(Kind kind, String mime, String... exts) {
        for (String e : exts) {
            KINDS.put(e, kind);
            MIMES.put(e, mime);
        }
    }

    static {
        add(Kind.VIDEO, "video/x-matroska", "mkv");
        add(Kind.VIDEO, "video/mp4", "mp4", "m4v");
        add(Kind.VIDEO, "video/quicktime", "mov");
        add(Kind.VIDEO, "video/x-msvideo", "avi");
        add(Kind.VIDEO, "video/mp2t", "ts", "m2ts");
        add(Kind.VIDEO, "video/webm", "webm");
        add(Kind.VIDEO, "video/x-ms-wmv", "wmv");
        add(Kind.VIDEO, "video/mpeg", "mpg");
        add(Kind.AUDIO, "audio/mpeg", "mp3");
        add(Kind.AUDIO, "audio/flac", "flac");
        add(Kind.AUDIO, "audio/mp4", "m4a");
        add(Kind.AUDIO, "audio/aac", "aac");
        add(Kind.AUDIO, "audio/ogg", "ogg", "opus");
        add(Kind.AUDIO, "audio/wav", "wav");
        add(Kind.IMAGE, "image/jpeg", "jpg", "jpeg");
        add(Kind.IMAGE, "image/png", "png");
        add(Kind.IMAGE, "image/webp", "webp");
        add(Kind.IMAGE, "image/heic", "heic");
        add(Kind.IMAGE, "image/gif", "gif");
        add(Kind.IMAGE, "image/bmp", "bmp");
        add(Kind.SUBTITLE, "application/x-subrip", "srt");
        add(Kind.SUBTITLE, "text/x-ssa", "ass", "ssa");
        add(Kind.SUBTITLE, "text/vtt", "vtt");
        add(Kind.SUBTITLE, "application/x-subtitle", "sub");
    }

    private MimeKinds() {}

    /** Lower-case extension without the dot, or "" when there is none (".bashrc" has none). */
    static String extension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static Kind kindOf(String name) {
        Kind k = KINDS.get(extension(name));
        return k == null ? Kind.OTHER : k;
    }

    public static String mimeOf(String name) {
        String m = MIMES.get(extension(name));
        return m == null ? UNKNOWN_MIME : m;
    }
}
