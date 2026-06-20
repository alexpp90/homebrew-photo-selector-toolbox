package com.photoselectortoolbox

import android.app.Application
import android.net.Uri
import android.util.Log
import coil.Coil
import coil.ImageLoader
import com.photoselectortoolbox.data.source.googledrive.DriveCoilFetcher
import com.photoselectortoolbox.data.source.googledrive.GoogleDriveImageSource
import dagger.hilt.android.HiltAndroidApp
import org.opencv.android.OpenCVLoader
import javax.inject.Inject

@HiltAndroidApp
class PhotoSelectorApp : Application() {

    companion object {
        private const val TAG = "PhotoSelectorApp"
    }

    @Inject lateinit var driveImageSource: GoogleDriveImageSource

    override fun onCreate() {
        super.onCreate()
        initOpenCV()
        initCoilWithDrive()
    }

    private fun initOpenCV() {
        if (OpenCVLoader.initLocal()) {
            Log.i(TAG, "OpenCV loaded successfully")
        } else {
            Log.e(TAG, "OpenCV initialization failed")
        }
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
