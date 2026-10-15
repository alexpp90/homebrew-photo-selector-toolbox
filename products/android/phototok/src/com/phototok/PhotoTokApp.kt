package com.phototok

import android.app.Application
import coil.Coil
import com.phototok.image.PhotoTokImageLoader
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PhotoTokApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Coil.setImageLoader(PhotoTokImageLoader.create(this))
    }
}
