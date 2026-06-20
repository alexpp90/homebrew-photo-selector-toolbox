package com.phototok

import android.app.Application
import coil.Coil
import coil.ImageLoader
import com.phototok.data.source.googledrive.DriveCoilFetcher
import com.phototok.data.source.googledrive.GoogleDriveImageSource
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PhotoTokApp : Application() {

    @Inject lateinit var driveImageSource: GoogleDriveImageSource

    override fun onCreate() {
        super.onCreate()
        initCoilWithDrive()
    }

    private fun initCoilWithDrive() {
        val imageLoader = ImageLoader.Builder(this)
            .components {
                add(DriveCoilFetcher.Factory(driveImageSource))
            }
            .build()
        Coil.setImageLoader(imageLoader)
    }
}
