# Plan: VIZIO local OS, Dig 1 (capability survey + architecture A/B/C)

Status: **Dig 1 only: survey and docs, waiting for Shibi's review.** Nothing on the TV was changed.
Branch: `opus/vizio-dig1`. Do not merge without Shibi's OK. Per the standing rule in
`WORK_INSTRUCTIONS.md` and the brief, anything past Dig 1 waits for a new explicit go.
Brief: `prompts/VIZIO_LOCAL_OS_DIG1.txt` (local, untracked). Device facts:
[docs/devices/vizio-vqm85c-1010.md](../devices/vizio-vqm85c-1010.md).

## 0. Summary

- **The panel is what the brief says.** The TV itself reports model `VQM85C-1010`, firmware
  `86.920.9.3-2`, `ViziOS 1.8.1768.1` and `operatingSystem: "Linux"`. It is not an Android TV.
- **What it exposes on the LAN:** the SmartCast local API on 7345 (device state is readable without
  pairing; the current-app read and the settings reads tried, apart from TV Information, answer
  `REQUIRES_PAIRING`; no control call was made), Google Cast (8008/8009/8443), AirPlay (7000), DIAL with Netflix (56789/56790), Netflix MDX
  (9080), and a Wake-on-LAN hint in DIAL.
- **What it does not expose:** ADB, SSH, Telnet, any Android TV service, any shell or package manager.
  CleanYourTV's debloat and HOME replacement have nothing to run against on this panel.
- **Ranking by feasibility on this panel: B > C > A.**
- **Recommended Dig 2: path B.** Prove the panel side of "HDMI Android TV box as the OS" (CEC,
  power-on input, HDMI HDR, VIZIO privacy settings) and choose the box. Observation only on the TV.

## 1. How the survey was done

- From the Alienware (Windows 11, Wi-Fi 10.0.0.197/24, gateway 10.0.0.1) on 2026-10-25, about
  18:27 to 19:10 ET. The TV was on (`power_mode` 1), on its `SMARTCAST` input, i.e. VIZIO's home or
  one of its apps rather than an HDMI input.
- Sent: ping, ARP lookups, TCP connects (no payload), TLS handshakes, HTTP `GET`s, SSDP `M-SEARCH`,
  mDNS queries, a reverse-DNS lookup through the router, a NetBIOS status query, and a full TCP
  connect scan of 1–65535 (64 connections at a time, 1.5 s timeout).
- Deliberately not sent: `PUT /pairing/start` (per pyvizio it puts a PIN on the TV's screen), key presses, app
  launches, any `PUT`/`POST`, DIAL `POST`, Wake-on-LAN packets, settings writes, `adb connect`.
- Web search and fetch were not available in this session. Where an inference leans on outside
  knowledge it says so: either pyvizio 0.1.64 (installed on the Alienware, source read locally), the
  repo's own `examples/`, or general protocol knowledge, marked "general knowledge, not re-checked".
- Raw captures: `backup/vizio-dig1/` on the Alienware (gitignored; they contain the serial, MACs and
  device IDs, which this public repo leaves out).

## 2. Capability survey

### 2.1 Measured

Lines starting with `>` ran in PowerShell, lines starting with `$` in Git Bash. Responses are trimmed
with `…`; device-unique values are withheld.

**Facts from the prior scan recorded in the brief (TV at 10.0.0.176, its MAC, port 7345 seen),
re-checked from this machine:**

```
> ping -n 4 10.0.0.176
Reply from 10.0.0.176: bytes=32 time=7ms TTL=64
…
    Packets: Sent = 4, Received = 4, Lost = 0 (0% loss),

> Get-NetNeighbor -IPAddress 10.0.0.176 | Format-Table IPAddress, LinkLayerAddress, State, InterfaceAlias -AutoSize
IPAddress  LinkLayerAddress      State InterfaceAlias
10.0.0.176 20-1B-A5-…        Reachable Wi-Fi                (same MAC as the prior scan)

> Test-NetConnection 10.0.0.176 -Port 7345 | Select-Object ComputerName, RemotePort, TcpTestSucceeded
ComputerName     : 10.0.0.176
RemotePort       : 7345
TcpTestSucceeded : True

> Resolve-DnsName 10.0.0.176
176.0.0.10.in-addr.arpa : DNS name does not exist
```

**Open TCP ports:** exactly eight in the full 1–65535 scan: 7000, 7345, 8008, 8009, 8443, 9080, 56789,
56790. The scan's 1.5 s timeout cannot separate refused ports from dropped ones (this Windows laptop
reports a refusal after about 2.1 s), so spot checks used 3–6 s; see the device doc's
[LAN services](../devices/vizio-vqm85c-1010.md#lan-services).

**SmartCast local API (7345).** HTTPS with HTTP/2, `server: lighttpd/1.4.67`, `x-powered-by: V2SCP`,
advertised as mDNS `_viziocast._tcp`. Certificate: `CN = MT5581.prod.vizio.com`, issued by
`CN = dev.vizio.com` (VIZIO Inc), valid 2016-04-12 to 2036-04-07.

```
$ curl -sk https://10.0.0.176:7345/state/device/deviceinfo            (HTTP 200, 4047 bytes; trimmed, IDs withheld)
{"STATUS":{"RESULT":"SUCCESS","DETAIL":"Success"},"URI":"/state/device/deviceinfo","ITEMS":[{"NAME":"VIZIO Device Info",
 "VALUE":{"MODEL_NAME":"VQM85C-1010","SETTINGS_ROOT":"tv_settings","API_VERSION":"3.7.3-2640.0001","CAST_NAME":"Living Room",
 "INPUTS":["hwtuner","comp","hdmi1","hdmi2","hdmi3","usb","cast","watchfree","TV","Recorder 1", … ,"Device 3","airplay","tuner"],
 "CAPABILITIES":{ … "BootToSC":true, … "ITV_CAPABLE":true, … "ITV_OPT_OUT":true, … "PROMO_CAPABLE":true, … "RJ45":true, …},
 "BINARIES":{"COBALT":"25.lts.20.1034877","ViziOS":"mtk5586-1.8.1768.1","appletv":"17.0.0.19", … ,"hal-acr":"4.1.0157", … ,"sshd":"1.0", …},
 "SYSTEM_INFO":{ … "MODEL_NAME":"VQM85C-1010", … "SIZE":85, … "VERSION":"86.920.9.3-2", … "TVAD_ID":{"IFA":"<withheld>","IFA_TYPE":"sessionid","LMT":1}, …}}}]}

$ curl -sk https://10.0.0.176:7345/menu_native/dynamic/tv_settings/admin_and_privacy/system_information/tv_information
… {"CNAME":"model_name", … "VALUE":"VQM85C-1010"}, {"CNAME":"firmware", … "VALUE":"86.920.9.3-2"},
  {"CNAME":"vizios", … "VALUE":"ViziOS 1.8.1768.1 68e92c0"}, {"CNAME":"input", … "VALUE":"SMARTCAST"}, …     (HTTP 200)

$ curl -sk https://10.0.0.176:7345/state/device/power_mode
{"STATUS":{"RESULT":"SUCCESS","DETAIL":"Success"},"URI":"/state/device/power_mode","ITEMS":[{"NAME":"Power Mode","CNAME":"power_mode","TYPE":"T_VALUE_V1","VALUE":1}]}

$ curl -sk https://10.0.0.176:7345/app/current
{"STATUS":{"RESULT":"REQUIRES_PAIRING","DETAIL":"Pairing is required"}}                      (HTTP 403)
```

`/menu_native/dynamic/tv_settings` and every sub-path tried outside the TV Information paths
(`system`, `system/cec`, `admin_and_privacy`, `admin_and_privacy/viewing_data`,
`devices/current_input`, `picture`, `audio`, `network`, …) answer the same `403 REQUIRES_PAIRING`,
and so does a made-up path (`zz_dig1_nonexistent`). So these answers do **not** show that a CEC or
Viewing Data path exists. Under the TV Information paths, missing items return `URI_NOT_FOUND`
instead. pyvizio 0.1.64's `get_version()` tries `system/…/tv_information/version` and then
`admin_and_privacy/…/tv_information/version`. Both return `URI_NOT_FOUND` here: the first because the
menu moved under `admin_and_privacy`, the second because this firmware calls the item `firmware`.
pyvizio would need adapting for this TV.

**Google Cast.**

```
$ curl -s http://10.0.0.176:8008/setup/eureka_info                     (same on https://…:8443; IDs withheld)
{"build_version":"800768591","cast_build_revision":"1.68.cast_20250829_0200_RC10.800768591","connected":true,
 "ethernet_connected":false,"has_update":false, … ,"name":"Living Room","opt_in":{"crash":true,"stats":true},
 "release_track":"stable-channel","setup_state":63, … ,"tos_accepted":false, … ,"version":10}

$ openssl s_client -connect 10.0.0.176:8443 </dev/null | openssl x509 -noout -subject -issuer
subject=C = US, ST = California, L = Mountain View, O = Google Inc, OU = Cast TV (Vizio 2025), CN = <withheld>
issuer=C = US, ST = California, L = Mountain View, O = Google Inc, OU = Cast, CN = Cast TV ICA (Vizio 2025)
```

Port 8009 completes a TLS handshake (self-signed, 24-hour certificate): the Cast v2 channel.

**AirPlay.**

```
$ curl -s -i http://10.0.0.176:7000/info
HTTP/1.1 200 OK
Content-Type: application/x-apple-binary-plist
Server: AirTunes/377.40.00
decoded: model "VQM85C-1010", manufacturer "VIZIO Inc.", firmwareRevision "86.920.9.3-2", firmwareBuildDate "Aug 11 2026",
         operatingSystem "Linux", serialNumber <withheld; equals the registration serial>,
         displays: widthPixelsMax 3840, heightPixelsMax 2160, maxFPS 60, HDR modes SDR / HDR / HDR10 / DoVi / HLG
```

**DIAL and Netflix.**

```
SSDP M-SEARCH, ST: urn:dial-multiscreen-org:service:dial:1  →  reply from 10.0.0.176:
  LOCATION: http://10.0.0.176:56790/dd.xml
  SERVER: Linux/2.6 UPnP/1.1 quick_ssdp/1.1
  WAKEUP: MAC=20:1B:A5:…;Timeout=30

$ curl -s -i http://10.0.0.176:56790/dd.xml
Application-URL: http://10.0.0.176:56789/apps/
… <deviceType>urn:schemas-upnp-org:device:tvdevice:1</deviceType> … <manufacturer>Vizio</manufacturer><modelName>VQM85C-1010</modelName> …

$ curl -s http://10.0.0.176:56789/apps/Netflix
<service xmlns="urn:dial-multiscreen-org:schemas:dial" dialVer="2.2"><name>Netflix</name><options allowStop="true"/><state>stopped</state> …

$ curl -s -i http://10.0.0.176:56789/apps/YouTube
HTTP/1.1 403 Forbidden

$ curl -s -i http://10.0.0.176:9080/
HTTP/1.1 200 OK
Server: NRDP/2025.2.3.0-8005e55f285
status=ok
```

SSDP also returns a Netflix MDX root device (`X-User-Agent: NRDP MDX`,
`ST: urn:mdx-netflix-com:service:target:3`, `LOCATION: http://10.0.0.176:9080`,
`SERVER: Linux/5.15.167-ab8692093, UPnP/1.0, Portable SDK for UPnP devices`).

**mDNS** (legacy-unicast queries): service types `_viziocast._tcp` (SRV 7345; TXT
`hp=7346 hsp=7345 wp=8005 wsp=8006`, plus IDs, both MACs and the serial, withheld) and
`_airplay._tcp` (SRV 7000). Host `LinuxTV.local` with one IPv4 and three IPv6 addresses. No answers
for `_googlecast._tcp`, `_adb._tcp`, `_adb-tls-connect._tcp`, `_androidtvremote2._tcp`, `_ssh._tcp`,
`_hap._tcp`, `_matter._tcp`, `_matterc._udp`, `_raop._tcp`, `_spotify-connect._tcp`,
`_companion-link._tcp`.

**Debug and Android surfaces:**

```
> Test-NetConnection 10.0.0.176 -Port 5555 | Select-Object ComputerName, RemotePort, TcpTestSucceeded
ComputerName     : 10.0.0.176
RemotePort       : 5555                                     (ADB)
TcpTestSucceeded : False
> Test-NetConnection 10.0.0.176 -Port 22 | Select-Object ComputerName, RemotePort, TcpTestSucceeded
ComputerName     : 10.0.0.176
RemotePort       : 22                                       (SSH)
TcpTestSucceeded : False
```

With a 6 s timeout, 5555, 6466/6467 (Android TV Remote) and 9000 (older SmartCast port) refuse the
connection (nothing listening), while 22, 23 (Telnet), 80 and 443 get no answer at all (dropped).
`deviceinfo` lists an `sshd` component, but nothing listens for it on the LAN.

**HDMI-CEC, as far as the LAN can see:** after `usb`, `cast` and `watchfree`, `INPUTS` lists "TV",
"Recorder 1", "Recorder 2", "Tuner 1", "Player 1", "Audio System", "Tuner 2", "Tuner 3", "Player 2",
"Recorder 3", "Tuner 4", "Player 3", "Device 1", "Device 2", "Device 3".

**Ads and tracking components the TV lists:** `hal-acr`, `recommendationd`, `collectd`, `s3logger`,
`eventanalyzer`, `avod`, WatchFree+ bundles; flags `ITV_CAPABLE`, `ITV_DYNAMIC_TRIGGER_CAPABLE`,
`ITV_OTT_CAPABLE`, `ITV_OPT_OUT`, `PROMO_CAPABLE`, `VIZIO_HOME_ONETRUST`; a Sentry DSN with
`TracesSampleRate: 1`; `TVAD_ID` with `IFA_TYPE: "sessionid"` and `LMT: 1`; Cast
`opt_in: {"crash": true, "stats": true}`.

### 2.2 Inferred

| # | Inference | Based on |
|---|---|---|
| I1 | VIZIO OS on Linux, with no ADB or shell route in the shipping state | §2.1: platform strings, no ADB/SSH/Telnet ports, no Android mDNS services |
| I2 | A paired client can press remote keys, switch inputs, launch apps, read the current app and input, and read and change settings | pyvizio 0.1.64 does all of these against `/key_command/`, `/menu_native/dynamic/tv_settings/devices/current_input`, `/app/launch`, `/app/current` and `/menu_native/dynamic/tv_settings`, after a PIN pairing (`PUT /pairing/start`, PIN shown on the TV, `PUT /pairing/pair` returns a token sent as the `AUTH` header). This TV's `403 REQUIRES_PAIRING` on the three of those paths it read (`/app/current`, `devices/current_input`, `tv_settings`) is consistent with that, but is no evidence for it: made-up paths get the same answer. `/key_command/` and `/app/launch` were not touched. **Not exercised on this panel** |
| I3 | VIZIO's home is a web page started like any app | pyvizio defines SmartCast Home (`APP_HOME`) as `NAME_SPACE 4`, `APP_ID "1"`, `MESSAGE "http://127.0.0.1:12345/scfs/sctv/main.html"`. In its bundled fallback app list, most entries are an `APP_ID` with no `MESSAGE`; a few pass a URL (AsianCrush, Vudu's cast-receiver page, WatchFree+ on the same local `main.html`). Fits the measured `home`, `loki`, `conjure`, `chromium-libs` components. Port 12345 is not reachable from the LAN |
| I4 | No overlay surface open to a LAN client | Cast, AirPlay, DIAL and `/app/launch` each change the foreground app. The `ITV_*` flags suggest VIZIO has its own overlay path for interactive-TV ads, but nothing measured exposes it to us |
| I5 | VIZIO OS models HDMI-CEC devices as inputs | the first 12 of those 15 `INPUTS` names are the HDMI-CEC logical address names for addresses 0 to 11, in order, and "Device 1" to "Device 3" sit at 12 to 14 (general knowledge of the CEC spec, not re-checked) |
| I6 | The TV starts into VIZIO's home | the flag name `BootToSC: true` only |
| I7 | The TV can be woken over the network | DIAL `WAKEUP: MAC=…;Timeout=30` (the DIAL wake-up header; general knowledge, not re-checked) and a `netwakeupmonitor` component. Not tested |
| I8 | ACR viewing-data software is installed; interactive-TV ads exist with an opt-out; ad tracking already looks limited | `hal-acr`; `ITV_*` and `ITV_OPT_OUT`; `LMT: 1` ("limit ad tracking" in the IAB's CTV ID guidelines; general knowledge, not re-checked). Whether Viewing Data is on is unknown |
| I9 | The panel can show 4K60 with HDR10, HLG and Dolby Vision | AirPlay `displays`. That is the AirPlay path, not a per-HDMI-port measurement |
| I10 | The Admin & Privacy menu exists on this firmware | the readable TV Information path sits under `admin_and_privacy/system_information` |
| I11 | A paired `/app/launch` may start a Google Cast receiver app directly | pyvizio's two `NAME_SPACE 0` entries pass a Cast app ID plus a Cast message (`urn:x-cast:tv.pluto`, `urn:x-cast:com.google.cast.media`). Untested |

### 2.3 Unknown

| Unknown | How to find out | Needs |
|---|---|---|
| SoC part number | Not needed by any option. Leave it | — |
| FCC ID | Label on the back of the TV. Not needed by Dig 2 | Shibi, optional |
| Whether PIN pairing works on this firmware, and exactly what it unlocks | Pair from the Alienware | Shibi at the TV to read the PIN; its own go |
| Whether `/app/launch` opens an arbitrary LAN URL, and whether remote keys reach that page | Try after pairing | Pairing; its own go |
| Whether port 7345 answers while the TV is in standby (`power_mode` was only read with the TV on) | Read `power_mode` with the TV in standby | Shibi's OK; read-only |
| Whether a custom Cast receiver page runs here and gets remote keys | Google Cast developer registration, then try | Its own go |
| CEC state and behaviour: one-touch play, standby, volume | The CEC setting on screen (menu location unverified); an HDMI-CEC device on a port | Shibi; a CEC device |
| Power-on behaviour (VIZIO home or last input) and any setting for it | Power on from standby and watch; look through the menus | Shibi |
| HDMI port features (eARC port; 4K120, HDR, Dolby Vision per port) | Input settings on screen; back-panel labels | Shibi |
| Viewing Data, ad and interactive-TV settings and their state | Admin & Privacy on screen | Shibi |
| Whether VIZIO shows ads or overlays on an HDMI input | Watch with a box on HDMI | B, Dig 2 |
| How VIZIO OS behaves with no network | Disconnect Wi-Fi briefly (reversible) | Shibi's OK |
| Whether Google Cast is announced over mDNS | A multicast mDNS browse from a phone or Linux host. On this Windows laptop, multicast queries from port 5353 got no answer from anyone (only its own queries looped back), so that test is inconclusive | — |
| Vendor of MAC prefix 20:1B:A5 | OUI lookup | Web access |
| Whether the listed `sshd` is ever reachable (service or factory modes) | Not pursued. Out of scope and risky | — |

## 3. Architecture options

Goals, from `WORK_INSTRUCTIONS.md`: **G1** an Apple TV-style home (home screen, poster rows, floating
selection glow); **G2** keep core TV functions (settings, apps); **G3** strip adware, bloat and
tracking. The brief adds: only after proving a path that works on this panel.

### A. Soft layer: SmartCast API + cast companion / overlay from a local server (no Home replace)

**How it would work.** A local server (the Alienware or a small always-on box) pairs once with the
SmartCast API and hosts an Apple TV-style web UI. It puts that UI on the TV as a Cast receiver page or
through `/app/launch` with a URL (or a Cast app ID, I11), and turns choices into API calls (launch
app, switch input). In its strongest form the server watches `power_mode`, which is readable without
pairing (read only while the TV was on; standby is Unknown), and opens its UI each time the TV turns
on.

**For:** the API is live, and the reads tried beyond device state and TV Information need pairing
(measured; no control call was made); the pairing flow is documented by pyvizio (I2); Cast, AirPlay and DIAL are present
(measured); network wake is advertised (I7).

**Against:**
- The "overlay" half has no mechanism open to us: nothing measured can draw over another app (I4).
- The remote's Home and app buttons still go to VIZIO's UI (inferred: nothing measured lets a LAN
  client rebind them). The custom UI is one more app the TV can leave at any moment, and an
  auto-reopen trick races VIZIO's own home on every power-on.
- The on-panel UI depends on two Unknowns: URL launch, and remote keys reaching the page. A Cast
  receiver page needs a Google Cast developer registration (general knowledge, not re-checked).
- No package control at all (measured), so G3 is limited to VIZIO's own settings plus DNS filtering on
  the LAN. DNS filtering is fragile here: the TV has global IPv6 addresses (measured), so an IPv4-only
  filter can be bypassed, and blocking the wrong host breaks apps.
- Needs an always-on server, and a firmware update could invalidate the pairing (Unknown).
- The pairing token (the `AUTH` header) gives full control of the TV (per pyvizio, I2). It has to
  stay out of this public repo (gitignored local config), and the server must stay LAN-only.

**Goals:** G1 partial at best and Unknown-dependent; G2 yes; G3 weak.

### B. HDMI Android TV box as the real OS; the VIZIO as the panel

**How it would work.** An Android TV or Google TV box on one HDMI port runs this repo's existing flow:
`PROMPT.md`'s backup, debloat in batches, and `launcher/` as HOME. The VIZIO keeps picture, sound,
the tuner and its own settings. Later, optionally, the VIZIO comes off the network.

**For:**
- The panel side needs HDMI inputs (`hdmi1` to `hdmi3` in the TV's input list, measured) and 4K HDR
  over HDMI (AirPlay reports 4K60 HDR10/HLG/Dolby Vision, I9; per HDMI port it is Unknown). CEC is
  modelled (I5).
- The OS side is the repo's home ground. `examples/` records the launcher taking over HOME on five
  external boxes: Chromecast with Google TV 4K, Xiaomi Mi Box S, NVIDIA SHIELD TV Pro 2019, and two
  marked provisional (short first reports): Xiaomi Mi Box S 2nd gen and Xiaomi TV Box S 3rd gen
  (`xiaomi-mitv-afmu0`). These are contributor reports, not tests on this panel, and each box's
  quirks are written down (on Google TV, the `setupwraith` setup wizard has to be disabled along with
  the stock launcher). Fire TV is debloat-only (protected launcher).
- G3 is real on the box (tested debloat lists and a restore script), and on the panel there are two
  plain levers: VIZIO's privacy settings, or no network at all.

**Against:**
- A box has to be bought. That decision is Shibi's.
- Two remotes unless CEC works (Unknown).
- The VIZIO may power up into its own home (I6) unless CEC one-touch play or a setting sends it to the
  HDMI input (Unknown).
- With Viewing Data on, VIZIO's ACR can still see what the box shows: `hal-acr` is installed (I8), and
  VIZIO's ACR covering external inputs is general knowledge from public reporting, not re-checked.
  Viewing Data off, or the panel offline, deals with it.
- The box brings its own Google or vendor tracking, which is what the repo's debloat lists address.
- HDR and Dolby Vision per HDMI port: Unknown.

**Goals:** G1 yes (existing launcher); G2 yes (VIZIO settings and tuner untouched, apps on the box);
G3 strong on the box, and on the panel through settings or going offline.

### C. Hybrid: SmartCast control + selective content (defer Home replace)

**How it would work.** Pair once. A companion (a web page on the phone or the Alienware) handles
power, input, volume and app launch through the API, and sends chosen content by Cast, AirPlay or
DIAL. VIZIO's home stays, and there is no TV-side UI work.

**For:** the same measured API and casting surfaces as A, without A's dependence on URL launch or an
overlay.

**Against:** the Apple TV look lives on the phone or PC, not on the TV. G3 is the same as A: settings
plus DNS. Pairing is still Unknown until tried, and the pairing token needs the same care as in A.

**Goals:** G1 off the TV only; G2 yes; G3 weak.

### Ranking

| Rank | Option | Feasibility on this panel | Why |
|---|---|---|---|
| 1 | **B** | High | Rests on a measured panel fact (HDMI inputs in the input list) and one inference (4K HDR over HDMI, I9). The OS side reuses tooling the repo records on five boxes (two provisional). The panel Unknowns (CEC, power-on input, whether VIZIO's ads or ACR reach the HDMI input) affect convenience and the panel half of G3, not whether it works |
| 2 | **C** | High if pairing works (Unknown); covers the goals least | Depends only on the documented PIN pairing, still to be tried. Gives control and casting, but no TV-side home and no debloat |
| 3 | **A** | Low | Its distinctive parts (overlay, on-panel home) rest on Unknowns, the remote's Home button still leads to VIZIO's UI (inferred), and it gives no debloat |

B and C combine well later: the box's launcher could use a paired SmartCast API to switch the VIZIO's
inputs or tuner from its Inputs pill. That is not part of Dig 2.

## 4. Recommended Dig 2 scope (one path: B)

**Goal:** show on this panel that a box-as-OS setup behaves, and choose the box. Observation only on
the VIZIO; no package or settings changes anywhere without Shibi's OK.

1. **On-screen facts from Shibi** (phone photos are fine; nothing gets changed): the CEC setting (on
   or off, device list; its menu location on this firmware is unverified); power-on behaviour from
   standby (VIZIO home or last input) and any start-up input setting; Admin & Privacy (Viewing Data,
   ad and interactive-TV settings, current values); which HDMI port is eARC/ARC and which ports offer
   4K120, HDR or "Full UHD Color" style options.
2. **CEC smoke test** with an HDMI-CEC device Shibi already owns, if there is one (console,
   soundbar, stick): one-touch play from standby, standby following, volume. Observation only.
3. **Box choice, by Shibi.** Criteria from this survey: Android TV or Google TV with developer options
   and ADB; HDMI-CEC; 4K HDR, with Dolby Vision if wanted (the panel shows it over AirPlay). A model
   already in `examples/` lowers the risk: Chromecast with Google TV 4K, SHIELD TV Pro 2019, Mi Box S
   2nd gen, Xiaomi TV Box S 3rd gen. Availability was not checked (the Chromecast with Google TV is
   reportedly discontinued; general knowledge, not re-checked).
4. **With the box on HDMI, read-only checks only.** Turning on the box's Developer options and ADB
   debugging is a settings change on the box, so it needs Shibi's OK. Then, on the box: `PROMPT.md`'s
   "Back up first" step, the HOME claimants (`cmd package query-activities -a
   android.intent.action.MAIN -c android.intent.category.HOME`) and the install-verification settings.
   With the VIZIO: CEC behaviour, 4K/HDR on the chosen port, and whether VIZIO overlays or ads appear
   on the HDMI input. The TV Information read that works without pairing reports the input,
   resolution and HDR state, so it can back up what Shibi sees.
5. **Write `docs/plans/vizio-local-os-dig2.md`** with the measured results and the box's build plan
   (debloat batches, launcher config), for a separate go.

**Dig 2 is done when** each of these has a recorded answer:

- Box: chosen by Shibi, or a decision not to buy one.
- One-touch play: from standby, waking the box turns the VIZIO on and switches it to that HDMI input.
  Yes or no.
- Standby: putting the box to sleep puts the VIZIO in standby. Yes or no.
- Volume: the box remote's volume keys change the TV's (or soundbar's) volume over CEC. Yes or no.
- Power-on: in three power-ons from standby, the unpaired TV Information read shows the box's HDMI
  input and Shibi's photo shows no VIZIO home or overlay. Yes or no.
- 4K HDR: while the box plays HDR10, the same read shows a 2160p `resolution` and an `hdr` value other
  than `NO`. Yes or no.
- These fields have only been seen on the `SMARTCAST` input. If the read doesn't reflect the HDMI
  signal, Shibi's on-screen info (the TV's own input and signal display) is the record instead.
- HOME readiness: the box's HOME claimants and install-verification settings are recorded. Whether it
  accepts the launcher APK stays inferred until an install, which is not in Dig 2.
- `docs/plans/vizio-local-os-dig2.md` is written.

If Shibi decides against a box, Dig 2 stops after steps 1 and 2 and reports the panel-side facts; the
box steps wait.

**Not in Dig 2:** SmartCast pairing, URL-launch tests, DNS filtering, taking the panel offline, any
debloat or launcher install, any VIZIO settings change. Each needs its own go.

## 5. Questions for Shibi

1. Is buying an Android TV or Google TV box OK? Any preference?
2. Which HDMI-CEC devices are plugged into the TV now (for step 2)?
3. Later, is it OK to change VIZIO's privacy settings (Viewing Data off and so on)? Dig 2 only reads
   them.
4. Once a box is in, should the VIZIO stay online (its apps, Cast, AirPlay, firmware updates) or go
   offline as a plain panel?
5. The full serial number and MACs are kept out of this public repo. OK to keep it that way?
