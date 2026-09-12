/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0
 */

package love.miao.yun.ui

import android.content.Context

/**
 * Which UI engine draws the app.
 *
 * The two engines are complete, independent implementations of the same four screens — one built
 * on miuix, one on Material Design — and the user picks between them in Settings.
 */
enum class UiEngine(val id: String, val label: String) {
    Miuix("miuix", "Miuix"),
    Material3("material3", "Material Design"),
    ;

    companion object {
        fun from(id: String?): UiEngine = entries.firstOrNull { it.id == id } ?: Miuix
    }
}

/** Persists the selected engine and the glass-effect switch. */
object UiEnginePrefs {
    private const val PREFS = "ui_prefs"
    private const val KEY_ENGINE = "ui_engine"
    private const val KEY_USE_BLUR = "use_blur"
    private const val KEY_FLOATING_COLOR = "floating_color"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): UiEngine =
        UiEngine.from(prefs(context).getString(KEY_ENGINE, null))

    fun save(context: Context, engine: UiEngine) {
        prefs(context).edit().putString(KEY_ENGINE, engine.id).apply()
    }

    /**
     * Whether translucency is on: the miuix bottom bar's liquid glass and the Material 3 top
     * bar's blur. Defaults to `true`, matching the reference project.
     */
    fun loadUseBlur(context: Context): Boolean =
        prefs(context).getBoolean(KEY_USE_BLUR, true)

    fun saveUseBlur(context: Context, useBlur: Boolean) {
        prefs(context).edit().putBoolean(KEY_USE_BLUR, useBlur).apply()
    }

    /** Which palette the floating window draws itself with. */
    fun loadFloatingColor(context: Context): FloatingColorSource =
        FloatingColorSource.from(prefs(context).getString(KEY_FLOATING_COLOR, null))

    fun saveFloatingColor(context: Context, source: FloatingColorSource) {
        prefs(context).edit().putString(KEY_FLOATING_COLOR, source.id).apply()
    }

    /**
     * Observable access for [love.miao.yun.service.FloatingWindowService]: the overlay lives
     * outside the activity, so it watches the preference instead of reading [MiaoState] once.
     * Pair every call with [unregisterListener].
     */
    fun registerListener(
        context: Context,
        listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        prefs(context).registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(
        context: Context,
        listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        prefs(context).unregisterOnSharedPreferenceChangeListener(listener)
    }

    /** True when [key] is the floating window's palette, i.e. the overlay must restyle. */
    fun isFloatingColorKey(key: String?): Boolean = key == KEY_FLOATING_COLOR
}
