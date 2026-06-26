package com.photoselectortoolbox.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.photoselectortoolbox.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for navigation between screens.
 * Verifies that tapping navigation items shows the correct screen content.
 *
 * These tests run on an emulator or real Android device.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun selectorScreenIsDefaultDestination() {
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithText("Select a Folder", substring = false, ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty() ||
            composeRule.onAllNodesWithText("Select photos folder", substring = false, ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        val hasDesktopLabel = composeRule.onAllNodesWithText("Select a Folder", substring = false, ignoreCase = true)
            .fetchSemanticsNodes().isNotEmpty()
        if (hasDesktopLabel) {
            composeRule.onNodeWithText("Select a folder to start reviewing and culling your photos.", substring = true, ignoreCase = true)
                .assertIsDisplayed()
        } else {
            composeRule.onNodeWithText("Select photos folder", substring = false, ignoreCase = true)
                .assertIsDisplayed()
        }
    }

    @Test
    fun navigateToStatistics() {
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithText("Select a Folder", substring = false, ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty() ||
            composeRule.onAllNodesWithText("Select photos folder", substring = false, ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        val isDesktop = composeRule.onAllNodesWithText("Select a Folder", substring = false, ignoreCase = true)
            .fetchSemanticsNodes().isNotEmpty()
        if (!isDesktop) {
            composeRule.onNodeWithText("Select photos folder", substring = false, ignoreCase = true)
                .assertIsDisplayed()
            return
        }

        // Tap on the Statistics nav item
        composeRule.onNode(
            hasClickAction() and hasAnyDescendant(hasContentDescription("Statistics")),
            useUnmergedTree = true
        ).performClick()

        composeRule.waitForIdle()

        // Statistics screen should show its empty state description
        composeRule.onNodeWithText("Select a folder containing photos to view EXIF statistics.", substring = true, ignoreCase = true)
            .assertIsDisplayed()
    }

    @Test
    fun navigateToDuplicates() {
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithText("Select a Folder", substring = false, ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty() ||
            composeRule.onAllNodesWithText("Select photos folder", substring = false, ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        val isDesktop = composeRule.onAllNodesWithText("Select a Folder", substring = false, ignoreCase = true)
            .fetchSemanticsNodes().isNotEmpty()
        if (!isDesktop) {
            composeRule.onNodeWithText("Select photos folder", substring = false, ignoreCase = true)
                .assertIsDisplayed()
            return
        }

        composeRule.onNode(
            hasClickAction() and hasAnyDescendant(hasContentDescription("Duplicates")),
            useUnmergedTree = true
        ).performClick()

        composeRule.waitForIdle()

        // Duplicates screen should show its empty state description
        composeRule.onNodeWithText("Select a folder to scan for duplicate images.", substring = true, ignoreCase = true)
            .assertIsDisplayed()
    }

    @Test
    fun navigateToSettings() {
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithText("Select a Folder", substring = false, ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty() ||
            composeRule.onAllNodesWithText("Select photos folder", substring = false, ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        val isDesktop = composeRule.onAllNodesWithText("Select a Folder", substring = false, ignoreCase = true)
            .fetchSemanticsNodes().isNotEmpty()
        if (!isDesktop) {
            composeRule.onNodeWithText("Select photos folder", substring = false, ignoreCase = true)
                .assertIsDisplayed()
            return
        }

        composeRule.onNode(
            hasClickAction() and hasAnyDescendant(hasContentDescription("Settings")),
            useUnmergedTree = true
        ).performClick()

        composeRule.waitForIdle()

        // Settings screen should show unique settings text
        composeRule.onNodeWithText("Analysis Threads", substring = true, ignoreCase = true)
            .assertExists()
    }

    @Test
    fun navigateBackToSelector() {
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithText("Select a Folder", substring = false, ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty() ||
            composeRule.onAllNodesWithText("Select photos folder", substring = false, ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        val isDesktop = composeRule.onAllNodesWithText("Select a Folder", substring = false, ignoreCase = true)
            .fetchSemanticsNodes().isNotEmpty()
        if (!isDesktop) {
            composeRule.onNodeWithText("Select photos folder", substring = false, ignoreCase = true)
                .assertIsDisplayed()
            return
        }

        // Navigate away first
        composeRule.onNode(
            hasClickAction() and hasAnyDescendant(hasContentDescription("Settings")),
            useUnmergedTree = true
        ).performClick()
        composeRule.waitForIdle()

        // Navigate back to Selector
        composeRule.onNode(
            hasClickAction() and hasAnyDescendant(hasContentDescription("Selector")),
            useUnmergedTree = true
        ).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Select a folder to start reviewing and culling your photos.", substring = true, ignoreCase = true)
            .assertIsDisplayed()
    }
}
