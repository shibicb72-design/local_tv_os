# Plan: Roman look v2 for the XGIMI HORIZON S Max

Status: **plan only.** No app code written, no Gradle run, nothing done on the projector.
Branch: `opus/xgimi-os-plan`. Supersedes the art direction in `xgimi-os-plan.md` section 5. The S5 keys
(`font`, `roman_date`, `accent_color`, `laurel`) and their code stay as they are; v2 is a new, separate switch.

Why: Shibi saw S5 on the wall and called it far too minimal. S5 only changed the font to system serif, tinted
the greeting gold and added a 40 dp laurel next to the clock, all on a black background. v2 keeps the Apple TV
layout (header, empty "stage", dock with big focused tiles, one highlight) and dresses it as a Roman interior:
stone wall, gilded frieze with a laurel medallion, fluted pilasters at the edges, carved Cinzel lettering, and
a gold focus glow with lift.

---

## 0. What the code does today (facts the slices depend on)

- `MainActivity.onCreate` → `Config.load(this)` → `applyRomanLook(cfg)` (S5: serif typeface on 6 header views,
  accent on `greeting`, `ic_laurel` visible). `showWallpaper()` adds an `ImageView` at root index 0, RGB_565.
- Layout (`activity_main.xml`), root `FrameLayout` children in z-order: `ambient` (full-screen View, alpha 0),
  `heroName` (48 sp Outfit), content `LinearLayout` (padding 56 dp sides, 26 dp top: header `RelativeLayout`
  with `leftBlock` [greeting, laurel, clock, ampm, a 1 dp divider View, shamsiDate, date] and `topButtons`
  [btnInputs, btnWifi, btnSettings], then the grid wrapper `FrameLayout`, `marginTop` 10 dp), `glass` (GlassView).
- Screen 1920x1080 px, density 2.0 (xhdpi), so **1 dp = 2 px**, screen = 960x540 dp.
- Tiles: 180x101 dp at 4 columns, `FOCUS_SCALE` 1.10, corner radius 16 dp, item height = tile + 45 dp.
  Grid inner width 848 dp, cell 212 dp, first tile left edge ≈ 72 dp. Dock row is pushed to ≈ 30 dp above
  the bottom; dock tile top ≈ 346 dp; hero name top ≈ 256 dp.
- `DockDecoration` draws a `#141416` round rect (radius 26 dp, pad 14 dp) behind the dock.
- `GlassView` is a hardware layer with a white fill/specular/rim and three white glow strokes.
- `setAmbient()` makes a 480x270 ARGB_8888 bitmap (518 KB) per app colour.
- Unit tests are plain JVM JUnit 4 in `launcher/app/src/test/java/com/gathin/tvhome/`. **No `android.*` class
  works in them** (stubs throw), so every piece of logic that needs a test lives in a pure-Java class.
- Cinzel subset `res/font/cinzel_roman.ttf` (21 KB, weight 500) covers ASCII 0x20–0x7E plus `·` (U+00B7),
  `–`, `—`, `•`. Lowercase letters are Cinzel's small capitals. The name table keeps the copyright
  (ID 0) but not the licence fields (ID 13/14); no Reserved Font Name is declared in the OFL file, so the
  subset may keep the name. The OFL text is `launcher/FONT-LICENSE-CINZEL-OFL.txt`. Both are untracked today.
- CI (`.github/workflows/ci.yml`) runs `testReleaseUnitTest assembleRelease` and fails above 409,600 bytes.

---

## 1. Art direction

### 1.1 Mood

An imperial reception room at dusk, lit by lamps: warm travertine wall, gilded mouldings, ivory carved
letters. Calm: nothing moves except the existing focus animation. The texture is mid-dark on purpose. A
projector's black is the colour of the wall, so a near-black background turns to grey mush, while a pale
stone glares in a dark room and kills the contrast of white text. A mid-dark warm stone keeps the texture
visible and the ivory text readable.

### 1.2 Palette (ARGB ints live in `RomanStyle`, the one source of truth)

| Name | Hex | Use |
|---|---|---|
| Gold (accent) | `#C9A24B` | Rules, meander, pilaster mouldings, greeting, focus glow. `accent_color` overrides it |
| Gold light | `#DCC38A` | Top of every gold gradient, Roman date, crest highlight. Derived: accent blended 35% toward white |
| Gold deep | `#896E33` | Bottom of every gold gradient. Derived: accent × 0.68 per channel |
| Ivory | `#F3EBDD` | Clock, hero name base, tile names, pill text |
| Ivory dim | `#99F3EBDD` | Gregorian date, AM/PM (`#B3F3EBDD`) |
| Stone ink | `#1A1511` | Frieze slab (60% alpha), dock band (70%), focused-pill text |
| Relief shadow | `#80000000` | 1 dp offset under gold strokes and carved text |
| Travertine deep | `#2E261E` | Dark strata |
| Travertine base | `#4B3F32` | Body |
| Travertine light | `#5F5140` | Light strata (R2: was `#6A5A47`, -10% so gold keeps 3:1 over the header) |
| Travertine vein | `#8A775F` | Faint cross veins (max 18% mix) |
| Travertine pit | `#1F1914` | Pores |
| Marble base | `#4A4742` | Body (dimmed Luna/Carrara; R2: was `#4E4B46`) |
| Marble light | `#5C5851` | Clouding (R2: was `#66625B`; marble veins also fade with `smoothstep(0.18, 0.38, v)` instead of 0.12..0.32, same reason) |
| Marble vein | `#A39C90` | Veins (max 55% mix) |

Gold ramp derivation (pure, unit tested): `light = round(c + (255 − c) × 0.35)`, `deep = round(c × 0.68)` per
channel. For `#C9A24B` this gives exactly `#DCC38A` and `#896E33`.

### 1.3 Stone recipe (procedural, rendered once, cached)

Rendered at **960x540** (exactly half resolution, drawn ×2 with bilinear filtering), RGB_565, ordered dither.
All maths is `float` with `+ − × ÷`, `Math.floor`, `Math.abs`, `Math.sqrt` only (all exact IEEE, so output is
identical on the JVM and on ART). **No `Math.sin/cos/pow/exp/random`** in the generator.

Building blocks (`StoneTexture`, pure Java):
- **PRNG**: SplitMix64 seeded with `roman_seed`. `next()`: `z += 0x9E3779B97F4A7C15L; z = (z ^ (z >>> 30)) *
  0xBF58476D1CE4E5B9L; z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL; return z ^ (z >>> 31)`.
- **Tables**: `perm[256]` by Fisher–Yates with `bound = (int) ((next() >>> 33) % n)`, then `val[256]` floats
  `(next() >>> 40) / (float) (1 << 24)` in [0,1).
- **Value noise** `noise(x, y)`: lattice `xi = floor(x)`, `yi = floor(y)`, value at a corner
  `val[perm[(perm[xi & 255] + yi) & 255]]`, quintic fade `t³(t(6t − 15) + 10)`, bilinear blend. Range [0,1).
- **fBm** `fbm(x, y, oct)`: amplitude 0.5, frequency ×2 per octave, coordinates rotated each octave by the fixed
  matrix (0.8, −0.6; 0.6, 0.8) plus an offset (17.3, 9.1) to hide lattice lines; result divided by the total
  amplitude, so [0,1).
- `smoothstep(a, b, x)`, `lerp`, `fract(x) = x − floor(x)`.

Coordinates `x, y` are pixels of the 960x540 image; `v = y / 540`.

**Travertine** (`roman_stone: "travertine"`, the default):
1. Warp: `wx = x + 18 (fbm(x/140, y/140, 3) − 0.5)`, `wy = y + 6 (fbm(x/140 + 31.7, y/140 + 11.3, 3) − 0.5)`.
2. Strata: `s = smoothstep(0.30, 0.72, fbm(wx/380, wy/26, 4))` (stretched about 15:1, horizontal layers).
3. Body colour: `s < 0.5 ? lerp(deep, base, 2s) : lerp(base, light, 2s − 1)`.
4. Grain: `colour × (1 + 0.10 (fbm(x/5, y/5, 2) − 0.5))`.
5. Pores: `pit = smoothstep(0.80, 0.90, noise(x/3.2, y/1.6)) × smoothstep(0.50, 0.62, fbm(x/70, y/24, 2))`;
   `colour = lerp(colour, pit, 0.75 pit)`. That gives about 1–2% of pixels, in clusters that follow the layers.
6. Cross vein: `t = (0.35x + 0.94y)/210 + 2.4 fbm(x/160, y/160, 4)`, `d = |fract(t) − 0.5| × 2`,
   `vein = 1 − smoothstep(0, 0.035, d)`; `colour = lerp(colour, travertineVein, 0.18 × vein × calm)`.

**Marble** (`roman_stone: "marble"`):
1. Warp as above with amplitudes 40 / 40.
2. Body: `lerp(marbleBase, marbleLight, smoothstep(0.35, 0.75, fbm(wx/260, wy/260, 5)))`.
3. Main veins: `t = (0.82x + 0.57y)/170 + 3.4 fbm(x/190, y/190, 5)`, `main = 1 − smoothstep(0, 0.045, d)`,
   mix 0.55 × main × calm. Fine veins: `t2 = (−0.5x + 0.87y)/60 + 2.0 fbm(x/90 + 7, y/90 + 3, 4)`,
   `fine = 1 − smoothstep(0, 0.02, d2)`, mix 0.25 × fine × calm.
4. Grain ±6% (factor 0.12 instead of 0.10).

**Shared finishing** (both stones, in this order):
- `calm = smoothstep(0.12, 0.32, v)`: veins fade out over the header so the text sits on quiet stone.
- Header shade: `colour × (0.62 + 0.38 × smoothstep(0, 0.30, v))`.
- Vignette (`roman_vignette` = k, 0..100): `nx = (x/960 − 0.5) × 2`, `ny = (v − 0.48) × 2 × 0.85`,
  `r = sqrt(nx² + ny²)`, `colour × (1 − 0.65 (k/100) smoothstep(0.45, 1.25, r))`. At k = 70 the corners sit at
  about 55% brightness.
- Brightness (`roman_stone_brightness` = b, 50..150): `colour × b/100`, clamped to 255.
- Dither + pack: 4x4 Bayer `{0,8,2,10, 12,4,14,6, 3,11,1,9, 15,7,13,5}`, `th = (bayer + 0.5)/16`,
  `r5 = clamp(floor(r × 31/255 + th), 0, 31)`, `g6 = clamp(floor(g × 63/255 + th), 0, 63)`, `b5` like r;
  `pixel = (r5 << 11) | (g6 << 5) | b5`, stored **little-endian** (low byte first; ART on ARMv7 is little-endian
  and `copyPixelsFromBuffer` copies native order).

`roman_stone: "none"` skips the texture; the background is `background_color` (or stone ink `#1A1511` if the
theme leaves it at black), and the frieze, pilasters and crest still draw.

### 1.4 Typography (Cinzel 500 from `R.font.cinzel_roman`, only when `style` is `roman`)

| Element (view id) | Size | Letter spacing | Colour | Extra |
|---|---|---|---|---|
| Greeting (`greeting`) | 13 sp | 0.30 | Gold | all caps (already) |
| Clock (`clock`) | 56 sp | 0.02 | Ivory | shadow dx 0, dy 2 dp, radius 4 dp, `#99000000` |
| AM/PM (`ampm`) | 14 sp | 0.12 | `#B3F3EBDD` | |
| Header divider (new id `headerRule`) | 1x26 dp | | Gold at 60% | was `#33FFFFFF` |
| Roman date (`shamsiDate`) | 18 sp | 0.16 | Gold light | text through `Roman.inscribe()`: `XX OCTOBRIS MMXXVI`, `IVLII`, `AVGVSTI` |
| Gregorian date (`date`) | 11 sp | 0.20 | Ivory dim | all caps (already), top padding 4 dp |
| Hero name (`heroName`) | 44 sp | 0.08 | Gilded: vertical LinearGradient gold light → gold (55%) → gold deep over the text height | shadow dy 3 dp, radius 6 dp, `#B3000000` |
| Tile name under grid tiles (`name`) | 13 sp | 0.08 | Ivory | |
| Icon tile label (`label`, e.g. HDMI 1) | 13 sp | 0.06 | Ivory | |
| Pills (`btnInputs/btnWifi/btnSettings`) | 14 sp | 0.06 | Ivory / stone ink when focused | |
| Crest inscription | 10 sp | 0.12 | Gold light | drawn by `RomanFrameView`, max 8 characters |

The clock keeps Arabic digits so it stays readable across the room. Non-ASCII text (other locales, Wi-Fi names)
falls back to the system font glyph by glyph; that is acceptable and must not show empty boxes.

### 1.5 Layout and ornaments on the 1920x1080 screen (px; dp = px/2)

```
 y px
   0 ┌──────────────────────────────────────────────────────────────────────────────┐
  52 │   GOOD EVENING                                         (Inputs)(Wi-Fi)(Set) │ header, unchanged
     │   10:45 PM │ XX OCTOBRIS MMXXVI                                              │
 ~212│              GREGORIAN DATE                                                  │ header bottom H
 272 │╔══╤══════ meander frieze ══════╗ (crest) ╔══════ meander frieze ═══════╤══╗│ frieze, H + 60 px
 320 │║  │                                                                  │  ║│
     │║  │      stage: empty stone, ambient glow of the focused app          │  ║│ pilasters
 512 │║  │  YouTube   (hero name, gilded, x 136 px)                          │  ║│
 664 │║  │ ┌────────────── dock band (gold double rules) ───────────────┐   │  ║│
     │║  │ │ [YouTube]   [Prime Video]   [HDMI 1]   [next app]           │   │  ║│
 922 │║  │ └─────────────────────────────────────────────────────────────┘   │  ║│
1056 │╚══╧                                                                ╧══╝│ bases
1080 └──────────────────────────────────────────────────────────────────────────────┘
```

| Ornament | Position (px) | Size | Drawing |
|---|---|---|---|
| Stone | full screen | 960x540 bitmap ×2 | `ImageView` at root index 0, `FIT_XY`, filtered |
| Vignette | baked into the stone | | no extra view, no runtime cost |
| Frieze slab | x 28..1892, top = header bottom + 60 (≈ 272), height 48 | 1864x48 | stone ink at 60% |
| Frieze rules | top edge and bottom edge of the slab | 2 px each | gold; 2 px relief shadow line under the bottom rule |
| Meander | centred in the slab, height 30 (key 5 g, g = 6 px), stroke 3 px | about 27 modules of 30 px per side of the crest | gold gradient (light top → deep bottom); the same path first drawn in relief shadow offset +2 px y |
| Frieze gap for the crest | x 880..1040 | 160 | meander and rules stop 8 px short of the wreath on each side |
| Laurel crest | centre (960, frieze centre ≈ 296), wreath outer diameter 144 | 144x144 | two branches of 9 leaves, 3 berries each, tie at the bottom; gold gradient fill; relief shadow +2 px |
| Inscription | inside the wreath, baseline centre + 7 | 10 sp | `roman_inscription` (S Max: `SALVE`, the Pompeian doorway greeting); nothing when empty |
| Pilasters | left x 28..84, right 1836..1892; from frieze bottom (≈ 320) to 1056 | 56 wide | see below |
| Dock band | around the dock tiles, pad 28 (14 dp) | ≈ 1690x258 | stone ink at 70%, radius 16 px (8 dp), gold double rule top and bottom |
| Grid wrapper top | frieze bottom + 16 (`marginTop` 62 dp instead of 10) | | tiles scrolled up pass under the crest's lower half, never under the frieze |

Pilaster details (dp, w = 28 dp shaft width): **capital** from the top: abacus 5 dp tall and w + 8 dp wide
(gold outline 1 dp, fill stone ink 50%), echinus 6 dp tall and w + 4 dp wide (fill stone ink 35%, gold top
line), astragal 1 dp gold line at w; capital total 12 dp. **Base** from the bottom: plinth 7 dp tall,
w + 8 dp; torus 5 dp, w + 4 dp, half-round drawn as a round rect radius 2.5 dp; gold top lines on both; base
total 12 dp. **Shaft**: fill ivory at 6% (a lighter slab), 1 dp edge lines `#33000000`. **Flutes**: 5,
centred at `shaftLeft + w × (i + 1)/6`, each a 2 dp dark groove `#47000000` plus a 1 dp highlight
`#1AFFF5E0` 2 dp to its right, stopping 6 dp short of the capital and the base (the fillet). A pilaster
shorter than 3w is not drawn at all.

Meander unit (unit tested). Grid step g, module width and height 5 g; segments in g units, y down, module origin
top-left:
`(0,5) → (0,0) → (4,0) → (4,3) → (2,3) → (2,2) → (3,2) → (3,1) → (1,1) → (1,5) → (5,5)`, and the next
module starts at its own `(0,5)` = this module's `(5,5)`. That is one continuous line with no crossings and
every gap at least 1 g. The usable width is filled with `floor(width / 5g)` modules, centred, and the line
continues as a flat run to both ends so it meets the rules.

Laurel geometry (unit tested). Centre (cx, cy), stem radius R = 30 dp. Angles in degrees, screen coordinates
(0° = +x, 90° = down). Left branch θ from 100° to 250°, right branch mirrored (θ' = 180° − θ), so a 40° gap is
left open at the top. 9 leaves per side at `θᵢ = 100 + i × 150/8.5`, alternating radius R + 3 dp (outside)
and R − 3 dp (inside), leaf length `10 dp × (1 − 0.3 i/8)`, width 0.4 × length, axis tangent to the stem tilted
30° toward its side. Each leaf is two quadratic curves; all leaves of both sides go into **one** `Path`. Stem:
an arc stroke of 1.5 dp per side. Berries: circles of radius 1.3 dp at R − 5 dp between leaves 2/3, 4/5 and 6/7.
Tie: a 6x3 dp round rect at (cx, cy + R).

### 1.6 Focus treatment

| Item | Today | Roman v2 |
|---|---|---|
| Tile scale | 1.10, overshoot 1.6, 260 ms | unchanged |
| Lift | none | `translationY −4 dp` and `translationZ +6 dp` (a real elevation shadow through the existing round-rect outline), in the same ViewPropertyAnimator, same duration and interpolator; reduced motion uses the same values with its own timing |
| Glass target | tile centre | tile centre −4 dp in y (`MainActivity.onTileFocused`), so the glass stays on the lifted tile |
| Glass glow | 3 white strokes `0x30/i` | 3 gold strokes, widths 2/4/6 dp, alpha `0x60`, `0x30`, `0x20` |
| Glass rim | 1.5 dp `#CCFFFFFF` | 2 dp, vertical LinearGradient gold light → gold → gold deep |
| Glass fill | `0x3DFFFFFF → 0x12FFFFFF` | `0x26FFF1D0 → 0x0AFFF1D0` (warm, fainter so the banner shows) |
| Glass specular | `0x66FFFFFF → 0` | `0x40FFF6E0 → 0` |
| Pills | frosted white, white when focused | normal: gradient `#66241D17 → #4D1A1511`, 1 dp stroke `#80C9A24B`, ivory text and icon; focused: gradient gold light → gold, stone-ink text and icon; radius 21 dp (unchanged) |
| Icon tiles (HDMI 1) | `#1C1C1E` | `#2A221B`, 1 dp stroke gold at 35%, icon tinted gold light |
| Ambient glow | 480x270 ARGB, alpha 1.0 | 240x135 ARGB (it is a blurred radial gradient, so the smaller size cannot be seen), shown at alpha 0.8 so the app colour warms the stone without flooding it |

---

## 2. Budgets

### 2.1 Memory (launcher PSS ≈ 58 MB today, ceiling 60 MB)

| Element | Steady state | Transient | Notes |
|---|---|---|---|
| Stone, preferred path | **1.04 MB** (960×540×2, `Bitmap.Config.HARDWARE`, gralloc only) | +1.04 MB software bitmap for < 100 ms, +1.04 MB `byte[]` file read, 64 KB write buffer, 2 row buffers of 1.9 KB | Software RGB_565 bitmap → `copy(HARDWARE, false)` → `recycle()` the software one |
| Stone, fallback (copy returns null) | 2.07 MB (1.04 native + 1.04 GPU texture) | same | Logged once; still under the ceiling (see total) |
| Window/root background removed while the stone shows | 0 MB, saves two full-screen fills per frame | | `getWindow().setBackgroundDrawable(null)`, root background null |
| Ambient (roman only) | 0.13 MB + 0.13 MB GPU | | **saves 0.78 MB** against today's 480x270 |
| Frieze, meander, pilasters, crest | < 0.05 MB (Paths, display list) + < 0.2 MB tessellation cache | | `RomanFrameView` must **never** use a layer: a full-screen ARGB layer is 8.3 MB |
| Cinzel | ≈ 0.05 MB | | glyphs go into the existing shared font atlas |
| Gold glass | unchanged | | same size hardware layer as today |
| Tile lift shadow | ≈ 0 | per-frame tessellation during the 260 ms animation | |
| Code | ≈ 0.02 MB | | |
| **Total** | **≈ +0.5 MB** (≈ 58.5 MB); fallback ≈ +1.5 MB (≈ 59.5 MB) | peak ≈ +3.2 MB for < 1 s at first start | First lever if over: render the stone at 768x432 (one constant) |

Rules: at most one full-screen-sized bitmap (the stone), no ARGB_8888 above 240x135 added, no `setLayerType` on
anything full-screen, no `int[w*h]` buffers (rows only), no continuous animation, no `Handler` loops.

#### 2.1.1 Screensaver (RomanDream) memory

Measured on the projector (`dumpsys meminfo com.gathin.tvhome`, total PSS): 51.8 MB fresh start; 70.0 MB right
after the dream ran and HOME ended it (the dream shares the launcher process); 60.4 MB 30 s later. The dream
already used the one shared hardware stone (no variants, no software copies, no extra bitmaps), so the rise is
the dream window's renderer state plus heap garbage that is only released at the next trim/GC. Changes:

- No config re-parse: the dream takes the style from `StoneCache.lastStyle()` (the launcher's), falling back to
  `Config.load` only if the launcher never loaded the stone. Saves the JSON parse garbage and a duplicate stone key.
- The pan no longer redraws 60 times a second: the wall moves ~1 px/s, so translations are snapped to 0.5 px
  (`RomanDreamMath.snap`), about 460 redraws per 90 s leg instead of 5400. That also stops the full-screen scene,
  including the blurred time shadow, being re-rasterised every frame, which is what keeps GPU caches warm.
- `teardown()` nulls `stone` and `overlay` (the system keeps the service instance for a while; animator listener
  and receiver are already released) and calls `Runtime.gc()` so heap garbage goes at once, not 30 s later.
- The shared stone bitmap is still never recycled by the dream; Home may be showing it.

Expected: peak lower (fewer frames drawn), and the post-dream value closer to the fresh start. Not yet
re-measured: repeat the three `dumpsys meminfo` readings on the device. If still above 60 MB, the next lever
is the dream's time shadow (`setShadowLayer`), then reflection-free `trimMemory` from `onDreamingStopped`.

### 2.2 APK (release ≈ 283 KB + 21 KB font ≈ 304 KB; CI hard limit 400 KB)

| Slice | Content | Budget (compressed, after R8) |
|---|---|---|
| R1 | Cinzel (21 KB, already counted), `RomanStyle`, `RomanLook`, Config keys | ≤ 6 KB |
| R2 | `StoneTexture`, `StoneCache` | ≤ 6 KB |
| R3 | `RomanFrameView`, `Meander`, `Laurel` | ≤ 6 KB |
| R4 | `Pilaster` + drawing | ≤ 3 KB |
| R5 | glass/dock/pill/tile changes (drawables built in code, no XML) | ≤ 4 KB |
| R6 | docs only | 0 |
| **Total** | | **≤ 25 KB → release ≤ 330 KB.** Stop and report if it goes over 345 KB |

No raster assets and no new XML drawables: every drawable is built in code (`GradientDrawable`,
`StateListDrawable`, `Path`), so the colours have one source (`RomanStyle`).

---

## 3. Theme keys (all added in R1; later slices only read them)

`config.xml` (defaults below, so other TVs keep today's look) and the same keys in `config.json`. Read only when
`style` is `roman`; otherwise none of the v2 code runs and nothing is allocated.

| Key | Type | Default | Meaning |
|---|---|---|---|
| `style` | string | `""` | `"roman"` turns on v2 (case-insensitive, trimmed). Any other value = off |
| `roman_stone` | string | `"travertine"` | `"travertine"`, `"marble"` or `"none"`; unknown → travertine |
| `roman_stone_brightness` | int 50..150 | 100 | Stone brightness in percent; out of range → default |
| `roman_seed` | int 0..9999 | 7 | Pattern of the stone |
| `roman_vignette` | int 0..100 | 70 | Edge darkening strength |
| `roman_meander` | bool | true | Greek-key frieze (the slab and rules stay, so the crest still has a frame) |
| `roman_pilasters` | bool | true | Fluted pilasters at the edges |
| `roman_crest` | bool | true | Laurel medallion in the frieze |
| `roman_inscription` | string | `""` | Up to 8 characters inside the crest, carved style (`U→V`, `J→I`, upper case, only `A–Z 0–9 space · . -`) |
| `accent_color` (S5) | `#RRGGBB` | unset → `#C9A24B` under `roman` | Gold; light and deep are derived |
| `roman_date` (S5) | bool | false | Still controls the Roman date line; under `roman` it is inscribed and gold |
| `font`, `laurel` (S5) | | | Ignored under `roman` (Cinzel is used; the crest replaces the small laurel) |
| `wallpaper` | bool | | A pushed `wallpaper.jpg` that is switched on wins over the stone (the theme author chose a picture) |

Turned on in:
- `themes/xgimi-s-max/config.json`: add `"style": "roman"`, `"roman_inscription": "SALVE"`; drop `"font": "serif"`
  and `"laurel": true` (ignored, so they would only confuse); keep `roman_date`, `accent_color`, the dock
  `["com.google.android.youtube.tv", "com.amazon.amazonvideo.livingroom", "input:hdmi1"]`, `dock_placeholders: false`.
- `launcher/app/src/debug/res/values/config.xml` (local only, stays **uncommitted** as its header says): add
  `<string name="style">roman</string>` and `<string name="roman_inscription">SALVE</string>`.

---

## 4. Rules for every slice

- Read this file, then only the files the slice lists. Java + Views, same style as the existing code (short
  Javadoc, comments that say why). No Kotlin, no new dependencies, no XML drawables, no raster assets.
- Pure-Java classes (no `android.*` import) for everything with a unit test; JUnit 4 in
  `launcher/app/src/test/java/com/gathin/tvhome/`.
- Default off: with `style` unset, the code path must be the old one, byte for byte where possible (an early
  `if (cfg.roman == null)` branch), with no extra allocation.
- No device commands. Gradle runs outside Claude (CI or Shibi): a slice is not "tested" until that output is
  shown. Do not claim tests pass without it.
- One commit per slice on the current branch, message `Rn: <what> (Sonnet)`. Never push to `main`.
- Report at the end: files changed, tests added, expected APK delta, anything not done.

---

## 5. Slices

### R1. Keys, palette and carved typography

**Goal:** the switch and every key exist; under `roman` the header, hero name and tile names are Cinzel with
the palette from 1.2/1.4. No stone or ornaments yet (background stays `background_color`).

Files:
- `git add launcher/app/src/main/res/font/cinzel_roman.ttf launcher/FONT-LICENSE-CINZEL-OFL.txt` (they are untracked).
- New `RomanStyle.java` (pure): the palette constants from 1.2; `static int[] goldRamp(int accent)` returning
  {light, mid, deep}; fields `stone`, `brightness`, `seed`, `vignette`, `meander`, `pilasters`, `crest`,
  `inscription`, `gold`, `goldLight`, `goldDeep`; `static RomanStyle of(String style, String stone,
  int brightness, int seed, int vignette, boolean meander, boolean pilasters, boolean crest, String inscription,
  int accent)` which returns **null** unless style is `roman`, normalises the stone name, clamps nothing (Config
  already rejects out-of-range ints) and runs the inscription through `Roman.inscribe`; `String stoneKey()` =
  `"v1-" + stone + "-" + seed + "-" + brightness + "-" + vignette` (cache key for R2).
- `Roman.java`: add `static String inscribe(String s, int max)`: upper case (`Locale.ROOT`), `U→V`, `J→I`, drop
  characters outside `A–Z 0–9 space · . -`, collapse spaces, trim, cut to `max`. `null` → `""`.
- `Config.java`: read the keys from 3 (xml + json, same helpers, ranges as in the table) into
  `final RomanStyle roman` (null when off). Add the constructor parameter at the end.
- `res/values/config.xml`: the 9 new entries with comments in the file's style.
- New `RomanLook.java` (Android helper, no state except a cached `Typeface`): `typeface(Context)` via
  `getResources().getFont(R.font.cinzel_roman)`; `applyHeader(Activity, RomanStyle)` sets the 1.4 values on
  greeting, clock, ampm, headerRule, shamsiDate, date (`setFontVariationSettings(null)` first, as S5 does) and
  the hero name size/spacing/shadow; `gild(TextView, RomanStyle)` installs a layout listener that sets the
  vertical gradient shader on the hero name's paint whenever its height changes.
- `activity_main.xml`: give the divider View `android:id="@+id/headerRule"`; give the header `RelativeLayout`
  `android:id="@+id/header"` (R3 needs it).
- `MainActivity.java`: `applyRomanLook(cfg)` calls `RomanLook.applyHeader` when `cfg.roman != null` and returns
  (the S5 path stays for everything else); hide `laurel` under roman. `updateGreeting()`: under roman the Roman
  date line is `Roman.inscribe(Roman.today(), 40)`.
- `AppAdapter.java`: under roman, `onCreateViewHolder` sets Cinzel, 13 sp, the letter spacing and ivory on
  `name` and `label`.
- `themes/xgimi-s-max/config.json`, the debug overlay (local), `themes/README.md` (new key rows, the S Max row
  says "Roman look v2").

Tests (`RomanStyleTest`, additions to `RomanTest`):
- `goldRamp(0xFFC9A24B)` = {`0xFFDCC38A`, `0xFFC9A24B`, `0xFF896E33`}; alpha always `0xFF`.
- `of("", …)`, `of(null, …)`, `of("greek", …)` → null; `of(" Roman ", …)` → not null.
- Stone: `"MARBLE"` → `marble`, `"granite"` → `travertine`, `"none"` → `none`.
- `stoneKey()` differs when any of seed/brightness/vignette/stone differ, equal otherwise.
- `inscribe("Augustus", 8)` = `AVGVSTVS`; `inscribe("Iulii", 40)` = `IVLII`; `inscribe("  salve  ", 8)` =
  `SALVE`; `inscribe("Ave · Roma!", 20)` = `AVE · ROMA`; `inscribe("ДОМ", 8)` = `""`; `inscribe(null, 8)` = `""`;
  cut at 8.
- `inscribe(Roman.date(2026, 10, 20), 40)` = `XX OCTOBRIS MMXXVI`; July → `… IVLII …`.

Budget: APK ≤ +6 KB beyond the font. Memory ≈ +0.05 MB.

Reviewer checks:
- With `style` unset the header code is the S5/default path (diff `applyRomanLook`); no `getFont` call.
- Every colour comes from `RomanStyle`, no stray hex literals in `RomanLook`.
- The gradient shader is rebuilt on size change, not per draw; the text shadow is dark, not gold, on the wall
  (HWUI draws text shadows with the shadow colour; a reviewer with the device photo confirms).
- Config: an out-of-range int or a wrong type falls back to the default, matching the existing `integer()`.
- The theme JSON parses (run `python -m json.tool`), has no Netflix, the dock is YouTube, Prime Video, `input:hdmi1`.
- The debug overlay was edited but not staged; the font and OFL file are staged.

### R2. Procedural stone, cached

**Goal:** under roman (and `roman_stone` ≠ `none`, no active wallpaper) the background is the travertine or
marble from 1.3, generated once on a background thread and cached.

Files:
- New `StoneTexture.java` (pure): constructor `(String stone, int seed, int brightness, int vignette, int w, int h)`;
  `void renderRow(int y, short[] out)` writes `w` packed RGB_565 pixels; package-private helpers `noise`,
  `fbm`, `smoothstep`, `ditherPack(int r, int g, int b, int x, int y)`, `static int unpackR/G/B(short)` for
  tests. Palette constants come from `RomanStyle`. Follow 1.3 exactly; tune palette constants by at most ±10%
  only if the contrast tests below fail, and write the new values back into this plan's table.
- New `StoneCache.java` (Android): `static void load(Context, RomanStyle, Consumer<Bitmap> onReady)`.
  1. A static field keeps the last bitmap and its key: same key → callback at once (activity recreation).
  2. Otherwise a single background `Thread` (`THREAD_PRIORITY_BACKGROUND`; a static in-flight flag prevents a
     second one) looks for `cacheDir/stone-<key>.565`: a 16-byte header (`'S','T','N','1'`, w, h,
     `key.hashCode()`, ints big-endian) then `w×h×2` bytes. The size and header must match exactly.
  3. A miss renders row by row into `stone-<key>.tmp` through a 64 KB `BufferedOutputStream`, renames it, and
     deletes other `stone-*.565` files.
  4. Reads the file into a `byte[]`, `Bitmap.createBitmap(w, h, RGB_565)`, `copyPixelsFromBuffer`, then
     `copy(Bitmap.Config.HARDWARE, false)`; on success recycle the software bitmap, otherwise keep it and log
     once. Posts to the main thread; drops the result if the activity is finishing.
  5. Any exception or `OutOfMemoryError` → log and leave the plain background. Home must never fail because of the stone.
  6. Debug log of the render time in ms (for the device measurement in R6).
- `MainActivity.java`: in `onCreate`, under roman with no wallpaper: root background = travertine/marble base
  colour at once (no black flash), then `StoneCache.load`. On ready, add an `ImageView` (`FIT_XY`) at index 0,
  fade it in once over 200 ms (no fade under `reduced_motion`), then `getWindow().setBackgroundDrawable(null)`
  and `root.setBackground(null)`. `setAmbient()`: under roman use 240x135 and set `ambient`'s target alpha
  to 0.8 where it animates to 1 today (all four places).

Tests (`StoneTextureTest`, use a small 96x54 image for speed unless stated):
- Determinism: two instances with the same arguments give identical rows; seed 7 vs 8 differ in > 50% of pixels.
- Range: every unpacked channel is within the palette's darkest × 0.3 and lightest × 1.5 (brightness 150).
- `ditherPack`: a flat (128,128,128) over a 4x4 block averages back to 128 ± 2 per channel; 0 → 0, 255 → max.
- Pack layout: `ditherPack(255, 0, 0, …)` has only bits 11–15 set (catches an R/B swap).
- Vignette: at 960x540, mean luminance of the 32x32 corner block < 0.7 × the centre block for vignette 70;
  vignette 0 → corner/centre ratio > 0.85.
- Brightness: mean of brightness 50 < mean of 100 < mean of 150.
- Header contrast (960 wide, rows 0 to 20% of h, both stones, defaults): with sRGB relative luminance
  (test-only `Math.pow` is fine), ivory vs the brightest header pixel ≥ 4.5:1, gold vs the brightest pixel of
  the left half ≥ 3:1.
- Pores: travertine at 960x540 has 0.5–4% of pixels darker than the pore colour + 10%.
- Source rule: a test that reads `StoneTexture.java` from `src/main/java` and fails on `Math.sin`, `Math.cos`,
  `Math.pow`, `Math.exp`, `Math.random`, `new Random` (keeps the output identical on JVM and ART).

Budget: APK ≤ 6 KB; memory as in 2.1 (stone 1.04 MB hardware, ambient −0.78 MB).

Reviewer checks:
- Only rows are buffered (no `int[w*h]`, no `short[w*h]`); the `byte[]` read is the one 1 MB transient.
- The temp file is renamed only after a complete write; a truncated or wrong-sized cache file is regenerated, not
  decoded; old cache files are deleted.
- The thread never touches views; the callback checks `isFinishing()/isDestroyed()`.
- The window background is removed only after the stone is attached (no black frame); with `style` unset nothing
  changes (`setAmbient` still 480x270, alpha 1).
- On the wall (Shibi's photo): travertine looks warm brown with horizontal layers, not blue (blue = R/B swapped).
  The header text is readable from the sofa.

### R3. Frieze, meander and laurel crest

**Goal:** the gilded frieze with the Greek key across the screen under the header, broken in the middle by the
laurel medallion with the inscription.

Files:
- New `Meander.java` (pure): `static float[] polyline(float left, float top, float width, float g)` returns
  x,y pairs: a flat lead-in at y = top + 5g from `left`, the meander unit from 1.5 repeated `floor(width / 5g)` times centred
  (called once per side of the crest),
  and a flat lead-out to `left + width`.
- New `Laurel.java` (pure): `static Leaf[] leaves(float cx, float cy, float r, float dp)` (Leaf: centre x/y,
  angle in degrees, length, width), `static float[] berries(…)` (x,y,r triples), following 1.5. `Math.sin/cos`
  are fine here (no cache, tests use tolerances).
- New `RomanFrameView.java` (custom View, **no layer**): `setStyle(RomanStyle)`, `setFriezeTop(int px)`.
  Builds Paths and Shaders in `onSizeChanged`/`setFriezeTop` only, never in `onDraw`. Draws in order: frieze slab,
  rules and relief line, meander (shadow pass, then gold gradient pass, `Paint.Join.MITER`, `Cap.SQUARE`),
  crest (shadow, stems, one leaf Path, berries, tie, all with one gold `LinearGradient`), inscription
  (Cinzel 10 sp, `Paint.Align.CENTER`). Respects `meander` and `crest` flags (gap only when the crest is on).
  Pilasters come in R4: leave a `drawPilasters(Canvas)` stub that does nothing.
- `activity_main.xml`: add `<com.gathin.tvhome.RomanFrameView android:id="@+id/romanFrame"
  android:visibility="gone" …match_parent/>` right after `ambient` (behind the hero name, the content and the
  glass, so tiles scrolled up pass over it).
- `MainActivity.java`: under roman: show `romanFrame`, `setStyle`; a layout listener on `header` sets
  `friezeTop = header.getBottom() + 30 dp` (only when it changes); the grid wrapper's `marginTop` becomes 62 dp
  (give the wrapper `android:id="@+id/gridFrame"`).

Tests (`MeanderTest`, `LaurelTest`):
- Meander: all segments are horizontal or vertical; consecutive points differ; no two non-adjacent segments
  touch or cross (O(n²) check on a 200 g wide band); every point within [top, top + 5g] and
  [left, left + width]; first point x = left, last point x = left + width; module count `floor(width/5g)`;
  equal margins left and right (± 0.001); width < 5g → only the flat line.
- Laurel: 18 leaves; left leaf i mirrors right leaf i around cx (|x − (2cx − x')| < 1e-3, same y, angle
  mirrored); no leaf centre at an angle in (255°, 285°) (the open top); every leaf tip within r + 8 dp of the
  centre; lengths shrink toward the top; 6 berries, mirrored.

Budget: APK ≤ 6 KB; memory < 0.1 MB (no bitmap; the crest is a Path, not a VectorDrawable).

Reviewer checks:
- No allocation in `onDraw` (Paths, Paints and Shaders are fields); `setLayerType` is never called.
- `setFriezeTop` with the same value does not invalidate (no layout loop with the listener).
- The frieze does not touch the header text or the pills on a long Wi-Fi name (`maxWidth` 180 dp) and a long
  Roman date (`XXVIII SEPTEMBRIS MMXXVIII`).
- The S Max dock, the hero name position and D-pad focus behave exactly as before (the frame is not focusable,
  `importantForAccessibility="no"`).
- With `roman_meander: false` and `roman_crest: false` only the slab and rules remain.

### R4. Fluted pilasters

**Goal:** the two pilasters from 1.5 framing the stage, from the frieze to the bottom.

Files:
- New `Pilaster.java` (pure): `static Pilaster layout(float shaftLeft, float top, float bottom, float w, float dp)`
  returns the rects of abacus, echinus, astragal line, shaft, torus, plinth and the 5 flute x positions with
  their top/bottom, or null when `bottom − top < 3w`.
- `RomanFrameView.java`: fill in `drawPilasters` for left (shaft left 14 dp) and right (shaft right edge at width −
  14 dp) using `Pilaster.layout` computed in `onSizeChanged`/`setFriezeTop`. Honour `roman_pilasters`.

Tests (`PilasterTest`):
- Capital 12 dp and base 12 dp tall; abacus and plinth w + 8 dp wide and centred on the shaft; shaft between
  them with no gap or overlap.
- 5 flutes, evenly spaced (step w/6 ± 1e-4), all strictly inside the shaft, starting 6 dp below the capital and
  ending 6 dp above the base.
- Too short (2.9w) → null; exactly 3w → not null.
- Right pilaster is the mirror of the left about the screen centre.

Budget: APK ≤ 3 KB; memory ≈ 0.

Reviewer checks:
- Pilasters stay clear of the focused tile and the glass: first tile left edge ≈ 72 dp; scaled tile plus glass
  reaches ≈ 57 dp; pilaster capital right edge ≈ 46 dp. Same on the right. Check at `columns` 4 and 5.
- No pilaster draws over the frieze; bases end 12 dp above the bottom edge.
- Still no layer and no allocation in `onDraw`.

### R5. Gold focus, lift, dock band, pills, icon tiles

**Goal:** the focus treatment of 1.6 and the dock band of 1.5.

Files:
- New `FocusGeometry.java` (pure): `static float[] glassRect(float cardLeft, float cardTop, float cardW,
  float cardH, float scale, float pad, float lift)` → {x, y, w, h}; `MainActivity.onTileFocused` uses it (with
  lift 0 the result must equal today's maths).
- `GlassView.java`: `setRoman(RomanStyle)` switches the colours of the glow, rim (gradient), fill and specular
  to 1.6; shaders are rebuilt in `onSizeChanged` as today. Default path unchanged.
- `AppAdapter.java`: `static float liftDp` (0, or 4 under roman, set from the constructor like `COLUMNS`); the
  Holder's focus animation adds `translationY(focused ? −lift : 0)` and `translationZ(focused ? 6 dp : 0)`; the
  same in `onBindViewHolder` for a rebound focused tile. Under roman the icon tile background is a
  `GradientDrawable` (`#2A221B`, stroke 1 dp gold at 35%, radius 16 dp) and `icon` is tinted gold light.
- `MainActivity.java`: `onTileFocused` passes the lift to `FocusGeometry` (the `requestChildRectangleOnScreen`
  rect gets the lift too); `glass.setRoman`; the pills get `RomanLook.pill(...)` (a `StateListDrawable` and a
  `ColorStateList` for text and `drawableTint`, Cinzel 14 sp).
- `DockDecoration.java`: optional `RomanStyle`; under roman the band is stone ink at 70%, radius 8 dp, no
  edge stroke, and two gold rules at the top and bottom (1 dp at 70% on the edge, 1 dp at 30% 3 dp inside,
  inset 8 dp from the band's ends).
- `RomanLook.java`: `pill(Context, TextView, RomanStyle)`.

Tests (`FocusGeometryTest`):
- With lift 0 the result equals the current formula in `onTileFocused` for several sizes (copy it into the test
  as the reference).
- With lift L the rect moves up by exactly L, same size.
- Scale 1.10 and pad 6 dp: width = cardW × 1.1 + 12 dp.

Budget: APK ≤ 4 KB; memory ≈ 0 (glass layer size unchanged; shadows are tessellated).

Reviewer checks:
- The glass sits on the lifted tile when it settles, after a scroll in the grid, and after returning from an app
  (the `focusTile → onTileFocused` path).
- A tile that loses focus returns to translationY 0 and Z 0 (no drift after fast D-pad presses: `animate().cancel()`
  already runs first).
- `reduced_motion: true` still has no overshoot.
- Pills: focused text readable (stone ink on gold), the VPN badge still fits in the Wi-Fi pill.
- With `style` unset: `GlassView`, `DockDecoration`, pills and tiles are identical to today (lift 0, no Z).

### R6. Integration, docs, size and memory check

**Goal:** the v2 look documented, the version bumped, the budgets confirmed with real numbers.

Files:
- `launcher/app/build.gradle`: versionCode 6, versionName `2.5`.
- `themes/README.md`: all keys of 3 with one-line meanings (R1 added the rows; reword if R2–R5 changed anything),
  a short "Roman look v2" paragraph and the projector advice (start at brightness 100, try 85–115).
- `docs/plans/xgimi-os-plan.md`: one line in section 5 pointing here.
- `launcher/app/src/main/res/values/config.xml` comments checked against the final behaviour.
- `README.md` credits: Cinzel, OFL 1.1, the licence file path.

Checks (outside Claude, record the results in `logs/R6.txt`):
- CI: `testReleaseUnitTest assembleRelease` green; release APK size, with the delta against 304 KB (≤ 330 KB
  expected, 345 KB stop).
- Device phase, **each line needs Shibi's OK**: install the debug APK; `dumpsys meminfo com.gathin.tvhome` at
  idle on Home, after 20 D-pad moves, and after Home is cold-started (`am kill` from another app): PSS < 60 MB;
  stone render time from logcat at first start; photos of the wall at night and in daylight (screencap does not
  work on this unit, plan risk R1).
- Tune on the wall with `config.json` only (no rebuild): `roman_stone_brightness`, `roman_vignette`,
  `roman_stone`.

Reviewer checks (whole v2):
- `style` unset on a generic config: no new class is loaded on the default path (`RomanStyle` null checks
  first), the default look is unchanged (compare against a pre-v2 photo or the code paths).
- Memory: one full-screen-sized bitmap at most, no full-screen layer, ambient 240x135 under roman.
- Licence: font and OFL file committed, credit in README.
- S Max theme and overlay: dock YouTube, Prime Video, HDMI 1; no Netflix.

---

## 6. Risks and open questions

- **Projector contrast** is the main design risk; that is why brightness and vignette are theme knobs and not
  constants. The contrast tests guard the header, not the stage.
- `copy(HARDWARE)` for RGB_565 should work on Android 11; if it returns null, the fallback costs about 1 MB
  more (still under 60 MB by the estimate). R6 measures it.
- First start renders the stone (estimated 0.3–1.5 s on the m7632, background thread, base colour shown
  meanwhile). After that every cold start reads 1 MB from the cache (≈ 10–20 ms).
- **Netflix:** the S Max theme and the debug overlay have none. The upstream default `dock_apps` in
  `res/values/config.xml` still starts with `com.netflix.ninja` for other TVs. v2 does not touch it; Shibi decides
  whether "no Netflix anywhere" should also change that upstream default.
- With 3 dock apps and `columns: 4`, the fourth dock slot holds the first app of the grid. Tiles do not grow
  beyond 180 dp at `columns: 3`, so "bigger tiles" would need a separate key; out of scope here.

## 7. Summary

1. v2 turns the S Max home into a Roman interior: procedural travertine wall with a baked vignette, a gilded Greek-key frieze broken by a laurel medallion (`SALVE`), fluted pilasters, Cinzel lettering, a gilded hero name, and the Roman date.
2. Focus stays Apple TV: the same 1.10 zoom plus a 4 dp lift with a real shadow, a gold-rimmed glow and gold pills. Nothing animates continuously.
3. Everything is behind `"style": "roman"` plus 8 `roman_*` knobs (off by default). It is turned on only in `themes/xgimi-s-max/config.json` and the local debug overlay; the dock stays YouTube, Prime Video, HDMI 1.
4. Budget: about +0.5 MB PSS (stone 960x540 RGB_565 as a hardware bitmap; the ambient bitmap shrinks to pay for it), at most +25 KB APK (≤ 330 KB), no raster assets, no layers.
5. Six slices (R1 keys and type, R2 stone, R3 frieze and crest, R4 pilasters, R5 focus and dock, R6 integration and measurement), each with pure-Java unit tests and explicit reviewer checks.

## 8. Roman screensaver (RomanDream)

A second `DreamService` next to `ClockDream` (which is untouched). It reuses the R1–R6 pieces: the palette in
`RomanStyle`, the cached stone from `StoneCache` (never re-rendered when cached; the same style as Home, or the
default travertine if `style` is not `roman`), `Laurel` for the wreath and the Cinzel face from `RomanLook`.

- Look: the stone drawn 12% larger than the screen and panned corner to corner by **one** `ValueAnimator`
  (linear, 90 s each way, reversing; no other animation), a vignette, a gold laurel (the R3 geometry at 1.7×),
  the time in Cinzel (96 dp, AM/PM small beside it), two thin gold rules and the inscribed Roman date
  (`XX OCTOBRIS MMXXVI`). The clock updates on `ACTION_TIME_TICK` (plus time and zone changes).
- Light: one bitmap, the shared 960x540 RGB_565 stone (no copy); no `WakeLock`; `setFullscreen(true)`,
  `setInteractive(false)`; the receiver, the animator and the image reference are released in
  `onDreamingStopped` and `onDetachedFromWindow`. The bitmap is dereferenced, **not recycled**, because it is
  the same object Home may be showing; recycling it would crash Home's next draw.
- Robustness: any failure loading the stone (or `roman_stone: "none"`) leaves the solid dark stone colour
  (`TRAVERTINE_DEEP`); a missing Cinzel falls back to the system font.
- Select it (document only, needs Shibi's OK before it is run on the projector):
  `settings put secure screensaver_components com.gathin.tvhome/.RomanDream`
  and `settings put secure screensaver_enabled 1` (the constant is `RomanDreamMath.COMPONENT`). The manifest entry is
  exported with the `android.service.dreams.DreamService` filter and `BIND_DREAM_SERVICE`, so it is also
  listed in the TV's screensaver settings. Back to the clock: `… com.gathin.tvhome/.ClockDream`.
- Tests: `RomanDreamMathTest` (pan travel and offset, ping-pong fraction, 12/24 h time, date line, manifest entry).

## 9. RomanDream v2

Status: **plan only.** Shibi liked v1. v2 keeps its skeleton (one shared stone, one overlay View, minute tick,
teardown from 2.1.1) and adds motion, a red palette, laurels at the sides, a legion standard, two date lines and
rolling quotes. The Home rules in 2.1 ("no continuous animation, no Handler loops") stay for Home; the dream is the
one place with a throttled frame loop.

### 9.1 Look (1920x1080; y as a fraction of the height h)

```
 0.05 ┌────────────────────────────────────────────────────────┐
      │ (  )             ┃  spear tip / small wreath           (  )│
      │ (  )      ═══════╋═══════  crossbar                    (  )│  laurel sprays
      │ (  )      ┃ crimson vexillum ┃  thunderbolt emblem       (  )│  left and right,
      │ (  )      ┃ LEG XII FVLMINATA┃  gold border, fringe       (  )│  x 4..12% / 88..96%
 0.40 │ (  )             ┃                                     (  )│
 0.60 │ (  )           10:45 PM            (Cinzel 96 dp)       (  )│
 0.64 │ (  )        ──────────────           two gold rules     (  )│
 0.70 │ (  )        XX OCTOBRIS MMXXVI      (Cinzel 22 dp gold)  (  )│
 0.75 │ (  )          20 October 2026       (Cinzel 14 dp ivory dim)│
 0.88 │      "They make a desert and call it peace." — Calgacus      │  quote, ≤ 2 lines
 1.00 └────────────────────────────────────────────────────────┘
```

- **Palette (dream only):** new constants in `RomanStyle`: `ROMAN_RED #9B1B1E`, `CRIMSON #B0202E`,
  `PORPHYRY #5E1A24`, `PORPHYRY_DEEP #2A0B10`; gold stays `style.gold/goldLight/goldDeep`. The stone is warmed
  toward red with `stone.setColorFilter(new LightingColorFilter(0xFFC07A6C, 0x00140000))`, set once (GPU, no new
  bitmap, works on the HARDWARE bitmap). Vignette ends in `PORPHYRY_DEEP` at `0xB0` instead of black. No stone
  (`none` or load failure) → `PORPHYRY_DEEP`. The code has no lavender colour; the cast on the wall is the
  projector rendering the dim grey-brown stone plus the black vignette, which the red tint and red vignette fix.
- **Decision, S Max Home:** stays gold on travertine. Its colours are contrast-tested over that stone (R2) and
  Shibi likes it; a red Home would need new contrast tests. Other TVs are unaffected: the dream already uses the
  launcher's `RomanStyle` or the default travertine, and the red lives only in dream code.
- **Laurels:** two vertical sprays, mirrored about the centre: a stem curving gently inward from 0.16h to
  0.86h, 13 leaves on alternating sides, 4 berries, the R3 leaf shape at 1.4×. One `Path` for all leaves of
  both sprays, gold gradient, relief shadow +2 dp, as v1. The centre wreath of v1 goes (the standard replaces it).
- **Legion standard (vexillum):** pole (gold, 3 dp) from 0.05h to 0.40h on the centre line, a spear tip at the
  top, a crossbar 200 dp wide, a crimson cloth 180x100 dp with a 1.5 dp gold border and a gold fringe (short
  vertical strokes) at the bottom. On the cloth: a **thunderbolt** (fulmen: a central spindle, four zigzag bolts,
  two small wings, all one gold `Path`) and the legend in Cinzel gold light, size fitted to the cloth width
  (shrink once in `onSizeChanged`, 10..14 dp). The legend comes from **one constant**
  `RomanDreamMath.LEGION = "LEG XII FVLMINATA"`. Shibi said "fifth"; the thunderbolt is the emblem of Legio XII
  Fulminata (the V legions are *Alaudae* and *Macedonica*), so the default is XII; change the constant (e.g.
  `"LEG V MACEDONICA"`) to switch. It is shown through `Roman.inscribe(LEGION, 24)`.
- **Dates:** the Roman line as v1 (`XX OCTOBRIS MMXXVI`), below it the plain localized Gregorian date from
  `java.text.DateFormat.getDateInstance(LONG, Locale.getDefault())` (en-GB: `20 October 2026`, en-US:
  `October 20, 2026`); both are recomputed on the minute tick only.
- **Time:** Cinzel 96 dp as v1, but the blurred `setShadowLayer` is replaced by a hard shadow (the same text in
  `TEXT_SHADOW` drawn +2 dp lower first). A blur is re-rasterised every composited frame on the Skia pipeline;
  at 16 fps that is the most expensive thing on screen.
- **Quotes:** `assets/quotes.json` (100 entries, `{"version":1,"quotes":[{"text","author","source"}]}`,
  fact-checked, ASCII only so Cinzel covers every glyph). Shown as `text — author` (U+2014 is in the Cinzel
  subset), ivory text, author in gold light, Cinzel 20 dp, at most 2 lines, centred at 0.88h, max width 70% of
  the screen. `source` is loaded and kept in code/docs, not drawn. One quote per 6 s: 0.8 s fade in, 4.4 s
  hold, 0.8 s fade out. Start index random once per dream, then sequential (all 100 before a repeat).

### 9.2 Motion and budgets

- **One frame loop:** a single `Choreographer.FrameCallback` field (no `ValueAnimator`, no per-frame lambda)
  re-posted with `postFrameCallbackDelayed(cb, FRAME_MS)`, `FRAME_MS = 60` (≈ 16 fps, 15–20 allowed). Each tick
  computes from `frameTimeNanos` only: stone `translationX/Y = offset(ease(pingPong(t, 71 s)), travelX)` and
  `… (pingPong(t, 47 s)) …, travelY` (two legs, so the wall drifts in a slow Lissajous, never a single
  diagonal; `ease` is smoothstep so the reversal is soft), and the quote line's alpha and index. Speed ≈ 3 px/s;
  no half-pixel snap any more (bilinear filtering makes sub-pixel steps smooth).
- Per frame only `RenderNode` properties change (`setTranslation*` on the stone, `setAlpha` on the quote View):
  no `invalidate()` of the overlay, no display-list re-record, **zero allocation**. `setText` runs once per quote
  change with a display string built at load time.
- **Memory:** still one bitmap (the shared stone, dereferenced not recycled); the quotes are ≈ 25 KB of heap,
  loaded once on `onAttachedToWindow` on the main thread (12 KB file, < 5 ms) and nulled in `teardown()`.
  Target PSS < 60 MB during the dream and 30 s after it (same three `dumpsys meminfo` readings as 2.1.1).
- **APK:** quotes.json 12.5 KB raw (≈ 6 KB deflated in the APK), DV1 code ≤ 6 KB, DV2 code ≤ 4 KB: **< 30 KB
  total even uncompressed**. Vector/Canvas only, no raster assets, no XML drawables.

### 9.3 Slices (Sonnet, one commit each, `DVn: <what> (Sonnet)`; rules of section 4 apply)

#### DV1. Red palette, laurels, legion standard, date lines

Files:
- `RomanStyle.java`: the four dream colours of 9.1.
- `RomanDreamMath.java`: `LEGION`; `static String gregorian(int y, int m, int d, Locale l)`; `static float
  fitTextSize(float maxWidth, float widthAtOnePx, float minPx, float maxPx)`.
- New `DreamOrnaments.java` (pure): `static Laurel.Leaf[] spray(float w, float h, float dp, boolean right)`,
  `static float[] sprayBerries(…)`, `static float[] fulmen(float cx, float cy, float size)` (polyline x,y pairs),
  `static float[] standard(float w, float h, float dp)` = {poleTop, poleBottom, barY, barHalf, clothL, clothT,
  clothR, clothB} (plain floats, no `RectF`, so it stays testable on the JVM).
- `RomanDream.java` (Overlay only): drop the centre wreath; draw sprays, standard, fulmen, legend, hard time
  shadow, Roman + Gregorian date lines; red vignette; `LightingColorFilter` on the stone; `PORPHYRY_DEEP`
  root colour. Everything built in `onSizeChanged`/`refresh()`, nothing allocated in `onDraw`.

Tests (`DreamOrnamentsTest`, additions to `RomanDreamMathTest`, `RomanStyleTest`):
- Each dream colour is opaque and red-dominant (`r > g && r > b`); `PORPHYRY_DEEP` darker than `PORPHYRY`.
- Spray: 13 leaves per side; right leaf i mirrors left leaf i about w/2 (1e-3); every leaf centre in x < 0.14w
  (left) and y in [0.14h, 0.88h]; no leaf inside the standard's or the time's box at 1920x1080 and 1280x720.
- Fulmen: symmetric about cx, every point inside the cloth rect; standard: cloth centred, pole bottom above the
  time's top at 1920x1080.
- `LEGION` equals `"LEG XII FVLMINATA"` and `Roman.inscribe(LEGION, 24)` returns it unchanged.
- `gregorian(2026, 10, 20, Locale.UK)` = `20 October 2026`; `Locale.US` = `October 20, 2026`.
- `fitTextSize` never exceeds maxWidth, clamps to [min, max].

Budget: APK ≤ 6 KB; memory ≈ 0 (Paths only).

Reviewer checks:
- Home (S Max and default config) unchanged: `git diff` touches no launcher view code, only `RomanStyle`
  constants and dream files.
- No allocation in `onDraw`; no `setLayerType`; no `setShadowLayer` left in the dream.
- Legend fits on the cloth for `"LEG XII FVLMINATA"` and a longer `"LEG XXII PRIMIGENIA"`.
- On the wall (Shibi's photo): red/porphyry, not lavender; laurels clear of the screen edge on the projector's
  overscan; the standard reads as a standard.

#### DV2. Rolling motion, quotes ticker, lifecycle

Files:
- New `Quotes.java` (pure): `static List<String[]> parse(String json)` — a small scanner for this flat format
  (string values with `\"` `\\` `\n` `\uXXXX` escapes; unknown keys ignored; malformed input → empty list, never
  an exception; `org.json` is not usable in JVM tests). `static String display(String[] q)` = `text + " — " + author`.
- New `QuoteClock.java` (pure): `PERIOD_MS = 6000`, `FADE_MS = 800`; `static int index(long t, int start, int n)`,
  `static float alpha(long t)`.
- `RomanDreamMath.java`: `FRAME_MS = 60`, `LEG_X_MS = 71_000`, `LEG_Y_MS = 47_000`, `static float ease(float)`;
  remove `PAN_MS`/`snap` use from the dream (keep the methods if tests still use them, else delete them with
  their tests).
- `RomanDream.java`: replace the `ValueAnimator` with the frame callback of 9.2; a quote `TextView` (Cinzel,
  2 lines, ellipsize end, not focusable); load the quotes from `getAssets()` in `onAttachedToWindow` (any
  failure → no quote line, dream goes on); start the loop in `onDreamingStarted`, stop it in `teardown()`
  (`removeFrameCallback`, null the quotes and the TextView, keep the `dead` guard for a late stone callback).

Tests (`QuotesTest`, `QuoteClockTest`, additions to `RomanDreamMathTest`):
- The real `src/main/assets/quotes.json` (read from disk): exactly 100, `version` 1, every text ≤ 110 chars and
  non-empty author and source, no duplicate texts, every char ASCII 0x20–0x7E.
- Parser: escapes decoded; extra key ignored; truncated JSON → empty list; `null` → empty list.
- QuoteClock: alpha 0 at t = 0, 1 from 800 to 5200 ms, 0 at 6000; monotonic in each fade; index steps once per
  6 s and wraps at n; start offset respected.
- `ease(0) = 0`, `ease(1) = 1`, `ease(0.5) = 0.5`, monotonic; offsets from both legs stay within ±travel;
  `FRAME_MS` in [50, 67].
- Source rule: `RomanDream.java` contains no `ValueAnimator` and no `setShadowLayer`.

Budget: APK ≤ 4 KB code + quotes.json (≈ 6 KB deflated); memory ≈ +25 KB heap while dreaming, 0 after.

Reviewer checks:
- `doFrame` allocates nothing (no `new`, no string concatenation, no boxing, no lambda) and calls no
  `invalidate()`; the callback is one field and is removed in every teardown path.
- After HOME ends the dream: no frame callback pending (`dumpsys gfxinfo` frame count stops), receiver gone,
  quotes nulled; the shared stone is not recycled.
- Device (each step needs Shibi's OK): PSS < 60 MB at 1 min into the dream, right after HOME, and 30 s later;
  motion visible but calm from the sofa; quotes readable at 3 m.

### 9.3.1 DM2: memory while running, shuffled quotes (Sonnet)

Measured total PSS of `com.gathin.tvhome` (launcher and dream share the process) before DM2: idle 52.8 MB,
**during** RomanDream v2 67.9 MB, right after 58.1 MB, 30 s after 48.2 MB. Target: during < 60 MB (aim ~56),
after unchanged or better. Not re-measured yet; repeat the readings on the device.

What changed (no device access, so these are the suspected causes, not proven ones):
- The panned stone was an `ImageView` scaled 1.12 with view-level alpha, scale and a colour filter. It is now
  `RomanDream.StoneView`: the shared bitmap drawn through one reused `Matrix` (scale + translate), colour filter
  and fade on the `Paint`. No view alpha or scale on a full-screen view, so no offscreen layer is possible, and no
  `ViewPropertyAnimator`. The fade-in runs in the existing frame loop.
- The quote `TextView` returns `hasOverlappingRendering() == false`, so its alpha fade is applied per draw op and
  does not allocate an offscreen layer every frame.
- Quotes are held as two small arrays (`String[]`, `int[]`) instead of 100 `SpannableString`s with spans; the gold
  author span is built once per quote change (every 6 s).
- Overlay (laurels, standard, text) was already Paths/Paints built in `onSizeChanged`, no layers: unchanged.
- If still over 60 MB, the remaining lever is the dream window's own buffers (1920x1080 x3); try an RGB_565
  window format, or a smaller `StoneView` render size.

Shuffled quotes: `QuoteOrder` (pure Java, injectable `Random`) gives a fresh shuffle on every dream start, each
quote once per pass, a reshuffle after a full pass, and never the same quote twice in a row (also across the pass
boundary). `QuoteClock.alpha` is still used for the fade; `QuoteClock.index` is no longer used by the dream.
Tests: `QuoteOrderTest`.

### 9.4 Summary

1. Quotes: 78 of Haiku's 87 candidates kept (with 14 sources or authors corrected), 22 well-attested additions, exactly 100 in `assets/quotes.json` (12.5 KB, ASCII only).
2. RomanDream v2: red/porphyry palette (dream only; S Max Home stays gold), laurels left and right, a vexillum with a thunderbolt and `LEGION = "LEG XII FVLMINATA"`, Roman + localized Gregorian dates, time in Cinzel.
3. Motion: one throttled Choreographer loop (≈ 16 fps) drifting the one shared stone and fading a quote every 6 s, zero per-frame allocation, PSS target < 60 MB during and after.
4. Two Sonnet slices: DV1 (palette, laurels, standard, dates, constant, tests) and DV2 (motion, quotes loader and ticker, lifecycle, tests); APK growth < 30 KB.
