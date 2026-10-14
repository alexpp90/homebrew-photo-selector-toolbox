package com.photoselectortoolbox.domain

/**
 * Single source of truth for Photo Selector Toolbox's public legal document URLs.
 *
 * The documents live in `docs/products/android-desktop/` in the repository and
 * are hosted via GitHub Pages. The same URLs must be entered in the Google Play
 * Console store listing.
 */
object LegalLinks {
    const val PRIVACY_POLICY = "https://alexpp90.github.io/homebrew-photo-selector-toolbox/products/android-desktop/privacy-policy.html"
    const val IMPRESSUM = "https://alexpp90.github.io/homebrew-photo-selector-toolbox/products/android-desktop/impressum.html"
    const val SOURCE_CODE = "https://github.com/alexpp90/homebrew-photo-selector-toolbox"
}
