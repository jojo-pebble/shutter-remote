package com.jojo.pebbleshutter

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * The only screen of the app: three simple steps (turn on the service, show the shutter button, try it)
 * and a hidden "Troubleshooting" section.
 */
class MainActivity : Activity() {

    private val orange = 0xFFFF5500.toInt()
    private val grey = 0xFF9A9A9A.toInt()
    private val cardColor = 0xFF161616.toInt()
    private val lineColor = 0xFF3A3A3A.toInt()

    private lateinit var store: Store
    private lateinit var status: TextView
    private lateinit var logView: TextView
    private lateinit var troubleshooting: LinearLayout
    private lateinit var troubleshootingToggle: TextView
    private val badges = ArrayList<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(56), dp(20), dp(32))
        }

        col.addView(label("Shutter Remote", 30f, Color.WHITE, bold = true))
        col.addView(label("Take photos with your phone's camera from your Pebble.", 16f, grey))
        status = label("", 16f, orange, bold = true)
        col.addView(status, margins(top = 16))

        // Step 1
        val step1 = card(1, "Turn on the service", "Find Shutter Remote in the list and switch it on. This lets the app press the camera button for you.")
        step1.addView(button("Open settings", primary = true) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, margins(top = 12))
        step1.addView(button("The switch is greyed out?", primary = false) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }, margins(top = 8))
        step1.addView(label("Tap ⋮ in the top corner and choose \"Allow restricted settings\", then try again.", 13f, grey), margins(top = 6))
        col.addView(step1, margins(top = 20))

        // Step 2
        val step2 = card(2, "Show it the shutter button", "Your camera opens. Press its shutter button once yourself, and the app remembers where it is.")
        step2.addView(button("Set up the button", primary = true) {
            withService { it.startLearning(30); openCamera() }
        }, margins(top = 12))
        col.addView(step2, margins(top = 12))

        // Step 3
        val step3 = card(3, "Try it", "The camera opens and takes a photo after 4 seconds.")
        step3.addView(button("Take a test photo", primary = true) {
            withService { it.scheduleShutter(4000, ShutterService.Mode.AUTO); openCamera() }
        }, margins(top = 12))
        col.addView(step3, margins(top = 12))

        col.addView(
            label("Then open Shutter Remote on your watch and press the middle button. The phone screen must be on and unlocked.", 14f, grey),
            margins(top = 20)
        )

        // Troubleshooting (hidden until tapped)
        troubleshootingToggle = label("Troubleshooting  ▾", 15f, orange, bold = true).apply {
            setOnClickListener {
                val show = troubleshooting.visibility != View.VISIBLE
                troubleshooting.visibility = if (show) View.VISIBLE else View.GONE
                text = if (show) "Troubleshooting  ▴" else "Troubleshooting  ▾"
            }
        }
        col.addView(troubleshootingToggle, margins(top = 28))

        troubleshooting = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        troubleshooting.addView(label("If the button is not found, tap the spot yourself.", 13f, grey), margins(top = 8))
        troubleshooting.addView(button("Backup method: tap the shutter spot", primary = false) {
            withService { it.startTapCalibration(3000); openCamera() }
        }, margins(top = 8))
        troubleshooting.addView(button("Forget the saved button", primary = false) {
            store.clear()
            ShutterService.log("Saved button removed")
            refresh()
        }, margins(top = 8))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button("Copy log", primary = false) {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("log", ShutterService.logText()))
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(button("Clear log", primary = false) { ShutterService.clearLog() },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        troubleshooting.addView(row, margins(top = 8))
        logView = label("", 12f, grey).apply {
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        troubleshooting.addView(logView, margins(top = 8))
        col.addView(troubleshooting)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(col)
        })
    }

    override fun onResume() {
        super.onResume()
        ShutterService.onLogChanged = { refresh() }
        refresh()
    }

    override fun onPause() {
        ShutterService.onLogChanged = null
        super.onPause()
    }

    private fun refresh() {
        val serviceOn = ShutterService.instance != null
        val setUp = store.isSetUp
        setBadge(badges[0], 1, serviceOn)
        setBadge(badges[1], 2, setUp)
        setBadge(badges[2], 3, false)
        status.text = when {
            !serviceOn -> "Let's get started — begin with step 1."
            !setUp -> "Nice. Now step 2."
            else -> "All set. Try step 3, then use your watch."
        }
        logView.text = ShutterService.logText().ifEmpty { "Nothing yet" }
    }

    private fun withService(action: (ShutterService) -> Unit) {
        val s = ShutterService.instance
        if (s == null) {
            Toast.makeText(this, "Turn on the service first (step 1)", Toast.LENGTH_LONG).show()
            return
        }
        action(s)
    }

    private fun openCamera() {
        val pkg = store.cameraPackage
        val launch = pkg?.let { packageManager.getLaunchIntentForPackage(it) }
        try {
            startActivity(launch ?: Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        } catch (e: Exception) {
            ShutterService.log("Could not open the camera, please open it yourself")
        }
    }

    // ---------- Small UI helpers ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun margins(top: Int = 0) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top) }

    private fun label(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    /** A rounded dark card with a numbered badge, a title and a short explanation. */
    private fun card(number: Int, title: String, hint: String): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(cardColor)
            }
        }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val badge = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
        }
        badges.add(badge)
        setBadge(badge, number, false)
        head.addView(badge, LinearLayout.LayoutParams(dp(32), dp(32)))
        head.addView(label(title, 18f, Color.WHITE, bold = true), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { leftMargin = dp(12) })
        box.addView(head)
        box.addView(label(hint, 14f, grey), margins(top = 10))
        return box
    }

    /** Orange filled circle with a tick when the step is done, otherwise an outlined circle with its number. */
    private fun setBadge(badge: TextView, number: Int, done: Boolean) {
        badge.text = if (done) "✓" else number.toString()
        badge.setTextColor(if (done) Color.BLACK else Color.WHITE)
        badge.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            if (done) setColor(orange) else {
                setColor(Color.TRANSPARENT)
                setStroke(dp(2), lineColor)
            }
        }
    }

    private fun button(text: String, primary: Boolean, onClick: (View) -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 16f
        setTextColor(if (primary) Color.BLACK else orange)
        stateListAnimator = null
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            if (primary) setColor(orange) else {
                setColor(Color.TRANSPARENT)
                setStroke(dp(1), lineColor)
            }
        }
        setOnClickListener(onClick)
    }
}
