# VIZIO VQM85C-1010 (85", VIZIO OS / SmartCast)

Device notes for Shibi's TV. Everything marked **Measured** was read from the TV over the LAN on
2026-10-25, between about 18:27 and 19:10 ET, from the Alienware laptop (Windows 11, Wi-Fi
10.0.0.197/24, gateway 10.0.0.1). Only read-only traffic was sent: ping, TCP connects, TLS
handshakes, HTTP `GET`s, SSDP `M-SEARCH`, mDNS queries, a reverse-DNS lookup through the router and a
NetBIOS status query. There was no pairing, no key press, no app launch, no `PUT`/`POST`, no
Wake-on-LAN packet and no settings change.

This repo is public, so device-unique values are withheld: the full serial number, MAC addresses
past the vendor prefix, the TV's IPv6 addresses, Cast/AirPlay/DIAL/Netflix device IDs, the
advertising ID, public keys and tokens. The raw captures stay on the Alienware in
`backup/vizio-dig1/` (gitignored).

"The prior scan" below means the earlier LAN scan recorded in the Dig 1 brief: TV candidate at
10.0.0.176, its MAC, and port 7345 seen.

Survey write-up and architecture options: [docs/plans/vizio-local-os-dig1.md](../plans/vizio-local-os-dig1.md).

**This is not an Android TV.** CleanYourTV's ADB debloat and launcher (`tools/`, `launcher/`) do not
apply to this panel. See [Not Android TV](#not-android-tv).

## Identity

| Item | Value | Source | Status |
|---|---|---|---|
| Model | `VQM85C-1010` | SmartCast API (`deviceinfo` `MODEL_NAME`, TV Information "Model Name"), AirPlay (`/info` `model`, mDNS TXT `model`), DIAL (`dd.xml` `modelName`) | Measured: three services agree |
| Manufacturer | `VIZIO Inc.` | AirPlay `manufacturer` and `integrator`; DIAL `manufacturer` "Vizio" | Measured |
| Screen size | 85 | `deviceinfo` `SYSTEM_INFO.SIZE: 85` | Measured |
| Mini LED panel | not reported | Walmart listing and registration only | Not measurable over the LAN |
| Purchase and registration | Walmart; registered on 2026-10-22 (account withheld) | Shibi's records | Not measurable over the LAN |
| Serial number | `MMVW…` (15 characters; full value withheld) | AirPlay `/info` `serialNumber` and the `_viziocast` mDNS TXT `esn` match the registration serial exactly. The SmartCast API (`SYSTEM_INFO.SERIAL_NUMBER` and TV Information "Serial Number") reports the same 15 characters with a two-character block moved to the front | Measured. The reordering is unexplained; treat the registration/AirPlay value as the serial of record (the back label was not read) |
| Firmware | `86.920.9.3-2` | TV Information "Firmware"; `SYSTEM_INFO.VERSION`; AirPlay `firmwareRevision`; AirPlay TXT `fv=p20.86.920.9.3-2` | Measured |
| Firmware build date | `Aug 11 2026` | AirPlay `/info` `firmwareBuildDate` | Measured (as AirPlay reports it) |
| VIZIO OS | `ViziOS 1.8.1768.1 68e92c0` | TV Information "VIZIOS"; `BINARIES.ViziOS` is `mtk5586-1.8.1768.1` | Measured |
| SmartCast platform / API | `3.7.3-2640.0001` | TV Information "SCPL Version"; `deviceinfo` `API_VERSION` | Measured |
| SmartCast config | `PROD v64.42` | TV Information "SC Config"; `SC_INFO: "prod 64.42"` | Measured |
| Google Cast | `mtk5586_sb-0.910.0.20\|cast_20250829_0200_RC10` | TV Information "Cast"; `eureka_info` `cast_build_revision` `1.68.cast_20250829_0200_RC10.800768591`, `release_track` `stable-channel` | Measured |
| AirPlay | SDK `AirPlay;3.72.210.0`, source version `377.40.00` | AirPlay `/info`; `Server: AirTunes/377.40.00` | Measured |
| Operating system | Linux | AirPlay `operatingSystem: "Linux"`; mDNS host name `LinuxTV.local`; SSDP `SERVER` headers `Linux/5.15.167-ab8692093` (Netflix's UPnP stack) and `Linux/2.6 UPnP/1.1 quick_ssdp/1.1` (the DIAL responder) | Measured strings. The two self-reported kernel strings disagree, so no kernel version is claimed |
| SoC | **Unknown** | Strings seen: `mtk5586` in the VIZIO OS and Cast build names; `mtk2024` in AirPlay's `webEngineVersion` (`15.3.0 (mtk2024-ffdc126e)`); `CN = MT5581.prod.vizio.com` on the port 7345 certificate, issued in 2016 by `dev.vizio.com`; `SYSTEM_INFO` `CHIPSET: 9` and `SOC: 1` (undocumented enums) | Inferred: a MediaTek platform, and the 2016 certificate is a fleet certificate rather than a per-model one. The part-number-like strings disagree, so no SoC is claimed |
| FCC ID | **Unknown** | Not reported over the LAN; no FCC records consulted | Unknown |
| Display | up to 3840×2160 at 60 fps; SDR, HDR10, HLG, Dolby Vision | AirPlay `/info` `displays` (`receiverHDRCapability: "4k60"`, Dolby Vision codec strings `dvhe.04.09`, `dvhe.05.09`). It also reports a current mode of 1920×1080 (`widthPixels`, `heightPixels`) | Measured for the AirPlay receiver. HDR support per HDMI port is Unknown |
| Picture modes listed | Bright, Calibrated, Calibrated Dark, Sports | `deviceinfo` `SYSTEM_PICTURE_MODES` | Measured (the API's list; the on-screen list may be longer) |
| Inputs | `hdmi1`, `hdmi2`, `hdmi3`, `comp`, `hwtuner`, `tuner`, `usb`, `cast`, `airplay`, `watchfree`, plus 15 HDMI-CEC device names | `deviceinfo` `INPUTS` | Measured list. Three HDMI inputs, composite and an over-the-air tuner are inferred from the names; see [HDMI-CEC](#hdmi-cec) |
| Bluetooth | driver `3.0.22.0`; `BT_REMOTE`, `BT_AUDIO_STREAMING`, `BT_GAMEPADS` true; `BLE` false | TV Information; `deviceinfo` `CAPABILITIES` | Measured flags |
| Device name | "Living Room" | SmartCast `CAST_NAME`, Cast `name`, AirPlay `name`, DIAL `friendlyName`, Netflix `X-Friendly-Name` (base64) | Measured |

## Network

| Item | Value | Status |
|---|---|---|
| IPv4 | 10.0.0.176 on 10.0.0.0/24, the same address as the prior scan. Whether the router reserves it was not checked (router admin page) | Measured |
| Link in use | Wi-Fi: `eureka_info` `ethernet_connected: false`; the laptop's ARP entry is on its Wi-Fi interface | Measured |
| Wi-Fi MAC | `20:1B:A5:…`, the same MAC as the prior scan (laptop ARP table `Reachable`, `_viziocast` TXT `wifi=`, DIAL `WAKEUP` header) | Measured |
| Ethernet | RJ45 port present (`CAPABILITIES.RJ45: true`), MAC `20:1B:A5:…` (`_viziocast` TXT `eth=`) | Measured |
| MAC vendor prefix `20:1B:A5` | Not looked up: no offline OUI database on this laptop and no web lookups in this session. Both MACs are universally administered (not randomized) | Unknown vendor |
| IPv6 | two global addresses and one link-local address, advertised over mDNS (values withheld) | Measured |
| Host name | `LinuxTV.local` over mDNS. The router's DNS has no reverse entry (`Resolve-DnsName 10.0.0.176`: `176.0.0.10.in-addr.arpa : DNS name does not exist`) and there is no NetBIOS name (`nbtstat -A 10.0.0.176`: `Host not found.`) | Measured |
| Ping | replies, TTL 64 | Measured |
| Ports with no service | some refuse the connection, some drop it silently; see [LAN services](#lan-services) | Measured (spot checks) |

## LAN services

Open TCP ports, from a full connect scan of 1–65535 plus targeted probes:

| Port | Service | Evidence | Answers without pairing |
|---|---|---|---|
| 7345 (HTTPS, HTTP/2) | SmartCast local API | `server: lighttpd/1.4.67`, `x-powered-by: V2SCP`; mDNS `_viziocast._tcp` SRV points here | `GET /state/device/deviceinfo`, `GET /state/device/power_mode` and the TV Information menu. `/app/current` and every other `menu_native` path tried outside the TV Information paths return `403` `REQUIRES_PAIRING`, made-up paths included. Missing paths under the TV Information prefixes return `URI_NOT_FOUND`; `/` returns `500` |
| 7000 | AirPlay receiver | `Server: AirTunes/377.40.00`; mDNS `_airplay._tcp` SRV points here | `GET /info` (binary plist) |
| 8008 (HTTP), 8443 (HTTPS) | Google Cast setup API | `GET /setup/eureka_info` returns JSON. The 8443 certificate is issued by `O=Google Inc, OU=Cast, CN=Cast TV ICA (Vizio 2025)` (leaf `OU=Cast TV (Vizio 2025)`, valid from 2025-08-18) | `GET /setup/eureka_info`. `GET /ssdp/device-desc.xml` is `404` |
| 8009 (TLS) | Cast v2 channel | self-signed certificate valid for 24 hours | TLS handshake only; nothing was sent |
| 9080 (HTTP) | Netflix NRDP / MDX | `Server: NRDP/2025.2.3.0-8005e55f285`; SSDP `ST: urn:mdx-netflix-com:service:target:3`. HTTPS on this port times out | `GET /` returns `status=ok` |
| 56790 | DIAL device description | SSDP `ST: urn:dial-multiscreen-org:service:dial:1`, `LOCATION: http://10.0.0.176:56790/dd.xml` | `GET /dd.xml` |
| 56789 | DIAL REST | `Application-URL: http://10.0.0.176:56789/apps/` | `GET /apps/Netflix` returns `dialVer="2.2"`, `<state>stopped</state>`, `allowStop="true"`. `GET /apps/YouTube` returns `403 Forbidden` |

No other port accepted a connection. The full scan (1–65535, 64 connections at a time, 1.5 s
timeout, 18:29 to 18:55 ET) ended with exactly these eight open; an open port answers in about
20 ms, so the timeout is unlikely to hide open ports. It does hide the difference between a refused
port and a dropped one: this Windows laptop reports a refusal only after about 2.1 s. Spot checks
with a 3–6 s timeout:

- Refused (TCP reset, nothing listening): 5555 (ADB), 6466 and 6467 (Android TV Remote), 7001,
  7346, 7347, 8000–8002, 8004–8007, 9000 (the older SmartCast port).
- Dropped (no answer within 6 s): 21, 22 (SSH), 23 (Telnet), 80, 443.

The `_viziocast` TXT record advertises `hp=7346`, `wp=8005` and `wsp=8006` next to `hsp=7345`;
nothing was listening on those three.

UDP: the TV answers SSDP `M-SEARCH` (Netflix MDX root device on 9080, DIAL on 56790) and
legacy-unicast mDNS queries (service types `_viziocast._tcp` and `_airplay._tcp` only).

### SmartCast API without pairing

```
$ curl -sk https://10.0.0.176:7345/state/device/power_mode
{"STATUS":{"RESULT":"SUCCESS","DETAIL":"Success"},"URI":"/state/device/power_mode","ITEMS":[{"NAME":"Power Mode","CNAME":"power_mode","TYPE":"T_VALUE_V1","VALUE":1}]}

$ curl -sk https://10.0.0.176:7345/app/current
{"STATUS":{"RESULT":"REQUIRES_PAIRING","DETAIL":"Pairing is required"}}          (HTTP 403)

$ curl -sk https://10.0.0.176:7345/menu_native/dynamic/tv_settings/zz_dig1_nonexistent
{"STATUS":{"RESULT":"REQUIRES_PAIRING","DETAIL":"Pairing is required"}}          (HTTP 403)
```

`REQUIRES_PAIRING` comes back for made-up paths too, so it says nothing about whether a settings
path exists. The TV Information menu is readable without pairing at
`/menu_native/dynamic/tv_settings/admin_and_privacy/system_information/tv_information`; the older
`system/system_information/...` path (still the first choice in pyvizio 0.1.64) returns
`URI_NOT_FOUND` on this firmware. At about 18:32 ET that menu showed input `SMARTCAST`, 1080p,
30 fps, audio in AAC, audio out PCM, HDR `NO`, VRR `NO`; `power_mode` read `1` (on) at 18:30.

### What `deviceinfo` reports

`GET /state/device/deviceinfo` returns about 4 KB of JSON without pairing. Selected fields, grouped
by why they matter here. The meanings of the flags are inferred from their names; VIZIO does not
document them.

| Topic | Fields |
|---|---|
| App runtimes | `BINARIES`: `COBALT` `25.lts.20.1034877` (YouTube), `appletv` `17.0.0.19`, `conjure` `128.1.8`, `chromium-libs` `1.1.0`, `castshell` `0.910.0.20`, `vzairplayd` `35.920.7-d375bd2-35X`, `home` `26.08.28.9`, `loki` `1.11.3-build.4`, `matter` `1.1.1` (nothing answered mDNS queries for the Matter service types). TV Information shows "Conjure" as `N/A` |
| Control | `YT_DEEPLINK_SUPPORT: true`, `BootToSC: true`, `SCTVInputsSupported: true`, `TUNER_OTA: true`, `MOBILE_VOICE_CAPABLE: true`, `SOFT_AP: false`; `BINARIES.netwakeupmonitor` `0.0.1` |
| Ads and tracking | `BINARIES`: `hal-acr` `4.1.0157`, `recommendationd`, `collectd`, `s3logger`, `eventanalyzer`, `avod`, `watchfree-helper`, `watchfree-plus-loki-bundle`. `CAPABILITIES`: `ITV_CAPABLE`, `ITV_DYNAMIC_TRIGGER_CAPABLE`, `ITV_OTT_CAPABLE`, `ITV_OPT_OUT`, `PROMO_CAPABLE`, `VIZIO_HOME_ONETRUST` all `true`; `EA_SENTRY` (a Sentry DSN on `o401343.ingest.us.sentry.io`, key withheld, `TracesSampleRate: 1`); `GeoLocation: "/system/dynamic/iptolocation"`. `SCPL_CAPABILITIES.itv_campaign_manager`. `SYSTEM_INFO.TVAD_ID`: `IFA_TYPE: "sessionid"`, `LMT: 1` (ID withheld) |
| Debug | `BINARIES.sshd` `1.0` and `perfetto` `39.0` are listed. Port 22 is dropped from the LAN |
| Other, meaning unknown | `CAPABILITIES.HDR: [0]`; `SYSTEM_INFO` `SERIES: "V"`, `SUBSERIES: "V"`, `MODEL_GROUP: 302`, `MODEL_TYPE: 10`, `PANEL_TYPE: 0` |

The Cast setup API adds `opt_in: {"crash": true, "stats": true}` and `tos_accepted: false`.

## HDMI-CEC

The LAN cannot exercise CEC; it is a wire protocol on the HDMI cables. What the LAN does show:
after `usb`, `cast` and `watchfree`, `deviceinfo` `INPUTS` lists "TV", "Recorder 1", "Recorder 2",
"Tuner 1", "Player 1", "Audio System", "Tuner 2", "Tuner 3", "Player 2", "Recorder 3",
"Tuner 4", "Player 3", "Device 1", "Device 2", "Device 3". The first twelve are the HDMI-CEC
logical address names for addresses 0 to 11, in order, and "Device 1" to "Device 3" sit where
addresses 12 to 14 (reserved and specific use in the spec) would be. That mapping is general
knowledge of the CEC spec, not re-checked in this session.

- Measured: those 15 names, in that order, in the input list.
- Inferred: VIZIO OS models CEC devices as inputs.
- Unknown: whether CEC is switched on, and how one-touch play, standby and volume control behave.
  Needs an HDMI-CEC device on one of the ports.

## Security notes

- Anything on the LAN can read this TV's identifiers without pairing: the serial from `deviceinfo`,
  the TV Information menu and AirPlay `/info`; the serial, both MACs and the device ID from the
  `_viziocast` mDNS record; the advertising ID from `deviceinfo`. That is how the platform behaves,
  not something this survey opened up.
- `deviceinfo` sends `access-control-allow-origin: *`. What keeps web pages out is the port 7345
  certificate: it does not verify (openssl `Verify return code: 21`), it names
  `MT5581.prod.vizio.com` rather than the IP, and it has no subjectAltName, so a browser shows a
  certificate warning. Use `curl -k`, and don't click through that warning for
  `https://10.0.0.176:7345` in a browser used every day.
- The survey was IPv4 only, and the TV has global IPv6 addresses. Check in the router's admin page
  that unsolicited inbound IPv6 is blocked. That check sends nothing to the TV.

## Not Android TV

- No ADB: 5555 refuses connections, no other port is open for it, and nothing answers mDNS
  `_adb._tcp` or `_adb-tls-connect._tcp`.
- No Android TV Remote service: 6466 and 6467 refuse connections; nothing answers
  `_androidtvremote2._tcp`.
- The platform names itself: `ViziOS 1.8.1768.1`, SmartCast `SCPL 3.7.3`, AirPlay
  `operatingSystem: "Linux"`, host name `LinuxTV.local`.
- The component list (`BINARIES`) names VIZIO and web runtimes (`conjure`, `chromium-libs`,
  `COBALT`, `castshell`, `loki`). Nothing in it is Android-specific (no `adbd`, ART or
  `system_server`); `perfetto` is listed, but it also runs on plain Linux.

None of `PROMPT.md`'s steps (ADB, `pm disable-user`, launcher APK as HOME) has anything to connect to
on this panel: nothing reachable offers a shell or a package manager.

## Re-running the checks

All of these are read-only. Anything that changes state is outside Dig 1: `PUT /pairing/start` puts
a PIN on the TV's screen (per pyvizio), `PUT /key_command/` presses remote keys, a `PUT` to
`/menu_native/…` or `/app/launch` changes settings or apps, DIAL `POST` launches apps and DIAL
`DELETE` stops them, any non-`GET` to the Cast setup API's `/setup/*` on 8008/8443 can reach Cast's
reboot and factory-reset calls (general knowledge of that API, not re-checked), and a Wake-on-LAN
packet may turn the TV on (untested). Don't send them without Shibi's OK.

The output of these commands contains the serial, MACs and device IDs that this doc withholds. Keep it
under `backup/vizio-dig1/` and redact it before pasting it into the repo, an issue or a PR.

PowerShell:

```powershell
ping -n 4 10.0.0.176
Get-NetNeighbor -IPAddress 10.0.0.176 | Format-Table IPAddress, LinkLayerAddress, State, InterfaceAlias -AutoSize
Test-NetConnection 10.0.0.176 -Port 7345        # TcpTestSucceeded : True
Test-NetConnection 10.0.0.176 -Port 5555        # False (ADB)
Test-NetConnection 10.0.0.176 -Port 22          # False (SSH)
Resolve-DnsName 10.0.0.176
nbtstat -A 10.0.0.176
```

Git Bash, from the repo root. The `openssl` line needs Git Bash as written; for the `curl` lines in
Windows PowerShell 5.1, type `curl.exe`, because plain `curl` there is an alias for
`Invoke-WebRequest`:

```bash
openssl s_client -connect 10.0.0.176:7345 </dev/null | openssl x509 -noout -subject -issuer -dates -ext subjectAltName
curl -sk https://10.0.0.176:7345/state/device/deviceinfo
curl -sk https://10.0.0.176:7345/state/device/power_mode
curl -sk https://10.0.0.176:7345/menu_native/dynamic/tv_settings/admin_and_privacy/system_information/tv_information
curl -sk -i https://10.0.0.176:7345/app/current             # 403 REQUIRES_PAIRING
curl -s http://10.0.0.176:8008/setup/eureka_info
curl -s --create-dirs http://10.0.0.176:7000/info -o backup/vizio-dig1/airplay-info.plist   # binary plist
curl -s -i http://10.0.0.176:9080/
curl -s -i http://10.0.0.176:56790/dd.xml
curl -s -i http://10.0.0.176:56789/apps/Netflix
```

SSDP: send `M-SEARCH * HTTP/1.1` to 239.255.255.250:1900 with `HOST: 239.255.255.250:1900`,
`MAN: "ssdp:discover"`, `MX: 2` and `ST: ssdp:all`, then `urn:dial-multiscreen-org:service:dial:1`
and `upnp:rootdevice`. mDNS: send a PTR query for `_services._dns-sd._udp.local` from an ephemeral
port, so the TV answers by unicast. The survey sent each query both to 224.0.0.251:5353 and straight
to 10.0.0.176:5353, so which copy the TV answered is not known. Multicast queries sent from port 5353 got no answer from anyone, the TV
included; the only packets that came back were the laptop's own queries, looped back. So that test
is inconclusive on this laptop.
