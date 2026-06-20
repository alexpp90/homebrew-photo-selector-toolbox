package com.photoselectortoolbox.ui.navigation

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for the Screen sealed class — routes, labels, and the
 * Screen.all list used by navigation bars.
 */
class ScreenTest {

    @Test
    fun `all screens have unique routes`() {
        val routes = Screen.all.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun `all screens have non-empty labels`() {
        Screen.all.forEach { screen ->
            assertTrue("${screen.route} has empty label", screen.label.isNotEmpty())
        }
    }

    @Test
    fun `Screen all contains exactly 4 desktop screens`() {
        assertEquals(4, Screen.all.size)
    }

    @Test
    fun `PhotoSelector is start destination`() {
        assertEquals("selector", Screen.PhotoSelector.route)
        assertEquals("Selector", Screen.PhotoSelector.label)
    }

    @Test
    fun `Statistics route and label`() {
        assertEquals("statistics", Screen.Statistics.route)
        assertEquals("Statistics", Screen.Statistics.label)
    }

    @Test
    fun `DuplicateFinder route and label`() {
        assertEquals("duplicates", Screen.DuplicateFinder.route)
        assertEquals("Duplicates", Screen.DuplicateFinder.label)
    }

    @Test
    fun `Settings route and label`() {
        assertEquals("settings", Screen.Settings.route)
        assertEquals("Settings", Screen.Settings.label)
    }


    @Test
    fun `Screen all order is Selector, Statistics, Duplicates, Settings`() {
        assertEquals(Screen.PhotoSelector, Screen.all[0])
        assertEquals(Screen.Statistics, Screen.all[1])
        assertEquals(Screen.DuplicateFinder, Screen.all[2])
        assertEquals(Screen.Settings, Screen.all[3])
    }
}
