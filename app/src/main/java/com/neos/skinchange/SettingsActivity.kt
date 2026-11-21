package com.neos.skinchange

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                pickFolder.launch(profile()?.folder)
                true
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

        private fun onFolderPicked(uri: Uri) {
            val context = requireContext()
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            val ok = SkinSwitcher(SafSkinFs(context, uri)).state() != null
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
        private const val KEY_PROFILES = "profiles_list"
        private const val KEY_HELP = "help"
        private const val KEY_NAME = "p_name"
        private const val KEY_FOLDER = "p_folder"
        private const val KEY_PACKAGE = "p_package"
        private const val KEY_DELETE = "p_delete"
    }
}
