# ArTunning Monitor

[![License](https://img.shields.io/github/license/areza7095/ArTunning-Monitor)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%20%7C%20iOS-166534)](android/)
[![Protocol%20tests](https://img.shields.io/badge/protocol%20tests-40%20%C3%97%202%20platform-D4A373)](docs/juken5-protocol-spec.md)

**Monitor live ECU motor injeksi BRT Juken 5 Plus dari HP** — Android lewat *Bluetooth SPP*, iOS lewat *WiFi TCP* — plus **spesifikasi protokol ECU yang terbuka** biar kamu bisa bikin client sendiri (ESP, web, tooling).

```
Connect  → 1B00/1B01 (handshake)  →  auto-live: 1617 → 160A  →  A603;…  →  gauge hidup
```

## Tentang

Aplikasi monitoring live yang ringan untuk ECU **Bintang Racing Team Juken 5 Plus**:

- **Live gauge** RPM, TPS, AFR, EOT, MAP, IAT, BATT, arus injeksi — skala sesuai protokol asli.
- **Auto-live** — begitu koneksi OK (balasan `1B01` ATAU fallback 1,5 dtk), mode live jalan
  tanpa klik apa pun dan tab Live terbuka sendiri.
- **Debug console** — log mentah TX/RX + kirim frame manual (alat validasi protokol ke ECU).
- **Auto-reconnect** dengan backoff (2/5/10/30 dtk) + keepalive `1B00` tiap 3 dtk.
- **Design system NomadKit** (light eksak + dark turunan), status selalu ikon + teks.
- **40 unit test protokol × 2 platform** (JVM & Linux) dari golden vectors yang sama.

> ⚠️ **Disclaimer** — Proyek pribadi, **tidak berafiliasi dengan Bintang Racing Team**.
> "Juken", "Bintang Racing Team", dan merek terkait adalah milik pemiliknya masing-masing;
> nama disini hanya rujukan faktual. Dokumentasi protokol dihasilkan dari analisis
> black-box aplikasi desktop resmi untuk keperluan **interoperabilitas**, tanpa
> menyalin kode milik pihak lain. Bukan produk resmi BRT — risiko penggunaan di kendaraan
  ada di tanganmu sendiri.

## Status

| Target | Status |
|---|---|
| **Android** (Bluetooth SPP) | ✅ APK teruji langsung ke ECU ·40/40 test |
| **iOS** (WiFi TCP) | ✅ Terpasang di iPhone via [xtool](https://github.com/xtool-org/xtool) (Apple ID gratis) ·40/40 test |
| **Spesifikasi protokol** | ✅ Lengkap + `opcodes.json` machine-readable |
| **ESP / bridge** | 🕜 menyusul — pakai `docs/opcodes.json` yang sama |

## Struktur monorepo

```
docs/                          # SOURCE OF TRUTH bersama antar target
├─ juken5-protocol-spec.md     # spesifikasi protokol ECU (bukti IL disertakan)
├─ opcodes.json                #151 opcode, layout struct, timing, transport
├─ install.md                  # build & install Windows -> Android / iPhone
└─ design/nomadkit.md          # design system NomadKit (DESIGN.md, MIT)

android/                       # Kotlin + Jetpack Compose (Bluetooth SPP)
├─ core/protocol/              # protokol murni JVM + codegen Opcodes.kt + unit test
├─ transport-bt/               # pipe byte Bluetooth (tahu koneksi, TIDAK tahu protokol)
└─ app/                        # UI Compose (Connect / Live / Console)

ios/                           # SwiftUI (WiFi TCP — iOS tidak punya BT SPP)
├─ Sources/ArTunningMonitor/   # Shared (protokol) + App (SwiftUI, guard #if os(iOS))
├─ Tests/                      # XCTest — mirror40 test Android
├─ Package.swift · xtool.yml   # SwiftPM + xtool (build tanpa Mac)
└─ tools/setup-relay.ps1       # relay usbmux iTunes <-> WSL

esp/                           # (menyusul) target ESP
```

**Konvensi:**
1. `docs/` = source of truth — kode di-*generate*/di-port dari dokumen, bukan sebaliknya.
2. Target baru = subfolder baru dengan build sendiri; jangan saling copy kode.
3. Transport terpisah dari protokol (tambah WiFi/transport lain tanpa ubah protokol).

## Install

Detail lengkap (prasyarat + troubleshooting) ada di [`docs/install.md`](docs/install.md).

### Windows — build + install ke Android

```powershell
cd android
.\gradlew.bat :core:protocol:test        # unit test protokol (tanpa HP)
.\gradlew.bat :app:assembleDebug         # APK -> app\build\outputs\apk\debug\
F:\Android_Studio\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
```

Prasyarat: Android SDK (`android/local.properties`) + JDK. Colok HP →
`adb devices` harus terdeteksi (status `device`, bukan `unauthorized`).

### Android — install saja (APK sudah di-build)

```powershell
F:\Android_Studio\platform-tools\adb.exe install -r android\app\build\outputs\apk\debug\app-debug.apk
```

### iOS — build + install via WSL (tanpa Mac)

```bash
wsl -d Ubuntu-24.04 -u root
cd /mnt/c/Users/<username>/<path>/ArTunning-Monitor/ios
swift test    # unit test (Linux, tanpa device)
xtool dev     # build + sign (Apple ID) + install ke iPhone
```

Setup WSL/xtool & syarat "Preparing device" lengkap di [`ios/README.md`](ios/README.md).
Akun Apple ID gratis = profil **7 hari** → refresh = `xtool dev` lagi.

**Validasi ke ECU (Android):** izinkan Bluetooth → tab **Connect** → pilih modul BT
→ tab **Console**: `TX 1B00` → `RX 1B01` → live otomatis → tab Live → gauge hidup.

Catatan iOS: transport = **WiFi ECU** (join AP → TCP `192.168.2.6:80`) karena iOS tidak
mendukung Bluetooth SPP.

## Protokol dalam satu layar

Format baris ASCII, TX = `<HEX>;<param>\r\n` (HEX = opcode `0x1601` → `"1601"`):

| Bagian | Nilai kunci |
|---|---|
| Handshake | kirim `1B00` → balasan `1B01` (keepalive tiap3 dtk, watchdog17 dtk) |
| Masuk live | `1617` lalu `160A` tiap500 ms (maks25×) → frame `A603;…` |
| Telemetri `A603` | `TPS;BAT;CORE;RPM;EOT×10;AFR;MAP×10;IAT×10;PUMP×1000;INJ×1000` |
| Bulk map/param/log | marker `#B1#`/`#B2#`/`#B3#` … `*eE*\r\n`, ukuran1012 /10248 /28 byte |
| Integrity | **CRC32 ala STM32** — init `0xFFFFFFFF`, poly `0x04C11DB7`, kata little-endian |
| Opcode | Read `0x16xx` · Write `0x26xx/0x36xx` · Bulk `0x15xx/0x95xx` · Reply `0x96xx/0xA6xx` |

Dokumen lengkap (termasuk **bukti IL per klaim**, layout struct per-byte, tabel dispatch):
[`docs/juken5-protocol-spec.md`](docs/juken5-protocol-spec.md) ·
mesin-readable: [`docs/opcodes.json`](docs/opcodes.json) — inilah yang dipakai codegen
`Opcodes.kt` (Android) & `Opcodes.swift` (iOS); target ESP nanti juga dari file ini.

## Design system

Palet & aturan dari [NomadKit](docs/design/nomadkit.md) (DESIGN.md, MIT, by @chef):
Sand `#D4A373` (aksi utama) · Ocean `#0891B2` (sekunder) · Forest `#166534` (sukses) ·
Background `#FFFDF7` — plus aturan: jangan andalkan warna saja, siapkan skeleton state.

## Roadmap

- [x] Reverse-engineering protokol ECU + spesifikasi berbukti
- [x] Android app (Bluetooth SPP) — live monitor
- [x] iOS app (WiFi TCP) — live monitor
- [x] Widget home screen Android (Glance) + konfigurasi metrik
- [x] Android Auto (Media Session — telemetri sbg now-playing)
- [x] Widget iOS (WidgetKit + App Group via xtool)
- [ ] CarPlay — **blocked by design** (lihat bawah)
- [ ] Target ESP (bridge/gauge) dari `docs/opcodes.json`
- [ ] Baca/tulis map & data logger (bulk + CRC) di aplikasi

## Widget, Android Auto & CarPlay

### Widget Android (Glance)

Koneksi ECU dipegang **foreground service** (`EcUService`, type `connectedDevice`)
+ notifikasi persisten "Terhubung ke ECU". Widget membaca snapshot dari
`EcURepository` (StateFlow) — **tidak** membuka koneksi sendiri.

- Tambah widget dari home screen → layar konfigurasi multi-select metrik
  (RPM, TPS, AFR, EOT, MAP, IAT, BATT, PUMP, INJ).
- Update ~1 dtk (throttle mengikuti notifikasi service); staleness eksplisit
  "update N dtk lalu".
- Pilihan metrik **global** (semua instance widget memakai pilihan sama) — MVP.

### Android Auto (kategori Media)

`EcUMediaService` (MediaBrowserServiceCompat + MediaSessionCompat):
telemetri live dipublikasikan sebagai **now-playing track**
(judul = deret metrik terpilih, artwork tile NomadKit).

**Test tanpa mobil (Desktop Head Unit):**

1. Install [Android Auto](https://play.google.com/store/apps/details?id=com.google.android.projection.gearhead)
   di HP + [Desktop Head Unit](https://developer.android.com/training/cars/downloads)
   (butuh Java; jalankan `desktop-head-unit.jar`).
2. Colok HP ke PC (USB) → aktifkan **Unknown sources** di pengaturan developer
   Android Auto (di DHU: Start head unit → menu ⋮ → Settings → Developer →
   Unknown sources).
3. Buka app → Connect ke ECU → buka DHU → app muncul di daftar Media →
   "now playing" = telemetri live.
4. HP: Android 9+ (API 28); app minSdk 26 — aman.

### iOS Widget (WidgetKit + App Group)

`TelemetryStore` menyimpan snapshot di App Group
`group.id.arvoid.artunningmonitor` (UserDefaults suite).
App menulis tiap frame `A603` (throttle 1 dtk) + `WidgetCenter.reloadTimelines`.
Widget `ArTunningWidget` (small/medium) menampilkan metrik terpilih + staleness.

Build & install via `xtool dev` — xtool **membuat & assign App Group otomatis**
saat signing (dukungan free account; sama dgn bundle prefix `XTL-xxxx.`).
Entitlements: `ios/App.entitlements` (app) + `ios/Support/ArTunningWidget.entitlements`.

### CarPlay — butuh izin Apple (tidak bisa free account)

Berdasarkan [Requesting CarPlay Entitlements](https://developer.apple.com/contact/carplay/)
(dokumen resmi Apple):

- Wajib **minta entitlement** via CarPlay Contact Us + tandatangani
  *CarPlay Entitlement Addendum* → Apple me-review tiap aplikasi.
- Entitlement masuk akun developer (managed capability) → butuh
  **Apple Developer berbayar** + lolos review.
- Kategori entitlement yang tersedia: `carplay-audio`, `carplay-communication`,
  `carplay-maps` (navigation), `carplay-parking`, `carplay-charging`,
  `carplay-quick-ordering` — **tidak ada** kategori dashboard/telemetri/gauge.
- CarPlay Simulator hanya untuk app yang **sudah** punya entitlement.

Kesimpulan: **bukan masalah kode, tapi izin Apple.** Tidak ditulis kode CarPlay.
Bila kelak lolos review, syarat teknis: CP templates + `entitlementsPath`
sudah tersedia di xtool.

## License

**Copyright (c)2026 Ahmad Reza A.** Dilisensikan di bawah
[CC BY-NC 4.0](LICENSE) — *Attribution-NonCommercial*.

- **Boleh**: share, fork, modifikasi, pelajari — **untuk keperluan non-komersial** —
  selama copyright notice `©2026 Ahmad Reza A` tetap ada dan lisensinya dirujuk.
- **Tidak boleh tanpa izin**: penggunaan **komersial** (dijual, layanan berbayar, dsb.)
  — hubungi pemilik: [@areza7095](https://github.com/areza7095).
- Lihat [LICENSE](LICENSE) untuk teks lengkap dan resmi.

## Credits

- [NomadKit](docs/design/nomadkit.md) design system oleh **@chef** (DESIGN.md, MIT).
- [xtool](https://github.com/xtool-org/xtool) — build & install iOS dari Windows/Linux (MIT).
- Dokumentasi protokol: hasil analisis black-box terhadap aplikasi desktop resmi BRT
  (metode + bukti ada di [`docs/juken5-protocol-spec.md`](docs/juken5-protocol-spec.md)).

<!-- Media: taruh screenshot di docs/media/ (Connect / Live / Console) lalu sisipkan di sini:
![Live](docs/media/live.png)
-->
