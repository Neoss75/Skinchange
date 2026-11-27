package com.neos.skinchange.ui

/** Background gradient of the main screen, top to bottom, plus a bright accent for the glow. */
data class Palette(val top: Int, val bottom: Int, val accent: Int)

/** Each skin number has its own colours, so the screen shows at a glance which one is on. */
object SkinStyle {

    /** No folder or no skins found yet. */
    val idle = Palette(0xFF263238.toInt(), 0xFF546E7A.toInt(), 0xFF80DEEA.toInt())

    private val palettes = listOf(
        Palette(0xFFBF360C.toInt(), 0xFFF57C00.toInt(), 0xFFFFE082.toInt()), // 1: sunset
        Palette(0xFF1A237E.toInt(), 0xFF5C6BC0.toInt(), 0xFFB388FF.toInt()), // 2: night
        Palette(0xFF004D40.toInt(), 0xFF00897B.toInt(), 0xFFA7FFEB.toInt()), // 3: sea
        Palette(0xFF4A148C.toInt(), 0xFFAB47BC.toInt(), 0xFFFF80AB.toInt()), // 4: berry
    )

    fun palette(skin: Int): Palette = palettes[Math.floorMod(skin - 1, palettes.size)]

    /** Tree URI document id "primary:iGO/x" -> ("primary", "iGO/x"); null when it is not a tree id. */
    /** The same for a plain path: "/storage/emulated/0/iGO" -> ("primary", "iGO"). */
    @Suppress("SdCardPath")
    fun splitFilePath(path: String): Pair<String, String>? {
        val p = path.trimEnd('/')
        for (internal in listOf("/storage/emulated/0", "/sdcard", "/mnt/sdcard")) {
            if (p == internal || p.startsWith("$internal/")) return "primary" to p.removePrefix(internal).trim('/')
        }
        if (p.startsWith("/storage/")) {
            val rest = p.removePrefix("/storage/")
            return rest.substringBefore('/') to rest.substringAfter('/', "")
        }
        return null
    }

    fun splitTreeId(id: String): Pair<String, String>? {
        if (':' !in id) return null
        return id.substringBefore(':') to id.substringAfter(':').trim('/')
    }
}
