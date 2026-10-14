# Android Apps — Full-Isolation Setup Instructions

**Decision:** each app gets its **own** Google Cloud / Firebase project, OAuth
consent screen + client, signing key, service accounts, and CI secrets. Nothing is
shared. This lets the apps diverge (different APIs/scopes, release cadence, even
ownership) without coupling.

| | Photo Selector Toolbox (`:android-desktop`) | PhotoTok (`:phototok`) |
| --- | --- | --- |
| Package | `com.photoselectortoolbox` | `com.phototok` |
| GCP / Firebase project | **reuse** `photo-selector-tb-dist` | **create** `phototok-app` |
| Action needed | clean up / rename credentials | **build from scratch** |

> The repo changes are already done (Gradle signing, CI workflow, `.firebaserc`,
> docs). This document covers the console + secret work you do by hand.
>
> **Architecture Update (2026):** Both `:android-desktop` and `:phototok` now operate 100% locally via the Android Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`) with zero runtime permissions and no network access. Google Drive OAuth clients, consent screens, and `PHOTOTOK_PICKER_API_KEY` / `PHOTOTOK_GCP_PROJECT_NUMBER` are no longer required at runtime; only the upload keystores, Firebase App Distribution service accounts, and Google Play publishing service accounts are used by CI.

---

## Part 0 — Naming reference

Use these names throughout so everything is consistent and self-describing.

| Concept | Toolbox | PhotoTok |
| --- | --- | --- |
| GCP / Firebase project id | `photo-selector-tb-dist` | `phototok-app` |
| Project display name | `Photo Selector Toolbox` | `PhotoTok` |
| Keystore file | `toolbox-upload.jks` | `phototok-upload.jks` |
| Key alias | `toolbox-upload` | `phototok-upload` |
| Play publishing service account | `play-publisher@photo-selector-tb-dist…` | `play-publisher@phototok-app…` |
| Firebase distribution service account | `firebase-distributor@photo-selector-tb-dist…` | `firebase-distributor@phototok-app…` |

### GitHub repository **secrets** to create

| Secret | Toolbox | PhotoTok | Description |
| --- | --- | --- | --- |
| Keystore (base64) | `TOOLBOX_KEYSTORE_FILE` | `PHOTOTOK_KEYSTORE_FILE` | Base64-encoded upload keystore |
| Keystore password | `TOOLBOX_STORE_PASSWORD` | `PHOTOTOK_STORE_PASSWORD` | Keystore store password |
| Key alias | `TOOLBOX_KEY_ALIAS` | `PHOTOTOK_KEY_ALIAS` | Keystore key alias |
| Key password | `TOOLBOX_KEY_PASSWORD` | `PHOTOTOK_KEY_PASSWORD` | Keystore key password |
| Firebase distribution creds | `TOOLBOX_FIREBASE_APP_DISTRIBUTION_CREDENTIALS` | `PHOTOTOK_FIREBASE_APP_DISTRIBUTION_CREDENTIALS` | Firebase Service Account credentials |
| Play publishing creds | `TOOLBOX_GP_SERVICE_ACCOUNT_JSON` | `PHOTOTOK_GP_SERVICE_ACCOUNT_JSON` | Google Play Console Service Account credentials |

### GitHub repository **variables** to create

| Variable | Value | Description |
| --- | --- | --- |
| `PHOTOTOK_FIREBASE_APP_ID` | the Android App ID from the new `phototok-app` Firebase project (looks like `1:NNN:android:xxxx`) | Firebase App ID |

> You can delete the old shared secrets once both apps are migrated:
> `ANDROID_KEYSTORE_FILE`, `ANDROID_STORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
> `ANDROID_KEY_PASSWORD`, `FIREBASE_APP_DISTRIBUTION_CREDENTIALS`,
> `GP_SERVICE_ACCOUNT_JSON`.

---

## Part 1 — Generate the two upload keystores

Run locally (needs the JDK `keytool`). Use strong, distinct passwords for each.

```bash
# Toolbox
keytool -genkeypair -v \
  -keystore toolbox-upload.jks \
  -alias toolbox-upload \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storetype JKS

# PhotoTok
keytool -genkeypair -v \
  -keystore phototok-upload.jks \
  -alias phototok-upload \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storetype JKS
```

Then base64-encode each for the GitHub secret:

```bash
base64 -i toolbox-upload.jks  | pbcopy   # paste into TOOLBOX_KEYSTORE_FILE
base64 -i phototok-upload.jks | pbcopy   # paste into PHOTOTOK_KEYSTORE_FILE
```

Store the `.jks` files and their passwords somewhere safe (password manager); they
are git-ignored and must never be committed.

> **Note on Play App Signing:** Google holds the real app-signing key; these are
> your *upload* keys. If an app already has Play App Signing enabled with the old
> shared key, register the new upload key in
> **Play Console → app → Test and release → App integrity → App signing →
> Upload key certificate** (upload the `.pem` exported via
> `keytool -export-cert … | openssl x509 -inform DER -outform PEM`). New apps just
> use the new key from the start.

---

## Part 2 — PhotoTok: create its own Google Cloud + Firebase project

1. **Create the project**
   - Firebase Console → **Add project** → name `PhotoTok`, set project id
     `phototok-app`. (Creating it in Firebase also creates the underlying GCP
     project.)

2. **Register the Android app in Firebase**
   - Project settings → **Add app** → Android → package `com.phototok`.
   - Copy the generated **App ID** (`1:NNN:android:xxxx`) → put it in the GitHub
     **variable** `PHOTOTOK_FIREBASE_APP_ID`.
   - (Optional) `google-services.json` is not needed since no Firebase client SDKs or Google Play services run in the app.

3. **Enable the API**
   - Google Cloud Console (same project) → **APIs & Services → Library** →
     enable **Google Play Android Developer API**. (Google Drive API and OAuth are not needed).

6. **Firebase App Distribution service account**
   - Cloud Console → IAM & Admin → **Service Accounts → Create** →
     `firebase-distributor`.
   - Grant role **Firebase App Distribution Admin**.
   - Create a JSON key → paste its contents into the GitHub secret
     `PHOTOTOK_FIREBASE_APP_DISTRIBUTION_CREDENTIALS`.
   - In Firebase Console → App Distribution, create the **`testers`** group and add
     testers.

7. **Google Play publishing service account**
   - Cloud Console → Service Accounts → **Create** → `play-publisher`.
   - Create a JSON key → paste into GitHub secret `PHOTOTOK_GP_SERVICE_ACCOUNT_JSON`.
   - Play Console → **Users & permissions → Invite (service account email)** →
     grant access to the **PhotoTok app only** with "Release to testing tracks"
     permission. (Play Console is one developer account; you scope each service
     account to its app.)
   - **Crucial First Release Step:** Because Google Play Developer API does not support initializing brand-new apps, the very first upload of `com.phototok`'s AAB must be done **manually** in the Play Console UI. Once a manual build is uploaded to the internal track, future updates can run automatically via GitHub Actions.

---

## Part 3 — Photo Selector Toolbox: clean up the existing project

The Toolbox keeps `photo-selector-tb-dist`. Tidy it so it's Toolbox-only:

1. **Remove PhotoTok's footprint here**
   - Firebase Console → project settings → delete the old PhotoTok app record
     (`1:211786657248:android:27e94ef870600f10f68f85`) so this project only holds
     the Toolbox app. (PhotoTok now lives in `phototok-app`.)

2. **Service accounts** — create/rename `firebase-distributor` and `play-publisher`
   here, generate fresh JSON keys, and store them as:
   - `TOOLBOX_FIREBASE_APP_DISTRIBUTION_CREDENTIALS`
   - `TOOLBOX_GP_SERVICE_ACCOUNT_JSON`
   In Play Console, scope `play-publisher@photo-selector-tb-dist…` to the **Toolbox
   app only**.
   - **Note:** Ensure the **Google Play Android Developer API** is enabled in the `photo-selector-tb-dist` GCP project, and that at least one manual AAB upload has been completed to Google Play Console before attempting automated builds. (OAuth clients are not required).

---

## Part 4 — Add all GitHub secrets/variables

Repo → **Settings → Secrets and variables → Actions**.

- Add the 12 secrets from the table in Part 0 (6 per app).
- Add the variable `PHOTOTOK_FIREBASE_APP_ID`.
- Delete the 6 old shared secrets after the first green release.

---

## Part 5 — Verify

1. Push to `main` (or open a PR) and confirm the **Android Build** workflow runs.
2. Check each step:
   - "Decode Keystores" reports both keystores decoded.
   - Both release APK/AAB builds succeed and are **signed** (the build won't apply
     the signing config if its app's keystore env var is empty).
   - Each app uploads to **its own** Firebase project and **its own** Play app.
3. Install each app on a device and confirm on-device SAF folder selection and culling succeed on the Play-signed build.

---

## What the repo changes already did

- `products/android/android-desktop/build.gradle.kts` → reads `TOOLBOX_*` signing env vars.
- `products/android/phototok/build.gradle.kts` → reads `PHOTOTOK_*` signing env vars.
- `.github/workflows/android.yml` → decodes two keystores, builds each app
  with its own signing env, distributes to each app's own Firebase project, and
  publishes to Play with each app's own service account.
- `.firebaserc` → adds `toolbox` and `phototok` project aliases.
- `REQUIREMENTS.md` §7.10 → documents the isolated setup.
