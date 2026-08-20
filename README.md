# PiFlash

Flash and configure Raspberry Pi OS from your Android phone over USB-C.

Independent tool — not affiliated with Raspberry Pi Ltd.

Play package name: `piflash.shizoghost.com`

Headless setup supports templates (Home lab, Coolify host, Headless LAN) and named presets stored only on the phone. Edit templates from the home screen without a card plugged in.

Settings cover UI language (system, English, Deutsch, Français, Italiano, Español) and theme (system / light / dark plus Raspberry, teal, indigo, amber, forest, or Android 12+ dynamic color).

## Play Store

Step-by-step publish guide: [docs/PLAY_STORE.md](docs/PLAY_STORE.md)

Privacy policy (GitHub Pages): https://janisxyz.github.io/PiFlash/

Listing copy lives in `fastlane/metadata/android/en-US/`.

To build the upload bundle after adding keystore secrets:

1. Actions → **Release AAB** → Run workflow
2. Download the `PiFlash-release-aab` artifact
3. Upload `app-release.aab` in Play Console

## Debug APK

Actions → **Build APK** → latest green run → `PiFlash-debug`.

## License

MIT
