package com.photoselectortoolbox.viewmodel

import com.photoselectortoolbox.data.model.ImageItem
import com.photoselectortoolbox.data.model.ScanResult
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for SelectorUiState data class — verifies defaults and
 * state transitions without needing a full ViewModel.
 */
class SelectorUiStateTest {

    @Test
    fun `default state has empty images and no loading`() {
        val state = SelectorUiState()
        assertTrue(state.images.isEmpty())
        assertEquals(0, state.currentIndex)
        assertFalse(state.isLoading)
        assertFalse(state.isScanRunning)
        assertEquals(0f, state.scanProgress, 1e-6f)
        assertNull(state.folderUri)
        assertNull(state.error)
        assertFalse(state.showDeleteConfirmation)
        assertFalse(state.groupingEnabled)
        assertTrue(state.groups.isEmpty())
    }

    @Test
    fun `expanded selector defaults to focused stacked layout`() {
        val state = SelectorUiState()
        // The space-efficient stacked view (current on top, prev/next below)
        // is the default; three-column is the opt-in alternative.
        assertTrue(state.selectorLayoutFocused)
    }

    @Test
    fun `nav hint is unseen by default so first-run arrows show once`() {
        val state = SelectorUiState()
        assertFalse(state.hasSeenNavHint)
        // After it has been shown, arrows are suppressed.
        val seen = state.copy(hasSeenNavHint = true)
        assertTrue(seen.hasSeenNavHint)
    }

    @Test
    fun `copy with loading flag`() {
        val state = SelectorUiState().copy(isLoading = true)
        assertTrue(state.isLoading)
        assertFalse(state.isScanRunning)
    }

    @Test
    fun `copy with scan running updates progress fields`() {
        val state = SelectorUiState().copy(
            isScanRunning = true,
            scanProgress = 0.5f,
            scanStatusText = "Analyzing IMG_001.jpg (5/10)"
        )
        assertTrue(state.isScanRunning)
        assertEquals(0.5f, state.scanProgress, 1e-6f)
        assertEquals("Analyzing IMG_001.jpg (5/10)", state.scanStatusText)
    }

    @Test
    fun `images can be set and indexed`() {
        val images = listOf(
            ImageItem("uri1", "a.jpg", 100, 1000, "image/jpeg"),
            ImageItem("uri2", "b.jpg", 200, 2000, "image/jpeg")
        )
        val state = SelectorUiState(images = images, currentIndex = 1)
        assertEquals(2, state.images.size)
        assertEquals("b.jpg", state.images[state.currentIndex].fileName)
    }

    @Test
    fun `error can be set and cleared`() {
        val state = SelectorUiState(error = "Something broke")
        assertEquals("Something broke", state.error)
        val cleared = state.copy(error = null)
        assertNull(cleared.error)
    }

    @Test
    fun `groups represent index-based grouping`() {
        val groups = listOf(listOf(0, 1, 2), listOf(3, 4))
        val state = SelectorUiState(groups = groups)
        assertEquals(2, state.groups.size)
        assertEquals(3, state.groups[0].size)
        assertEquals(2, state.groups[1].size)
    }

    @Test
    fun `filmstrip and details are shown by default`() {
        val state = SelectorUiState()
        assertTrue(state.filmstripVisible)
        assertTrue(state.detailsVisible)
    }

    @Test
    fun `neighbours resolve around the current frame`() {
        val state = SelectorUiState(images = threeImages, currentIndex = 1)
        assertEquals("b.jpg", state.currentImage?.fileName)
        assertEquals("a.jpg", state.previousImage?.fileName)
        assertEquals("c.jpg", state.nextImage?.fileName)
    }

    @Test
    fun `the folder edges have no neighbour rather than wrapping around`() {
        // Wrapping would silently take the photographer back to the start of
        // the shoot mid-comparison.
        val first = SelectorUiState(images = threeImages, currentIndex = 0)
        assertNull(first.previousImage)
        assertEquals("b.jpg", first.nextImage?.fileName)

        val last = SelectorUiState(images = threeImages, currentIndex = 2)
        assertEquals("b.jpg", last.previousImage?.fileName)
        assertNull(last.nextImage)
    }

    @Test
    fun `position is one-based and zero for an empty folder`() {
        assertEquals(2, SelectorUiState(images = threeImages, currentIndex = 1).position)
        assertEquals(0, SelectorUiState().position)
    }

    @Test
    fun `hasAnyScores gates the legend on there being something to explain`() {
        assertFalse(SelectorUiState(images = threeImages).hasAnyScores)

        val scanned = threeImages.toMutableList().also { list ->
            list[1] = list[1].copy(
                scanResult = ScanResult(filePath = list[1].uri, sharpnessScore = 40.0)
            )
        }
        assertTrue(SelectorUiState(images = scanned).hasAnyScores)
    }

    @Test
    fun `a current image out of range does not crash`() {
        // Deletes shrink the list; the index is coerced afterwards, but the UI
        // may read state in between.
        val state = SelectorUiState(images = threeImages, currentIndex = 99)
        assertNull(state.currentImage)
        assertNull(state.nextImage)
    }

    private val threeImages = listOf(
        ImageItem("uri1", "a.jpg", 100, 1000, "image/jpeg"),
        ImageItem("uri2", "b.jpg", 200, 2000, "image/jpeg"),
        ImageItem("uri3", "c.jpg", 300, 3000, "image/jpeg"),
    )
}
