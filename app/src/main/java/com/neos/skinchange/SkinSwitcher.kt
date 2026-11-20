package com.neos.skinchange

/** The three skin slots the iGO folder can hold. [marker] is the text in save.ch. */
enum class Skin(val marker: String, val suffix: String) {
    DEFAULT("default_skin", "default"),
    PONGO("pongo_skin", "pongo"),
    ALT("alt_skin", "alt");

    companion object {
        fun fromMarker(text: String): Skin? = entries.firstOrNull { it.marker == text.trim() }
    }
}

/** Minimal file operations on the top level of the iGO folder, so the logic can be unit tested. */
interface SkinFs {
    fun dirExists(name: String): Boolean
    /** Content of `<dir>/save.ch`, or null when it does not exist. */
    fun readMarker(dir: String): String?
    fun rename(from: String, to: String): Boolean
}

/**
 * Switches skins by renaming folders in the iGO folder:
 * the active skin lives in `ux` (and `save`), the others wait in `ux_<skin>` (and `save_<skin>`).
 *
 * When `ux/save.ch` exists only `ux` is swapped; when only `save/save.ch` exists, both `save` and
 * `ux` are swapped. A failed switch is rolled back so the folders are never left half-renamed.
 */
class SkinSwitcher(private val fs: SkinFs) {

    data class State(val current: Skin, val uxOnly: Boolean, val available: Set<Skin>)

    sealed class Result {
        object Ok : Result()
        data class Failed(val step: String, val rolledBack: Boolean) : Result()
    }

    fun state(): State? {
        val uxMarker = fs.readMarker(UX)
        val uxOnly = uxMarker != null
        val marker = uxMarker ?: fs.readMarker(SAVE) ?: return null
        val current = Skin.fromMarker(marker) ?: return null
        val available = Skin.entries.filter { it == current || isStashed(it, uxOnly) }.toSet()
        return State(current, uxOnly, available)
    }

    private fun isStashed(skin: Skin, uxOnly: Boolean) =
        fs.dirExists(stash(UX, skin)) && (uxOnly || fs.dirExists(stash(SAVE, skin)))

    /** The renames needed to go from the current skin to [target], in order. */
    fun plan(state: State, target: Skin): List<Pair<String, String>> {
        if (target == state.current) return emptyList()
        val dirs = if (state.uxOnly) listOf(UX) else listOf(SAVE, UX)
        return dirs.flatMap { dir ->
            listOf(dir to stash(dir, state.current), stash(dir, target) to dir)
        }
    }

    fun switchTo(target: Skin): Result {
        val state = state() ?: return Result.Failed("state", rolledBack = true)
        if (target !in state.available) return Result.Failed("not available", rolledBack = true)
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
        fun stash(dir: String, skin: Skin) = "${dir}_${skin.suffix}"
    }
}
