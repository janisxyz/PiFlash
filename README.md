# PiFlash

Flash and configure Raspberry Pi OS from your Android phone over USB-C.

Independent tool — not affiliated with Raspberry Pi Ltd.

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
