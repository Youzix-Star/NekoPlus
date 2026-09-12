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

/** Persists the selected engine. */
object UiEnginePrefs {
    private const val PREFS = "ui_prefs"
    private const val KEY_ENGINE = "ui_engine"

    fun load(context: Context): UiEngine {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return UiEngine.from(prefs.getString(KEY_ENGINE, null))
    }

    fun save(context: Context, engine: UiEngine) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENGINE, engine.id)
            .apply()
    }
}
