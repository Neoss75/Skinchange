package com.neos.skinchange

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var buttons: Map<Skin, Button>
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        buttons = mapOf(
            Skin.DEFAULT to findViewById(R.id.button_default),
            Skin.PONGO to findViewById(R.id.button_pongo),
            Skin.ALT to findViewById(R.id.button_alt),
        )
        buttons.forEach { (skin, button) -> button.setOnClickListener { switchTo(skin) } }
        findViewById<TextView>(R.id.version).text = getString(R.string.version, versionName())
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun prefs() = PreferenceManager.getDefaultSharedPreferences(this)

    private fun switcher(): SkinSwitcher? =
        prefs().getString(KEY_FOLDER, null)?.let { SkinSwitcher(SafSkinFs(this, Uri.parse(it))) }

    private fun refresh() {
        val switcher = switcher()
        if (switcher == null) {
            render(null, getString(R.string.status_no_folder))
            return
        }
        lifecycleScope.launch {
            val state = withContext(Dispatchers.IO) { switcher.state() }
            render(state, if (state == null) getString(R.string.status_no_marker) else null)
        }
    }

    private fun render(state: SkinSwitcher.State?, message: String?) {
        status.text = message ?: getString(R.string.status_current, skinName(state!!.current))
        buttons.forEach { (skin, button) ->
            button.visibility = if (state == null || skin in state.available) View.VISIBLE else View.GONE
            button.isEnabled = !busy && state != null && skin != state.current
        }
    }

    private fun switchTo(target: Skin) {
        val switcher = switcher() ?: return
        busy = true
        buttons.values.forEach { it.isEnabled = false }
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { switcher.switchTo(target) }
            busy = false
            when (result) {
                SkinSwitcher.Result.Ok -> {
                    Toast.makeText(this@MainActivity, R.string.toast_done, Toast.LENGTH_SHORT).show()
                    afterSwitch()
                }
                is SkinSwitcher.Result.Failed -> {
                    val msg = if (result.rolledBack) R.string.toast_failed else R.string.toast_failed_partial
                    Toast.makeText(this@MainActivity, getString(msg, result.step), Toast.LENGTH_LONG).show()
                }
            }
            refresh()
        }
    }

    private fun afterSwitch() {
        val p = prefs()
        if (p.getBoolean(KEY_LAUNCH, false)) {
            val pkg = p.getString(KEY_PACKAGE, null)
            val intent = pkg?.let { packageManager.getLaunchIntentForPackage(it) }
            if (intent != null) startActivity(intent)
            else Toast.makeText(this, R.string.toast_no_app, Toast.LENGTH_SHORT).show()
        }
        if (p.getBoolean(KEY_CLOSE, false)) finish()
    }

    private fun skinName(skin: Skin) = getString(
        when (skin) {
            Skin.DEFAULT -> R.string.skin_default
            Skin.PONGO -> R.string.skin_pongo
            Skin.ALT -> R.string.skin_alt
        }
    )

    @Suppress("DEPRECATION")
    private fun versionName(): String = packageManager.getPackageInfo(packageName, 0).versionName ?: ""

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        if (item.itemId == R.id.action_settings) {
            startActivity(Intent(this, SettingsActivity::class.java)); true
        } else super.onOptionsItemSelected(item)

    companion object {
        const val KEY_FOLDER = "igo_folder"
        const val KEY_CLOSE = "close_after_switch"
        const val KEY_LAUNCH = "launch_igo"
        const val KEY_PACKAGE = "igo_package"
    }
}
