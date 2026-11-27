package com.neos.skinchange

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.preference.PreferenceManager
import com.neos.skinchange.ui.SkinStyle

/** One installed iGO: its folder with the skins and the app to launch after a switch. */
data class Profile(val id: String, val name: String?, val folder: Uri?, val pkg: String?)

/**
 * The navigators the user switches skins in (up to [MAX]), stored in the default preferences.
 * There is always at least one. Settings of 2.1 and older (one folder, one package) become
 * the first navigator.
 */
class Profiles(private val context: Context) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    init {
        migrate()
    }

    private fun ids(): List<String> =
        prefs.getString(KEY_LIST, null).orEmpty().split(',').filter { it.isNotBlank() }

    fun all(): List<Profile> = ids().map(::read)

    fun get(id: String): Profile? = if (id in ids()) read(id) else null

    private fun read(id: String) = Profile(
        id = id,
        name = prefs.getString(key(id, "name"), null),
        folder = prefs.getString(key(id, "folder"), null)?.let(Uri::parse),
        pkg = prefs.getString(key(id, "package"), null),
    )

    var activeId: String
        get() = prefs.getString(KEY_ACTIVE, null)?.takeIf { it in ids() } ?: ids().first()
        set(value) = prefs.edit().putString(KEY_ACTIVE, value).apply()

    fun active(): Profile = read(activeId)

    fun canAdd() = ids().size < MAX

    /** Adds an empty navigator and returns it. */
    fun add(): Profile {
        val id = nextId(ids())
        prefs.edit().putString(KEY_LIST, (ids() + id).joinToString(",")).apply()
        return read(id)
    }

    fun save(p: Profile) {
        prefs.edit()
            .putString(key(p.id, "name"), p.name?.trim()?.takeIf { it.isNotEmpty() })
            .putString(key(p.id, "folder"), p.folder?.toString())
            .putString(key(p.id, "package"), p.pkg)
            .apply()
    }

    /** Removes a navigator (never the last one) and gives up its folder access if unused. */
    fun remove(id: String) {
        val rest = ids() - id
        if (rest.isEmpty()) return
        val folder = read(id).folder
        prefs.edit()
            .putString(KEY_LIST, rest.joinToString(","))
            .remove(key(id, "name")).remove(key(id, "folder")).remove(key(id, "package"))
            .apply()
        if (folder != null && folder.scheme != "file" && rest.none { read(it).folder == folder }) {
            try {
                context.contentResolver.releasePersistableUriPermission(
                    folder, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (e: SecurityException) {
                // already released
            }
        }
    }

    /** The given name, else the navigator's app name, else the folder name, else "iGO N". */
    fun displayName(p: Profile): String {
        p.name?.takeIf { it.isNotBlank() }?.let { return it }
        p.pkg?.let { AppList.label(context, it) }?.let { return it }
        p.folder?.let(::folderName)?.takeIf { it.isNotEmpty() }?.let { return it }
        return context.getString(R.string.profile_fallback, ids().indexOf(p.id) + 1)
    }

    /** "Internal storage/iGO" or "Memory card 1234-ABCD/iGO". */
    fun describeFolder(uri: Uri): String {
        val (volume, path) = split(uri) ?: return uri.path ?: uri.toString()
        val storage = if (volume == "primary") context.getString(R.string.info_internal_storage)
        else context.getString(R.string.info_sd_card, volume)
        return if (path.isEmpty()) storage else "$storage/$path"
    }

    private fun folderName(uri: Uri): String? =
        if (uri.scheme == "file") uri.path?.trimEnd('/')?.substringAfterLast('/')
        else split(uri)?.second?.substringAfterLast('/')

    private fun split(uri: Uri): Pair<String, String>? =
        if (uri.scheme == "file") uri.path?.let(SkinStyle::splitFilePath)
        else treeId(uri)?.let(SkinStyle::splitTreeId)

    private fun treeId(uri: Uri): String? = try {
        DocumentsContract.getTreeDocumentId(uri)
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun migrate() {
        if (prefs.contains(KEY_LIST)) return
        prefs.edit()
            .putString(KEY_LIST, "1")
            .putString(KEY_ACTIVE, "1")
            .putString(key("1", "folder"), prefs.getString(OLD_FOLDER, null))
            .putString(key("1", "package"), prefs.getString(OLD_PACKAGE, null))
            .remove(OLD_FOLDER)
            .remove(OLD_PACKAGE)
            .apply()
    }

    companion object {
        const val MAX = 4
        private const val KEY_LIST = "profiles"
        private const val KEY_ACTIVE = "active_profile"
        private const val OLD_FOLDER = "igo_folder"
        private const val OLD_PACKAGE = "igo_package"

        private fun key(id: String, field: String) = "profile.$id.$field"

        /** Smallest free number, as a string. */
        fun nextId(ids: List<String>): String {
            val used = ids.mapNotNull { it.toIntOrNull() }.toSet()
            return generateSequence(1) { it + 1 }.first { it !in used }.toString()
        }
    }
}
