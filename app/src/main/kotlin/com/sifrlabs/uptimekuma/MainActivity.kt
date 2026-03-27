package com.sifrlabs.uptimekuma

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*

class MainActivity : Activity() {

    private val intervalOptions = listOf(1, 5, 10, 15, 30, 60)

    private lateinit var saveButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val success = intent.getBooleanExtra(WidgetUpdateService.EXTRA_SUCCESS, false)
            progressBar.visibility = View.GONE
            saveButton.isEnabled = true
            statusText.text = if (success) "Widget updated successfully." else "Failed to load — check hostname/slug."
            statusText.setTextColor(if (success) 0xFF4CAF50.toInt() else 0xFFF44336.toInt())
            statusText.visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 56, 56, 56)
        }

        root.addView(TextView(this).apply {
            text = "UptimeKuma Widget"
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        })
        root.addView(TextView(this).apply {
            text = "Settings"
            textSize = 13f
            setTextColor(0xFF888888.toInt())
            setPadding(0, 0, 0, 40)
        })

        root.addView(label("Hostname"))
        val hostnameEdit = EditText(this).apply {
            setText(Prefs.hostname(this@MainActivity))
            inputType = InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_CLASS_TEXT
            setSingleLine()
        }
        root.addView(hostnameEdit)
        root.addView(spacer(8))

        root.addView(label("Dashboard slug"))
        val slugEdit = EditText(this).apply {
            setText(Prefs.slug(this@MainActivity))
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
        }
        root.addView(slugEdit)
        root.addView(spacer(8))

        root.addView(label("Refresh interval"))
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                intervalOptions.map { if (it == 1) "1 minute" else "$it minutes" }
            )
            setSelection(intervalOptions.indexOf(Prefs.intervalMinutes(this@MainActivity)).coerceAtLeast(0))
        }
        root.addView(spinner)
        root.addView(spacer(32))

        saveButton = Button(this).apply {
            text = "Save & Refresh"
            setOnClickListener {
                val hostname = hostnameEdit.text.toString().trimEnd('/')
                val slug = slugEdit.text.toString().trim()
                val interval = intervalOptions[spinner.selectedItemPosition]
                Prefs.save(this@MainActivity, hostname, slug, interval)
                UptimeWidget.cancelAlarm(this@MainActivity)
                UptimeWidget.scheduleAlarm(this@MainActivity)

                isEnabled = false
                progressBar.visibility = View.VISIBLE
                statusText.visibility = View.GONE

                UptimeWidget.triggerUpdate(this@MainActivity)
            }
        }
        root.addView(saveButton)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (8 * resources.displayMetrics.density).toInt() }
        }
        root.addView(progressBar)

        statusText = TextView(this).apply {
            textSize = 13f
            setPadding(0, 12, 0, 0)
            visibility = View.GONE
        }
        root.addView(statusText)

        scroll.addView(root)
        setContentView(scroll)

        // Trigger a refresh on open (show progress while it runs)
        saveButton.isEnabled = false
        progressBar.visibility = View.VISIBLE
        UptimeWidget.triggerUpdate(this)
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(WidgetUpdateService.ACTION_UPDATE_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(updateReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(updateReceiver)
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 12f
        setTextColor(0xFF888888.toInt())
        setPadding(0, 0, 0, 4)
    }

    private fun spacer(dp: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (dp * resources.displayMetrics.density).toInt()
        )
    }
}
