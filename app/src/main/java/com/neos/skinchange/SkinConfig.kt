package com.neos.skinchange

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Skin settings from the `[skin_changer]` section of the iGO `sys.txt`:
 *
 * ```
 * [skin_changer]
 * skin_quantity = 4
 * skin_1 = "Day"
 * skin_2 = "Night"
 * ```
 */
data class SkinConfig(val quantity: Int, val names: Map<Int, String>) {

    /** Button label for skin [n], or null when sys.txt gives no name. */
    fun name(n: Int): String? = names[n]?.takeIf { it.isNotBlank() }

    companion object {
        const val MIN_SKINS = 2
        const val MAX_SKINS = 4
        const val SECTION = "skin_changer"

        private val skinKey = Regex("""skin_(\d+)""", RegexOption.IGNORE_CASE)

        /** Parses sys.txt. Returns null when there is no [skin_changer] section. */
        fun parse(text: String): SkinConfig? {
            var inSection = false
            var found = false
            var quantity: Int? = null
            val names = mutableMapOf<Int, String>()
            for (raw in text.lineSequence()) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) continue
                if (line.startsWith("[")) {
                    inSection = line.removePrefix("[").substringBefore("]").trim().equals(SECTION, ignoreCase = true)
                    if (inSection) found = true
                    continue
                }
                if (!inSection) continue
                val key = line.substringBefore('=', "").trim()
                if (key.isEmpty()) continue
                val value = unquote(line.substringAfter('=').trim())
                when {
                    key.equals("skin_quantity", ignoreCase = true) -> quantity = value.toIntOrNull()
                    else -> skinKey.matchEntire(key)?.groupValues?.get(1)?.toIntOrNull()?.let { names[it] = value }
                }
            }
            if (!found) return null
            val q = (quantity ?: names.keys.maxOrNull() ?: MAX_SKINS).coerceIn(MIN_SKINS, MAX_SKINS)
            return SkinConfig(q, names.filterKeys { it in 1..MAX_SKINS })
        }

        private fun unquote(s: String): String {
            val v = if (s.length >= 2 && s.first() == '"') s.substring(1).substringBefore('"') else
                s.substringBefore(';').trim() // unquoted value: allow a trailing comment
            return v.trim()
        }

        /**
         * Decodes an iGO text file: UTF-16 (with or without BOM), UTF-8 (with or without BOM),
         * falling back to Windows-1251 for old Cyrillic files.
         */
        fun decode(bytes: ByteArray): String {
            if (bytes.size >= 2) {
                val b0 = bytes[0].toInt() and 0xFF
                val b1 = bytes[1].toInt() and 0xFF
                if (b0 == 0xFF && b1 == 0xFE) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
                if (b0 == 0xFE && b1 == 0xFF) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            }
            if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
                return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            }
            // UTF-16 without BOM: ASCII text then has a zero in every other byte.
            if (bytes.size >= 4) {
                val oddZeros = bytes.indices.count { it % 2 == 1 && bytes[it] == 0.toByte() }
                val evenZeros = bytes.indices.count { it % 2 == 0 && bytes[it] == 0.toByte() }
                if (oddZeros > bytes.size / 4 && evenZeros == 0) return String(bytes, Charsets.UTF_16LE)
                if (evenZeros > bytes.size / 4 && oddZeros == 0) return String(bytes, Charsets.UTF_16BE)
            }
            return try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString()
            } catch (e: CharacterCodingException) {
                String(bytes, Charset.forName("windows-1251"))
            }
        }
    }
}
