package com.jojo.pebbleshutter

import android.content.Context

/** What is saved about the shutter button: which app, which button and where it is on screen. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("shutter", Context.MODE_PRIVATE)

    var cameraPackage: String?
        get() = prefs.getString("pkg", null)
        set(v) = prefs.edit().putString("pkg", v).apply()

    var viewId: String?
        get() = prefs.getString("viewId", null)
        set(v) = prefs.edit().putString("viewId", v).apply()

    var description: String?
        get() = prefs.getString("desc", null)
        set(v) = prefs.edit().putString("desc", v).apply()

    var tapX: Float
        get() = prefs.getFloat("x", -1f)
        set(v) = prefs.edit().putFloat("x", v).apply()

    var tapY: Float
        get() = prefs.getFloat("y", -1f)
        set(v) = prefs.edit().putFloat("y", v).apply()

    val hasTap: Boolean get() = tapX >= 0f && tapY >= 0f

    fun clear() = prefs.edit().clear().apply()

    /** True once a camera app and either a button id or a screen position are saved. */
    val isSetUp: Boolean get() = cameraPackage != null && (viewId != null || description != null || hasTap)
}
