---
name: release-compliance
description: "Check a change for Google Play, DSGVO/GDPR, German DDG/Impressum and EU DSA impact: OAuth scope policy (drive.file only), privacy-policy-to-code sync, Data Safety answers, target-API deadlines, non-trader status. Use before any release and for any change touching permissions, endpoints, SDKs or data flow."
allowed-tools: Read, Grep, Glob, Edit, WebSearch, WebFetch
---

# Release and compliance check

Owned by `@shared-publish-agent`. Applies to PhotoTok (`:phototok`) and Android Desktop (`:android-desktop`) releases and to any
change anywhere that alters what data the apps access or transmit.

## Read first

- `docs/products/phototok/REQUIREMENTS.md` and `docs/products/android-desktop/REQUIREMENTS.md`
- `docs/products/phototok/RELEASE_CHECKLIST.md` and `docs/products/android-desktop/RELEASE_CHECKLIST.md` — single source of truth for open release tasks
- `ai/memory/sentinel.md` — contains the `drive.file` / zero-permission SAF scope lessons

## The invariants

1. **Zero-permission SAF & OAuth scope policy.** Both `:phototok` and `:android-desktop` operate 100% locally via the Android Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`) with zero `<uses-permission>` entries (not even `INTERNET`). If Google Drive integration is ever re-introduced, only the non-restricted `https://www.googleapis.com/auth/drive.file` scope is permitted; any change introducing `drive` or `drive.readonly` triggers Google restricted-scope verification plus an annual paid CASA assessment — reject or escalate it.
2. **Privacy policy ⇄ code.** `docs/products/{phototok,android-desktop}/privacy-policy.html` document 100% on-device SAF processing, zero runtime permissions, and no analytics/ads/crash reporting. A change that adds a permission, network endpoint, SDK or data flow updates the privacy policy, the Data Safety answers, and the checklist **in the same change**.
3. **Non-trader status.** The apps are free, ad-free and non-monetized; the developer is declared a non-trader under the DSA, so the physical address stays private. Any monetization proposal changes trader status, public address disclosure, Impressum wording, and potentially tax status — flag all four.
4. **In-app legal links.** Settings in both `:phototok` and `:android-desktop` keep working "Privacy Policy" and "Legal Notice (Impressum)" links. URLs live only in each product's `LegalLinks.kt`.
5. **Target-API deadline.** Play requires staying within roughly one year of the latest Android release; new-app submissions require API 36 from 31 Aug 2026. Verify `targetSdk` in `products/android/{phototok,android-desktop}/build.gradle.kts` against **current** Google policy.

## Triage: does this change need a compliance pass?

Yes if the diff touches any of:

- `AndroidManifest.xml` permissions
- a network endpoint, or any new SDK or third-party dependency
- OAuth scopes, sign-in, or Drive access
- anything reading or writing user files outside SAF-granted trees
- store listing text, screenshots, or app metadata
- `LegalLinks.kt` or the hosted legal documents

## Rules

1. **Facts, not memory.** Play policies, fees, tester counts and deadlines change. Verify
   every present-day claim against current official documentation before asserting it. This
   is what `WebSearch`/`WebFetch` are granted for.
2. **You are not a lawyer.** For German legal edge cases (Impressum obligation, GDPR
   controller analysis), state the practical consensus, cite sources, and recommend
   professional advice for high-stakes ambiguity. Never remove the developer's opportunity to
   review legal texts before publication.
3. **Human-only steps stay human.** Play Console registration and ID verification, payments,
   Google Cloud Console clicks, and tester recruitment cannot be performed by an agent.
   Prepare exact instructions and keep the checklist truthful — do not mark them done.
4. **No secrets in the repo.** Signing keys and release credentials come from GitHub secrets/variables
   (`KEYSTORE_FILE`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, `PLAY_SERVICE_ACCOUNT_JSON`) via environment variables.

## Output

Report as: what changed → which invariant it touches → what must be updated → what a human
must do in a console. Update the checklist's ☐/☑ status for anything that genuinely completed.
