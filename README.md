# PiFlash

Flash and configure Raspberry Pi OS from your Android phone over USB-C.

PiFlash is a native Android app (Kotlin, Jetpack Compose, Material 3) for writing Raspberry Pi OS images to a microSD card and applying headless first-boot configuration (Wi-Fi, SSH, user, hostname) without a monitor or keyboard.

## Features

- Select `.img`, `.img.xz`, or `.img.gz` via the system file picker
- Streamed flash path — images are **not** loaded fully into RAM
- USB Host mass-storage detection with explicit device confirmation
- Clear destructive-write warnings before any erase
- Headless config: hostname, user, password (hashed), locale, timezone, Wi-Fi, SSH
- Modern Pi OS `userconf` / `ssh` / `wpa_supplicant.conf` / `firstrun.sh` hooks
- Progress, speed, ETA, cancel, flush, and practical verification
- Offline after the image is on the device — no backend

## Requirements

- Android 8.0+ (API 26) with **USB Host** support
- USB-C OTG SD card reader
- Raspberry Pi OS image (Lite or Desktop)

## Build

```bash
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Open in Android Studio (Ladybug / Koala+) and run on a USB-host capable device.

## Defaults

| Field | Default |
|-------|--------|
| Hostname | `raspberrypi` |
| Country | `CH` |
| Timezone | `Europe/Zurich` |

No default password is assumed — you must set one.

## License

MIT
