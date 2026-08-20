# Publish PiFlash on Google Play

Privacy policy URL (after Pages deploys):

https://janisxyz.github.io/PiFlash/

Package: `piflash.shizoghost.com`  
Version: `3.2.x` (`versionCode` = GitHub Actions Release AAB run number)  
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

## 9. Hook Play Developer API to GitHub Actions

Play Console has no GitHub button. A Google Cloud **service account** calls the Play API; the **Release AAB** workflow uploads the signed AAB.

### A. Google Cloud (once)

1. Open [Google Cloud Console](https://console.cloud.google.com/) and pick/create a project (any name, e.g. `piflash-play`).
2. APIs & Services → Library → enable **Google Play Android Developer API**.
3. IAM & Admin → Service accounts → **Create service account**
   - Name: `piflash-play-upload`
   - Skip extra roles
4. Open the account → Keys → Add key → **JSON**. Download the file. Keep it off git.

### B. Play Console (once)

1. [Play Console](https://play.google.com/console) → **Users and permissions** → Invite user.
2. Email = the service account (`piflash-play-upload@YOUR-PROJECT.iam.gserviceaccount.com`).
3. App: PiFlash (`piflash.shizoghost.com`).
4. Permissions (minimum):
   - View app information and download bulk reports
   - View financial data, orders, and cancellation survey responses — **off**
   - **Releases** → Release to testing tracks (and, later, production if you want)
   - Manage testing tracks and edit testers
5. Send invite. Wait up to **24 hours** before the API works (403 until then).

### C. GitHub secret (once)

Repo → Settings → Secrets and variables → Actions → New repository secret:

| Secret | Value |
|--------|--------|
| `PLAY_SERVICE_ACCOUNT_JSON` | entire JSON file, paste as-is (starts with `{`) |

Keep the existing signing secrets too: `PIFLASH_STORE_BASE64`, `PIFLASH_STORE_PASSWORD`, `PIFLASH_KEY_ALIAS`, `PIFLASH_KEY_PASSWORD`.

### D. What the workflow does

`.github/workflows/release-aab.yml`

- Push to `main` **or** Actions → **Release AAB** → Run workflow
- Builds signed `app-release.aab` (`versionName` 3.2.<run>, `versionCode` = run number)
- If `PLAY_SERVICE_ACCOUNT_JSON` is set, uploads to Play:
  - push to `main` → **internal** testing
  - manual run → pick `internal`, `alpha` (**closed testing**), or `production`
- Status is `completed` so testers can install immediately after Play processes the release

Look in Play Console:

- Internal: **Testing → Internal testing**
- Closed: **Testing → Closed testing** (API track name `alpha`)
- Production: **Release → Production**

Closed testers must join the opt-in link. A new personal Play account still needs **12 testers for 14 days** on closed testing before production.

If a run says skip Play upload, the secret is missing. If it 403s, the service account invite has not propagated yet.
