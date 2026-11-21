package com.neos.skinchange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.charset.Charset

class SkinConfigTest {

    @Test fun `parses the sample sys txt`() {
        val text = "[skin_changer]\r\nskin_quantity = 4\r\nskin_1 = \"name 1\"\r\nskin_2 = \"name 2\"\r\n" +
            "skin_3 = \"name 3\"\r\nskin_4 = \"name 4\""
        val c = SkinConfig.parse(text)!!
        assertEquals(4, c.quantity)
        assertEquals(mapOf(1 to "name 1", 2 to "name 2", 3 to "name 3", 4 to "name 4"), c.names)
    }

    @Test fun `only the skin_changer section counts`() {
        val text = """
            [interface]
            skin_quantity = 9
            skin_1 = "wrong"
            ; comment
            [Skin_Changer]
            skin_quantity=3
            skin_1="День"
            skin_2 = Ночь ; trailing comment
            skin_3 = "Зима; снег"
            [other]
            skin_4 = "wrong"
        """.trimIndent()
        val c = SkinConfig.parse(text)!!
        assertEquals(3, c.quantity)
        assertEquals("День", c.name(1))
        assertEquals("Ночь", c.name(2))
        assertEquals("Зима; снег", c.name(3))
        assertNull(c.name(4))
    }

    @Test fun `quantity is clamped to 2-4 and defaults sensibly`() {
        assertEquals(4, SkinConfig.parse("[skin_changer]\nskin_quantity = 7")!!.quantity)
        assertEquals(2, SkinConfig.parse("[skin_changer]\nskin_quantity = 1")!!.quantity)
        assertEquals(3, SkinConfig.parse("[skin_changer]\nskin_1=a\nskin_2=b\nskin_3=c")!!.quantity)
        assertNull(SkinConfig.parse("[interface]\nfoo = 1"))
    }

    @Test fun `decodes the encodings iGO files come in`() {
        val text = "[skin_changer]\r\nskin_1 = \"Ночной\""
        assertEquals(text, SkinConfig.decode(text.toByteArray(Charsets.UTF_8)))
        assertEquals(text, SkinConfig.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.toByteArray()))
        assertEquals(text, SkinConfig.decode(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)))
        assertEquals(text, SkinConfig.decode(text.toByteArray(Charsets.UTF_16BE).let { byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + it }))
        assertEquals(text, SkinConfig.decode(text.toByteArray(Charsets.UTF_16LE)))
        assertEquals(text, SkinConfig.decode(text.toByteArray(Charset.forName("windows-1251"))))
    }
}
