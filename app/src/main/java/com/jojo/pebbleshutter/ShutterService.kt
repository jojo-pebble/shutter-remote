package com.jojo.pebbleshutter

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Accessibility service that can press the shutter button in another app's camera.
 * It does nothing on its own until asked (from the app screen or from the watch).
 */
class ShutterService : AccessibilityService() {

    enum class Mode { AUTO, TAP_ONLY }

    companion object {
        @Volatile
        var instance: ShutterService? = null
            private set

        private val logLines = ArrayList<String>()
        var onLogChanged: (() -> Unit)? = null

        fun log(msg: String) {
            val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            synchronized(logLines) {
                logLines.add("$time  $msg")
                if (logLines.size > 300) logLines.removeAt(0)
            }
            Handler(Looper.getMainLooper()).post { onLogChanged?.invoke() }
        }

        fun logText(): String = synchronized(logLines) { logLines.joinToString("\n") }

        fun clearLog() {
            synchronized(logLines) { logLines.clear() }
            onLogChanged?.invoke()
        }

        /** Words used to find the shutter button when none has been saved yet (includes Russian and Chinese labels). */
        private val SHUTTER_WORDS = listOf(
            "shutter", "capture", "take_photo", "takephoto", "take photo", "shoot",
            "photo_button", "затвор", "снимок", "снять", "съёмк", "съемк", "фото", "拍照", "快门"
        )
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var store: Store
    private var learningUntil = 0L
    private var overlay: View? = null

    override fun onServiceConnected() {
        instance = this
        store = Store(this)
        log("Service turned on")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        log("Service turned off")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        removeOverlay()
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return
        if (System.currentTimeMillis() > learningUntil) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        val node = event.source
        learningUntil = 0L
        store.cameraPackage = pkg
        if (node == null) {
            log("Tap in $pkg was caught, but the camera did not say which button it was. Use the backup method.")
            vibrate(2)
            return
        }
        val r = Rect().also { node.getBoundsInScreen(it) }
        store.viewId = node.viewIdResourceName
        store.description = node.contentDescription?.toString()
        store.tapX = r.exactCenterX()
        store.tapY = r.exactCenterY()
        log("Saved the button in $pkg: id=${node.viewIdResourceName ?: "-"}, label=${node.contentDescription ?: "-"}, position=${r.centerX()},${r.centerY()}")
        vibrate(1)
    }

    // ---------- Saving the button ----------

    /** Waits for the user to press the shutter in the camera and remembers which button it was. */
    fun startLearning(seconds: Int) {
        learningUntil = System.currentTimeMillis() + seconds * 1000L
        log("Waiting for you to press the shutter in the camera (up to $seconds s)...")
        main.postDelayed({
            if (learningUntil != 0L && System.currentTimeMillis() >= learningUntil) {
                learningUntil = 0L
                log("No tap caught. The camera may not report its buttons, so use the backup method.")
            }
        }, seconds * 1000L + 200)
    }

    /** After [delayMs] shows a translucent layer over the camera: wherever you tap is treated as the shutter. */
    fun startTapCalibration(delayMs: Long) {
        log("A grey layer will appear in ${delayMs / 1000} s: tap where the shutter button is")
        main.postDelayed({ showOverlay() }, delayMs)
    }

    private fun showOverlay() {
        removeOverlay()
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val label = TextView(this).apply {
            text = "Tap the shutter button"
            setTextColor(Color.WHITE)
            textSize = 22f
            setBackgroundColor(0xAA000000.toInt())
            setPadding(40, 24, 40, 24)
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(0x55000000)
            addView(label, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }
        root.setOnTouchListener { _, ev ->
            if (ev.action == MotionEvent.ACTION_UP) {
                val pkg = currentAppPackage()
                if (pkg != null) store.cameraPackage = pkg
                store.tapX = ev.rawX
                store.tapY = ev.rawY
                log("Saved the shutter position: ${ev.rawX.toInt()}, ${ev.rawY.toInt()} (app: ${pkg ?: "?"})")
                removeOverlay()
                vibrate(1)
            }
            true
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        try {
            wm.addView(root, lp)
            overlay = root
            main.postDelayed({
                if (overlay === root) {
                    removeOverlay()
                    log("Layer removed: no tap for 20 s")
                }
            }, 20_000)
        } catch (e: Exception) {
            log("Could not show the layer: ${e.message}")
        }
    }

    private fun removeOverlay() {
        val v = overlay ?: return
        overlay = null
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v)
        } catch (_: Exception) {
        }
    }

    // ---------- Pressing the shutter ----------

    fun scheduleShutter(delayMs: Long, mode: Mode) {
        log("Will press the shutter in ${delayMs / 1000} s (${if (mode == Mode.AUTO) "auto" else "by screen position"})")
        main.postDelayed({ pressShutter(mode) }, delayMs)
    }

    /** Result of the press; this code is sent to the watch. */
    enum class Result(val code: Int) { OK(1), NOT_CAMERA(2), NO_BUTTON(3) }

    /**
     * Main function: press the shutter in the open camera.
     * Presses only if the saved camera app is on screen, so other apps are never tapped.
     */
    fun pressShutter(mode: Mode = Mode.AUTO): Result {
        val root = rootInActiveWindow
        val pkg = root?.packageName?.toString()
        log("On screen now: ${pkg ?: "nothing visible"}")
        if (pkg == null || pkg == packageName) {
            log("Camera is not on screen, not pressing")
            vibrate(2)
            return Result.NOT_CAMERA
        }
        val camera = store.cameraPackage
        if (camera != null && pkg != camera) {
            log("The screen shows $pkg but the saved camera is $camera, not pressing")
            vibrate(2)
            return Result.NOT_CAMERA
        }

        if (mode == Mode.AUTO) {
            val node = findShutterNode(root, pkg)
            if (node != null) {
                val target = clickableSelfOrParent(node)
                if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    log("Pressed the button with a click command (id=${target.viewIdResourceName ?: "-"}, label=${target.contentDescription ?: "-"})")
                    vibrate(1)
                    return Result.OK
                }
                log("Button found but the click did not work, tapping its position instead")
                val r = Rect().also { node.getBoundsInScreen(it) }
                return if (tap(r.exactCenterX(), r.exactCenterY())) Result.OK else Result.NO_BUTTON
            }
        }

        if (store.hasTap) {
            return if (tap(store.tapX, store.tapY)) Result.OK else Result.NO_BUTTON
        }
        log("The shutter button is not set up yet. Run the setup first.")
        vibrate(2)
        return Result.NO_BUTTON
    }

    private fun findShutterNode(root: AccessibilityNodeInfo, pkg: String?): AccessibilityNodeInfo? {
        val samePkg = pkg != null && pkg == store.cameraPackage
        if (samePkg) {
            store.viewId?.let { id ->
                root.findAccessibilityNodeInfosByViewId(id).firstOrNull { it.isVisibleToUser }?.let { return it }
            }
            store.description?.let { d ->
                findInTree(root) { it.contentDescription?.toString() == d && it.isVisibleToUser }?.let { return it }
            }
        }
        // If a button was already saved, do not search by words: that could press the "Photo" tab while in video mode
        if (store.viewId != null || store.description != null || store.hasTap) return null
        return findInTree(root) { n ->
            if (!n.isVisibleToUser) return@findInTree false
            val hay = listOfNotNull(
                n.viewIdResourceName, n.contentDescription?.toString(), n.text?.toString()
            ).joinToString(" ").lowercase(Locale.ROOT)
            SHUTTER_WORDS.any { hay.contains(it) } && !hay.contains("video") && !hay.contains("видео")
        }
    }

    private fun findInTree(
        root: AccessibilityNodeInfo,
        test: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var seen = 0
        while (queue.isNotEmpty() && seen < 2000) {
            val n = queue.removeFirst()
            seen++
            if (test(n)) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    private fun clickableSelfOrParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var n: AccessibilityNodeInfo? = node
        var depth = 0
        while (n != null && depth < 5) {
            if (n.isClickable) return n
            n = n.parent
            depth++
        }
        return null
    }

    private fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        val sent = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                log("Tap at ${x.toInt()}, ${y.toInt()} done")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                log("Tap at ${x.toInt()}, ${y.toInt()} was cancelled by the system")
            }
        }, null)
        if (sent) vibrate(1) else log("The system did not accept the tap")
        return sent
    }

    // ---------- Diagnostics ----------

    /** Writes to the log which buttons the camera shows to the service. Useful when the button is not found. */
    fun scheduleDump(delayMs: Long) {
        log("In ${delayMs / 1000} s will record which buttons are visible on the camera screen")
        main.postDelayed({ dumpScreen() }, delayMs)
    }

    private fun dumpScreen() {
        val root = rootInActiveWindow
        if (root == null) {
            log("The screen is not visible to the service (the camera hides its content)")
            return
        }
        log("Screen: ${root.packageName}")
        var count = 0
        findInTree(root) { n ->
            val id = n.viewIdResourceName
            val desc = n.contentDescription?.toString()
            val text = n.text?.toString()
            if ((id != null || desc != null || text != null || n.isClickable) && count < 80) {
                val r = Rect().also { n.getBoundsInScreen(it) }
                log("  ${if (n.isClickable) "[click] " else ""}id=${id?.substringAfter(":id/") ?: "-"} label=${desc ?: "-"} text=${text?.take(20) ?: "-"} @${r.centerX()},${r.centerY()}")
                count++
            }
            false
        }
        log("Items recorded: $count")
    }

    private fun currentAppPackage(): String? {
        val pkg = rootInActiveWindow?.packageName?.toString()
        if (pkg != null && pkg != packageName) return pkg
        return windows.firstOrNull { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION }
            ?.root?.packageName?.toString()
    }

    private fun vibrate(times: Int) {
        val vib: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = if (times <= 1) longArrayOf(0, 60) else longArrayOf(0, 60, 120, 60)
        try {
            vib.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } catch (_: Exception) {
        }
    }

    fun toast(msg: String) = main.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
}
