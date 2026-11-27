package com.neos.skinchange

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.EditText
import android.widget.FrameLayout
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        if (savedInstanceState == null) {
            val start = intent.getStringExtra(EXTRA_PROFILE)
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, SettingsFragment())
                .commit()
            if (start != null) openProfile(start)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        if (!supportFragmentManager.popBackStackImmediate()) finish()
        return true
    }

    fun openProfile(id: String) {
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, ProfileFragment.newInstance(id))
            .addToBackStack(null)
            .commit()
    }

    /** The main settings: the list of navigators, what happens after a switch, looks, help. */
    class SettingsFragment : PreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            findPreference<Preference>(KEY_HELP)?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), HelpActivity::class.java))
                true
            }
        }

        override fun onResume() {
            super.onResume()
            requireActivity().setTitle(R.string.action_settings)
            buildProfiles()
        }

        private fun buildProfiles() {
            val context = requireContext()
            val profiles = Profiles(context)
            val category = findPreference<PreferenceCategory>(KEY_PROFILES) ?: return
            category.removeAll()
            val activeId = profiles.activeId
            for (p in profiles.all()) {
                category.addPreference(
                    Preference(context).apply {
                        key = "profile_${p.id}"
                        isIconSpaceReserved = false
                        title = profiles.displayName(p)
                        summary = buildString {
                            append(p.folder?.let(profiles::describeFolder) ?: getString(R.string.profile_no_folder))
                            if (p.id == activeId && profiles.all().size > 1) {
                                append('\n').append(getString(R.string.profile_active))
                            }
                        }
                        setOnPreferenceClickListener {
                            (requireActivity() as SettingsActivity).openProfile(p.id)
                            true
                        }
                    },
                )
            }
            if (profiles.canAdd()) {
                category.addPreference(
                    Preference(context).apply {
                        key = "profile_add"
                        isIconSpaceReserved = false
                        setTitle(R.string.profile_add)
                        summary = getString(R.string.profile_add_summary, Profiles.MAX)
                        setOnPreferenceClickListener {
                            (requireActivity() as SettingsActivity).openProfile(profiles.add().id)
                            true
                        }
                    },
                )
            }
        }
    }

    /** One navigator: name, iGO folder, app to launch; it can be removed if it is not the last. */
    class ProfileFragment : PreferenceFragmentCompat() {

        private lateinit var profiles: Profiles
        private lateinit var id: String

        private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) onFolderPicked(uri)
        }

        private val storagePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) showFoundFolders()
            else Toast.makeText(requireContext(), R.string.toast_storage_denied, Toast.LENGTH_LONG).show()
        }

        private fun profile(): Profile? = profiles.get(id)

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            profiles = Profiles(requireContext())
            id = requireArguments().getString(ARG_ID)!!
            setPreferencesFromResource(R.xml.profile, rootKey)

            findPreference<EditTextPreference>(KEY_NAME)?.apply {
                text = profile()?.name.orEmpty()
                setOnPreferenceChangeListener { _, value ->
                    profile()?.let { profiles.save(it.copy(name = (value as String).trim())) }
                    refresh()
                    true
                }
            }
            findPreference<Preference>(KEY_FOLDER)?.setOnPreferenceClickListener {
                chooseFolder()
                true
            }
            findPreference<Preference>(KEY_PICKER)?.apply {
                summary = pickerSummary()
                setOnPreferenceClickListener {
                    startActivity(Intent(requireContext(), HelpActivity::class.java))
                    true
                }
            }
            findPreference<Preference>(KEY_PACKAGE)?.setOnPreferenceClickListener {
                pickApp()
                true
            }
            findPreference<Preference>(KEY_DELETE)?.setOnPreferenceClickListener {
                confirmDelete()
                true
            }
        }

        override fun onResume() {
            super.onResume()
            refresh()
        }

        private fun refresh() {
            val p = profile() ?: return
            val title = profiles.displayName(p)
            requireActivity().title = title
            findPreference<EditTextPreference>(KEY_NAME)?.summary =
                if (p.name.isNullOrBlank()) getString(R.string.profile_name_auto, title) else p.name
            findPreference<Preference>(KEY_FOLDER)?.summary =
                p.folder?.let(profiles::describeFolder) ?: getString(R.string.pref_folder_not_set)
            findPreference<Preference>(KEY_PACKAGE)?.summary = p.pkg?.let {
                AppList.describe(requireContext(), it) ?: getString(R.string.pref_package_missing, it)
            } ?: getString(R.string.pref_package_not_set)
            findPreference<Preference>(KEY_DELETE)?.isVisible = profiles.all().size > 1
        }

        private fun confirmDelete() {
            val p = profile() ?: return
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.profile_delete)
                .setMessage(getString(R.string.profile_delete_confirm, profiles.displayName(p)))
                .setPositiveButton(R.string.profile_delete_button) { _, _ ->
                    profiles.remove(p.id)
                    parentFragmentManager.popBackStack()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        /** Shows the launchable apps on the device, iGO builds first, and stores the chosen package. */
        private fun pickApp() {
            val context = requireContext()
            val loading = MaterialAlertDialogBuilder(context)
                .setTitle(R.string.pref_package)
                .setMessage(R.string.apps_loading)
                .show()
            lifecycleScope.launch {
                val apps = try {
                    withContext(Dispatchers.IO) { AppList.load(context) }
                } finally {
                    loading.dismiss()
                }
                val current = profile()?.pkg
                val adapter = object : ArrayAdapter<AppEntry>(context, R.layout.item_app, apps) {
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val view = convertView ?: layoutInflater.inflate(R.layout.item_app, parent, false)
                        val app = getItem(position)!!
                        view.findViewById<ImageView>(R.id.app_icon).setImageDrawable(app.icon)
                        view.findViewById<TextView>(R.id.app_label).text =
                            if (app.packageName == current) getString(R.string.app_selected, app.label) else app.label
                        view.findViewById<TextView>(R.id.app_package).text = app.packageName
                        return view
                    }
                }
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.pref_package)
                    .setAdapter(adapter) { _, which ->
                        profile()?.let { profiles.save(it.copy(pkg = apps[which].packageName)) }
                        refresh()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }

        /** Whether the system folder picker exists: many head units ship without it. */
        private fun pickerSummary(): String {
            val pkg = StorageFolders.pickerPackage(requireContext())
            return when {
                pkg != null -> getString(R.string.pref_picker_yes, pkg)
                Build.VERSION.SDK_INT >= 30 -> getString(R.string.pref_picker_no_way)
                else -> getString(R.string.pref_picker_list)
            }
        }

        /** The system folder picker, or our own list where a head unit has none. */
        private fun chooseFolder() {
            if (StorageFolders.pickerPackage(requireContext()) == null) return chooseFolderWithoutPicker()
            try {
                pickFolder.launch(profile()?.folder?.takeIf { it.scheme != "file" })
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "No system folder picker", e)
                chooseFolderWithoutPicker()
            }
        }

        private fun chooseFolderWithoutPicker() {
            val context = requireContext()
            when {
                Build.VERSION.SDK_INT >= 30 ->
                    MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.pref_folder)
                        .setMessage(R.string.folder_no_picker)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                    PackageManager.PERMISSION_GRANTED -> storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                else -> showFoundFolders()
            }
        }

        /** iGO folders found on the internal storage, memory cards and USB drives, plus "enter the path". */
        private fun showFoundFolders() {
            val context = requireContext()
            lifecycleScope.launch {
                val found = withContext(Dispatchers.IO) { StorageFolders.findOnStorage(context) }
                val items = found.map { it.absolutePath } + getString(R.string.folder_enter_path)
                MaterialAlertDialogBuilder(context)
                    .setTitle(if (found.isEmpty()) R.string.folder_none_found else R.string.folder_found_title)
                    .setItems(items.toTypedArray()) { _, which ->
                        if (which < found.size) onFolderPicked(Uri.fromFile(found[which])) else enterPath()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }

        private fun enterPath() {
            val context = requireContext()
            @Suppress("DEPRECATION")
            val start = profile()?.folder?.takeIf { it.scheme == "file" }?.path
                ?: (Environment.getExternalStorageDirectory().absolutePath + "/")
            val input = EditText(context).apply {
                setSingleLine()
                setText(start)
                setSelection(start.length)
            }
            val pad = (20 * resources.displayMetrics.density).toInt()
            val box = FrameLayout(context).apply {
                setPadding(pad, pad / 2, pad, 0)
                addView(input)
            }
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.folder_path_title)
                .setMessage(R.string.folder_path_hint)
                .setView(box)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val path = input.text.toString().trim().trimEnd('/')
                    if (path.isNotEmpty()) onFolderPicked(Uri.fromFile(File(path)))
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        private fun onFolderPicked(uri: Uri) {
            val context = requireContext()
            if (uri.scheme != "file") {
                // Keep access to the folder after a reboot. Some head units hand out folders
                // that cannot be kept; the folder then works until the next reboot.
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                } catch (e: SecurityException) {
                    Log.w(TAG, "Cannot keep access to $uri", e)
                }
            }
            val ok = try {
                SkinSwitcher(SafSkinFs(context, uri)).state() != null
            } catch (e: RuntimeException) {
                Log.w(TAG, "Cannot read $uri", e)
                false
            }
            Toast.makeText(context, if (ok) R.string.toast_folder_ok else R.string.toast_folder_bad, Toast.LENGTH_LONG)
                .show()
            if (ok) {
                profile()?.let { profiles.save(it.copy(folder = uri)) }
                refresh()
            }
        }

        companion object {
            private const val ARG_ID = "id"
            fun newInstance(id: String) = ProfileFragment().apply { arguments = Bundle().apply { putString(ARG_ID, id) } }
        }
    }

    companion object {
        /** Opens the settings of this navigator right away. */
        const val EXTRA_PROFILE = "profile"
        private const val TAG = "Settings"
        private const val KEY_PROFILES = "profiles_list"
        private const val KEY_HELP = "help"
        private const val KEY_NAME = "p_name"
        private const val KEY_FOLDER = "p_folder"
        private const val KEY_PICKER = "p_picker"
        private const val KEY_PACKAGE = "p_package"
        private const val KEY_DELETE = "p_delete"
    }
}
