package com.neos.skinchange

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.IOException

/** [SkinFs] on a folder picked through the Storage Access Framework. */
class SafSkinFs(private val context: Context, treeUri: Uri) : SkinFs {

    private val root: DocumentFile? = DocumentFile.fromTreeUri(context, treeUri)

    private fun dir(name: String) = root?.findFile(name)?.takeIf { it.isDirectory }

    override fun dirExists(name: String) = dir(name) != null

    override fun readMarker(dir: String): String? =
        dir(dir)?.findFile(MARKER)?.takeIf { it.isFile }?.let(::readBytes)?.let(SkinConfig::decode)

    override fun readRootFile(name: String): ByteArray? =
        root?.findFile(name)?.takeIf { it.isFile }?.let(::readBytes)

    private fun readBytes(file: DocumentFile): ByteArray? = try {
        context.contentResolver.openInputStream(file.uri)?.use { it.readBytes() }
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    }

    override fun rename(from: String, to: String): Boolean = try {
        dir(from)?.renameTo(to) == true && dirExists(to)
    } catch (e: SecurityException) {
        false
    }

    companion object {
        const val MARKER = "save.ch"
    }
}
