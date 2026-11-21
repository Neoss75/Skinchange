package com.neos.skinchange

/** Minimal file operations on the top level of the iGO folder, so the logic can be unit tested. */
interface SkinFs {
    fun dirExists(name: String): Boolean
    /** Content of `<dir>/save.ch`, or null when it does not exist. */
    fun readMarker(dir: String): String?
    /** Raw bytes of a file in the iGO folder itself (e.g. sys.txt), or null. */
    fun readRootFile(name: String): ByteArray?
    fun rename(from: String, to: String): Boolean
}

/**
 * Switches between numbered skins by renaming folders in the iGO folder.
 *
 * The active skin lives in `save` and `ux`; skin N waits in `save_N` and `ux_N`.
 * Every skin carries its own `save.ch` with its number (`skin_1` … `skin_4`), so the marker
 * moves with its folder. When `ux/save.ch` exists only `ux` is swapped, otherwise both
 * `save` and `ux` are. How many skins there are and their names come from `sys.txt`.
 *
 * A failed switch is rolled back so the folders are never left half-renamed.
 */
class SkinSwitcher(private val fs: SkinFs) {

    data class State(
        val current: Int,
        val uxOnly: Boolean,
        /** Skins 1..[quantity] whose folders are present (including the current one). */
        val available: Set<Int>,
        val quantity: Int,
        val config: SkinConfig?,
    )

    sealed class Result {
        object Ok : Result()
        data class Failed(val step: String, val rolledBack: Boolean) : Result()
    }

    fun config(): SkinConfig? = fs.readRootFile(SYS_TXT)?.let { SkinConfig.parse(SkinConfig.decode(it)) }

    fun state(): State? {
        val uxMarker = fs.readMarker(UX)
        val uxOnly = uxMarker != null
        val current = parseMarker(uxMarker ?: fs.readMarker(SAVE) ?: return null) ?: return null
        val config = config()
        // Without sys.txt: as many buttons as there are skin folders (at least 2).
        val quantity = config?.quantity
            ?: (1..SkinConfig.MAX_SKINS).filter { it == current || isStashed(it, uxOnly) }.maxOrNull()
                ?.coerceIn(SkinConfig.MIN_SKINS, SkinConfig.MAX_SKINS)
            ?: SkinConfig.MIN_SKINS
        val available = (1..quantity).filter { it == current || isStashed(it, uxOnly) }.toSet()
        return State(current, uxOnly, available, quantity, config)
    }

    private fun isStashed(skin: Int, uxOnly: Boolean) =
        fs.dirExists(stash(UX, skin)) && (uxOnly || fs.dirExists(stash(SAVE, skin)))

    /** The renames needed to go from the current skin to [target], in order. */
    fun plan(state: State, target: Int): List<Pair<String, String>> {
        if (target == state.current) return emptyList()
        val dirs = if (state.uxOnly) listOf(UX) else listOf(SAVE, UX)
        return dirs.flatMap { dir ->
            listOf(dir to stash(dir, state.current), stash(dir, target) to dir)
        }
    }

    fun switchTo(target: Int): Result {
        val state = state() ?: return Result.Failed("state", rolledBack = true)
        if (target !in state.available || target == state.current) {
            return Result.Failed("not available", rolledBack = true)
        }
        val done = mutableListOf<Pair<String, String>>()
        for ((from, to) in plan(state, target)) {
            // Never overwrite: the storage provider would silently pick another name.
            val ok = fs.dirExists(from) && !fs.dirExists(to) && fs.rename(from, to)
            if (!ok) {
                val rolledBack = done.asReversed().all { (f, t) -> fs.rename(t, f) }
                return Result.Failed("$from -> $to", rolledBack)
            }
            done += from to to
        }
        return Result.Ok
    }

    companion object {
        const val UX = "ux"
        const val SAVE = "save"
        const val SYS_TXT = "sys.txt"

        private val BOM = 0xFEFF.toChar().toString()
        private val marker = Regex("""^\s*skin_(\d+)\s*$""", RegexOption.IGNORE_CASE)

        fun stash(dir: String, skin: Int) = "${dir}_$skin"

        /** "skin_3" -> 3; anything else (or a number outside 1..4) -> null. */
        fun parseMarker(text: String): Int? =
            marker.find(text.removePrefix(BOM))?.groupValues?.get(1)?.toIntOrNull()
                ?.takeIf { it in 1..SkinConfig.MAX_SKINS }
    }
}
