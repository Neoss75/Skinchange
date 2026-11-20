package com.neos.skinchange

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, SettingsFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) onFolderPicked(uri)
        }

        private fun storedFolder(): Uri? =
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString(MainActivity.KEY_FOLDER, null)?.let(Uri::parse)

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            findPreference<Preference>(MainActivity.KEY_FOLDER)?.apply {
                summary = folderSummary(storedFolder())
                setOnPreferenceClickListener {
                    pickFolder.launch(storedFolder())
                    true
                }
            }
        }

        private fun onFolderPicked(uri: Uri) {
            val context = requireContext()
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            val ok = SkinSwitcher(SafSkinFs(context, uri)).state() != null
            Toast.makeText(context, if (ok) R.string.toast_folder_ok else R.string.toast_folder_bad, Toast.LENGTH_LONG)
                .show()
            if (ok) {
                PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .putString(MainActivity.KEY_FOLDER, uri.toString()).apply()
                findPreference<Preference>(MainActivity.KEY_FOLDER)?.summary = folderSummary(uri)
            }
        }

        private fun folderSummary(uri: Uri?): String {
            if (uri == null) return getString(R.string.pref_folder_not_set)
            val id = try {
                DocumentsContract.getTreeDocumentId(uri)
            } catch (e: IllegalArgumentException) {
                return uri.toString()
            }
            val volume = id.substringBefore(':')
            val path = id.substringAfter(':', "")
            return if (volume == "primary") "/$path" else "$volume/$path"
        }
    }
}
