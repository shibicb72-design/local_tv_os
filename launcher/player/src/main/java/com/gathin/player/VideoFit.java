package com.gathin.player;

/**
 * Pure layout maths: the largest rectangle with the video's display aspect that fits the container.
 * A SurfaceView stretches the picture to its own size, so the activity sizes the view with this.
 */
final class VideoFit {

    private VideoFit() {}

    /** Returns {width, height} in container pixels; the container size itself when the video size is unknown. */
    static int[] fit(int videoW, int videoH, float pixelRatio, int containerW, int containerH) {
        if (videoW <= 0 || videoH <= 0 || containerW <= 0 || containerH <= 0) {
            return new int[]{Math.max(containerW, 0), Math.max(containerH, 0)};
        }
        float ratio = pixelRatio > 0 ? pixelRatio : 1f;
        double aspect = (double) videoW * ratio / videoH;
        double containerAspect = (double) containerW / containerH;
        if (aspect >= containerAspect) {
            return new int[]{containerW, Math.max(1, (int) Math.round(containerW / aspect))};
        }
        return new int[]{Math.max(1, (int) Math.round(containerH * aspect)), containerH};
    }
}
