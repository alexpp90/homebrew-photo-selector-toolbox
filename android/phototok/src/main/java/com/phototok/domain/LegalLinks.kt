package com.phototok.domain

/**
 * Single source of truth for Photo-Tok's public legal document URLs.
 *
 * The documents live in `docs/phototok/` in the repository and must be hosted
 * at a public URL (Google Play requires a reachable privacy-policy link even
 * when the repository is private — e.g. a small public "phototok-legal" repo
 * with GitHub Pages). Update these constants if the hosting location changes;
 * the same URLs must be entered in the Play Console store listing.
 */
object LegalLinks {
    const val PRIVACY_POLICY = "https://alexpp90.github.io/phototok-legal/privacy-policy.html"
    const val IMPRESSUM = "https://alexpp90.github.io/phototok-legal/impressum.html"
}
