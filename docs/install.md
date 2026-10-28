# Install — ArTunning Monitor

Panduan build & install dari **Windows** (host) ke **Android** / **iPhone**.
Ringkasan satu layar juga ada di [README](../README.md#install).

## Prasyarat

| Komponen | Untuk | Catatan |
|---|---|---|
| JDK17+ | Android | Lihat `android/gradle.properties` |
| Android SDK | Android | Path di `android/local.properties` (`sdk.dir=...`) |
| adb (platform-tools) | Install APK | Mesin ini: `F:\Android_Studio\platform-tools\adb.exe` |
| HP Android (USB debugging) | Install + test | Settings → Developer → USB debugging ON |
| WSL Ubuntu-24.04 + Swift + xtool | iOS | Setup lengkap di [`ios/README.md`](../ios/README.md) |
| iPhone (Trust + Developer Mode) | iOS | Kabel data; lihat `ios/README.md` |

## Windows — build + install ke Android

Dari root repo (PowerShell):

```powershell
cd android
.\gradlew.bat :core:protocol:test        # unit test protokol — wajib pass
.\gradlew.bat :app:assembleDebug         # hasil: app\build\outputs\apk\debug\app-debug.apk
F:\Android_Studio\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
```

Output `install` sukses = `Success` di terminal. App muncul di launcher:
**ArTunning Monitor**.

## Android — install saja (tanpa build ulang)

APK sudah ada (mis. hasil build sebelumnya atau dari CI):

```powershell
F:\Android_Studio\platform-tools\adb.exe install -r android\app\build\outputs\apk\debug\app-debug.apk
```

- `-r` = replace/install ulang (data app tetap).
- Ganti path bila adb ada di PATH: cukup `adb install -r ...`.

### Cek device sebelum install

```powershell
F:\Android_Studio\platform-tools\adb.exe devices
```

| Status | Arti / solusi |
|---|---|
| `device` | Siap install |
| `unauthorized` | Izinkan USB debugging di layar HP |
| `offline` | Cabut–colok ulang; cek kabel |
| (kosong) | Driver USB / USB debugging belum aktif |

## iOS — build + install via WSL (tanpa Mac)

```bash
wsl -d Ubuntu-24.04 -u root
cd /mnt/c/Users/<username>/<path>/ArTunning-Monitor/ios
swift test       # unit test (Linux, tanpa device)
xtool dev        # build + sign (Apple ID) + install ke iPhone
```

Prasyarat device (sekali + setelah reboot): kabel data → Trust →
**Developer Mode** ON + restart → Trust sertifikat. Detail & troubleshooting:
[`ios/README.md`](../ios/README.md).

Apple ID **gratis**: profil berlaku **7 hari** → refresh = jalankan `xtool dev` lagi.

## Setelah install — smoke test

1. Buka app → izinkan **Bluetooth** (Android) / **Local Network** (iOS).
2. Tab **Connect** → pilih modul BT (Android) atau pastikan iPhone join AP ECU (iOS).
3. Tab **Console**: `TX 1B00` → balasan **`RX 1B01`** = handshake OK.
4. Live jalan otomatis → tab **Live** → gauge hidup.
5. (Opsional) Tambah **widget** home screen; Android Auto: lihat checklist DHU di [README](../README.md#widget-android-auto--carplay).

## Troubleshooting singkat

| Gejala | Solusi |
|---|---|
| `adb` tidak dikenal | Pakai path penuh `F:\Android_Studio\platform-tools\adb.exe` |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | `adb uninstall id.arvoid.artunningmonitor` lalu install lagi |
| Build Gradle gagal SDK | Cek `android/local.properties` → `sdk.dir` valid |
| iOS install diam di `Waiting for device` | Colok HP + Trust + Developer Mode; iTunes terbuka; 3uTools ditutup |
| Widget iOS tidak muncul | Pastikan build via `xtool dev` terbaru (entitlement App Groups auto) |
