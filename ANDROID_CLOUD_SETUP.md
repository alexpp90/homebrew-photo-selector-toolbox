# Android Apps — Cloud Projects, Secrets & Signing: Clean Setup Plan

> **Decision (chosen):** Full isolation — each app gets its own GCP/Firebase
> project, OAuth client, signing key, service accounts, and CI secrets, to allow
> the apps to diverge independently. This document is the analysis/rationale; the
> concrete steps are in **`ANDROID_CLOUD_SETUP_INSTRUCTIONS.md`**.

This document untangles how the two Android apps relate to Google Cloud, Firebase,
OAuth, signing keys, and CI secrets, and proposes a single clean, consistent setup.

The two apps:

| Module | Product name | Package / namespace |
| --- | --- | --- |
| `:app` | Photo Selector Toolbox | `com.photoselectortoolbox` |
| `:phototok` | Photo Tok | `com.phototok` |

**Key constraint you confirmed:** *both* apps need the **same full Google Drive
read/write access** (the `https://www.googleapis.com/auth/drive` scope). This is a
Google **restricted scope** and is the single biggest factor in the recommendations
below.

---

## 1. What is wired up today

Everything is currently *shared* between the two apps, with a couple of
inconsistencies:

**Google Cloud / Firebase — one project for both**
- Project number `211786657248` (project id `photo-selector-tb-dist`).
- Firebase App Distribution holds two app records, one per package:
  - Photo Selector Toolbox: `1:211786657248:android:8c889798b3318b40f68f85`
  - Photo Tok: `1:211786657248:android:27e94ef870600f10f68f85`
- `.firebaserc` default project = `photo-selector-tb-dist`; `firebase.json` is empty (`{}`).

**Google Drive OAuth**
- Both apps use Google Sign-In and request **`drive` + `drive.file`**.
- Auth resolves by **package name + SHA-1**, so each package needs its **own
  Android OAuth client**. An optional `default_web_client_id` string resource is
  read if present (currently relied on as a workaround "to support multiple
  package names/SHA-1 signatures" per the changelog).

**Signing — one key shared by both apps (inconsistency #1)**
- Both `app/build.gradle.kts` and `phototok/build.gradle.kts` read the *same* env
  vars: `KEYSTORE_FILE`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- So both apps are signed with one identical upload key.

**CI secrets (GitHub Actions) — all shared**

| Secret | Used for | Shared across apps? |
| --- | --- | --- |
| `ANDROID_KEYSTORE_FILE` | base64 keystore | Yes (same key) |
| `ANDROID_KEY_ALIAS` | key alias | Yes |
| `ANDROID_KEY_PASSWORD` | key password | Yes |
| `ANDROID_STORE_PASSWORD` | keystore password | Yes |
| `FIREBASE_APP_DISTRIBUTION_CREDENTIALS` | Firebase service account | Yes (one project) |
| `GP_SERVICE_ACCOUNT_JSON` | Google Play publishing | Yes (both packages) |

**The confusing parts**
1. One signing key for two distinct published apps — couples their signing
   identities and makes independent rotation/revocation impossible.
2. Secret names don't say which app they belong to, so it's unclear at a glance
   what is shared on purpose vs by accident.
3. A `default_web_client_id` workaround hints at OAuth client / SHA-1
   registration that was patched rather than designed.

---

## 2. Decision 1 — One Cloud project or one per app?

You asked for the trade-offs rather than a pick. Here they are, framed by your
constraint that **both apps need the same full `drive` scope.**

### Option A — One shared Google Cloud + Firebase project (recommended)

**Pros**
- **One OAuth verification, not two.** The full `drive` scope is *restricted*:
  Google requires brand verification **and** a CASA security assessment (often a
  recurring annual cost in money and effort). With one project and one OAuth
  consent screen, you do this **once** and cover both apps' OAuth clients. With
  two projects you do — and re-do annually — the whole thing **twice**.
- One consent screen, one set of enabled APIs, one billing account, one place for
  quotas and monitoring.
- Matches today's reality (both apps already live here and in one Firebase
  project), so little migration.
- One Play service account and one Firebase service account already manage both
  apps cleanly.

**Cons**
- Shared blast radius: a credential leak, quota exhaustion, or consent-screen
  suspension affects **both** apps at once.
- Shared Drive API quota between the two apps.
- Harder to cleanly hand off / sell / open-source one app later — you'd have to
  migrate it to its own project at that point.

### Option B — One project per app

**Pros**
- Full isolation: separate credentials, quotas, OAuth verification status, and
  billing. A problem in one app can't take down the other.
- Clean separation if an app may be spun off, transferred, or open-sourced.
- Each app's consent screen shows only its own branding.

**Cons**
- **Verification + CASA assessment done twice**, and maintained twice, every
  cycle — for the *same* restricted scope. This is the dominant cost given your
  constraint.
- Two consent screens, two billing setups, two sets of service accounts, two
  Firebase projects to keep in sync.
- More secrets and more places for configuration to drift.

### Recommendation

**Use one shared project (Option A).** Because both apps need the identical
restricted `drive` scope, separating projects roughly **doubles the verification
and security-assessment burden** while buying isolation you don't currently need
(same developer, same data access, same product family). Keep them together,
register a distinct Android OAuth client per package under the one consent screen,
and revisit only if you decide to spin an app off.

If you expect to sell or open-source one of these apps within the year, that
single fact flips the recommendation toward Option B for that app — tell me and
I'll adjust.

---

## 3. Decision 2 — How to organize signing keys

Today both apps sign with one identical upload key. Note that with **Play App
Signing**, Google holds the real *app signing key*; what you manage is the
**upload key**, which can be rotated and even shared. So this is lower-stakes than
it looks — but the current setup is still messier than it should be.

### Option A — Separate upload key per app (recommended)

**Pros**
- Independent rotation/revocation — compromising or losing one app's key never
  forces action on the other.
- Secrets map 1:1 to apps, so it's obvious what belongs where.
- Standard, future-proof: required anyway if an app ever moves to its own
  Play account/owner.

**Cons**
- Two keystores + two sets of passwords to store and back up.
- Slightly more CI wiring (per-app env / secrets).

### Option B — One keystore, separate alias per app

**Pros**
- Decouples the *keys* (different alias/cert per app) while keeping one file and
  one store password to manage.
- Lighter than Option A, cleaner than today.

**Cons**
- The keystore file and its store password are still shared — partial coupling.
- Losing/rotating the keystore touches both apps.

### Option C — Keep one shared key (status quo)

**Pros**
- Nothing to change; fewest files.

**Cons**
- Both published apps share one signing identity; can't rotate independently;
  ambiguous ownership. This is the source of the "confusing" feeling.

### Recommendation

**Separate upload key per app (Option A)** — it's the cleanest and removes the
coupling, and Play App Signing makes per-app upload-key rotation painless. If
you'd rather minimize files, **Option B** is a reasonable middle ground. I'd avoid
keeping the status quo.

---

## 4. OAuth clients & service accounts (applies under the one-project plan)

- **Android OAuth client per package** (required — clients are keyed by package +
  SHA-1):
  - one for `com.photoselectortoolbox`
  - one for `com.phototok`
  - Register **both** SHA-1s that will sign each app: your local/debug SHA-1 **and**
    the **Play App Signing SHA-1** from the Play Console (this is the usual cause
    of "sign-in works in debug but not in the Play build", and likely why the
    `default_web_client_id` workaround was added).
- **One OAuth consent screen** for the project, listing the `drive` and
  `drive.file` scopes, submitted for verification once.
- **One Web OAuth client** only if you actually need an ID token / server-side
  auth; otherwise drop the `default_web_client_id` workaround once the Android
  clients + SHA-1s are registered correctly.
- **One Google Play service account** can publish both packages — keep it shared,
  it's the normal pattern.
- **One Firebase service account** for App Distribution to both app records — keep
  shared.

So: **clients are per-app, the consent screen and service accounts are shared.**

---

## 5. Suggested naming conventions

Consistent, app-scoped names so it's obvious what's shared vs per-app.

### Products / packages (already good — keep)

| Thing | Photo Selector Toolbox | Photo Tok |
| --- | --- | --- |
| Gradle module | `:app` | `:phototok` |
| Package / applicationId | `com.photoselectortoolbox` | `com.phototok` |

> Optional: rename the `:app` module to `:toolbox` so both modules are named after
> their product rather than one being the generic `:app`. Cosmetic; only if you
> want symmetry.

### Cloud / Firebase (one shared project)

| Thing | Value |
| --- | --- |
| GCP / Firebase project id | `photo-selector-suite` (or keep `photo-selector-tb-dist`) |
| Project display name | `Photo Selector Suite` |
| Android OAuth client (Toolbox) | `android-photoselectortoolbox` |
| Android OAuth client (Photo Tok) | `android-phototok` |
| Web OAuth client (if needed) | `web-photo-selector-suite` |

> The current id `photo-selector-tb-dist` reads as "toolbox dist", which is
> confusing now that it hosts both apps. A neutral suite-level id is clearer. If
> you don't want to migrate the project id (it can't be changed in place — it
> means a new project), just keep the existing id and rely on the consistent
> client/secret names below.

### Signing keys (Option A — per app)

| Thing | Toolbox | Photo Tok |
| --- | --- | --- |
| Keystore file | `toolbox-upload.jks` | `phototok-upload.jks` |
| Key alias | `toolbox-upload` | `phototok-upload` |

### GitHub Actions secrets (app-scoped names)

| Purpose | Toolbox secret | Photo Tok secret |
| --- | --- | --- |
| Keystore (base64) | `TOOLBOX_KEYSTORE_FILE` | `PHOTOTOK_KEYSTORE_FILE` |
| Keystore password | `TOOLBOX_STORE_PASSWORD` | `PHOTOTOK_STORE_PASSWORD` |
| Key alias | `TOOLBOX_KEY_ALIAS` | `PHOTOTOK_KEY_ALIAS` |
| Key password | `TOOLBOX_KEY_PASSWORD` | `PHOTOTOK_KEY_PASSWORD` |

| Shared secret (rename for clarity) | New name |
| --- | --- |
| `FIREBASE_APP_DISTRIBUTION_CREDENTIALS` | keep (already clear) |
| `GP_SERVICE_ACCOUNT_JSON` | keep (already clear) — it's one Play account for both |

> If you go with Option B (one keystore, two aliases), keep a single
> `ANDROID_KEYSTORE_FILE` + `ANDROID_STORE_PASSWORD`, but split only the alias and
> key-password secrets per app (`TOOLBOX_KEY_ALIAS` / `PHOTOTOK_KEY_ALIAS`, etc.).

### Firebase App Distribution app ids (already distinct — document, don't change)

| App | App id |
| --- | --- |
| Photo Selector Toolbox | `1:211786657248:android:8c889798b3318b40f68f85` |
| Photo Tok | `1:211786657248:android:27e94ef870600f10f68f85` |

---

## 6. Migration checklist

A safe order of operations to reach the clean setup (one project, per-app keys):

1. **Google Cloud Console (one project)**
   - Confirm the Drive API is enabled and the OAuth consent screen lists `drive`
     and `drive.file`; submit/refresh restricted-scope verification once.
   - Create/clean up **two Android OAuth clients** named `android-photoselectortoolbox`
     and `android-phototok`.
   - For each, register both the debug SHA-1 and the **Play App Signing SHA-1**.
   - If sign-in then works without it, remove the `default_web_client_id` workaround.

2. **Signing keys (Option A)**
   - Generate `phototok-upload.jks` (and, if you want full symmetry, a fresh
     `toolbox-upload.jks`); since Play App Signing is in use, register the new
     upload key(s) via Play Console → App integrity if rotating.
   - Add the per-app GitHub secrets from §5.

3. **CI — `.github/workflows/build-android.yml`**
   - Split the signing env so the Toolbox build step reads `TOOLBOX_*` secrets and
     the Photo Tok build step reads `PHOTOTOK_*` secrets (today both read the same
     `ANDROID_*` ones).
   - Keep `FIREBASE_APP_DISTRIBUTION_CREDENTIALS` and `GP_SERVICE_ACCOUNT_JSON`
     shared.

4. **Docs**
   - Update `REQUIREMENTS.md` §7.10 to describe per-app keys/secrets and the
     one-project + per-package-OAuth-client model.

5. **Verify**
   - Build a signed release for **both** apps, install, and confirm Google Drive
     sign-in succeeds on a Play-signed build (not just debug).

---

## 7. Summary of recommendations

- **Cloud projects:** keep **one** shared project — your shared full-`drive` scope
  makes a second project mean a second (recurring) verification + security
  assessment for no isolation benefit you currently need.
- **OAuth:** one consent screen + service accounts shared; **one Android OAuth
  client per package**, each with debug **and** Play SHA-1 registered.
- **Signing:** move to **separate upload key per app**; rename secrets to be
  app-scoped (`TOOLBOX_*` / `PHOTOTOK_*`).
- **Naming:** app-scoped, consistent names for OAuth clients, keystores, aliases,
  and CI secrets; optionally a neutral suite-level project id and a `:toolbox`
  module name.
