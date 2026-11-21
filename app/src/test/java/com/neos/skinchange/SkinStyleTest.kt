package com.neos.skinchange

import com.neos.skinchange.ui.SkinStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkinStyleTest {

    @Test
    fun everySkinHasItsOwnOpaquePalette() {
        val palettes = (1..4).map(SkinStyle::palette)
        assertEquals(4, palettes.toSet().size)
        for (p in palettes + SkinStyle.idle) {
            assertTrue((p.top ushr 24) == 0xFF && (p.bottom ushr 24) == 0xFF)
        }
        assertTrue(SkinStyle.idle !in palettes)
    }

    @Test
    fun splitTreeId() {
        assertEquals("primary" to "iGO", SkinStyle.splitTreeId("primary:iGO"))
        assertEquals("1234-ABCD" to "Navi/iGO", SkinStyle.splitTreeId("1234-ABCD:Navi/iGO/"))
        assertEquals("primary" to "", SkinStyle.splitTreeId("primary:"))
        assertNull(SkinStyle.splitTreeId("raw"))
    }
}
