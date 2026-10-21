package com.gathin.roman;

import java.util.Locale;

/**
 * The Roman look v2 switch and its palette (docs/plans/roman-theme-v2.md, section 1.2). Pure Java so the
 * colours and the cache key can be unit tested; every colour the look uses is defined here and nowhere else.
 */
public final class RomanStyle {

    // ---- palette (ARGB) --------------------------------------------------------------------------
    public static final int DEFAULT_GOLD = 0xFFC9A24B;
    public static final int IVORY = 0xFFF3EBDD;
    /** Gregorian date. */
    public static final int IVORY_DIM = 0x99F3EBDD;
    /** AM/PM. */
    public static final int IVORY_DIM_AMPM = 0xB3F3EBDD;
    public static final int STONE_INK = 0xFF1A1511;
    /** Background of icon tiles (HDMI 1 and friends). */
    public static final int ICON_TILE = 0xFF2A221B;
    public static final int RELIEF_SHADOW = 0x80000000;
    /** Dark shadow under the clock. */
    public static final int TEXT_SHADOW = 0x99000000;
    /** Dark shadow under the hero name. */
    public static final int HERO_SHADOW = 0xB3000000;
    public static final int TRAVERTINE_DEEP = 0xFF2E261E;
    public static final int TRAVERTINE_BASE = 0xFF4B3F32;
    public static final int TRAVERTINE_LIGHT = 0xFF5F5140;
    public static final int TRAVERTINE_VEIN = 0xFF8A775F;
    public static final int TRAVERTINE_PIT = 0xFF1F1914;
    public static final int MARBLE_BASE = 0xFF4A4742;
    public static final int MARBLE_LIGHT = 0xFF5C5851;
    public static final int MARBLE_VEIN = 0xFFA39C90;

    // Screensaver only (RomanDream v2); the Home look stays gold on travertine.
    public static final int ROMAN_RED = 0xFF9B1B1E;
    public static final int CRIMSON = 0xFFB0202E;
    public static final int PORPHYRY = 0xFF5E1A24;
    public static final int PORPHYRY_DEEP = 0xFF2A0B10;

    public static final String STONE_TRAVERTINE = "travertine";
    public static final String STONE_MARBLE = "marble";
    public static final String STONE_NONE = "none";

    /** Longest inscription inside the crest. */
    public static final int INSCRIPTION_MAX = 8;

    public final String stone;
    public final int brightness;
    public final int seed;
    public final int vignette;
    public final boolean meander;
    public final boolean pilasters;
    public final boolean crest;
    public final String inscription;
    public final int gold;
    public final int goldLight;
    public final int goldDeep;

    private RomanStyle(String stone, int brightness, int seed, int vignette, boolean meander,
                       boolean pilasters, boolean crest, String inscription, int accent) {
        this.stone = stone;
        this.brightness = brightness;
        this.seed = seed;
        this.vignette = vignette;
        this.meander = meander;
        this.pilasters = pilasters;
        this.crest = crest;
        this.inscription = inscription;
        int[] ramp = goldRamp(accent != 0 ? accent : DEFAULT_GOLD);
        this.goldLight = ramp[0];
        this.gold = ramp[1];
        this.goldDeep = ramp[2];
    }

    /**
     * The style for a config, or null unless {@code style} is "roman" (case-insensitive, trimmed): null
     * means the old look and none of the v2 code runs. Config has already rejected out-of-range numbers.
     * An unknown stone name means travertine; {@code accent} 0 means the default gold.
     */
    public static RomanStyle of(String style, String stone, int brightness, int seed, int vignette,
                         boolean meander, boolean pilasters, boolean crest, String inscription, int accent) {
        if (style == null || !"roman".equals(style.trim().toLowerCase(Locale.ROOT))) return null;
        return new RomanStyle(stoneName(stone), brightness, seed, vignette, meander, pilasters, crest,
                Roman.inscribe(inscription, INSCRIPTION_MAX), accent);
    }

    /** The style of the apps that do not read the launcher's config (Files, Player): travertine, default gold. */
    public static RomanStyle defaults() {
        return of("roman", STONE_TRAVERTINE, 100, 7, 70, true, true, true, "", 0);
    }

    private static String stoneName(String stone) {
        String s = stone == null ? "" : stone.trim().toLowerCase(Locale.ROOT);
        if (s.equals(STONE_MARBLE)) return STONE_MARBLE;
        if (s.equals(STONE_NONE)) return STONE_NONE;
        return STONE_TRAVERTINE;
    }

    /**
     * {light, mid, deep} of a gold: light is the accent blended 35% toward white, deep is the accent times
     * 0.68, per channel, rounded. Alpha is always opaque. #C9A24B gives #DCC38A and #896E33.
     */
    public static int[] goldRamp(int accent) {
        int[] out = new int[3];
        int r = (accent >> 16) & 0xFF, g = (accent >> 8) & 0xFF, b = accent & 0xFF;
        out[0] = 0xFF000000 | light(r) << 16 | light(g) << 8 | light(b);
        out[1] = 0xFF000000 | (accent & 0x00FFFFFF);
        out[2] = 0xFF000000 | deep(r) << 16 | deep(g) << 8 | deep(b);
        return out;
    }

    private static int light(int c) {
        return Math.round(c + (255 - c) * 0.35f);
    }

    private static int deep(int c) {
        return Math.round(c * 0.68f);
    }

    /** The colour with its alpha replaced (0 to 255). */
    public static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    /** Cache key of the rendered stone (used from R2): changes whenever a pixel would. */
    public String stoneKey() {
        return "v1-" + stone + "-" + seed + "-" + brightness + "-" + vignette;
    }
}
