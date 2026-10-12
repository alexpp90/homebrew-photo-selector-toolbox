# PhotoTok — Legal & Regulatory Compliance Guide

> **Product:** PhotoTok (`products/android/phototok/`, package `com.phototok`)  
> **Target Audience:** Individual Open-Source Developers Publishing from Germany  
> **Status:** Authoritative Compliance Architecture (German DDG, GewO, EStG, EU DSA, GDPR/DSGVO)  
> **Associated Legal Documents:** `docs/products/phototok/legal/privacy-policy.html`, `docs/products/phototok/legal/impressum.html`  
> **Hosted Deployment Target:** `https://alexpp90.github.io/phototok-legal/` (aligned with `LegalLinks.kt`)  

---

## 1. Executive Summary & Regulatory Boundary

PhotoTok is an ultra-fast, gesture-first Android photo culling application engineered for smartphones of all tiers (portrait phone form factor, < 600 dp Compact window size class). Architecturally, PhotoTok is **100% local and offline**:
- Requests **zero permissions** in `AndroidManifest.xml` — notably omitting `android.permission.INTERNET`.
- Interacts with local storage exclusively through Android's **Storage Access Framework (SAF)** via user-granted directory tree URIs (`ACTION_OPEN_DOCUMENT_TREE`).
- Bundles **zero cloud SDKs, zero analytics or telemetry frameworks, zero crash reporting agents, and zero advertising SDKs**.
- Sets `android:allowBackup="false"` to prevent automated cloud synchronisation of local preferences to Google Drive.
- Is published under the permissive open-source **MIT License** free of charge (0.00 EUR download price, zero in-app purchases, zero subscriptions, zero monetisation).

Publishing software from Germany into public application marketplaces (Google Play Store, F-Droid, GitHub Releases) requires compliance across four distinct statutory frameworks:

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           REGULATORY COMPLIANCE TIERS                           │
├─────────────────────────┬─────────────────────────┬─────────────────────────────┤
│ 1. Trade & Tax Law      │ 2. Telemedia Law        │ 3. EU Digital Services Act  │
│ (§ 14 GewO / § 15 EStG) │ (§ 5 DDG / § 18 MStV)   │ (Regulation (EU) 2022/2065) │
│ • Non-commercial hobby  │ • Impressum obligation  │ • Non-Trader declaration    │
│   (Liebhaberei)         │   (geschäftsmäßig)      │   on Google Play            │
│ • No trade registration │ • Physical address      │ • Suppresses storefront     │
│   (Gewerbeanmeldung)    │   (ladungsfähig)        │   home address display      │
│ • Zero business tax     │ • Compliant c/o service │ • Keeps phone number        │
│   liability             │   shields home address  │   private                   │
├─────────────────────────┴─────────────────────────┴─────────────────────────────┤
│ 4. Data Protection & App Store Policy (GDPR / DSGVO & Google Play Data Safety) │
│ • 100% on-device processing qualifying for Art. 2(2)(c) GDPR household exemption│
│ • Developer is NOT a Data Controller under Art. 4(7) GDPR                       │
│ • Google Play Data Safety: "No data collected", "No data shared"                │
└─────────────────────────────────────────────────────────────────────────────────┘
```

This guide details the legal foundations, evaluates address privacy options, specifies exact Google Play Console answers, and provides an actionable developer checklist.

---

## 2. German Trade & Tax Law: Gewerbe vs. Liebhaberei

### 2.1. Statutory Criteria (§ 14 GewO & § 15 Abs. 2 EStG)

Under **§ 14 GewO (Gewerbeordnung)**, anyone who commences an independent commercial trade (*stehendes Gewerbe*) must notify the local trade office (*Gewerbeamt*) simultaneously. 

Under German tax law (**§ 15 Abs. 2 EStG**), a commercial business (*Gewerbebetrieb*) requires the simultaneous fulfillment of four cumulative criteria:
1. **Selbständigkeit** (Self-employed / independent action on one's own account and risk)
2. **Nachhaltigkeit** (Sustained, continuous activity intended to be repeated)
3. **Gewinnerzielungsabsicht** (Intent to generate an ongoing net financial profit)
4. **Beteiligung am allgemeinen wirtschaftlichen Verkehr** (Participation in general economic commerce)

### 2.2. Application to PhotoTok

PhotoTok operates under the following economic facts:
- **0.00 EUR Download Price:** Distributed free of charge on all distribution tracks.
- **No Monetisation Mechanics:** Zero in-app purchases, zero subscription tiers, zero paid feature locks.
- **No In-App Advertising:** Zero display ads, rewarded banners, or interstitial networks.
- **No Data Commercialisation:** Zero sale, leasing, or collection of telemetry, user metrics, or behavioral profiles.
- **Open Source Licensing:** Released under the MIT License, granting worldwide rights to use, copy, modify, and distribute the code freely.

### 2.3. Legal Classification: *Reine Liebhaberei*

Because **Gewinnerzielungsabsicht** is entirely absent:
- The development and publication of PhotoTok constitutes non-commercial activity classified under German law as **Liebhaberei** (hobby activity) or voluntary open-source contribution (*gemeinnütziges bzw. ehrenamtliches Engagement*).
- **No Trade Registration Required:** The developer is **not** required to submit a trade registration (*Gewerbeanmeldung*) to the local trade office (*Gewerbeamt*). In fact, registering a trade with zero intended revenue would be classified by the tax authorities (*Finanzamt*) as non-taxable *Liebhaberei*.
- **No Tax Liability:** There is no trade tax (*Gewerbesteuer*), corporate income tax (*Körperschaftsteuer*), or commercial profit taxation (*Einkünfte aus Gewerbebetrieb*).
- **No Chamber of Commerce Membership:** No mandatory membership fees to the IHK (*Industrie- und Handelskammer*).

### 2.4. Triggers for Commercial Reclassification

If the developer introduces monetization in the future, the legal status changes immediately:

| Future Action | Legal Consequence | Required Compliance |
|---|---|---|
| **In-App Donations**<br>(*Buy Me a Coffee*, *GitHub Sponsors*, *Patreon*) | Minor voluntary tips may be accepted without profit intent if they merely offset hosting expenses. However, regular recurring tips exceeding incidental costs risk being classified as taxable other income (§ 22 Nr. 3 EStG) or commercial sponsoring. | Maintain accounting of server/tool expenses vs. receipts. If income exceeds 256 EUR/year, declare to *Finanzamt*. |
| **In-App Advertising**<br>(*AdMob*, *Unity Ads*) | Introduces automated commercial revenue. Immediately establishes a *Gewerbebetrieb*. | Mandatory *Gewerbeanmeldung*, VAT registration (*USt-IdNr.* or *Kleinunternehmerregelung* § 19 UStG), switch to DSA Trader status. |
| **Paid In-App Features / Pro Tier** | Commercial app distribution. | Mandatory *Gewerbeanmeldung*, corporate tax accounting, consumer warranty compliance, DSA Trader status. |

---

## 3. German Telemedia Law: § 5 DDG Impressumspflicht

### 3.1. Statutory Transition: TMG to DDG (May 14, 2024)

On **May 14, 2024**, the German Telemediengesetz (TMG) was formally repealed and replaced by the **Digitale-Dienste-Gesetz (DDG)**, executing Regulation (EU) 2022/2065 (Digital Services Act) into German national law.
- The provider identification requirement (*Impressumspflicht*) previously located in **§ 5 TMG** is now codified in **§ 5 DDG** ("Allgemeine Informationspflichten").
- References in documentation or source code to "§ 5 TMG" are legally obsolete and must cite **§ 5 DDG**.

### 3.2. Why PhotoTok Requires an Impressum

Under § 5 Abs. 1 DDG, service providers must provide identification for:
> *„... geschäftsmäßige, in der Regel gegen Entgelt angebotene digitale Dienste ...“*

1. **"Geschäftsmäßig" is Broader than "Gewerblich":** In established German civil jurisprudence (Bundesgerichtshof, OLG Hamburg, OLG München), an offering is "geschäftsmäßig" if it is intended for a permanent or indefinite duration (*auf Dauer angelegt*) and offered publicly, **even without profit motive**.
2. **Public App Marketplace Distribution:** Offering an app on Google Play or public repositories to an unrestricted audience is considered *geschäftsmäßig*.
3. **§ 18 Abs. 1 MStV (Medienstaatsvertrag):** Telemedia that are not purely for personal or family purposes (*persönliche oder familiäre Zwecke*) require provider identification (name and address). Because the app is downloaded by thousands of strangers, it extends beyond personal/family circles.
4. **Risk of Cease-and-Desist (*Abmahnrisiko*):** Omitting an Impressum exposes the publisher to civil cease-and-desist warnings (*Abmahnungen*) under § 3a UWG (*Gesetz gegen den unlauteren Wettbewerb*) by competitors or consumer protection watchdogs.

**Conclusion:** Providing a complete, legally compliant Impressum is mandatory and indispensable for publishing PhotoTok.

---

## 4. Address Compliance: *Ladungsfähige Anschrift* & Privacy Shielding

### 4.1. The Legal Mandate

§ 5 Abs. 1 Nr. 1 DDG requires the disclosure of:
> *„den Namen und die Anschrift, unter der sie niedergelassen sind...“*

Jurisprudence mandates that this address must be a **ladungsfähige Anschrift** — a physical geographical address where official court bailiffs (*Gerichtsvollzieher*) and formal registered postal notices (*Zustellungen*) can reliably serve legal summonses in person or by physical mail.

### 4.2. Analysis of Address Options

| Address Option | Legally Compliant? | Privacy Level | Legal Risk & Practical Evaluation |
|---|---|---|---|
| **Postfach (P.O. Box)** | ❌ **Strictly Illegal** | High | **Unlawful.** German courts unanimously hold that a P.O. Box is *not* ladungsfähig because court bailiffs cannot effect formal legal service to a locker. Using a P.O. Box alone triggers immediate civil warnings (*Abmahnungen*). |
| **Virtual Mailbox / Scan Service (without agent)** | ⚠️ **High Risk** | High | Dubious legality if the service provider operates without local physical presence or refuses in-person legal service. If court summonses bounce, default judgments (*Versäumnisurteil*) can be issued. |
| **c/o Impressum Service with Authorized Agent (*Zustellungsbevollmächtigter*)** | ✅ **Compliant** | 🛡️ **High (Home address protected)** | **Recommended Solution.** A specialized service provides a physical street address with a contractually appointed representative authorized to accept legal service (*Zustellungsbevollmächtigter*). Court mail is physically accepted and scanned/forwarded immediately to the developer. |
| **Coworking Space / Commercial Office** | ✅ **Compliant** | 🛡️ **High** | Valid if the developer has a contracted postal agreement and the developer's full name is visibly mounted on the physical mailbox or directory board. |
| **Private Residential Address** | ✅ **Compliant** | ❌ **Zero Privacy** | Fully compliant, but exposes the developer's private home address to web scrapers, automated search engines, and potential harassment. |

### 4.3. Compliant c/o Address Structure

When using an authorized representation service, the address must be structured as follows:

```text
[Developer Full Legal Name]
c/o [Authorized Service Provider Name]
[Street and House Number]
[Postal Code and City]
Germany
```

*Requirements for the c/o relationship:*
1. The developer must have an active legal service contract with the provider.
2. The contract must grant the provider express power of attorney for postal service (*Zustellungsbevollmächtigung*).
3. The provider must guarantee immediate digital forwarding of incoming official notices (as legal answer deadlines in Germany run from the day of service at the c/o address).

### 4.4. Mandatory Contents of the § 5 DDG Impressum

1. **Full Legal Name:** Natural person's first and last name (pseudonyms or online handles are strictly invalid).
2. **Physical Postal Address:** Valid residential address or compliant c/o service address (never a P.O. Box).
3. **Electronic Contact Channels (§ 5 Abs. 1 Nr. 2 DDG):**
   - Active email address with prompt response capability.
   - Contact form URL or phone number satisfying rapid communication requirements (EuGH C-298/07).
4. **Statement of Non-Commercial / Unregistered Status:**
   - Explicit declaration: *Kein Eintrag im Handelsregister* (No commercial registry entry).
   - Explicit declaration: *Keine Umsatzsteuer-Identifikationsnummer gemäß § 27a UStG* (No VAT identification number).
5. **Editorial Responsibility (§ 18 Abs. 2 MStV):** Name and address of the individual responsible for journalistic or editorial content.
6. **Consumer Dispute Resolution Notice:**
   - Link to the EU Online Dispute Resolution platform (`https://ec.europa.eu/consumers/odr/`).
   - Declaration under **§ 36 VSBG** (Verbraucherstreitbeilegungsgesetz): The developer is neither willing nor obligated to participate in dispute resolution proceedings before a consumer arbitration board.
7. **Two-Click Accessibility:** Reached within two clicks from anywhere in the application (implemented in PhotoTok via `SettingsScreen.kt` → "Legal Notice (Impressum)" linking to `LegalLinks.IMPRESSUM`).

---

## 5. EU Digital Services Act (DSA) — Non-Trader Status on Google Play

### 5.1. Regulatory Context (Regulation (EU) 2022/2065, Art. 30)

Article 30 of the EU Digital Services Act mandates that online platforms (such as Google Play) verify and display the commercial identity of all app publishers offering goods or services to EU consumers.

Under Art. 3(f) of the DSA and Directive 2005/29/EC, a **Trader** (*Unternehmer*) is defined as:
> *„any natural person or any legal person ... who is acting ... for purposes relating to their trade, business, craft or profession.“*

Conversely, a **Non-Trader** is an individual acting outside any trade, business, craft, or profession.

### 5.2. Qualification of the PhotoTok Developer

Because PhotoTok is:
1. Developed as a private, open-source personal project.
2. Distributed completely free of charge (0.00 EUR).
3. Free of in-app advertisements, analytics monetisation, and paid upgrades.
4. Not connected to any commercial entity or professional service offering.

The developer **strictly qualifies as a Non-Trader** under the DSA.

### 5.3. Impact on Google Play Storefront Privacy

This distinction is decisive for developer privacy on the Google Play Store:

| Storefront Attribute | Declared as **Trader** | Declared as **Non-Trader** |
|---|---|---|
| **Public Storefront Street Address** | ⚠️ **Publicly Displayed** to all European consumers on the Play Store page | 🛡️ **SUPPRESSED / HIDDEN** from the public Play Store page |
| **Public Phone Number** | ⚠️ **Publicly Displayed** | 🛡️ **SUPPRESSED / HIDDEN** |
| **Public Developer Email** | Displayed | Displayed |
| **D-U-N-S Number Required?** | Required for organizations | ❌ Not required for individual accounts |
| **Consumer Protection Banner** | Standard commercial terms | Displayed notice: *"The developer has declared that this product does not constitute a commercial trade, business, craft, or profession. Consumer rights resulting from European consumer protection law do not apply."* |

### 5.4. Distinction from Internal Google Play KYC Verification

Declaring Non-Trader status **does not exempt** the developer from Google's private internal identity verification (KYC):
- Google requires individual developers to verify their identity with an official government ID (passport or national ID card) and proof of residential address (bank statement or utility bill).
- This internal verification is confidential between Google and the developer.
- **The Non-Trader declaration guarantees that Google does not publish this verified private address on the public Google Play Store page.**

### 5.5. Step-by-Step Google Play Console Non-Trader Declaration

1. Sign in to the [Google Play Console](https://play.google.com/console/).
2. Select your developer account.
3. In the left navigation menu, navigate to **Developer account** → **Account details** (or **About you**).
4. Locate the **EU Digital Services Act (DSA)** declaration card.
5. Under the prompt: *"Are you a trader in the context of the Digital Services Act?"*:
   - Select **"No, I am not a trader"**.
6. Review the declaration and click **Save**.

---

## 6. GDPR / DSGVO Compliance Analysis for PhotoTok

### 6.1. Architecture & Data Flow Isolation

```
┌────────────────────────────────────────────────────────────────────────┐
│                        ANDROID MOBILE HARDWARE                         │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                    PhotoTok Application                          │  │
│  │                                                                  │  │
│  │  [Storage Access Framework (SAF)]                                │  │
│  │    • User explicitly grants directory via ACTION_OPEN_DOCUMENT_TREE  │
│  │    • Bitmaps rendered via Coil into volatile memory cache        │  │
│  │    • EXIF read via androidx.exifinterface in RAM                 │  │
│  │    • File operations (Move/Copy/Trash) via DocumentFile          │  │
│  │                                                                  │  │
│  │  [Local Sandbox Jetpack DataStore Preferences]                   │  │
│  │    • folder_pos_v2_<uri> (numerical last viewed photo index)     │  │
│  │    • Configuration flags (haptics, coach mark seen, gestures)    │  │
│  │    • android:allowBackup="false" (cloud backup disabled)         │  │
│  │  └───────────────────────────────────────────────────────────────┘  │
│                                 │                                      │
│                                 ▼                                      │
│  ╔══════════════════════════════════════════════════════════════════╗  │
│  ║             ZERO NETWORK PERMISSIONS IN MANIFEST                 ║  │
│  ║          android.permission.INTERNET is NOT requested            ║  │
│  ║    OS Kernel blocks all TCP/UDP sockets and outbound packets     ║  │
│  ╚══════════════════════════════════════════════════════════════════╝  │
│                                 │                                      │
│                                 ▼                                      │
│             [ZERO BYTES TRANSMITTED OFF THE DEVICE]                    │
└────────────────────────────────────────────────────────────────────────┘
```

1. **Zero Network Permissions (`REQ-TOK-ARCH.19`):** `AndroidManifest.xml` requests no network permissions. The Android Linux kernel strictly forbids the app process from creating sockets or reaching network interfaces.
2. **Storage Access Framework (SAF):** PhotoTok requests no broad storage permissions (`MANAGE_EXTERNAL_STORAGE`, `READ_EXTERNAL_STORAGE`). Access is strictly confined to user-selected folders via system document pickers.
3. **Local In-Memory Computation:** EXIF extraction, image decodes, and file moves execute exclusively on-device in volatile RAM.
4. **Local Configuration Preferences:** Jetpack DataStore stores only local operational keys (`folder_pos_v2`, bookmarks, UI flags). No identifiers, usernames, or telemetry are recorded.
5. **Backup Suppression:** `android:allowBackup="false"` in `AndroidManifest.xml` prohibits the operating system from uploading app preferences to Google Drive.
6. **Zero Tracking SDKs:** The dependency tree contains no Google Analytics, Firebase Analytics, Crashlytics, Sentry, or advertising SDKs.

### 6.2. Statutory Evaluation under GDPR (DSGVO)

1. **Photographs as Personal Data (Art. 4 Nr. 1 DSGVO):** User photos frequently portray natural persons and contain embedded EXIF metadata (geographic GPS coordinates, camera serial numbers, capture timestamps), qualifying as personal data.
2. **Household Exemption (Art. 2 Abs. 2 lit. c DSGVO):** The processing of personal photos by an individual user on their personal smartphone falls under the purely personal or household activity exemption (*Haushaltsausnahme*), exempting the user from GDPR record-keeping.
3. **Developer is NOT a Data Controller (Art. 4 Nr. 7 DSGVO):** A Data Controller is defined as the entity that determines the purposes and means of the processing of personal data. Because PhotoTok transmits **zero bytes** to the developer, the developer neither collects, stores, transmits, accesses, nor controls any user data. PhotoTok is an offline software tool operating entirely within the user's sovereign domain.

### 6.3. Google Play Console Data Safety Form Inputs

When completing the mandatory **Data safety** questionnaire in Google Play Console:

| Question / Section | Input / Answer | Technical Justification |
|---|---|---|
| **Does your app collect or share any of the required user data types?** | **No** | The application operates 100% locally. Zero data leaves the physical device. |
| **Is all user data collected by your app encrypted in transit?** | **N/A** (Option disabled) | No data is collected or transmitted over any network. |
| **Do you provide a way for users to request that their data be deleted?** | **N/A** (Option disabled) | The developer holds no user data on servers. Data resides solely in the user's local device files and internal app sandbox, removed when files are deleted or the app is uninstalled. |
| **Is your app target audience children?** | **No** | General photographic utility tool. |

**Resulting Play Store Badges:**  
- 🛡️ **No data collected:** The developer says this app doesn't collect user data.
- 🛡️ **No data shared with third parties:** The developer says this app doesn't share user data with other companies or organizations.

---

## 7. Legal Hosting Architecture & `LegalLinks.kt` Alignment

### 7.1. Canonical URL Contract (`LegalLinks.kt`)

PhotoTok hardcodes public legal endpoints in `products/android/phototok/src/com/phototok/domain/LegalLinks.kt`:

```kotlin
object LegalLinks {
    const val PRIVACY_POLICY = "https://alexpp90.github.io/phototok-legal/privacy-policy.html"
    const val IMPRESSUM = "https://alexpp90.github.io/phototok-legal/impressum.html"
}
```

This contract is verified by automated unit tests in `products/android/phototok/tests/unit/com/phototok/domain/LegalLinksTest.kt` (`REQ-TOK-ARCH.20` and `REQ-TOK-ARCH.19`).

### 7.2. Public Hosting Strategy: `phototok-legal` GitHub Pages

Google Play Console requires that the Privacy Policy link be an **active, publicly accessible HTTPS URL** before submitting an application for review.

```
Monorepo (Internal / Private or Public)
├── docs/products/phototok/
│   ├── LEGAL_COMPLIANCE.md          <-- Authoritative legal guide & checklist
│   └── legal/
│       ├── privacy-policy.html      <-- Master ready-to-host privacy policy
│       └── impressum.html           <-- Master ready-to-host Impressum
│
└── [Deploy / Sync to Public Repo]
        │
        ▼
GitHub Public Repository: alexpp90/phototok-legal (Branch: main)
├── index.html                       <-- Optional landing or redirect page
├── privacy-policy.html              <-- Hosted at https://alexpp90.github.io/phototok-legal/privacy-policy.html
└── impressum.html                   <-- Hosted at https://alexpp90.github.io/phototok-legal/impressum.html
```

#### Advantages of the Dedicated `phototok-legal` Repository:
1. **Public Reachability Guaranteed:** GitHub Pages on private repositories requires a paid GitHub Pro/Enterprise tier. A dedicated, lightweight public repository (`phototok-legal`) provides free, permanent 24/7 HTTPS hosting.
2. **Zero Code Changes:** Matches the exact constants already tested in `LegalLinks.kt`.
3. **Decoupled Updates:** Legal address or email changes can be pushed to GitHub Pages instantly without rebuilding or re-submitting Android APK/AAB binaries to Google Play.

### 7.3. Ready-to-Deploy Legal HTML Specifications

The master templates in `docs/products/phototok/legal/` meet strict technical and aesthetic standards:
- **100% Standalone CSS:** Zero external CSS frameworks, zero Google Fonts, zero CDN dependencies.
- **Privacy First:** Zero tracking pixels, zero analytics scripts, zero cookies, zero local storage invocations.
- **Dark & Light Mode Support:** Automatic color scheme switching using CSS `@media (prefers-color-scheme: dark)`.
- **System Font Stack:** Seamless integration using native system typography (`-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif`).
- **Bilingual Structure:** Clean German (*Deutsch*) and English (*English*) parallel sections with anchor navigation.
- **Explicit Placeholders:** Prominent placeholders (`[IHR VOLLSTÄNDIGER NAME]`, `[IHRE STRASSE UND HAUSNUMMER]`, `[IHRE E-MAIL-ADRESSE]`) for developer details.

---

## 8. Actionable Developer Checklist

### Phase 1: Legal Preparation & Address Decision
- [ ] **Decide on Impressum Address:**
  - Option A: Contract an authorized c/o representation service (e.g., *Autorenglück*, *Impressumsservice*) to keep private home address shielded.
  - Option B: Use registered commercial office / coworking space address with physical nameplate.
  - Option C: Use personal residential address (understand public indexing implications).
- [ ] **Prepare Placeholders:** Collect full legal name, chosen physical address, and dedicated developer contact email.

### Phase 2: Deploy Public Legal Pages (`alexpp90/phototok-legal`)
- [ ] **Create GitHub Repository:** Create a public GitHub repository named `phototok-legal` under user `alexpp90`.
- [ ] **Populate HTML Files:**
  - Copy `docs/products/phototok/legal/privacy-policy.html` and `docs/products/phototok/legal/impressum.html` into repository root.
  - Replace all bracketed placeholders (`[...]`) with verified contact details.
  - Add an `index.html` redirecting to `privacy-policy.html`.
- [ ] **Enable GitHub Pages:** Go to repository **Settings** → **Pages** → Build and deployment: **Deploy from a branch** → Branch: `main` / `(root)` → **Save**.
- [ ] **Verify Live HTTPS Endpoints:**
  ```bash
  curl -I https://alexpp90.github.io/phototok-legal/privacy-policy.html
  curl -I https://alexpp90.github.io/phototok-legal/impressum.html
  ```
  Ensure both return `HTTP/2 200 OK`.

### Phase 3: Google Play Console Account Setup & DSA Verification
- [ ] **Complete Private Identity Verification (KYC):** Upload government ID and address document in Play Console (remains confidential with Google).
- [ ] **Declare EU DSA Non-Trader Status:**
  - Navigate to **Developer account** → **Account details** → **EU Digital Services Act (DSA)**.
  - Select **"No, I am not a trader"** and save.
- [ ] **Confirm Storefront Address Suppression:** Verify that the public developer profile on the Play Store does not display a physical street address.

### Phase 4: App Content & Policy Questionnaires in Google Play Console
- [ ] **Store Listing Privacy Policy:**
  - Navigate to **Store presence** → **Store settings** → **Privacy Policy**.
  - Enter: `https://alexpp90.github.io/phototok-legal/privacy-policy.html`.
- [ ] **Data Safety Form Submission:**
  - Does app collect or share data? → Select **"No"**.
  - Confirm resulting badges: "No data collected", "No data shared".
- [ ] **Target Audience & Content Rating:**
  - Declare target age 13+ (not directed at children).
  - Complete IARC questionnaire (utility app, zero violence/nudity).

### Phase 5: Verification & Automated Tests
- [ ] Run automated legal and permission tests:
  ```bash
  cd products/android && ./gradlew :phototok:testDebugUnitTest --tests "com.phototok.domain.LegalLinksTest"
  ```
  Confirm 100% pass rate.
- [ ] Verify release build passes with zero network permissions:
  ```bash
  cd products/android && ./gradlew :phototok:assembleRelease :phototok:bundleRelease
  ```

---

## 9. Regulatory Summary Matrix

| Regulatory Requirement | Legal Basis | PhotoTok Implementation | Verification Status |
|---|---|---|---|
| **Trade Registration (Gewerbe)** | § 14 GewO / § 15 EStG | Non-commercial hobby (*Liebhaberei*); 0.00 EUR price, zero ads, zero IAP. | ✅ Exempt (No trade registration needed) |
| **Provider Identification (Impressum)** | § 5 DDG, § 18 MStV | In-app 2-click access via Settings; hosted bilingual HTML at `LegalLinks.IMPRESSUM`. | ✅ Compliant (Physical address required) |
| **Address Privacy** | BGH jurisprudence on *Ladungsfähigkeit* | Supported via compliant c/o service with authorized agent (*Zustellungsbevollmächtigter*). | ✅ Compliant (P.O. Box prohibited) |
| **DSA Storefront Privacy** | Art. 30 Regulation (EU) 2022/2065 | Declared as **Non-Trader** in Google Play Console. | ✅ Storefront address suppressed |
| **Data Protection (GDPR/DSGVO)** | Art. 2(2)(c), Art. 4(7) DSGVO | 100% offline, zero network permissions, developer is not a Data Controller. | ✅ Exempt / Fully Compliant |
| **Google Play Data Safety** | Google Play Developer Policy | Form answered "No data collected", "No data shared". | ✅ Cleanest safety badge |
