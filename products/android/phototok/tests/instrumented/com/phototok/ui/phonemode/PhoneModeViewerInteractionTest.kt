package com.phototok.ui.phonemode

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.phototok.data.model.ImageItem
import com.phototok.domain.CollectionAction
import com.phototok.domain.FirstRunHint
import com.phototok.domain.SwipeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoneModeViewerInteractionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun testImage(name: String) = ImageItem(
        uri = "content://media/external/images/media/$name",
        fileName = name,
        fileSize = 1024L,
        lastModified = 1000L,
        mimeType = "image/jpeg",
        imageWidth = 1920,
        imageHeight = 1080,
    )

    @Test
    fun singleTap_togglesHudAndEmitsTapHudHint() {
        var hinted: FirstRunHint? = null
        val images = listOf(testImage("photo1.jpg"), testImage("photo2.jpg"))

        composeTestRule.setContent {
            PhoneModeViewer(
                images = images,
                currentIndex = 0,
                portraitSectionStart = -1,
                onNavigate = {},
                onAddToCollection = {},
                onRequestDelete = {},
                onFirstRunHint = { hinted = it },
            )
        }

        composeTestRule.waitForIdle()

        // Page counter is visible initially
        composeTestRule.onNodeWithText("1 / 2").assertExists()

        // Single tap on viewer
        composeTestRule.onRoot().performClick()
        composeTestRule.waitForIdle()

        assertEquals(FirstRunHint.TAP_HUD, hinted)
    }

    @Test
    fun readOnlyViewer_suppressesFirstRunHintOnTap() {
        var hintTriggered = false
        val images = listOf(testImage("photo1.jpg"))

        composeTestRule.setContent {
            PhoneModeViewer(
                images = images,
                currentIndex = 0,
                portraitSectionStart = -1,
                onNavigate = {},
                onAddToCollection = {},
                onRequestDelete = {},
                readOnly = true,
                onFirstRunHint = { hintTriggered = true },
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onRoot().performClick()
        composeTestRule.waitForIdle()

        assertFalse("In read-only mode, TAP_HUD hint must not trigger", hintTriggered)
    }
}
