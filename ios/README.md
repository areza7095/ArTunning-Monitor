# ArTunning Monitor — iOS (xtool, tanpa Mac)

Target iOS dari monorepo. Stack: **SwiftUI + port Swift** dari protokol
(`docs/opcodes.json` sebagai source of truth — sama dengan Android).
Build & install memakai **[xtool](https://github.com/xtool-org/xtool)** di **WSL** —
tidak butuh Mac sama sekali.

## Batasan penting (baca dulu)

- **iOS tidak mendukung Bluetooth SPP** (hanya BLE / MFi) — jadi app iOS memakai
  **jalur WiFi ECU**: iPhone join AP WiFi ECU, lalu **TCP `192.168.2.6:80`**
  (endpoint bisa diubah di layar Connect, ada tombol auto-join SSID/password).
- Protokol di atas TCP = **persis** jalur serial/BT (`1B00`/`1B01`, `1617`/`160A`,
  telemetri `A603` — lihat `docs/juken5-protocol-spec.md`).
- **Auto-live**: begitu connect (pemicu `1B01` ATAU fallback 1,5 dtk), app otomatis
  masuk live → tab Live terbuka sendiri (sama seperti Android).

## Struktur

```
ios/
├─ Package.swift            # SwiftPM (library ArTunningMonitor + ArTunningWidget + test)
├─ xtool.yml                # bundleID + infoPath + entitlementsPath + extensions (widget)
├─ Info.plist               # hanya key merge: NSLocalNetworkUsageDescription, CFBundleDisplayName
├─ App.entitlements         # App Groups (group.id.arvoid.artunningmonitor) utk widget
├─ Support/
│   ├─ ArTunningWidget-Info.plist   # NSExtensionPointIdentifier = widgetkit-extension
│   └─ ArTunningWidget.entitlements # App Groups (sama dgn app)
├─ Sources/ArTunningMonitor/
│   ├─ Shared/              # port protokol (FrameCodec, LineSplitter, ReplyDispatch,
│   │                       #   TelemetryParser, Crc32Stm32, LiveSession, Opcodes*generated,
│   │                       #   Metrics, TelemetryStore)
│   └─ App/                 # SwiftUI: NetClient, AppViewModel, Connect/Live/Console, tema
│                           #   (seluruh file App/ dibungkus #if os(iOS) agar test jalan di Linux)
├─ Sources/ArTunningWidget/
│   └─ Widget.swift         # WidgetKit: TelemetryWidget (small/medium), baca TelemetryStore
├─ Tests/ArTunningMonitorTests/
│   ├─ ProtocolTests.swift  #40 XCTest (mirror Android)
│   └─ MetricsStoreTests.swift #MetricSet/MetricFormatter/TelemetryStore
├─ scripts/gen-opcodes.mjs  # docs/opcodes.json -> Sources/.../Opcodes.swift
└─ tools/setup-relay.ps1    # relay usbmux iTunes (Windows) <-> WSL - jalankan saat IP WSL berubah
```

## Environment (sudah terpasang di mesin ini — catatan reproduksi)

Yang sudah dijalankan sekali di mesin ini:

1. **WSL distro**: `wsl --install -d Ubuntu-24.04 --no-launch`
2. **Deps + Swift 6.4 + xtool** (di WSL, sebagai root):
   - `apt install build-essential libfuse2t64 libimobiledevice-utils` + dev libs (curl/icu/xml/ncurses/z3/dll)
   - Swift 6.4 → `https://download.swift.org/swift-6.4.0-release/ubuntu2404/swift-6.4.0-RELEASE/swift-6.4.0-RELEASE-ubuntu24.04.tar.gz` → ekstrak ke `/opt/swift` + `PATH` di `/etc/profile.d/swift.sh`
   - `xtool` → `https://github.com/xtool-org/xtool/releases/latest/download/xtool-x86_64.AppImage` → `/usr/local/bin/xtool`
3. **Login xtool (sekali)** — butuh Apple ID kamu:
   ```
   wsl -d Ubuntu-24.04 -u root
   xtool setup
   # mode 1 (Password, Apple ID gratis) -> email -> password ->2FA
   # Path to Xcode.xip: /mnt/<drive>/Xcode_27.1.xip
   # (unduh "Xcode 27.1.xip" dari https://developer.apple.com/download/all/?q=Xcode)
   # verifikasi: swift sdk list -> darwin
   ```
   > Setup menghasilkan iPhoneOS27.1.sdk + iPhoneSimulator27.1.sdk + MacOSX27.0.sdk.
4. **Relay device** (sekali; ulangi bila IP WSL berubah / setelah reboot Windows) —
   karena koneksi iPhone dilayani **iTunes di Windows** (Apple Mobile Device Service),
   bukan usbipd:
   ```powershell
   powershell -ExecutionPolicy Bypass -File ios\tools\setup-relay.ps1
   ```
   (membuat firewall rule + `netsh portproxy27015 -> 127.0.0.1:27015`;
   `USBMUXD_SOCKET_ADDRESS` sudah otomatis di `~/.bashrc` WSL)

## Build & test

```bash
wsl -d Ubuntu-24.04 -u root
cd /mnt/c/Users/Administrator/Documents/Project/personal/xsr_juken_5/ios

# unit test protokol (tanpa device, tanpa login) — wajib50/50:
swift test

# build iOS + sign (Apple ID) + install ke iPhone:
xtool dev
```

**Prasyarat `xtool dev`:** kabel data iPhone → Windows, layar iPhone:
1. Tap **Trust** (pertama kali),
2. Settings → Privacy & Security → **Developer Mode** → ON **lalu restart iPhone**
   (toggle baru muncul setelah ada app dev-signed / dipancing; setelah enable WAJIB reboot
   agar benar-benar aktif),
3. Setelah install pertama: Settings → General → VPN & Device Management → **Trust** sertifikat.

**Syarat "Preparing device" (issue xtool #164 — terbukti di mesin ini):**
- **iTunes harus TERPASANG dan DIBUKA** dengan halaman info iPhone terlihat
  (resep komentar issue #19/#164) — versi Microsoft Store cukup;
- **3uTools (atau tool sejenis) harus ditutup** — jangan berjalan bersama;
- Developer Mode aktif (setelah restart) + layar HP terbuka/unlock.
Tanpa ini, install berhenti di `[Preparing device] Error: noDevice`.

Catatan:
- Build pertama SwiftPM membuat modul iOS (beberapa menit); setelah itu cepat (~6 dtk).
- Kalau berhenti di `Waiting for device to be connected...` = HP belum dicolok/Trust.
- Hasil build bisa diinspeksi: `ios/xtool/ArTunningMonitor.app` (Info.plist merge terverifikasi:
  `NSLocalNetworkUsageDescription` + `CFBundleDisplayName = ArTunning Monitor` +
  `CFBundleIdentifier = id.arvoid.artunningmonitor` + `MinimumOSVersion15.0`).
- `.bsp/xtool.json` dibuat otomatis oleh xtool untuk dukungan IDE (di-gitignore).

### Signing & refresh (Apple ID gratis)

`xtool dev` mendaftarkan device, membuat Certificate + App ID + Provisioning Profile
otomatis lewat Apple Developer Services (tanpa konsol web). Akun gratis = profil
**berlaku7 hari** → **refresh = jalankan `xtool dev` lagi** (atau app kadaluarsa
di hari ke-8). Bundle ID di-sign bisa di-prefix `XTL-xxxx.` oleh xtool (perilaku normal
untuk akun gratis).

## Checklist test ke ECU (sebelum ke mobil)

1. iPhone **join AP WiFi ECU** (Pengaturan WiFi, atau form Join WiFi di app).
2. Tab **Connect** → isi IP `192.168.2.6`, port `80` → **Connect** → izinkan
   *Local Network* saat diminta iOS.
3. Tab **Console**: `TX 1B00` berulang → **`RX 1B01`** = handshake OK.
4. Live mulai **otomatis** (pemicu `1B01` atau fallback 1,5 dtk): `TX 1617`/`TX 160A`
   → **`RX A603;…`** → tab Live terbuka sendiri → gauge hidup.
   Tekan **Stop Live** untuk mode manual (**Start Live** untuk mulai lagi).
5. Putus WiFi >17 dtk → status **Reconnecting** (backoff 2/5/10/30 s).

## Codegen

`Sources/ArTunningMonitor/Shared/Opcodes.swift` dihasilkan dari `docs/opcodes.json`
— **jangan edit tangan**:

```bash
node scripts/gen-opcodes.mjs
# atau: node scripts/gen-opcodes.mjs <path-json> <path-swift>
```

## Troubleshooting

| Gejala | Penyebab / solusi |
|---|---|
| `ERROR: No device found!` / `AFCClient...muxError` | iPhone belum dicolok/Trust, ATAU relay belum jalan → jalankan `ios/tools/setup-relay.ps1` lalu cek `ideviceinfo` |
| `xtool dev` diam di `Waiting for device...` | Colok HP + Trust + Developer Mode |
| Prompt login lagi di xtool | `xtool setup` belum selesai / config hilang — ulangi langkah Setup |
| Connect gagal dari app | iOS *Local Network* belum diizinkan → Pengaturan → Privacy → Local Network |
| Selalu "Gagal koneksi" | Pastikan iPhone di AP ECU (bukan internet rumah); IP/port salah |
| Gauge kosong | Cek Console: tidak ada `RX 1B01` → verifikasi dulu pakai app desktop |
| App kadaluarsa (7 hari) | Jalankan `xtool dev` lagi (re-sign + install) |
| AppImage gagal jalan | `libfuse2t64` belum terpasang, atau jalankan AppImage dengan `--appimage-extract` |
