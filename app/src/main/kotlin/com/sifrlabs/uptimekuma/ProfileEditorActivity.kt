package com.sifrlabs.uptimekuma

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.*

class ProfileEditorActivity : Activity() {

    companion object {
        const val EXTRA_PROFILE_ID        = "profile_id"
        const val EXTRA_RESULT_PROFILE_ID = "result_profile_id"
    }

    private val intervalOptions = listOf(1, 5, 10, 15, 30, 60)

    private val isDark get() = (resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private val cBg        get() = if (isDark) 0xFF0C1A27.toInt() else 0xFFF2F6FA.toInt()
    private val cCard      get() = if (isDark) 0xFF142030.toInt() else 0xFFFFFFFF.toInt()
    private val cCardHi    get() = if (isDark) 0xFF1C2D3E.toInt() else 0xFFF0F5FA.toInt()
    private val cStroke    get() = if (isDark) 0xFF243548.toInt() else 0xFFD8E3EE.toInt()
    private val cText      get() = if (isDark) 0xFFE2EAF4.toInt() else 0xFF18273A.toInt()
    private val cMuted     get() = if (isDark) 0xFF6A8099.toInt() else 0xFF607080.toInt()
    private val cGreen     = 0xFF4CAF50.toInt()
    private val cRed       = 0xFFF44336.toInt()
    private val cRedBg     get() = if (isDark) 0xFF2A0E0E.toInt() else 0xFFFFF0F0.toInt()
    private val cRedStroke get() = if (isDark) 0xFF7A2020.toInt() else 0xFFEF9A9A.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(cBg)

        val existingId = intent.getStringExtra(EXTRA_PROFILE_ID)
        val existing   = existingId?.let { Prefs.getProfile(this, it) }
        val isEdit     = existing != null

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

        // ── Back + title ───────────────────────────────────────────────────────
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        headerRow.addView(TextView(this).apply {
            text = "‹"; textSize = 28f; setTextColor(cMuted)
            setPadding(0, 0, dp(10), 0)
            isClickable = true; isFocusable = true
            setOnClickListener { finish() }
        })
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleCol.addView(tv(if (isEdit) "Edit Instance" else "New Instance", 20f, cText, bold = true))
        if (isEdit && existing != null) titleCol.addView(tv(existing.name, 12f, cMuted))
        headerRow.addView(titleCol)
        root.addView(headerRow)

        // ── Instance Details section header with Advanced switch ───────────────
        var showAdvanced = existing?.authEnabled == true

        val detailsHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        detailsHeaderRow.addView(sectionLabel("Instance Details").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val advSwitch = Switch(this).apply {
            text = "Advanced"; textSize = 12f; setTextColor(cMuted)
            isChecked = showAdvanced
            thumbTintList = android.content.res.ColorStateList.valueOf(if (showAdvanced) cGreen else cMuted)
            trackTintList = android.content.res.ColorStateList.valueOf(
                if (showAdvanced) (cGreen and 0x00FFFFFF or 0x44000000) else (cMuted and 0x00FFFFFF or 0x44000000))
        }
        detailsHeaderRow.addView(advSwitch)
        root.addView(detailsHeaderRow)

        // ── Details card ───────────────────────────────────────────────────────
        val detailsCard = card()

        detailsCard.addView(label("Name"))
        val nameEdit = styledEdit("Home Lab", existing?.name ?: "")
        detailsCard.addView(nameEdit)

        // Simple: URL field
        val urlSpacer = spacer(12); val urlLabel = label("Status page URL")
        val urlEdit = styledEdit(
            "https://uptime.example.com/status/my-slug",
            if (existing != null) reconstructUrl(existing.hostname, existing.slug) else ""
        ).apply { inputType = InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_CLASS_TEXT }
        detailsCard.addView(urlSpacer); detailsCard.addView(urlLabel); detailsCard.addView(urlEdit)

        // Advanced: hostname + slug
        val hostSpacer = spacer(12); val hostLabel = label("Hostname")
        val hostnameEdit = styledEdit(getString(R.string.hint_hostname), existing?.hostname ?: "").apply {
            inputType = InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_CLASS_TEXT
        }
        val slugSpacer = spacer(8); val slugLabel = label("Slug")
        val slugEdit = styledEdit("default", existing?.slug ?: "")
        detailsCard.addView(hostSpacer); detailsCard.addView(hostLabel); detailsCard.addView(hostnameEdit)
        detailsCard.addView(slugSpacer); detailsCard.addView(slugLabel); detailsCard.addView(slugEdit)

        root.addView(detailsCard)

        // ── Advanced section (separate card, hidden by default) ────────────────
        val advSectionLabel = sectionLabel("Advanced")
        val advCard = card()

        // Auth
        val authCheckbox = CheckBox(this).apply {
            text = getString(R.string.label_basic_auth); setTextColor(cText); textSize = 14f
            isChecked = existing?.authEnabled ?: false
        }
        val credSpacer = spacer(8)
        val usernameEdit = styledEdit("Username", existing?.username ?: "")
        val pwSpacer = spacer(8)
        val passwordEdit = styledEdit("Password", existing?.password ?: "").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        advCard.addView(authCheckbox)
        advCard.addView(credSpacer); advCard.addView(usernameEdit)
        advCard.addView(pwSpacer);   advCard.addView(passwordEdit)

        // Refresh interval — label + current value on one line, seekbar below
        advCard.addView(spacer(14))
        val intervalInitIdx    = intervalOptions.indexOf(existing?.intervalMinutes ?: 5).coerceAtLeast(0)
        val intervalValueLabel = tv(formatInterval(intervalOptions[intervalInitIdx]), 12f, cText)
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
        val seekBar = SeekBar(this).apply {
            max = intervalOptions.size - 1; progress = intervalInitIdx
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
        advCard.addView(seekBar)

        root.addView(advSectionLabel)
        root.addView(advCard)

        // ── Visibility logic ───────────────────────────────────────────────────
        fun applyMode() {
            val adv = showAdvanced
            // Details card: simple vs advanced fields
            urlSpacer.visibility    = if (!adv) View.VISIBLE else View.GONE
            urlLabel.visibility     = if (!adv) View.VISIBLE else View.GONE
            urlEdit.visibility      = if (!adv) View.VISIBLE else View.GONE
            hostSpacer.visibility   = if (adv) View.VISIBLE else View.GONE
            hostLabel.visibility    = if (adv) View.VISIBLE else View.GONE
            hostnameEdit.visibility = if (adv) View.VISIBLE else View.GONE
            slugSpacer.visibility   = if (adv) View.VISIBLE else View.GONE
            slugLabel.visibility    = if (adv) View.VISIBLE else View.GONE
            slugEdit.visibility     = if (adv) View.VISIBLE else View.GONE
            // Advanced section
            advSectionLabel.visibility = if (adv) View.VISIBLE else View.GONE
            advCard.visibility         = if (adv) View.VISIBLE else View.GONE
            // Auth credentials
            val showCreds = adv && authCheckbox.isChecked
            credSpacer.visibility   = if (showCreds) View.VISIBLE else View.GONE
            usernameEdit.visibility = if (showCreds) View.VISIBLE else View.GONE
            pwSpacer.visibility     = if (showCreds) View.VISIBLE else View.GONE
            passwordEdit.visibility = if (showCreds) View.VISIBLE else View.GONE
            // Switch tint
            advSwitch.thumbTintList = android.content.res.ColorStateList.valueOf(if (adv) cGreen else cMuted)
            advSwitch.trackTintList = android.content.res.ColorStateList.valueOf(
                if (adv) (cGreen and 0x00FFFFFF or 0x44000000) else (cMuted and 0x00FFFFFF or 0x44000000))
        }
        applyMode()

        advSwitch.setOnCheckedChangeListener { _, checked ->
            showAdvanced = checked
            if (checked) {
                val (h, s) = parseStatusPageUrl(urlEdit.text.toString())
                hostnameEdit.setText(h); slugEdit.setText(s)
            } else {
                urlEdit.setText(reconstructUrl(
                    hostnameEdit.text.toString().trim(),
                    slugEdit.text.toString().trim()))
            }
            applyMode()
        }
        authCheckbox.setOnCheckedChangeListener { _, _ -> applyMode() }

        // ── Actions ────────────────────────────────────────────────────────────
        root.addView(spacer(16))
        root.addView(primaryBtn(if (isEdit) "Save Changes" else "Add Instance").apply {
            setOnClickListener {
                save(existingId, nameEdit, urlEdit, hostnameEdit, slugEdit, { showAdvanced },
                    seekBar, authCheckbox, usernameEdit, passwordEdit)
            }
        })
        if (isEdit) {
            root.addView(spacer(12))
            root.addView(destructiveBtn("Delete Instance").apply {
                setOnClickListener { deleteProfile(existingId!!) }
            })
        }

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun save(
        existingId: String?,
        nameEdit: EditText,
        urlEdit: EditText, hostnameEdit: EditText, slugEdit: EditText,
        isAdvanced: () -> Boolean,
        seekBar: SeekBar,
        authCheckbox: CheckBox, usernameEdit: EditText, passwordEdit: EditText
    ) {
        val name = nameEdit.text.toString().trim()
        val (hostname, slug) = if (isAdvanced()) {
            hostnameEdit.text.toString().trimEnd('/') to slugEdit.text.toString().trim().ifEmpty { "default" }
        } else {
            parseStatusPageUrl(urlEdit.text.toString())
        }
        if (name.isEmpty() || hostname.isEmpty()) {
            Toast.makeText(this, "Name and URL are required", Toast.LENGTH_SHORT).show()
            return
        }
        val profile = Profile(
            id              = existingId ?: Profile.newId(),
            name            = name,
            hostname        = hostname,
            slug            = slug,
            intervalMinutes = intervalOptions[seekBar.progress],
            authEnabled     = isAdvanced() && authCheckbox.isChecked,
            username        = usernameEdit.text.toString(),
            password        = passwordEdit.text.toString()
        )
        Prefs.upsertProfile(this, profile)

        val widgetIds = android.appwidget.AppWidgetManager.getInstance(this)
            .getAppWidgetIds(android.content.ComponentName(this, UptimeWidget::class.java))
            .filter { Prefs.getProfileIdForWidget(this, it) == profile.id }
            .toIntArray()
        if (widgetIds.isNotEmpty()) UptimeWidget.triggerUpdate(this, widgetIds)

        setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT_PROFILE_ID, profile.id))
        finish()
    }

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

    private fun formatInterval(minutes: Int) = if (minutes == 1) "1 minute" else "$minutes minutes"

    private fun deleteProfile(profileId: String) {
        Prefs.deleteProfile(this, profileId)
        val manager = android.appwidget.AppWidgetManager.getInstance(this)
        val ids = manager.getAppWidgetIds(android.content.ComponentName(this, UptimeWidget::class.java))
        ids.filter { Prefs.getProfileIdForWidget(this, it) == profileId }
           .forEach { Prefs.removeWidget(this, it) }
        setResult(RESULT_OK)
        finish()
    }

    // ── Design helpers ─────────────────────────────────────────────────────────

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = GradientDrawable().apply {
            setColor(cCard); cornerRadius = dp(12).toFloat(); setStroke(dp(1), cStroke)
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(4) }
    }

    private fun primaryBtn(text: String) = Button(this).apply {
        this.text = text; textSize = 15f
        setTextColor(0xFFFFFFFF.toInt())
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        isAllCaps = false; elevation = 0f
        background = GradientDrawable().apply { setColor(cGreen); cornerRadius = dp(12).toFloat() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52))
    }

    private fun destructiveBtn(text: String) = Button(this).apply {
        this.text = text; textSize = 15f; setTextColor(cRed); isAllCaps = false; elevation = 0f
        background = GradientDrawable().apply {
            setColor(cRedBg); cornerRadius = dp(12).toFloat(); setStroke(dp(1), cRedStroke)
        }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52))
    }

    private fun styledEdit(hint: String, value: String = "") = EditText(this).apply {
        this.hint = hint; setText(value); setSingleLine()
        background = GradientDrawable().apply {
            setColor(cCardHi); cornerRadius = dp(8).toFloat(); setStroke(dp(1), cStroke)
        }
        setPadding(dp(12), dp(10), dp(12), dp(10))
        setTextColor(cText); setHintTextColor(cMuted)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = text.uppercase(); textSize = 11f; letterSpacing = 0.12f
        setTextColor(cMuted)
        setPadding(dp(2), dp(16), dp(2), dp(8))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    private fun label(text: String) = tv(text, 12f, cMuted, bpad = dp(4))

    private fun tv(text: String, size: Float, color: Int, bold: Boolean = false, bpad: Int = 0) =
        TextView(this).apply {
            this.text = text; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            if (bpad > 0) setPadding(paddingLeft, paddingTop, paddingRight, bpad)
        }

    private fun spacer(n: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(n))
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun sysBarTop(insets: WindowInsets) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()).top
        else @Suppress("DEPRECATION") insets.systemWindowInsetTop
}
