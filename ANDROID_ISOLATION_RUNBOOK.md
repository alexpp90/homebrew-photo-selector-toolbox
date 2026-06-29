# Full-Isolation Runbook — do these in order

A step-by-step checklist to split the two apps into fully isolated cloud setups.
Work top to bottom; each phase says **WHERE** you are, **WHAT** to click/run, and a
**✓ Checkpoint** to confirm before moving on. Estimated hands-on time: ~2–3 hours
(plus Google's OAuth verification, which runs in the background for days/weeks).

Legend of the four places you'll work:
- 💻 **Terminal** (your Mac)
- 🔥 **Firebase Console** — https://console.firebase.google.com
- ☁️ **Google Cloud Console** — https://console.cloud.google.com
- ▶️ **Play Console** — https://play.google.com/console
- 🐙 **GitHub** — your repo → Settings

---

## Phase 0 — Gather what you'll need  💻 / ▶️

1. Confirm you can sign in to all four consoles with the **same Google account**
   that owns the existing project `photo-selector-tb-dist` and the Play developer
   account.
2. Get your **debug SHA-1** (used by both apps for local testing):
   ```bash
   keytool -list -v \
     -keystore ~/.android/debug.keystore \
     -alias androiddebugkey -storepass android -keypass android | grep SHA1
   ```
   Copy the `SHA1:` value somewhere — you'll paste it twice later.
3. Get each app's **Play App Signing SHA-1** (only if the app already exists in Play):
   - ▶️ Play Console → select the app → left sidebar (security section) →
     **Protected with Play** → in the **App signing key certificate** block copy the
     **SHA-1 certificate fingerprint** (not SHA-256, and use the *App signing key*,
     not the *Upload key*).
     *(Older layout: **Test and release → App integrity → App signing**.)*
   - Do this for **Photo Selector Toolbox** now; do it for **Photo Tok** after you
     create it in Play (Phase 5) — the certificate only appears once the first AAB
     has been uploaded.

✓ **Checkpoint:** you have the debug SHA-1 and the Toolbox Play SHA-1 saved.

---

## Phase 1 — Generate the two upload keystores  💻

Run in a safe folder **outside** the repo (these files must never be committed):

Run each as a **single line** (multi-line pastes can truncate flags). Don't pass
`-storetype` — keytool defaults to **PKCS12**, the recommended format for Android
upload keys. (You'll see a harmless "stored in PKCS12" note; the `.jks` extension is
fine.)

```bash
# Photo Selector Toolbox
keytool -genkeypair -v -keystore toolbox-upload.jks -alias toolbox-upload -keyalg RSA -keysize 2048 -validity 10000
```

```bash
# Photo Tok
keytool -genkeypair -v -keystore phototok-upload.jks -alias phototok-upload -keyalg RSA -keysize 2048 -validity 10000
```

For each: enter a **strong, distinct password** and the same value when it asks for
the key password (or set a separate one — just remember which is which). Save all
passwords in your password manager.

Base64-encode them (you'll paste these into GitHub in Phase 8):
```bash
base64 -i toolbox-upload.jks  | pbcopy   # → TOOLBOX_KEYSTORE_FILE
# paste somewhere, then:
base64 -i phototok-upload.jks | pbcopy   # → PHOTOTOK_KEYSTORE_FILE
```

> If an app already uses **Play App Signing** with the old shared key, you must
> register the new upload key in Play later (Phase 5 / Phase 6, step "Upload key
> certificate"). New apps just adopt the new key from the start.

✓ **Checkpoint:** two `.jks` files exist, passwords saved, base64 strings captured.

---

## Phase 2 — Create the Photo Tok project  🔥

1. 🔥 Firebase Console → **Add project**.
2. Name it `Photo Tok`. When it shows the project ID, click the edit pencil and set
   it to **`phototok-app`** (must be globally unique — if taken, use
   `phototok-app-2026` and note the actual id). Finish creating (Analytics optional).
3. In the new project: **Project Overview → Add app → Android**.
   - Android package name: **`com.phototok`**
   - App nickname: `Photo Tok`
   - Register the app. You can **skip** downloading `google-services.json` and skip
     the SDK steps (the app resolves Drive auth via package + SHA-1, not that file).
4. **Project settings (gear) → General → Your apps** → copy the **App ID** for
   `com.phototok` (format `1:NNNNNN:android:xxxxxxxx`). Save it — this becomes the
   GitHub variable `PHOTOTOK_FIREBASE_APP_ID` in Phase 8.

✓ **Checkpoint:** project `phototok-app` exists with a `com.phototok` Android app,
and you have its App ID.

---

## Phase 3 — Photo Tok: enable Drive API + OAuth  ☁️

> The Google Cloud Console and Firebase share the **same** project; just switch the
> project picker at the top to **Photo Tok / phototok-app**.

1. **Enable the APIs**
   - ☁️ APIs & Services → **Library** → search **Google Drive API** → **Enable**.
   - ☁️ APIs & Services → **Library** → search **Google Play Android Developer API** → **Enable**.
2. **OAuth consent screen**
   - APIs & Services → **OAuth consent screen** → User type **External** → Create.
   - App name `Photo Tok`, support email = your email, developer contact = your email.
   - **Scopes → Add or remove scopes** → add:
     - `.../auth/drive` (full — **restricted**)
     - `.../auth/drive.file`
   - **Test users → Add users** → add your own Google account (lets you use the app
     immediately while verification is pending).
   - Save. Then **Publishing status → Prepare for verification / Submit**. Because
     `.../auth/drive` is restricted, expect a verification review and a **CASA
     security assessment**. This runs in the background — keep going.
3. **Android OAuth client**
   - APIs & Services → **Credentials → + Create credentials → OAuth client ID**.
   - Application type **Android**, name **`android-phototok`**.
   - Package name **`com.phototok`**.
   - SHA-1: paste your **debug SHA-1** (from Phase 0). Save.
   - Come back after Phase 5 and **add the Photo Tok Play App Signing SHA-1** to
     this same client.

✓ **Checkpoint:** Drive and Google Play APIs enabled, consent screen has both scopes + you as test
user, and an `android-phototok` OAuth client exists with the debug SHA-1.

---

## Phase 4 — Photo Tok: service accounts  ☁️

Make two service accounts in the **phototok-app** project.

1. ☁️ IAM & Admin → **Service Accounts → + Create service account**.
   - Name **`firebase-distributor`** → Create.
   - Grant role **Firebase App Distribution Admin** → Done.
   - Open it → **Keys → Add key → Create new key → JSON** → download.
   - This JSON's contents → GitHub secret `PHOTOTOK_FIREBASE_APP_DISTRIBUTION_CREDENTIALS`.
2. **+ Create service account** again.
   - Name **`play-publisher`** → Create (no GCP role needed; access is granted in
     Play Console in Phase 5).
   - **Keys → Add key → JSON** → download.
   - This JSON's contents → GitHub secret `PHOTOTOK_GP_SERVICE_ACCOUNT_JSON`.
   - Copy the service account **email** (`play-publisher@phototok-app….iam.gserviceaccount.com`).
3. 🔥 Firebase Console → **App Distribution** (left nav) → if prompted, get started →
   **Testers & Groups → Add group** named exactly **`testers`** → add tester emails.

✓ **Checkpoint:** two JSON keys downloaded and mapped to their secret names; the
`testers` group exists in Firebase.

---

## Phase 5 — Photo Tok: create the Play app + grant access  ▶️

1. ▶️ Play Console → **Create app**.
   - App name `Photo Tok`, default language, **App or Game = App**, free/paid as you
     wish, accept declarations → Create.
   - This reserves package **`com.phototok`**.
   - **Crucial:** You must **manually upload the very first release AAB** to the **Internal testing** track via the Google Play Console web interface. The Google Play Developer API (used by GitHub Actions) cannot initialize brand-new apps or create the package association until a manual upload has been completed.
2. **Grant the publisher service account access**
   - Play Console → **Users & permissions → Invite new users**.
   - Email = the `play-publisher@phototok-app…` address from Phase 4.
   - **App permissions** → select **Photo Tok only**.
   - Permissions → at minimum **Release to testing tracks** (and **View app
     information**). Invite.
3. **Get the Photo Tok Play App Signing SHA-1** (now that the app exists)
   - The first upload must be done manually (see Step 1 above). Once uploaded,
     the SHA-1 fingerprint becomes available immediately: left sidebar → **Protected with Play** → **App signing key
     certificate → SHA-1** (older layout: **App integrity → App signing**) → copy it
     → go back to ☁️ Phase 3 step 3 and **add it to the `android-phototok` OAuth
     client**.

✓ **Checkpoint:** Photo Tok app exists in Play, the `play-publisher` service account
is invited with testing-track access scoped to Photo Tok.

---

## Phase 6 — Clean up the Toolbox project  🔥 / ☁️

The Toolbox keeps the existing project `photo-selector-tb-dist`. Strip Photo Tok out
of it so it's Toolbox-only.

1. 🔥 Firebase Console → switch to **photo-selector-tb-dist** → Project settings →
   **Your apps** → find the old Photo Tok app
   (`1:211786657248:android:27e94ef870600f10f68f85`) → **Remove this app**.
   (Leave the Toolbox app `…8c88…` intact.)
2. ☁️ Switch project picker to **photo-selector-tb-dist** → APIs & Services →
   **Credentials**:
   - Ensure there's an Android OAuth client **`android-photoselectortoolbox`** for
     package `com.photoselectortoolbox`. Rename it to that if needed.
   - It must list **two** SHA-1s: your debug SHA-1 **and** the Toolbox Play App
     Signing SHA-1 (from Phase 0).
   - Delete any leftover `com.phototok` OAuth client here.
3. APIs & Services → **OAuth consent screen** → confirm scopes are `.../auth/drive`
   and `.../auth/drive.file`, and you're a test user. (Verification likely already
   done for this project; if not, submit.)

✓ **Checkpoint:** `photo-selector-tb-dist` now contains only the Toolbox app and a
correctly-configured `android-photoselectortoolbox` OAuth client with both SHA-1s.

---

## Phase 7 — Toolbox: service accounts + upload key  ☁️ / ▶️

1. ☁️ In **photo-selector-tb-dist** → IAM & Admin → Service Accounts:
   - Create **`firebase-distributor`** (role: Firebase App Distribution Admin) →
     JSON key → GitHub secret `TOOLBOX_FIREBASE_APP_DISTRIBUTION_CREDENTIALS`.
   - Create **`play-publisher`** → JSON key → GitHub secret
     `TOOLBOX_GP_SERVICE_ACCOUNT_JSON`. Copy its email.
   - (If these already exist from the old shared setup, just create fresh JSON keys
     and reuse them — but make sure each is used by only one app's secrets now.)
2. ▶️ Play Console → **Users & permissions** → ensure
   `play-publisher@photo-selector-tb-dist…` is invited with **Photo Selector Toolbox
   only** + Release to testing tracks. Remove any over-broad/all-app grants from the
   old shared service account.
3. ▶️ Toolbox **upload key** — depends on whether the app has ever been released:
   - **If Toolbox has NEVER had an AAB uploaded to Play** (Play Console says
     "upload the app first" / there's no App signing section yet): **do nothing
     here.** There is no key to reset before the first release. Just put
     `toolbox-upload.jks` into the `TOOLBOX_*` secrets (Phase 8) and let CI upload
     the first AAB — that upload enrolls the app in Play App Signing, making your
     key the **upload key** and letting Google generate the **app signing key**.
   - **Only if Toolbox is ALREADY live under a different (old shared) key** do you
     need to register the new upload key:
     ```bash
     keytool -export -rfc -keystore toolbox-upload.jks -alias toolbox-upload \
       -file toolbox-upload-cert.pem
     ```
     Play Console → Photo Selector Toolbox → left sidebar → **Protected with Play**
     (older layout: **App integrity → App signing**) → **Upload key certificate →
     Request upload key reset** → upload `toolbox-upload-cert.pem` (takes a day or
     two to take effect).
   - **Photo Tok** is brand-new, so it always uses the "do nothing" path: its key is
     adopted on the first CI upload.
   - **After the first upload (either path)**, copy the **App signing key SHA-1**
     from **Protected with Play** and add it to the app's OAuth client (Phase 3 /
     Phase 6), then re-test Drive sign-in.

✓ **Checkpoint:** Toolbox has its own two service-account JSON keys mapped to
`TOOLBOX_*` secrets, Play access is scoped to Toolbox only, and the new upload key is
registered.

---

## Phase 8 — Add GitHub secrets & variable  🐙

🐙 Repo → **Settings → Secrets and variables → Actions**.

**Secrets tab → New repository secret** (create all 12):

| Secret | Paste this value | Where it comes from |
| --- | --- | --- |
| `TOOLBOX_KEYSTORE_FILE` | base64 text of `toolbox-upload.jks` | Phase 1: `base64 -i toolbox-upload.jks` (paste the text, not the binary file) |
| `TOOLBOX_STORE_PASSWORD` | toolbox keystore password | the password you typed during `keytool -genkeypair` for `toolbox-upload.jks` (Phase 1) |
| `TOOLBOX_KEY_ALIAS` | `toolbox-upload` | the `-alias` you passed in Phase 1 (a literal string) |
| `TOOLBOX_KEY_PASSWORD` | toolbox key password | the key password from the same keytool prompt; same as store password if you pressed Enter to reuse it |
| `TOOLBOX_FIREBASE_APP_DISTRIBUTION_CREDENTIALS` | full JSON file contents | Phase 7 Step 1 — `firebase-distributor` service-account JSON key (project `photo-selector-tb-dist`) |
| `TOOLBOX_GP_SERVICE_ACCOUNT_JSON` | full JSON file contents | Phase 7 Step 1 — `play-publisher` service-account JSON key (project `photo-selector-tb-dist`) |
| `PHOTOTOK_KEYSTORE_FILE` | base64 text of `phototok-upload.jks` | Phase 1: `base64 -i phototok-upload.jks` |
| `PHOTOTOK_STORE_PASSWORD` | phototok keystore password | keytool prompt for `phototok-upload.jks` (Phase 1) |
| `PHOTOTOK_KEY_ALIAS` | `phototok-upload` | the `-alias` from Phase 1 (a literal string) |
| `PHOTOTOK_KEY_PASSWORD` | phototok key password | keytool prompt for `phototok-upload.jks` (Phase 1) |
| `PHOTOTOK_FIREBASE_APP_DISTRIBUTION_CREDENTIALS` | full JSON file contents | Phase 4 Step 1 — `firebase-distributor` service-account JSON key (project `phototok-app`) |
| `PHOTOTOK_GP_SERVICE_ACCOUNT_JSON` | full JSON file contents | Phase 4 Step 2 — `play-publisher` service-account JSON key (project `phototok-app`) |

> For the JSON-credential secrets, paste the **entire** file contents (the whole
> `{ … }` object including the multi-line `private_key`) — don't reformat or strip
> newlines. For the keystore secrets, paste the **base64 text**, never the binary
> `.jks`.

**Variables tab → New repository variable** (2):

| Variable | Value | Where it comes from |
| --- | --- | --- |
| `TOOLBOX_FIREBASE_APP_ID` | the `1:NNN:android:xxxx` App ID | Firebase → `photo-selector-tb-dist` project settings → Your apps → the `com.photoselectortoolbox` app. **If that app isn't registered there yet, add it** (Firebase → Add app → Android → package `com.photoselectortoolbox`) and copy the App ID. |
| `PHOTOTOK_FIREBASE_APP_ID` | the `1:NNN:android:xxxx` App ID | Phase 2 Step 4 — Firebase → `phototok-app` project settings → Your apps → the `com.phototok` app. |

> Both are **Variables**, not Secrets (an App ID isn't sensitive).

> Don't delete the old shared secrets yet — wait until Phase 9 passes.

✓ **Checkpoint:** 12 secrets + 1 variable saved, names match exactly.

---

## Phase 9 — Trigger the pipeline & verify  🐙 / device

1. 🐙 Push a small commit to `main` (or merge a PR). Open **Actions → Android Build**.
2. Watch these steps:
   - **Decode Keystores** → logs "decoded" for both, not the "empty" warning.
   - **Build release APK / AAB** → succeed and are signed.
   - **Upload … to Firebase App Distribution** → Toolbox to its project, Photo Tok
     using `PHOTOTOK_FIREBASE_APP_ID` to the new project.
   - **Upload … AAB to Google Play** → each to its own app via its own service account.
3. On a phone, install each app (from Play internal track or Firebase tester link)
   and confirm **Google Drive sign-in works on the Play-signed build**. If sign-in
   fails with a `DEVELOPER_ERROR`/`10`, the app's **Play App Signing SHA-1 is missing
   from its OAuth client** — add it (Phase 3 / Phase 6) and retry.

✓ **Checkpoint:** green pipeline; both apps install and Drive sign-in works.

---

## Phase 10 — Decommission the old shared secrets  🐙

Once Phase 9 is green, 🐙 delete the now-unused shared secrets:
`ANDROID_KEYSTORE_FILE`, `ANDROID_STORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
`ANDROID_KEY_PASSWORD`, `FIREBASE_APP_DISTRIBUTION_CREDENTIALS`,
`GP_SERVICE_ACCOUNT_JSON`. Revoke the old shared service-account key in ☁️ IAM if it's
no longer referenced.

✓ **Done.** Each app now has its own project, OAuth client, signing key, service
accounts, and secrets — they can diverge freely.

---

### Quick "where does each thing live" map

| Thing | Toolbox | Photo Tok |
| --- | --- | --- |
| GCP/Firebase project | `photo-selector-tb-dist` | `phototok-app` (new) |
| OAuth client | `android-photoselectortoolbox` | `android-phototok` |
| Upload keystore / alias | `toolbox-upload.jks` / `toolbox-upload` | `phototok-upload.jks` / `phototok-upload` |
| Firebase App ID | `1:211786657248:android:8c88…` | `PHOTOTOK_FIREBASE_APP_ID` var |
| Play package | `com.photoselectortoolbox` | `com.phototok` |
| Secrets prefix | `TOOLBOX_*` | `PHOTOTOK_*` |
