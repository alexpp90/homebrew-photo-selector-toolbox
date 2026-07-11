package com.phototok.domain

/**
 * Single source of truth for the app-managed folder names created inside
 * user-selected folders (previously duplicated as constants on the repository).
 */
object PhotoFolders {
    const val SELECTION = "PhotoTok_Selection"
    const val LEFT_SWIPE = "PhotoTok_LeftSwipe"

    /**
     * App-created folder in the Google Drive root used as the default
     * destination for collection actions on picked-file sources (a picked
     * selection is not a folder, so there is no source folder to default to).
     */
    const val DRIVE_APP_FOLDER = "PhotoTok"
}
