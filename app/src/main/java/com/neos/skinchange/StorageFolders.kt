package com.neos.skinchange

import android.content.Context
import android.content.Intent
import android.os.Environment
import java.io.File
import java.io.IOException

/**
 * For car head units without the system folder picker: there the iGO folder is a plain
 * `file://` path, found on the storage or typed in, and accessed with the storage permission.
 */
object StorageFolders {

    /** Where else to look for iGO folders. */
    private val EXTRA_ROOTS = listOf(
        "/mnt/sdcard", "/mnt/sdcard2", "/mnt/extsd", "/mnt/external_sd", "/mnt/usbhost",
        "/mnt/usb_storage", "/sdcard2", "/extsd",
    )

    /** The package of the system folder picker, or null when the device has none. */
    fun pickerPackage(context: Context): String? {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        @Suppress("DEPRECATION") // the flags overload needs API 33
        val info = context.packageManager.resolveActivity(intent, 0) ?: return null
        return info.activityInfo?.packageName
    }

    /** True when [dir] holds `ux` or `save`, like an iGO folder. */
    fun looksLikeIgo(dir: File): Boolean = File(dir, "ux").isDirectory || File(dir, "save").isDirectory

    /** Internal storage, memory cards and USB drives that can be read. */
    fun storageRoots(context: Context): List<File> {
        val roots = mutableListOf<File>()
        @Suppress("DEPRECATION")
        roots += Environment.getExternalStorageDirectory()
        context.getExternalFilesDirs(null).filterNotNull().forEach {
            roots += File(it.absolutePath.substringBefore("/Android/"))
        }
        File("/storage").listFiles()?.filter { it.name != "self" && it.name != "emulated" }?.let { roots += it }
        EXTRA_ROOTS.forEach { roots += File(it) }
        return roots.filter { it.isDirectory && it.canRead() }.distinctBy { canonical(it) }
    }

    /** iGO folders on the storage roots, up to two levels deep (e.g. /storage/sdcard1/Navi/iGO). Slow. */
    fun findOnStorage(context: Context): List<File> {
        val found = mutableListOf<File>()
        fun scan(dir: File, depth: Int) {
            if (looksLikeIgo(dir)) {
                found += dir
                return
            }
            if (depth == 0) return
            dir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") && it.name != "Android" }
                ?.forEach { scan(it, depth - 1) }
        }
        storageRoots(context).forEach { scan(it, 2) }
        return found.distinctBy { canonical(it) }
    }

    private fun canonical(file: File): String = try {
        file.canonicalPath
    } catch (e: IOException) {
        file.absolutePath
    }
}
