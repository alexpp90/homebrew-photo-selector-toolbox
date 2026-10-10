package com.phototok.ui.phonemode

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.phototok.domain.FirstRunHint
import com.phototok.ui.components.ViewerBottomBar
import com.phototok.viewmodel.FirstRunHintUi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoneModeScreenDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun directDeleteDialog_showsWarningAndSettingsHint() {
        var confirmed = false
        var dismissed = false

        composeTestRule.setContent {
            val colors = MaterialTheme.colorScheme
            AlertDialog(
                onDismissRequest = { dismissed = true },
                title = { Text("Delete Permanently", color = colors.error) },
                text = {
                    Text("This picture will be directly deleted (permanently) because trash is not supported for this location.\n\nHint: You can disable this confirmation in Settings.")
                },
                confirmButton = {
                    TextButton(onClick = { confirmed = true }) {
                        Text("Delete", color = colors.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dismissed = true }) {
                        Text("Cancel")
                    }
                },
            )
        }

        composeTestRule.onNodeWithText("Delete Permanently").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "This picture will be directly deleted (permanently) because trash is not supported for this location.\n\nHint: You can disable this confirmation in Settings."
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeTestRule.onNodeWithText("Delete").assertIsDisplayed()

        composeTestRule.onNodeWithText("Delete").performClick()
        assertTrue("Confirm button must trigger deletion", confirmed)
        assertFalse("Cancel must not have been triggered", dismissed)
    }

    @Test
    fun directDeleteDialog_cancelDismissesWithoutDeleting() {
        var confirmed = false
        var dismissed = false

        composeTestRule.setContent {
            AlertDialog(
                onDismissRequest = { dismissed = true },
                title = { Text("Delete Permanently") },
                text = { Text("Warning text") },
                confirmButton = {
                    TextButton(onClick = { confirmed = true }) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dismissed = true }) {
                        Text("Cancel")
                    }
                },
            )
        }

        composeTestRule.onNodeWithText("Cancel").performClick()
        assertTrue("Dismiss button must trigger dismiss callback", dismissed)
        assertFalse("Confirm must not be triggered on cancel", confirmed)
    }

    @Test
    fun firstRunHintCard_displaysTitleAndMessageAndDismissesOnClick() {
        var dismissed = false
        val hintUi = FirstRunHintUi(
            hint = FirstRunHint.SWIPE_RIGHT,
            title = "Copied to Collection",
            message = "Every swipe right copies photos to the collection folder. You can change this in Settings.",
            id = 1,
        )

        composeTestRule.setContent {
            FirstRunHintCard(
                hint = hintUi,
                onDismiss = { dismissed = true },
            )
        }

        composeTestRule.onNodeWithTag("first_run_hint").assertIsDisplayed()
        composeTestRule.onNodeWithText("Copied to Collection").assertIsDisplayed()
        composeTestRule.onNodeWithText("Every swipe right copies photos to the collection folder. You can change this in Settings.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("first_run_hint_dismiss").assertIsDisplayed()

        composeTestRule.onNodeWithTag("first_run_hint_dismiss").performClick()
        assertTrue("Dismiss button must invoke onDismiss callback", dismissed)
    }

    @Test
    fun firstRunHintCard_withAction_displaysActionAndInvokesCallbacks() {
        var actionInvoked = false
        var dismissed = false
        val hintUi = FirstRunHintUi(
            hint = FirstRunHint.FILTER_MISMATCH,
            title = "Filter Active",
            message = "Showing RAW photos only. Most photos in this folder are JPEG and are hidden.",
            actionLabel = "SHOW ALL",
            onAction = { actionInvoked = true },
            id = 2,
        )

        composeTestRule.setContent {
            FirstRunHintCard(
                hint = hintUi,
                onDismiss = { dismissed = true },
            )
        }

        composeTestRule.onNodeWithTag("first_run_hint").assertIsDisplayed()
        composeTestRule.onNodeWithText("Filter Active").assertIsDisplayed()
        composeTestRule.onNodeWithTag("first_run_hint_action").assertIsDisplayed()
        composeTestRule.onNodeWithText("SHOW ALL").assertIsDisplayed()
        composeTestRule.onNodeWithTag("first_run_hint_dismiss").assertIsDisplayed()

        composeTestRule.onNodeWithTag("first_run_hint_action").performClick()
        assertTrue("Action button must invoke onAction callback", actionInvoked)
        assertTrue("Action button click must also dismiss the hint", dismissed)
    }

    @Test
    fun rawJpegSuggestionDialog_displaysAllChoicesClearly() {
        composeTestRule.setContent {
            RawJpegSuggestionDialog(
                visible = true,
                onFilterRaw = {},
                onFilterJpg = {},
                onEnableMoveRelatedFiles = {},
                onDismiss = {},
            )
        }

        composeTestRule.onAllNodesWithTag("raw_jpeg_suggestion_dialog").onFirst().assertIsDisplayed()
        composeTestRule.onAllNodesWithText("RAW + JPEG Pairs Detected").onFirst().assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("raw_jpeg_dismiss").onFirst().assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("apply_both_button").onFirst().assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("filter_raw_button").onFirst().assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("filter_jpg_button").onFirst().assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("dont_change_settings_button").onFirst().assertIsDisplayed()
    }

    @Test
    fun rawJpegSuggestionDialog_dontChangeSettingsOption_invokesDismissCallback() {
        var dismissed = false
        var filterRaw = false
        var filterJpg = false
        var moveRelated = false

        composeTestRule.setContent {
            RawJpegSuggestionDialog(
                visible = true,
                onFilterRaw = { filterRaw = true },
                onFilterJpg = { filterJpg = true },
                onEnableMoveRelatedFiles = { moveRelated = true },
                onDismiss = { dismissed = true },
            )
        }

        composeTestRule.onAllNodesWithTag("dont_change_settings_button").onFirst().performClick()
        assertTrue("Don't change settings must invoke dismiss callback", dismissed)
        assertFalse("Filter RAW must not be invoked on dismiss", filterRaw)
        assertFalse("Filter JPG must not be invoked on dismiss", filterJpg)
        assertFalse("Move related files must not be invoked on dismiss", moveRelated)
    }

    @Test
    fun rawJpegSuggestionDialog_linkActionsOption_invokesCallback() {
        var moveRelated = false

        composeTestRule.setContent {
            RawJpegSuggestionDialog(
                visible = true,
                onFilterRaw = {},
                onFilterJpg = {},
                onEnableMoveRelatedFiles = { moveRelated = true },
                onDismiss = {},
            )
        }

        composeTestRule.onAllNodesWithTag("apply_both_button").onFirst().performClick()
        assertTrue("Link actions option must invoke onEnableMoveRelatedFiles callback", moveRelated)
    }

    @Test
    fun rawJpegSuggestionDialog_filterButtons_invokeCallbacks() {
        var filterRaw = false
        var filterJpg = false

        composeTestRule.setContent {
            RawJpegSuggestionDialog(
                visible = true,
                onFilterRaw = { filterRaw = true },
                onFilterJpg = { filterJpg = true },
                onEnableMoveRelatedFiles = {},
                onDismiss = {},
            )
        }

        composeTestRule.onAllNodesWithTag("filter_raw_button").onFirst().performClick()
        assertTrue("Filter RAW button must invoke onFilterRaw callback", filterRaw)

        composeTestRule.onAllNodesWithTag("filter_jpg_button").onFirst().performClick()
        assertTrue("Filter JPG button must invoke onFilterJpg callback", filterJpg)
    }

    @Test
    fun rawJpegSuggestionDialog_dismissButton_invokesDismissCallback() {
        var dismissed = false

        composeTestRule.setContent {
            RawJpegSuggestionDialog(
                visible = true,
                onFilterRaw = {},
                onFilterJpg = {},
                onEnableMoveRelatedFiles = {},
                onDismiss = { dismissed = true },
            )
        }

        composeTestRule.onAllNodesWithTag("raw_jpeg_dismiss").onFirst().performClick()
        assertTrue("Dismiss 'X' button must invoke onDismiss callback", dismissed)
    }

    @Test
    fun rawJpegSuggestionDialog_inLandscape_displaysOptionsAndIsClickable() {
        var clickedOption = false

        composeTestRule.setContent {
            Box(modifier = Modifier.size(width = 640.dp, height = 360.dp)) {
                RawJpegSuggestionDialog(
                    visible = true,
                    onFilterRaw = {},
                    onFilterJpg = {},
                    onEnableMoveRelatedFiles = { clickedOption = true },
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onAllNodesWithTag("raw_jpeg_suggestion_dialog").onFirst().assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("apply_both_button").onFirst().assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("apply_both_button").onFirst().performClick()
        assertTrue("Options must be clickable in landscape layout", clickedOption)
    }

    @Test
    fun rawJpegSuggestionDialog_withViewerBottomBar_notOccludedOrCovered() {
        var dismissed = false

        composeTestRule.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                // Viewer bottom bar containing Sources (folder) and Selection (favorites/star)
                ViewerBottomBar(
                    canRevert = false,
                    onRevert = {},
                    onJumpToSelection = {},
                    onGoToLanding = {},
                    modifier = Modifier.align(Alignment.BottomCenter),
                )

                // RAW + JPEG suggestion dialog
                RawJpegSuggestionDialog(
                    visible = true,
                    onFilterRaw = {},
                    onFilterJpg = {},
                    onEnableMoveRelatedFiles = {},
                    onDismiss = { dismissed = true },
                )
            }
        }

        // Verify the dialog is displayed
        composeTestRule.onAllNodesWithTag("raw_jpeg_suggestion_dialog").onFirst().assertIsDisplayed()

        // Verify bottom options of the dialog are displayed and interactable (not occluded by bottom bar)
        composeTestRule.onAllNodesWithTag("dont_change_settings_button").onFirst().assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("dont_change_settings_button").onFirst().performClick()
        assertTrue("Don't change settings button must be clickable when bottom bar is present", dismissed)

        // Verify bottom bar also exists in background
        composeTestRule.onAllNodesWithTag("viewer_bottom_bar").onFirst().assertIsDisplayed()
    }

    @Test
    fun reloadJumpDialog_displaysFoundCountAndTriggersCallbacks() {
        var jumped = false
        var stayed = false

        composeTestRule.setContent {
            AlertDialog(
                onDismissRequest = { stayed = true },
                title = { Text("New Photos Found") },
                text = { Text("5 new photos were found while reviewing. Would you like to jump to the new photos or stay at your current photo?") },
                confirmButton = {
                    Button(
                        onClick = { jumped = true },
                        modifier = Modifier.testTag("reload_jump_confirm_button"),
                    ) {
                        Text("Jump to Latest")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { stayed = true },
                        modifier = Modifier.testTag("reload_jump_stay_button"),
                    ) {
                        Text("Stay at Current")
                    }
                },
                modifier = Modifier.testTag("reload_jump_dialog"),
            )
        }

        composeTestRule.onAllNodesWithTag("reload_jump_dialog").onFirst().assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("reload_jump_confirm_button").onFirst().assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("reload_jump_stay_button").onFirst().assertIsDisplayed()

        composeTestRule.onAllNodesWithTag("reload_jump_confirm_button").onFirst().performClick()
        assertTrue("Confirm button must trigger jump", jumped)
        assertFalse("Stay button must not be triggered", stayed)
    }
}
