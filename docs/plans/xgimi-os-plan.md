# Plan: custom home + debloat for the XGIMI HORIZON S Max

Status: **plan only, waiting for Shibi's review.** No app code has been written and nothing on the
projector was changed. Source brief: `C:\Users\shibi\cc_xgimi\PROMPT.md`.
Branch: `opus/xgimi-os-plan` (work stays on feature branches; never `main`).

Files, Player and voice control are planned in [`next-features.md`](next-features.md); this file stays the device reference.

## 1. What was checked on the device (read-only, 2026-10-20)

All commands were read-only (`getprop`, `pm list packages`, `dumpsys`, `cmd package query-activities`,
`settings get`, `wm size`, `screencap`). Raw package list: `backup/plan-inspect/packages.txt` (gitignored).

| Item | Result |
|---|---|
| Model / product / device | XK03H / `aiolia` / `xgimigalileo`, manufacturer XGIMI |
| OS / build | Android TV 11 (SDK 30), RT.3.24.329 release-keys, patch 2026-05-01 |
| SoC / ABI | `m7632` (MediaTek/MStar), **32-bit only**: `armeabi-v7a,armeabi` |
| RAM | MemTotal 1,737,740 kB (~1.7 GB). Free RAM ~546 MB, MemAvailable ~656 MB with the stock home running. `ro.config.low_ram` unset |
| Java heap | `heapgrowthlimit` 192m, `heapsize` 512m |
| Display | 1920x1080, density 320 (xhdpi; 960x540 dp) |
| Stock home PSS | `com.google.android.tvlauncher` 78.7 MB PSS (148 MB RSS), v7.7.15 |
| Other big residents | gms 88 MB, vending 62+43 MB, katniss 46+44 MB, Play Movies 37 MB, Sling 31 MB, tvrecommendations 26 MB, calendar sync 25 MB, mediashell 25 MB, setupwraith 23 MB |
| HOME claimants | `com.google.android.tvlauncher/.MainActivity` (priority 2) and `com.android.tv.settings/.system.FallbackHome` (priority -1000). Nothing else; setupwraith does **not** claim HOME |
| Disabled packages | none (`pm list packages -d` is empty) |
| Install verification | `verifier_verify_adb_installs` null, `package_verifier_enable` null, `install_non_market_apps` 1, `package_verifier_user_consent` 1. Nothing blocks adb installs. Third-party apps are already sideloaded/installed (10 user apps), so APKs are accepted. **Actual install still needs Shibi's OK.** |
| HDMI player | `com.xgimi.xhplayer/.InputActivity` exists (v60.00.00.53, enabled), answers `com.xgimi.action.hdmiPlayer` with type `vnd.android.cursor.item` (also `android.intent.action.MAIN`, `mstar.tvsetting.ui.intent.action.RootActivity`) |
| HDMI inputs | `com.xgimi.tvinput` has 3 services (Hdmi1/2/3) but **only one is registered**: `com.xgimi.tvinput/.inputservice.Hdmi1TvInputService/HW1` (hardware id 1, hdmi_port 1). No tuner |
| Screenshots | **`screencap` returns 0 bytes** on this unit (exit 0, no error). See risk R1 |

The package set and build match `examples/xgimi-horizon-s-pro/debloat.txt` (same XK03H/xgimigalileo/RT.3.24.329).
Every package in that list exists on the S Max.

## 2. Dock (in order)

| Slot | App | Package / target | Present? |
|---|---|---|---|
| 1 | YouTube | `com.google.android.youtube.tv` (`...ShellActivity`) | yes (system) |
| 2 | Prime Video | `com.amazon.amazonvideo.livingroom` (`com.amazon.ignition.IgnitionActivity`) | yes (**system app**) |
| 3 | Netflix | `com.netflix.ninja` | **NOT INSTALLED.** Not reported as fixed here; Shibi installs it from the Play Store if it is offered for this device. The launcher already skips missing dock apps, so the slot fills in once it is installed |
| 4 | HDMI input | input `com.xgimi.tvinput/.inputservice.Hdmi1TvInputService/HW1` | yes |

### HDMI intent (verified against the package manager, not yet fired)

```
action:  com.xgimi.action.hdmiPlayer
data:    content://android.media.tv/passthrough/com.xgimi.tvinput%2F.inputservice.Hdmi1TvInputService%2FHW1
         (= TvContract.buildChannelUriForPassthroughInput(inputId))
type:    vnd.android.cursor.item/channel
target:  resolves to com.xgimi.xhplayer/.InputActivity
flags:   FLAG_ACTIVITY_NEW_TASK
```

This is exactly what the launcher's existing `openInput()` already sends after the standard `ACTION_VIEW`
fails (`MainActivity.java:835`), with `input_action` set to `com.xgimi.action.hdmiPlayer`. The S Pro notes
confirm the standard `ACTION_VIEW` does nothing on this firmware. Equivalent manual test (a device action,
needs Shibi's OK): `am start -a com.xgimi.action.hdmiPlayer -d <uri above> -t vnd.android.cursor.item/channel`.

## 3. Architecture decision: extend the Java launcher (option b)

**Decision: extend `launcher/` (Java + Views, `com.gathin.tvhome`) in place. No Compose module.**

Why, from what was measured:

1. **32-bit ARMv7 with ~650 MB available and a 192 MB heap limit.** Compose for TV brings the Compose
   runtime, UI, foundation, `tv-material` and the Kotlin stdlib. On a 32-bit ART with a slow MStar CPU
   that means a larger dex (typically +3 to 6 MB of APK after R8), more JIT/AOT work, and a noticeably
   slower cold start. A home screen cold-starts often on this device: Android's low-memory killer reclaims
   it whenever YouTube or Prime Video takes the memory, and every Home press after that is a cold start.
   The current Java launcher has two small AndroidX dependencies (RecyclerView, dynamicanimation) and R8 on.
2. **It is already proven on this exact model and build.** The S Pro notes record install, HOME switch,
   HDMI via `input_action`, Projector Settings reachability and a reboot, all on XK03H/RT.3.24.329
   (2026-10-19). Compose would discard that verification.
3. **Almost everything the brief asks for already exists**: black background, dock, 4-column grid,
   glass highlight, pills (Inputs, Wi-Fi, Settings), greeting/clock/date, deterministic D-pad and
   remembered focus, config.xml + `config.json` themes, `input_action`, `live_tv_package`, clock
   screensaver, restore scripts. The gaps are small and additive: an HDMI tile *in the dock*, and the
   subtle Roman look. Each fits in a small PR.
4. **Config, theme and card-spec compatibility is free**: same app, same keys. A Compose rewrite would
   have to re-implement `Config`, `Cards`/`CardFeature` (docs/specs/card.md) and 12 locales, then keep two
   launchers in sync.
5. **Upstream stays mergeable.** Changes stay in the same files and style as CleanYourTV.

What would change the decision: if the Java launcher, once installed, uses more than about 60 MB PSS at
idle or stutters on D-pad moves on this unit, re-evaluate. The number is recorded in step S8. Kotlin/Compose
remains an option for a later, separate module once the base is stable; it is not needed for this scope.

APK size: no APK has been built yet (Gradle runs outside Claude). Step S1 records the release APK size
as the baseline; each later PR reports the delta. Budget: the release APK stays under 2 MB and each
step adds no more than 150 KB.

Stays within minSdk 26 / targetSdk 34 (device is SDK 30). The launcher's manifest already handles the
Android 11 `<queries>` rule for `LEANBACK_LAUNCHER`; S3 re-checks it.

## 4. S Max debloat list

Format and tooling are the repo's: `examples/xgimi-horizon-s-max/debloat.txt`, applied with
`tools/disable.ps1` (`pm disable-user --user 0`), undone with `tools/restore.ps1`. Nothing is uninstalled.

**Main difference from the S Pro list: Prime Video (`com.amazon.amazonvideo.livingroom`) is in the dock,
so it is NOT disabled here.** The S Pro list disables it.

### Batches (one batch per Shibi OK, screenshot/check between batches)

**Batch 1: factory, demo and test tools** (lowest risk)
| Package | Reason |
|---|---|
| `fusion.android.tv.demo` | RetailDisplay store demo |
| `com.xgimi.burnin` | factory burn-in test, runs on boot |
| `com.xgimi.atvwifiverify` | factory Wi-Fi verification |
| `com.xgimi.minitvfactory` | factory menu, file transfer and burn-in services |

**Batch 2: telemetry and auto-installs**
| Package | Reason |
|---|---|
| `com.xgimi.bugreportsender` | XGIMI bug report uploader |
| `com.google.android.feedback` | Google feedback |
| `android.autoinstalls.config.xgimi.xgimigalileo` | Play auto-install config that re-pushes preloads |

**Batch 3: Google content rows and preloads** (frees ~60 MB resident)
| Package | Reason |
|---|---|
| `com.google.android.tvrecommendations` | recommendation rows for the stock home; 26 MB resident |
| `com.google.android.videos` | Play Movies; 37 MB resident, registers TV inputs |
| `com.google.android.play.games` | Play Games |
| `com.google.android.youtube.tvmusic` | YouTube Music (YouTube itself stays) |
| `com.ted.android.tv` | TED |

**Batch 4: XGIMI extras and screensaver**
| Package | Reason |
|---|---|
| `com.xgimi.filemanager` | file manager, DLNA/Samba servers |
| `com.xgimi.gimiplayer` | local video/photo player |
| `com.xgimi.doubanfm` | XGIMI music app / speaker-mode screen |
| `com.xgimi.atmosphere` | animated screensaver/ambient scenes; 13 MB resident |
| `com.google.android.backdrop` | Backdrop screensaver. **Before this batch**, point the screensaver at the launcher's ClockDream or `com.android.dreams.basic/.Colors` (a `settings put`, needs OK) |

**Batch 5: stock launcher** (done in the device phase right after the new home is verified, not with the debloat)
| Package | Reason |
|---|---|
| `com.google.android.tvlauncher` | stock home, 79 MB resident. Disabled, never uninstalled. Only after `set-home-activity` to `com.gathin.tvhome/.MainActivity` is confirmed |

**Batch 6: optional, Shibi decides each line** (off by choice on the S Pro; each removes a feature)
| Package | What is lost |
|---|---|
| `com.google.android.katniss` | Google Assistant / voice search on the remote mic button (~90 MB resident across two processes) |
| `com.google.android.apps.mediashell` | Chromecast built-in (casting from a phone). Cast ports 8008/8009/8443 are open, so it is in use or ready |
| `com.google.android.tv.remote.service` | Google TV phone remote app |
| `com.google.android.marvin.talkback` | TalkBack screen reader |
| `com.google.android.syncadapters.calendar` | calendar sync (25 MB resident, no use on a projector). Not on the S Pro list; low risk |

User-installed apps (Crunchyroll, Spotify, Duo/`tachyon`, Hulu, MX Player, Twitch, VLC, Sling, Willow,
Xumo) are Shibi's own installs and are **not** in the debloat list. Sling (31 MB resident) and Xumo
register background TV inputs; Shibi can decide about them separately.

### NEVER disable (S Max)

Projector hardware, input and remote:
`com.xgimi.autokst`, `com.xgimi.windowsystem`, `com.xgimi.tof`, `com.xgimi.tvinput`, `com.xgimi.xhplayer`,
`com.xgimi.bluetoothservice`, `com.xgimi.misckey`, `com.xgimi.uiconfiguration`, `com.xgimi.xrmservice`,
`com.xgimi.soundermodeservice`, `com.xgimi.xgimiservice`, `com.xgimi.simpservice`,
`com.xgimi.overseas.xgimiatvbootwizard`.

New on the S Max list (not covered by the S Pro notes):
- `com.xgimi.ota.accessories`: remote-control firmware updater (`UpdateRemoteControlActivity`). Remote related: keep.
- `com.xgimi.upgrade`: system OTA (`UpgradeService`, `OTADownloadService`). Keep so security updates still arrive.
- `com.mediatek.wwtv.webview`: vendor WebView. Keep.

Android/Google core: `com.android.vending` (Play Store), `com.google.android.gms`, `com.google.android.gsf`,
`com.google.android.inputmethod.latin` (keyboard), `com.android.bluetooth`, `com.android.tv.settings`
(Settings, Projector Settings, FallbackHome), `com.android.systemui`, `com.android.providers.tv`
(TV input database for HDMI), `com.google.android.tungsten.setupwraith`, `com.google.android.webview`,
`com.google.android.tts`, `com.android.dreams.basic`, every `com.android.providers.*`, every
`*.auto_generated_rro_*` overlay, and the dock apps `com.google.android.youtube.tv` and
`com.amazon.amazonvideo.livingroom`.

## 5. Subtle Roman design (inside the low-RAM rules)

> Superseded by `docs/plans/roman-theme-v2.md` (Roman look v2, behind `"style": "roman"`). The S5 keys below stay.

No bundled fonts, no bitmaps beyond the optional wallpaper, no blur, no animation beyond what exists.
- **Type**: the system serif (`fontFamily="serif"`, Noto Serif, already on the device) for the clock,
  greeting and date. Clock stays large and thin.
- **Roman numerals**: one small dim line under the date, e.g. `XX · X · MMXXVI`. The clock itself stays
  Arabic so it is readable from across the room.
- **Accent**: one muted gold (`#B8975A`-ish) used only for the focus glow edge and a hairline under the
  greeting. Everything else stays black/white/grey.
- **Laurel**: a small single-colour VectorDrawable (two laurel branches) beside the greeting, under 2 KB.
- **No marble texture** (a full-screen bitmap costs ~8 MB decoded). If Shibi wants marble later, it goes
  through the existing optional `wallpaper.jpg` theme path with the < 300 KB rule.
- All of it switched by theme keys (`font`, `roman_date`, `accent_color`, `laurel`), default off, so the
  upstream look is unchanged and other TVs are unaffected.

Note (S5 as built): the Roman look deviates from the above. The accent tints the greeting text (not the
focus-glow edge plus a hairline), the laurel sits beside the clock (not the greeting), the theme gold is
`#C9A24B` (not `#B8975A`), and the date line reads `XX Octobris MMXXVI` (Latin month, not `XX · X · MMXXVI`).
To be tuned with Shibi.

## 6. Steps (each a small PR from its own feature branch; first line of every PR body: `Do not merge without Shibi's OK.`)

| Step | Who | What | Device? |
|---|---|---|---|
| **S0** | Opus | This plan (`docs/plans/xgimi-os-plan.md`). | no |
| **S1** | Sonnet + Gradle outside | Baseline only: build the current launcher (`assembleRelease testReleaseUnitTest lint`), record APK size and test results in `logs/S1.txt`. No code change. | no |
| **S2** | Sonnet | `examples/xgimi-horizon-s-max/debloat.txt` (batches 1-4 + 6 commented out with reasons, NEVER-disable header), rows in `examples/README.md` and `README.md` tables, `python -m unittest discover -s tests` green. | no |
| **S3** | Sonnet | `themes/xgimi-s-max/config.json`: `dock_apps` [YouTube, Prime Video, Netflix], `input_action: com.xgimi.action.hdmiPlayer`, `live_tv_package: ""`, `hidden_apps` for system leftovers; table row in `themes/README.md`. Re-check manifest `<queries>` for LEANBACK_LAUNCHER. Works with today's APK. | no |
| **S4** | Sonnet | Launcher: HDMI entries in the dock. A `dock_apps` item of the form `input:<inputId>` (or `input:hdmi1`, the first HDMI input) becomes a dock tile with the input icon and label "HDMI 1", focusable like any app tile, opening through the existing `openInput()`. Missing/unregistered input = slot skipped, like a missing app. JUnit tests for the parsing; `themes/README.md` key docs. Theme from S3 gets `"input:hdmi1"` as slot 4. Version bump. | no |
| **S5** | Sonnet | Roman look: `font`, `roman_date`, `accent_color`, `laurel` keys (config.xml + config.json), laurel VectorDrawable, Roman numeral formatter with JUnit tests (1-3999, date line). Defaults keep the current look. | no |
| **S6** | Sonnet | `tools/backup.ps1` / `.sh`: save `pm list packages`, `pm list packages -d`, `getprop`, `settings list global/secure/system`, and the current HOME resolve to `backup/<date>/` on the PC. Read-only on the device. | no |
| **S7** | Opus | Review S1-S6 against PROMPT.md and this plan; Sonnet applies fixes. | no |
| **S8** | Device phase, **Shibi OK per line** | (a) run S6 backup; (b) install the APK and set it as Home **without** disabling the stock launcher (`build-and-install.ps1 -StockLauncher none`); verify Home, dock, HDMI, Settings → Projector Settings; record launcher PSS (`dumpsys meminfo com.gathin.tvhome`); (c) push the S Max theme; (d) disable `com.google.android.tvlauncher`, reboot, confirm Home survives; (e) debloat batches 1, 2, 3, 4, then 6 lines Shibi chose, checking after each. Stop at the first problem and restore that batch. | **yes** |
| **S9** | Sonnet | README notes for the S Max (build, install, restore), final cost report. | no |

## 7. Risks

- **R1: no screenshots.** `screencap` returns an empty image on this unit, so "inject keys, take a
  screenshot, fix what you see" does not work over adb. Options: Shibi photographs the wall, or check
  state with `dumpsys activity activities` (resumed activity), `dumpsys window` (focus) and `uiautomator dump`
  (which printed nothing to stdout here; it may need a file on `/sdcard`, which is a device write needing OK).
  Decide before S8.
- **R2: `build-and-install.ps1` does several writes at once** (install, set-home, grant location,
  `compile -m speed`, disable the stock launcher, accessibility settings). For per-batch OK, S8 runs it with
  `-StockLauncher none` first and disables the stock launcher as a separate step. Shibi should know it also
  grants `ACCESS_FINE_LOCATION` (used for the Wi-Fi name in the pill).
- **R3: `tools/tv.py` uses its own adb key** (`tools/adbkey/`), not the Platform-Tools key already
  authorized. The first run will show an authorization prompt on the projector that someone must accept.
- **R4: Netflix is missing.** If the Play Store does not offer it for this device, slot 3 stays empty
  (skipped) and the dock shows three items. Sideloading Netflix is out of scope.
- **R5: only HDMI1 is registered** although tvinput has three services. If the projector has a second
  physical HDMI port, it may only register when something is plugged in; `input:hdmi1` handles the
  registered one and the Inputs pill still lists whatever appears.
- **R6: Prime Video differs from the S Pro list.** Anyone reusing the S Pro list on this unit would
  disable a dock app; the S Max list comments this explicitly.
- **R7: disabling the stock launcher on Android TV 11** can leave `FallbackHome` as Home if the new
  launcher crashes. Recovery: `pm enable com.google.android.tvlauncher` over adb (the restore script's
  first line). Keep adb on the network enabled throughout.
- **R8: low memory.** With the stock home, Assistant and Play Movies gone, about 200 MB more should be
  free; the gain is measured in S8, not assumed.

## 8. Update 2026-10-20: Netflix

Netflix is a **top goal**. Shibi approved a direct install of `com.netflix.ninja`; a separate task handles that install (not part of S1-S7).
In the launcher, the Netflix dock tile must stay visible and show **"Not installed"** gracefully (dimmed, focusable, no crash) when the package is missing, instead of silently skipping the slot. Implement this in S4 alongside the HDMI dock tiles, with JUnit coverage. This supersedes R4 and the "skip missing dock apps" note for Netflix.

## 9. Update 2026-10-20 (after first look on the projector)

Shibi asked to remove Netflix from the S Max dock entirely. The S Max dock is now YouTube, Prime Video, HDMI 1, and dock_placeholders is off for this theme (no 'Not installed' tile). This supersedes section 8. The placeholder feature stays in the launcher as an opt-in key, off by default.
