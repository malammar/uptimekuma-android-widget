package com.sifrlabs.uptimekuma

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.*
import android.widget.FrameLayout

class WidgetConfigActivity : Activity() {

    companion object {
        private const val REQ_ADD_PROFILE = 1001
    }

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var selectedProfileId: String? = null
    private var isExistingWidget = false
    private lateinit var root: LinearLayout

    // Appearance state — persists across buildUi() calls
    private val darkBgPresets = listOf(
        0x1A1A2E to "Navy", 0x000000 to "Black", 0x1C1C2E to "Slate",
        0x0A1628 to "Blue", 0x1C1C1C to "Charcoal", 0x0D1F12 to "Forest",
    )
    private val lightBgPresets = listOf(
        0xFFFFFF to "White", 0xE3F2FD to "Blue",  0xEDE7F6 to "Purple",
        0xFCE4EC to "Rose",  0xFFF8E1 to "Amber", 0xE0F2F1 to "Teal",
    )
    private val darkFontPresets  = listOf(0xFFFFFF, 0xB0BEC5, 0xE0E0E0, 0x212121, 0xFFB300, 0x4DB6AC)
    private val lightFontPresets = listOf(0x1A1A2E, 0x212121, 0x607080, 0x000000, 0x8B0000, 0x1B5E20)

    private val effectiveDarkMode get() = when (widgetTheme) { 0 -> true; 1 -> false; else -> isDark }
    private val bgPresets         get() = if (effectiveDarkMode) darkBgPresets else lightBgPresets
    private val fontPresets       get() = if (effectiveDarkMode) darkFontPresets else lightFontPresets

    private var selectedBgRgb: Int     = 0   // 0 = auto (first preset of current theme)
    private var bgOpacityPct: Int      = 80
    private var widgetTheme: Int       = 2    // 0=Dark, 1=Light, 2=System
    private var selectedHeaderRgb: Int = 0   // 0 = auto
    private var selectedFooterRgb: Int = 0   // 0 = auto
    private var selectedFontRgb: Int   = 0   // 0 = auto
    private var textScalePct: Int      = 100  // 100 = 1.0x
    private var showGroupMonitors: Boolean = false

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
    private val cGreenBg get() = if (isDark) 0xFF0C2218.toInt() else 0xFFE8F5E9.toInt()
    private val cSelected get() = if (isDark) 0xFF1A3A28.toInt() else 0xFFE8F5E9.toInt()
    private val cSelectedStroke get() = if (isDark) 0xFF2E6644.toInt() else 0xFF81C784.toInt()

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        window.decorView.setBackgroundColor(cBg)

        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        // Pre-populate from existing widget settings (edit mode)
        selectedProfileId = Prefs.getProfileIdForWidget(this, appWidgetId)
        isExistingWidget  = selectedProfileId != null
        widgetTheme       = Prefs.getWidgetTheme(this, appWidgetId)
        val storedColor   = Prefs.getWidgetBgColor(this, appWidgetId)
        val storedRgb     = storedColor and 0x00FFFFFF
        if (bgPresets.any { it.first == storedRgb }) selectedBgRgb = storedRgb
        bgOpacityPct = (storedColor ushr 24) * 100 / 255
        selectedHeaderRgb = Prefs.getWidgetHeaderBg(this, appWidgetId).let { if (it == 0) 0 else it and 0x00FFFFFF }
        selectedFooterRgb = Prefs.getWidgetFooterBg(this, appWidgetId).let { if (it == 0) 0 else it and 0x00FFFFFF }
        selectedFontRgb   = Prefs.getWidgetFontColor(this, appWidgetId).let { if (it == 0) 0 else it and 0x00FFFFFF }
        textScalePct      = Prefs.getWidgetTextScalePct(this, appWidgetId)
        showGroupMonitors = Prefs.getWidgetShowGroupMonitors(this, appWidgetId)

        val p = dp(20)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(p, p, p, p)
        }
        scroll.setOnApplyWindowInsetsListener { _, insets ->
            root.setPadding(p, p + sysBarTop(insets), p, p)
            insets
        }
        scroll.addView(root)
        setContentView(scroll)
        buildUi()
    }

    private fun buildUi() {
        root.removeAllViews()
        val profiles = Prefs.getProfiles(this)

        // ── Header ─────────────────────────────────────────────────────────────
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                rightMargin = dp(12)
            }
        })
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleCol.addView(tv(if (isExistingWidget) "Edit Widget" else "Add Widget", 20f, cText, bold = true))
        titleCol.addView(tv(if (isExistingWidget) "Change instance or appearance" else "Choose an instance to display", 12f, cMuted))
        header.addView(titleCol)
        root.addView(header)

        if (profiles.isEmpty()) {
            // ── Empty state ────────────────────────────────────────────────────
            root.addView(spacer(32))

            val emptyCard = card()
            emptyCard.addView(tv("📭", 36f, cText, align = Gravity.CENTER, bpad = dp(12)))
            emptyCard.addView(tv("No instances configured yet.", 15f, cText,
                bold = true, align = Gravity.CENTER, bpad = dp(6)))
            emptyCard.addView(tv("Open the UptimeKuma Widget app first to add an Uptime Kuma instance.",
                13f, cMuted, align = Gravity.CENTER, lineSpacing = 1.4f, bpad = dp(20)))
            emptyCard.addView(primaryBtn("Open Settings").apply {
                setOnClickListener {
                    startActivity(Intent(this@WidgetConfigActivity, MainActivity::class.java))
                }
            })
            root.addView(emptyCard)
            return
        }

        // ── Instance list ──────────────────────────────────────────────────────
        root.addView(sectionLabel("Select Instance"))

        if (selectedProfileId == null) selectedProfileId = profiles[0].id

        profiles.forEach { profile ->
            val isSelected = profile.id == selectedProfileId
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background = GradientDrawable().apply {
                    setColor(if (isSelected) cSelected else cCard)
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(if (isSelected) 2 else 1),
                              if (isSelected) cSelectedStroke else cStroke)
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
                isClickable = true
                isFocusable = true
            }

            val texts = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            texts.addView(tv(profile.name, 15f, cText, bold = true))
            texts.addView(tv(profile.hostname, 12f, cMuted).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            row.addView(texts)

            // Checkmark for selected
            row.addView(tv(if (isSelected) "✓" else "", 16f, cGreen).apply {
                layoutParams = LinearLayout.LayoutParams(dp(24), LinearLayout.LayoutParams.WRAP_CONTENT)
                gravity = Gravity.CENTER
            })

            row.setOnClickListener {
                selectedProfileId = profile.id
                buildUi()
            }
            root.addView(row)
        }

        // ── Add instance ───────────────────────────────────────────────────────
        root.addView(spacer(4))
        root.addView(addInstanceRow())

        // ── Appearance ─────────────────────────────────────────────────────────
        root.addView(sectionLabel("Appearance"))
        val appearCard = card()

        // Theme selector
        val themeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        }
        themeRow.addView(tv("Theme", 14f, cText).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val themeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@WidgetConfigActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("System", "Dark", "Light")
            )
            // spinner pos: 0=System, 1=Dark, 2=Light  →  widgetTheme: 2, 0, 1
            setSelection(when (widgetTheme) { 0 -> 1; 1 -> 2; else -> 0 })
        }
        themeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: android.view.View?, pos: Int, id: Long) {
                val newTheme = when (pos) { 1 -> 0; 2 -> 1; else -> 2 }
                if (newTheme != widgetTheme) {
                    widgetTheme = newTheme
                    if (selectedBgRgb != 0 && bgPresets.none { it.first == selectedBgRgb }) selectedBgRgb = 0
                    buildUi()
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }
        themeRow.addView(themeSpinner)
        appearCard.addView(themeRow)

        // Background color
        appearCard.addView(tv("Background color", 12f, cMuted).apply { setPadding(0, 0, 0, dp(10)) })
        appearCard.addView(swatchRowWithAuto(bgPresets.map { it.first }, selectedBgRgb) { rgb ->
            // First preset == auto; normalize so A stays highlighted
            selectedBgRgb = if (rgb == bgPresets[0].first) 0 else rgb
            buildUi()
        }.apply {
            (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(16)
        })

        // Opacity slider
        appearCard.addView(tv("Background opacity", 12f, cMuted).apply { setPadding(0, 0, 0, dp(8)) })
        val opacityRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        }
        val opacityLabel = tv("$bgOpacityPct%", 13f, cText).apply {
            layoutParams = LinearLayout.LayoutParams(dp(40), LinearLayout.LayoutParams.WRAP_CONTENT)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        val seekBar = SeekBar(this).apply {
            max = 100
            progress = bgOpacityPct
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(10)
            }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    bgOpacityPct = p; opacityLabel.text = "$p%"
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        opacityRow.addView(seekBar)
        opacityRow.addView(opacityLabel)
        appearCard.addView(opacityRow)

        // Header color
        appearCard.addView(tv("Header background color", 12f, cMuted).apply { setPadding(0, 0, 0, dp(10)) })
        appearCard.addView(swatchRowWithAuto(bgPresets.map { it.first }, selectedHeaderRgb) {
            selectedHeaderRgb = it; buildUi()
        }.apply {
            (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(16)
        })

        // Footer color
        appearCard.addView(tv("Footer background color", 12f, cMuted).apply { setPadding(0, 0, 0, dp(10)) })
        appearCard.addView(swatchRowWithAuto(bgPresets.map { it.first }, selectedFooterRgb) {
            selectedFooterRgb = it; buildUi()
        }.apply {
            (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(16)
        })

        // Font color
        appearCard.addView(tv("Font color", 12f, cMuted).apply { setPadding(0, 0, 0, dp(10)) })
        appearCard.addView(swatchRowWithAuto(fontPresets, selectedFontRgb) {
            selectedFontRgb = it; buildUi()
        }.apply {
            (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(16)
        })

        // Text size
        appearCard.addView(tv("Text size", 12f, cMuted).apply { setPadding(0, 0, 0, dp(10)) })
        val sizeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        listOf(85 to "S", 100 to "M", 120 to "L", 140 to "XL").forEach { (pct, label) ->
            val sel = textScalePct == pct
            sizeRow.addView(Button(this).apply {
                text = label
                isAllCaps = false
                elevation = 0f
                textSize = 13f
                setTextColor(if (sel) 0xFFFFFFFF.toInt() else cMuted)
                background = GradientDrawable().apply {
                    setColor(if (sel) cGreen else cCard)
                    cornerRadius = dp(8).toFloat()
                    setStroke(dp(1), if (sel) cGreen else cStroke)
                }
                layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply { rightMargin = dp(6) }
                setOnClickListener { textScalePct = pct; buildUi() }
            })
        }
        appearCard.addView(sizeRow)

        // Show group monitors
        appearCard.addView(spacer(14))
        val gmRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        gmRow.addView(tv("Show group monitors", 14f, cText).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val gmSwitch = Switch(this).apply {
            isChecked = showGroupMonitors
            thumbTintList = android.content.res.ColorStateList.valueOf(if (showGroupMonitors) cGreen else cMuted)
            trackTintList = android.content.res.ColorStateList.valueOf(
                if (showGroupMonitors) (cGreen and 0x00FFFFFF or 0x44000000) else (cMuted and 0x00FFFFFF or 0x44000000))
        }
        gmSwitch.setOnCheckedChangeListener { _, checked ->
            showGroupMonitors = checked
            gmSwitch.thumbTintList = android.content.res.ColorStateList.valueOf(if (checked) cGreen else cMuted)
            gmSwitch.trackTintList = android.content.res.ColorStateList.valueOf(
                if (checked) (cGreen and 0x00FFFFFF or 0x44000000) else (cMuted and 0x00FFFFFF or 0x44000000))
        }
        gmRow.addView(gmSwitch)
        appearCard.addView(gmRow)
        root.addView(appearCard)

        root.addView(spacer(20))

        // ── Confirm ────────────────────────────────────────────────────────────
        root.addView(View(this).apply {
            setBackgroundColor(if (isDark) 0xFF192A38.toInt() else 0xFFE2ECF5.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            ).apply { bottomMargin = dp(16) }
        })
        root.addView(primaryBtn(if (isExistingWidget) "Save Changes" else "Add Widget").apply {
            setOnClickListener { confirm() }
        })
    }

    private fun addInstanceRow(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(12))
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
        addView(TextView(this@WidgetConfigActivity).apply {
            text = "＋"
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(cGreen)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = dp(10) }
        })
        addView(tv(getString(R.string.label_add_profile), 14f, cGreen, bold = true))
        setOnClickListener {
            startActivityForResult(
                Intent(this@WidgetConfigActivity, ProfileEditorActivity::class.java),
                REQ_ADD_PROFILE
            )
        }
    }

    private fun confirm() {
        val profileId = selectedProfileId ?: return
        Prefs.setProfileIdForWidget(this, appWidgetId, profileId)
        val alpha = bgOpacityPct * 255 / 100
        val bgRgb = if (selectedBgRgb == 0) bgPresets[0].first else selectedBgRgb
        Prefs.setWidgetBgColor(this, appWidgetId, (alpha shl 24) or bgRgb)
        Prefs.setWidgetTheme(this, appWidgetId, widgetTheme)
        Prefs.setWidgetHeaderBg(this, appWidgetId, if (selectedHeaderRgb == 0) 0 else 0xFF000000.toInt() or selectedHeaderRgb)
        Prefs.setWidgetFooterBg(this, appWidgetId, if (selectedFooterRgb == 0) 0 else 0xFF000000.toInt() or selectedFooterRgb)
        Prefs.setWidgetFontColor(this, appWidgetId, if (selectedFontRgb == 0) 0 else 0xFF000000.toInt() or selectedFontRgb)
        Prefs.setWidgetTextScalePct(this, appWidgetId, textScalePct)
        Prefs.setWidgetShowGroupMonitors(this, appWidgetId, showGroupMonitors)
        UptimeWidget.scheduleAlarm(this)
        UptimeWidget.triggerUpdate(this, intArrayOf(appWidgetId))
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_ADD_PROFILE && resultCode == RESULT_OK) {
            val newId = data?.getStringExtra(ProfileEditorActivity.EXTRA_RESULT_PROFILE_ID)
            if (newId != null) selectedProfileId = newId
            buildUi()
        }
    }

    // ── Design helpers ─────────────────────────────────────────────────────────

    private fun swatchRowWithAuto(
        rgbList: List<Int>,
        selectedRgb: Int,
        onSelect: (Int) -> Unit
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )

        // Auto swatch
        val autoSel = selectedRgb == 0
        addView(FrameLayout(this@WidgetConfigActivity).apply {
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { rightMargin = dp(8) }
            isClickable = true; isFocusable = true
            addView(android.view.View(this@WidgetConfigActivity).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(cCardHi)
                    setStroke(dp(if (autoSel) 2 else 1), if (autoSel) cGreen else cStroke)
                }
                layoutParams = android.widget.FrameLayout.LayoutParams(dp(32), dp(32))
            })
            addView(TextView(this@WidgetConfigActivity).apply {
                text = "A"
                textSize = 10f
                setTextColor(if (autoSel) cGreen else cMuted)
                gravity = Gravity.CENTER
                layoutParams = android.widget.FrameLayout.LayoutParams(dp(32), dp(32))
            })
            setOnClickListener { onSelect(0) }
        })

        // Color swatches
        rgbList.forEach { rgb ->
            val isSel = rgb == selectedRgb
            if (isSel) {
                addView(FrameLayout(this@WidgetConfigActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { rightMargin = dp(8) }
                    isClickable = true; isFocusable = true
                    addView(android.view.View(this@WidgetConfigActivity).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(0xFF000000.toInt() or rgb)
                            setStroke(dp(2), cGreen)
                        }
                        layoutParams = android.widget.FrameLayout.LayoutParams(dp(32), dp(32))
                    })
                    addView(TextView(this@WidgetConfigActivity).apply {
                        text = "✓"
                        textSize = 11f
                        setTextColor(contrastColor(rgb))
                        gravity = Gravity.CENTER
                        layoutParams = android.widget.FrameLayout.LayoutParams(dp(32), dp(32))
                    })
                    setOnClickListener { onSelect(rgb) }
                })
            } else {
                addView(android.view.View(this@WidgetConfigActivity).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(0xFF000000.toInt() or rgb)
                        setStroke(dp(1), cStroke)
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { rightMargin = dp(8) }
                    isClickable = true; isFocusable = true
                    setOnClickListener { onSelect(rgb) }
                })
            }
        }
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = GradientDrawable().apply {
            setColor(cCard)
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), cStroke)
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
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

    private fun tv(
        text: String, size: Float, color: Int,
        bold: Boolean = false, lineSpacing: Float = 0f,
        align: Int = Gravity.START, bpad: Int = 0
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

    private fun contrastColor(rgb: Int): Int {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        val lum = (r * 299 + g * 587 + b * 114) / 1000
        return if (lum > 160) 0xFF1A1A2E.toInt() else 0xFFFFFFFF.toInt()
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun sysBarTop(insets: WindowInsets) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()).top
        else @Suppress("DEPRECATION") insets.systemWindowInsetTop
}
