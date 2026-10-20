package com.gathin.player;

import android.app.Activity;
import android.app.Dialog;
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
import android.widget.Toast;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
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

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Playback (P1) with the Roman overlay and resume (P3): plays the intent's file into a SurfaceView, keys go
 * through {@link KeyMap}, the position is saved in onPause and on completion. Subtitles (P4): sidecar files and
 * embedded tracks, drawn by {@link CueView}. The tracks menu arrives in a later step. The player lives between onStart and onStop.
 */
@UnstableApi
public class PlayerActivity extends Activity {

    /** Absolute path sent by Files next to the content URI (see Opener in :files). */
    static final String EXTRA_PATH = "com.gathin.extra.PATH";

    /** Minimum gap between seeks while a key is held; the key repeat rate is far faster than useful. */
    private static final long HELD_SEEK_GAP_MS = 400L;
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
        resumeKey = computeResumeKey();
        overlay.setTitle(displayName());
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
        player.addListener(new Player.Listener() {
            @Override
            public void onVideoSizeChanged(VideoSize size) {
                videoSize = size;
                applyFit();
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                Toast.makeText(PlayerActivity.this, "Cannot play this file: " + error.getErrorCodeName(),
                        Toast.LENGTH_LONG).show();
            }

            @Override
            public void onCues(CueGroup cueGroup) {
                cueView.setCues(cueGroup.cues);
            }

            @Override
            public void onTracksChanged(Tracks tracks) {
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
                    finish();
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
    }

    @Override
    protected void onPause() {
        savePosition();
        super.onPause();
    }

    @Override
    protected void onStop() {
        overlay.release();
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
        KeyMap.Action action = KeyMap.map(keyCode, repeat, overlay.isVisible(), false);
        switch (action) {
            case NONE:
                return super.onKeyDown(keyCode, event);
            case HIDE_OVERLAY:
                overlay.hide();
                return true;
            case SAVE_AND_EXIT:
                finish();   // onPause saves the position
                return true;
            case TOGGLE_PLAY:
                if (repeat == 0) player.setPlayWhenReady(!player.getPlayWhenReady());
                break;
            case SEEK_BACK_SHORT:
            case SEEK_FORWARD_SHORT:
            case SEEK_BACK_LONG:
            case SEEK_FORWARD_LONG:
                seek(KeyMap.seekDelta(action), repeat > 0);
                break;
            default:
                break;   // tracks, next and previous arrive in later steps; the key still shows the overlay
        }
        overlay.show();
        return true;
    }

    /**
     * Default subtitle choice ({@link SubtitlePick}): remembered choice, forced track in the system language,
     * first sidecar, else off. Runs on track changes until the first READY; applying the same choice twice is a
     * no-op, so the selection change it causes does not loop.
     */
    private void chooseSubtitles(Tracks tracks) {
        if (player == null) return;
        Map<String, Tracks.Group> groups = new LinkedHashMap<>();
        Map<String, Integer> indexes = new LinkedHashMap<>();
        List<SubtitlePick.Track> found = new ArrayList<>();
        for (Tracks.Group g : tracks.getGroups()) {
            if (g.getType() != C.TRACK_TYPE_TEXT) continue;
            for (int i = 0; i < g.length; i++) {
                if (!g.isTrackSupported(i)) continue;
                Format f = g.getTrackFormat(i);
                if (f.id == null) continue;
                String id = f.id.startsWith(SubtitlePick.SIDECAR_PREFIX) ? f.id : SubtitlePick.EMBEDDED_PREFIX + f.id;
                if (groups.containsKey(id)) continue;
                groups.put(id, g);
                indexes.put(id, i);
                found.add(new SubtitlePick.Track(id, SubtitleFinder.normalizeLang(f.language),
                        (f.selectionFlags & C.SELECTION_FLAG_FORCED) != 0));
            }
        }
        if (found.isEmpty()) return;
        ResumeStore.Entry saved = resumeKey == null ? null : resume.get(resumeKey);
        String id = SubtitlePick.pick(found, saved == null ? null : saved.subtitleId,
                SubtitleFinder.normalizeLang(Locale.getDefault().getLanguage()));
        if (subtitleApplied && (id == null ? appliedSubtitleId == null : id.equals(appliedSubtitleId))) return;
        subtitleApplied = true;
        appliedSubtitleId = id;
        TrackSelectionParameters.Builder b = player.getTrackSelectionParameters().buildUpon();
        if (id == null) {
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true);
        } else {
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setOverrideForType(
                    new TrackSelectionOverride(groups.get(id).getMediaTrackGroup(), indexes.get(id)));
        }
        player.setTrackSelectionParameters(b.build());
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
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
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
                return new MediaItem.Builder().setUri(Uri.fromFile(f)).setSubtitleConfigurations(sidecars).build();
            }
        }
        Uri data = getIntent().getData();
        return data == null ? null : MediaItem.fromUri(data);
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
