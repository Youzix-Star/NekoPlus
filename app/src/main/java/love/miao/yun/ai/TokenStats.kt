// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 NekoPlus contributors
//
// Ported from NekoNeko (top.youzix.nekoneko), TokenStats.java. The original is the
// author's own work, AGPL-3.0-only, so it is carried over here under the same terms.

package love.miao.yun.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Token 用量统计：按模型分别记录，支持按时间范围和模型筛选。
 * 数据存储在 SharedPreferences 的 JSON 数组中。
 * 自动清理 90 天前的记录。
 */
object TokenStats {

    private const val PREFS = "token_stats"
    private const val KEY_RECORDS = "records"

    /** 单次用量记录。 */
    class Record {
        var timestamp: Long
        var model: String
        var promptTokens: Int
        var completionTokens: Int
        var totalTokens: Int
        var cachedTokens: Int

        constructor(
            timestamp: Long,
            model: String?,
            prompt: Int,
            completion: Int,
            total: Int,
            cachedTokens: Int,
        ) {
            this.timestamp = timestamp
            this.model = model ?: "unknown"
            this.promptTokens = prompt
            this.completionTokens = completion
            this.totalTokens = total
            this.cachedTokens = cachedTokens
        }
    }

    /** 一段时间内的统计汇总。 */
    class Stats {
        var totalCalls: Int = 0
        var totalPromptTokens: Int = 0
        var totalCompletionTokens: Int = 0
        var totalTokens: Int = 0
        var cachedTokens: Int = 0
        var cachedCalls: Int = 0

        fun cacheHitPercent(): Int =
            if (totalCalls > 0) (cachedCalls * 100f / totalCalls).toInt() else 0
    }

    /** 记录一次 API 调用。 */
    fun record(
        context: Context,
        model: String?,
        promptTokens: Int,
        completionTokens: Int,
        totalTokens: Int,
        cachedTokens: Int,
    ) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = sp.getString(KEY_RECORDS, "[]")
        val records = parseRecords(raw).toMutableList()

        records.add(
            Record(
                System.currentTimeMillis(),
                model,
                promptTokens,
                completionTokens,
                totalTokens,
                cachedTokens,
            ),
        )

        // 清理 90 天前的记录
        val cutoff = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
        val cleaned = ArrayList<Record>()
        for (r in records) {
            if (r.timestamp >= cutoff) cleaned.add(r)
        }

        sp.edit().putString(KEY_RECORDS, serialize(cleaned)).apply()
    }

    /** 查询指定时间范围、指定模型的统计。model 为 null 表示全部模型。 */
    fun query(context: Context, fromTimestamp: Long, model: String?): Stats {
        val records = loadRecords(context)
        val s = Stats()
        for (r in records) {
            if (r.timestamp < fromTimestamp) continue
            if (model != null && model != r.model) continue
            s.totalCalls++
            s.totalPromptTokens += r.promptTokens
            s.totalCompletionTokens += r.completionTokens
            s.totalTokens += r.totalTokens
            s.cachedTokens += r.cachedTokens
            if (r.cachedTokens > 0) s.cachedCalls++
        }
        return s
    }

    /** 获取所有出现过的模型名（按使用量降序）。 */
    fun getModelNames(context: Context): List<String> {
        val records = loadRecords(context)
        val counts = LinkedHashMap<String, Int>()
        for (r in records) {
            counts[r.model] = (counts[r.model] ?: 0) + r.totalTokens
        }
        val sorted = ArrayList(counts.entries)
        sorted.sortWith(
            Comparator<Map.Entry<String, Int>> { a, b -> b.value - a.value },
        )
        val result = ArrayList<String>()
        for (e in sorted) {
            result.add(e.key)
        }
        return result
    }

    // ---------- 内部 ----------

    private fun loadRecords(context: Context): List<Record> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_RECORDS, "[]")
        return parseRecords(raw)
    }

    private fun serialize(records: List<Record>): String {
        val arr = JSONArray()
        try {
            for (r in records) {
                val o = JSONObject()
                o.put("ts", r.timestamp)
                o.put("m", r.model)
                o.put("p", r.promptTokens)
                o.put("c", r.completionTokens)
                o.put("t", r.totalTokens)
                o.put("cache", r.cachedTokens)
                arr.put(o)
            }
        } catch (ignored: Exception) {
        }
        return arr.toString()
    }

    private fun parseRecords(json: String?): List<Record> {
        val list = ArrayList<Record>()
        try {
            val arr = JSONArray(json ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    Record(
                        o.optLong("ts", 0),
                        o.optString("m", "unknown"),
                        o.optInt("p", 0),
                        o.optInt("c", 0),
                        o.optInt("t", 0),
                        o.optInt("cache", 0),
                    ),
                )
            }
        } catch (ignored: Exception) {
        }
        return list
    }
}
