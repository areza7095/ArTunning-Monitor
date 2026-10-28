# Install — ArTunning Monitor

**English** · [Bahasa Indonesia](../id/install.md)

Build & install guide from **Windows** (host) to **Android** / **iPhone**.
A one-screen summary also lives in the [README](../../README.md#install).

## Prerequisites

| Component | For | Notes |
|---|---|---|
| JDK 17+ | Android | See `android/gradle.properties` |
| Android SDK | Android | Path in `android/local.properties` (`sdk.dir=...`) |
| adb (platform-tools) | Install APK | On this machine: `F:\Android_Studio\platform-tools\adb.exe` |
| Android phone (USB debugging) | Install + test | Settings → Developer → USB debugging ON |
| WSL Ubuntu-24.04 + Swift + xtool | iOS | Full setup in [`ios/README.md`](../../ios/README.md) |
| iPhone (Trust + Developer Mode) | iOS | Data cable; see `ios/README.md` |

## Windows — build + install to Android

From the repo root (PowerShell):

```powershell
cd android
.\gradlew.bat :core:protocol:test        # protocol unit tests — must pass
.\gradlew.bat :app:assembleDebug         # output: app\build\outputs\apk\debug\app-debug.apk
F:\Android_Studio\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
```

A successful `install` prints `Success` in the terminal. The app appears in the launcher as
**ArTunning Monitor**.

## Android — install only (no rebuild)

The APK already exists (e.g. from a previous build or CI):

```powershell
F:\Android_Studio\platform-tools\adb.exe install -r android\app\build\outputs\apk\debug\app-debug.apk
```

- `-r` = replace/reinstall (app data is kept).
- Swap the path if adb is on PATH: plain `adb install -r ...` is enough.

### Check the device before installing

```powershell
F:\Android_Studio\platform-tools\adb.exe devices
```

| Status | Meaning / fix |
|---|---|
| `device` | Ready to install |
| `unauthorized` | Allow USB debugging on the phone screen |
| `offline` | Unplug–replug; check the cable |
| (empty) | USB driver missing / USB debugging not enabled |

## iOS — build + install via WSL (no Mac)

```bash
wsl -d Ubuntu-24.04 -u root
cd /mnt/c/Users/<username>/<path>/ArTunning-Monitor/ios
swift test       # unit tests (Linux, no device)
xtool dev        # build + sign (Apple ID) + install to iPhone
```

Device prerequisites (once + after every reboot): data cable → Trust →
**Developer Mode** ON + restart → trust the certificate. Details & troubleshooting:
[`ios/README.md`](../../ios/README.md).

Free Apple ID: the profile is valid for **7 days** → refresh = run `xtool dev` again.

## After install — smoke test

1. Open the app → allow **Bluetooth** (Android) / **Local Network** (iOS).
2. **Connect** tab → pick the BT module (Android) or make sure the iPhone joined the ECU AP (iOS).
3. **Console** tab: `TX 1B00` → reply **`RX 1B01`** = handshake OK.
4. Live starts automatically → **Live** tab → gauges up.
5. (Optional) Add a home-screen **widget**; Android Auto: see the DHU checklist in the [README](../../README.md#widgets-android-auto--carplay).

## Quick troubleshooting

| Symptom | Fix |
|---|---|
| `adb` not recognized | Use the full path `F:\Android_Studio\platform-tools\adb.exe` |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | `adb uninstall id.arvoid.artunningmonitor` then install again |
| Gradle build fails on SDK | Check `android/local.properties` → `sdk.dir` valid |
| iOS install stuck at `Waiting for device` | Plug in phone + Trust + Developer Mode; iTunes open; 3uTools closed |
| iOS widget missing | Make sure the latest build came from `xtool dev` (App Groups entitlement is automatic) |
