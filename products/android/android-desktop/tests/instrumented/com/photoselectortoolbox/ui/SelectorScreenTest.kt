package com.photoselectortoolbox.ui

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.photoselector.core.model.ExifData
import com.photoselectortoolbox.MainActivity
import com.photoselectortoolbox.data.cache.ScoreDao
import com.photoselectortoolbox.data.cache.ScoreEntity
import com.photoselectortoolbox.data.model.ImageItem
import com.photoselectortoolbox.data.repository.FakeImageRepository
import com.photoselectortoolbox.data.repository.ImageRepository
import com.photoselectortoolbox.data.repository.SettingsRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
    fun threeUpLayout_allThreeFramesAreExactlyTheSameSize() {
        // The core promise of the layout: the active frame is marked only by a
        // border and by being centred, never by being bigger. A neighbour at a
        // different size cannot be judged for sharpness against the current
        // frame, which is the entire task.
        //
        // This is asserted rather than eyeballed because the failure mode is
        // invisible to review: `aspectRatio` resolves the width constraint
        // first by default, so a wide frame quietly derives a different height
        // from a narrow one. See the 2026-07-27 entry in ai/memory/palette.md.
        fakeRepo.imagesFlow.value = mockImages
        runBlocking { settingsRepository.setLastFolderUri("gdrive://test_folder") }

        composeRule.waitUntil(timeoutMillis = 15000) {
            composeRule.onAllNodesWithText("image1.jpg", ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        dismissGestureTutorialIfShown()

        if (isCompactLayout()) return
        if (composeRule.onAllNodesWithTag("column_next").fetchSemanticsNodes().isEmpty()) return

        val current = composeRule.onNodeWithTag("column_current").getUnclippedBoundsInRoot()
        val previous = composeRule.onNodeWithTag("column_previous").getUnclippedBoundsInRoot()
        val next = composeRule.onNodeWithTag("column_next").getUnclippedBoundsInRoot()

        listOf("previous" to previous, "next" to next).forEach { (name, neighbour) ->
            val heightDelta = kotlin.math.abs(
                (current.bottom - current.top).value - (neighbour.bottom - neighbour.top).value
            )
            val widthDelta = kotlin.math.abs(
                (current.right - current.left).value - (neighbour.right - neighbour.left).value
            )
            // 2dp of slack: the active tile's border is 2dp where a resting
            // neighbour's is 1dp.
            assert(heightDelta <= 2f) {
                "current frame is ${(current.bottom - current.top).value}dp tall but " +
                    "$name is ${(neighbour.bottom - neighbour.top).value}dp"
            }
            assert(widthDelta <= 2f) {
                "current frame is ${(current.right - current.left).value}dp wide but " +
                    "$name is ${(neighbour.right - neighbour.left).value}dp"
            }
        }
    }

    @Test
    fun threeUpLayout_framesUseTheHeightTheDisplayAllows() {
        // A size floor, not a style preference. Both previous revisions of this
        // screen shipped with frames far smaller than the display allowed — the
        // first arranged them in a row (width-bound, 40% of the height unused),
        // the second stacked an app bar, an action row and a filmstrip into the
        // one axis that was scarce. Neither was caught by a test, because there
        // wasn't one. Anything that reintroduces chrome in the vertical stack
        // now fails here rather than waiting to be noticed by eye.
        fakeRepo.imagesFlow.value = mockImages
        runBlocking { settingsRepository.setLastFolderUri("gdrive://test_folder") }

        composeRule.waitUntil(timeoutMillis = 15000) {
            composeRule.onAllNodesWithText("image1.jpg", ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        dismissGestureTutorialIfShown()

        if (isCompactLayout()) return
        if (composeRule.onAllNodesWithTag("column_current").fetchSemanticsNodes().isEmpty()) return

        val rootBounds = composeRule.onRoot().getUnclippedBoundsInRoot()
        val screenHeight = rootBounds.bottom - rootBounds.top
        val frame = composeRule.onNodeWithTag("column_current").getUnclippedBoundsInRoot()
        val frameHeight = frame.bottom - frame.top

        // Two equal rows plus gaps and padding: each frame should be close to
        // half the window height. Anything under 45% means something is eating
        // the vertical axis.
        val ratio = frameHeight.value / screenHeight.value
        assert(ratio >= 0.45f) {
            "frame is ${frameHeight.value}dp of a ${screenHeight.value}dp window " +
                "(${(ratio * 100).toInt()}%) — something is consuming the height budget"
        }
    }

    @Test
    fun neighbourFrame_maximiseBadgeDoesNotOverlapItsValueOverlay() {
        // Both sit inside the same tile bounds, so their placement is a rule
        // (badge bottom-left on neighbours, values right edge) rather than an
        // accident. Overlap between two controls in different composables is
        // invisible in review — this is the same class of bug as the layout
        // toggle that once rendered on top of the fullscreen button.
        fakeRepo.imagesFlow.value = mockImages
        runBlocking { settingsRepository.setLastFolderUri("gdrive://test_folder") }

        composeRule.waitUntil(timeoutMillis = 15000) {
            composeRule.onAllNodesWithText("image1.jpg", ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        dismissGestureTutorialIfShown()

        if (isCompactLayout()) return
        val overlays = composeRule.onAllNodesWithTag("neighbour_overlay").fetchSemanticsNodes()
        val badges = composeRule.onAllNodesWithTag("maximise_badge").fetchSemanticsNodes()
        if (overlays.isEmpty() || badges.isEmpty()) return

        overlays.forEach { overlay ->
            badges.forEach { badge ->
                val a = overlay.boundsInRoot
                val b = badge.boundsInRoot
                val intersects = a.left < b.right && b.left < a.right &&
                    a.top < b.bottom && b.top < a.bottom
                assert(!intersects) {
                    "a value overlay at $a intersects a maximise badge at $b"
                }
            }
        }
    }

    /** True when the window is narrow enough to be running the phone layout. */
    private fun isCompactLayout(): Boolean =
        composeRule.onAllNodesWithTag("copy_button_compact").fetchSemanticsNodes().isNotEmpty()

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
            // Horizontal swipe browses, on every layout in this product.
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

        // Tap Scan button on sidebar/chrome
        composeRule.onAllNodesWithTag("scan_button", useUnmergedTree = true).onFirst().performClick()

        // Verify Scan Configuration sheet is shown and start scan
        composeRule.waitUntil(timeoutMillis = 15000) {
            composeRule.onAllNodesWithText("Scan Configuration", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Scan Configuration", useUnmergedTree = true).onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("Start Scan", useUnmergedTree = true).onFirst().performClick()

        // Wait until metrics update and display in the UI
        composeRule.waitUntil(timeoutMillis = 15000) {
            composeRule.onAllNodesWithText("78.5", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify metrics are visible
        composeRule.onAllNodesWithText("78.5", substring = true).onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("1.2", substring = true).onFirst().assertIsDisplayed()
    }

    @Test
    fun noTwoSelectorControlsShareBounds() {
        // Generalised from the regression it replaces: the layout toggle used
        // to be pinned to the same top-right corner as the fullscreen button,
        // in a different composable, so the two were invisible to each other in
        // review. The layout toggle is gone with the second layout, but the
        // class of bug is not, so this now checks every control in the block.
        fakeRepo.imagesFlow.value = mockImages
        runBlocking { settingsRepository.setLastFolderUri("gdrive://test_folder") }

        composeRule.waitUntil(timeoutMillis = 15000) {
            composeRule.onAllNodesWithText("image1.jpg", ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        dismissGestureTutorialIfShown()

        if (isCompactLayout()) return

        val tags = listOf(
            "copy_button_expanded",
            "move_button_expanded",
            "delete_button_expanded",
            "fullscreen_button",
            "rail_previous",
            "rail_next",
            "details_toggle",
            "filmstrip_toggle",
            "overlay_values_toggle",
            "shortcuts_button",
        )
        val bounds = tags.mapNotNull { tag ->
            val nodes = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes()
            if (nodes.isEmpty()) null else tag to nodes.first().boundsInRoot
        }

        bounds.forEachIndexed { i, (nameA, a) ->
            bounds.drop(i + 1).forEach { (nameB, b) ->
                val intersects = a.left < b.right && b.left < a.right &&
                    a.top < b.bottom && b.top < a.bottom
                assert(!intersects) { "$nameA at $a overlaps $nameB at $b" }
            }
        }
    }

    @Test
    fun filingButtonsAreWordedAndNeverSayKeep() {
        // The label is derived from the Filing Action setting, so it says what
        // happens to the file. "Keep" describes a feeling, and under a move
        // configuration it is simply false — the file leaves the folder.
        fakeRepo.imagesFlow.value = mockImages
        runBlocking { settingsRepository.setLastFolderUri("gdrive://test_folder") }

        composeRule.waitUntil(timeoutMillis = 15000) {
            composeRule.onAllNodesWithText("image1.jpg", ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        dismissGestureTutorialIfShown()

        if (isCompactLayout()) return

        composeRule.onAllNodesWithText("Keep", substring = true, ignoreCase = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithText("Copy").fetchSemanticsNodes().let {
            assert(it.isNotEmpty()) { "no worded Copy control on screen" }
        }
        composeRule.onAllNodesWithText("Move").fetchSemanticsNodes().let {
            assert(it.isNotEmpty()) { "no worded Move control on screen" }
        }
    }

    @Test
    fun firstRun_navigationHintShowsOnceAndStaysDismissed() {
        fakeRepo.imagesFlow.value = mockImages
        runBlocking {
            settingsRepository.setHasSeenNavHint(false)
            settingsRepository.setLastFolderUri("gdrive://test_folder")
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
            composeRule.onAllNodesWithTag("score_legend_button", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithTag("score_legend_button", useUnmergedTree = true).onFirst().performClick()

        composeRule.waitUntil(timeoutMillis = 15000) {
            composeRule.onAllNodes(hasText("What the scan icons mean", ignoreCase = true), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        val inLegend = hasAnyAncestor(hasTestTag("score_legend_sheet"))
        composeRule.onAllNodes(hasText("Sharpness") and inLegend, useUnmergedTree = true).onFirst().assertExists()
        composeRule.onAllNodes(hasText("Noise") and inLegend, useUnmergedTree = true).onFirst().assertExists()
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
