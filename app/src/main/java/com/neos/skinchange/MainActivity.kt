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
    /** Buttons for skins 1..4, index 0 = skin 1. */
    private lateinit var buttons: List<Button>
    private var state: SkinSwitcher.State? = null
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        buttons = listOf(R.id.button_skin1, R.id.button_skin2, R.id.button_skin3, R.id.button_skin4)
            .map { findViewById(it) }
        buttons.forEachIndexed { i, button -> button.setOnClickListener { switchTo(i + 1) } }
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
            val s = withContext(Dispatchers.IO) { switcher.state() }
            render(s, if (s == null) getString(R.string.status_no_marker) else null)
        }
    }

    private fun render(s: SkinSwitcher.State?, message: String?) {
        state = s
        status.text = message ?: buildString {
            append(getString(R.string.status_current, skinName(s!!, s.current)))
            if (s.config == null) append("\n").append(getString(R.string.status_no_sys_txt))
        }
        val quantity = s?.quantity ?: 0
        buttons.forEachIndexed { i, button ->
            val skin = i + 1
            button.visibility = if (skin <= quantity) View.VISIBLE else View.GONE
            if (s == null || skin > quantity) return@forEachIndexed
            button.text = when {
                skin == s.current -> getString(R.string.button_active, skinName(s, skin))
                skin !in s.available -> getString(R.string.button_missing, skinName(s, skin))
                else -> skinName(s, skin)
            }
            button.isEnabled = !busy && skin != s.current && skin in s.available
        }
    }

    private fun switchTo(target: Int) {
        val switcher = switcher() ?: return
        busy = true
        buttons.forEach { it.isEnabled = false }
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

    /** Name from sys.txt, or "Skin N". */
    private fun skinName(s: SkinSwitcher.State, skin: Int): String =
        s.config?.name(skin) ?: getString(R.string.skin_fallback, skin)

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
