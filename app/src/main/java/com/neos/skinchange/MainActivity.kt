package com.neos.skinchange

import android.animation.ArgbEvaluator
import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.TextViewCompat
import android.view.Gravity
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.neos.skinchange.ui.HeroBadgeView
import com.neos.skinchange.ui.Motion
import com.neos.skinchange.ui.Palette
import com.neos.skinchange.ui.PillDrawable
import com.neos.skinchange.ui.SkinBackdropView
import com.neos.skinchange.ui.SkinStyle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    /** A row of the "Details" card; tapping it opens the settings. */
    private class InfoRow(root: View, @DrawableRes iconRes: Int, @StringRes labelRes: Int, onClick: () -> Unit) {
        val value: TextView = root.findViewById(R.id.info_value)
        private val note: TextView = root.findViewById(R.id.info_note)

        init {
            root.findViewById<ImageView>(R.id.info_icon).setImageResource(iconRes)
            root.findViewById<TextView>(R.id.info_label).setText(labelRes)
            root.setOnClickListener { onClick() }
        }

        fun setNote(text: String?) {
            note.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
            note.text = text
        }
    }

    private enum class Kind { ACTIVE, READY, MISSING, SWITCHING }

    /** The rounded button of one skin. */
    @SuppressLint("ClickableViewAccessibility") // the touch listener only adds a press animation
    private inner class SkinCard(val root: View, val skin: Int) {
        private val number: TextView = root.findViewById(R.id.skin_number)
        private val name: TextView = root.findViewById(R.id.skin_name)
        private val state: TextView = root.findViewById(R.id.skin_state)
        private val icon: ImageView = root.findViewById(R.id.skin_icon)
        private val pill = PillDrawable(resources.displayMetrics.density)
        private var spin: Motion.Tween? = null
        var kind: Kind? = null
            private set

        init {
            number.text = String.format(Locale.getDefault(), "%d", skin)
            // The number shows the colours this skin gives the screen.
            val own = SkinStyle.palette(skin)
            number.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(own.bottom, own.top)).apply {
                shape = GradientDrawable.OVAL
                setStroke((2 * resources.displayMetrics.density).toInt(), 0xCCFFFFFF.toInt())
            }
            val mask = GradientDrawable().apply {
                cornerRadius = 1000f
                setColor(0xFFFFFFFF.toInt())
            }
            root.background = RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), pill, mask)
            root.setOnClickListener { switchTo(skin) }
            root.setOnTouchListener { v, e ->
                if (motion && v.isClickable) when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> Motion.animate(v, 120, scale = 0.96f).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        Motion.animate(v, 450, Motion.overshoot(3f), scale = 1f).start()
                }
                false
            }
        }

        fun bind(title: String, kind: Kind, p: Palette) {
            this.kind = kind
            name.text = title
            val white = 0xFFFFFFFF.toInt()
            val muted = ContextCompat.getColor(this@MainActivity, R.color.on_skin_muted)
            val active = kind == Kind.ACTIVE
            pill.fillColor = if (active) 0xF7FFFFFF.toInt() else 0x2EFFFFFF
            pill.strokeColor = if (active) 0 else 0x4DFFFFFF
            pill.progress = 0f
            pill.shimmerColor = when {
                !motion -> 0
                active -> (p.bottom and 0x00FFFFFF) or 0x38000000
                kind == Kind.SWITCHING -> 0x80FFFFFF.toInt()
                else -> 0
            }
            name.setTextColor(if (active) p.top else white)
            state.setTextColor(if (active) p.bottom else muted)
            state.setText(
                when (kind) {
                    Kind.ACTIVE -> R.string.skin_state_active
                    Kind.READY -> R.string.skin_state_ready
                    Kind.MISSING -> R.string.skin_state_missing
                    Kind.SWITCHING -> R.string.skin_state_switching
                },
            )
            icon.setImageResource(
                when (kind) {
                    Kind.ACTIVE -> R.drawable.ic_check
                    Kind.READY -> R.drawable.ic_chevron
                    Kind.MISSING -> R.drawable.ic_warning
                    Kind.SWITCHING -> R.drawable.ic_sync
                },
            )
            ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(if (active) p.top else white))
            icon.alpha = if (kind == Kind.READY) 0.7f else 1f
            root.alpha = if (kind == Kind.MISSING) 0.5f else 1f
            root.isClickable = kind == Kind.READY && !busy
            setSpinning(kind == Kind.SWITCHING)
        }

        /** Fill of the progress pill, 0..1. */
        fun setProgress(f: Float) {
            pill.progress = f
        }

        private fun setSpinning(on: Boolean) {
            spin?.cancel()
            spin = null
            icon.rotation = 0f
            if (!on || !motion) return
            spin = Motion.Tween(800, { it }) { f -> icon.rotation = -360f * f }
            spin!!.then { if (this.kind == Kind.SWITCHING) setSpinning(true) }.start()
        }

        /** A quick "pop" when this skin has just been switched on. */
        fun pop() {
            if (!motion) return
            Motion.animate(root, 130, scale = 0.92f).then {
                Motion.animate(root, 600, Motion.overshoot(3f), scale = 1f).start()
            }.start()
        }
    }

    private lateinit var backdrop: SkinBackdropView
    private lateinit var warning: View
    private lateinit var errorCard: View
    private lateinit var hero: View
    private lateinit var heroBadge: View
    private lateinit var heroGlow: HeroBadgeView
    private lateinit var heroIcon: ImageView
    private lateinit var heroName: TextView
    private lateinit var heroSub: TextView
    private lateinit var skins: View
    private lateinit var cards: List<SkinCard>
    private lateinit var info: View
    private lateinit var folderRow: InfoRow
    private lateinit var sysRow: InfoRow
    private lateinit var navigatorRow: InfoRow
    private lateinit var profiles: Profiles
    private lateinit var profilesBar: View
    private lateinit var profileRow: LinearLayout

    private var state: SkinSwitcher.State? = null
    private var busy = false
    private var motion = true
    private var skinsShown = false

    private val background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0, 0))
    /** Colours currently on screen (GradientDrawable.getColors needs API 24); 0 = not set yet. */
    private val shownColors = intArrayOf(0, 0)
    private var palette = SkinStyle.idle
    private var paletteTween: Motion.Tween? = null
    private var bob: Motion.Tween? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        motion = Motion.enabled(this)
        profiles = Profiles(this)

        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        window.setBackgroundDrawable(background)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        backdrop = findViewById(R.id.backdrop)
        warning = findViewById(R.id.warning)
        errorCard = findViewById(R.id.error_card)
        hero = findViewById(R.id.hero)
        heroBadge = findViewById(R.id.hero_badge)
        heroGlow = findViewById(R.id.hero_glow)
        heroIcon = findViewById(R.id.hero_icon)
        heroName = findViewById(R.id.hero_name)
        heroSub = findViewById(R.id.hero_sub)
        skins = findViewById(R.id.skins)
        cards = listOf(R.id.skin1, R.id.skin2, R.id.skin3, R.id.skin4)
            .mapIndexed { i, id -> SkinCard(findViewById(id), i + 1) }
        info = findViewById(R.id.info)
        folderRow = InfoRow(findViewById(R.id.info_folder), R.drawable.ic_folder, R.string.info_folder) { openProfile() }
        sysRow = InfoRow(findViewById(R.id.info_sys), R.drawable.ic_file, R.string.info_sys, ::openHelp)
        navigatorRow = InfoRow(findViewById(R.id.info_navigator), R.drawable.ic_car, R.string.info_navigator) { openProfile() }
        profilesBar = findViewById(R.id.profiles_bar)
        profileRow = findViewById(R.id.profiles)
        warning.setOnClickListener { openProfile() }
        errorCard.setOnClickListener { openProfile() }
        findViewById<TextView>(R.id.version).text = getString(R.string.version, versionName())

        backdrop.animated = motion
        heroGlow.animated = motion
        applyPalette(palette)
        if (savedInstanceState == null) enter(hero, info)
    }

    override fun onResume() {
        super.onResume()
        val nowMotion = Motion.enabled(this)
        if (nowMotion != motion) {
            motion = nowMotion
            backdrop.animated = motion
            heroGlow.animated = motion
        }
        setBobbing(true)
        refresh()
    }

    override fun onPause() {
        setBobbing(false)
        super.onPause()
    }

    /** The badge in the middle floats gently up and down. */
    private fun setBobbing(on: Boolean) {
        bob?.cancel()
        bob = null
        heroBadge.translationY = 0f
        if (!on || !motion) return
        val lift = 7 * resources.displayMetrics.density
        bob = Motion.Tween(1800, Motion.smooth, loop = true) { f -> heroBadge.translationY = -lift * f }.start()
    }

    private fun prefs() = PreferenceManager.getDefaultSharedPreferences(this)

    private fun folder(): Uri? = profiles.active().folder

    private fun switcher(): SkinSwitcher? = folder()?.let { SkinSwitcher(SafSkinFs(this, it)) }

    private fun refresh() {
        val switcher = switcher()
        if (switcher == null) {
            render(null)
            return
        }
        lifecycleScope.launch {
            render(withContext(Dispatchers.IO) { switcher.state() })
        }
    }

    // ---- Rendering ----

    private fun render(s: SkinSwitcher.State?) {
        val previous = state
        state = s
        val hasFolder = folder() != null
        renderProfiles(if (s == null) SkinStyle.idle else SkinStyle.palette(s.current))
        warning.visibility = if (hasFolder) View.GONE else View.VISIBLE
        errorCard.visibility = if (hasFolder && s == null) View.VISIBLE else View.GONE

        if (s == null) {
            applyPalette(SkinStyle.idle)
            setText(heroName, getString(if (hasFolder) R.string.hero_empty else R.string.hero_no_folder))
            heroSub.visibility = View.GONE
            skins.visibility = View.GONE
            skinsShown = false
        } else {
            val target = SkinStyle.palette(s.current)
            applyPalette(target)
            setText(heroName, skinName(s, s.current))
            heroSub.visibility = View.VISIBLE
            setText(heroSub, getString(R.string.hero_sub, s.current, s.quantity))
            renderCards(s, target)
            if (previous != null && previous.current != s.current) {
                spinHeroIcon()
                cards[s.current - 1].pop()
            }
            if (!skinsShown) {
                skins.visibility = View.VISIBLE
                enter(*cards.filter { it.skin <= s.quantity }.map { it.root }.toTypedArray())
                skinsShown = true
            }
        }
        renderInfo(s)
    }

    private fun renderCards(s: SkinSwitcher.State, p: Palette, switching: Int? = null) {
        for (card in cards) {
            val skin = card.skin
            card.root.visibility = if (skin <= s.quantity) View.VISIBLE else View.GONE
            if (skin > s.quantity) continue
            val kind = when {
                skin == switching -> Kind.SWITCHING
                skin == s.current -> Kind.ACTIVE
                skin !in s.available -> Kind.MISSING
                else -> Kind.READY
            }
            card.bind(skinName(s, skin), kind, p)
        }
    }

    private fun renderInfo(s: SkinSwitcher.State?) {
        val uri = folder()
        folderRow.value.text = uri?.let(profiles::describeFolder) ?: getString(R.string.info_not_set)
        folderRow.setNote(s?.let { getString(if (it.uxOnly) R.string.info_mode_ux else R.string.info_mode_both) })

        when {
            s == null -> {
                sysRow.value.text = getString(R.string.hero_empty)
                sysRow.setNote(null)
            }
            s.config == null -> {
                sysRow.value.text = getString(R.string.info_sys_missing)
                sysRow.setNote(getString(R.string.info_sys_missing_note))
            }
            else -> {
                sysRow.value.text = getString(R.string.info_sys_ok, s.quantity)
                sysRow.setNote(getString(R.string.info_sys_names, (1..s.quantity).joinToString(", ") { skinName(s, it) }))
            }
        }

        val p = prefs()
        val pkg = profiles.active().pkg
        navigatorRow.value.text = when (pkg) {
            null -> getString(R.string.info_not_set)
            else -> AppList.label(this, pkg) ?: getString(R.string.pref_package_missing, pkg)
        }
        val notes = mutableListOf(
            getString(if (p.getBoolean(KEY_LAUNCH, false)) R.string.info_navigator_autostart else R.string.info_navigator_no_autostart),
        )
        if (p.getBoolean(KEY_CLOSE, false)) notes += getString(R.string.info_close)
        navigatorRow.setNote(notes.joinToString("\n"))
    }

    /** One pill per navigator, shown when there are two or more; the selected one is white. */
    private fun renderProfiles(p: Palette) {
        val all = profiles.all()
        profilesBar.visibility = if (all.size > 1) View.VISIBLE else View.GONE
        profileRow.removeAllViews()
        if (all.size < 2) return
        val density = resources.displayMetrics.density
        val activeId = profiles.activeId
        for (profile in all) {
            val selected = profile.id == activeId
            val fg = if (selected) p.top else 0xFFFFFFFF.toInt()
            val chip = TextView(this).apply {
                text = profiles.displayName(profile)
                TextViewCompat.setTextAppearance(this, com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
                setTextColor(fg)
                gravity = Gravity.CENTER_VERTICAL
                minHeight = (44 * density).toInt()
                setPadding((14 * density).toInt(), 0, (18 * density).toInt(), 0)
                setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_car, 0, 0, 0)
                compoundDrawablePadding = (8 * density).toInt()
                TextViewCompat.setCompoundDrawableTintList(this, ColorStateList.valueOf(fg))
                val pill = PillDrawable(density).apply {
                    fillColor = if (selected) 0xF7FFFFFF.toInt() else 0x2EFFFFFF
                    strokeColor = if (selected) 0 else 0x4DFFFFFF
                }
                val mask = GradientDrawable().apply {
                    cornerRadius = 1000f
                    setColor(0xFFFFFFFF.toInt())
                }
                background = RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), pill, mask)
                setOnClickListener { selectProfile(profile.id) }
            }
            profileRow.addView(
                chip,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { marginEnd = (8 * density).toInt() },
            )
        }
    }

    /** Shows the skins of another navigator, with the buttons flying in again. */
    private fun selectProfile(id: String) {
        if (busy || id == profiles.activeId) return
        profiles.activeId = id
        state = null
        skinsShown = false
        spinHeroIcon()
        heroGlow.flare()
        refresh()
    }

    // ---- Switching ----

    private fun switchTo(target: Int) {
        val s = state ?: return
        val switcher = switcher() ?: return
        if (busy) return
        busy = true
        renderCards(s, palette, switching = target)
        val card = cards[target - 1]

        // The pill fills with the real progress of the renames, but never faster than the
        // animation, so it can be seen even though renaming is usually instant.
        var real = 0f
        var timed = if (motion) 0f else 1f
        val shown = { card.setProgress(min(real, timed)) }
        val filled = CompletableDeferred<Unit>()
        if (motion) {
            Motion.Tween(PROGRESS_MS, Motion.standard) { f -> timed = f; shown() }
                .then { filled.complete(Unit) }.start()
        } else {
            filled.complete(Unit)
        }

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                switcher.switchTo(target) { done, total ->
                    runOnUiThread { real = done.toFloat() / total; shown() }
                }
            }
            if (result == SkinSwitcher.Result.Ok) {
                real = 1f
                shown()
                filled.await()
                if (motion) delay(150)
            }
            busy = false
            val newState = withContext(Dispatchers.IO) { switcher.state() }
            when (result) {
                SkinSwitcher.Result.Ok -> {
                    burstFrom(card.root)
                    heroGlow.flare()
                    render(newState)
                    Toast.makeText(this@MainActivity, R.string.toast_done, Toast.LENGTH_SHORT).show()
                    // Let the celebration play before the navigator covers the screen.
                    val p = prefs()
                    if (motion && (p.getBoolean(KEY_LAUNCH, false) || p.getBoolean(KEY_CLOSE, false))) {
                        delay(AFTER_SWITCH_DELAY_MS)
                    }
                    afterSwitch()
                }
                is SkinSwitcher.Result.Failed -> {
                    render(newState)
                    val msg = if (result.rolledBack) R.string.toast_failed else R.string.toast_failed_partial
                    Toast.makeText(this@MainActivity, getString(msg, result.step), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun afterSwitch() {
        val p = prefs()
        if (p.getBoolean(KEY_LAUNCH, false)) {
            val pkg = profiles.active().pkg
            val intent = pkg?.let { packageManager.getLaunchIntentForPackage(it) }
            if (intent != null) startActivity(intent)
            else Toast.makeText(this, R.string.toast_no_app, Toast.LENGTH_SHORT).show()
        }
        if (p.getBoolean(KEY_CLOSE, false)) finish()
    }

    /** Name from sys.txt, or "Skin N". */
    private fun skinName(s: SkinSwitcher.State, skin: Int): String =
        s.config?.name(skin) ?: getString(R.string.skin_fallback, skin)

    // ---- Animations ----

    /** Fades and slides views up into place, one after another. */
    private fun enter(vararg views: View) {
        if (!motion) return
        val shift = 40 * resources.displayMetrics.density
        views.forEachIndexed { i, v ->
            v.alpha = 0f
            v.translationY = shift
            Motion.animate(v, 700, Motion.enter, delayMs = 90L * i, alpha = 1f, translationY = 0f).start()
        }
    }

    /** A wave of light and sparks roll across the screen from the button that was tapped. */
    private fun burstFrom(card: View) {
        val a = IntArray(2)
        val b = IntArray(2)
        card.getLocationInWindow(a)
        backdrop.getLocationInWindow(b)
        backdrop.burst(a[0] - b[0] + card.width / 2f, a[1] - b[1] + card.height / 2f)
    }

    /** The palette in the middle turns a full circle with a little overshoot. */
    private fun spinHeroIcon() {
        if (!motion) return
        heroIcon.rotation = 0f
        Motion.animate(heroIcon, 1100, Motion.overshoot(1.8f), rotation = 360f).then { heroIcon.rotation = 0f }.start()
        Motion.animate(hero, 120, scale = 0.96f).then {
            Motion.animate(hero, 500, Motion.overshoot(2f), scale = 1f).start()
        }.start()
    }

    /** Smoothly blends the background (and the system bars) into the new skin colours. */
    private fun applyPalette(target: Palette) {
        if (target == palette && paletteTween == null && shownColors[0] != 0) return
        val from = shownColors.copyOf()
        paletteTween?.cancel()
        palette = target
        backdrop.setAccent(target.accent)
        heroGlow.accent = target.accent
        if (!motion || from[0] == 0) {
            setBackgroundColors(target.top, target.bottom)
            return
        }
        val argb = ArgbEvaluator()
        paletteTween = Motion.Tween(1000, Motion.standard) { f ->
            setBackgroundColors(
                argb.evaluate(f, from[0], target.top) as Int,
                argb.evaluate(f, from[1], target.bottom) as Int,
            )
        }.then { paletteTween = null }.start()
    }

    @Suppress("DEPRECATION") // system bar colours still apply: the app opts out of edge-to-edge
    private fun setBackgroundColors(top: Int, bottom: Int) {
        shownColors[0] = top
        shownColors[1] = bottom
        background.colors = intArrayOf(top, bottom)
        window.statusBarColor = top
        window.navigationBarColor = bottom
    }

    /** Changes text with a quick fade when the screen is already showing something. */
    private fun setText(view: TextView, text: CharSequence) {
        if (view.text.toString() == text.toString()) return
        if (!motion || view.text.isNullOrEmpty() || !view.isShown) {
            view.text = text
            return
        }
        Motion.animate(view, 150, alpha = 0f).then {
            view.text = text
            Motion.animate(view, 300, alpha = 1f).start()
        }.start()
    }

    // ---- Menu ----

    @Suppress("DEPRECATION")
    private fun versionName(): String = packageManager.getPackageInfo(packageName, 0).versionName ?: ""

    private fun openSettings() = startActivity(Intent(this, SettingsActivity::class.java))

    /** Settings of the navigator shown on the screen. */
    private fun openProfile() = startActivity(
        Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_PROFILE, profiles.activeId),
    )

    private fun openHelp() = startActivity(
        Intent(this, HelpActivity::class.java)
            .putExtra(HelpActivity.EXTRA_TOP, palette.top)
            .putExtra(HelpActivity.EXTRA_BOTTOM, palette.bottom),
    )

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_settings -> { openSettings(); true }
        R.id.action_help -> { openHelp(); true }
        R.id.action_about -> {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.app_name) + " " + versionName())
                .setMessage(R.string.about_text)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    companion object {
        const val KEY_CLOSE = "close_after_switch"
        const val KEY_LAUNCH = "launch_igo"
        const val KEY_ANIMATIONS = "animations"
        private const val PROGRESS_MS = 900L
        private const val AFTER_SWITCH_DELAY_MS = 900L
    }
}
