# Themes

A theme is a folder with a `config.json` and, optionally, a `wallpaper.jpg`. Pushing one changes the
launcher's dock, layout and look on the TV without rebuilding the APK: the launcher reads
`config.json` as an overlay over its built-in `config.xml` the next time it starts.

```bash
TV_HOST=192.168.1.50 tools/theme.sh themes/aurora          # push a theme and restart Home
TV_HOST=192.168.1.50 tools/theme.sh --reset                # back to the built-in config.xml
powershell -ExecutionPolicy Bypass -File tools\theme.ps1 -TvHost 192.168.1.50 themes\aurora
```

| Theme | What it does |
|---|---|
| `default/` | The built-in look written out as JSON, as a starting point for your own |
| `aurora/` | A dark aurora wallpaper (teal and violet on navy, darker at the bottom) with a matching background colour |
| `six-columns/` | Six smaller tiles per row and one grid row visible under the dock |
| `xgimi-s-max/` | For the XGIMI HORIZON S Max (XK03H): YouTube, Prime Video, HDMI 1 and Files in the dock, five columns, HDMI opened through XGIMI's own player (`com.xgimi.action.hdmiPlayer`), no Live TV, factory and screensaver apps hidden; the Roman look v2 is on (travertine wall, gilded Greek-key frieze with a SALVE medallion, fluted pilasters, Cinzel lettering, Roman date, gold focus glow) |

## Keys

Every key is optional; a missing key keeps the value from `config.xml`, an unknown or wrongly typed key
is ignored, and a file that doesn't parse is ignored as a whole, so a theme can never break Home.

| Key | Type | Meaning |
|---|---|---|
| `dock_apps` | list of strings | The dock row, in order. A package name is an app; missing apps are skipped (see `dock_placeholders`). `input:<inputId>` is a TV input tile (e.g. `input:com.mediatek.tvinput/.hdmi.HDMIInputService/HW5`), `input:hdmi1` is the first HDMI input (`hdmi2`, ... the next ones); it opens like the Inputs menu entry, and an input the TV has not registered is skipped |
| `dock_placeholders` | bool (default `false`) | `true`: a `dock_apps` package that is not installed keeps its slot as a dimmed "Not installed" tile (select it to open its Play Store page, or get a short toast). `false`: it is skipped. A package also in `hidden_apps` is never shown either way |
| `hidden_apps` | list of package names | Never shown as tiles |
| `live_tv_package`, `live_tv_action` | string | The Live TV app and, if it has no leanback entry, the action to start it with |
| `source_panel_service`, `source_panel_action` | string | The TV's own source panel, for sets with no TvInputManager |
| `input_action` | string | Action sent with an HDMI/AV row's channel URI when nothing handles the standard `VIEW` (XGIMI: `com.xgimi.action.hdmiPlayer`) |
| `columns` | 1 to 8 | Tiles per row; tiles shrink to fit from 5 up |
| `rows_below_dock` | 0 to 3 | Grid rows left visible under the dock |
| `shamsi_date` | bool | Show the Solar Hijri date above the Gregorian one |
| `module_cards` | bool | Look for module cards and ask about them (`true`); `false` turns the feature off |
| `reduced_motion` | bool | Calmer motion for weak GPUs: stiff highlight spring, no tile overshoot, short fades, no entrance replay |
| `vpn_app` | string | App the network pill opens when a VPN is configured; also shows a red "VPN off" badge while no VPN is up |
| `show_home_settings_shortcut` | bool | The "Default home" tile, where the TV has a home picker |
| `background_color` | `#RRGGBB` | Colour behind everything; black by default |
| `wallpaper` | bool | Show `wallpaper.jpg` from the theme folder (`true`), or ignore it (`false`) |
| `font` | string | `"serif"` sets the header texts (clock, date, greeting, app name) in the system serif face; anything else keeps Outfit. Nothing is bundled |
| `roman_date` | bool | Date line in Roman numerals, e.g. `XX Octobris MMXXVI`; replaces the Shamsi line when both are on |
| `accent_color` | `#RRGGBB` | Colour of the greeting line and the laurel; unset keeps the default white look |
| `laurel` | bool | A small laurel wreath beside the clock, tinted with `accent_color` |
| `style` | string | `"roman"` turns on the Roman look v2 (Cinzel lettering, gold palette); anything else is off. Under it `font` and `laurel` are ignored and `accent_color` (default `#C9A24B`) is the gold. See `docs/plans/roman-theme-v2.md` |
| `roman_stone` | string | `"travertine"` (default), `"marble"` or `"none"` |
| `roman_stone_brightness` | 50 to 150 | Stone brightness in percent, default 100 |
| `roman_seed` | 0 to 9999 | Pattern of the stone, default 7 |
| `roman_vignette` | 0 to 100 | Edge darkening, default 70 |
| `roman_meander` | bool (default `true`) | Greek-key frieze |
| `roman_pilasters` | bool (default `true`) | Fluted pilasters at the edges |
| `roman_crest` | bool (default `true`) | Laurel medallion in the frieze |
| `roman_inscription` | string | Up to 8 letters inside the medallion, carved style (`U` becomes `V`, `J` becomes `I`; only `A-Z 0-9 space · . -`) |

### Roman look v2

With `"style": "roman"` the home becomes a Roman interior: a procedural stone wall (rendered once on first start,
then cached), a gilded Greek-key frieze under the header broken by a laurel medallion, fluted pilasters at the
edges, Cinzel lettering, a gilded app name, and a gold focus glow with a small lift, gold pills and a dock band.
Everything is switched by the keys above; with `style` unset nothing of it runs. A `wallpaper.jpg` that is
switched on replaces the stone. Details and budgets: `docs/plans/roman-theme-v2.md`.

Projector advice: a projector's black is the colour of the wall, so the stone is mid-dark on purpose. Start with
`roman_stone_brightness` at 100, then try 85 to 115 in the room you watch in. `roman_vignette` (default 70) darkens
the edges; lower it for a flatter wall. Both only need a `config.json` change, no rebuild. The stone is cached by
its settings, so the first start after a change takes a moment longer.

### Roman screensaver

`RomanDream` is a second screensaver beside the clock: the cached stone panning slowly behind a gold laurel, the
time in Cinzel and the Roman date. It needs no keys (it follows `roman_stone`, `roman_seed`, `roman_stone_brightness`,
`roman_vignette` and `accent_color` when the theme is Roman, else a default travertine). To select it:
`settings put secure screensaver_components com.gathin.tvhome/.RomanDream` and
`settings put secure screensaver_enabled 1` over adb (not run automatically). Details: `docs/plans/roman-theme-v2.md`, section 8.

## Wallpapers

`wallpaper.jpg` is drawn full screen behind the launcher, centre-cropped. Keep it dark, 1920×1080 and
under about 300 KB: the text, the hero name and the dock band are designed for black, and a 1 GB TV
decodes the picture into memory on every Home start. Pictures larger than the screen are downsampled.

## Reduced motion

`"reduced_motion": true` keeps the look but calms the movement: the highlight spring is stiff instead
of bouncing, tiles zoom without overshoot, fades are shorter, and Home does not replay the tile
entrance when you come back to it. Use it on TVs whose GPU cannot keep up; a MediaTek MT5895 on
Android 10 is the known case. The default, `false`, keeps the normal motion.

```json
{ "reduced_motion": true }
```

## Adding a theme

Open a PR with a new folder here, a one-line row in the table above and a screenshot from a real TV
in the PR description. Keep the dock list generic (the big streaming apps) unless the theme is for a
specific TV, and say which TV you tried it on.
