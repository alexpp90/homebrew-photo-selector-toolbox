package com.photoselector.core

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import com.photoselector.core.storage.ExternalStorageDetector
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ExternalStorageDetectorTest {

    @Test
    fun `detectExternalVolumes returns mounted volumes and skips unmounted volumes`() {
        val context = mockk<Context>()
        val storageManager = mockk<StorageManager>()

        val primaryVolume = mockk<StorageVolume>()
        every { primaryVolume.state } returns Environment.MEDIA_MOUNTED
        every { primaryVolume.getDescription(context) } returns "Internal Shared Storage"
        every { primaryVolume.directory } returns File("/storage/emulated/0")
        every { primaryVolume.isRemovable } returns false
        every { primaryVolume.isPrimary } returns true
        every { primaryVolume.uuid } returns null

        val sdVolume = mockk<StorageVolume>()
        every { sdVolume.state } returns Environment.MEDIA_MOUNTED
        every { sdVolume.getDescription(context) } returns "SD Card"
        every { sdVolume.directory } returns File("/storage/1234-5678")
        every { sdVolume.isRemovable } returns true
        every { sdVolume.isPrimary } returns false
        every { sdVolume.uuid } returns "1234-5678"

        val unmountedVolume = mockk<StorageVolume>()
        every { unmountedVolume.state } returns Environment.MEDIA_UNMOUNTED

        every { context.getSystemService(Context.STORAGE_SERVICE) } returns storageManager
        every { storageManager.storageVolumes } returns listOf(primaryVolume, sdVolume, unmountedVolume)
        every { context.getExternalFilesDirs(null) } returns emptyArray()

        val detector = ExternalStorageDetector(context)
        val volumes = detector.detectExternalVolumes()

        assertEquals(2, volumes.size)
        assertEquals("Internal Shared Storage", volumes[0].description)
        assertEquals("/storage/emulated/0", volumes[0].path)
        assertFalse(volumes[0].isRemovable)
        assertTrue(volumes[0].isPrimary)

        assertEquals("SD Card", volumes[1].description)
        assertEquals("/storage/1234-5678", volumes[1].path)
        assertTrue(volumes[1].isRemovable)
        assertFalse(volumes[1].isPrimary)
        assertEquals("1234-5678", volumes[1].uuid)
    }

    @Test
    fun `detectRemovableVolumes filters out non-removable primary storage`() {
        val context = mockk<Context>()
        val storageManager = mockk<StorageManager>()

        val primaryVolume = mockk<StorageVolume>()
        every { primaryVolume.state } returns Environment.MEDIA_MOUNTED
        every { primaryVolume.getDescription(context) } returns "Internal Shared Storage"
        every { primaryVolume.directory } returns File("/storage/emulated/0")
        every { primaryVolume.isRemovable } returns false
        every { primaryVolume.isPrimary } returns true
        every { primaryVolume.uuid } returns null

        val usbVolume = mockk<StorageVolume>()
        every { usbVolume.state } returns Environment.MEDIA_MOUNTED_READ_ONLY
        every { usbVolume.getDescription(context) } returns "USB Drive"
        every { usbVolume.directory } returns File("/storage/ABCD-EF01")
        every { usbVolume.isRemovable } returns true
        every { usbVolume.isPrimary } returns false
        every { usbVolume.uuid } returns "ABCD-EF01"

        every { context.getSystemService(Context.STORAGE_SERVICE) } returns storageManager
        every { storageManager.storageVolumes } returns listOf(primaryVolume, usbVolume)
        every { context.getExternalFilesDirs(null) } returns emptyArray()

        val detector = ExternalStorageDetector(context)
        val removable = detector.detectRemovableVolumes()

        assertEquals(1, removable.size)
        assertEquals("USB Drive", removable[0].description)
        assertEquals("/storage/ABCD-EF01", removable[0].path)
        assertTrue(removable[0].isRemovable)
        assertEquals("ABCD-EF01", removable[0].uuid)
    }

    @Test
    @Config(sdk = [28])
    fun `detectExternalVolumes on API 28 below R resolves volume path from uuid without skipping`() {
        val context = mockk<Context>()
        val storageManager = mockk<StorageManager>()

        val sdVolume = mockk<StorageVolume>()
        every { sdVolume.state } returns Environment.MEDIA_MOUNTED
        every { sdVolume.getDescription(context) } returns "SD Card API 28"
        every { sdVolume.isRemovable } returns true
        every { sdVolume.isPrimary } returns false
        every { sdVolume.uuid } returns "9988-7766"

        every { context.getSystemService(Context.STORAGE_SERVICE) } returns storageManager
        every { storageManager.storageVolumes } returns listOf(sdVolume)
        every { context.getExternalFilesDirs(null) } returns emptyArray()

        val detector = ExternalStorageDetector(context)
        val volumes = detector.detectExternalVolumes()

        assertEquals(1, volumes.size)
        assertEquals("SD Card API 28", volumes[0].description)
        assertEquals("/storage/9988-7766", volumes[0].path)
        assertEquals("9988-7766", volumes[0].uuid)
        assertTrue(volumes[0].isRemovable)
    }
}
