# Publish PiFlash on Google Play

Privacy policy URL (after Pages deploys):

https://janisxyz.github.io/PiFlash/

Package: `piflash.shizoghost.com`  
Version: `3.1` (`versionCode` 4)  
Category: Tools  
Default language: English (United States)

## 1. Developer account

- Pay the Play one-time registration fee.
- If this is a personal account created after 13 Nov 2023 you must run a **14-day closed test with at least 12 testers** before production.

## 2. Upload key (once)

```bash
keytool -genkey -v -keystore piflash-upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias piflash
base64 -w0 piflash-upload.jks > piflash-upload.b64
```

Add GitHub Actions secrets (never commit the jks):

| Secret | Value |
|--------|--------|
| `PIFLASH_STORE_BASE64` | contents of `piflash-upload.b64` |
| `PIFLASH_STORE_PASSWORD` | keystore password |
| `PIFLASH_KEY_ALIAS` | `piflash` |
| `PIFLASH_KEY_PASSWORD` | key password |

Then run workflow **Release AAB** → download `app-release.aab`.

In Play Console: Create app → enroll **Play App Signing** → upload the AAB as the first artifact.

The Play package name is `piflash.shizoghost.com` (`applicationId`). Kotlin/R namespace stays `ch.leftclick.piflash`.

## 3. Store listing copy

Paste from `fastlane/metadata/android/en-US/`.

- Title: PiFlash
- Short description: Flash and configure Raspberry Pi OS to an SD card from your phone.
- Full description: `full_description.txt`

## 4. Graphics

| Asset | File |
|-------|------|
| App icon 512×512 | `store/icon.svg` (export PNG) |
| Feature graphic 1024×500 | `store/feature.svg` (export PNG) |
| Phone screenshots | at least 2 from a real device; mockups can be used for the first upload |

Do **not** round the Play icon. Google applies the mask.

## 5. App content declarations

- Privacy policy: `https://janisxyz.github.io/PiFlash/`
- Data safety: see `docs/DATA_SAFETY.md`
- Content rating: see `docs/CONTENT_RATING.md`
- Ads: no
- Target audience: 18+ or 13+ utility is fine; not a kids app
- News / COVID / government: no
- Financial features: no
- Health: no

## 6. Device catalog

`android.hardware.usb.host` is required. Phones without USB host will be excluded. Leave it that way.

## 7. Target API

`targetSdk` is **35**. From **31 August 2026** new submissions must target **36**. Bump `compileSdk` / `targetSdk` before that date if you have not shipped yet.

## 8. Trademark

Do not use the Raspberry Pi berry logo. Nominative use of the words “Raspberry Pi OS” in the description is fine with the unaffiliated disclaimer already in the listing.

## 9. Auto-upload from GitHub (internal testing)

Play Console has no “Connect GitHub” switch. GitHub Actions uses the Play Developer API.

Do this once:

1. [Google Cloud Console](https://console.cloud.google.com/) → create or pick a project.
2. Enable **Google Play Android Developer API**.
3. IAM → Service accounts → Create (`piflash-play-upload`).
4. Keys → Add key → JSON. Download the file.
5. Play Console → **Users and permissions** → Invite the service account email.
   Grant at least: **View app information**, **Release to testing tracks**, **Manage testing tracks**.
6. Wait up to **24 hours** after the invite (API 403 until it propagates).
7. GitHub → PiFlash → Settings → Secrets → Actions → New secret:

| Secret | Value |
|--------|--------|
| `PLAY_SERVICE_ACCOUNT_JSON` | entire JSON file, paste as-is |

After that, every **Release AAB** run on `main` uploads the signed AAB to the **internal** track (`status: completed`).

Manual override: Actions → Release AAB → Run workflow → choose `internal` or `production`.

Do **not** use `production` until closed testing + the 12 testers / 14 days gate is done.
