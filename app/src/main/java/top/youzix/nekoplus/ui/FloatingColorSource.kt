/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.nekoplus.ui

/**
 * Where the floating window takes its colours from.
 *
 * The overlay is drawn by [top.youzix.nekoplus.service.FloatingWindowService] with plain framework
 * views, outside any Compose theme, so it cannot inherit the app's colour scheme implicitly.
 * This setting makes the choice explicit instead.
 */
enum class FloatingColorSource(val id: String, val label: String) {
    /** Material You / Monet: the wallpaper-derived palette the system exposes. */
    Dynamic("dynamic", "动态取色"),

    /** The miuix design language's own palette. */
    Miuix("miuix", "跟随 Miuix"),

    /** The Material Design 3 baseline palette. */
    Material3("material3", "跟随 Material Design"),
    ;

    companion object {
        fun from(id: String?): FloatingColorSource =
            entries.firstOrNull { it.id == id } ?: Dynamic
    }
}
