# PiFlash

Flash and configure Raspberry Pi OS from your Android phone over USB-C.

Native Android app (Kotlin, Jetpack Compose, Material 3). Independent tool — not affiliated with Raspberry Pi Ltd.

## Logo

White microSD + gold lightning on raspberry `#C51A4A`. Adaptive launcher icon with monochrome layer for Android 13 themed icons.

## Download APK via GitHub Actions

1. Open **[Actions](https://github.com/janisxyz/PiFlash/actions)**
2. Select **Build APK** → latest green run
3. Download **PiFlash-debug** and sideload (allow unknown sources)

## Google Play

See [docs/PLAY_STORE.md](docs/PLAY_STORE.md) for listing copy, Data safety answers, and how to sign an AAB.
Privacy policy: [docs/privacy-policy.md](docs/privacy-policy.md).

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
