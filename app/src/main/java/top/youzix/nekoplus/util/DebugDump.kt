/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.nekoplus.util

import android.content.Context
import java.io.File

/**
 * Stores the last screen dump, so it can be looked at and copied after the fact.
 *
 * It has to be a file rather than a value in memory: the dump is taken while some *other* app is
 * on screen — that is the whole point — and by the time the user is back in this one, the
 * accessibility tree they wanted is long gone.
 */
object DebugDump {
    private const val DIR = "debug"
    private const val FILE_NAME = "ui-dump.txt"

    fun file(context: Context): File {
        val dir = (context.getExternalFilesDir(null) ?: context.filesDir).let { File(it, DIR) }
        return File(dir, FILE_NAME)
    }

    fun save(context: Context, text: String): Boolean = runCatching {
        val target = file(context)
        target.parentFile?.mkdirs()
        target.writeText(text)
        true
    }.getOrDefault(false)

    fun read(context: Context): String? =
        runCatching { file(context).takeIf { it.isFile }?.readText() }.getOrNull()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    /** Where the file is, spelled out for the user. */
    fun location(context: Context): String = file(context).absolutePath
}
