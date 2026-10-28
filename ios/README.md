# ArTunning Monitor — iOS (xtool, no Mac required)

**English** · [Bahasa Indonesia](README.id.md)

The iOS target of this monorepo. Stack: **SwiftUI + a Swift port of the protocol**
(`docs/opcodes.json` as the source of truth — same as Android).
Build & install use **[xtool](https://github.com/xtool-org/xtool)** in **WSL** —
no Mac required at all.

## Important limitations (read first)

- **iOS does not support Bluetooth SPP** (only BLE / MFi) — so the iOS app uses the
  **ECU WiFi path**: the iPhone joins the ECU WiFi AP, then **TCP `192.168.2.6:80`**
  (the endpoint can be changed on the Connect screen; there is an auto-join SSID/password button).
- The protocol over TCP is **exactly** the serial/BT path (`1B00`/`1B01`, `1617`/`160A`,
  telemetry `A603` — see `docs/en/juken5-protocol-spec.md`).
- **Auto-live**: as soon as the connection is up (trigger `1B01` OR the 1.5 s fallback), the app
  enters live mode automatically → the Live tab opens by itself (same as Android).

## Structure

```
ios/
├─ Package.swift            # SwiftPM (ArTunningMonitor library + ArTunningWidget + tests)
├─ xtool.yml                # bundleID + infoPath + entitlementsPath + extensions (widget)
├─ Info.plist               # merge-only keys: NSLocalNetworkUsageDescription, CFBundleDisplayName
├─ App.entitlements         # App Groups (group.id.arvoid.artunningmonitor) for the widget
├─ Support/
│   ├─ ArTunningWidget-Info.plist   # NSExtensionPointIdentifier = widgetkit-extension
│   └─ ArTunningWidget.entitlements # App Groups (same as the app)
├─ Sources/ArTunningMonitor/
│   ├─ Shared/              # protocol port (FrameCodec, LineSplitter, ReplyDispatch,
│   │                       #   TelemetryParser, Crc32Stm32, LiveSession, Opcodes*generated,
│   │                       #   Metrics, TelemetryStore)
│   └─ App/                 # SwiftUI: NetClient, AppViewModel, Connect/Live/Console, theme
│                           #   (every App/ file is wrapped in #if os(iOS) so tests run on Linux)
├─ Sources/ArTunningWidget/
│   └─ Widget.swift         # WidgetKit: TelemetryWidget (small/medium), reads TelemetryStore
├─ Tests/ArTunningMonitorTests/
│   ├─ ProtocolTests.swift  # 40 XCTest (Android mirror)
│   └─ MetricsStoreTests.swift # MetricSet/MetricFormatter/TelemetryStore
├─ scripts/gen-opcodes.mjs  # docs/opcodes.json -> Sources/.../Opcodes.swift
└─ tools/setup-relay.ps1    # usbmux iTunes (Windows) <-> WSL relay - run when the WSL IP changes
```

## Environment (already installed on this machine — reproduction notes)

What was already run once on this machine:

1. **WSL distro**: `wsl --install -d Ubuntu-24.04 --no-launch`
2. **Deps + Swift 6.4 + xtool** (in WSL, as root):
   - `apt install build-essential libfuse2t64 libimobiledevice-utils` + dev libs (curl/icu/xml/ncurses/z3/dll)
   - Swift 6.4 → `https://download.swift.org/swift-6.4.0-release/ubuntu2404/swift-6.4.0-RELEASE/swift-6.4.0-RELEASE-ubuntu24.04.tar.gz` → extract to `/opt/swift` + `PATH` in `/etc/profile.d/swift.sh`
   - `xtool` → `https://github.com/xtool-org/xtool/releases/latest/download/xtool-x86_64.AppImage` → `/usr/local/bin/xtool`
3. **xtool login (once)** — requires your Apple ID:
   ```
   wsl -d Ubuntu-24.04 -u root
   xtool setup
   # mode 1 (Password, free Apple ID) -> email -> password -> 2FA
   # Path to Xcode.xip: /mnt/<drive>/Xcode_27.1.xip
   # (download "Xcode 27.1.xip" from https://developer.apple.com/download/all/?q=Xcode)
   # verify: swift sdk list -> darwin
   ```
   > Setup produces iPhoneOS27.1.sdk + iPhoneSimulator27.1.sdk + MacOSX27.0.sdk.
4. **Device relay** (once; repeat whenever the WSL IP changes / after a Windows reboot) —
   because the iPhone connection is served by **iTunes on Windows** (Apple Mobile Device Service),
   not usbipd:
   ```powershell
   powershell -ExecutionPolicy Bypass -File ios\tools\setup-relay.ps1
   ```
   (creates a firewall rule + `netsh portproxy 27015 -> 127.0.0.1:27015`;
   `USBMUXD_SOCKET_ADDRESS` is already set automatically in `~/.bashrc` in WSL)

## Build & test

```bash
wsl -d Ubuntu-24.04 -u root
cd /mnt/c/Users/Administrator/Documents/Project/personal/xsr_juken_5/ios

# protocol unit tests (no device, no login) — must be 50/50:
swift test

# build iOS + sign (Apple ID) + install to iPhone:
xtool dev
```

**`xtool dev` prerequisites:** iPhone data cable → Windows, on the iPhone screen:
1. Tap **Trust** (first time),
2. Settings → Privacy & Security → **Developer Mode** → ON **then restart the iPhone**
   (the toggle only appears after a dev-signed app exists / has been prompted; after enabling
   a reboot is REQUIRED for it to actually take effect),
3. After the first install: Settings → General → VPN & Device Management → **Trust** the certificate.

**"Preparing device" requirements (xtool issue #164 — proven on this machine):**
- **iTunes must be INSTALLED and OPEN** with the iPhone info page visible
  (recipe from issue comments #19/#164) — the Microsoft Store version is fine;
- **3uTools (or similar tools) must be closed** — do not run them side by side;
- Developer Mode active (after restart) + phone screen unlocked.
Without these, the install stops at `[Preparing device] Error: noDevice`.

Notes:
- The first SwiftPM build creates the iOS module (takes minutes); afterwards it's fast (~6 s).
- If it stops at `Waiting for device to be connected...` = phone not plugged in / not trusted.
- The build output can be inspected at `ios/xtool/ArTunningMonitor.app` (verified merged Info.plist:
  `NSLocalNetworkUsageDescription` + `CFBundleDisplayName = ArTunning Monitor` +
  `CFBundleIdentifier = id.arvoid.artunningmonitor` + `MinimumOSVersion 15.0`).
- `.bsp/xtool.json` is created automatically by xtool for IDE support (gitignored).

### Signing & refresh (free Apple ID)

`xtool dev` registers the device and creates a Certificate + App ID + Provisioning Profile
automatically via Apple Developer Services (no web console). Free account = profile
**valid for 7 days** → **refresh = run `xtool dev` again** (or the app expires on day 8).
The signed bundle ID can get an `XTL-xxxx.` prefix from xtool (normal behavior
for free accounts).

## ECU test checklist (before the bike)

1. iPhone **joins the ECU WiFi AP** (WiFi Settings, or the Join WiFi form in the app).
2. **Connect** tab → enter IP `192.168.2.6`, port `80` → **Connect** → allow
   *Local Network* when iOS asks.
3. **Console** tab: repeated `TX 1B00` → **`RX 1B01`** = handshake OK.
4. Live starts **automatically** (trigger `1B01` or the 1.5 s fallback): `TX 1617`/`TX 160A`
   → **`RX A603;…`** → the Live tab opens by itself → gauges up.
   Press **Stop Live** for manual mode (**Start Live** to start again).
5. WiFi dropped for >17 s → status **Reconnecting** (backoff 2/5/10/30 s).

## Codegen

`Sources/ArTunningMonitor/Shared/Opcodes.swift` is generated from `docs/opcodes.json`
— **do not edit by hand**:

```bash
node scripts/gen-opcodes.mjs
# or: node scripts/gen-opcodes.mjs <path-json> <path-swift>
```

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `ERROR: No device found!` / `AFCClient...muxError` | iPhone not plugged in / not trusted, OR the relay isn't running → run `ios/tools/setup-relay.ps1` then check `ideviceinfo` |
| `xtool dev` stuck at `Waiting for device...` | Plug in phone + Trust + Developer Mode |
| xtool asks for login again | `xtool setup` incomplete / config lost — repeat the Setup steps |
| Connect fails from the app | iOS *Local Network* not allowed → Settings → Privacy → Local Network |
| Always "connection failed" | Make sure the iPhone is on the ECU AP (not home internet); wrong IP/port |
| Empty gauges | Check Console: no `RX 1B01` → validate first with the desktop app |
| App expired (7 days) | Run `xtool dev` again (re-sign + install) |
| AppImage won't run | `libfuse2t64` not installed, or run the AppImage with `--appimage-extract` |
