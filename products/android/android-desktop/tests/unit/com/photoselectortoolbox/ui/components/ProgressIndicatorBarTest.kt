package com.photoselectortoolbox.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class ProgressIndicatorBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun progressIndicatorBar_whenIndeterminate_displaysStatusTextAndIndeterminateBar() {
        val status = "Scanning images... 45/100"

        composeTestRule.setContent {
            ProgressIndicatorBar(
                progress = 0.45f,
                statusText = status,
                isIndeterminate = true,
                modifier = Modifier.testTag("progress_bar_root"),
            )
        }

        // Verify status text is displayed
        composeTestRule.onNodeWithText(status).assertIsDisplayed()

        // Verify root node with custom modifier is displayed
        composeTestRule.onNodeWithTag("progress_bar_root").assertIsDisplayed()

        // Verify progress bar is indeterminate (ProgressBarRangeInfo.Indeterminate)
        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertIsDisplayed()
    }

    @Test
    fun progressIndicatorBar_whenDeterminate_displaysStatusTextAndProgress() {
        val status = "Processing: 50%"

        composeTestRule.setContent {
            ProgressIndicatorBar(
                progress = 0.5f,
                statusText = status,
                isIndeterminate = false,
                modifier = Modifier.testTag("progress_bar_root"),
            )
        }

        // Verify status text
        composeTestRule.onNodeWithText(status).assertIsDisplayed()

        // Verify determinate progress range info
        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.5f, 0f..1f)))
            .assertIsDisplayed()
    }

    @Test
    fun progressIndicatorBar_whenProgressLessThanZero_coercesToZero() {
        val status = "Starting..."

        composeTestRule.setContent {
            ProgressIndicatorBar(
                progress = -0.2f,
                statusText = status,
                isIndeterminate = false,
            )
        }

        composeTestRule.onNodeWithText(status).assertIsDisplayed()

        // Negative progress should be coerced to 0f
        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0f, 0f..1f)))
            .assertIsDisplayed()
    }

    @Test
    fun progressIndicatorBar_whenProgressGreaterThanOne_coercesToOne() {
        val status = "Complete!"

        composeTestRule.setContent {
            ProgressIndicatorBar(
                progress = 1.5f,
                statusText = status,
                isIndeterminate = false,
            )
        }

        composeTestRule.onNodeWithText(status).assertIsDisplayed()

        // Progress > 1f should be coerced to 1f
        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(1f, 0f..1f)))
            .assertIsDisplayed()
    }
}
