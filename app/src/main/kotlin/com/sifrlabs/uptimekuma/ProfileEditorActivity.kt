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

    // ── Theme ──────────────────────────────────────────────────────────────────

    private val isDark get() = (resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private val cBg      get() = if (isDark) 0xFF0C1A27.toInt() else 0xFFF2F6FA.toInt()
    private val cCard    get() = if (isDark) 0xFF142030.toInt() else 0xFFFFFFFF.toInt()
    private val cCardHi  get() = if (isDark) 0xFF1C2D3E.toInt() else 0xFFF0F5FA.toInt()
    private val cStroke  get() = if (isDark) 0xFF243548.toInt() else 0xFFD8E3EE.toInt()
    private val cText    get() = if (isDark) 0xFFE2EAF4.toInt() else 0xFF18273A.toInt()
    private val cMuted   get() = if (isDark) 0xFF6A8099.toInt() else 0xFF607080.toInt()
    private val cGreen   = 0xFF4CAF50.toInt()
    private val cRed     = 0xFFF44336.toInt()
    private val cRedBg   get() = if (isDark) 0xFF2A0E0E.toInt() else 0xFFFFF0F0.toInt()
    private val cRedStroke get() = if (isDark) 0xFF7A2020.toInt() else 0xFFEF9A9A.toInt()

    // ── Build UI ───────────────────────────────────────────────────────────────

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
            text = "‹"
            textSize = 28f
            setTextColor(cMuted)
            setPadding(0, 0, dp(10), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { finish() }
        })
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleCol.addView(tv(
            if (isEdit) "Edit Instance" else "New Instance",
            20f, cText, bold = true
        ))
        if (isEdit && existing != null) {
            titleCol.addView(tv(existing.name, 12f, cMuted))
        }
        headerRow.addView(titleCol)
        root.addView(headerRow)

        // ── Instance details ───────────────────────────────────────────────────
        root.addView(sectionLabel("Instance Details"))

        val details = card()

        details.addView(label("Name"))
        val nameEdit = styledEdit("Home Lab", existing?.name ?: "")
        details.addView(nameEdit)
        details.addView(spacer(12))

        details.addView(label("Hostname"))
        val hostnameEdit = styledEdit(getString(R.string.hint_hostname), existing?.hostname ?: "").apply {
            inputType = InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_CLASS_TEXT
        }
        details.addView(hostnameEdit)
        details.addView(spacer(12))

        // Auto-discover on by default for new profiles; on for existing ones with empty slug.
        val autoSlug = existing?.slug?.isEmpty() ?: true

        val slugHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        slugHeaderRow.addView(label("Status page slug").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val autoSlugSwitch = Switch(this).apply {
            text = "Auto"
            setTextColor(cMuted)
            textSize = 13f
            isChecked = autoSlug
            thumbTintList = android.content.res.ColorStateList.valueOf(if (autoSlug) cGreen else cMuted)
            trackTintList = android.content.res.ColorStateList.valueOf(
                if (autoSlug) (cGreen and 0x00FFFFFF or 0x44000000) else (cMuted and 0x00FFFFFF or 0x44000000))
        }
        slugHeaderRow.addView(autoSlugSwitch)
        details.addView(slugHeaderRow)

        val slugEdit = styledEdit(getString(R.string.hint_slug), existing?.slug ?: "").apply {
            isEnabled = !autoSlug
            alpha = if (autoSlug) 0.35f else 1f
        }
        details.addView(slugEdit)

        autoSlugSwitch.setOnCheckedChangeListener { _, checked ->
            slugEdit.isEnabled = !checked
            slugEdit.alpha = if (checked) 0.35f else 1f
            val green = cGreen
            val muted = cMuted
            autoSlugSwitch.thumbTintList = android.content.res.ColorStateList.valueOf(if (checked) green else muted)
            autoSlugSwitch.trackTintList = android.content.res.ColorStateList.valueOf(
                if (checked) (green and 0x00FFFFFF or 0x44000000) else (muted and 0x00FFFFFF or 0x44000000))
        }

        root.addView(details)

        // ── Refresh interval ───────────────────────────────────────────────────
        root.addView(sectionLabel("Schedule"))

        val schedCard = card()
        schedCard.addView(label("Refresh interval"))
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@ProfileEditorActivity,
                android.R.layout.simple_spinner_dropdown_item,
                intervalOptions.map { if (it == 1) "1 minute" else "$it minutes" }
            )
            val idx = intervalOptions.indexOf(existing?.intervalMinutes ?: 5).coerceAtLeast(0)
            setSelection(idx)
        }
        schedCard.addView(spinner)
        schedCard.addView(spacer(14))

        val groupMonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        groupMonRow.addView(tv("Show group monitors", 14f, cText).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val groupMonSwitch = Switch(this).apply {
            isChecked = existing?.showGroupMonitors ?: false
            thumbTintList = android.content.res.ColorStateList.valueOf(
                if (existing?.showGroupMonitors == true) cGreen else cMuted)
            trackTintList = android.content.res.ColorStateList.valueOf(
                if (existing?.showGroupMonitors == true)
                    (cGreen and 0x00FFFFFF or 0x44000000)
                else
                    (cMuted and 0x00FFFFFF or 0x44000000))
        }
        groupMonSwitch.setOnCheckedChangeListener { _, checked ->
            groupMonSwitch.thumbTintList = android.content.res.ColorStateList.valueOf(if (checked) cGreen else cMuted)
            groupMonSwitch.trackTintList = android.content.res.ColorStateList.valueOf(
                if (checked) (cGreen and 0x00FFFFFF or 0x44000000) else (cMuted and 0x00FFFFFF or 0x44000000))
        }
        groupMonRow.addView(groupMonSwitch)
        schedCard.addView(groupMonRow)
        root.addView(schedCard)

        // ── Authentication ─────────────────────────────────────────────────────
        root.addView(sectionLabel("Authentication"))

        val authCard = card()
        val authCheckbox = CheckBox(this).apply {
            text = getString(R.string.label_basic_auth)
            setTextColor(cText)
            textSize = 14f
            isChecked = existing?.authEnabled ?: false
        }
        authCard.addView(authCheckbox)

        val credSpacer = spacer(12).apply {
            visibility = if (existing?.authEnabled == true) View.VISIBLE else View.GONE
        }
        val usernameEdit = styledEdit("Username", existing?.username ?: "").apply {
            visibility = if (existing?.authEnabled == true) View.VISIBLE else View.GONE
        }
        val fieldSpacer = spacer(12).apply {
            visibility = if (existing?.authEnabled == true) View.VISIBLE else View.GONE
        }
        val passwordEdit = styledEdit("Password", existing?.password ?: "").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            visibility = if (existing?.authEnabled == true) View.VISIBLE else View.GONE
        }
        authCard.addView(credSpacer)
        authCard.addView(usernameEdit)
        authCard.addView(fieldSpacer)
        authCard.addView(passwordEdit)
        root.addView(authCard)

        authCheckbox.setOnCheckedChangeListener { _, checked ->
            val v = if (checked) View.VISIBLE else View.GONE
            credSpacer.visibility = v
            usernameEdit.visibility = v
            fieldSpacer.visibility = v
            passwordEdit.visibility = v
        }

        // ── Actions ────────────────────────────────────────────────────────────
        root.addView(spacer(16))
        root.addView(primaryBtn(if (isEdit) "Save Changes" else "Add Instance").apply {
            setOnClickListener {
                save(existingId, nameEdit, hostnameEdit, autoSlugSwitch, slugEdit,
                    spinner, groupMonSwitch, authCheckbox, usernameEdit, passwordEdit)
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

    // ── Logic ──────────────────────────────────────────────────────────────────

    private fun save(
        existingId: String?,
        nameEdit: EditText, hostnameEdit: EditText,
        autoSlugCheck: Switch, slugEdit: EditText,
        spinner: Spinner, groupMonSwitch: Switch,
        authCheckbox: CheckBox,
        usernameEdit: EditText, passwordEdit: EditText
    ) {
        val name     = nameEdit.text.toString().trim()
        val hostname = hostnameEdit.text.toString().trimEnd('/')
        // Empty slug is the sentinel for auto-discover; otherwise use the entered value.
        val slug     = if (autoSlugCheck.isChecked) "" else slugEdit.text.toString().trim().ifEmpty { "default" }
        if (name.isEmpty() || hostname.isEmpty()) {
            Toast.makeText(this, "Name and hostname are required", Toast.LENGTH_SHORT).show()
            return
        }
        val profile = Profile(
            id                = existingId ?: Profile.newId(),
            name              = name,
            hostname          = hostname,
            slug              = slug,
            intervalMinutes   = intervalOptions[spinner.selectedItemPosition],
            authEnabled       = authCheckbox.isChecked,
            username          = usernameEdit.text.toString(),
            password          = passwordEdit.text.toString(),
            showGroupMonitors = groupMonSwitch.isChecked
        )
        Prefs.upsertProfile(this, profile)

        // Immediately refresh any widgets that use this profile
        val widgetIds = android.appwidget.AppWidgetManager.getInstance(this)
            .getAppWidgetIds(android.content.ComponentName(this, UptimeWidget::class.java))
            .filter { Prefs.getProfileIdForWidget(this, it) == profile.id }
            .toIntArray()
        if (widgetIds.isNotEmpty()) UptimeWidget.triggerUpdate(this, widgetIds)

        setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT_PROFILE_ID, profile.id))
        finish()
    }

    private fun deleteProfile(profileId: String) {
        Prefs.deleteProfile(this, profileId)
        val manager = android.appwidget.AppWidgetManager.getInstance(this)
        val ids = manager.getAppWidgetIds(
            android.content.ComponentName(this, UptimeWidget::class.java)
        )
        ids.filter { Prefs.getProfileIdForWidget(this, it) == profileId }
           .forEach { Prefs.removeWidget(this, it) }
        setResult(RESULT_OK)
        finish()
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
        ).apply { bottomMargin = dp(4) }
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

    private fun destructiveBtn(text: String) = Button(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(cRed)
        isAllCaps = false
        elevation = 0f
        background = GradientDrawable().apply {
            setColor(cRedBg)
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), cRedStroke)
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(52)
        )
    }

    private fun styledEdit(hint: String, value: String = "") = EditText(this).apply {
        this.hint = hint
        setText(value)
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

    private fun label(text: String) = tv(text, 12f, cMuted, bpad = dp(4))

    private fun tv(
        text: String, size: Float, color: Int,
        bold: Boolean = false, bpad: Int = 0
    ) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
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
