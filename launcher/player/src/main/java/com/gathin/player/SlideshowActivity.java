package com.gathin.player;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.gathin.roman.RomanLook;
import com.gathin.roman.RomanStyle;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Photo slideshow for VIEW image/*. Shows the images of the opened file's folder in natural order, 8 s each,
 * with a 300 ms cross-fade. At most two bitmaps are alive: the one on screen and the next one (the outgoing
 * one is released when the fade ends; the next is only decoded after that). Decoding runs on one background
 * thread at 1920x1080 or less in RGB_565 where the image has no alpha. OK pauses, LEFT/RIGHT step, BACK exits.
 * No handler callback is pending while the activity is stopped or paused.
 */
public class SlideshowActivity extends Activity {

    private static final int MAX_W = 1920;
    private static final int MAX_H = 1080;
    private static final long FADE_MS = 300;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SlideTimer timer = new SlideTimer(SlideTimer.DEFAULT_INTERVAL_MS);
    private ExecutorService decoder;

    private FrameLayout root;
    private ImageView front;
    private ImageView back;
    private TextView pausedLabel;

    /** Null for a bare content URI: then there is one image and no folder. */
    private File folder;
    private Uri singleUri;
    private Playlist playlist;

    private Bitmap frontBmp;
    private Bitmap nextBmp;
    private String nextName;
    /** Bumped to discard decodes that are no longer wanted. */
    private int generation;
    private String loadingName;
    /** Set while the user (or the timer) is waiting for this file; the tick/keys are ignored meanwhile. */
    private String wantedName;
    private boolean wantedForward;

    private Runnable endFade;
    private boolean userPaused;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (timer.isDue(SystemClock.uptimeMillis())) {
                advance(true);
            } else {
                schedule();
            }
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        decoder = Executors.newSingleThreadExecutor();

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        front = newImageView();
        back = newImageView();
        root.addView(back);
        root.addView(front);

        pausedLabel = new TextView(this);
        pausedLabel.setText("Paused");
        RomanLook.text(pausedLabel, 22, 0.08f, RomanStyle.IVORY);
        pausedLabel.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        int m = (int) (32 * getResources().getDisplayMetrics().density);
        lp.setMargins(m, m, m, m);
        root.addView(pausedLabel, lp);
        setContentView(root);
        hideSystemBars();

        resolveSource();
    }

    private ImageView newImageView() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setFocusable(false);
        v.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        return v;
    }

    /** Folder and playlist from the Files path extra or a file: URI; anything else is a single content image. */
    private void resolveSource() {
        String path = getIntent().getStringExtra(PlayerActivity.EXTRA_PATH);
        Uri data = getIntent().getData();
        if (path == null && data != null && "file".equals(data.getScheme())) path = data.getPath();
        if (path != null) {
            File f = new File(path);
            File dir = f.getParentFile();
            if (f.isFile() && f.canRead() && dir != null) {
                String[] names = dir.list();
                folder = dir;
                playlist = Playlist.of(names == null ? java.util.Collections.<String>emptyList()
                        : Arrays.asList(names), f.getName(), Playlist.Kind.IMAGE);
                return;
            }
        }
        singleUri = data;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (frontBmp == null && wantedName == null) {
            String first = playlist != null ? playlist.current() : (singleUri != null ? singleUri.toString() : null);
            if (first == null) {
                finish();
                return;
            }
            wantedName = first;
            wantedForward = true;
            startLoad(first);
        } else if (!userPaused) {
            timer.resume(SystemClock.uptimeMillis());
            schedule();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(tick);
        timer.pause(SystemClock.uptimeMillis());
        generation++;
        loadingName = null;
        wantedName = null;
        finishFade();
        recycleNext();
        front.setImageBitmap(null);
        back.setImageBitmap(null);
        if (frontBmp != null) {
            frontBmp.recycle();
            frontBmp = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        decoder.shutdownNow();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (event.getRepeatCount() == 0) togglePause();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                advance(true);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                advance(false);
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    private void togglePause() {
        long now = SystemClock.uptimeMillis();
        userPaused = !userPaused;
        if (userPaused) {
            timer.pause(now);
            handler.removeCallbacks(tick);
        } else {
            timer.resume(now);
            schedule();
        }
        pausedLabel.setVisibility(userPaused ? View.VISIBLE : View.GONE);
    }

    private void schedule() {
        handler.removeCallbacks(tick);
        long left = timer.remaining(SystemClock.uptimeMillis());
        if (left >= 0) handler.postDelayed(tick, left);
    }

    // ---- moving between slides ------------------------------------------------------------------

    private String peek(boolean forward) {
        if (playlist == null) return null;
        if (forward) {
            if (!playlist.hasNext()) return null;
            String n = playlist.next();
            playlist.previous();
            return n;
        }
        if (!playlist.hasPrevious()) return null;
        String n = playlist.previous();
        playlist.next();
        return n;
    }

    /** Goes to the next or previous photo: the prefetched one when it is ready, else a decode is started. */
    private void advance(boolean forward) {
        handler.removeCallbacks(tick);
        if (playlist == null) return; // a single image just stays
        String name = peek(forward);
        if (name == null) {
            if (forward) finish(); // the last photo has had its time
            else schedule();
            return;
        }
        wantedName = name;
        wantedForward = forward;
        if (name.equals(nextName) && nextBmp != null) {
            Bitmap b = nextBmp;
            nextBmp = null;
            nextName = null;
            consume(b);
        } else if (!name.equals(loadingName)) {
            recycleNext();
            startLoad(name);
        }
    }

    /** The wanted photo arrived: move the cursor onto it and show it. */
    private void consume(Bitmap b) {
        moveCursorTo(wantedName);
        wantedName = null;
        present(b);
    }

    /** The first photo is already under the cursor; later ones are one step away in the wanted direction. */
    private void moveCursorTo(String name) {
        if (playlist == null || name.equals(playlist.current())) return;
        if (wantedForward) playlist.next();
        else playlist.previous();
    }

    private void startLoad(final String name) {
        final int gen = ++generation;
        loadingName = name;
        final File file = folder == null ? null : new File(folder, name);
        final Uri uri = singleUri;
        decoder.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap bmp = decode(file, uri);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        onLoaded(name, bmp, gen);
                    }
                });
            }
        });
    }

    private void onLoaded(String name, Bitmap bmp, int gen) {
        if (gen != generation) {
            if (bmp != null) bmp.recycle();
            return;
        }
        loadingName = null;
        if (bmp == null) {
            Toast.makeText(this, "Cannot show " + name, Toast.LENGTH_SHORT).show();
            if (name.equals(wantedName)) {
                // Skip the bad file in the direction we were going.
                if (playlist == null) {
                    finish();
                    return;
                }
                boolean forward = wantedForward;
                moveCursorTo(name);
                wantedName = null;
                if (frontBmp == null && peek(forward) == null) {
                    finish();
                    return;
                }
                advance(forward);
            }
            return;
        }
        if (name.equals(wantedName)) {
            consume(bmp);
        } else {
            nextName = name;
            nextBmp = bmp;
        }
    }

    private void recycleNext() {
        if (nextBmp != null) nextBmp.recycle();
        nextBmp = null;
        nextName = null;
    }

    /** Runs on the decode thread. Returns null when the file cannot be decoded. */
    private Bitmap decode(File file, Uri uri) {
        try {
            ImageDecoder.Source src = file != null
                    ? ImageDecoder.createSource(file)
                    : ImageDecoder.createSource(getContentResolver(), uri);
            return ImageDecoder.decodeBitmap(src, new ImageDecoder.OnHeaderDecodedListener() {
                @Override
                public void onHeaderDecoded(ImageDecoder d, ImageDecoder.ImageInfo info, ImageDecoder.Source s) {
                    int[] wh = SlideFit.fit(info.getSize().getWidth(), info.getSize().getHeight(), MAX_W, MAX_H);
                    d.setTargetSize(wh[0], wh[1]);
                    d.setMemorySizePolicy(ImageDecoder.MEMORY_POLICY_LOW_RAM);
                }
            });
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    // ---- showing --------------------------------------------------------------------------------

    private void present(Bitmap bmp) {
        finishFade();
        final ImageView in = back;
        final ImageView out = front;
        final Bitmap outBmp = frontBmp;
        in.setImageBitmap(bmp);
        in.setAlpha(0f);
        in.bringToFront();
        pausedLabel.bringToFront();
        front = in;
        frontBmp = bmp;
        back = out;
        endFade = new Runnable() {
            @Override
            public void run() {
                endFade = null;
                out.animate().cancel();
                out.setImageBitmap(null);
                out.setAlpha(0f);
                if (outBmp != null) outBmp.recycle();
                afterShown();
            }
        };
        in.animate().alpha(1f).setDuration(FADE_MS).withEndAction(new Runnable() {
            @Override
            public void run() {
                finishFade();
            }
        });
        out.animate().alpha(0f).setDuration(FADE_MS);
    }

    /** Ends a running cross-fade at once (also releases the outgoing bitmap). */
    private void finishFade() {
        Runnable r = endFade;
        if (r == null) return;
        front.animate().cancel();
        front.setAlpha(1f);
        r.run();
    }

    /** The photo is fully shown and only one bitmap is alive: start its time and fetch the next one. */
    private void afterShown() {
        if (!userPaused) {
            long now = SystemClock.uptimeMillis();
            timer.resume(now);
            timer.restart(now);
            schedule();
        } else {
            timer.restart(SystemClock.uptimeMillis());
        }
        String next = peek(true);
        if (next != null && wantedName == null && loadingName == null) {
            startLoad(next);
        }
    }

    private void hideSystemBars() {
        root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
}
