package com.gathin.player;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
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

import java.io.File;

/**
 * Minimal playback (P1): plays the intent's file into a SurfaceView. Overlay, resume, subtitles and
 * the tracks menu arrive in later steps. The player lives between onStart and onStop.
 */
@UnstableApi
public class PlayerActivity extends Activity {

    /** Absolute path sent by Files next to the content URI (see Opener in :files). */
    static final String EXTRA_PATH = "com.gathin.extra.PATH";

    private FrameLayout root;
    private SurfaceView surface;
    private ExoPlayer player;
    private VideoSize videoSize = VideoSize.UNKNOWN;
    private long resumeMs;
    private boolean resumePlaying = true;

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
        setContentView(root);
        hideSystemBars();
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
                if (playbackState == Player.STATE_ENDED) finish();
            }
        });
        player.setVideoSurfaceView(surface);
        player.setMediaItem(item, resumeMs);
        player.setPlayWhenReady(resumePlaying);
        player.prepare();
    }

    @Override
    protected void onStop() {
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
        if (player != null && event.getRepeatCount() == 0) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    player.setPlayWhenReady(!player.getPlayWhenReady());
                    return true;
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                    player.setPlayWhenReady(true);
                    return true;
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    player.setPlayWhenReady(false);
                    return true;
                default:
                    break;
            }
        }
        return super.onKeyDown(keyCode, event);
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
