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
    private val bgPresets = listOf(
        0x1A1A2E to "Navy",
        0x000000 to "Black",
        0x1C1C2E to "Slate",
        0x0A1628 to "Blue",
        0x1C1C1C to "Charcoal",
        0x0D1F12 to "Forest",
    )
    private var selectedBgRgb: Int = 0x1A1A2E
    private var bgOpacityPct: Int  = 80

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
        val storedColor   = Prefs.getWidgetBgColor(this, appWidgetId)
        val storedRgb     = storedColor and 0x00FFFFFF
        if (bgPresets.any { it.first == storedRgb }) selectedBgRgb = storedRgb
        bgOpacityPct = (storedColor ushr 24) * 100 / 255

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

        // Color swatches
        appearCard.addView(tv("Background color", 12f, cMuted).apply {
            setPadding(0, 0, 0, dp(10))
        })
        val swatchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        }
        bgPresets.forEach { (rgb, _) ->
            val isSelected = rgb == selectedBgRgb
            val swatch = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xFF000000.toInt() or rgb)
                    if (isSelected) setStroke(dp(3), cGreen)
                    else setStroke(dp(2), cStroke)
                }
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                    rightMargin = dp(10)
                }
                isClickable = true
                isFocusable = true
            }
            if (isSelected) {
                // Overlay a checkmark on the selected swatch
                val overlay = FrameLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                        rightMargin = dp(10)
                    }
                }
                swatch.layoutParams = android.widget.FrameLayout.LayoutParams(dp(36), dp(36))
                overlay.addView(swatch)
                overlay.addView(TextView(this).apply {
                    text = "✓"
                    textSize = 13f
                    setTextColor(0xFFFFFFFF.toInt())
                    gravity = Gravity.CENTER
                    layoutParams = android.widget.FrameLayout.LayoutParams(dp(36), dp(36))
                })
                overlay.isClickable = true
                overlay.isFocusable = true
                overlay.setOnClickListener { selectedBgRgb = rgb; buildUi() }
                swatchRow.addView(overlay)
            } else {
                swatch.setOnClickListener { selectedBgRgb = rgb; buildUi() }
                swatchRow.addView(swatch)
            }
        }
        appearCard.addView(swatchRow)

        // Opacity slider
        appearCard.addView(tv("Opacity", 12f, cMuted).apply { setPadding(0, 0, 0, dp(8)) })
        val opacityRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
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
                    bgOpacityPct = p
                    opacityLabel.text = "$p%"
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        opacityRow.addView(seekBar)
        opacityRow.addView(opacityLabel)
        appearCard.addView(opacityRow)
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
        Prefs.setWidgetBgColor(this, appWidgetId, (alpha shl 24) or selectedBgRgb)
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

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun sysBarTop(insets: WindowInsets) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()).top
        else @Suppress("DEPRECATION") insets.systemWindowInsetTop
}
