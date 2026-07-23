package com.photoselectortoolbox.ui

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.photoselectortoolbox.MainActivity
import com.photoselectortoolbox.data.cache.ScoreDao
import com.photoselectortoolbox.data.cache.ScoreEntity
import com.photoselectortoolbox.data.model.ExifData
import com.photoselectortoolbox.data.model.ImageItem
import com.photoselectortoolbox.data.repository.FakeImageRepository
import com.photoselectortoolbox.data.repository.ImageRepository
import com.photoselectortoolbox.data.repository.SettingsRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SelectorScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var repository: ImageRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var scoreDao: ScoreDao

    private val fakeRepo: FakeImageRepository
        get() = repository as FakeImageRepository

    private val mockImages = listOf(
        ImageItem(
            uri = "gdrive://test_folder/image1.jpg",
            fileName = "image1.jpg",
            fileSize = 1024L,
            lastModified = 1000L,
            mimeType = "image/jpeg",
            imageWidth = 1920,
            imageHeight = 1080,
            exifData = ExifData(
                shutterSpeed = 0.005, // 1/200s
                aperture = 2.8,
                iso = 100,
                focalLength = 50.0,
                focalLength35mm = 50.0,
                lens = "FE 50mm F1.2 GM",
                isFallback = false
            )
        ),
        ImageItem(
            uri = "gdrive://test_folder/image2.jpg",
            fileName = "image2.jpg",
            fileSize = 2048L,
            lastModified = 2000L,
            mimeType = "image/jpeg",
            imageWidth = 1920,
            imageHeight = 1080,
            exifData = ExifData(
                shutterSpeed = 0.008, // 1/125s
                aperture = 4.0,
                iso = 200,
                focalLength = 85.0,
                focalLength35mm = 85.0,
                lens = "FE 85mm F1.4 GM",
                isFallback = false
            )
        )
    )

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            scoreDao.deleteAll()
            settingsRepository.setLastFolderUri(null)
            settingsRepository.setSortingEnabled(true)
            settingsRepository.setGroupingEnabled(false)
        }
    }

    @After
    fun teardown() {
        runBlocking {
            scoreDao.deleteAll()
            settingsRepository.setLastFolderUri(null)
        }
    }

    @Test
    fun initialEmptyState_isDisplayed() {
        // App starts with no folder selected, should show the empty state card.
        composeRule.waitUntil(timeoutMillis = 15000) {
            composeRule.onAllNodesWithText("No Photos Loaded", ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("No Photos Loaded", ignoreCase = true).assertIsDisplayed()
        composeRule.onNodeWithText("Select a folder to start reviewing and culling your photos.", substring = true, ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun folderLoaded_reviewUiAndExifShown() {
        // Prepare mock images
        fakeRepo.imagesFlow.value = mockImages

        // Simulate folder loading by setting preferred folder URI
        runBlocking {
            settingsRepository.setLastFolderUri("gdrive://test_folder")
        }

        // Wait until empty state disappears and review UI appears
        composeRule.waitUntil(timeoutMillis = 15000) {
            composeRule.onAllNodesWithText("image1.jpg", ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Dismiss gesture tutorial overlay if shown
        dismissGestureTutorialIfShown()

        // Verify active photo and EXIF details are shown in UI
        composeRule.onNodeWithText("image1.jpg", ignoreCase = true).assertIsDisplayed()
        composeRule.onNodeWithText("1/200s", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("f/2.8", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("ISO 100", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("50mm", substring = true).assertIsDisplayed()
    }

    @Test
    fun navigateBetweenImages_updatesActiveExif() {
        fakeRepo.imagesFlow.value = mockImages
        runBlocking {
            settingsRepository.setLastFolderUri("gdrive://test_folder")
        }

        composeRule.waitUntil(timeoutMillis = 15000) {
            composeRule.onAllNodesWithText("image1.jpg", ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        dismissGestureTutorialIfShown()

        // Determine layout: check for compact culling button vs expanded layout
        val isCompact = composeRule.onAllNodesWithTag("copy_button_compact")
            .fetchSemanticsNodes().isNotEmpty()

        if (isCompact) {
            // In compact layout, swipe left to navigate to next page
            composeRule.onAllNodesWithContentDescription("image1.jpg").onFirst().performTouchInput {
                swipeLeft()
            }
        } else {
            // In expanded layout, click the Next image column's clickable box
            composeRule.onNodeWithTag("column_next").performClick()
        }

        composeRule.waitForIdle()

        // Verify the second image details are now shown
        composeRule.onNodeWithText("image2.jpg", ignoreCase = true).assertIsDisplayed()
        composeRule.onNodeWithText("1/125s", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("f/4.0", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("ISO 200", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("85mm", substring = true).assertIsDisplayed()
    }

    @Test
    fun cullingAction_CopyAndMove_showSnackbar() {
        fakeRepo.imagesFlow.value = mockImages
        runBlocking {
            settingsRepository.setLastFolderUri("gdrive://test_folder")
        }

        composeRule.waitUntil(timeoutMillis = 15000) {
            composeRule.onAllNodesWithText("image1.jpg", ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        dismissGestureTutorialIfShown()

        val isCompact = composeRule.onAllNodesWithTag("copy_button_compact")
            .fetchSemanticsNodes().isNotEmpty()

        // Click Copy Button
        if (isCompact) {
            composeRule.onNodeWithTag("copy_button_compact").performClick()
        } else {
            composeRule.onNodeWithTag("copy_button_expanded").performClick()
        }
        composeRule.waitForIdle()

        try {
            composeRule.waitUntil(timeoutMillis = 15000) {
                composeRule.onAllNodesWithText("Copied to Selection", ignoreCase = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: Exception) {
            try { composeRule.onRoot().printToLog("SelectorScreenTest_Copy") } catch (_: Exception) {}
            throw e
        }
        composeRule.onNodeWithText("Copied to Selection", ignoreCase = true).assertIsDisplayed()

        // Wait for the copy snackbar to disappear so it doesn't block the move button on phone layouts
        composeRule.waitUntil(timeoutMillis = 15000) {
            composeRule.onAllNodesWithText("Copied to Selection", ignoreCase = true)
                .fetchSemanticsNodes().isEmpty()
        }

        // Click Move Button
        if (isCompact) {
            composeRule.onNodeWithTag("move_button_compact").performClick()
        } else {
            composeRule.onNodeWithTag("move_button_expanded").performClick()
        }
        composeRule.waitForIdle()

        try {
            composeRule.waitUntil(timeoutMillis = 15000) {
                composeRule.onAllNodesWithText("Moved to Selection", ignoreCase = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: Exception) {
            try { composeRule.onRoot().printToLog("SelectorScreenTest_Move") } catch (_: Exception) {}
            throw e
        }
        composeRule.onNodeWithText("Moved to Selection", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun cullingAction_DeleteImage_removesImageAfterConfirmation() {
        fakeRepo.imagesFlow.value = mockImages
        runBlocking {
            settingsRepository.setLastFolderUri("gdrive://test_folder")
        }

        composeRule.waitUntil(timeoutMillis = 15000) {
            composeRule.onAllNodesWithText("image1.jpg", ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        dismissGestureTutorialIfShown()

        val isCompact = composeRule.onAllNodesWithTag("copy_button_compact")
            .fetchSemanticsNodes().isNotEmpty()

        // Print tree before clicking delete to debug compact layout clicks
        try { composeRule.onRoot().printToLog("SelectorScreenTest_BeforeDelete") } catch (_: Exception) {}

        // Click Delete Button
        if (isCompact) {
            composeRule.onNodeWithTag("delete_button_compact").performClick()
        } else {
            composeRule.onNodeWithTag("delete_button_expanded").performClick()
        }

        // Verify Delete Confirmation Dialog is shown
        try {
            composeRule.waitUntil(timeoutMillis = 15000) {
                composeRule.onAllNodesWithText("Delete Image", ignoreCase = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: Exception) {
            try { composeRule.onRoot().printToLog("SelectorScreenTest_DeleteDialogTimeout") } catch (_: Exception) {}
            throw e
        }
        composeRule.onNodeWithText("Delete Image", ignoreCase = true).assertIsDisplayed()
        composeRule.onNodeWithText("Cancel", ignoreCase = true).assertIsDisplayed()

        // Click Confirm Delete inside the Delete Image dialog
        composeRule.onNodeWithTag("dialog_confirm_delete").performClick()

        // Verify image1 is removed and image2 becomes active
        composeRule.waitUntil(timeoutMillis = 15000) {
            composeRule.onAllNodesWithText("image2.jpg", ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("image2.jpg", ignoreCase = true).assertIsDisplayed()
        composeRule.onNodeWithText("image1.jpg", ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun scanImages_computesMetricsAndDisplaysScores() {
        fakeRepo.imagesFlow.value = mockImages

        // Pre-populate the cache for the mock image to avoid actual decoding failure
        runBlocking {
            scoreDao.insertOrUpdate(
                ScoreEntity(
                    filePath = "gdrive://test_folder/image1.jpg",
                    fileSize = 1024L,
                    lastModified = 1000L,
                    sharpnessScore = 78.5,
                    noiseLevel = 1.2,
                    highlightClipping = 2.4,
                    shadowClipping = 0.5
                )
            )
            settingsRepository.setLastFolderUri("gdrive://test_folder")
        }

        composeRule.waitUntil(timeoutMillis = 15000) {
            composeRule.onAllNodesWithText("image1.jpg", ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        dismissGestureTutorialIfShown()

        // Open options menu and tap Scan Images
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Scan Images").performClick()

        // Verify Scan Configuration sheet is shown and start scan
        composeRule.onNodeWithText("Scan Configuration").assertIsDisplayed()
        composeRule.onNodeWithText("Start Scan").performClick()

        // Wait until metrics update and display in the UI
        composeRule.waitUntil(timeoutMillis = 15000) {
            // Compact chips have labels like "Sharpness" or display compact values.
            // Let's check for the presence of the score values like "78.5" or "1.2".
            composeRule.onAllNodesWithText("78.5", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify metrics are visible
        composeRule.onNodeWithText("78.5", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("1.2", substring = true).assertIsDisplayed()
    }

    private fun dismissGestureTutorialIfShown() {
        if (composeRule.onAllNodesWithText("Gestures").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("Gestures").performClick()
            composeRule.waitUntil(timeoutMillis = 15000) {
                composeRule.onAllNodesWithText("Gestures").fetchSemanticsNodes().isEmpty()
            }
        }
    }
}
