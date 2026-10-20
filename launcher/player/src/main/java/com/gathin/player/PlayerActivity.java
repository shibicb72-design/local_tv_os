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

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

import com.gathin.roman.RomanDialog;

import java.io.File;

/**
 * Playback (P1) with the Roman overlay and resume (P3): plays the intent's file into a SurfaceView, keys go
 * through {@link KeyMap}, the position is saved in onPause and on completion. Subtitles and the tracks menu
 * arrive in later steps. The player lives between onStart and onStop.
 */
@UnstableApi
public class PlayerActivity extends Activity {

    /** Absolute path sent by Files next to the content URI (see Opener in :files). */
    static final String EXTRA_PATH = "com.gathin.extra.PATH";

    /** Minimum gap between seeks while a key is held; the key repeat rate is far faster than useful. */
    private static final long HELD_SEEK_GAP_MS = 400L;
    private static final String PREFS = "resume";
    private static final String PREF_DATA = "data";

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
            public void onPlaybackStateChanged(int playbackState) {
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
            if (f.isFile() && f.canRead()) return MediaItem.fromUri(Uri.fromFile(f));
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
