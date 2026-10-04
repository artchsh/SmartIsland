package dev.qarasky.dotisland.data

import org.junit.Assert.*
import org.junit.Test

class DotIslandSettingsTest {
    @Test fun personalProfileHasFixedGeometryAndNoAutomaticExpansion() {
        val s = DotIslandSettings.Default
        assertEquals(112f, s.width, 0f)
        assertEquals(34f, s.height, 0f)
        assertEquals(12f, s.yOffset, 0f)
        assertFalse(s.autoExpandOnNotification)
        assertFalse(s.showOnLockScreen)
        assertFalse(s.showInLandscape)
        assertFalse(s.enableShadow)
    }
    @Test fun powerDoesNotChangeStyle() {
        val on = DotIslandSettings(enabled = true)
        assertTrue(on.enabled)
        assertEquals(DotIslandSettings.Default.pillColor, on.pillColor)
        assertEquals(1f, on.opacity, 0f)
        assertEquals("None", on.swipeDownAction)
    }
}
