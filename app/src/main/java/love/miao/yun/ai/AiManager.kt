// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 NekoPlus contributors
//
// Ported from NekoNeko (top.youzix.nekoneko), AiManager.java. The original is the
// author's own work, AGPL-3.0-only, so it is carried over here under the same terms.

package love.miao.yun.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * AI 配置、预设与调用（OpenAI 兼容 chat/completions 接口）。
 * 默认配置为 DeepSeek（deepseek-v4-flash），提示词默认为"微软式中文"风格。
 */
object AiManager {

    private const val PREFS = "ai_config"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL = "model"
    private const val KEY_PROMPT = "prompt"
    private const val KEY_SYSTEM_PROMPT = "system_prompt"
    private const val KEY_PRESETS = "presets"

    const val DEFAULT_BASE_URL = "https://api.deepseek.com"
    const val DEFAULT_MODEL = "deepseek-v4-flash"

    // 系统框架提示词（控制回答格式，不推荐用户改）
    const val DEFAULT_SYSTEM_PROMPT =
        "你是一个文本改写助手。只输出改写后的文本，不要任何解释。"

    // 人设提示词（用户可自由修改）
    const val DEFAULT_PROMPT =
        "你是一位专业的中文编辑。请将用户提供的文本改写为“微软式中文”风格：" +
            "使用正式、书面、简洁的简体中文；" +
            "多使用“请”“请确保”“请注意”等礼貌指令式表达；" +
            "避免口语化、网络用语和不必要的英文混排；" +
            "保持原意不变，只优化表达方式。"

    // 内置预设
    const val PRESET_MS_TRANSLATE = "微软式翻译"
    const val PRESET_MS_CHINESE = "微软式中文"
    const val PRESET_EMOJI = "Emoji"

    const val BUILTIN_TRANSLATE_PROMPT =
        "你是一位幽默的文本改写专家。请把用户提供的文本改写为“微软式翻译腔”风格：" +
            "使用正式、书面、略带生硬直译腔调的简体中文；" +
            "多使用“请”“请确保”“请勿”“请联系您的管理员”“为了继续”“我们对此感到抱歉，但也不是非常抱歉”等典型微软系统提示式表达；" +
            "语气礼貌、机械，像系统更新提示或错误对话框；" +
            "保持原意大体不变，允许适度夸张以增强幽默效果。" +
            "参考风格示例：“我们正在为您的设备准备一些重要的更新。请勿关闭您的计算机。”、“您没有权限执行此操作。请联系您的管理员为了请求这个权限。”" +
            "直接输出改写后的文本，不要任何解释。"

    const val BUILTIN_EMOJI_PROMPT =
        "你是一位富有创意的 emoji 转译专家。请将用户提供的文本转化为 emoji 表达：" +
            "用 emoji 组合来传达原文的意思、情绪和画面感；" +
            "保留原文的结构（如分句、段落），用 emoji 风格呈现；" +
            "适当使用少量文字辅助表达，确保含义清晰；" +
            "可以发挥创意，使用比喻、谐音、象形等方式匹配语义；" +
            "保持轻松有趣的风格。" +
            "直接输出 emoji 版本，不要输出任何解释。"

    class Config {
        var baseUrl: String? = DEFAULT_BASE_URL
        var apiKey: String? = ""
        var model: String? = DEFAULT_MODEL
        var systemPrompt: String? = DEFAULT_SYSTEM_PROMPT
        var prompt: String? = DEFAULT_PROMPT
    }

    interface Callback {
        fun onSuccess(modifiedText: String)
        fun onError(message: String)
    }

    interface ListCallback {
        fun onSuccess(models: List<String>)
        fun onError(message: String)
    }

    private val MAIN = Handler(Looper.getMainLooper())
    private var _lastUsage: UsageRecord? = null

    /** 上次调用的用量记录。 */
    class UsageRecord {
        var model: String? = null
        var promptTokens: Int = 0
        var completionTokens: Int = 0
        var totalTokens: Int = 0
        var cachedTokens: Int = 0
    }

    /** 获取上次调用的用量记录，查询后清空。 */
    fun consumeLastUsage(): UsageRecord? {
        val u = _lastUsage
        _lastUsage = null
        return u
    }

    fun load(context: Context): Config {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val c = Config()
        c.baseUrl = sp.getString(KEY_BASE_URL, DEFAULT_BASE_URL)
        c.apiKey = sp.getString(KEY_API_KEY, "")
        c.model = sp.getString(KEY_MODEL, DEFAULT_MODEL)
        c.systemPrompt = sp.getString(KEY_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT)
        c.prompt = sp.getString(KEY_PROMPT, DEFAULT_PROMPT)
        return c
    }

    fun save(context: Context, c: Config) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BASE_URL, c.baseUrl ?: "")
            .putString(KEY_API_KEY, c.apiKey ?: "")
            .putString(KEY_MODEL, c.model ?: "")
            .putString(KEY_SYSTEM_PROMPT, c.systemPrompt ?: "")
            .putString(KEY_PROMPT, c.prompt ?: "")
            .apply()
    }

    // ---------- 预设 ----------

    /** 内置预设：仅提示词非空，其余字段留空表示沿用当前配置。 */
    fun builtinPreset(name: String): Config {
        val c = Config()
        c.baseUrl = ""
        c.apiKey = ""
        c.model = ""
        c.systemPrompt = DEFAULT_SYSTEM_PROMPT
        if (PRESET_MS_TRANSLATE == name) {
            c.prompt = BUILTIN_TRANSLATE_PROMPT
        } else if (PRESET_MS_CHINESE == name) {
            c.prompt = DEFAULT_PROMPT
        } else if (PRESET_EMOJI == name) {
            c.prompt = BUILTIN_EMOJI_PROMPT
        } else {
            c.prompt = DEFAULT_PROMPT
        }
        return c
    }

    fun savePreset(context: Context, name: String, c: Config) {
        val presets = getPresetsJson(context)
        val o = JSONObject()
        try {
            o.put(KEY_BASE_URL, c.baseUrl ?: "")
            o.put(KEY_API_KEY, c.apiKey ?: "")
            o.put(KEY_MODEL, c.model ?: "")
            o.put(KEY_SYSTEM_PROMPT, c.systemPrompt ?: "")
            o.put(KEY_PROMPT, c.prompt ?: "")
            presets.put(name, o)
            putPresetsJson(context, presets)
        } catch (ignored: Exception) {
        }
    }

    fun deletePreset(context: Context, name: String): Boolean {
        val presets = getPresetsJson(context)
        if (presets.has(name)) {
            presets.remove(name)
            putPresetsJson(context, presets)
            return true
        }
        return false
    }

    /** 用户自定义预设名（不含内置）。 */
    fun getUserPresetNames(context: Context): List<String> {
        val names = ArrayList<String>()
        val presets = getPresetsJson(context)
        val iterator = presets.keys()
        while (iterator.hasNext()) {
            names.add(iterator.next())
        }
        names.sort()
        return names
    }

    /** 全部可加载预设名（内置 + 用户自定义）。 */
    fun getAllPresetNames(context: Context): List<String> {
        val names = ArrayList<String>()
        names.add(PRESET_MS_TRANSLATE)
        names.add(PRESET_MS_CHINESE)
        names.add(PRESET_EMOJI)
        names.addAll(getUserPresetNames(context))
        return names
    }

    /** 读取预设；内置预设或用户预设均可。 */
    fun loadPreset(context: Context, name: String): Config {
        if (PRESET_MS_TRANSLATE == name || PRESET_MS_CHINESE == name || PRESET_EMOJI == name) {
            return builtinPreset(name)
        }
        val presets = getPresetsJson(context)
        val o = presets.optJSONObject(name)
        val c = Config()
        if (o != null) {
            c.baseUrl = o.optString(KEY_BASE_URL, "")
            c.apiKey = o.optString(KEY_API_KEY, "")
            c.model = o.optString(KEY_MODEL, "")
            c.systemPrompt = o.optString(KEY_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT)
            c.prompt = o.optString(KEY_PROMPT, DEFAULT_PROMPT)
        }
        return c
    }

    private fun getPresetsJson(context: Context): JSONObject {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PRESETS, "{}")
        return try {
            JSONObject(raw ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun putPresetsJson(context: Context, obj: JSONObject) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PRESETS, obj.toString())
            .apply()
    }

    // ---------- AI 调用 ----------

    /**
     * 异步调用 AI 修改文本，结果通过回调返回（主线程）。
     * 提示词中含 {text} 时：{text} 替换为捕获文本，整体作为用户消息发送；
     * 否则提示词作为系统指令、捕获文本作为用户消息发送。
     */
    fun modifyText(cfg: Config, text: String, callback: Callback) {
        val rawSystemPrompt = cfg.systemPrompt
        val rawPrompt = cfg.prompt

        val sysPrompt: String = if (rawSystemPrompt == null || rawSystemPrompt.trim().isEmpty()) {
            DEFAULT_SYSTEM_PROMPT
        } else {
            rawSystemPrompt.trim()
        }
        val userContent: String
        if (rawPrompt != null && rawPrompt.contains("{text}")) {
            userContent = rawPrompt.replace("{text}", text)
        } else {
            val persona: String = if (rawPrompt == null || rawPrompt.trim().isEmpty()) {
                DEFAULT_PROMPT
            } else {
                rawPrompt.trim()
            }
            userContent = persona + "\n\n" + text
        }

        val modelName = cfg.model
        val thread = Thread {
            try {
                _lastUsage = null
                val result = requestChatCompletion(cfg, sysPrompt, userContent)
                // 补充 model 名到 usage
                val usage = _lastUsage
                if (usage != null && usage.model == null) {
                    usage.model = modelName
                }
                MAIN.post { callback.onSuccess(result) }
            } catch (e: Exception) {
                val msg = e.message ?: e.toString()
                MAIN.post { callback.onError(msg) }
            }
        }
        thread.isDaemon = true
        thread.start()
    }

    /**
     * 异步获取模型列表（GET {base}/models），结果通过回调返回（主线程）。
     */
    fun listModels(cfg: Config, callback: ListCallback) {
        val thread = Thread {
            try {
                val rawBase = cfg.baseUrl
                var base: String = if (rawBase == null || rawBase.trim().isEmpty()) {
                    DEFAULT_BASE_URL
                } else {
                    rawBase.trim()
                }
                base = base.replace(Regex("/+$"), "")
                val url = URL(base + "/models")
                val conn = url.openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 30000
                    conn.readTimeout = 30000
                    conn.setRequestProperty(
                        "Authorization",
                        "Bearer " + (cfg.apiKey ?: "").trim(),
                    )

                    val code = conn.responseCode
                    val stream = if (code >= 400) conn.errorStream else conn.inputStream
                    val resp = readAll(stream)
                    if (code >= 400) {
                        throw Exception("HTTP " + code + ": " + truncate(resp, 200))
                    }
                    val root = JSONObject(resp)
                    val data = root.getJSONArray("data")
                    val models = ArrayList<String>()
                    for (i in 0 until data.length()) {
                        models.add(data.getJSONObject(i).getString("id"))
                    }
                    if (models.isEmpty()) {
                        throw Exception("模型列表为空")
                    }
                    MAIN.post { callback.onSuccess(models) }
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                val msg = e.message ?: e.toString()
                MAIN.post { callback.onError(msg) }
            }
        }
        thread.isDaemon = true
        thread.start()
    }

    private fun requestChatCompletion(cfg: Config, sysPrompt: String, userContent: String): String {
        val rawBase = cfg.baseUrl
        var base: String = if (rawBase == null || rawBase.trim().isEmpty()) {
            DEFAULT_BASE_URL
        } else {
            rawBase.trim()
        }
        base = base.replace(Regex("/+$"), "")
        val url = URL(base + "/chat/completions")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 30000
            conn.readTimeout = 60000
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Authorization", "Bearer " + (cfg.apiKey ?: "").trim())
            conn.doOutput = true

            val rawModel = cfg.model
            val body = JSONObject()
            body.put(
                "model",
                if (rawModel == null || rawModel.trim().isEmpty()) DEFAULT_MODEL else rawModel.trim(),
            )
            val messages = JSONArray()
            messages.put(JSONObject().put("role", "system").put("content", sysPrompt))
            messages.put(JSONObject().put("role", "user").put("content", userContent))
            body.put("messages", messages)
            body.put("temperature", 0.3)

            val os = conn.outputStream
            os.write(body.toString().toByteArray(Charsets.UTF_8))
            os.flush()
            os.close()

            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val resp = readAll(stream)
            if (code >= 400) {
                throw Exception("HTTP " + code + ": " + truncate(resp, 200))
            }
            val root = JSONObject(resp)
            val content: String? = root.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
            if (content == null || content.trim().isEmpty()) {
                throw Exception("AI 返回内容为空")
            }

            // 提取 token 用量并记录
            if (root.has("usage")) {
                val usage = root.getJSONObject("usage")
                val prompt = usage.optInt("prompt_tokens", 0)
                val completion = usage.optInt("completion_tokens", 0)
                val total = usage.optInt("total_tokens", 0)
                // DeepSeek 返回两种缓存字段：
                //   顶层 prompt_cache_hit_tokens
                //   嵌套 prompt_tokens_details.cached_tokens
                var cached = usage.optInt("prompt_cache_hit_tokens", 0)
                if (cached == 0 && usage.has("prompt_tokens_details")) {
                    cached = usage.getJSONObject("prompt_tokens_details")
                        .optInt("cached_tokens", 0)
                }
                val rec = UsageRecord()
                rec.promptTokens = prompt
                rec.completionTokens = completion
                rec.totalTokens = total
                rec.cachedTokens = cached
                _lastUsage = rec
            } else {
                _lastUsage = null
            }

            return content.trim()
        } finally {
            conn.disconnect()
        }
    }

    private fun readAll(stream: InputStream?): String {
        if (stream == null) {
            return ""
        }
        val reader = stream.bufferedReader(Charsets.UTF_8)
        val sb = StringBuilder()
        var line = reader.readLine()
        while (line != null) {
            sb.append(line)
            line = reader.readLine()
        }
        reader.close()
        return sb.toString()
    }

    private fun truncate(s: String?, max: Int): String {
        if (s == null) {
            return ""
        }
        return if (s.length > max) s.substring(0, max) + "..." else s
    }
}
