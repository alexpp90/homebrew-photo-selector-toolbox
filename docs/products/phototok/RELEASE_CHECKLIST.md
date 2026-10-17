# PhotoTok (`:phototok`) — Release & Compliance Checklist

This checklist is the **single source of truth** for Google Play release readiness, DSGVO/GDPR privacy compliance, German DDG/Impressum compliance, and EU Digital Services Act (DSA) status for **PhotoTok** (`com.phototok`).

---

## 1. Architecture & Privacy Invariants (Repo-Side)

- [x] **100% Local On-Device SAF Architecture:** PhotoTok accesses user photos exclusively via Android Storage Access Framework (`Intent.ACTION_OPEN_DOCUMENT_TREE`). No cloud backend, no Google Drive OAuth, and no WebView Picker are used.
- [x] **Zero Runtime Permissions & Zero Package Queries:** `products/android/phototok/AndroidManifest.xml` declares zero `<uses-permission>` and zero `<queries>` entries (no `INTERNET`, no `READ_MEDIA_IMAGES`, no `READ_EXTERNAL_STORAGE`). System Share / Open With (`Intent.createChooser`) and Android 11+ `MediaStore` favourite/trash/delete requests (`createFavoriteRequest`, `createTrashRequest`, `createDeleteRequest`) run strictly through user-confirmed system sheets without storage permissions.
- [x] **Cloud Auto-Backup Hardened:** `AndroidManifest.xml` configures `android:allowBackup="false"`, `android:dataExtractionRules="@xml/data_extraction_rules"`, and `android:fullBackupContent="@xml/backup_rules"` so persisted SAF URI grants, local favourites/pending-delete journals, seen-photo fingerprints (`seen_photos.log`), and local culling statistics in app-private storage are never silently uploaded to cloud backup.
- [x] **Target & Compile SDK:** `targetSdk = 36` (meets Google Play August 31, 2026 API 36 requirement) and `compileSdk = 37` in `products/android/phototok/build.gradle.kts`.
- [x] **Hosted Legal Documents Present in Repo:**
  - `docs/products/phototok/privacy-policy.html` (bilingual EN/DE GDPR/DSGVO Art. 13 policy)
  - `docs/products/phototok/impressum.html` (bilingual DE/EN § 5 DDG legal notice)
- [x] **In-App Legal Links Wired:** `products/android/phototok/src/com/phototok/domain/LegalLinks.kt` defines `PRIVACY_POLICY`, `IMPRESSUM`, and `SOURCE_CODE`, surfaced in the Settings sheet ("About" section).

---

## 2. Google Play Data Safety Form Answers

Because PhotoTok requests zero permissions, has no network access, and processes all photos locally on-device:

| Play Console Question | Answer | Rationale |
|---|---|---|
| **Does your app collect or share any of the required user data types?** | **No** | All photo viewing, EXIF reading, seen-photo fingerprinting, culling statistics, `MediaStore` favourite/trash updates, and file operations (Copy/Move/Delete) happen strictly locally on the user's device. Explicit user-initiated sharing via the Android system share sheet (`Intent.createChooser`) does not constitute developer data collection or sharing under Play policy. |
| **Is all of the user data collected by your app encrypted in transit?** | **N/A** (No data collected) | No network transmission occurs. |
| **Do you provide a way for users to request that their data is deleted?** | **N/A** (No data collected) | Users control all local files directly and can clear app preferences or uninstall the app at any time. |
| **Advertising ID declaration** | **No** | App does not use Advertising ID and includes no ad SDKs. |

---

## 3. EU Digital Services Act (DSA) & German Legal Status

- [x] **Non-Trader Declaration:** PhotoTok is free, open-source (MIT), ad-free, and has no in-app purchases or monetization. Declared as **Non-Trader** in Google Play Console (private physical address is not publicly displayed on the store listing).
- [ ] **Human Review Before Public Store Launch:** If personal contact details in `docs/products/phototok/impressum.html` need to be customized beyond the GitHub contact address prior to public production release in Germany (§ 5 DDG), update `impressum.html` and verify GitHub Pages serving at the `LegalLinks.kt` URLs (`https://alexpp90.github.io/phototok-legal/privacy-policy.html` and `impressum.html`).

---

## 4. Play Console Manual Steps (Human-Only)

- [ ] Verify GitHub Pages is enabled and serves `privacy-policy.html` and `impressum.html` with HTTP 200.
- [ ] Paste the Privacy Policy URL into **Play Console → Policy and programs → App content → Privacy policy**.
- [ ] Submit the Data Safety form using the table in §2 above.
- [ ] Upload the first signed release `.aab` manually to the **Internal testing** track to initialize the `com.phototok` package in Play Console, then enable automated GitHub Actions publishing via `PHOTOTOK_GP_SERVICE_ACCOUNT_JSON`.
