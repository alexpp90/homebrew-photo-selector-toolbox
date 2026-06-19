package com.photoselectortoolbox.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.photoselectortoolbox.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test: verifies the app launches without crashing and
 * shows expected UI elements. Runs on emulator or real device.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunchesWithoutCrash() {
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithText("Select a Folder", substring = false, ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty() ||
            composeRule.onAllNodesWithText("Select photos folder", substring = false, ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        val hasDesktopLabel = composeRule.onAllNodesWithText("Select a Folder", substring = false, ignoreCase = true)
            .fetchSemanticsNodes().isNotEmpty()
        if (hasDesktopLabel) {
            composeRule.onNodeWithText("Select a Folder", substring = false, ignoreCase = true)
                .assertIsDisplayed()
        } else {
            composeRule.onNodeWithText("Select photos folder", substring = false, ignoreCase = true)
                .assertIsDisplayed()
        }
    }
}
