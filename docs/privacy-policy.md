# Privacy Policy for PiFlash

**Effective date:** 20 August 2026  
**Developer:** Leftclick AG  
**Contact:** via [leftclick.ch](https://leftclick.ch)

PiFlash is a local utility. It flashes a Raspberry Pi OS image from your phone to a USB-connected SD card writer and optionally writes first-boot configuration files.

## Data we collect

PiFlash does **not** collect, sell, share, or transmit personal data.

- No accounts
- No analytics or crash reporting SDKs
- No advertising
- No internet permission

## Data that stays on the device

You may enter a hostname, username, password, Wi-Fi SSID and password, SSH public key, country, timezone, locale, and keyboard layout.

These values are used to write standard Raspberry Pi first-boot files (`userconf`, `ssh`, `wpa_supplicant.conf`, `firstrun.sh`) onto the selected SD card. They are not uploaded anywhere.

If you save a named preset, the same values (including Wi-Fi and account secrets) are stored in the app’s private files on this phone so you can flash another card later. Built-in templates do not contain secrets. Language and theme settings are stored in app preferences on this phone. Android backup is disabled for this app, so presets and settings are not copied to cloud backup. Uninstalling the app deletes them.

Passwords are hashed on-device (SHA-512 crypt) before being written to the SD card. Credentials are not logged.

## Permissions

- **USB host** is required to talk to a USB SD card reader. The app cannot function without it.
- Storage access is limited to the image file you pick through the system file picker.

## Children

The app is not directed at children and does not knowingly collect data from anyone.

## Changes

If this policy changes, we will update this page and the date above.

## Not affiliated

PiFlash is an independent tool. It is not affiliated with, endorsed by, or sponsored by Raspberry Pi Ltd.
