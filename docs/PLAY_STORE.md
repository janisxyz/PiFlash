# Google Play listing

## App identity

| Field | Value |
|-------|--------|
| App name | PiFlash |
| Package | `ch.leftclick.piflash` |
| Category | Tools |
| Tags | Raspberry Pi, SD card, imager, USB |
| Default language | English |
| Contact | Leftclick AG |

## Short description (max 80)

Flash and configure Raspberry Pi OS to an SD card from your phone.

## Full description

PiFlash writes a Raspberry Pi OS image to a microSD card over USB-C and applies headless first-boot settings on the phone.

What you can do

• Flash .img, .img.xz, and .img.gz images with streaming decompression
• Set hostname, username, and password
• Enable SSH with password, public key, or both
• Configure Wi-Fi (WPA2 / WPA3 / open, hidden SSID, country code)
• Set timezone, locale, and keyboard layout
• Works fully offline. Nothing is uploaded.

You need a USB-C SD card reader (or a phone that can act as USB host) and a Raspberry Pi OS image stored on the device.

Warnings

Flashing erases everything on the selected card. Confirm the target before you start. Use a genuine SD card. This app is not affiliated with Raspberry Pi Ltd.

## Store assets (upload in Play Console)

| Asset | Spec |
|-------|------|
| App icon | 512 × 512 PNG, square, no baked-in rounding |
| Feature graphic | 1024 × 500 PNG, no alpha |
| Phone screenshots | at least 2, 16:9 or 9:16 |

Export the launcher mark from the adaptive vectors in `app/src/main/res/drawable/`.

## Data safety form

- Data collected: no
- Data shared: no
- Security practices: data is encrypted in transit N/A (no network); users can request deletion N/A
- Account: no

## Content rating

Everyone / Tools. Destructive write is a user-confirmed action, not violent content.

## Signing

Play requires an Android App Bundle (`.aab`), not a debug APK.

Create an upload keystore once and keep it offline:

```bash
keytool -genkey -v -keystore piflash-upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias piflash
```

Then build:

```bash
export PIFLASH_STORE_FILE=/absolute/path/piflash-upload.jks
export PIFLASH_STORE_PASSWORD=...
export PIFLASH_KEY_ALIAS=piflash
export PIFLASH_KEY_PASSWORD=...
gradle :app:bundleRelease
# → app/build/outputs/bundle/release/app-release.aab
```

Never commit the keystore. Enroll Play App Signing so Google holds the app signing key.

## Policy notes that will be asked

- Privacy policy URL: publish `docs/privacy-policy.md` on a public HTTPS page and paste the URL.
- USB host is required (`android.hardware.usb.host`). Devices without host USB will be excluded. That is intended.
- Target API: bump `targetSdk` to the current Play requirement before submission if Console rejects 34.
