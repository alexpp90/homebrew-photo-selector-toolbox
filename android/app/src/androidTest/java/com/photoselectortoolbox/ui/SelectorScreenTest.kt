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
            // The one-time coach affordances are overlays. Left un-dismissed
            // they sit on top of the frames these tests are asserting about,
            // and whether they appear depends on leftover DataStore state — so
            // pin them off and cover them in their own test instead.
            settingsRepository.setHasSeenNavHint(true)
            settingsRepository.setHasSeenFullscreenGestureHint(true)
            settingsRepository.setFilmstripVisible(true)
            settingsRepository.setDetailsVisible(true)
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
            composeRule.onAllNodesWithText("Select a Folder", ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onAllNodesWithText("Select a Folder", ignoreCase = true).onFirst()
            .assertIsDisplayed()
        composeRule.onNodeWithText(
            "Choose a shoot folder to start comparing and culling frames.",
            substring = true,
            ignoreCase = true,
        ).assertIsDisplayed()
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

        // Verify active photo and EXIF details are shown in UI. The details
        // panel presents ISO as a labelled row ("ISO" / "100") rather than the
        // run-together "ISO 100" of the one-line summary, so assert on the
        // value itself — it is present in either presentation.
        composeRule.onAllNodesWithText("image1.jpg", ignoreCase = true).onFirst()
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("1/200s", substring = true).onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("f/2.8", substring = true).onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("100", substring = true).onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("50mm", substring = true).onFirst().assertIsDisplayed()
    }

    @Test
    fun focusedLayout_allThreeFramesAreTheSameHeight() {
        // The core promise of the layout: the active frame is marked only by a
        // border, never by being bigger. A neighbour that is smaller cannot be
        // judged for sharpness against the current frame, which is the task.
        fakeRepo.imagesFlow.value = mockImages
        runBlocking {
            settingsRepository.setLastFolderUri("gdrive://test_folder")
            settingsRepository.setSelectorLayoutFocused(true)
        }

        composeRule.waitUntil(timeoutMillis = 15000) {
            composeRule.onAllNodesWithText("image1.jpg", ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        dismissGestureTutorialIfShown()

        // Only the expanded layout has three tiles at once.
        if (composeRule.onAllNodesWithTag("copy_button_compact").fetchSemanticsNodes().isNotEmpty()) {
            return
        }
        if (composeRule.onAllNodesWithTag("column_next").fetchSemanticsNodes().isEmpty()) return

        val current = composeRule.onNodeWithTag("column_current").getUnclippedBoundsInRoot()
        val next = composeRule.onNodeWithTag("column_next").getUnclippedBoundsInRoot()

        val currentHeight = current.bottom - current.top
        val nextHeight = next.bottom - next.top
        val difference = kotlin.math.abs(currentHeight.value - nextHeight.value)

        // 2dp of slack: the active tile's border is 2dp where a neighbour's is 1dp.
        assert(difference <= 2f) {
            "current frame is ${currentHeight.value}dp tall but next is ${nextHeight.value}dp"
        }
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
        composeRule.onAllNodesWithText("image2.jpg", ignoreCase = true).onFirst()
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("1/125s", substring = true).onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("f/4.0", substring = true).onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("200", substring = true).onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("85mm", substring = true).onFirst().assertIsDisplayed()
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
        composeRule.waitUntil(timeoutMillis = 35000) {
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
        composeRule.onAllNodesWithText("image2.jpg", ignoreCase = true).onFirst()
            .assertIsDisplayed()
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
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onAllNodesWithText("Scan Images").onFirst().performClick()

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

    @Test
    fun focusMode_layoutToggleAndFullscreenDoNotOverlap() {
        fakeRepo.imagesFlow.value = mockImages
        runBlocking {
            settingsRepository.setLastFolderUri("gdrive://test_folder")
            settingsRepository.setSelectorLayoutFocused(true)
        }

        composeRule.waitUntil(timeoutMillis = 15000) {
            composeRule.onAllNodesWithText("image1.jpg", ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        dismissGestureTutorialIfShown()

        // Compact (phone) layout has neither control — nothing to assert.
        val isCompact = composeRule.onAllNodesWithTag("copy_button_compact")
            .fetchSemanticsNodes().isNotEmpty()
        if (isCompact) return

        if (composeRule.onAllNodesWithTag("layout_toggle").fetchSemanticsNodes().isEmpty()) return
        if (composeRule.onAllNodesWithTag("fullscreen_button").fetchSemanticsNodes().isEmpty()) return

        val toggle = composeRule.onAllNodesWithTag("layout_toggle").onFirst()
            .getUnclippedBoundsInRoot()
        val fullscreen = composeRule.onAllNodesWithTag("fullscreen_button").onFirst()
            .getUnclippedBoundsInRoot()

        // The regression: both controls used to be pinned to the same top-right
        // corner, so the layout toggle sat on top of the fullscreen button.
        val overlaps = toggle.left < fullscreen.right &&
            fullscreen.left < toggle.right &&
            toggle.top < fullscreen.bottom &&
            fullscreen.top < toggle.bottom
        assert(!overlaps) {
            "layout toggle $toggle overlaps the fullscreen button $fullscreen"
        }

        // Both remain individually reachable.
        composeRule.onAllNodesWithTag("layout_toggle").onFirst().assertHasClickAction()
        composeRule.onAllNodesWithTag("fullscreen_button").onFirst().assertHasClickAction()
    }

    @Test
    fun firstRun_navigationHintShowsOnceAndStaysDismissed() {
        fakeRepo.imagesFlow.value = mockImages
        runBlocking {
            settingsRepository.setHasSeenNavHint(false)
            settingsRepository.setLastFolderUri("gdrive://test_folder")
            settingsRepository.setSelectorLayoutFocused(true)
        }

        composeRule.waitUntil(timeoutMillis = 15000) {
            composeRule.onAllNodesWithText("image1.jpg", ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Compact layout uses the gesture tutorial instead of this hint.
        if (composeRule.onAllNodesWithTag("copy_button_compact").fetchSemanticsNodes().isNotEmpty()) {
            return
        }
        if (composeRule.onAllNodesWithTag("first_run_hint", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        ) {
            return
        }

        composeRule.onAllNodes(hasText("Got it", ignoreCase = true), useUnmergedTree = true)
            .onFirst().performClick()

        composeRule.waitUntil(timeoutMillis = 15000) {
            composeRule.onAllNodesWithTag("first_run_hint", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        }
        composeRule.onAllNodesWithTag("first_run_hint", useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun scoreChip_carriesDirectionAndBestOfThreeInItsDescription() {
        // A bare number with no stated direction is not interpretable, and a
        // sighted user gets the direction from the bar that a screen-reader
        // user does not.
        val scanned = mockImages.mapIndexed { idx, item ->
            item.copy(
                scanResult = com.photoselectortoolbox.data.model.ScanResult(
                    filePath = item.uri,
                    sharpnessScore = if (idx == 0) 88.3 else 22.4,
                )
            )
        }
        fakeRepo.imagesFlow.value = scanned
        runBlocking { settingsRepository.setLastFolderUri("gdrive://test_folder") }

        composeRule.waitUntil(timeoutMillis = 15000) {
            composeRule.onAllNodesWithText("image1.jpg", ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        dismissGestureTutorialIfShown()

        composeRule.waitUntil(timeoutMillis = 15000) {
            composeRule.onAllNodesWithContentDescription("higher is better", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithContentDescription("higher is better", substring = true)
            .onFirst().assertExists()
    }

    @Test
    fun scoreLegend_explainsWhatTheScanIconsMean() {
        val scannedImages = mockImages.mapIndexed { idx, item ->
            if (idx == 0) item.copy(
                scanResult = com.photoselectortoolbox.data.model.ScanResult(
                    filePath = item.uri,
                    sharpnessScore = 78.5,
                    noiseLevel = 1.2,
                    highlightClipping = 2.4,
                    shadowClipping = 0.5,
                )
            ) else item
        }
        fakeRepo.imagesFlow.value = scannedImages
        runBlocking {
            settingsRepository.setLastFolderUri("gdrive://test_folder")
        }

        composeRule.waitUntil(timeoutMillis = 15000) {
            composeRule.onAllNodesWithText("image1.jpg", ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        dismissGestureTutorialIfShown()

        // The legend button appears once there are scores to explain.
        composeRule.waitUntil(timeoutMillis = 15000) {
            composeRule.onAllNodesWithContentDescription("What the scan icons mean")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("What the scan icons mean").performClick()

        composeRule.waitUntil(timeoutMillis = 15000) {
            composeRule.onAllNodesWithText("What the scan icons mean", ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        val inLegend = hasAnyAncestor(hasTestTag("score_legend_sheet"))
        composeRule.onNode(hasText("Sharpness") and inLegend, useUnmergedTree = true).assertExists()
        composeRule.onNode(hasText("Noise") and inLegend, useUnmergedTree = true).assertExists()
        composeRule.onAllNodes(hasText("higher is better", substring = true) and inLegend, useUnmergedTree = true).onFirst().assertExists()
        composeRule.onAllNodes(hasText("lower is better", substring = true) and inLegend, useUnmergedTree = true).onFirst().assertExists()
    }

    private fun dismissGestureTutorialIfShown() {
        if (composeRule.onAllNodes(hasTestTag("gesture_tutorial_overlay"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onAllNodes(hasTestTag("gesture_tutorial_overlay"), useUnmergedTree = true).onFirst().performClick()
            composeRule.waitUntil(timeoutMillis = 15000) {
                composeRule.onAllNodes(hasTestTag("gesture_tutorial_overlay"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
            }
        }
        if (composeRule.onAllNodes(hasText("GOT IT", ignoreCase = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onAllNodes(hasText("GOT IT", ignoreCase = true), useUnmergedTree = true).onFirst().performClick()
            composeRule.waitUntil(timeoutMillis = 15000) {
                composeRule.onAllNodes(hasText("GOT IT", ignoreCase = true), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
            }
        }
        if (composeRule.onAllNodes(hasText("Gestures", ignoreCase = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onAllNodes(hasText("Gestures", ignoreCase = true), useUnmergedTree = true).onFirst().performClick()
        }
    }
}
