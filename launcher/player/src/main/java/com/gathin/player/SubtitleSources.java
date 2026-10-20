package com.gathin.player;

import android.net.Uri;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Turns the sidecar subtitles next to a video into {@link MediaItem.SubtitleConfiguration}s: each file is read,
 * converted to UTF-8 ({@link SrtText}) and handed to ExoPlayer as a copy in {@link SubCache}. ASS/SSA files are
 * played as plain text; their styling is dropped on purpose.
 */
@UnstableApi
final class SubtitleSources {

    /** Fewer than {@link SubCache#MAX_FILES}, so a copy is never trimmed while ExoPlayer still has to open it. */
    static final int MAX_SIDECARS = 12;
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private SubtitleSources() {}

    static List<MediaItem.SubtitleConfiguration> build(File video, File cacheDir, Charset fallback,
                                                       String systemLang) {
        List<MediaItem.SubtitleConfiguration> out = new ArrayList<>();
        File folder = video.getParentFile();
        String[] names = folder == null ? null : folder.list();
        if (names == null) return out;
        File dir = SubCache.dir(cacheDir);
        for (SubtitleFinder.Candidate c : SubtitleFinder.find(video.getName(), Arrays.asList(names), systemLang)) {
            if (out.size() >= MAX_SIDECARS) break;
            MediaItem.SubtitleConfiguration config = config(new File(folder, c.name), c.name, c.language,
                    c.forced, dir, fallback);
            if (config != null) out.add(config);
        }
        return out;
    }

    /** One subtitle file of the video's folder picked by hand ("Subtitle file…"); null when it cannot be read. */
    static MediaItem.SubtitleConfiguration buildOne(File video, String name, File cacheDir, Charset fallback) {
        File folder = video.getParentFile();
        if (folder == null || !SubtitleFinder.isSubtitleFile(name)) return null;
        return config(new File(folder, name), name, null, false, SubCache.dir(cacheDir), fallback);
    }

    private static MediaItem.SubtitleConfiguration config(File src, String name, String language, boolean forced,
                                                          File dir, Charset fallback) {
        if (!src.isFile() || src.length() > MAX_BYTES) return null;
        try {
            byte[] utf8 = SrtText.toUtf8(Files.readAllBytes(src.toPath()), fallback);
            String key = src.getAbsolutePath() + "|" + src.length() + "|" + src.lastModified();
            File copy = SubCache.write(dir, key, extensionOf(name), utf8);
            return new MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(copy))
                    .setMimeType(SubtitleFinder.mimeFor(name))
                    .setLanguage(language)
                    .setLabel(name)
                    .setId(SubtitlePick.SIDECAR_PREFIX + name)
                    .setSelectionFlags(forced ? C.SELECTION_FLAG_FORCED : 0)
                    .build();
        } catch (IOException | RuntimeException e) {
            return null;   // unreadable or oversized file: playback goes on without that subtitle
        }
    }

    private static String extensionOf(String name) {
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(java.util.Locale.ROOT);
    }
}
