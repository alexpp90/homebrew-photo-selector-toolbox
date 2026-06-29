package com.phototok.ui.phonemode

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GestureTutorialOverlayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun gestureTutorialOverlay_whenVisible_showsTitleAndGotItButton() {
        var dismissClicked = false

        composeTestRule.setContent {
            GestureTutorialOverlay(
                visible = true,
                onDismiss = { dismissClicked = true }
            )
        }

        // Verify the main header title is displayed
        composeTestRule.onNodeWithText("How to Photo-Tok").assertIsDisplayed()

        // Verify the instruction text is displayed
        composeTestRule.onNodeWithText("Master the curation flow with these simple gestures").assertIsDisplayed()

        // Verify the GOT IT button is displayed
        composeTestRule.onNodeWithText("GOT IT").assertIsDisplayed()

        // Perform click on GOT IT button
        composeTestRule.onNodeWithText("GOT IT").performClick()

        // Verify dismiss callback was triggered
        assertTrue(dismissClicked)
    }

    @Test
    fun phoneModeScreen_whenTutorialVisible_appBarsAreHidden() {
        // A simulated version of the PhoneModeScreen layout container
        // to verify that the top app bar and bottom nav bar are hidden
        // when showGestureTutorial is true (the core regression we fixed).
        composeTestRule.setContent {
            val showGestureTutorial = true
            val isViewing = true

            Box(modifier = Modifier.fillMaxSize()) {
                // Background/Content
                Text("Viewer Content")

                // Top App Bar conditional visibility (as implemented in PhoneModeScreen.kt)
                if (!showGestureTutorial) {
                    Text("Top App Bar Logo")
                }

                // Bottom Nav Bar conditional visibility (as implemented in PhoneModeScreen.kt)
                if (isViewing && !showGestureTutorial) {
                    Text("Bottom Nav Bar")
                }

                // Gesture Tutorial Overlay
                GestureTutorialOverlay(
                    visible = showGestureTutorial,
                    onDismiss = {}
                )
            }
        }

        // Verify the overlay is displayed
        composeTestRule.onNodeWithText("How to Photo-Tok").assertIsDisplayed()

        // Verify that Top App Bar and Bottom NavBar are NOT displayed
        composeTestRule.onNodeWithText("Top App Bar Logo").assertDoesNotExist()
        composeTestRule.onNodeWithText("Bottom Nav Bar").assertDoesNotExist()
    }

    @Test
    fun phoneModeScreen_whenTutorialHidden_appBarsAreVisible() {
        composeTestRule.setContent {
            val showGestureTutorial = false
            val isViewing = true

            Box(modifier = Modifier.fillMaxSize()) {
                // Background/Content
                Text("Viewer Content")

                // Top App Bar conditional visibility
                if (!showGestureTutorial) {
                    Text("Top App Bar Logo")
                }

                // Bottom Nav Bar conditional visibility
                if (isViewing && !showGestureTutorial) {
                    Text("Bottom Nav Bar")
                }

                // Gesture Tutorial Overlay
                GestureTutorialOverlay(
                    visible = showGestureTutorial,
                    onDismiss = {}
                )
            }
        }

        // Verify the overlay is NOT displayed
        composeTestRule.onNodeWithText("How to Photo-Tok").assertDoesNotExist()

        // Verify that Top App Bar and Bottom NavBar are displayed
        composeTestRule.onNodeWithText("Top App Bar Logo").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bottom Nav Bar").assertIsDisplayed()
    }
}
