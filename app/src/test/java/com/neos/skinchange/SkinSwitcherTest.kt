package com.neos.skinchange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkinSwitcherTest {

    /** In-memory folder: dir name -> marker text (or null when the dir has no save.ch). */
    private class FakeFs(vararg dirs: Pair<String, String?>) : SkinFs {
        val dirs = linkedMapOf(*dirs)
        var failOn: String? = null
        override fun dirExists(name: String) = name in dirs
        override fun readMarker(dir: String) = dirs[dir]
        override fun rename(from: String, to: String): Boolean {
            if (from == failOn || from !in dirs || to in dirs) return false
            dirs[to] = dirs.remove(from)
            return true
        }
    }

    @Test fun `ux-only mode swaps only ux`() {
        val fs = FakeFs("ux" to "default_skin", "ux_pongo" to "pongo_skin", "save" to null)
        val s = SkinSwitcher(fs)
        assertEquals(setOf(Skin.DEFAULT, Skin.PONGO), s.state()!!.available)
        assertEquals(SkinSwitcher.Result.Ok, s.switchTo(Skin.PONGO))
        assertEquals(setOf("save", "ux_default", "ux"), fs.dirs.keys)
        assertEquals(Skin.PONGO, s.state()!!.current)
    }

    @Test fun `save mode swaps save and ux`() {
        val fs = FakeFs(
            "save" to "pongo_skin", "ux" to null,
            "save_alt" to "alt_skin", "ux_alt" to null,
            "save_default" to "default_skin", "ux_default" to null,
        )
        val s = SkinSwitcher(fs)
        assertEquals(Skin.PONGO, s.state()!!.current)
        assertEquals(SkinSwitcher.Result.Ok, s.switchTo(Skin.ALT))
        assertEquals("alt_skin", fs.dirs["save"])
        assertTrue("save_pongo" in fs.dirs && "ux_pongo" in fs.dirs && "ux" in fs.dirs)
    }

    @Test fun `failure in the middle is rolled back`() {
        val fs = FakeFs(
            "save" to "default_skin", "ux" to null,
            "save_pongo" to "pongo_skin", "ux_pongo" to null,
        )
        fs.failOn = "ux" // third of four renames
        val before = fs.dirs.toMap()
        val r = SkinSwitcher(fs).switchTo(Skin.PONGO)
        assertTrue(r is SkinSwitcher.Result.Failed && r.rolledBack)
        assertEquals(before, fs.dirs.toMap())
    }

    @Test fun `does not overwrite an existing folder`() {
        // A stray ux_default would be clobbered (or renamed to "ux_default (1)") by a naive switch.
        val fs = FakeFs("ux" to "default_skin", "ux_pongo" to "pongo_skin", "ux_default" to null)
        val before = fs.dirs.toMap()
        val r = SkinSwitcher(fs).switchTo(Skin.PONGO)
        assertTrue(r is SkinSwitcher.Result.Failed)
        assertEquals(before, fs.dirs.toMap())
    }

    @Test fun `unknown or missing marker means no state`() {
        assertNull(SkinSwitcher(FakeFs("ux" to null)).state())
        assertNull(SkinSwitcher(FakeFs("ux" to "weird")).state())
        assertEquals(Skin.ALT, SkinSwitcher(FakeFs("ux" to "alt_skin\r\n")).state()!!.current)
    }

    @Test fun `unavailable skin is refused without touching anything`() {
        val fs = FakeFs("ux" to "default_skin")
        val r = SkinSwitcher(fs).switchTo(Skin.ALT)
        assertTrue(r is SkinSwitcher.Result.Failed)
        assertEquals(setOf("ux"), fs.dirs.keys)
    }
}
