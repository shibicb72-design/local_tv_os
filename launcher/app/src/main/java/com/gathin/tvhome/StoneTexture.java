package com.gathin.tvhome;

/**
 * The procedural stone wall of the Roman look v2 (docs/plans/roman-theme-v2.md, section 1.3). Pure Java, one
 * row at a time, so a full-screen image never has to sit in memory as ints. Only {@code float} arithmetic with
 * + - * /, floor, abs and sqrt is used (all exact IEEE), so the JVM and ART produce the same pixels and the
 * cache file written on one is valid on the other. No sin, cos, pow, exp or random here (a test enforces it).
 */
final class StoneTexture {

    private static final float[] BAYER = {
            0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};

    private final boolean marble;
    private final float brightness;
    private final float vignette;
    private final int w;
    private final int h;
    private final int[] perm = new int[256];
    private final float[] val = new float[256];
    private final float[] rgb = new float[3];

    // Palette as floats, 0..255 per channel.
    private final float[] deep = channels(RomanStyle.TRAVERTINE_DEEP);
    private final float[] base = channels(RomanStyle.TRAVERTINE_BASE);
    private final float[] light = channels(RomanStyle.TRAVERTINE_LIGHT);
    private final float[] vein = channels(RomanStyle.TRAVERTINE_VEIN);
    private final float[] pit = channels(RomanStyle.TRAVERTINE_PIT);
    private final float[] mBase = channels(RomanStyle.MARBLE_BASE);
    private final float[] mLight = channels(RomanStyle.MARBLE_LIGHT);
    private final float[] mVein = channels(RomanStyle.MARBLE_VEIN);

    /**
     * @param stone      "marble", anything else is travertine
     * @param brightness percent, 50..150
     * @param vignette   edge darkening, 0..100
     * @param w          image width in pixels (the pattern is laid out for 960x540)
     * @param h          image height in pixels
     */
    StoneTexture(String stone, int seed, int brightness, int vignette, int w, int h) {
        this.marble = RomanStyle.STONE_MARBLE.equals(stone);
        this.brightness = brightness / 100f;
        this.vignette = vignette / 100f;
        this.w = w;
        this.h = h;
        long[] state = {seed};
        for (int i = 0; i < 256; i++) perm[i] = i;
        for (int i = 255; i > 0; i--) {
            int j = (int) ((next(state) >>> 33) % (i + 1));
            int t = perm[i];
            perm[i] = perm[j];
            perm[j] = t;
        }
        for (int i = 0; i < 256; i++) val[i] = (float) (next(state) >>> 40) / (float) (1 << 24);
    }

    /** SplitMix64. */
    private static long next(long[] s) {
        long z = s[0] += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static float[] channels(int argb) {
        return new float[]{(argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF};
    }

    /** Writes row {@code y} as {@code w} packed RGB_565 pixels. */
    void renderRow(int y, short[] out) {
        for (int x = 0; x < w; x++) {
            pixel(x, y);
            int r = clamp255(rgb[0]);
            int g = clamp255(rgb[1]);
            int b = clamp255(rgb[2]);
            out[x] = (short) ditherPack(r, g, b, x, y);
        }
    }

    private void pixel(int ix, int iy) {
        float x = ix;
        float y = iy;
        float v = y / h;
        // The pattern is designed for 960x540; other sizes scale it.
        float px = x * (960f / w);
        float py = y * (540f / h);
        float calm = smoothstep(0.12f, 0.32f, v);
        if (marble) marble(px, py, v); else travertine(px, py, calm);

        float shade = 0.62f + 0.38f * smoothstep(0f, 0.30f, v);
        float nx = (x / w - 0.5f) * 2f;
        float ny = (v - 0.48f) * 2f * 0.85f;
        float r = (float) Math.sqrt(nx * nx + ny * ny);
        float vig = 1f - 0.65f * vignette * smoothstep(0.45f, 1.25f, r);
        float k = shade * vig * brightness;
        rgb[0] *= k;
        rgb[1] *= k;
        rgb[2] *= k;
    }

    private void travertine(float x, float y, float calm) {
        float wx = x + 18f * (fbm(x / 140f, y / 140f, 3) - 0.5f);
        float wy = y + 6f * (fbm(x / 140f + 31.7f, y / 140f + 11.3f, 3) - 0.5f);
        float s = smoothstep(0.30f, 0.72f, fbm(wx / 380f, wy / 26f, 4));
        if (s < 0.5f) mix3(deep, base, 2f * s, rgb); else mix3(base, light, 2f * s - 1f, rgb);
        float grain = 1f + 0.10f * (fbm(x / 5f, y / 5f, 2) - 0.5f);
        scale(grain);
        float p = smoothstep(0.80f, 0.90f, noise(x / 3.2f, y / 1.6f))
                * smoothstep(0.50f, 0.62f, fbm(x / 70f, y / 24f, 2));
        mixInto(pit, 0.75f * p);
        float t = (0.35f * x + 0.94f * y) / 210f + 2.4f * fbm(x / 160f, y / 160f, 4);
        float d = Math.abs(fract(t) - 0.5f) * 2f;
        float veinMask = 1f - smoothstep(0f, 0.035f, d);
        mixInto(vein, 0.18f * veinMask * calm);
    }

    private void marble(float x, float y, float v) {
        // Marble veins are bright, so they stay out of the header a little longer than the travertine ones.
        float calm = smoothstep(0.18f, 0.38f, v);
        float wx = x + 40f * (fbm(x / 140f, y / 140f, 3) - 0.5f);
        float wy = y + 40f * (fbm(x / 140f + 31.7f, y / 140f + 11.3f, 3) - 0.5f);
        mix3(mBase, mLight, smoothstep(0.35f, 0.75f, fbm(wx / 260f, wy / 260f, 5)), rgb);
        float t = (0.82f * x + 0.57f * y) / 170f + 3.4f * fbm(x / 190f, y / 190f, 5);
        float main = 1f - smoothstep(0f, 0.045f, Math.abs(fract(t) - 0.5f) * 2f);
        mixInto(mVein, 0.55f * main * calm);
        float t2 = (-0.5f * x + 0.87f * y) / 60f + 2.0f * fbm(x / 90f + 7f, y / 90f + 3f, 4);
        float fine = 1f - smoothstep(0f, 0.02f, Math.abs(fract(t2) - 0.5f) * 2f);
        mixInto(mVein, 0.25f * fine * calm);
        scale(1f + 0.12f * (fbm(x / 5f, y / 5f, 2) - 0.5f));
    }

    private static void mix3(float[] a, float[] b, float t, float[] out) {
        for (int i = 0; i < 3; i++) out[i] = a[i] + (b[i] - a[i]) * t;
    }

    private void mixInto(float[] c, float t) {
        for (int i = 0; i < 3; i++) rgb[i] += (c[i] - rgb[i]) * t;
    }

    private void scale(float k) {
        rgb[0] *= k;
        rgb[1] *= k;
        rgb[2] *= k;
    }

    private static int clamp255(float v) {
        int i = (int) (v + 0.5f);
        return i < 0 ? 0 : Math.min(i, 255);
    }

    // ---- noise -----------------------------------------------------------------------------------

    /** Value noise, [0,1). */
    float noise(float x, float y) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        float fx = x - xi;
        float fy = y - yi;
        float u = fade(fx);
        float vv = fade(fy);
        float v00 = corner(xi, yi);
        float v10 = corner(xi + 1, yi);
        float v01 = corner(xi, yi + 1);
        float v11 = corner(xi + 1, yi + 1);
        float top = v00 + (v10 - v00) * u;
        float bottom = v01 + (v11 - v01) * u;
        return top + (bottom - top) * vv;
    }

    private float corner(int xi, int yi) {
        return val[perm[(perm[xi & 255] + yi) & 255]];
    }

    private static float fade(float t) {
        return t * t * t * (t * (t * 6f - 15f) + 10f);
    }

    /** Fractal noise, [0,1): each octave doubles the frequency, halves the weight and turns the lattice. */
    float fbm(float x, float y, int octaves) {
        float sum = 0f;
        float total = 0f;
        float amp = 0.5f;
        float px = x;
        float py = y;
        for (int o = 0; o < octaves; o++) {
            sum += amp * noise(px, py);
            total += amp;
            amp *= 0.5f;
            float nx = (0.8f * px - 0.6f * py) * 2f + 17.3f;
            float ny = (0.6f * px + 0.8f * py) * 2f + 9.1f;
            px = nx;
            py = ny;
        }
        return sum / total;
    }

    static float smoothstep(float a, float b, float x) {
        float t = (x - a) / (b - a);
        t = t < 0f ? 0f : Math.min(t, 1f);
        return t * t * (3f - 2f * t);
    }

    private static float fract(float x) {
        return x - (float) Math.floor(x);
    }

    // ---- RGB_565 ---------------------------------------------------------------------------------

    /** Ordered 4x4 Bayer dither of 8-bit channels into RGB_565 (r in bits 11..15, g 5..10, b 0..4). */
    static int ditherPack(int r, int g, int b, int x, int y) {
        float th = (BAYER[(y & 3) * 4 + (x & 3)] + 0.5f) / 16f;
        int r5 = level(r * 31f / 255f, th, 31);
        int g6 = level(g * 63f / 255f, th, 63);
        int b5 = level(b * 31f / 255f, th, 31);
        return (r5 << 11) | (g6 << 5) | b5;
    }

    private static int level(float v, float th, int max) {
        int i = (int) Math.floor(v + th);
        return i < 0 ? 0 : Math.min(i, max);
    }

    static int unpackR(short p) {
        return Math.round(((p >> 11) & 31) * 255f / 31f);
    }

    static int unpackG(short p) {
        return Math.round(((p >> 5) & 63) * 255f / 63f);
    }

    static int unpackB(short p) {
        return Math.round((p & 31) * 255f / 31f);
    }
}
