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
    fun `Screen all contains exactly 5 desktop screens`() {
        assertEquals(5, Screen.all.size)
    }

    @Test
    fun `PhotoSelector is start destination`() {
        assertEquals("selector", Screen.PhotoSelector.route)
        assertEquals("Selector", Screen.PhotoSelector.label)
    }

    @Test
    fun `Inspect route and label`() {
        assertEquals("inspect", Screen.Inspect.route)
        assertEquals("Inspect & Learn", Screen.Inspect.label)
        assertEquals("Inspect", Screen.Inspect.shortLabel)
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
    fun `Screen all order is Selector, Inspect, Statistics, Duplicates, Settings`() {
        assertEquals(Screen.PhotoSelector, Screen.all[0])
        assertEquals(Screen.Inspect, Screen.all[1])
        assertEquals(Screen.Statistics, Screen.all[2])
        assertEquals(Screen.DuplicateFinder, Screen.all[3])
        assertEquals(Screen.Settings, Screen.all[4])
    }
}
