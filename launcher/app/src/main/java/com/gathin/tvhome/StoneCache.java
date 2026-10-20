package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * The Roman look's stone wall (docs/plans/roman-theme-v2.md, R2): rendered once by {@link StoneTexture} on a
 * background thread, kept as an RGB_565 file in the cache directory, and handed out as a hardware bitmap (the
 * pixels live in graphics memory only). Home must never fail because of the stone: any problem leaves the
 * plain background and is only logged.
 */
final class StoneCache {

    private static final String TAG = "StoneCache";
    /** Half of 1920x1080; the ImageView stretches it. */
    static final int WIDTH = 960;
    static final int HEIGHT = 540;
    private static final int HEADER = 16;

    private static Bitmap last;
    private static String lastKey;
    private static boolean inFlight;
    /** Whoever asked last: after an activity recreation the new activity gets the result, not the dead one. */
    private static Consumer<Bitmap> pending;

    private StoneCache() {
    }

    /** Calls {@code onReady} on the main thread with the stone; never calls it when something went wrong. */
    static void load(Context context, RomanStyle style, Consumer<Bitmap> onReady) {
        final String key = style.stoneKey();
        if (last != null && !last.isRecycled() && key.equals(lastKey)) {
            onReady.accept(last);
            return;
        }
        pending = onReady;
        if (inFlight) return;
        inFlight = true;
        final File dir = context.getApplicationContext().getCacheDir();
        final Handler main = new Handler(Looper.getMainLooper());
        Thread t = new Thread(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            Bitmap result = null;
            try {
                result = build(dir, style, key);
            } catch (Exception | OutOfMemoryError e) {
                Log.w(TAG, "stone failed, plain background stays", e);
            }
            final Bitmap bmp = result;
            main.post(() -> {
                inFlight = false;
                if (bmp == null) {
                    pending = null;
                    return;
                }
                last = bmp;
                lastKey = key;
                Consumer<Bitmap> cb = pending;
                pending = null;
                if (cb != null) cb.accept(bmp);
            });
        }, "stone");
        t.start();
    }

    private static Bitmap build(File dir, RomanStyle style, String key) throws IOException {
        File file = new File(dir, "stone-" + key + ".565");
        byte[] data = read(file, key);
        if (data == null) {
            long t0 = System.currentTimeMillis();
            render(dir, file, style, key);
            Log.d(TAG, "stone rendered in " + (System.currentTimeMillis() - t0) + " ms");
            data = read(file, key);
            if (data == null) return null;
        }
        Bitmap soft = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.RGB_565);
        soft.copyPixelsFromBuffer(ByteBuffer.wrap(data));
        Bitmap hw = soft.copy(Bitmap.Config.HARDWARE, false);
        if (hw != null) {
            soft.recycle();
            return hw;
        }
        Log.w(TAG, "no hardware copy, keeping the software bitmap");
        return soft;
    }

    /** The pixel bytes of a cache file, or null when it is missing, the wrong size or for another key. */
    private static byte[] read(File file, String key) {
        if (!file.isFile() || file.length() != HEADER + (long) WIDTH * HEIGHT * 2) return null;
        try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
            if (in.readInt() != magic() || in.readInt() != WIDTH || in.readInt() != HEIGHT
                    || in.readInt() != key.hashCode()) return null;
            byte[] data = new byte[WIDTH * HEIGHT * 2];
            in.readFully(data);
            return data;
        } catch (IOException e) {
            return null;
        }
    }

    /** 'S','T','N','1'. */
    private static int magic() {
        return 'S' << 24 | 'T' << 16 | 'N' << 8 | '1';
    }

    /** Renders row by row into a temp file and renames it only when complete; older stones are deleted. */
    private static void render(File dir, File file, RomanStyle style, String key) throws IOException {
        File tmp = new File(dir, "stone-" + key + ".tmp");
        StoneTexture tex = new StoneTexture(style.stone, style.seed, style.brightness, style.vignette, WIDTH, HEIGHT);
        short[] row = new short[WIDTH];
        byte[] bytes = new byte[WIDTH * 2];
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmp), 64 * 1024))) {
            out.writeInt(magic());
            out.writeInt(WIDTH);
            out.writeInt(HEIGHT);
            out.writeInt(key.hashCode());
            for (int y = 0; y < HEIGHT; y++) {
                tex.renderRow(y, row);
                for (int x = 0; x < WIDTH; x++) {
                    bytes[2 * x] = (byte) row[x];            // little-endian, as the bitmap copy expects
                    bytes[2 * x + 1] = (byte) (row[x] >> 8);
                }
                out.write(bytes);
            }
        } catch (IOException | RuntimeException e) {
            tmp.delete();
            throw e;
        }
        if (file.exists() && !file.delete()) {
            tmp.delete();
            throw new IOException("cannot replace " + file);
        }
        if (!tmp.renameTo(file)) {
            tmp.delete();
            throw new IOException("cannot rename " + tmp);
        }
        File[] old = dir.listFiles((d, name) -> name.startsWith("stone-") && !name.equals(file.getName()));
        if (old != null) for (File f : old) f.delete();
    }
}
