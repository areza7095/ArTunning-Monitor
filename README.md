# ArTunning Monitor

**English** · [Bahasa Indonesia](README.id.md)

[![License](https://img.shields.io/github/license/areza7095/ArTunning-Monitor)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%20%7C%20iOS-166534)](android/)
[![Protocol%20tests](https://img.shields.io/badge/protocol%20tests-40%20%C3%97%202%20platform-D4A373)](docs/en/juken5-protocol-spec.md)

**Live-monitor the ECU of a BRT Juken 5 Plus injection motorcycle from your phone** — Android over *Bluetooth SPP*, iOS over *WiFi TCP* — plus an **open ECU protocol specification** so you can build your own client (ESP, web, tooling).

```
Connect  → 1B00/1B01 (handshake)  →  auto-live: 1617 → 160A  →  A603;…  →  gauges live
```

## About

A lightweight live monitoring app for the **Bintang Racing Team Juken 5 Plus** ECU:

- **Live gauges** for RPM, TPS, AFR, EOT, MAP, IAT, BATT, injection current — scaled exactly per the real protocol.
- **Auto-live** — once the connection is OK (`1B01` reply OR 1.5 s fallback), live mode starts
  with no taps and the Live tab opens by itself.
- **Debug console** — raw TX/RX log + manual frame sender (protocol validation tool against the ECU).
- **Auto-reconnect** with backoff (2/5/10/30 s) + `1B00` keepalive every 3 s.
- **NomadKit design system** (exact light + derived dark), every status is icon + text.
- **40 protocol unit tests × 2 platforms** (JVM & Linux) from the same golden vectors.

> ⚠️ **Disclaimer** — Personal project, **not affiliated with Bintang Racing Team**.
> "Juken", "Bintang Racing Team", and related marks belong to their respective owners;
> names here are used only as factual references. The protocol documentation was produced by
> black-box analysis of the official desktop application for **interoperability** purposes,
> without copying anyone else's code. Not an official BRT product — using it on a vehicle
> is at your own risk.

## Status

| Target | Status |
|---|---|
| **Android** (Bluetooth SPP) | ✅ APK tested directly against the ECU · 40/40 tests |
| **iOS** (WiFi TCP) | ✅ Installed on iPhone via [xtool](https://github.com/xtool-org/xtool) (free Apple ID) · 40/40 tests |
| **Protocol specification** | ✅ Complete + machine-readable `opcodes.json` |
| **ESP / bridge** | 🕜 coming — reuse the same `docs/opcodes.json` |

## Monorepo structure

```
docs/                          # SHARED SOURCE OF TRUTH across targets
├─ en/                         # English docs (default)
│  ├─ juken5-protocol-spec.md  # ECU protocol spec (IL evidence included)
│  └─ install.md               # build & install Windows -> Android / iPhone
├─ id/                         # Bahasa Indonesia docs
│  ├─ juken5-protocol-spec.md
│  └─ install.md
├─ opcodes.json                # 151 opcodes, struct layout, timing, transport (language-neutral)
├─ README.md · README.id.md    # docs index (EN / ID)
└─ design/nomadkit.md          # NomadKit design system (DESIGN.md, MIT)

android/                       # Kotlin + Jetpack Compose (Bluetooth SPP)
├─ core/protocol/              # pure-JVM protocol + Opcodes.kt codegen + unit tests
├─ transport-bt/               # Bluetooth byte pipe (knows the connection, NOT the protocol)
└─ app/                        # Compose UI (Connect / Live / Console)

ios/                           # SwiftUI (WiFi TCP — iOS has no BT SPP)
├─ Sources/ArTunningMonitor/   # Shared (protocol) + App (SwiftUI, #if os(iOS) guards)
├─ Tests/                      # XCTest — mirror of the 40 Android tests
├─ README.md · README.id.md    # iOS build guide (EN / ID)
├─ Package.swift · xtool.yml   # SwiftPM + xtool (build without a Mac)
└─ tools/setup-relay.ps1       # relay usbmux iTunes <-> WSL

esp/                           # (coming) ESP target
```

**Conventions:**
1. `docs/` = source of truth — code is *generated*/ported from the documents, not the other way around.
2. New target = new subfolder with its own build; never copy code between targets.
3. Transport is separate from protocol (add WiFi/another transport without touching the protocol).

## Install

Full details (prerequisites + troubleshooting) live in [`docs/en/install.md`](docs/en/install.md).

### Windows — build + install to Android

```powershell
cd android
.\gradlew.bat :core:protocol:test        # protocol unit tests (no phone needed)
.\gradlew.bat :app:assembleDebug         # APK -> app\build\outputs\apk\debug\
F:\Android_Studio\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
```

Prerequisites: Android SDK (`android/local.properties`) + JDK. Plug in the phone →
`adb devices` must list it (status `device`, not `unauthorized`).

### Android — install only (APK already built)

```powershell
F:\Android_Studio\platform-tools\adb.exe install -r android\app\build\outputs\apk\debug\app-debug.apk
```

### iOS — build + install via WSL (no Mac required)

```bash
wsl -d Ubuntu-24.04 -u root
cd /mnt/c/Users/<username>/<path>/ArTunning-Monitor/ios
swift test    # unit tests (Linux, no device)
xtool dev     # build + sign (Apple ID) + install to iPhone
```

Full WSL/xtool setup and the "Preparing device" requirements are in [`ios/README.md`](ios/README.md).
Free Apple ID = profile valid for **7 days** → refresh = run `xtool dev` again.

**Validate against the ECU (Android):** allow Bluetooth → **Connect** tab → pick the BT
module → **Console** tab: `TX 1B00` → `RX 1B01` → live starts automatically → Live tab → gauges up.

iOS note: transport = **ECU WiFi** (join the AP → TCP `192.168.2.6:80`) because iOS does
not support Bluetooth SPP.

## Protocol on one screen

Line-based ASCII frames, TX = `<HEX>;<param>\r\n` (HEX = opcode `0x1601` → `"1601"`):

| Part | Key values |
|---|---|
| Handshake | send `1B00` → reply `1B01` (keepalive every 3 s, watchdog 17 s) |
| Enter live | `1617` then `160A` every 500 ms (max 25×) → frame `A603;…` |
| Telemetry `A603` | `TPS;BAT;CORE;RPM;EOT×10;AFR;MAP×10;IAT×10;PUMP×1000;INJ×1000` |
| Bulk map/param/log | markers `#B1#`/`#B2#`/`#B3#` … `*eE*\r\n`, sizes 1012 / 10248 / 28 bytes |
| Integrity | **STM32-style CRC32** — init `0xFFFFFFFF`, poly `0x04C11DB7`, little-endian words |
| Opcode | Read `0x16xx` · Write `0x26xx/0x36xx` · Bulk `0x15xx/0x95xx` · Reply `0x96xx/0xA6xx` |

Full document (including **per-claim IL evidence**, byte-level struct layout, dispatch tables):
[`docs/en/juken5-protocol-spec.md`](docs/en/juken5-protocol-spec.md) ·
machine-readable: [`docs/opcodes.json`](docs/opcodes.json) — this is what feeds the codegen for
`Opcodes.kt` (Android) and `Opcodes.swift` (iOS); the future ESP target will use it too.

## Design system

Palette & rules from [NomadKit](docs/design/nomadkit.md) (DESIGN.md, MIT, by @chef):
Sand `#D4A373` (primary action) · Ocean `#0891B2` (secondary) · Forest `#166534` (success) ·
Background `#FFFDF7` — plus the rule: never rely on color alone, always prepare a skeleton state.

## Roadmap

- [x] Reverse-engineered ECU protocol + evidence-backed specification
- [x] Android app (Bluetooth SPP) — live monitor
- [x] iOS app (WiFi TCP) — live monitor
- [x] Android home-screen widget (Glance) + metric configuration
- [x] Android Auto (Media Session — telemetry as now-playing)
- [x] iOS widget (WidgetKit + App Groups via xtool)
- [ ] CarPlay — **blocked by design** (see below)
- [ ] ESP target (bridge/gauge) from `docs/opcodes.json`
- [ ] Read/write maps & data logger (bulk + CRC) inside the apps

## Widgets, Android Auto & CarPlay

### Android widget (Glance)

The ECU connection is held by a **foreground service** (`EcUService`, type `connectedDevice`)
+ persistent "Connected to ECU" notification. The widget reads a snapshot from
`EcURepository` (StateFlow) — it does **not** open its own connection.

- Add the widget from the home screen → multi-select metric configuration screen
  (RPM, TPS, AFR, EOT, MAP, IAT, BATT, PUMP, INJ).
- Updates ~1 s (throttled to the service notification); staleness is explicit
  ("updated N s ago").
- Metric choice is **global** (all widget instances share it) — MVP.

### Android Auto (media category)

`EcUMediaService` (MediaBrowserServiceCompat + MediaSessionCompat):
live telemetry is published as a **now-playing track**
(title = the selected metric row, NomadKit tile artwork).

**Test without a car (Desktop Head Unit):**

1. Install [Android Auto](https://play.google.com/store/apps/details?id=com.google.android.projection.gearhead)
   on the phone + [Desktop Head Unit](https://developer.android.com/training/cars/downloads)
   (needs Java; run `desktop-head-unit.jar`).
2. Plug the phone into the PC (USB) → enable **Unknown sources** in the Android Auto
   developer settings (in DHU: Start head unit → menu ⋮ → Settings → Developer →
   Unknown sources).
3. Open the app → Connect to the ECU → open DHU → the app appears under Media →
   "now playing" = live telemetry.
4. Phone: Android 9+ (API 28); app minSdk 26 — fine.

### iOS widget (WidgetKit + App Group)

`TelemetryStore` keeps snapshots in App Group
`group.id.arvoid.artunningmonitor` (UserDefaults suite).
The app writes on every `A603` frame (1 s throttle) + `WidgetCenter.reloadTimelines`.
Widget `ArTunningWidget` (small/medium) shows the selected metrics + staleness.

Build & install via `xtool dev` — xtool **creates & assigns the App Group automatically**
during signing (free-account support; same as the bundle prefix `XTL-xxxx.`).
Entitlements: `ios/App.entitlements` (app) + `ios/Support/ArTunningWidget.entitlements`.

### CarPlay — requires an Apple grant (not possible with a free account)

Based on [Requesting CarPlay Entitlements](https://developer.apple.com/contact/carplay/)
(Apple's official document):

- You must **request the entitlement** via CarPlay Contact Us and sign the
  *CarPlay Entitlement Addendum* → Apple reviews every app.
- The entitlement is a managed capability on a developer account → requires a
  **paid Apple Developer** membership plus passing review.
- Available entitlement categories: `carplay-audio`, `carplay-communication`,
  `carplay-maps` (navigation), `carplay-parking`, `carplay-charging`,
  `carplay-quick-ordering` — there is **no** dashboard/telemetry/gauge category.
- The CarPlay Simulator only works for apps that **already** hold the entitlement.

Conclusion: **not a code problem, an Apple permission problem.** No CarPlay code will be written.
If review ever passes, the technical requirements (CP templates + `entitlementsPath`)
are already available in xtool.

## License

**Copyright (c) 2026 Ahmad Reza A.** Licensed under
[CC BY-NC 4.0](LICENSE) — *Attribution-NonCommercial*.

- **Allowed**: share, fork, modify, learn — **for non-commercial purposes** —
  as long as the copyright notice `©2026 Ahmad Reza A` remains and the license is referenced.
- **Not allowed without permission**: **commercial** use (sold, paid services, etc.)
  — contact the owner: [@areza7095](https://github.com/areza7095).
- See [LICENSE](LICENSE) for the full official text.

## Credits

- [NomadKit](docs/design/nomadkit.md) design system by **@chef** (DESIGN.md, MIT).
- [xtool](https://github.com/xtool-org/xtool) — build & install iOS from Windows/Linux (MIT).
- Protocol documentation: black-box analysis of the official BRT desktop app
  (method + evidence in [`docs/en/juken5-protocol-spec.md`](docs/en/juken5-protocol-spec.md)).

<!-- Media: drop screenshots in docs/media/ (Connect / Live / Console) then embed here:
![Live](docs/media/live.png)
-->
