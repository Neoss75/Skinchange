package com.neos.skinchange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SkinSwitcherTest {

    /** In-memory iGO folder: dir name -> marker text (null = no save.ch), plus root files. */
    private class FakeFs(vararg dirs: Pair<String, String?>) : SkinFs {
        val dirs = linkedMapOf(*dirs)
        val files = mutableMapOf<String, ByteArray>()
        var failOn: String? = null
        override fun dirExists(name: String) = name in dirs
        override fun readMarker(dir: String) = dirs[dir]
        override fun readRootFile(name: String) = files[name]
        override fun rename(from: String, to: String): Boolean {
            if (from == failOn || from !in dirs || to in dirs) return false
            dirs[to] = dirs.remove(from)
            return true
        }
    }

    /** [SkinFs] on real folders, to check the renames on an actual file system. */
    private class FileFs(private val root: File) : SkinFs {
        override fun dirExists(name: String) = File(root, name).isDirectory
        override fun readMarker(dir: String) =
            File(root, "$dir/save.ch").takeIf { it.isFile }?.readBytes()?.let(SkinConfig::decode)
        override fun readRootFile(name: String) = File(root, name).takeIf { it.isFile }?.readBytes()
        override fun rename(from: String, to: String) = File(root, from).renameTo(File(root, to))
    }

    private val sys4 = "[skin_changer]\r\nskin_quantity = 4\r\nskin_1 = \"name 1\"\r\nskin_2 = \"name 2\"\r\n" +
        "skin_3 = \"name 3\"\r\nskin_4 = \"name 4\""

    /** The layout of D:\Downloads\IGO: skin 1 active, 2..4 stashed, markers in save. */
    private fun fourSkins(sys: String = sys4) = FakeFs(
        "save" to "skin_1", "ux" to null,
        "save_2" to "skin_2", "ux_2" to null,
        "save_3" to "skin_3", "ux_3" to null,
        "save_4" to "skin_4", "ux_4" to null,
    ).apply { files["sys.txt"] = sys.toByteArray() }

    @Test fun `reads the sample folder`() {
        val s = SkinSwitcher(fourSkins()).state()!!
        assertEquals(1, s.current)
        assertEquals(4, s.quantity)
        assertEquals(setOf(1, 2, 3, 4), s.available)
        assertEquals("name 3", s.config!!.name(3))
    }

    @Test fun `switches save and ux and back`() {
        val fs = fourSkins()
        val sw = SkinSwitcher(fs)
        assertEquals(SkinSwitcher.Result.Ok, sw.switchTo(3))
        assertEquals("skin_3", fs.dirs["save"])
        assertEquals("skin_1", fs.dirs["save_1"])
        assertTrue("ux_1" in fs.dirs && "ux" in fs.dirs && "ux_3" !in fs.dirs && "save_3" !in fs.dirs)
        assertEquals(SkinSwitcher.Result.Ok, sw.switchTo(4))
        assertEquals(4, sw.state()!!.current)
        assertEquals(SkinSwitcher.Result.Ok, sw.switchTo(1))
        assertEquals(setOf("save", "ux", "save_2", "ux_2", "save_3", "ux_3", "save_4", "ux_4"), fs.dirs.keys)
    }

    @Test fun `skin_quantity limits the buttons`() {
        val s = SkinSwitcher(fourSkins(sys4.replace("skin_quantity = 4", "skin_quantity = 2"))).state()!!
        assertEquals(2, s.quantity)
        assertEquals(setOf(1, 2), s.available)
        val fs = fourSkins(sys4.replace("skin_quantity = 4", "skin_quantity = 3"))
        assertEquals(setOf(1, 2, 3), SkinSwitcher(fs).state()!!.available)
        assertTrue(SkinSwitcher(fs).switchTo(4) is SkinSwitcher.Result.Failed)
    }

    @Test fun `missing folders make a skin unavailable`() {
        val fs = fourSkins().apply { dirs.remove("ux_3") }
        val s = SkinSwitcher(fs).state()!!
        assertEquals(setOf(1, 2, 4), s.available)
        assertTrue(SkinSwitcher(fs).switchTo(3) is SkinSwitcher.Result.Failed)
    }

    @Test fun `without sys txt the folders decide`() {
        val fs = fourSkins().apply { files.clear(); dirs.remove("save_4"); dirs.remove("ux_4") }
        val s = SkinSwitcher(fs).state()!!
        assertNull(s.config)
        assertEquals(3, s.quantity)
    }

    @Test fun `ux-only mode swaps only ux`() {
        val fs = FakeFs("ux" to "skin_2", "ux_1" to "skin_1", "save" to null)
        fs.files["sys.txt"] = "[skin_changer]\nskin_quantity = 2".toByteArray()
        assertEquals(SkinSwitcher.Result.Ok, SkinSwitcher(fs).switchTo(1))
        assertEquals(setOf("save", "ux_2", "ux"), fs.dirs.keys)
        assertEquals("skin_1", fs.dirs["ux"])
    }

    @Test fun `failure in the middle is rolled back`() {
        val fs = fourSkins()
        fs.failOn = "ux" // third of four renames
        val before = fs.dirs.toMap()
        val r = SkinSwitcher(fs).switchTo(2)
        assertTrue(r is SkinSwitcher.Result.Failed && r.rolledBack)
        assertEquals(before, fs.dirs.toMap())
    }

    @Test fun `does not overwrite an existing folder`() {
        // A stray ux_1 would be clobbered (or renamed to "ux_1 (1)") by a naive switch.
        val fs = fourSkins().apply { dirs["ux_1"] = null }
        val before = fs.dirs.toMap()
        assertTrue(SkinSwitcher(fs).switchTo(2) is SkinSwitcher.Result.Failed)
        assertEquals(before, fs.dirs.toMap())
    }

    @Test fun `markers`() {
        assertEquals(3, SkinSwitcher.parseMarker("skin_3"))
        assertEquals(1, SkinSwitcher.parseMarker("\uFEFFSKIN_1\r\n"))
        assertNull(SkinSwitcher.parseMarker("skin_5"))
        assertNull(SkinSwitcher.parseMarker("default_skin"))
        assertNull(SkinSwitcher(FakeFs("save" to "weird", "ux" to null)).state())
    }

    @get:Rule val tmp = TemporaryFolder()

    @Test fun `real folders like D Downloads IGO`() {
        val root = tmp.newFolder("IGO")
        File(root, "sys.txt").writeBytes(sys4.toByteArray())
        File(root, "save").mkdir(); File(root, "save/save.ch").writeText("skin_1")
        File(root, "ux").mkdir()
        for (n in 2..4) {
            File(root, "save_$n").mkdir(); File(root, "save_$n/save.ch").writeText("skin_$n")
            File(root, "ux_$n").mkdir(); File(root, "ux_$n/marker_$n").writeText("$n") // to see where ux_N went
        }
        val sw = SkinSwitcher(FileFs(root))
        assertEquals(SkinSwitcher.Result.Ok, sw.switchTo(4))
        assertEquals("skin_4", File(root, "save/save.ch").readText())
        assertTrue(File(root, "ux/marker_4").isFile)
        assertEquals("skin_1", File(root, "save_1/save.ch").readText())
        assertEquals(SkinSwitcher.Result.Ok, sw.switchTo(2))
        assertEquals(2, sw.state()!!.current)
        assertTrue(File(root, "ux/marker_2").isFile && File(root, "ux_4/marker_4").isFile)
    }
}
