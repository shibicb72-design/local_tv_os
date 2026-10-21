package com.gathin.player;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

import com.gathin.roman.RomanDialog;
import com.gathin.roman.RomanLook;
import com.gathin.roman.RomanRows;
import com.gathin.roman.RomanStyle;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Playback (P1) with the Roman overlay and resume (P3): plays the intent's file into a SurfaceView, keys go
 * through {@link KeyMap}, the position is saved in onPause and on completion. Subtitles (P4): sidecar files and
 * embedded tracks, drawn by {@link CueView}. Tracks menu (P5): audio, subtitles, "Subtitle file…", remembered per file. The player lives between onStart and onStop.
 */
@UnstableApi
public class PlayerActivity extends Activity {

    /** Absolute path sent by Files next to the content URI (see Opener in :files). */
    static final String EXTRA_PATH = "com.gathin.extra.PATH";

    /** Minimum gap between seeks while a key is held; the key repeat rate is far faster than useful. */
    private static final long HELD_SEEK_GAP_MS = 400L;
    private static final String ID_FILE_LIST = "file-list";
    /** Rows in the "Subtitle file…" list; a folder with more subtitle files than this is cut. */
    private static final int MAX_FILE_ROWS = 60;
    private static final String PREFS = "resume";
    private static final String PREF_DATA = "data";
    /** Settings prefs: the charset used for subtitle files that are not UTF-8 (plan Q4). */
    private static final String SETTINGS = "settings";
    private static final String PREF_SUB_CHARSET = "sub_charset";
    private static final String DEFAULT_SUB_CHARSET = "windows-1256";

    private FrameLayout root;
    private SurfaceView surface;
    private ExoPlayer player;
    private VideoSize videoSize = VideoSize.UNKNOWN;
    private long resumeMs;
    private boolean resumePlaying = true;
    private Overlay overlay;
    private ResumeStore resume;
    private String resumeKey;
    private boolean resumeDecided;
    private Dialog resumeDialog;
    private long lastHeldSeekMs;
    private CueView cueView;
    private List<MediaItem.SubtitleConfiguration> sidecars;
    /** True once the first READY has settled the default subtitle choice; later changes belong to the user. */
    private boolean subtitlesSettled;
    private boolean subtitleApplied;
    private String appliedSubtitleId;
    private TracksMenu menu;
    /** Remembered choices (ResumeStore): audio track id, subtitle id or {@link SubtitlePick#OFF}; null = none made. */
    private String chosenAudioId;
    private String chosenSubtitleId;
    private boolean audioFailureShown;
    private boolean audioRestored;
    /** Audio file (P6): no video surface, title and tags centred, the overlay stays up, no resume. */
    private boolean audioMode;
    private TextView audioTitle;
    private TextView audioArtist;
    private TextView audioAlbum;
    /** Siblings of the file being played; built on first use (next/previous, next track on completion). */
    private Playlist playlist;

    /** A text track of the current media: where it is in the tracks and how to show it. */
    private static final class TextEntry {
        final Tracks.Group group;
        final int index;
        final Format format;
        final boolean supported;

        TextEntry(Tracks.Group group, int index, Format format, boolean supported) {
            this.group = group;
            this.index = index;
            this.format = format;
            this.supported = supported;
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        root = new FrameLayout(this);
        surface = new SurfaceView(this);
        // Never takes focus: keys go to the activity (plan P3 reviewer check).
        surface.setFocusable(false);
        root.addView(surface, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        audioMode = detectAudio();
        if (audioMode) {
            surface.setVisibility(View.GONE);
            buildAudioPanel();
        }
        cueView = new CueView(this);
        root.addView(cueView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> applyFit());
        overlay = new Overlay(this, root, new Overlay.Source() {
            @Override
            public long positionMs() {
                return player == null ? 0L : player.getCurrentPosition();
            }

            @Override
            public long durationMs() {
                return player == null ? 0L : player.getDuration();
            }

            @Override
            public boolean playing() {
                return player != null && player.getPlayWhenReady();
            }
        });
        setContentView(root);
        hideSystemBars();
        resume = new ResumeStore(prefsStore());
        resumeKey = audioMode ? null : computeResumeKey();   // album tracks do not resume
        menu = new TracksMenu(this, root, this::onMenuChosen);
        ResumeStore.Entry remembered = resumeKey == null ? null : resume.get(resumeKey);
        if (remembered != null) {
            chosenAudioId = remembered.audioId;
            chosenSubtitleId = remembered.subtitleId;
        }
        overlay.setTitle(displayName());
        overlay.setPinned(audioMode);
        if (audioMode) audioTitle.setText(displayName());
        SubCache.prune(SubCache.dir(getCacheDir()), SubCache.MAX_FILES);
    }

    @Override
    protected void onStart() {
        super.onStart();
        MediaItem item = mediaItem();
        if (item == null) {
            Toast.makeText(this, "Nothing to play", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        player = PlayerFactory.create(this);
        subtitlesSettled = false;
        subtitleApplied = false;
        audioRestored = false;
        player.addListener(new Player.Listener() {
            @Override
            public void onVideoSizeChanged(VideoSize size) {
                videoSize = size;
                applyFit();
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                showPlaybackFailure("Cannot play this file: " + error.getErrorCodeName());
            }

            @Override
            public void onMediaMetadataChanged(MediaMetadata metadata) {
                if (audioMode) showTags(metadata);
            }

            @Override
            public void onCues(CueGroup cueGroup) {
                cueView.setCues(cueGroup.cues);
            }

            @Override
            public void onTracksChanged(Tracks tracks) {
                if (!audioFailureShown && noPlayableAudio(tracks)) {
                    audioFailureShown = true;
                    showPlaybackFailure("No decoder for this audio track");
                }
                if (!audioRestored) restoreAudio(tracks);
                if (!subtitlesSettled) chooseSubtitles(tracks);
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY && !subtitlesSettled) {
                    chooseSubtitles(player.getCurrentTracks());
                    subtitlesSettled = true;
                }
                if (playbackState == Player.STATE_ENDED) {
                    saveFinished();
                    if (!(audioMode && openSibling(true))) finish();   // audio goes on to the next track
                }
            }
        });
        player.setVideoSurfaceView(surface);
        long saved = resumeKey == null ? 0L : resume.resumePosition(resumeKey);
        boolean ask = !resumeDecided && saved > 0;
        player.setMediaItem(item, resumeMs);
        player.setPlayWhenReady(!ask && resumePlaying);
        player.prepare();
        if (ask) {
            showResumePrompt(saved);
        } else {
            resumeDecided = true;
        }
        if (audioMode) overlay.show();
    }

    @Override
    protected void onPause() {
        savePosition();
        super.onPause();
    }

    @Override
    protected void onStop() {
        overlay.release();
        menu.hide();
        cueView.setCues(Collections.<androidx.media3.common.text.Cue>emptyList());
        if (resumeDialog != null) {
            resumeDialog.dismiss();   // dismiss, not cancel: no "start over" callback
            resumeDialog = null;
        }
        if (player != null) {
            resumeMs = player.getCurrentPosition();
            resumePlaying = player.getPlayWhenReady();
            player.release();
            player = null;
        }
        super.onStop();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (player == null) return super.onKeyDown(keyCode, event);
        int repeat = event.getRepeatCount();
        KeyMap.Action action = KeyMap.map(keyCode, repeat, overlay.isVisible() && !audioMode, menu.isShowing());
        switch (action) {
            case NONE:
                return super.onKeyDown(keyCode, event);   // with the menu open this is focus movement
            case CLOSE_MENU:
                menu.hide();
                return true;
            case OPEN_TRACKS:
                if (repeat == 0 && !audioMode) showTracksMenu();
                return true;
            case HIDE_OVERLAY:
                overlay.hide();
                return true;
            case SAVE_AND_EXIT:
                finish();   // onPause saves the position
                return true;
            case TOGGLE_PLAY:
                if (repeat == 0) player.setPlayWhenReady(!player.getPlayWhenReady());
                break;
            case PLAY:
                player.setPlayWhenReady(true);
                break;
            case PAUSE:
                player.setPlayWhenReady(false);
                break;
            case SEEK_BACK_SHORT:
            case SEEK_FORWARD_SHORT:
            case SEEK_BACK_LONG:
            case SEEK_FORWARD_LONG:
                seek(KeyMap.seekDelta(action), repeat > 0);
                break;
            case NEXT:
            case PREVIOUS:
                if (repeat == 0) openSibling(action == KeyMap.Action.NEXT);
                break;
            default:
                break;
        }
        overlay.show();
        return true;
    }

    private boolean detectAudio() {
        String path = getIntent().getStringExtra(EXTRA_PATH);
        if (path != null) return Playlist.kindOf(new File(path).getName()) == Playlist.Kind.AUDIO;
        String type = getIntent().getType();
        return type != null && type.startsWith("audio/");
    }

    /** Centred Cinzel title with artist and album under it, behind the overlay; nothing is redrawn per frame. */
    private void buildAudioPanel() {
        RomanStyle s = RomanStyle.defaults();
        float density = getResources().getDisplayMetrics().density;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        int pad = Math.round(64 * density);
        panel.setPadding(pad, pad, pad, pad);

        audioTitle = new TextView(this);
        RomanRows.title(audioTitle, s);
        audioTitle.setTextSize(36f);
        audioTitle.setGravity(Gravity.CENTER);
        audioTitle.setMaxLines(2);
        audioTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        panel.addView(audioTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        audioArtist = tagLine(s.gold, density);
        audioAlbum = tagLine(RomanStyle.IVORY, density);
        panel.addView(audioArtist);
        panel.addView(audioAlbum);
        root.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private TextView tagLine(int color, float density) {
        TextView tv = new TextView(this);
        RomanLook.text(tv, 22f, 0.04f, color);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Math.round(12 * density);
        tv.setLayoutParams(lp);
        return tv;
    }

    /** Tags from the file (ID3, Vorbis, MP4); a missing title keeps the file name. */
    private void showTags(MediaMetadata m) {
        CharSequence title = m.title;
        if (title != null && title.length() > 0) {
            audioTitle.setText(title);
            overlay.setTitle(title.toString());
        }
        CharSequence artist = m.artist != null ? m.artist : m.albumArtist;
        audioArtist.setText(artist == null ? "" : artist);
        audioAlbum.setText(m.albumTitle == null ? "" : m.albumTitle);
    }

    /**
     * Moves to the next or previous file of the same kind in the folder by starting this activity for it.
     * False (and nothing happens) when the file is not a plain file or there is no such sibling.
     */
    private boolean openSibling(boolean forward) {
        File current = videoFile();
        if (current == null || current.getParentFile() == null) return false;
        if (playlist == null) {
            String[] names = current.getParentFile().list();
            playlist = Playlist.of(names == null ? Collections.<String>emptyList() : Arrays.asList(names),
                    current.getName(), Playlist.kindOf(current.getName()));
        }
        String name = forward ? playlist.next() : playlist.previous();
        if (name == null) return false;
        startActivity(new Intent(this, PlayerActivity.class)
                .putExtra(EXTRA_PATH, new File(current.getParentFile(), name).getAbsolutePath())
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION));
        finish();   // onPause saves this file's position first
        return true;
    }

    /**
     * Default subtitle choice ({@link SubtitlePick}): remembered choice, forced track in the system language,
     * first sidecar, else off. Runs on track changes until the first READY; applying the same choice twice is a
     * no-op, so the selection change it causes does not loop.
     */
    private void chooseSubtitles(Tracks tracks) {
        if (player == null) return;
        Map<String, TextEntry> entries = textEntries(tracks);
        List<SubtitlePick.Track> found = new ArrayList<>();
        for (Map.Entry<String, TextEntry> e : entries.entrySet()) {
            if (!e.getValue().supported) continue;
            Format f = e.getValue().format;
            found.add(new SubtitlePick.Track(e.getKey(), SubtitleFinder.normalizeLang(f.language),
                    (f.selectionFlags & C.SELECTION_FLAG_FORCED) != 0));
        }
        if (found.isEmpty()) return;
        String id = SubtitlePick.pick(found, chosenSubtitleId,
                SubtitleFinder.normalizeLang(Locale.getDefault().getLanguage()));
        if (subtitleApplied && (id == null ? appliedSubtitleId == null : id.equals(appliedSubtitleId))) return;
        applySubtitle(entries, id);
    }

    /** Text tracks of the media keyed by subtitle id ({@code ext:<file>} or {@code emb:<format id>}). */
    private Map<String, TextEntry> textEntries(Tracks tracks) {
        Map<String, TextEntry> out = new LinkedHashMap<>();
        for (Tracks.Group g : tracks.getGroups()) {
            if (g.getType() != C.TRACK_TYPE_TEXT) continue;
            for (int i = 0; i < g.length; i++) {
                Format f = g.getTrackFormat(i);
                if (f.id == null) continue;
                String id = f.id.startsWith(SubtitlePick.SIDECAR_PREFIX) ? f.id : SubtitlePick.EMBEDDED_PREFIX + f.id;
                if (!out.containsKey(id)) out.put(id, new TextEntry(g, i, f, g.isTrackSupported(i)));
            }
        }
        return out;
    }

    /** Shows the subtitle track {@code id}, or none when null or unknown. */
    private void applySubtitle(Map<String, TextEntry> entries, String id) {
        TextEntry e = id == null ? null : entries.get(id);
        subtitleApplied = true;
        appliedSubtitleId = e == null ? null : id;
        TrackSelectionParameters.Builder b = player.getTrackSelectionParameters().buildUpon();
        if (e == null) {
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true);
        } else {
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setOverrideForType(
                    new TrackSelectionOverride(e.group.getMediaTrackGroup(), e.index));
        }
        player.setTrackSelectionParameters(b.build());
    }

    /** The user's subtitle choice from the menu: applied, remembered with the file, and final for this playback. */
    private void chooseSubtitleByUser(String id) {
        subtitlesSettled = true;
        chosenSubtitleId = id == null ? SubtitlePick.OFF : id;
        saveChoices();
        applySubtitle(textEntries(player.getCurrentTracks()), id);
    }

    private static String audioId(Format f, int group, int index) {
        return "aud:" + (f.id != null ? f.id : group + "/" + index);
    }

    /** Applies the remembered audio track once the tracks are known; nothing to do when none was chosen. */
    private void restoreAudio(Tracks tracks) {
        if (player == null) return;
        if (chosenAudioId == null) {
            audioRestored = true;
            return;
        }
        boolean any = false;
        List<Tracks.Group> groups = tracks.getGroups();
        for (int gi = 0; gi < groups.size(); gi++) {
            Tracks.Group g = groups.get(gi);
            if (g.getType() != C.TRACK_TYPE_AUDIO) continue;
            any = true;
            for (int i = 0; i < g.length; i++) {
                if (g.isTrackSupported(i) && chosenAudioId.equals(audioId(g.getTrackFormat(i), gi, i))) {
                    if (!g.isTrackSelected(i)) selectAudio(g, i);
                    audioRestored = true;
                    return;
                }
            }
        }
        if (any) audioRestored = true;   // the remembered track is not in this file: keep the default
    }

    private void selectAudio(Tracks.Group g, int index) {
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .setOverrideForType(new TrackSelectionOverride(g.getMediaTrackGroup(), index))
                .build());
    }

    private void saveChoices() {
        if (resumeKey != null) resume.saveTracks(resumeKey, chosenAudioId, chosenSubtitleId);
    }

    private void showTracksMenu() {
        if (player == null) return;
        Locale locale = Locale.getDefault();
        Tracks tracks = player.getCurrentTracks();
        List<TracksMenu.Row> rows = new ArrayList<>();
        boolean headed = false;
        List<Tracks.Group> groups = tracks.getGroups();
        for (int gi = 0; gi < groups.size(); gi++) {
            Tracks.Group g = groups.get(gi);
            if (g.getType() != C.TRACK_TYPE_AUDIO) continue;
            if (!headed) rows.add(TracksMenu.Row.header("Audio"));
            headed = true;
            for (int i = 0; i < g.length; i++) {
                Format f = g.getTrackFormat(i);
                boolean ok = g.isTrackSupported(i);
                rows.add(TracksMenu.Row.option("a:" + audioId(f, gi, i),
                        TrackLabels.audio(f.language, f.sampleMimeType, f.channelCount, ok, locale),
                        g.isTrackSelected(i), ok));
            }
        }
        rows.add(TracksMenu.Row.header("Subtitles"));
        Map<String, TextEntry> entries = textEntries(tracks);
        boolean anySelected = false;
        for (TextEntry e : entries.values()) anySelected |= e.group.isTrackSelected(e.index);
        rows.add(TracksMenu.Row.option("s:" + SubtitlePick.OFF, "Off", !anySelected, true));
        for (Map.Entry<String, TextEntry> me : entries.entrySet()) {
            TextEntry e = me.getValue();
            Format f = e.format;
            rows.add(TracksMenu.Row.option("s:" + me.getKey(),
                    TrackLabels.subtitle(f.language, f.label, (f.selectionFlags & C.SELECTION_FLAG_FORCED) != 0,
                            e.supported, locale),
                    e.group.isTrackSelected(e.index), e.supported));
        }
        if (!subtitleFiles().isEmpty()) {
            rows.add(TracksMenu.Row.option(ID_FILE_LIST, "Subtitle file…", false, true));
        }
        menu.show("Tracks", rows);
    }

    private void showSubtitleFileMenu() {
        List<TracksMenu.Row> rows = new ArrayList<>();
        rows.add(TracksMenu.Row.header("Subtitle file"));
        for (String name : subtitleFiles()) {
            rows.add(TracksMenu.Row.option("file:" + name, name,
                    (SubtitlePick.SIDECAR_PREFIX + name).equals(chosenSubtitleId), true));
        }
        menu.show("Tracks", rows);
    }

    private void onMenuChosen(String id) {
        if (player == null) return;
        if (id.equals(ID_FILE_LIST)) {
            showSubtitleFileMenu();
            return;
        }
        menu.hide();
        if (id.startsWith("a:")) {
            List<Tracks.Group> groups = player.getCurrentTracks().getGroups();
            for (int gi = 0; gi < groups.size(); gi++) {
                Tracks.Group g = groups.get(gi);
                if (g.getType() != C.TRACK_TYPE_AUDIO) continue;
                for (int i = 0; i < g.length; i++) {
                    if (id.equals("a:" + audioId(g.getTrackFormat(i), gi, i))) {
                        chosenAudioId = id.substring(2);
                        saveChoices();
                        selectAudio(g, i);
                        return;
                    }
                }
            }
        } else if (id.startsWith("s:")) {
            String sub = id.substring(2);
            chooseSubtitleByUser(sub.equals(SubtitlePick.OFF) ? null : sub);
        } else if (id.startsWith("file:")) {
            pickSubtitleFile(id.substring(5));
        }
    }

    /** A folder file chosen by hand: select it when it is already a sidecar track, else add it and reload. */
    private void pickSubtitleFile(String name) {
        String sidecarId = SubtitlePick.SIDECAR_PREFIX + name;
        TextEntry known = textEntries(player.getCurrentTracks()).get(sidecarId);
        if (known != null && known.supported) {
            chooseSubtitleByUser(sidecarId);
            return;
        }
        File video = videoFile();
        MediaItem.SubtitleConfiguration config = video == null || sidecars == null ? null
                : SubtitleSources.buildOne(video, name, getCacheDir(), subtitleCharset());
        if (config == null) {
            Toast.makeText(this, "Cannot read that subtitle file", Toast.LENGTH_LONG).show();
            return;
        }
        // Stay under SubCache.MAX_FILES so a copy ExoPlayer still needs is never pruned.
        if (sidecars.size() >= SubCache.MAX_FILES - 2) sidecars.remove(0);
        sidecars.add(config);
        chosenSubtitleId = sidecarId;
        saveChoices();
        long position = player.getCurrentPosition();
        boolean playing = player.getPlayWhenReady();
        subtitlesSettled = false;   // the remembered choice is applied on the next READY
        subtitleApplied = false;
        player.setMediaItem(mediaItem(), position);
        player.setPlayWhenReady(playing);
        player.prepare();
    }

    /** Every subtitle file in the video's folder, by name; empty when the video is not a plain file. */
    private List<String> subtitleFiles() {
        List<String> out = new ArrayList<>();
        File video = videoFile();
        String[] names = video == null || video.getParentFile() == null ? null : video.getParentFile().list();
        if (names == null) return out;
        for (String n : names) {
            if (SubtitleFinder.isSubtitleFile(n) && out.size() < MAX_FILE_ROWS) out.add(n);
        }
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    private File videoFile() {
        String path = getIntent().getStringExtra(EXTRA_PATH);
        if (path == null) return null;
        File f = new File(path);
        return f.isFile() ? f : null;
    }

    private Charset subtitleCharset() {
        String name = getSharedPreferences(SETTINGS, MODE_PRIVATE).getString(PREF_SUB_CHARSET, DEFAULT_SUB_CHARSET);
        try {
            return Charset.forName(name);
        } catch (RuntimeException e) {
            return Charset.forName("ISO-8859-1");
        }
    }

    private void seek(long delta, boolean held) {
        long now = SystemClock.uptimeMillis();
        if (held && now - lastHeldSeekMs < HELD_SEEK_GAP_MS) return;
        if (held) lastHeldSeekMs = now;
        player.seekTo(SeekMath.seek(player.getCurrentPosition(), delta, player.getDuration()));
    }

    private void showResumePrompt(long savedMs) {
        overlay.show();
        resumeDialog = RomanDialog.confirm(this, "Resume", displayName(), OverlayMath.resumeLabel(savedMs),
                "Start over", false,
                () -> decide(savedMs),
                () -> decide(0L));
        resumeDialog.show();
    }

    private void decide(long startMs) {
        resumeDialog = null;
        resumeDecided = true;
        if (player == null) return;
        if (startMs > 0) player.seekTo(startMs);
        player.setPlayWhenReady(true);
    }

    /** Not while the prompt is up: the position is still 0 and would erase the saved one. */
    private void savePosition() {
        if (player == null || resumeKey == null || !resumeDecided) return;
        long duration = player.getDuration();
        if (duration <= 0) return;
        resume.savePosition(resumeKey, player.getCurrentPosition(), duration);
    }

    private void saveFinished() {
        if (resumeKey == null || player == null) return;
        long duration = player.getDuration();
        if (duration > 0) resume.savePosition(resumeKey, duration, duration);   // finished: forgotten
    }

    private ResumeStore.Store prefsStore() {
        return prefsStore(this);
    }

    /** The resume blob in SharedPreferences; the Library screen reads the same one. */
    static ResumeStore.Store prefsStore(android.content.Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, MODE_PRIVATE);
        return new ResumeStore.Store() {
            @Override
            public String load() {
                return prefs.getString(PREF_DATA, null);
            }

            @Override
            public void save(String data) {
                prefs.edit().putString(PREF_DATA, data).apply();
            }
        };
    }

    /** Resume needs a real file (path + size + mtime); a bare content URI has none, so it never resumes. */
    private String computeResumeKey() {
        String path = getIntent().getStringExtra(EXTRA_PATH);
        if (path == null) return null;
        File f = new File(path);
        if (!f.isFile()) return null;
        return ResumeStore.key(f.getAbsolutePath(), f.length(), f.lastModified());
    }

    private String displayName() {
        String path = getIntent().getStringExtra(EXTRA_PATH);
        if (path != null) return new File(path).getName();
        Uri data = getIntent().getData();
        if (data == null) return "";
        try (Cursor c = getContentResolver().query(data, new String[]{OpenableColumns.DISPLAY_NAME},
                null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (RuntimeException ignored) {
            // fall through to the last path segment
        }
        String seg = data.getLastPathSegment();
        return seg == null ? "" : seg;
    }

    /** The path from Files when readable (sidecar files can then be found), else the intent's URI. */
    private MediaItem mediaItem() {
        String path = getIntent().getStringExtra(EXTRA_PATH);
        if (path != null) {
            File f = new File(path);
            if (f.isFile() && f.canRead()) {
                if (sidecars == null) {
                    sidecars = SubtitleSources.build(f, getCacheDir(), subtitleCharset(),
                            Locale.getDefault().getLanguage());
                }
                addRememberedSidecar(f);
                return new MediaItem.Builder().setUri(Uri.fromFile(f)).setSubtitleConfigurations(sidecars).build();
            }
        }
        Uri data = getIntent().getData();
        return data == null ? null : MediaItem.fromUri(data);
    }

    /** A subtitle file picked by hand can have another name than the video's: put it back so the choice is restored. */
    private void addRememberedSidecar(File video) {
        String id = chosenSubtitleId;
        if (id == null || !id.startsWith(SubtitlePick.SIDECAR_PREFIX)) return;
        for (MediaItem.SubtitleConfiguration c : sidecars) {
            if (id.equals(c.id)) return;
        }
        MediaItem.SubtitleConfiguration config = SubtitleSources.buildOne(video,
                id.substring(SubtitlePick.SIDECAR_PREFIX.length()), getCacheDir(), subtitleCharset());
        if (config != null) sidecars.add(config);
    }

    private static boolean noPlayableAudio(Tracks tracks) {
        boolean any = false;
        for (Tracks.Group g : tracks.getGroups()) {
            if (g.getType() != C.TRACK_TYPE_AUDIO) continue;
            any = true;
            if (g.isSupported()) return false;
        }
        return any;
    }

    /** Plan D7: say what failed on the overlay and offer the file to any other installed player. */
    private void showPlaybackFailure(String message) {
        overlay.setTitle(message);
        overlay.show();
        String path = getIntent().getStringExtra(EXTRA_PATH);
        Uri data = path != null ? Uri.fromFile(new File(path)) : getIntent().getData();
        if (data == null) return;
        Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(data, "*/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent chooser = Intent.createChooser(view, "Open in another player");
        chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS,
                new android.content.ComponentName[]{new android.content.ComponentName(this, PlayerActivity.class)});
        try {
            startActivity(chooser);
        } catch (RuntimeException e) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }

    private void applyFit() {
        int[] wh = VideoFit.fit(videoSize.width, videoSize.height, videoSize.pixelWidthHeightRatio,
                root.getWidth(), root.getHeight());
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) surface.getLayoutParams();
        if (lp.width != wh[0] || lp.height != wh[1]) {
            lp.width = wh[0];
            lp.height = wh[1];
            surface.setLayoutParams(lp);
        }
    }

    private void hideSystemBars() {
        root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
}
