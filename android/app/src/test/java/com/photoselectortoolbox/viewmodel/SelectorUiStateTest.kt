package com.photoselectortoolbox.viewmodel

import com.photoselectortoolbox.data.model.ImageItem
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
}
