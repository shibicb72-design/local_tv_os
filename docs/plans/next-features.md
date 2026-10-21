# Plan: Files, Player, Voice control and the Atmosphere report (XGIMI HORIZON S Max)

Status: **plan only.** No app code, no Gradle run, nothing done on the projector. Source brief:
`C:\Users\shibi\cc_xgimi\NEXT_PROMPT.md` (approved by Shibi), plus the operator's constraints for this step.
Base: branch `feat/plan`, which sits on the Roman theme v2 branch (PR #2, not merged yet). Read with
`docs/plans/xgimi-os-plan.md` (device facts, never-disable list) and `docs/plans/roman-theme-v2.md` (palette,
Cinzel, budgets, rules for slices).

Words used below:
- **Operator**: the person who runs Gradle and every device command outside Claude. Every adb command is
  logged with a timestamp in `C:\Users\shibi\cc_xgimi\adb_log.txt`.
- **Read-only**: the command reads state and changes nothing on the projector (`getprop`, `dumpsys`, `pm path`,
  `cmd package query-*`, `cat` of system files, `adb pull`).
- **Needs OK**: a device write. It runs only after Shibi's OK for that line (install, `appops set`, a theme push,
  a key or `am start` that changes what is on screen).
- `$adb` = `"$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"`, `$d` = `10.0.0.242:5555`; every device
  command below is `& $adb -s $d shell <command>` unless written out.

---

## 1. Decisions

| # | Decision | Why |
|---|---|---|
| D1 | **New apps are Gradle modules of `launcher/`**: `:romancore` (Android library), `:files` (`com.gathin.files`), `:player` (`com.gathin.player`), next to `:app` (`com.gathin.tvhome`). One `gradlew testReleaseUnitTest assembleRelease assembleDebug` in `launcher/` builds and tests everything | Operator constraint; one CI job, one Gradle wrapper, one AGP version (8.5.2), one place for the Roman code |
| D2 | **Shared Roman code moves to `:romancore` first (F1, F2), behaviour-preserving**, in package `com.gathin.roman` with public API. The launcher keeps everything that is about the home screen | "Roman style everywhere, shared code, not copies". Doing it first means the launcher's tests prove the move before any new app depends on it |
| D3 | **Java + Views, no Kotlin, no Compose** in all modules | Same reasons as `xgimi-os-plan.md` section 3 (32-bit ART, ~650 MB free, cold starts). Keeps one code style |
| D4 | **Files and Player use `minSdk 30`** (the device is SDK 30); the launcher stays at 26 | Removes every pre-Android-11 storage branch (`MANAGE_EXTERNAL_STORAGE`, `StorageVolume.getDirectory()`, `registerStorageVolumeCallback`, `ImageDecoder`). These two apps are written for this projector; lowering it later is a separate decision |
| D5 | **Storage access: `MANAGE_EXTERNAL_STORAGE` ("All files access") plus plain `java.io.File`**, for Files and Player. Granted by the operator with `appops set` (needs OK) when the TV has no settings screen for it. Not SAF | SAF on Android TV often has no `DocumentsUI` (Op-F0 checks), needs a tree pick per USB stick, and lists through `ContentResolver` one query per folder: slow on a 2,000-file stick on this CPU. `java.io.File` on the FUSE mount is fast, works on removable volumes with All files access, and is unit-testable on the JVM. Scoped `READ_EXTERNAL_STORAGE` cannot write to USB at targetSdk 34 |
| D6 | **Files hands media to Player through its own read-only `ContentProvider` (`content://com.gathin.files.stream/...`) plus an extra with the absolute path.** No `androidx.core` `FileProvider`, no `file://` | `file://` in an intent throws `FileUriExposedException` at targetSdk ≥ 24; `FileProvider` cannot root on `/storage/XXXX-XXXX` without a `root-path` hack and pulls in `androidx.core`. A 120-line provider with a tested path check is smaller. Third-party apps (system chooser) get the content URI; Player prefers the path, so it can find sidecar subtitles and the folder playlist itself |
| D7 | **Player engine: Media3 ExoPlayer (`media3-exoplayer` only, no `media3-ui`), pinned to the last Media3 release that builds with `compileSdk 34`** (expected 1.4.1; P1 confirms and records it) | Requirement 4.2 makes **embedded** subtitles mandatory where the container allows. Framework `MediaPlayer` exposes no subtitle tracks inside MKV (the usual container for subtitled films) and only `addTimedTextSource` for external SRT; track selection in MKV/TS depends on the vendor. ExoPlayer reads SRT, SSA/ASS (as plain text), WebVTT, PGS and VobSub from MKV, tx3g/WebVTT from MP4, DVB/CEA-608 from TS, merges sidecar SRT, and has real audio/text track selection. It decodes with the same hardware `MediaCodec`s, so codec coverage equals the SoC's. Cost: APK about 1.0 to 1.5 MB after R8 (budget D9) and a Java-heap buffer we cap (P1). `MediaPlayer` stays as the fallback path: any file ExoPlayer cannot play gets "Open in another player" (the XGIMI player is still installed) |
| D8 | **No stone texture in Files or Player.** Background is stone ink `#1A1511`; Roman look = Cinzel titles, gold rules, gold focus rows and pills from `:romancore` | The stone costs 1 to 2 MB and a first-start render per process. Files must open fast; Player shows video. The palette and type carry the look |
| D9 | **Budgets enforced by CI** (release APK, after R8): app < 400 KB (unchanged), files < 300 KB, player < 2 MB | Section 2 |
| D10 | **Voice: free Home Assistant `google_assistant` (manual setup), HA in Docker on the Alienware, exposed by Tailscale Funnel** through a Caddy path allowlist. Cloudflare Tunnel is the alternative only if Shibi already owns a domain | Section 5.2 compares the three free options |
| D11 | **HA drives the projector with the `androidtv` (ADB) integration with polling switched off**, using the **already authorised** Platform-Tools key of the Alienware (mounted read-only). Google sees one `media_player` named **TV** (`universal` media player whose state lives in HA helpers) | No new authorisation prompt on the projector; no 10-second `dumpsys` polling on the slow SoC ("no network polling"); one Google device with OnOff, Volume and InputSelector traits |
| D12 | **Display off/on uses XGIMI's own mechanism** found by the read-only investigation Op-V0. No black activity, no POWER/SLEEP keys, no sleep/power/standby settings | Brief 4.3. The mechanism is wired in V5 only after the findings are written down |
| D13 | **All device investigation scripts live on the A branch** (`tools/inspect/`, step A1) with one shared logger and a read-only guard, and the operator runs them in one sitting before F, P and V need the answers | Atmosphere is an investigation feature; one guarded logger for every investigation is safer than four copies |
| D14 | **S Max dock becomes YouTube, Prime Video, HDMI 1, Files, Player with `columns: 5`** (theme JSON only, no launcher change) | The dock is the first `columns` slots of the grid (`MainActivity`, `position < COLUMNS`). Five dock items need five columns; tiles get smaller (R4 already checked pilaster clearance at columns 5). Alternative for Shibi: keep 4 columns and drop HDMI 1 from the dock (still on the Inputs pill). **Open question Q1** |
| D15 | **Files and Player always use the default Roman style** (`RomanStyle.defaults()`: travertine values, gold `#C9A24B`), they do not read the launcher's `config.json` | On Android 11 another app cannot read `/sdcard/Android/data/com.gathin.tvhome/`. A shared-config `ContentProvider` in the launcher is possible later; not needed for one projector |

### 1.1 What moves to `:romancore` and what stays

| Class / resource | Goes to | Notes |
|---|---|---|
| `Roman`, `RomanStyle`, `StoneTexture`, `Laurel`, `Meander` (pure Java) | `:romancore`, `com.gathin.roman` | Made `public`; members the app uses become `public`. No logic change |
| `RomanLook` minus `applyHeader` | `:romancore` | `typeface`, `text`, `gild`, `pill`, `iconTile` are generic |
| `RomanLook.applyHeader` | stays in `:app` as `RomanHeader.applyHeader` | It uses the launcher's `R.id`s |
| `StoneCache` | `:romancore` | Generic (Context + style). Files/Player do not call it (D8), but the launcher and `RomanDream` do, and a future app may |
| `res/font/cinzel_roman.ttf` | `:romancore` `src/main/res/font/` | Referenced as `com.gathin.roman.R.font.cinzel_roman` inside the library only (`nonTransitiveRClass=true`) |
| `FONT-LICENSE-CINZEL-OFL.txt` | stays at `launcher/` | Path unchanged; README credit unchanged |
| `RomanFrameView`, `Pilaster`, `GlassView`, `FocusGeometry`, `DockDecoration`, `RomanDream`, `RomanDreamMath`, `Config`, `AppAdapter`, `MainActivity` | stay in `:app` | Home-screen specific. `RomanDream` stays because its manifest entry and `RomanDreamMathTest` are the launcher's |
| New: `RomanRows`, `RomanDialog`, `RomanStyle.defaults()` | `:romancore` (F4) | List rows and confirm dialogs for Files and Player |

---

## 2. Budgets

### 2.1 APK (release, after R8, signed with the debug key like the launcher)

| APK | CI hard limit | Target | Stop and report at | Main content |
|---|---|---|---|---|
| `app` (launcher) | **409,600 B (400 KB)**, unchanged | today's size ± 2 KB after F1/F2 | any growth > 2 KB from the extraction | unchanged code + `:romancore` |
| `files` | **307,200 B (300 KB)** | ≤ 200 KB | 250 KB | RecyclerView (as the launcher), Cinzel 21 KB, ~40 small classes, vector banner |
| `player` | **2,097,152 B (2 MB)** | ≤ 1.5 MB | 1.8 MB | Media3 ExoPlayer + 9 extractors (custom `ExtractorsFactory`, no `DefaultExtractorsFactory`), Guava as shrunk by R8, Cinzel |

No raster assets in Files or Player: banners and icons are `VectorDrawable`s (≤ 3 KB each). Cinzel is
duplicated in each APK (21 KB, accepted; there is no shared-APK resource on Android).

### 2.2 Memory (PSS, `dumpsys meminfo <pkg>`, measured by the operator; ~650 MB available on the device)

| App | State | Target | Hard ceiling | Levers if over |
|---|---|---|---|---|
| Launcher | idle on Home | < 60 MB (51.8 MB measured) | 60 MB | unchanged by this plan; F2 re-measures once |
| Files | browsing a 2,000-entry folder | ≤ 35 MB | 45 MB | no thumbnails, `RecyclerView` view recycling, entries as small value objects (name, size, mtime, kind) |
| Files | copying 4 GB across volumes | ≤ 40 MB | 50 MB | one 256 KB buffer, one worker thread |
| Player | browse/library screen | ≤ 35 MB | 45 MB | no thumbnails |
| Player | 1080p H.264/HEVC with subtitles | ≤ 90 MB | 110 MB | load control: `setTargetBufferBytes(32 MB)`, buffer 15 s min / 30 s max, `setPrioritizeTimeOverSizeThresholds(false)` (ExoPlayer's default video target is far larger) |
| Player | 4K HEVC, if the SoC decodes it (Op-P0) | ≤ 100 MB | 120 MB | target bytes 32 MB is ~3.5 s at 70 Mbit/s, enough for USB |
| Player | photo slideshow | ≤ 50 MB | 60 MB | `ImageDecoder` with target size ≤ 1920x1080 and `MEMORY_POLICY_LOW_RAM` (RGB_565), at most 2 decoded images (current + next) |

Rules for every app (from `roman-theme-v2.md` section 2): no continuous animation, no `Handler` loops except
the player's 1 Hz progress tick **while the overlay is visible** and the slideshow timer, no network,
no full-screen layers, no `int[w*h]` buffers.

### 2.3 Home Assistant on the Alienware

Docker containers: `homeassistant` (~250 to 400 MB RAM), `caddy` (~20 MB), `tailscale` (~30 MB). No cost on the projector
except one adb TCP connection and a few `am`/`cmd` calls per voice command.

---

## 3. Order of work, branches and PRs

| Order | Branch | PR into `main` | Content | Depends on |
|---|---|---|---|---|
| 1 | `feat/atmosphere-report` | **A** | A1, A2 (scripts + template) merge-ready early; A3, A4 (report) after the operator run | nothing |
| 2 | (operator) | | Op-A, Op-F0, Op-P0, Op-V0 in one sitting with the A1 scripts | A1 |
| 3 | `feat/files` | **F** | F1 to F11 | PR #2 (Roman v2) merged, or F stacked on it |
| 4 | `feat/player` | **P** | P1 to P9 | F (romancore, `StreamProvider` contract). Stacked PR with base `feat/files` until F merges, then retargeted to `main` |
| 5 | `feat/voice` | **V** | V1 to V6 | Op-V0 findings (V5); F and P installed for the "open Files/Player" commands (V6 test only) |

Every PR body starts with `Do not merge without Shibi's OK.` CI must be green. One commit per step, message
`F3: <what> (Sonnet)` / `(Haiku)` / `(Opus)`. Never push to `main`.

**Recipe per step** (from the brief): a fresh session `claude -p --model sonnet --max-budget-usd 1.50 --max-turns 30`
(or `--model haiku` where marked **Haiku**) gets: "Read `docs/plans/next-features.md` sections 1-3 and step Xn,
then only the files that step lists." Gradle runs outside Claude (`gradlew testReleaseUnitTest assembleRelease
assembleDebug` in `launcher/`); on failure Haiku summarises the log, Sonnet fixes it, at most 2 retries, then
Opus. **Opus reviews** the diff at the gates marked **[Opus gate]**.

Rules for every code step (same as `roman-theme-v2.md` section 4): read the plan, then only the listed files;
Java + Views; logic with a test lives in a pure-Java class (no `android.*` import; JUnit 4; `android.*` stubs throw
in JVM tests); no new dependency unless the step names it; no device commands; report files changed, tests added,
expected APK delta, anything not done.

---

## 4. Feature F: File Manager (`com.gathin.files`, branch `feat/files`)

### 4.1 Architecture

```
FilesActivity ─── RootsScreen (USB volumes first, then Internal storage)
     │              └ Volumes (StorageManager, mount callback) ── VolumeModel (pure: order, labels)
     ├─ ListScreen (RecyclerView) ── DirLister (pure, java.io) ── FileSort / NaturalOrder (pure)
     │      └ FocusMemory (pure: path → focused position, scroll offset)
     ├─ ActionMenu (MENU key or long OK) ── RomanDialog (romancore) ── FileOps (pure, worker thread)
     │                                                                 └ ConflictNamer, OpPlan (pure)
     └─ Opener ── MimeKinds (pure) ── StreamProvider (content://com.gathin.files.stream/<enc path>)
                    └ UriPaths (pure: encode/decode, root containment)
            video/audio/image → explicit intent to com.gathin.player (if installed) else system chooser
            anything else     → system chooser
```

Manifest (`files`): `MANAGE_EXTERNAL_STORAGE`; `FilesActivity` with `MAIN` + `LEANBACK_LAUNCHER`, `android:banner`
(vector), `launchMode="singleTask"`, `configChanges` as the launcher; `StreamProvider`
`exported="false" grantUriPermissions="true"`; `<queries>`: `<package android:name="com.gathin.player"/>` and
`VIEW` intents for `video/*`, `audio/*`, `image/*`, `*/*`.

Behaviour summary: D-pad first; OK opens; BACK goes up (at a root: back to roots, then exit); remembered focus
per folder; sort pill (Name, Date, Size; folders first; natural order so `Ep 2` < `Ep 10`); hidden dot-files
skipped; delete/rename/copy/move with confirmation (Cancel focused by default on destructive dialogs);
read-only volumes (NTFS mounted read-only, Op-F0) show the operations disabled with a reason; unmount of the
current volume returns to the roots with a toast.

### 4.2 Steps

**F1. `:romancore` library, pure classes** · Sonnet
- Files: `launcher/settings.gradle` (`include ':app', ':romancore'`); `launcher/build.gradle` (add
  `id 'com.android.library' version '8.5.2' apply false`); new `launcher/romancore/build.gradle` (`com.android.library`,
  `namespace 'com.gathin.roman'`, compileSdk 34, minSdk 26, Java 17, `testImplementation 'junit:junit:4.13.2'`,
  no other dependency); new `launcher/romancore/src/main/AndroidManifest.xml` (empty `<manifest/>`).
- Move with `git mv` into `launcher/romancore/src/main/java/com/gathin/roman/`: `Roman`, `RomanStyle`,
  `StoneTexture`, `Laurel`, `Meander`. Change only `package`, `public` on classes and on members `:app` uses.
- Move their tests (`RomanTest`, `RomanStyleTest`, `StoneTextureTest`, `LaurelTest`, `MeanderTest`) to
  `launcher/romancore/src/test/java/com/gathin/roman/`. Fix `StoneTextureTest`'s source path
  (`src/main/java/com/gathin/roman/StoneTexture.java`, fallback `romancore/src/...`).
- `:app`: `implementation project(':romancore')`; add `import com.gathin.roman.*` where needed (Config,
  MainActivity, AppAdapter, GlassView, DockDecoration, RomanFrameView, RomanDream, RomanLook, StoneCache, Pilaster).
- Tests: no new tests; every moved test must still pass unchanged except package/path lines.
- Reviewer checks: `git diff -M` shows renames with only package/visibility/import changes; no logic line changed;
  `testReleaseUnitTest` runs tests in both modules (the operator pastes the counts: same total as before).
- Device: none.

**F2. `:romancore` Android pieces and the font** · Sonnet · **[Opus gate: extraction]**
- Move `RomanLook` (without `applyHeader`) and `StoneCache` to `com.gathin.roman`; `RomanLook.typeface` uses
  `com.gathin.roman.R.font.cinzel_roman`. Move `res/font/cinzel_roman.ttf` to `launcher/romancore/src/main/res/font/`.
- New `launcher/app/src/main/java/com/gathin/tvhome/RomanHeader.java` with the old `applyHeader` body,
  unchanged; `MainActivity` calls it.
- `launcher/app/build.gradle`: versionCode 7, versionName `2.6` (so an installed build is identifiable).
- `logs/F2.txt` (operator fills): release APK size before/after, test counts.
- Tests: existing launcher tests green (`RomanDreamMathTest` still finds the manifest).
- Budget: APK delta ≤ +2 KB (library code is merged and shrunk by the app's R8; `-dontobfuscate` stays in `:app`).
- Reviewer checks: no behaviour change (diff is moves + `RomanHeader` cut/paste); the font is in exactly one
  module; `RomanStyle` has still one palette; `StoneCache` cache file name/format unchanged (`stone-<key>.565`,
  so the existing cache on the device is reused, not re-rendered).
- Device (needs OK, can wait until F11's install): install the launcher release APK, Home looks identical,
  `dumpsys meminfo com.gathin.tvhome` < 60 MB.

**F3. `:files` module skeleton + CI for all modules** · Sonnet
- New `launcher/files/build.gradle` (`com.android.application`, `namespace`/`applicationId 'com.gathin.files'`,
  compileSdk 34, minSdk 30, targetSdk 34, versionCode 1, `'1.0'`, release `minifyEnabled true`,
  `shrinkResources true`, `signingConfig signingConfigs.debug`, `proguard-rules.pro` with `-dontobfuscate`;
  deps: `project(':romancore')`, `androidx.recyclerview:recyclerview:1.3.2`, junit).
- `AndroidManifest.xml` as 4.1 (provider entry added in F8), `res/drawable/banner.xml` (vector, Cinzel-free:
  a gold laurel from paths + stone-ink background, 320x180 dp viewport), `res/values/strings.xml`
  (`app_name` "Files"), theme `Theme.Files` (`@android:style/Theme.DeviceDefault.NoActionBar`, window background
  stone ink).
- `FilesActivity.java`: a title "FILES" in Cinzel gold and an empty list; nothing else yet.
- `settings.gradle`: `include ':files'`.
- `.github/workflows/ci.yml`: replace the two Gradle steps with one
  `./gradlew --no-daemon testReleaseUnitTest assembleRelease assembleDebug`; replace the size step with the
  budget step below; keep artifact `launcher-debug-apk`; add `files-debug-apk`
  (`launcher/files/build/outputs/apk/debug/files-debug.apk`).
  ```yaml
      - name: APK size budgets
        run: |
          fail=0
          check() { f="launcher/$1/build/outputs/apk/release/$1-release.apk"; s=$(stat -c %s "$f") || { echo "::error::$f missing"; fail=1; return; }
                    echo "$1: $((s / 1024)) KB (limit $(($2 / 1024)) KB)"
                    [ "$s" -lt "$2" ] || { echo "::error::$1 release APK over budget"; fail=1; }; }
          check app 409600     # the launcher targets 1 GB TVs, keep it small
          check files 307200
          exit $fail
  ```
- Tests: one trivial JVM test in `:files` so the task exists (`FilesSmokeTest`, asserts the package constant).
- Reviewer checks: the launcher's 400 KB message/limit is unchanged; a missing APK fails CI; artifacts named per app.
- Device: none.

**F4. Roman list kit in `:romancore`** · Sonnet
- `RomanStyle.defaults()` (pure): the style Files/Player use (stone travertine, brightness 100, seed 7,
  vignette 70, all ornaments on, empty inscription, default gold).
- `RomanRows.java`: `rowBackground(Context, RomanStyle)` → `StateListDrawable` (focused: gold gradient light→gold,
  radius 8 dp; rest: transparent with a 1 dp gold-30% bottom hairline), `rowText(TextView, RomanStyle, boolean focused-aware)`
  (system sans 16 sp for file names, ivory / stone ink when focused via `ColorStateList`; Cinzel only for titles,
  because Cinzel's lowercase are small caps and would hide the case of file names), `title(TextView, RomanStyle)`
  (Cinzel 22 sp gold, letter spacing 0.16), `rule(View, RomanStyle)` (1 dp gold 60%).
- `RomanDialog.java`: confirm dialog as a plain `Dialog` with a Cinzel title, message, two pills
  (`RomanLook.pill`), `destructive` flag puts initial focus on Cancel; BACK = Cancel. Also `RomanDialog.input(...)`
  for rename (an `EditText` with the system keyboard).
- Tests: `RomanStyleTest` additions for `defaults()` (not null, gold ramp of `#C9A24B`, stone travertine).
- Reviewer checks: no new colours outside `RomanStyle`; no XML drawables; nothing allocates in `onDraw`.
- Device: none.

**F5. File model (pure)** · Sonnet
- `files/.../Entry.java` (name, absolute path, isDir, size, mtime, kind), `MimeKinds.java` (extension → kind
  VIDEO/AUDIO/IMAGE/SUBTITLE/OTHER and MIME: mkv, mp4, m4v, mov, avi, ts, m2ts, webm, wmv, mpg, mp3, flac, m4a,
  aac, ogg, opus, wav, jpg, jpeg, png, webp, heic, gif, bmp, srt, ass, ssa, vtt, sub), `NaturalOrder.java`
  (case-insensitive, digit runs compared numerically, stable tie-break on the raw name), `FileSort.java`
  (NAME/DATE/SIZE, ascending/descending, folders first always), `DirLister.java` (`List<Entry> list(File dir,
  boolean showHidden)`; null `listFiles()` → empty list + `denied` flag; never throws).
- Tests (`NaturalOrderTest`, `FileSortTest`, `MimeKindsTest`, `DirListerTest` with JUnit `TemporaryFolder`):
  `Ep 2` < `Ep 10`; `a.MKV` is VIDEO; unknown ext is OTHER `application/octet-stream`; folders first in every sort;
  hidden skipped; unreadable dir → empty + denied.
- Device: none.

**F6. Volumes and the storage permission** · Sonnet · needs **Op-F0** results
- `VolumeModel.java` (pure): from (uuid, description, path, removable, primary, state, readOnly) build the root
  list: mounted USB volumes first (label = description, else `USB XXXX-XXXX`), then "Internal storage";
  read-only flag from `MNT_RDONLY` (passed in by the Android side).
- `Volumes.java`: `StorageManager.getStorageVolumes()`, `getDirectory()`, `isRemovable()`, `getState()`;
  read-only via `android.system.Os.statvfs(path).f_flag & ST_RDONLY`; `registerStorageVolumeCallback` on the main
  executor while the activity is started, unregistered in `onStop`.
- `PermissionGate.java`: if `!Environment.isExternalStorageManager()` show a Roman screen: "Files needs All files
  access" + the exact operator command (below) + a button that opens
  `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` **only if it resolves** (Op-F0 says whether it does).
- Tests (`VolumeModelTest`): order, labels, unmounted volumes hidden, read-only flag kept.
- Reviewer checks: the callback is unregistered; no polling of volumes.
- Device: none (the grant itself is in Op-F-install).
- **Status: Op-F0 PENDING** (projector was off, so it was not run). F6 was built for both outcomes with runtime
  detection: `PermissionGate` offers the Settings button only if the intent resolves (`<queries>` entry added for
  it), otherwise shows the `appops` command (`FilesInfo.APPOPS_COMMAND`); `Volumes` treats a mount with
  `ST_RDONLY` or state `mounted_ro` as read-only (`VolumeModel.Root.readOnly`). When Op-F0 runs, confirm which
  path the device takes and whether NTFS sticks mount at all or read-only; adjust only the wording if needed.
  `FilesActivity` is not yet wired to `PermissionGate`/`Volumes` (F7 does that).

**F7. Browser UI** · Sonnet
- `FilesActivity` + `EntryAdapter` (RecyclerView, `LinearLayoutManager`, stable ids = path hash), breadcrumb
  title (Cinzel, middle-ellipsised path), sort pill (cycles Name → Date → Size, remembered in
  `SharedPreferences`), empty/denied/unmounted states, size and date columns (`Formatter.formatShortFileSize`,
  `DateFormat`).
- `FocusMemory.java` (pure): `remember(path, position, offset)`, `recall(path)`, LRU of 64 folders.
- Listing runs on one background executor; results posted to the main thread; a newer request cancels an older one.
- Tests (`FocusMemoryTest`): recall after going down and back up; LRU eviction; unknown path → 0.
- Reviewer checks: BACK at the top of a volume goes to roots; D-pad never loses focus after a refresh
  (focus the remembered position after `notifyDataSetChanged`); listing is off the main thread.
- Device: none.

**F8. Open files: `StreamProvider` and the Player hand-off** · Sonnet
- `UriPaths.java` (pure): `encode(path)` / `decode(segment)` (URL-safe Base64 of UTF-8, no padding);
  `isInside(canonicalPath, roots)`; rejects `..`, NUL, non-absolute.
- `StreamProvider.java`: authority `com.gathin.files.stream`; `openFile` read-only
  (`ParcelFileDescriptor.MODE_READ_ONLY`), `getType` via `MimeKinds`, `query` for `OpenableColumns`
  (DISPLAY_NAME, SIZE); every call canonicalises the path and checks it is inside a current volume root; anything
  else → `FileNotFoundException`. No insert/update/delete.
- `Opener.java`: VIDEO/AUDIO/IMAGE → if `com.gathin.player` resolves, explicit `ACTION_VIEW` to it with the
  content URI, MIME, `FLAG_GRANT_READ_URI_PERMISSION`, extra `com.gathin.extra.PATH` (absolute path); else and for
  other kinds → `Intent.createChooser`. "Open with…" in the action menu always uses the chooser.
- Tests (`UriPathsTest`): round trip of Unicode/space names; `..`, symlink-escaping canonical path, path outside
  roots → rejected.
- Reviewer checks: provider not exported; read-only mode only; no `file://` anywhere.
- Device: none.

**F9. File operations engine (pure)** · Sonnet
- `FileOps.java`: `copy`, `move`, `delete`, `rename` on `java.io.File` with a `Progress` callback (bytes done/total,
  current name), a cancel flag checked per 256 KB buffer, `fsync` (`FileOutputStream.getFD().sync()`) before close.
  Move on the same volume = `renameTo`; across volumes = copy, verify size, then delete the source. A cancelled copy
  deletes its partial file.
- `OpPlan.java`: totals (files, bytes) before starting; refuses moving/copying a folder into itself or a
  descendant; free-space check through a `LongSupplier` (Android side passes `StatFs`).
- `ConflictNamer.java`: `name (2).ext`, `name (3).ext`, folders `name (2)`.
- Tests (`FileOpsTest` with `TemporaryFolder`): copy tree equals source; move removes source; cancel leaves no
  partial; conflict naming; self-descendant refused; insufficient space refused before any byte is written; delete
  of a read-only file reports failure without throwing.
- Device: none.

**F10. File operations UI** · Sonnet · **[Opus gate: data deletion]**
- `ActionMenu` (MENU key or OK held ≥ 600 ms): Open, Open with…, Rename, Copy, Move, Delete, Sort, Show hidden.
  Copy/Move enter **paste mode** (a gold bar "Paste here / Cancel" at the bottom; navigate to the target and confirm).
- Confirm dialogs (`RomanDialog`, destructive → Cancel focused): delete shows count and total size; replace on
  conflict asks Replace / Keep both / Skip (apply to all).
- One worker thread; progress dialog with Cancel; the screen stays on (`FLAG_KEEP_SCREEN_ON`) only while an
  operation runs; refresh the list after.
- Operations disabled with a reason on read-only volumes and on the roots screen.
- Tests: none new beyond F9 (UI).
- Reviewer checks: nothing deletes without a confirmed dialog; a move across volumes deletes the source only after a
  verified copy; the worker never touches views; rotation/recreation does not start an operation twice.
- Device: none.

**F11. Integration** · **Haiku**
- `themes/xgimi-s-max/config.json`: `dock_apps` + `"com.gathin.files"` (Player comes in P9) and `"columns": 5`
  (or per Q1). `themes/README.md` S Max row. `launcher/README` or `README.md`: a "Files" section (install, the
  `appops` grant, restore). `docs/plans/xgimi-os-plan.md`: one line pointing here. `logs/F11.txt` template for the
  operator's numbers.
- Reviewer checks: JSON parses (`python -m json.tool`); dock has no Netflix; `com.xgimi.filemanager` is **not**
  added to `hidden_apps` (it stays visible until Shibi signs off).

**Op-F-install** (operator, after F11, **each line needs OK**)
```powershell
cd launcher; .\gradlew.bat testReleaseUnitTest assembleRelease
& $adb -s $d install -r app\build\outputs\apk\release\app-release.apk        # F2 check: Home unchanged
& $adb -s $d install -r files\build\outputs\apk\release\files-release.apk
& $adb -s $d shell appops set --uid com.gathin.files MANAGE_EXTERNAL_STORAGE allow
& $adb -s $d shell appops get --uid com.gathin.files MANAGE_EXTERNAL_STORAGE   # read-only check: allow
..\tools\theme.ps1 ..\themes\xgimi-s-max                                      # pushes the theme, restarts Home
```
Then with a USB stick (a few videos, an `.srt`, photos, a 2,000-file folder): browse, sort, open, rename, copy to
internal, move back, delete a copy, unplug while browsing. Read-only checks:
`dumpsys meminfo com.gathin.files` (browsing, and during a 4 GB copy), `dumpsys activity top | Select-String ACTIVITY`,
`exec-out uiautomator dump /dev/tty` (UI tree to stdout, no file written; plan risk R1 says screenshots are empty).
Record in `logs/F11.txt`. Shibi tries it from the sofa and signs off (or not) on replacing `com.xgimi.filemanager`.

---

## 5. Feature P: Media Player (`com.gathin.player`, branch `feat/player`)

### 5.1 Architecture

```
Intent VIEW (content URI + extra PATH)  or  launcher dock / voice (MAIN)
        │
PlayerActivity ── engine: ExoPlayer (custom RenderersFactory default, DefaultMediaSourceFactory(dataSourceFactory,
        │                 PlayerExtractors: Matroska, Mp4, FragmentedMp4, Ts, Mp3, Flac, Ogg, Wav, Avi),
        │                 LoadControl caps from 2.2)
        ├─ Overlay (Cinzel title, gold progress bar, times; fades after 4 s; 1 Hz tick only while visible)
        ├─ CueView (text cues: StaticLayout with dark outline; bitmap cues: PGS/VobSub scaled)
        ├─ TracksMenu (Audio, Subtitles incl. Off, Subtitle file…) ── TrackLabels (pure)
        ├─ SubtitleFinder (pure) + SrtText (pure charset sniff → UTF-8 copy in cacheDir)
        ├─ ResumeStore (pure logic; SharedPreferences adapter)  ├─ KeyMap / SeekMath (pure)
        └─ Playlist (pure: same-kind siblings, natural order) for audio and "next episode"
SlideshowActivity ── ImageDecoder (≤ 1920x1080, LOW_RAM) ── SlideTimer (8 s)
LibraryActivity (MAIN, no data) ── "Continue watching" (ResumeStore) + "Browse USB" → com.gathin.files
```

Remote keys (`KeyMap`, unit tested): OK / MEDIA_PLAY_PAUSE / MEDIA_PLAY / MEDIA_PAUSE = play/pause (and show
overlay); LEFT / RIGHT = −10 s / +10 s; LEFT / RIGHT held (repeat) and MEDIA_REWIND / MEDIA_FAST_FORWARD =
−30 s / +30 s per step; DOWN or MENU = tracks menu; UP = show overlay; MEDIA_NEXT / MEDIA_PREVIOUS = next/previous
in the playlist; BACK = close menu → hide overlay → save position and exit.

Subtitles (requirement): sidecar `.srt` (also `.ass`, `.ssa`, `.vtt`) with the same base name, including
language tags (`Movie.en.srt`, `Movie.eng.forced.srt`, `Movie.fa.srt`); "Subtitle file…" lists every subtitle
file in the video's folder; embedded tracks from the container; the choice per file is remembered with the resume
position. Non-UTF-8 SRT (common for Persian/Arabic: Windows-1256; Western: Windows-1252) is sniffed and converted
to a UTF-8 copy in `cacheDir`; the fallback charset is a setting (default Windows-1256, **Q4**).

Fallback: on a `PlaybackException`, or when the audio track has no decoder (e.g. DTS if the SoC lacks it, Op-P0),
the overlay says what failed and offers "Open in another player" (chooser excluding `com.gathin.player`).

### 5.2 Steps

**P1. `:player` module, ExoPlayer, minimal playback, CI** · Sonnet
- `launcher/player/build.gradle` like `:files` (`com.gathin.player`, minSdk 30) + `androidx.media3:media3-exoplayer`
  pinned. Before choosing the version read the Media3 release notes: take the newest release whose
  `compileSdk` requirement is ≤ 34 (expected 1.4.1); write the version and the reason in the build file comment.
  Do not raise `compileSdk`.
- `proguard-rules.pro`: `-dontobfuscate` only (Media3 ships its consumer rules).
- Manifest: `PlayerActivity` with `VIEW` filters for `video/*`, `audio/*` (schemes `content`, `file`),
  `LibraryActivity` with `MAIN` + `LEANBACK_LAUNCHER` + vector banner, `SlideshowActivity` with `VIEW` `image/*`;
  `MANAGE_EXTERNAL_STORAGE`; `<queries>` for `com.gathin.files` and `VIEW video/*`.
- `PlayerExtractors.java` (the 9 extractors, no `DefaultExtractorsFactory`), `PlayerFactory.java` (builds the
  ExoPlayer with `new DefaultMediaSourceFactory(new DefaultDataSource.Factory(ctx), PlayerExtractors)` and the
  load control from 2.2), `PlayerActivity.java` (plays the intent's path if readable, else the URI, into a
  `SurfaceView`; releases the player in `onStop`).
- CI: add `check player 2097152` and artifact `player-debug-apk`.
- Tests: `PlayerExtractorsTest` (exactly the 9 classes, no Default factory referenced: read the source like
  `StoneTextureTest` does).
- Budget: report the release size; stop at 1.8 MB.
- Status: written with Media3 1.4.1. **Op-P0 (codec inventory) is pending** (projector was off): no codec
  assumption is made in code beyond ExoPlayer's `DefaultRenderersFactory` and the failure toast; P8's debug
  codec report will answer it.
- Reviewer checks: no `media3-ui`; no `DefaultExtractorsFactory`, no `new ExoPlayer.Builder(context)` without the
  custom factory (that pulls all extractors back in); player released in `onStop`; `settings.gradle` includes `:player`.
- Device: none.

**P2. Pure playback logic** · Sonnet
- `KeyMap.java` (key code + repeat count + overlay/menu state → action), `SeekMath.java` (clamp to [0, duration],
  ±10/±30 s), `TimeFormat.java` (`m:ss` under an hour, else `h:mm:ss`), `ResumeStore.java` (key = path + size +
  mtime; save when position > 60 s and < 95% of duration; clear when finished; LRU 200; also stores chosen audio
  and subtitle track ids) behind a `Store` interface with an in-memory test double.
- Tests: every row of the key table above; seek clamps at 0 and duration; `59:59` vs `1:00:00`; resume thresholds
  and LRU; a changed mtime does not resume.
- Device: none.

**P3. Roman overlay and resume** · Sonnet
- `Overlay.java` (Cinzel title, gold `ProgressBarView` drawn with two rects, current/total time, state glyph);
  shows on any key, hides after 4 s (one 250 ms alpha animation each way; reduced to none if the system animator
  scale is 0); 1 Hz updates **only while visible**.
- Resume prompt (`RomanDialog`, default focus "Resume from 1:02:13", second button "Start over") when
  `ResumeStore` has a position; positions saved in `onPause` and on completion.
- Keys wired through `KeyMap`.
- Reviewer checks: no `Handler` running while the overlay is hidden (check `removeCallbacks` on hide/stop);
  focus never lands on the `SurfaceView`.
- Device: none.

**P4. Subtitles: sidecar and embedded** · Sonnet · **[Opus gate: subtitles]**
- `SubtitleFinder.java` (pure): candidates for a video path given a folder listing; same-name first, then
  same-name with language tags (2/3-letter codes, `forced`, `sdh`), case-insensitive; order: system language,
  then English, then others.
- `SrtText.java` (pure): BOM → that charset; valid UTF-8 → UTF-8; else the fallback charset; returns UTF-8 bytes.
  Player writes the copy to `cacheDir/subs/<hash>.srt` (old copies deleted at start, max 20).
- Sidecars go in as `MediaItem.SubtitleConfiguration` (MIME from extension); embedded tracks come from the
  extractor. Default selection: remembered choice → forced track in the system language → first sidecar → off.
- `CueView.java`: text cues with `StaticLayout`, ivory text with a 2 dp dark outline (draw twice: stroke then fill),
  bottom margin 6% of height; bitmap cues drawn at the cue's position/size. Paint and layouts cached per cue group.
- Tests (`SubtitleFinderTest`, `SrtTextTest`): `Movie.fa.srt` matches `Movie.mkv`; `Movie2.srt` does not;
  Windows-1256 bytes decode to the expected Persian string; UTF-8 with BOM; invalid UTF-8 falls back.
- Reviewer checks: no allocation per frame in `CueView.onDraw`; cache copies are bounded; ASS styling dropped
  is documented, not a bug.
- Device: none.

**P5. Tracks menu** · Sonnet
- `TracksMenu.java` (a Roman side panel, D-pad list): Audio tracks, Subtitle tracks + Off, "Subtitle file…"
  (all subtitle files of the folder); selection through `TrackSelectionParameters`
  (`setPreferredAudioLanguage`/`setOverrideForType`), stored in `ResumeStore`.
- `TrackLabels.java` (pure): language code → display name in the system locale (`Locale.forLanguageTag`), channel
  count → `Stereo`/`5.1`/`7.1`, codec MIME → short name (`AC-3`, `E-AC-3`, `DTS`, `AAC`, `TrueHD`), "Forced",
  unsupported tracks marked "(not supported)".
- Tests (`TrackLabelsTest`): `fa` → Persian name in an English locale, `und` → "Unknown", 6 channels → `5.1`.
- Device: none.

**P6. Audio and the folder playlist** · Sonnet
- `Playlist.java` (pure): siblings of the same kind in natural order (reuse the rule from Files' `NaturalOrder`:
  copy into `:romancore` as `com.gathin.roman.NaturalOrder` in this step and make `:files` use it, so there is
  one copy), next/previous, wrap off.
- Audio screen in `PlayerActivity` (no video surface): Cinzel title, artist/album from `MediaMetadata`, progress,
  the same keys; next track on completion.
- Tests (`PlaylistTest`, move `NaturalOrderTest` to `:romancore`).
- Reviewer checks: `:files` still passes its tests after the move; no second `NaturalOrder`.
- Device: none.

**P7. Photo slideshow** · Sonnet
- `SlideshowActivity.java`: images of the folder (`Playlist` IMAGE), `ImageDecoder` with `setTargetSize` ≤
  1920x1080 keeping aspect, `setMemorySizePolicy(MEMORY_POLICY_LOW_RAM)`, decode on one background thread,
  keep current + next only, 300 ms cross-fade per change, 8 s per slide (`SlideTimer`, pure state: running/paused,
  next due time), OK pauses, LEFT/RIGHT manual, BACK exits. HEIC only if the device decodes it; a failed decode
  skips the file with a short toast.
- Tests (`SlideTimerTest`).
- Reviewer checks: at most 2 bitmaps referenced; old bitmap released after the fade; timer stopped in `onStop`.
- Device: none.

**P8. Library screen and debug codec report** · Sonnet
- `LibraryActivity.java`: "Continue watching" (ResumeStore entries whose file still exists, newest first, max 20)
  and "Browse USB" (opens `com.gathin.files`); empty state text.
- Debug only (`src/debug/`): `CodecReportActivity` listing `MediaCodecList(REGULAR_CODECS)` decoders (name, MIME,
  hardware/software, max size/frame rate) and `AudioManager`/`AudioCapabilities` passthrough encodings, also
  written to logcat tag `PlayerCodecs`. Not in the release APK.
- Reviewer checks: the debug activity is absent from the release manifest (`aapt2 dump xmltree` on the release
  APK, operator); Library uses no thumbnails.
- Device: none.

**P9. Integration** · **Haiku**
- `themes/xgimi-s-max/config.json`: add `"com.gathin.player"` to `dock_apps`. `themes/README.md`, `README.md`
  "Player" section (install, grant, supported formats from Op-P0, subtitle rules). `logs/P9.txt` template.
- Reviewer checks: JSON parses; `com.xgimi.gimiplayer` not hidden.
- **[Opus gate: whole P diff]**

**Op-P-install** (operator, **each line needs OK**): install `player-release.apk`;
`appops set --uid com.gathin.player MANAGE_EXTERNAL_STORAGE allow`; push the theme; install `player-debug.apk`
once to read the codec report (`logcat -d -s PlayerCodecs`, read-only), then reinstall the release APK. Test matrix
(record each in `logs/P9.txt`): MKV H.264 + AC-3 + embedded SRT and PGS; MKV HEVC 10-bit + E-AC-3/DTS; MP4 H.264
+ AAC with a sidecar `Movie.fa.srt` in Windows-1256; AVI Xvid + MP3; TS; MP3/FLAC album folder; 30 JPEG/HEIC
photos; resume after BACK; ±10/±30 s; audio and subtitle switching. Read-only: `dumpsys meminfo com.gathin.player`
during each video; compare failures with the codec report. Shibi signs off on replacing
`com.xgimi.gimiplayer`.

---

## 6. Feature V: Voice control (branch `feat/voice`)

### 6.1 Architecture

```
 Shibi: "Hey Google, set TV volume to 30 percent"           (Google Home, home "Transcendence Living")
   │
 Google Assistant cloud ── smart-home intents SYNC / QUERY / EXECUTE + OAuth access token
   │  HTTPS 443, public
   ▼
 https://<ha-node>.<tailnet>.ts.net            Tailscale Funnel (free): public TLS, ts.net certificate,
   │                                            tunnel opened outbound from the Alienware, no router port
   ▼
 ┌──────────────── Docker Desktop (WSL2) on the Alienware, compose project "ha" ────────────────┐
 │ [tailscale]  userspace networking, serve.json: :443 (Funnel) → http://caddy:8080              │
 │      ▼                                                                                        │
 │ [caddy]      path ALLOWLIST → http://homeassistant:8123 ; every other path → 404              │
 │              /api/google_assistant  /auth/authorize  /auth/token  /auth/login_flow*           │
 │              /auth/providers  /frontend_latest/*  /frontend_es5/*  /static/*  /manifest.json  │
 │      ▼                                                                                        │
 │ [homeassistant :8123]  also published on 127.0.0.1:8123 only (operator UI on the Alienware)   │
 │   google_assistant: expose ONLY media_player.tv ("TV"), report_state via service account      │
 │   media_player.tv = universal player: state from input_boolean / input_number / input_select  │
 │   scripts: tv_display_off/on, tv_set_volume, tv_select_source  (mode: queued, max 3)          │
 │   androidtv integration (ADB, polling OFF), key = Alienware's adbkey, mounted read-only       │
 └───────────────────────────────────────────┬──────────────────────────────────────────────────┘
                                             │ TCP 5555, LAN only (never exposed)
                                             ▼
                          Projector 10.0.0.242:5555 (adbd)
                          am start … | cmd media_session volume … | <display mechanism from Op-V0>
```

Commands (Google device **TV**: traits OnOff, Volume, InputSelector):

| Say | Google trait → HA | Projector command (adb shell) |
|---|---|---|
| "Turn off TV" / "Turn on TV" | OnOff → `script.tv_display_off` / `_on` | from Op-V0 (V5). Never POWER/SLEEP keys, never settings |
| "Set TV volume to 30 percent" | Volume (setVolume 30) → `script.tv_set_volume` | `cmd media_session volume --stream 3 --set N`, N = round(30/100 × max), max from Op-V0 (exact syntax confirmed by Op-V0) |
| "Switch TV to HDMI 1" | InputSelector "HDMI 1" | `am start -a com.xgimi.action.hdmiPlayer -d content://android.media.tv/passthrough/com.xgimi.tvinput%2F.inputservice.Hdmi1TvInputService%2FHW1 -t vnd.android.cursor.item/channel` |
| "Switch TV to YouTube" | InputSelector "YouTube" | `am start -n com.google.android.youtube.tv/com.google.android.apps.youtube.tv.activity.ShellActivity` |
| "Switch TV to Prime Video" | InputSelector "Prime Video" | `am start -n com.amazon.amazonvideo.livingroom/com.amazon.ignition.IgnitionActivity` |
| "Switch TV to Files" | InputSelector "Files" | `am start -n com.gathin.files/.FilesActivity` |
| "Switch TV to Player" | InputSelector "Player" | `am start -n com.gathin.player/.LibraryActivity` |
| "Switch TV to Home" | InputSelector "Home" | `am start -a android.intent.action.MAIN -c android.intent.category.HOME` |

"Open YouTube on TV" style phrases may be taken by Google's own YouTube/cast handling; the tested phrases are
the "switch TV to …" / "set TV input to …" forms (V6 test matrix). Google Home routines for nicer phrases are
optional and can be added by Shibi later; nothing depends on them.

### 6.2 Free HTTPS exposure: decision

| Option | Cost | Router change | Exposure | Verdict |
|---|---|---|---|---|
| **Tailscale Funnel** | free personal plan, no domain | none (outbound tunnel) | only the port/paths we serve; TLS at the node | **Chosen**: free, stable hostname, no inbound port, no domain purchase |
| Cloudflare Tunnel | free tunnel, **but needs a domain on Cloudflare** (~10 USD/year) | none | ingress rules can restrict paths | Use instead if Shibi already owns a domain (Q3); `cloudflared` replaces the `tailscale` service, Caddy stays |
| DuckDNS + Let's Encrypt | free | **port forward 443** to the Alienware | the Alienware is reachable from the internet; fails behind CGNAT; cert renewal | Rejected |

Security rules (each is a test or a reviewer check in V1 to V4):
1. Only Caddy's allowlisted paths reach HA; `/api/` (REST, long-lived tokens), `/api/websocket`, `/config`, the UI
   pages and everything else return 404 from outside. HA's own port is published on `127.0.0.1` only.
2. adb (5555) is never published or proxied; HA reaches the projector over the LAN only.
3. Google links to a **dedicated non-admin HA user** `google-link` with a 32-character random password (kept in
   the operator's password manager, never in the repo); `http: ip_ban_enabled: true, login_attempts_threshold: 5`,
   `use_x_forwarded_for` with `trusted_proxies` = the compose subnet only.
4. Google sees only `media_player.tv` (`expose_by_default: false`).
5. Secrets (`secrets.yaml`, `SERVICE_ACCOUNT.json`, `.env` with `TS_AUTHKEY`, the adb key) live in
   `homeassistant/secrets/` and `homeassistant/config/secrets.yaml`, both gitignored; the repo has `.example` files
   with placeholders only.
6. The Tailscale auth key is single-use, tagged `tag:ha`, and the tailnet policy allows Funnel only for that tag.

### 6.3 Repo layout (all prepared without Shibi)

```
homeassistant/
  RUNBOOK.md                  operator runbook, steps 1-14, SHIBI STEP marked
  compose.yaml                homeassistant (pinned tag), caddy (pinned), tailscale (pinned); network ha_net 172.30.0.0/24
  .env.example                TS_AUTHKEY=<placeholder>, TS_HOSTNAME=transcendence-ha, TZ=<placeholder>
  .gitignore                  .env, secrets/, config/secrets.yaml, config/SERVICE_ACCOUNT.json, config/.storage/, *.db*, config/.HA_VERSION
  caddy/Caddyfile             the allowlist
  tailscale/serve.json        Funnel :443 → http://caddy:8080 (uses ${TS_CERT_DOMAIN})
  config/configuration.yaml   http, recorder (db in /data volume, 3 days), logger, google_assistant, packages
  config/secrets.yaml.example google_project_id, google_service_account path, placeholders only
  config/packages/tv.yaml     helpers, scripts, universal media_player "TV"
  scripts/setup.ps1           checks Docker, creates secrets/, copies the adb key, writes .env from prompts
  scripts/check-exposure.ps1  curl tests of the public URL (allowlist works, /api/ is 404)
  scripts/adb-reauth.ps1      re-authorise the key after a reset (prints the steps, runs adb connect on the PC)
tests/test_homeassistant.py   CI: YAML/JSON parse, allowlist, forbidden commands, no secrets
docs/plans/xgimi-display-off.md   Op-V0 findings (written in V5)
```

adb key handling: HA's `androidtv` integration (python `adb-shell`, no adb server) reads its key from
`/config/.storage/androidtv_adbkey`. `setup.ps1` copies `%USERPROFILE%\.android\adbkey` and `adbkey.pub` (the
Platform-Tools key the projector already trusts) to `homeassistant/secrets/` and compose mounts them **read-only**
at that path, so no new "Allow USB debugging?" prompt appears on the projector. Re-authorisation (after a factory
reset, "Revoke USB debugging authorisations", or a new PC key): the operator runs `& $adb connect 10.0.0.242:5555`
on the Alienware, someone accepts the prompt on the projector with the remote and ticks "Always allow", then
`docker compose restart homeassistant`. The same key serves both. If the projector allows only one adb
connection at a time (risk V-R3), the operator runs `& $adb disconnect 10.0.0.242:5555` after each manual session.

### 6.4 Read-only investigation the operator runs (Op-V0, before V5)

Static, read-only (output to `C:\Users\shibi\cc_xgimi\inspect\<date>\voice\`):
```powershell
& $adb -s $d shell dumpsys package com.xgimi.uiconfiguration
& $adb -s $d shell dumpsys activity services com.xgimi.uiconfiguration/.PowerListenerService
& $adb -s $d shell getprop | Select-String -Pattern 'xgimi|standby|gmpf|power|panel|backlight'
& $adb -s $d shell getprop vendor.xgimi.standby.stage
& $adb -s $d shell service list | Select-String -Pattern 'gmpf|xgimi|power|panel'
& $adb -s $d shell dumpsys power
& $adb -s $d shell dumpsys display
& $adb -s $d shell dumpsys package | Select-String -Pattern 'standby|displayoff|display_off|screenoff|screen_off|panel_off|backlight|Gmpf' -Context 2,2
& $adb -s $d shell pm list packages -f | Select-String xgimi
# pull (read-only) the candidate APKs for analysis on the PC:
foreach ($p in 'com.xgimi.uiconfiguration','com.xgimi.xgimiservice','com.xgimi.misckey','com.xgimi.windowsystem') {
  $path = (& $adb -s $d shell pm path $p) -replace '^package:',''
  & $adb -s $d pull $path "C:\Users\shibi\cc_xgimi\inspect\apks\$p.apk" }
# volume, read-only:
& $adb -s $d shell cmd media_session help
& $adb -s $d shell cmd media_session volume --stream 3 --get
& $adb -s $d shell dumpsys audio | Select-String -Pattern 'STREAM_MUSIC' -Context 0,8
& $adb -s $d shell dumpsys package com.xgimi.soundermodeservice
# launch targets resolve (read-only):
& $adb -s $d shell cmd package resolve-activity --brief -n com.google.android.youtube.tv/com.google.android.apps.youtube.tv.activity.ShellActivity
& $adb -s $d shell cmd package resolve-activity --brief -n com.amazon.amazonvideo.livingroom/com.amazon.ignition.IgnitionActivity
& $adb -s $d shell cmd package resolve-activity --brief -a com.xgimi.action.hdmiPlayer -t vnd.android.cursor.item/channel
& $adb -s $d shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```
On the PC (not a device command): `aapt2 dump xmltree --file AndroidManifest.xml <apk>` for each pulled APK,
and `jadx` (or `apktool d`) to search the code for the "Display off" handler: strings `Display off`, `standby`,
`Gmpf`, `PowerListener`, `setBacklight`, `panel`, `vendor.xgimi.standby.stage`. Claude may read the decompiled
text the operator puts in `C:\Users\shibi\cc_xgimi\inspect\` and help find the call.

Observed, **needs OK** (it blanks the picture, exactly as Shibi would from the menu; no setting is changed):
```powershell
$log = "C:\Users\shibi\cc_xgimi\inspect\<date>\voice\logcat-displayoff.txt"
$job = Start-Job { & $using:adb -s $using:d logcat -v threadtime -b main,system,events > $using:log }   # read-only stream
# 1. someone presses Power on the remote: the power menu (Power off / Restart / Display off) appears
& $adb -s $d shell dumpsys activity top | Select-String -Pattern 'ACTIVITY|mResumed'
& $adb -s $d shell dumpsys window | Select-String -Pattern 'mCurrentFocus|mFocusedApp'
& $adb -s $d exec-out uiautomator dump /dev/tty                                       # menu tree, no file written
# 2. pick "Display off" with the remote, wait 10 s
& $adb -s $d shell getprop vendor.xgimi.standby.stage
& $adb -s $d shell dumpsys power   | Select-String -Pattern 'mWakefulness|Display Power|mScreenOn|state='
& $adb -s $d shell dumpsys display | Select-String -Pattern 'mState|mScreenState|Display Power'
& $adb -s $d shell dumpsys activity broadcasts history | Select-String -Pattern 'xgimi|standby|SCREEN' -Context 0,3
& $adb -s $d shell echo alive                                                          # adb still reachable?
# 3. wake it the normal way (the key XGIMI documents, e.g. OK or Power on the remote: a person does this), repeat step 2's reads
Stop-Job $job; Select-String -Path $log -Pattern 'xgimi|standby|Gmpf|PowerListener|display|backlight|panel'
```

How the result feeds the V steps (written by Opus into `docs/plans/xgimi-display-off.md` before V5):

| Finding | V5 implements |
|---|---|
| An exported activity/receiver/service of `com.xgimi.uiconfiguration` (or another XGIMI app) is called with an action/extra for "display off" | `script.tv_display_off` = that exact `am start`/`am broadcast`/`am startservice` line; display on = its counterpart found the same way |
| Only an internal (non-exported) call reaches `Gmpf_PowerManager` / sets `vendor.xgimi.standby.stage` | Stop. Opus writes the options for Shibi (e.g. `service call` to the vendor binder with the observed code, or opening the power menu and selecting the item with D-pad keys); nothing is wired without Shibi's OK. `setprop` is never used |
| adb is **not** reachable while the display is off | display off stays possible but "turn on TV" by voice is not; V5 documents it and the operator decides with Shibi |
| Display on = any key | display on uses the most harmless key that exits the mode (e.g. `KEYCODE_BACK`), never POWER/SLEEP/WAKEUP without Shibi's OK |

### 6.5 Steps

**V1. Compose, proxy, tunnel config, CI tests** · Sonnet
- Files: `homeassistant/compose.yaml`, `.env.example`, `.gitignore`, `caddy/Caddyfile`, `tailscale/serve.json`
  as 6.3; images pinned to explicit tags (HA: the current stable `YYYY.M.x` on the day; Caddy 2.x; Tailscale
  stable), each with a comment "update by hand". HA: volumes `./config:/config`, `ha_db:/data`,
  `./secrets/adbkey:/config/.storage/androidtv_adbkey:ro`, `./secrets/adbkey.pub:/config/.storage/androidtv_adbkey.pub:ro`;
  ports `127.0.0.1:8123:8123` only; `restart: unless-stopped`. Tailscale: `TS_USERSPACE=true`, `TS_SERVE_CONFIG`,
  `TS_STATE_DIR` volume, no ports. Caddy: no ports.
- `tools/requirements.txt`: add `pyyaml`.
- `tests/test_homeassistant.py`: compose parses; only `127.0.0.1:8123` is published; no service publishes 5555;
  Caddy allowlist equals the list in 6.1 exactly (parse `@allowed path …` lines) and there is a final 404;
  `serve.json` has `AllowFunnel` only for 443 and proxies only to `caddy:8080`; `.gitignore` covers the secret paths.
- Reviewer checks: no real hostnames, keys or emails; userspace networking (no `/dev/net/tun`, no `NET_ADMIN`).
- Device: none.

**V2. HA configuration** · Sonnet
- `config/configuration.yaml`: `default_config:` minus nothing heavy (`recorder: db_url: sqlite:////data/home-assistant_v2.db`,
  `purge_keep_days: 3`), `http:` (6.2 rule 3), `logger: default: warning`, `homeassistant: packages: !include_dir_named packages`,
  `google_assistant:` (`project_id: !secret google_project_id`, `service_account: !include SERVICE_ACCOUNT.json`,
  `report_state: true`, `expose_by_default: false`, `entity_config: media_player.tv: {name: TV, expose: true, room: <placeholder>}`).
- `config/secrets.yaml.example`; RUNBOOK section "Google side" (values to type in the Google Home Developer Console:
  fulfillment `https://<host>/api/google_assistant`, client id `https://oauth-redirect.googleusercontent.com/r/<project_id>`,
  authorisation URL `https://<host>/auth/authorize`, token URL `https://<host>/auth/token`, scopes `email`, `name`;
  HomeGraph API on; service account key → `SERVICE_ACCOUNT.json`). The operator follows HA's current
  "Google Assistant – Manual setup" page for the exact console labels (Google renames them).
- Tests: YAML loads with a loader that accepts `!secret`, `!include`, `!include_dir_named`; only
  `media_player.tv` exposed; `expose_by_default` false; `trusted_proxies` only the compose subnet.
- Device: none.

**V3. The TV package** · Sonnet · **[Opus gate: commands sent to the projector]**
- `config/packages/tv.yaml`: helpers `input_boolean.tv_display`, `input_number.tv_volume` (0-100),
  `input_select.tv_source` (HDMI 1, YouTube, Prime Video, Files, Player, Home); scripts `tv_select_source`
  (choose → the table in 6.1 via `androidtv.adb_command` on `media_player.projector_adb`), `tv_set_volume`
  (`N = (pct * VOLUME_MAX / 100) | round`; `VOLUME_MAX` a substitution placeholder until V5), `tv_display_off` and
  `tv_display_on` (until V5: a `persistent_notification` "display mechanism not wired yet", no device command);
  `media_player.tv` = `universal` with `state_template` from `tv_display`, `attributes` volume/source from the
  helpers, `commands` turn_on/turn_off/volume_set/select_source → scripts, `source_list` = the 6 sources. Scripts
  `mode: queued`, `max: 3`, each command with a 10 s timeout and a notification on failure.
- Tests: every `source_list` entry has a command; every `adb_command` string matches an allowlist of patterns
  (`am start …`, `cmd media_session volume --stream 3 --set {{…}}`, later the V5 line); a denylist fails the build:
  `KEYCODE_POWER`, `KEYCODE_SLEEP`, `KEYCODE_WAKEUP`, `keyevent 26`, `keyevent 223`, `keyevent 224`, `settings put`,
  `setprop`, `svc power`, `reboot`, `pm `, `input keyevent POWER`.
- Reviewer checks: no polling (`scan_interval` not set anywhere, RUNBOOK says to switch polling off for the
  androidtv entry); `media_player.projector_adb` is not exposed to Google.
- Device: none.

**V4. Operator scripts and RUNBOOK** · Sonnet
- `scripts/setup.ps1` (no device writes: checks `docker version`, creates `secrets/`, copies the adb key, asks for
  `TS_AUTHKEY` and writes `.env`, copies `secrets.yaml.example` → `secrets.yaml` if missing),
  `scripts/check-exposure.ps1` (`curl.exe` from the Alienware to the public URL: `/api/` → 404, `/api/websocket`
  → 404, `/auth/authorize` → 200, `/api/google_assistant` POST without token → 401), `scripts/adb-reauth.ps1`.
- `RUNBOOK.md` (14 steps, see 6.6), with the SHIBI STEP in a box.
- Tests: the Python test checks every `.ps1` under `homeassistant/scripts/` contains no `adb … shell` write verbs
  (same denylist as V3 plus `install`, `uninstall`, `appops set`).
- Device: none.

**V5. Wire display off/on and the volume max** · Sonnet · needs Op-V0 · **[Opus gate]**
- Opus first writes `docs/plans/xgimi-display-off.md` (findings, chosen mechanism, why it is XGIMI's own, how to
  undo). Sonnet then replaces the V3 placeholders in `tv.yaml` with the chosen lines and `VOLUME_MAX`, and adds
  them to the allowlist in the test.
- Reviewer checks: the command is the one observed from XGIMI's own menu, not a black activity, not a key from the
  denylist; display-on works from display-off (Op-V0 showed adb alive), or the limitation is documented.
- Device: none (tests in V6).

**V6. Bring-up and voice test** · operator (+ SHIBI STEP) · Haiku tabulates the results into `logs/V6.txt`
- RUNBOOK steps 1-14 below. Each line that touches the projector **needs OK**.

### 6.6 RUNBOOK outline (`homeassistant/RUNBOOK.md`)

1. Docker Desktop (WSL2) running, starts with Windows; the Alienware's sleep is off (Alienware setting, not the projector).
2. `scripts\setup.ps1`.
3. Tailscale (operator's own account, free): enable HTTPS and Funnel for `tag:ha` in the tailnet policy; create a
   tagged single-use auth key → `.env`.
4. `docker compose up -d`; open `http://127.0.0.1:8123`, create the HA **owner** account (operator, strong password);
   create user `google-link` (non-admin, "can only log in from the local network" off).
5. Add integration "Android Debug Bridge": host `10.0.0.242`, port 5555, key path default; System options → **turn
   polling off**. Test from Developer tools: `androidtv.adb_command` `echo ok` (read-only; projector unchanged).
6. `scripts\check-exposure.ps1` must pass before Google is configured.
7. Google Cloud / Google Home Developer Console with the **operator's** Google account: project
   `transcendence-ha`, cloud-to-cloud integration with the values from V2, HomeGraph API, service account key →
   `config/SERVICE_ACCOUNT.json`, `google_project_id` in `secrets.yaml`; restart HA.
8. Make the test integration visible to Shibi's account: add Shibi's Google account to the project in Cloud IAM
   with the Viewer role (Google's documented way to let other accounts test a smart-home project). If the
   integration does not show up for Shibi, fallback: in step 9 Shibi signs in once to the console on the Alienware
   and the operator creates the project under Shibi's account in the same sitting.
9. ┌──────────────────────────────────────────────────────────────────────────────────────────┐
   │ **SHIBI STEP (the only one).** On Shibi's phone, Google Home app (signed in to Shibi's     │
   │ Google account; sign in if asked) → + → Set up device → Works with Google → search         │
   │ "[test] transcendence-ha" → the Home Assistant sign-in page opens → user `google-link`,    │
   │ password from the operator → **Allow / Link** → choose home "Transcendence Living" and the │
   │ room. Done: a device "TV" appears.                                                         │
   └──────────────────────────────────────────────────────────────────────────────────────────┘
10. If the projector already exists in Google Home as a Cast device named "TV", the names clash (risk V-R4):
    Shibi decides which keeps "TV" (Q2).
11. Voice tests (each **needs OK** because it changes the screen/volume): "switch TV to Home / YouTube / Prime Video
    / Files / Player / HDMI 1", "set TV volume to 20 / 35 percent" (check `cmd media_session volume --stream 3 --get`
    after, read-only), "turn off TV", "turn on TV". Log each in `adb_log.txt` and `logs/V6.txt`.
12. Reboot the Alienware once; check HA, Caddy and Tailscale come back and step 11 still works.
13. Re-authorisation drill described (not executed).
14. Rollback: `docker compose down`; unlink in the Google Home app (Shibi, optional); delete the console project.

---

## 7. Feature A: Atmosphere report (branch `feat/atmosphere-report`)

### 7.1 Steps

**A1. Read-only inspection scripts for all features** · Sonnet
- `tools/inspect/common.ps1`: `Invoke-Adb` wrapper: logs `yyyy-MM-dd HH:mm:ss <args>` to
  `C:\Users\shibi\cc_xgimi\adb_log.txt` (path overridable), writes output to a per-run folder
  `C:\Users\shibi\cc_xgimi\inspect\<yyyyMMdd-HHmm>\<topic>\NN-<name>.txt`, and **refuses** (throws before running)
  anything matching the denylist: `install|uninstall|settings put|setprop|appops set|pm (disable|enable|clear|grant|revoke|hide)|am (start|broadcast|startservice|force-stop|kill)|input |svc |reboot|cmd .* --set|rm |mv |cp |dd |logcat -c`.
- `tools/inspect/atmosphere.ps1` (7.2), `tools/inspect/voice.ps1` (6.4 static part; the observed part prints the
  steps for a person and runs only the reads), `tools/inspect/storage.ps1` (Op-F0), `tools/inspect/media.ps1` (Op-P0).
- `tests/test_inspect_scripts.py`: every `Invoke-Adb` line in `tools/inspect/*.ps1` passes the same denylist; the
  logger path is the one in the brief.
- Device: none (the scripts are run by the operator).

**A2. Report template** · **Haiku**
- `docs/reports/atmosphere.md` = the template in 7.3 with placeholders; a line in `docs/plans/xgimi-os-plan.md`
  section 4 (batch 4) pointing to it.

**Op-A** (operator, read-only, ~70 min, Home idle, no USB/HDMI activity): `tools\inspect\atmosphere.ps1`.

**A3. Tabulate** · **Haiku**: fill the tables of the report from the raw files (no judgement, numbers only;
unit conversions; mean/max).

**A4. Findings and verdict** · Opus · **[Opus gate]**: what it is, what triggers it, cost, heavy or not (7.4),
recommendation. Then the A PR is complete.

### 7.2 Exact read-only commands (Op-A)

```powershell
$o = "C:\Users\shibi\cc_xgimi\inspect\$(Get-Date -f yyyyMMdd-HHmm)\atmosphere"; New-Item -ItemType Directory -Force $o | Out-Null
# identity, manifest, size
& $adb -s $d shell dumpsys package com.xgimi.atmosphere                       > "$o\01-package.txt"
& $adb -s $d shell pm list packages -f -U com.xgimi.atmosphere               > "$o\02-path-uid.txt"
$uid = ((Get-Content "$o\02-path-uid.txt" | Select-String 'uid:(\d+)').Matches[0].Groups[1].Value)
$apk = ((& $adb -s $d shell pm path com.xgimi.atmosphere) -replace '^package:','').Trim()
& $adb -s $d shell ls -l $apk                                                 > "$o\03-apk-size.txt"
& $adb -s $d pull $apk "$o\atmosphere.apk"
# triggers: launcher entry, dreams, receivers, alarms, jobs
& $adb -s $d shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER > "$o\04-launchers.txt"
& $adb -s $d shell cmd package query-services --brief -a android.service.dreams.DreamService > "$o\05-dreams.txt"
& $adb -s $d shell settings get secure screensaver_components                 > "$o\06-screensaver.txt"
& $adb -s $d shell settings get secure screensaver_enabled                   >> "$o\06-screensaver.txt"
& $adb -s $d shell settings get secure screensaver_activate_on_sleep         >> "$o\06-screensaver.txt"
& $adb -s $d shell dumpsys dreams                                             > "$o\07-dreams-state.txt"
& $adb -s $d shell dumpsys activity broadcasts | Select-String -Pattern atmosphere -Context 3,6 > "$o\08-receivers.txt"
& $adb -s $d shell dumpsys alarm             | Select-String -Pattern atmosphere -Context 1,4 > "$o\09-alarms.txt"
& $adb -s $d shell dumpsys jobscheduler      | Select-String -Pattern atmosphere -Context 1,6 > "$o\10-jobs.txt"
& $adb -s $d shell dumpsys activity services com.xgimi.atmosphere             > "$o\11-services.txt"
& $adb -s $d shell dumpsys activity processes | Select-String -Pattern atmosphere -Context 2,12 > "$o\12-procstate.txt"
& $adb -s $d shell dumpsys usagestats        | Select-String -Pattern atmosphere -Context 0,2 > "$o\13-usage.txt"
& $adb -s $d shell dumpsys batterystats --charged com.xgimi.atmosphere        > "$o\14-cpu-since-boot.txt"
& $adb -s $d logcat -d -v threadtime         | Select-String -Pattern 'atmosphere|XgimiWall|Wall' > "$o\15-logcat.txt"
& $adb -s $d shell getconf CLK_TCK                                            > "$o\16-clk-tck.txt"
# 60 minutes of samples, every 5 minutes (13 samples)
for ($i = 0; $i -le 12; $i++) {
  $t = Get-Date -f HH:mm:ss
  $pid_ = ((& $adb -s $d shell pidof com.xgimi.atmosphere) | Out-String).Trim()
  "== $t pid=$pid_" | Add-Content "$o\20-samples.txt"
  & $adb -s $d shell ps -A -o PID,PPID,USER,RSS,VSZ,TIME,S,NAME | Select-String atmosphere | Add-Content "$o\20-samples.txt"
  & $adb -s $d shell top -b -n 1 -m 15 -o PID,USER,%CPU,RES,NAME          | Add-Content "$o\21-top.txt"
  if ($pid_) {
    & $adb -s $d shell cat /proc/$pid_/stat                                    | Add-Content "$o\22-stat.txt"
    & $adb -s $d shell dumpsys meminfo com.xgimi.atmosphere                    | Add-Content "$o\23-meminfo.txt"
  }
  & $adb -s $d shell dumpsys netstats detail | Select-String -Pattern "uid=$uid" -Context 0,6 | Add-Content "$o\24-net.txt"
  if ($i -lt 12) { Start-Sleep -Seconds 300 }
}
```
On the PC (no device): `aapt2 dump badging "$o\atmosphere.apk"`, `aapt2 dump xmltree --file AndroidManifest.xml
"$o\atmosphere.apk"`, `tar -tvf "$o\atmosphere.apk"` (asset list and sizes; Windows `tar` reads zip).

Optional, **needs OK** (only if the manifest shows it is user-launchable and Shibi wants the active cost): Shibi
opens "XGIMI Wall" with the remote; the operator repeats the sample loop for 5 minutes (1-minute interval) and adds
`dumpsys gfxinfo com.xgimi.atmosphere` (frame stats: shows continuous animation).

### 7.3 Report template (`docs/reports/atmosphere.md`)

```markdown
# com.xgimi.atmosphere ("XGIMI Wall"): what it does and what it costs
Measured: <date, time span>, build RT.3.24.329, Home = com.gathin.tvhome, state during sampling: <idle on Home>.
Raw files: C:\Users\shibi\cc_xgimi\inspect\<run>\atmosphere\ (not in the repo).

## 1. Identity
| Item | Value |  (version name/code, uid, system app?, privileged?, enabled?, APK size, first install, last update, signature: platform/XGIMI)

## 2. Components (from the manifest)
| Kind | Name | Exported | Intent filters / triggers | Permission |
(activities, services, receivers, providers; dream service yes/no)

## 3. Permissions requested / granted
| Permission | Granted | Why it matters |

## 4. What starts it
| Trigger | Evidence (file:line) | Seen in this run? |
(boot receiver, screensaver/dream, launcher entry, alarms, jobs, sticky service, another XGIMI app binding it)

## 5. Assets
| Folder | Files | Total size | Content (scenes, video, audio, shaders) |

## 6. Cost over 60 minutes at idle
| Sample | Running? | PID | RSS (MB) | PSS total (MB) | Java heap (MB) | Native (MB) | Graphics (MB) | top %CPU |
| Mean / max | | | | | | | | |
CPU time over the window: (utime+stime delta) / CLK_TCK = <s> → <%> of one core. Since boot (batterystats): <ms>.
Network over the window: rx <B>, tx <B>. Wakeups: alarms <n>/h, jobs <n>/h.

## 7. Active cost (optional, 5 min while XGIMI Wall is shown)
| PSS | %CPU | janky frames / total (gfxinfo) |

## 8. Verdict
Heavy: yes/no, against the thresholds in docs/plans/next-features.md 7.4 (list each threshold and the measured value).
Recommendation: <keep as is | keep hidden from the grid | disable in debloat batch 4 (Shibi decides) | replace with RomanDream>.
The package stays installed either way.
```

### 7.4 "Heavy" thresholds (any one makes it heavy)

- Resident while not shown: PSS mean > 20 MB, or it runs at all while not shown **and** cannot be stopped by
  the hidden/disabled state the debloat plan already proposes.
- CPU while not shown: > 0.5% of one core averaged over 60 minutes, or any non-zero network traffic.
- Wakeups: alarms or jobs more than once per hour.
- While shown: PSS > 80 MB or a continuous animation that keeps the GPU busy (gfxinfo frames growing at ~60/s).

If heavy, the proposal is `RomanDream` (already built) as the ambient screen, and keeping atmosphere disabled via
debloat batch 4 (Shibi decides). It is never uninstalled.

---

## 8. Operator device checklist per step

| Step | Device work | Read-only? |
|---|---|---|
| A1, A2, A3, A4 | none | |
| Op-A | 7.2 (70 min) | yes; the optional active run needs OK |
| Op-F0 (before F6) | `tools\inspect\storage.ps1` with a USB stick inserted (FAT32, plus exFAT/NTFS if available): `sm list-volumes all`; `sm list-disks`; `dumpsys mount`; `df -h`; `mount` (filter `storage`); `ls -la /storage` and `/storage/<uuid>`; `getprop persist.sys.fuse`; `pm list packages` (filter `documentsui`); `cmd package resolve-activity --brief -a android.intent.action.OPEN_DOCUMENT_TREE`; `cmd package resolve-activity --brief -a android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION`; `cmd package resolve-activity --brief -a android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION -d package:com.xgimi.filemanager`; `dumpsys package com.xgimi.filemanager` (permissions, flags); `appops get com.xgimi.filemanager`; `cmd package query-activities --brief -a android.intent.action.VIEW -t video/*` (same for `audio/*`, `image/*`) | yes |
| Op-P0 (before P1 is finalised) | `tools\inspect\media.ps1`: `ls /vendor/etc /odm/etc /system/etc` (find `media_codecs*.xml`) then `cat` each; `dumpsys media.extractor`; `dumpsys audio` (filter `surround|encoded|passthrough|STREAM_MUSIC`); `settings get global encoded_surround_output`; `dumpsys display` (filter `mSupportedModes|HdrCapabilities|mActiveModeId`); `cmd package query-activities --brief -a android.intent.action.VIEW -t video/x-matroska` | yes |
| Op-V0 (before V5) | 6.4 | static part yes; observed part needs OK |
| F1-F10, P1-P8, V1-V5 | none | |
| F2 / F11 / Op-F-install | install launcher + files, `appops set` (All files access), theme push, USB tests, meminfo | needs OK per line |
| P9 / Op-P-install | install player (+ debug once for the codec report), `appops set`, theme push, test matrix, meminfo | needs OK per line |
| V6 | RUNBOOK 4-12 | the voice tests need OK |

How the findings feed the code: Op-F0 decides whether F6 shows a Settings button (an "All files access" screen
resolves) or only the operator command, and which file systems are read-only; Op-P0 confirms D7 (if a
critical codec is missing from `MediaCodec` but the XGIMI player plays it, the fallback "Open in another player"
covers it, and the P9 docs list it); Op-V0 decides V5.

---

## 9. Risks

| # | Risk | Mitigation |
|---|---|---|
| R1 | The romancore move changes behaviour or breaks the release APK (resource shrinking, R class) | F1/F2 are pure moves; the launcher's 30+ unit tests and the unchanged 400 KB CI limit catch it; F2 is an Opus gate and checks `StoneCache` file format so the device cache is reused |
| R2 | No "All files access" screen in this TV's Settings | `appops set --uid … MANAGE_EXTERNAL_STORAGE allow` by the operator (needs OK; it gives our own two apps storage access, nothing else) |
| R3 | NTFS/exFAT sticks mount read-only or not at all | Op-F0 records the file systems; Files shows read-only volumes with operations disabled |
| R4 | Media3 + Guava push the player over budget | custom extractors, no `media3-ui`, R8; stop at 1.8 MB and report; a `MediaPlayer` build is the fallback design (loses embedded MKV subtitles) |
| R5 | Media3 version vs `compileSdk 34` / AGP 8.5.2 | P1 picks the newest release that builds with 34 and records it; no SDK bump in this plan |
| R6 | 32-bit RAM: 4K playback with a large buffer | 32 MB target bytes; PSS measured in Op-P-install; the launcher process may be killed while playing (expected; Home cold-starts in ~1 s from the stone cache) |
| R7 | DTS / TrueHD with no decoder on the SoC | Op-P0; audio track falls back to another track or the "Open in another player" path |
| R8 | Persian/Arabic SRT in legacy encodings | `SrtText` sniff + fallback setting (Q4) |
| V-R1 | Google renames console screens; the IAM-Viewer route for a second test account may not show the integration | RUNBOOK follows HA's current manual-setup doc; step 8 fallback keeps it one Shibi sitting |
| V-R2 | Funnel/Google reachability: the Alienware asleep, Docker not started, Tailscale key expiry | Docker starts with Windows; tagged keys do not expire once the node is up; RUNBOOK step 12 reboot test; voice works only while the Alienware is on (accepted in the brief) |
| V-R3 | The projector allows one adb connection; the operator's PC adb kicks HA off | `adb disconnect` after manual sessions; HA scripts reconnect on the next command; notification on failure |
| V-R4 | Name clash: a Cast/Google TV device already called "TV" in "Transcendence Living" | Q2; Shibi chooses which device keeps the name |
| V-R5 | Display-off mechanism is internal only, or adb dies while the display is off | 6.4 decision table; nothing wired without Shibi's OK; never POWER/SLEEP keys or settings |
| V-R6 | Exposing HA to the internet | allowlist proxy, non-admin link user, ip_ban, only one entity exposed, adb never exposed, tests in CI |
| V-R7 | Real standby (projector powered off) cannot be woken over adb | out of scope; "turn on TV" means "display on" from display-off mode only |
| A-R1 | Atmosphere is not running during the window, so the cost is ~0 and the trigger unknown | the report says so; the trigger comes from the manifest (receivers, dream); the optional active run needs OK |

## 10. Open questions for Shibi

- **Q1** Dock with five items: `columns: 5` (smaller tiles) or keep 4 and drop HDMI 1 from the dock (still on the Inputs pill)?
- **Q2** If the projector is already a Google Home device called "TV" (Cast), which one keeps the name?
- **Q3** Do you own a domain? If yes, Cloudflare Tunnel can replace Tailscale Funnel; otherwise Funnel (free) stays.
- **Q4** Default fallback encoding for old subtitle files: Windows-1256 (Persian/Arabic) or Windows-1252 (Western)?
- **Q5** App labels "Files" and "Player", or Roman names (e.g. "Tabularium", "Theatrum")?

## 11. Summary

1. Two new apps, Files (`com.gathin.files`) and Player (`com.gathin.player`), become modules of the existing `launcher/` Gradle project; the shared Roman code first moves unchanged into `:romancore` (F1, F2), and CI builds, tests, size-checks (app < 400 KB, files < 300 KB, player < 2 MB) and uploads a debug APK for each.
2. Files uses All files access + `java.io` for USB and internal storage, D-pad-first lists with remembered focus, safe copy/move/delete/rename, and hands media to Player through its own read-only content provider (F1-F11).
3. Player uses a trimmed Media3 ExoPlayer for embedded and sidecar subtitles (with legacy-encoding SRT fixed), audio/subtitle track menus, resume, ±10/±30 s keys, audio playlists and a photo slideshow, with a Roman overlay that is still when idle (P1-P9).
4. Voice runs Google Home "TV" → free HA `google_assistant` in Docker on the Alienware via Tailscale Funnel and a path-allowlist proxy → ADB with the already-trusted key; everything is in `homeassistant/` and Shibi only links the account once (RUNBOOK step 9).
5. Display off/on will use XGIMI's own power-menu mechanism found by a read-only investigation (6.4); POWER/SLEEP keys, black screens and power settings are ruled out and blocked by tests.
6. Atmosphere gets read-only scripts (shared by all investigations), a 60-minute cost capture and a report with explicit "heavy" thresholds; it stays installed whatever the verdict.
