/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Ported from NekoNeko (top.youzix.nekoneko), UpdateChecker.java.
 */

package love.miao.yun.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import love.miao.yun.BuildConfig
import org.json.JSONObject

/** What a version check found. */
sealed interface UpdateResult {
    /** A newer release exists. [apkUrl] is null when the release carries no APK asset. */
    data class Available(
        val version: String,
        val notes: String,
        val apkUrl: String?,
    ) : UpdateResult

    data object UpToDate : UpdateResult

    data class Failed(val message: String) : UpdateResult
}

/**
 * Checks GitHub Releases for a newer build.
 *
 * The API is tried first; when it answers with an error — rate limiting, most often — the check
 * falls back to the releases page and reads the tag out of its redirect, which needs no token and
 * has no quota. That fallback is the difference between "check for updates" working and quietly
 * failing for the one user who taps it twice.
 */
object UpdateChecker {
    private const val REPO = "Youzix-Star/NekoPlus"
    private const val API_URL = "https://api.github.com/repos/$REPO/releases/latest"
    private const val RELEASES_PAGE = "https://github.com/$REPO/releases/latest"

    private val main = Handler(Looper.getMainLooper())

    /** Runs the check off the main thread and calls back on it. */
    fun check(onResult: (UpdateResult) -> Unit) {
        val current = BuildConfig.VERSION_NAME
        Thread {
            val result = try {
                viaApi(current)
            } catch (error: Exception) {
                // A failed request is not the end of the story: try the page fallback first.
                runCatching { viaPage(current) }.getOrElse {
                    UpdateResult.Failed(error.message ?: error.toString())
                }
            }
            main.post { onResult(result) }
        }.apply { isDaemon = true }.start()
    }

    /** Opens the release page, for when the user would rather grab the APK by hand. */
    fun openReleasePage(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_PAGE))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun viaApi(current: String): UpdateResult {
        val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github.v3+json")
        }
        return try {
            val code = connection.responseCode
            if (code >= 400) {
                // Rate limited or otherwise refused: the page fallback has no quota to hit.
                return viaPage(current)
            }
            val release = JSONObject(readAll(connection.inputStream))
            val tag = release.optString("tag_name", "")
            val version = tag.removePrefix("v")
            val notes = release.optString("body", "")

            var apkUrl: String? = null
            release.optJSONArray("assets")?.let { assets ->
                for (index in 0 until assets.length()) {
                    val asset = assets.optJSONObject(index) ?: continue
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk") && !name.contains("debug")) {
                        apkUrl = asset.optString("browser_download_url", null)
                        break
                    }
                }
            }

            if (isNewer(version, current)) {
                UpdateResult.Available(version = version, notes = notes, apkUrl = apkUrl)
            } else {
                UpdateResult.UpToDate
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun viaPage(current: String): UpdateResult {
        val connection = (URL(RELEASES_PAGE).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            instanceFollowRedirects = false
        }
        return try {
            val code = connection.responseCode
            val location = if (code in 300..399) connection.getHeaderField("Location") else null
            if (location != null && location.contains("/tag/")) {
                val version = location.substringAfterLast("/").removePrefix("v")
                if (isNewer(version, current)) {
                    UpdateResult.Available(version = version, notes = "", apkUrl = null)
                } else {
                    UpdateResult.UpToDate
                }
            } else {
                UpdateResult.Failed("无法获取版本信息")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun readAll(input: InputStream?): String {
        if (input == null) return ""
        return BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader ->
            reader.readText()
        }
    }
}

/**
 * Whether [latest] is a later release than [current].
 *
 * Versions are compared on their numeric core first, and only then on the pre-release suffix —
 * so `2.0.0` counts as newer than `2.0.0-alpha.2`, which the plain "strip every non-digit"
 * comparison the original used would have missed, leaving alpha users stuck for good.
 */
internal fun isNewer(latest: String, current: String): Boolean {
    val (latestCore, latestPre) = splitVersion(latest)
    val (currentCore, currentPre) = splitVersion(current)

    for (index in 0 until maxOf(latestCore.size, currentCore.size)) {
        val l = latestCore.getOrElse(index) { 0 }
        val c = currentCore.getOrElse(index) { 0 }
        if (l != c) return l > c
    }

    // Same numbers: a finished release beats any pre-release of it.
    if (latestPre == null && currentPre != null) return true
    if (latestPre != null && currentPre == null) return false
    if (latestPre == null && currentPre == null) return false
    return comparePreRelease(latestPre!!, currentPre!!) > 0
}

private fun splitVersion(version: String): Pair<List<Int>, String?> {
    val cleaned = version.trim().removePrefix("v")
    val core = cleaned.substringBefore('-')
    val pre = cleaned.substringAfter('-', missingDelimiterValue = "").ifBlank { null }
    val numbers = core.split('.').map { part ->
        part.filter { it.isDigit() }.toIntOrNull() ?: 0
    }
    return numbers to pre
}

/** `alpha.2` < `alpha.10` < `beta.1` < `rc.1`, with plain words ranking by name. */
private fun comparePreRelease(latest: String, current: String): Int {
    val left = latest.split('.')
    val right = current.split('.')
    for (index in 0 until maxOf(left.size, right.size)) {
        val l = left.getOrNull(index) ?: return -1
        val c = right.getOrNull(index) ?: return 1
        val ln = l.toIntOrNull()
        val cn = c.toIntOrNull()
        val comparison = when {
            ln != null && cn != null -> ln.compareTo(cn)
            ln != null -> -1
            cn != null -> 1
            else -> rank(l).compareTo(rank(c)).takeIf { it != 0 } ?: l.compareTo(c)
        }
        if (comparison != 0) return comparison
    }
    return 0
}

private fun rank(word: String): Int = when (word.lowercase()) {
    "alpha", "a" -> 0
    "beta", "b" -> 1
    "rc" -> 2
    else -> 3
}
