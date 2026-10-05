package com.phototok.ui.phonemode

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.phototok.domain.FirstRunHint
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
}
