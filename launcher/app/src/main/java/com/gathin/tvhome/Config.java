package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The launcher's settings: the values in res/values/config.xml, with an optional overlay read from
 * {@code config.json} in the app's external files directory
 * ({@code /sdcard/Android/data/com.gathin.tvhome/files/}). Every key in the JSON is optional and an
 * unknown or badly typed key is ignored, so a theme can change the dock and the look without a rebuild:
 * {@code tools/theme.sh themes/<name>} pushes a theme folder there and restarts Home.
 *
 * Read once in {@link MainActivity#onCreate}; nothing here touches the network.
 */
final class Config {
    private static final String TAG = "TvHome.Config";
    static final String FILE_NAME = "config.json";
    static final String WALLPAPER_NAME = "wallpaper.jpg";

    final List<String> dockApps;
    final List<String> hiddenApps;
    /** Keep a dimmed "Not installed" tile for a missing dock app (config.xml, dock_placeholders). */
    final boolean dockPlaceholders;
    final String liveTvPackage;
    final String liveTvAction;
    final String sourcePanelService;
    final String sourcePanelAction;
    final String inputAction;
    final int columns;
    /** Tiles in the dock row (dock_columns); equals {@link #columns} unless set. */
    final int dockColumns;
    final int rowsBelowDock;
    final boolean shamsiDate;
    final boolean moduleCards;
    /** Calmer motion for weak GPUs (config.xml, reduced_motion): stiff spring, short fades, no replay. */
    final boolean reducedMotion;
    final String vpnApp;
    final boolean showHomeSettingsShortcut;
    /** Background colour behind everything, #RRGGBB; black unless a theme says otherwise. */
    final int backgroundColor;
    /** Header font: "serif" for the system serif face, anything else keeps Outfit. */
    final String font;
    /** Show the date in Roman numerals. */
    final boolean romanDate;
    /** Accent for the greeting and the laurel, or 0 for the default look. */
    final int accentColor;
    /** Show the laurel wreath beside the clock. */
    final boolean laurel;
    /** Full-screen picture behind the launcher, or null for the plain colour. */
    final File wallpaper;
    /** Whether the overlay file was present and parsed. */
    final boolean overlayApplied;
    /** Roman look v2 (config.xml, style and roman_*), or null when style is not "roman": the old look. */
    final RomanStyle roman;

    private Config(List<String> dockApps, List<String> hiddenApps, boolean dockPlaceholders,
                   String liveTvPackage, String liveTvAction,
                   String sourcePanelService, String sourcePanelAction, String inputAction,
                   int columns, int dockColumns, int rowsBelowDock,
                   boolean shamsiDate, boolean moduleCards, boolean reducedMotion, String vpnApp,
                   boolean showHomeSettingsShortcut, int backgroundColor,
                   String font, boolean romanDate, int accentColor, boolean laurel,
                   File wallpaper, boolean overlayApplied, RomanStyle roman) {
        this.dockApps = Collections.unmodifiableList(dockApps);
        this.hiddenApps = Collections.unmodifiableList(hiddenApps);
        this.dockPlaceholders = dockPlaceholders;
        this.liveTvPackage = liveTvPackage;
        this.liveTvAction = liveTvAction;
        this.sourcePanelService = sourcePanelService;
        this.sourcePanelAction = sourcePanelAction;
        this.inputAction = inputAction;
        this.columns = columns;
        this.dockColumns = dockColumns;
        this.rowsBelowDock = rowsBelowDock;
        this.shamsiDate = shamsiDate;
        this.moduleCards = moduleCards;
        this.reducedMotion = reducedMotion;
        this.vpnApp = vpnApp;
        this.showHomeSettingsShortcut = showHomeSettingsShortcut;
        this.backgroundColor = backgroundColor;
        this.font = font;
        this.romanDate = romanDate;
        this.accentColor = accentColor;
        this.laurel = laurel;
        this.wallpaper = wallpaper;
        this.overlayApplied = overlayApplied;
        this.roman = roman;
    }

    /** The folder the theme tools push into; null when external storage is unavailable. */
    static File dir(Context ctx) {
        return ctx.getExternalFilesDir(null);
    }

    static Config load(Context ctx) {
        Resources res = ctx.getResources();
        List<String> dock = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.dock_apps)));
        List<String> hidden = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.hidden_apps)));
        boolean dockPlaceholders = res.getBoolean(R.bool.dock_placeholders);
        String liveTv = res.getString(R.string.live_tv_package).trim();
        String liveTvAction = res.getString(R.string.live_tv_action).trim();
        String panelService = res.getString(R.string.source_panel_service).trim();
        String panelAction = res.getString(R.string.source_panel_action).trim();
        String inputAction = res.getString(R.string.input_action).trim();
        int columns = res.getInteger(R.integer.columns);
        int dockColumns = res.getInteger(R.integer.dock_columns);
        int rowsBelow = res.getInteger(R.integer.rows_below_dock);
        boolean shamsi = res.getBoolean(R.bool.shamsi_date);
        boolean moduleCards = res.getBoolean(R.bool.module_cards);
        boolean reducedMotion = res.getBoolean(R.bool.reduced_motion);
        String vpn = res.getString(R.string.vpn_app).trim();
        boolean homeSettings = res.getBoolean(R.bool.show_home_settings_shortcut);
        int background = 0xFF000000;
        String font = res.getString(R.string.font).trim();
        boolean romanDate = res.getBoolean(R.bool.roman_date);
        int accent = color(res.getString(R.string.accent_color), 0);
        boolean laurel = res.getBoolean(R.bool.laurel);
        String style = res.getString(R.string.style).trim();
        String stone = res.getString(R.string.roman_stone).trim();
        int brightness = ranged(res.getInteger(R.integer.roman_stone_brightness), 100, 50, 150);
        int seed = ranged(res.getInteger(R.integer.roman_seed), 7, 0, 9999);
        int vignette = ranged(res.getInteger(R.integer.roman_vignette), 70, 0, 100);
        boolean meander = res.getBoolean(R.bool.roman_meander);
        boolean pilasters = res.getBoolean(R.bool.roman_pilasters);
        boolean crest = res.getBoolean(R.bool.roman_crest);
        String inscription = res.getString(R.string.roman_inscription);
        boolean applied = false;

        File dir = dir(ctx);
        File file = dir != null ? new File(dir, FILE_NAME) : null;
        JSONObject json = read(file);
        if (json != null) {
            applied = true;
            dock = stringList(json, "dock_apps", dock);
            hidden = stringList(json, "hidden_apps", hidden);
            dockPlaceholders = bool(json, "dock_placeholders", dockPlaceholders);
            liveTv = string(json, "live_tv_package", liveTv);
            liveTvAction = string(json, "live_tv_action", liveTvAction);
            panelService = string(json, "source_panel_service", panelService);
            panelAction = string(json, "source_panel_action", panelAction);
            inputAction = string(json, "input_action", inputAction);
            columns = integer(json, "columns", columns, 1, 8);
            dockColumns = integer(json, "dock_columns", dockColumns, 0, 8);
            rowsBelow = integer(json, "rows_below_dock", rowsBelow, 0, 3);
            shamsi = bool(json, "shamsi_date", shamsi);
            moduleCards = bool(json, "module_cards", moduleCards);
            reducedMotion = bool(json, "reduced_motion", reducedMotion);
            vpn = string(json, "vpn_app", vpn);
            homeSettings = bool(json, "show_home_settings_shortcut", homeSettings);
            background = color(json, "background_color", background);
            font = string(json, "font", font);
            romanDate = bool(json, "roman_date", romanDate);
            accent = color(json, "accent_color", accent);
            laurel = bool(json, "laurel", laurel);
            style = string(json, "style", style);
            stone = string(json, "roman_stone", stone);
            brightness = integer(json, "roman_stone_brightness", brightness, 50, 150);
            seed = integer(json, "roman_seed", seed, 0, 9999);
            vignette = integer(json, "roman_vignette", vignette, 0, 100);
            meander = bool(json, "roman_meander", meander);
            pilasters = bool(json, "roman_pilasters", pilasters);
            crest = bool(json, "roman_crest", crest);
            inscription = string(json, "roman_inscription", inscription);
        }
        File wallpaper = dir != null ? new File(dir, WALLPAPER_NAME) : null;
        if (wallpaper != null && !(wallpaper.isFile() && wallpaper.length() > 0)) wallpaper = null;
        // A theme can also switch the wallpaper off while the file is still there.
        if (json != null && json.has("wallpaper") && !bool(json, "wallpaper", true)) wallpaper = null;

        return new Config(dock, hidden, dockPlaceholders, liveTv, liveTvAction, panelService, panelAction, inputAction,
                Math.max(1, columns), dockColumns > 0 ? dockColumns : Math.max(1, columns), Math.max(0, rowsBelow), shamsi, moduleCards, reducedMotion,
                vpn, homeSettings, background, font, romanDate, accent, laurel, wallpaper, applied,
                RomanStyle.of(style, stone, brightness, seed, vignette, meander, pilasters, crest, inscription, accent));
    }

    /** Reads and parses the overlay; any problem means "no overlay", never a crash on the home screen. */
    private static JSONObject read(File file) {
        if (file == null || !file.isFile()) return null;
        if (file.length() > 64 * 1024) {
            Log.w(TAG, "ignoring " + file + ": larger than 64 KB");
            return null;
        }
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            String text = new String(buf, 0, n, StandardCharsets.UTF_8);
            if (text.startsWith("﻿")) text = text.substring(1);   // Windows editors add a BOM
            return new JSONObject(text);
        } catch (Exception e) {
            Log.w(TAG, "ignoring " + file + ": " + e.getMessage());
            return null;
        }
    }

    private static String string(JSONObject o, String key, String fallback) {
        Object v = o.opt(key);
        return v instanceof String ? ((String) v).trim() : fallback;
    }

    private static boolean bool(JSONObject o, String key, boolean fallback) {
        Object v = o.opt(key);
        return v instanceof Boolean ? (Boolean) v : fallback;
    }

    private static int integer(JSONObject o, String key, int fallback, int min, int max) {
        Object v = o.opt(key);
        if (!(v instanceof Number)) return fallback;
        int i = ((Number) v).intValue();
        return i < min || i > max ? fallback : i;
    }

    /** A config.xml number outside its range falls back to the default, like a bad JSON value does. */
    private static int ranged(int v, int fallback, int min, int max) {
        return v < min || v > max ? fallback : v;
    }

    private static int color(JSONObject o, String key, int fallback) {
        Object v = o.opt(key);
        return v instanceof String ? color((String) v, fallback) : fallback;
    }

    private static int color(String text, int fallback) {
        if (text.trim().isEmpty()) return fallback;
        try {
            int c = Color.parseColor(text.trim());
            return c | 0xFF000000;   // always opaque: the window behind is black anyway
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static List<String> stringList(JSONObject o, String key, List<String> fallback) {
        Object v = o.opt(key);
        if (!(v instanceof JSONArray)) return fallback;
        JSONArray a = (JSONArray) v;
        List<String> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            Object item = a.opt(i);
            if (item instanceof String && !((String) item).trim().isEmpty()) out.add(((String) item).trim());
        }
        return out;
    }
}
