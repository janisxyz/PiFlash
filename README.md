# PiFlash

Flash and configure Raspberry Pi OS from your Android phone over USB-C.

Native Android app (Kotlin, Jetpack Compose, Material 3).

## Download APK via GitHub Actions

1. Open **[Actions](https://github.com/janisxyz/PiFlash/actions)**
2. Select **Build APK** → **Run workflow** (or open the latest run)
3. When green, download the **PiFlash-debug** artifact
4. Unzip and sideload `app-debug.apk` (allow unknown sources)

Workflow: [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml)

## Local build

```bash
gradle :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

JDK 17 + Android SDK 34 required.

## Defaults

| Field | Default |
|-------|--------|
| Hostname | `raspberrypi` |
| Country | `CH` |
| Timezone | `Europe/Zurich` |

No default password.

## License

MIT
