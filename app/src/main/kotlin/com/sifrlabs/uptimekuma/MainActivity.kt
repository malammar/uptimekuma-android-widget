package com.sifrlabs.uptimekuma

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.*

class MainActivity : Activity() {

    companion object {
        private const val REQ_PROFILE = 2001
    }

    private val intervalOptions = listOf(1, 5, 10, 15, 30, 60)

    // Wizard form fields
    private var wizNameEdit: EditText? = null
    private var wizUrlEdit: EditText? = null
    private var wizHostEdit: EditText? = null
    private var wizSlugEdit: EditText? = null
    private var wizShowAdvanced = false
    private var wizSeekBar: SeekBar? = null
    private var wizAuthCheck: CheckBox? = null
    private var wizUserEdit: EditText? = null
    private var wizPassEdit: EditText? = null

    // Profile manager
    private var profileCardContainer: LinearLayout? = null
    private var statusText: TextView? = null
    private var progressBar: ProgressBar? = null
    private var refreshBtn: TextView? = null
    private var managerRoot: LinearLayout? = null
    private var batteryBanner: View? = null

    // ── Theme / colours ────────────────────────────────────────────────────────

    private val isDark get() = (resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private val cBg      get() = if (isDark) 0xFF0C1A27.toInt() else 0xFFF2F6FA.toInt()
    private val cCard    get() = if (isDark) 0xFF142030.toInt() else 0xFFFFFFFF.toInt()
    private val cCardHi  get() = if (isDark) 0xFF1C2D3E.toInt() else 0xFFF0F5FA.toInt()
    private val cStroke  get() = if (isDark) 0xFF243548.toInt() else 0xFFD8E3EE.toInt()
    private val cText    get() = if (isDark) 0xFFE2EAF4.toInt() else 0xFF18273A.toInt()
    private val cMuted   get() = if (isDark) 0xFF6A8099.toInt() else 0xFF607080.toInt()
    private val cGreen   = 0xFF4CAF50.toInt()
    private val cGreenBg get() = if (isDark) 0xFF0C2218.toInt() else 0xFFE8F5E9.toInt()
    private val cDivider get() = if (isDark) 0xFF192A38.toInt() else 0xFFE2ECF5.toInt()

    // ── Update broadcast ───────────────────────────────────────────────────────

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val ok = intent.getBooleanExtra(WidgetUpdateService.EXTRA_SUCCESS, false)
            progressBar?.visibility = View.GONE
            refreshBtn?.apply { text = "↻"; isEnabled = true; setTextColor(cMuted) }
            val hasWidgets = android.appwidget.AppWidgetManager.getInstance(context)
                .getAppWidgetIds(android.content.ComponentName(context, UptimeWidget::class.java))
                .isNotEmpty()
            if (!hasWidgets) {
                statusText?.text = "No widgets created yet. Add one to your home screen first!"
                statusText?.setTextColor(cMuted)
            } else {
                statusText?.text = if (ok) "✓  Widget updated successfully."
                                   else "↻  Couldn't refresh — widget is showing cached data."
                statusText?.setTextColor(if (ok) cGreen else cMuted)
            }
            statusText?.visibility = View.VISIBLE
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.migrateIfNeeded(this)
        window.decorView.setBackgroundColor(cBg)
        if (!Prefs.isWizardDone(this) && Prefs.getProfiles(this).isEmpty()) {
            showWizard()
        } else {
            showProfileManager()
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(WidgetUpdateService.ACTION_UPDATE_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(updateReceiver, filter)
        }
        checkBatteryOptimization()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(updateReceiver) } catch (_: Exception) {}
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_PROFILE) refreshProfileList()
    }

    override fun onStart() {
        super.onStart()
        // Rebuild the manager screen when returning from WidgetConfigActivity
        // so the Widgets section reflects any color/profile changes.
        if (managerRoot != null) showProfileManager()
    }

    // ── Wizard ─────────────────────────────────────────────────────────────────

    private fun showWizard() {
        val p = dp(24)
        val scroll = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(p, p, p, p)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        scroll.setOnApplyWindowInsetsListener { _, insets ->
            root.setPadding(p, p + sysBarTop(insets), p, p)
            insets
        }

        val step1 = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // App icon
        step1.addView(ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(20)
            }
        })

        step1.addView(tv("UptimeKuma Widget", 26f, cText, bold = true,
            align = Gravity.CENTER, bpad = dp(6)))
        step1.addView(tv("Your homelab, always in view 📡", 14f, cMuted,
            align = Gravity.CENTER, bpad = dp(28)))

        // Divider
        step1.addView(View(this).apply {
            setBackgroundColor(cDivider)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            ).apply { bottomMargin = dp(24) }
        })

        step1.addView(tv(getString(R.string.wizard_welcome_body), 15f, cText,
            lineSpacing = 1.5f, align = Gravity.CENTER, bpad = dp(32)))

        step1.addView(primaryBtn("Get started  →").apply {
            setOnClickListener {
                step1.visibility = View.GONE
                root.gravity = Gravity.TOP
                buildWizardStep2(root)
            }
        })

        root.addView(step1)
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun buildWizardStep2(root: LinearLayout) {
        wizShowAdvanced = false
        val step2 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        step2.addView(tv("🖥️  Add your first instance", 22f, cText, bold = true, bpad = dp(20)))

        // ── Instance Details section header with Advanced switch ───────────────
        val detailsHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        detailsHeaderRow.addView(sectionLabel("Instance Details").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val advSwitch = Switch(this).apply {
            text = "Advanced"; textSize = 12f; setTextColor(cMuted); isChecked = false
            thumbTintList = android.content.res.ColorStateList.valueOf(cMuted)
            trackTintList = android.content.res.ColorStateList.valueOf(cMuted and 0x00FFFFFF or 0x44000000)
        }
        detailsHeaderRow.addView(advSwitch)
        step2.addView(detailsHeaderRow)

        // ── Details card ───────────────────────────────────────────────────────
        val detailsCard = card()
        detailsCard.addView(label("Name"))
        wizNameEdit = styledEdit("Home Lab")
        detailsCard.addView(wizNameEdit)

        val urlSpacer = spacer(12); val urlLabel = label("Status page URL")
        wizUrlEdit = styledEdit("https://uptime.example.com/status/my-slug").apply {
            inputType = InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_CLASS_TEXT
        }
        detailsCard.addView(urlSpacer); detailsCard.addView(urlLabel); detailsCard.addView(wizUrlEdit)

        val hostSpacer = spacer(12); val hostLabel = label("Hostname")
        wizHostEdit = styledEdit(getString(R.string.hint_hostname)).apply {
            inputType = InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_CLASS_TEXT
        }
        val slugSpacer = spacer(8); val slugLabel = label("Slug")
        wizSlugEdit = styledEdit("default")
        detailsCard.addView(hostSpacer); detailsCard.addView(hostLabel); detailsCard.addView(wizHostEdit)
        detailsCard.addView(slugSpacer); detailsCard.addView(slugLabel); detailsCard.addView(wizSlugEdit)
        step2.addView(detailsCard)

        // ── Advanced section ───────────────────────────────────────────────────
        val advSectionLabel = sectionLabel("Advanced")
        val advCard = card()

        wizAuthCheck = CheckBox(this).apply {
            text = getString(R.string.label_basic_auth); setTextColor(cText); textSize = 14f
        }
        val credSpacer = spacer(8)
        wizUserEdit = styledEdit("Username")
        val pwSpacer = spacer(8)
        wizPassEdit = styledEdit("Password").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        advCard.addView(wizAuthCheck)
        advCard.addView(credSpacer); advCard.addView(wizUserEdit)
        advCard.addView(pwSpacer);   advCard.addView(wizPassEdit)

        advCard.addView(spacer(14))
        val intervalValueLabel = tv("5 minutes", 12f, cText)
        val intervalLabelRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        intervalLabelRow.addView(label("Refresh interval").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        intervalLabelRow.addView(intervalValueLabel)
        advCard.addView(intervalLabelRow)
        wizSeekBar = SeekBar(this).apply {
            max = intervalOptions.size - 1; progress = 1
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(32))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    intervalValueLabel.text = formatInterval(intervalOptions[p])
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        advCard.addView(wizSeekBar)

        step2.addView(advSectionLabel); step2.addView(advCard)

        // ── Visibility ─────────────────────────────────────────────────────────
        fun applyWizMode() {
            val adv = wizShowAdvanced
            urlSpacer.visibility     = if (!adv) View.VISIBLE else View.GONE
            urlLabel.visibility      = if (!adv) View.VISIBLE else View.GONE
            wizUrlEdit!!.visibility  = if (!adv) View.VISIBLE else View.GONE
            hostSpacer.visibility    = if (adv) View.VISIBLE else View.GONE
            hostLabel.visibility     = if (adv) View.VISIBLE else View.GONE
            wizHostEdit!!.visibility = if (adv) View.VISIBLE else View.GONE
            slugSpacer.visibility    = if (adv) View.VISIBLE else View.GONE
            slugLabel.visibility     = if (adv) View.VISIBLE else View.GONE
            wizSlugEdit!!.visibility = if (adv) View.VISIBLE else View.GONE
            advSectionLabel.visibility = if (adv) View.VISIBLE else View.GONE
            advCard.visibility         = if (adv) View.VISIBLE else View.GONE
            val showCreds = adv && wizAuthCheck!!.isChecked
            credSpacer.visibility    = if (showCreds) View.VISIBLE else View.GONE
            wizUserEdit!!.visibility = if (showCreds) View.VISIBLE else View.GONE
            pwSpacer.visibility      = if (showCreds) View.VISIBLE else View.GONE
            wizPassEdit!!.visibility = if (showCreds) View.VISIBLE else View.GONE
            advSwitch.thumbTintList = android.content.res.ColorStateList.valueOf(if (adv) cGreen else cMuted)
            advSwitch.trackTintList = android.content.res.ColorStateList.valueOf(
                if (adv) (cGreen and 0x00FFFFFF or 0x44000000) else (cMuted and 0x00FFFFFF or 0x44000000))
        }
        applyWizMode()

        advSwitch.setOnCheckedChangeListener { _, checked ->
            wizShowAdvanced = checked
            if (checked) {
                val (h, s) = parseStatusPageUrl(wizUrlEdit!!.text.toString())
                wizHostEdit!!.setText(h); wizSlugEdit!!.setText(s)
            } else {
                wizUrlEdit!!.setText(reconstructUrl(
                    wizHostEdit!!.text.toString().trim(),
                    wizSlugEdit!!.text.toString().trim()))
            }
            applyWizMode()
        }
        wizAuthCheck!!.setOnCheckedChangeListener { _, _ -> applyWizMode() }

        step2.addView(spacer(8))
        step2.addView(primaryBtn("Save & Continue").apply {
            setOnClickListener { saveWizardProfile(root, step2) }
        })

        root.addView(step2)
    }

    private fun saveWizardProfile(root: LinearLayout, step2: LinearLayout) {
        val name = wizNameEdit!!.text.toString().trim()
        val (hostname, slug) = if (wizShowAdvanced) {
            wizHostEdit!!.text.toString().trimEnd('/') to wizSlugEdit!!.text.toString().trim().ifEmpty { "default" }
        } else {
            parseStatusPageUrl(wizUrlEdit!!.text.toString())
        }
        if (name.isEmpty() || hostname.isEmpty()) {
            Toast.makeText(this, "Name and URL are required", Toast.LENGTH_SHORT).show()
            return
        }
        val profile = Profile(
            id              = Profile.newId(),
            name            = name,
            hostname        = hostname,
            slug            = slug,
            intervalMinutes = intervalOptions[wizSeekBar!!.progress],
            authEnabled     = wizShowAdvanced && wizAuthCheck!!.isChecked,
            username        = wizUserEdit!!.text.toString(),
            password        = wizPassEdit!!.text.toString()
        )
        Prefs.upsertProfile(this, profile)
        Prefs.setWizardDone(this)
        step2.visibility = View.GONE

        val step3 = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        step3.addView(tv("🎉", 52f, cText, align = Gravity.CENTER, bpad = dp(12)))
        step3.addView(tv(getString(R.string.wizard_done_title), 24f, cText,
            bold = true, align = Gravity.CENTER, bpad = dp(12)))
        step3.addView(tv(getString(R.string.wizard_done_body), 15f, cMuted,
            lineSpacing = 1.5f, align = Gravity.CENTER, bpad = dp(32)))
        step3.addView(primaryBtn("Done").apply { setOnClickListener { recreate() } })
        root.addView(step3)
    }

    // ── Profile manager ────────────────────────────────────────────────────────

    private fun showProfileManager() {
        val p = dp(20)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(p, p, p, p)
        }
        scroll.setOnApplyWindowInsetsListener { _, insets ->
            root.setPadding(p, p + sysBarTop(insets), p, p)
            insets
        }

        // ── Header ────────────────────────────────────────────────────────────
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, dp(2), dp(2))
        }
        header.addView(ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                rightMargin = dp(14)
            }
        })
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleCol.addView(tv(getString(R.string.app_name), 20f, cText, bold = true))
        titleCol.addView(tv("Monitor your homelab", 12f, cMuted))
        header.addView(titleCol)
        refreshBtn = TextView(this).apply {
            text = "↻"
            textSize = 22f
            setTextColor(cMuted)
            setPadding(dp(12), dp(8), dp(4), dp(8))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val hasWidgets = android.appwidget.AppWidgetManager.getInstance(this@MainActivity)
                    .getAppWidgetIds(android.content.ComponentName(this@MainActivity, UptimeWidget::class.java))
                    .isNotEmpty()
                if (!hasWidgets) {
                    statusText?.text = "No widgets created yet. Add one to your home screen first!"
                    statusText?.setTextColor(cMuted)
                    statusText?.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                if (Prefs.getProfiles(this@MainActivity).isNotEmpty()) {
                    text = "…"
                    isEnabled = false
                    progressBar?.visibility = View.VISIBLE
                    statusText?.visibility = View.GONE
                    UptimeWidget.triggerUpdate(this@MainActivity)
                }
            }
        }
        header.addView(refreshBtn)
        root.addView(header)

        // ── Instances section ─────────────────────────────────────────────────
        root.addView(sectionLabel("Instances"))

        profileCardContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(profileCardContainer)
        root.addView(spacer(4))

        // ── Add instance row ──────────────────────────────────────────────────
        root.addView(addInstanceRow())

        // ── Active widgets ────────────────────────────────────────────────────
        val widgetIds = android.appwidget.AppWidgetManager.getInstance(this)
            .getAppWidgetIds(android.content.ComponentName(this, UptimeWidget::class.java))
        data class WidgetEntry(val id: Int, val profileName: String, val bgColor: Int)
        val validWidgets = mutableListOf<WidgetEntry>()
        for (wid in widgetIds) {
            val profile = Prefs.getProfileIdForWidget(this, wid)
                ?.let { Prefs.getProfile(this, it) }
            if (profile == null) { Prefs.removeWidget(this, wid) }
            else validWidgets.add(WidgetEntry(wid, profile.name, Prefs.getWidgetBgColor(this, wid)))
        }
        if (validWidgets.isNotEmpty()) {
            root.addView(sectionLabel("Active Widgets"))
            validWidgets.forEach { root.addView(widgetRow(it.id, it.profileName, it.bgColor)) }
        }

        root.addView(spacer(20))

        // ── Status section ────────────────────────────────────────────────────
        root.addView(View(this).apply {          // divider
            setBackgroundColor(cDivider)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            )
        })
        root.addView(spacer(12))

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(progressBar)

        statusText = TextView(this).apply {
            textSize = 13f
            setPadding(dp(2), dp(8), dp(2), 0)
            visibility = View.GONE
        }
        root.addView(statusText)

        managerRoot = root
        scroll.addView(root)
        setContentView(scroll)
        refreshProfileList()
    }

    private fun checkBatteryOptimization() {
        val root = managerRoot ?: return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            batteryBanner?.let { (it.parent as? ViewGroup)?.removeView(it) }
            batteryBanner = null
        } else if (batteryBanner == null) {
            batteryBanner = buildBatteryBanner()
            root.addView(batteryBanner, 0)
        }
    }

    private fun buildBatteryBanner(): View = TextView(this).apply {
        text = "⚠  Background refresh may be delayed. Tap to fix battery settings."
        textSize = 13f
        setTextColor(0xFFFFB3B3.toInt())
        background = GradientDrawable().apply {
            setColor(0x22FF4444)
            cornerRadius = dp(10).toFloat()
            setStroke(dp(1), 0x55FF4444)
        }
        setPadding(dp(14), dp(12), dp(14), dp(12))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) }
        isClickable = true
        isFocusable = true
        setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        }
    }

    private fun widgetRow(widgetId: Int, profileName: String, bgColor: Int): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(cCard)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), cStroke)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
            isClickable = true
            isFocusable = true

            // Color swatch preview
            addView(View(this@MainActivity).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(bgColor or 0xFF000000.toInt())   // full opacity for the preview dot
                    setStroke(dp(1), cStroke)
                }
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply {
                    rightMargin = dp(12)
                }
            })

            val texts = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            texts.addView(tv(profileName, 15f, cText, bold = true))
            texts.addView(tv("Widget #$widgetId", 12f, cMuted))
            addView(texts)
            addView(tv("›", 22f, cMuted))

            setOnClickListener {
                startActivity(
                    Intent(this@MainActivity, WidgetConfigActivity::class.java)
                        .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                )
            }
        }

    private fun addInstanceRow(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = GradientDrawable().apply {
            setColor(cGreenBg)
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), cGreen)
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        isClickable = true
        isFocusable = true

        addView(TextView(this@MainActivity).apply {
            text = "＋"
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(cGreen)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = dp(10) }
        })
        addView(tv(getString(R.string.label_add_profile), 15f, cGreen, bold = true))

        setOnClickListener {
            startActivityForResult(
                Intent(this@MainActivity, ProfileEditorActivity::class.java), REQ_PROFILE
            )
        }
    }

    private fun refreshProfileList() {
        val container = profileCardContainer ?: return
        container.removeAllViews()
        val profiles = Prefs.getProfiles(this)
        if (profiles.isEmpty()) {
            container.addView(tv("No instances yet — add one below.", 13f, cMuted).apply {
                setPadding(dp(4), dp(4), dp(4), dp(8))
            })
            return
        }
        profiles.forEach { container.addView(profileCard(it)) }
    }

    private fun profileCard(profile: Profile): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = GradientDrawable().apply {
            setColor(cCard)
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), cStroke)
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) }
        isClickable = true
        isFocusable = true

        val texts = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        texts.addView(tv(profile.name, 15f, cText, bold = true))
        texts.addView(tv(profile.hostname, 12f, cMuted).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        addView(texts)
        addView(tv("›", 22f, cMuted))

        setOnClickListener {
            startActivityForResult(
                Intent(this@MainActivity, ProfileEditorActivity::class.java)
                    .putExtra(ProfileEditorActivity.EXTRA_PROFILE_ID, profile.id),
                REQ_PROFILE
            )
        }
        setOnLongClickListener {
            android.app.AlertDialog.Builder(this@MainActivity)
                .setTitle("Delete \"${profile.name}\"?")
                .setMessage("This will remove the instance and disconnect any widgets using it.")
                .setPositiveButton("Delete") { _, _ ->
                    Prefs.deleteProfile(this@MainActivity, profile.id)
                    val manager = android.appwidget.AppWidgetManager.getInstance(this@MainActivity)
                    manager.getAppWidgetIds(android.content.ComponentName(this@MainActivity, UptimeWidget::class.java))
                        .filter { Prefs.getProfileIdForWidget(this@MainActivity, it) == profile.id }
                        .forEach { Prefs.removeWidget(this@MainActivity, it) }
                    showProfileManager()
                }
                .setNegativeButton("Cancel", null)
                .show()
            true
        }
    }

    // ── Design helpers ─────────────────────────────────────────────────────────

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = GradientDrawable().apply {
            setColor(cCard)
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), cStroke)
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) }
    }

    private fun primaryBtn(text: String) = Button(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(0xFFFFFFFF.toInt())
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        isAllCaps = false
        elevation = 0f
        background = GradientDrawable().apply {
            setColor(cGreen)
            cornerRadius = dp(12).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(52)
        )
    }

    private fun styledEdit(hint: String) = EditText(this).apply {
        this.hint = hint
        setSingleLine()
        background = GradientDrawable().apply {
            setColor(cCardHi)
            cornerRadius = dp(8).toFloat()
            setStroke(dp(1), cStroke)
        }
        setPadding(dp(12), dp(10), dp(12), dp(10))
        setTextColor(cText)
        setHintTextColor(cMuted)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    /** Small all-caps section header. */
    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = text.uppercase()
        textSize = 11f
        letterSpacing = 0.12f
        setTextColor(cMuted)
        setPadding(dp(2), dp(16), dp(2), dp(8))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    /** Small muted label above a form field. */
    private fun label(text: String) = tv(text, 12f, cMuted, bpad = dp(4))

    /** General-purpose TextView factory to avoid boilerplate. */
    private fun tv(
        text: String, size: Float, color: Int,
        bold: Boolean = false,
        lineSpacing: Float = 0f,
        align: Int = Gravity.START,
        bpad: Int = 0
    ) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        if (lineSpacing > 0f) setLineSpacing(0f, lineSpacing)
        gravity = align
        if (bpad > 0) setPadding(paddingLeft, paddingTop, paddingRight, bpad)
    }

    private fun spacer(n: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(n))
    }

    private fun formatInterval(minutes: Int) = if (minutes == 1) "1 minute" else "$minutes minutes"

    private fun parseStatusPageUrl(input: String): Pair<String, String> {
        val trimmed = input.trim().trimEnd('/')
        val m = Regex("""^(https?://[^/]+)/(?:status|api/status-page)/([A-Za-z0-9][A-Za-z0-9_-]*)""")
            .find(trimmed)
        if (m != null) return m.groupValues[1] to m.groupValues[2]
        val hostM = Regex("""^(https?://[^/]+)""").find(trimmed)
        if (hostM != null) return hostM.groupValues[1] to "default"
        return trimmed to "default"
    }

    private fun reconstructUrl(hostname: String, slug: String): String {
        if (hostname.isEmpty()) return ""
        return "$hostname/status/${slug.ifEmpty { "default" }}"
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun sysBarTop(insets: WindowInsets) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()).top
        else @Suppress("DEPRECATION") insets.systemWindowInsetTop
}
