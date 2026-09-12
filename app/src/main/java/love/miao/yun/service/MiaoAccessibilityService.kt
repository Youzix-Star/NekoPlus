package love.miao.yun.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import love.miao.yun.util.CrashHandler
import love.miao.yun.util.DebugLog
import love.miao.yun.util.MiaoConfig
import love.miao.yun.util.TextProcessor

open class MiaoAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MiaoSvc"
        private const val PREFS_NAME = "miao_config"
        private const val PROCESSING_TIMEOUT = 2000L
        private const val ECHO_DETECT_MS = 800L
        private const val CONTENT_CHANGE_THROTTLE_MS = 300L
        // 默认语音防抖时间（会被配置覆盖）
        private const val DEFAULT_VOICE_DEBOUNCE_MS = 1000L

        private val WECHAT_EDIT_IDS = listOf(
            "com.tencent.mm:id/chatting_content_et",
            "com.tencent.mm:id/alk",
            "com.tencent.mm:id/alj",
            "com.tencent.mm:id/y5"
        )
        private val WECHAT_SEND_IDS = listOf(
            "com.tencent.mm:id/chatting_send_btn",
            "com.tencent.mm:id/anv",
            "com.tencent.mm:id/emoji_send_btn"
        )
        // 微信发送按钮可能被混淆 ID，按文字兜底
        private val WECHAT_SEND_TEXTS = listOf("发送", "Send")

        // 需要用 contentChanged 兜底的应用
        private val CONTENT_CHANGED_PACKAGES = setOf("com.tencent.mm")

        var isRunning = false
            private set
        var instance: MiaoAccessibilityService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean("service_enabled", true)
        }

        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean("service_enabled", enabled).apply()
        }
    }

    private var cachedConfig: MiaoConfig? = null
    private val userOriginalMap = HashMap<String, String>()
    private val lastSetMap = HashMap<String, String>()
    private val lastWriteTimeMap = HashMap<String, Long>()
    private var currentPkg: String = ""
    private var processing = false
    private val handler = Handler(Looper.getMainLooper())
    private var lastContentChangeTime = 0L
    private var lastContentChangeText = ""
    // 内容删除优化
    private var lastRawTextMap = HashMap<String, String>()
    private var deleteOptimizeRunnable: Runnable? = null
    private var deleteOptimizePendingPkg: String? = null
    private var deleteOptimizePendingCfg: MiaoConfig? = null
    private var deleteOptimizeActive = HashMap<String, Boolean>()
    // 实时模式防抖：语音输入时不打断
    private var debounceRunnable: Runnable? = null
    private var pendingPkg: String? = null
    private var pendingConfig: MiaoConfig? = null
    // 删除后自动恢复：用户删除喵/颜文字后，停顿0.5秒自动加回
    private var deleteRecoveryRunnable: Runnable? = null
    private var deleteRecoveryPkg: String? = null
    private var deleteRecoveryCfg: MiaoConfig? = null

    /**
     * 微信 v8.0.52+ 节点混淆后 rootInActiveWindow 可能为空/节点缺失。
     * 降级：遍历所有窗口，优先取当前包名的窗口根。
     */
    private fun getBestRoot(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow
        if (root != null) {
            Log.d(TAG, "getBestRoot: got rootInActiveWindow")
            return root
        }
        Log.w(TAG, "getBestRoot: rootInActiveWindow is null, trying getWindows()")
        try {
            val windows: List<AccessibilityWindowInfo>? = windows
            if (windows != null) {
                for (w in windows) {
                    val r = w.root
                    if (r == null) { w.recycle(); continue }
                    val rPkg = r.packageName?.toString() ?: ""
                    if (rPkg == currentPkg) {
                        val result = AccessibilityNodeInfo.obtain(r)
                        r.recycle()
                        w.recycle()
                        Log.d(TAG, "getBestRoot: found root from window for $currentPkg")
                        return result
                    }
                    r.recycle()
                    w.recycle()
                }
                // 没找到匹配包名的，取第一个非空根
                for (w in windows) {
                    val r = w.root
                    if (r == null) { w.recycle(); continue }
                    val result = AccessibilityNodeInfo.obtain(r)
                    r.recycle()
                    w.recycle()
                    Log.d(TAG, "getBestRoot: using first available root")
                    return result
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getWindows traversal failed: ${e.message}")
        }
        Log.e(TAG, "getBestRoot: all methods failed!")
        return null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        instance = this

        // 注册 flagRetrieveInteractiveWindows 以支持 getWindows()
        try {
            val info = serviceInfo
            if (info != null) {
                info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                serviceInfo = info
            }
        } catch (_: Exception) {}

        cachedConfig = MiaoConfig.load(this)
        try { CrashHandler(this).init() } catch (_: Exception) {}
        // 通知悬浮窗更新状态
        FloatingWindowService.onStatusChanged?.invoke()
        Log.d(TAG, "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isEnabled(this)) return

        val pkg = event.packageName?.toString() ?: return

        // 微信事件日志
        if (pkg == "com.tencent.mm") {
            Log.d(TAG, "WeChat event: type=${event.eventType} pkg=$pkg")
            DebugLog.d(TAG, "WeChat event: type=${event.eventType}")
        }

        val cfg = MiaoConfig.load(this)
        cachedConfig = cfg

        if (cfg.enabledApps.isEmpty()) {
            Log.d(TAG, "enabledApps is empty, skipping")
            DebugLog.w(TAG, "enabledApps为空，请在选择应用中开启")
            return
        }
        if (pkg !in cfg.enabledApps) {
            Log.d(TAG, "$pkg not in enabledApps, skipping")
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (pkg != currentPkg) {
                    Log.d(TAG, "App switched: $currentPkg -> $pkg")
                    currentPkg = pkg
                    lastContentChangeText = ""
                }
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                handleViewClicked(event, pkg)
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                Log.d(TAG, "TEXT_CHANGED in $pkg")
                handleTextChanged(pkg, cfg)
            }
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                if (pkg in CONTENT_CHANGED_PACKAGES) {
                    Log.d(TAG, "FOCUSED in $pkg")
                    handleTextChanged(pkg, cfg)
                }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (pkg in CONTENT_CHANGED_PACKAGES) {
                    handleContentChanged(pkg, cfg)
                }
            }
        }
    }

    private fun handleContentChanged(pkg: String, cfg: MiaoConfig) {
        // 悬浮窗模式不自动处理
        if (cfg.processingMode == MiaoConfig.MODE_FLOATING_WINDOW) return
        // 标点触发模式：不在 contentChanged 里处理，等标点触发
        if (cfg.processingMode == MiaoConfig.MODE_PUNCTUATION) return
        val now = System.currentTimeMillis()
        if (now - lastContentChangeTime < CONTENT_CHANGE_THROTTLE_MS) return
        lastContentChangeTime = now

        val root = getBestRoot() ?: return
        val inp = findEditable(root, pkg) ?: run {
            root.recycle()
            return
        }
        val cs = inp.text?.toString()?.trim() ?: ""
        val hintText = try { inp.hintText?.toString()?.trim() } catch (_: Exception) { null }
        inp.recycle()
        root.recycle()

        if (cs.isEmpty() || cs == lastContentChangeText) return
        if (!hintText.isNullOrEmpty() && cs == hintText) return

        // 内容删除优化：用 lastSetMap 判断是否删除
        if (cfg.deleteOptimize) {
            val lastSet = lastSetMap[pkg] ?: ""
            if (lastSet.isNotEmpty() && cs.isNotEmpty()) {
                val isDelete = cs.length < lastSet.length
                val isModify = cs.length == lastSet.length && cs != lastSet
                if (isDelete || isModify) {
                    Log.d(TAG, "handleContentChanged: Delete optimize skip (cur=${cs.length} last=${lastSet.length})")
                    lastContentChangeText = cs
                    deleteOptimizeActive[pkg] = true
                    // 取消待处理的 debounce 和删除恢复
                    cancelDebounce()
                    cancelDeleteRecovery()
                    scheduleDeleteOptimize(pkg, cfg)
                    return
                }
            }
            deleteOptimizeActive[pkg] = false
        }

        lastContentChangeText = cs
        Log.d(TAG, "CONTENT_CHANGED detected text in $pkg: $cs")
        doProcess(pkg, cfg)
    }

    /**
     * 微信等 App 控件 ID 被混淆，按文字识别发送按钮
     */
    private fun isSendByText(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val combined = "$text|$desc"
        return WECHAT_SEND_TEXTS.any { combined.contains(it) }
    }

    private fun handleViewClicked(event: AccessibilityEvent, pkg: String) {
        val src = event.source ?: return
        val id = src.viewIdResourceName ?: ""
        val isSendButton = when (pkg) {
            "com.tencent.mm" -> WECHAT_SEND_IDS.any { id.contains(it) } || isSendByText(src)
            else -> id.contains("send") || id.contains("btn_send") || id.contains("send_btn")
        }
        src.recycle()

        if (isSendButton) {
            Log.d(TAG, "Send button clicked in $pkg, flushing debounce & resetting state")
            flushDebounce()
            resetState(pkg)
        }
    }

    private fun handleTextChanged(pkg: String, cfg: MiaoConfig) {
        val mode = cfg.processingMode
        Log.d(TAG, "handleTextChanged: pkg=$pkg mode=$mode")
        DebugLog.d(TAG, "文本变化: pkg=$pkg mode=$mode")

        // 悬浮窗模式不自动处理，由悬浮按钮触发
        if (mode == MiaoConfig.MODE_FLOATING_WINDOW) return

        if (mode == MiaoConfig.MODE_REALTIME) {
            // 内容删除优化：用 lastSetMap 判断是否删除，无需读输入框
            if (cfg.deleteOptimize) {
                val lastSet = lastSetMap[pkg] ?: ""
                if (lastSet.isNotEmpty()) {
                    // lastSetMap 是服务上次写入的文本（带喵和颜文字）
                    // 用 lastContentChangeText 作为当前文本的近似值
                    val current = lastContentChangeText
                    if (current.isNotEmpty()) {
                        val isDelete = current.length < lastSet.length
                        val isModify = current.length == lastSet.length && current != lastSet
                        if (isDelete || isModify) {
                            Log.d(TAG, "handleTextChanged: Delete optimize skip (cur=${current.length} last=${lastSet.length})")
                            deleteOptimizeActive[pkg] = true
                            // 取消待处理的 debounce 和删除恢复
                            cancelDebounce()
                            cancelDeleteRecovery()
                            scheduleDeleteOptimize(pkg, cfg)
                            return
                        }
                    }
                }
                deleteOptimizeActive[pkg] = false
            }
            if (cfg.voiceInputOptimize) {
                scheduleDebounce(pkg, cfg)
            } else {
                doProcess(pkg, cfg)
            }
            return
        }

        // 标点触发模式
        val root = getBestRoot()
        if (root == null) {
            Log.w(TAG, "handleTextChanged: getBestRoot returned null")
            return
        }
        val inp = findEditable(root, pkg)
        if (inp == null) {
            Log.w(TAG, "handleTextChanged: findEditable returned null for $pkg")
            root.recycle()
            return
        }
        val cs = inp.text
        val hintText = try { inp.hintText?.toString()?.trim() } catch (_: Exception) { null }
        inp.recycle()
        root.recycle()
        if (cs.isNullOrEmpty()) {
            Log.d(TAG, "handleTextChanged: text is null/empty")
            return
        }
        val raw = cs.toString().trim()
        if (raw.isEmpty() || (!hintText.isNullOrEmpty() && raw == hintText)) return
        if (raw.isNotEmpty() && isPunctuationEnding(raw)) {
            Log.d(TAG, "Punctuation trigger in $pkg: $raw")
            doProcess(pkg, cfg)
        }
    }

    private fun isPunctuationEnding(s: String): Boolean {
        if (s.isEmpty()) return false
        val last = s.last()
        return last == '。' || last == '！' || last == '!' ||
                last == '？' || last == '?' || last == ' ' ||
                last == '，' || last == ','
    }

    private fun scheduleDebounce(pkg: String, cfg: MiaoConfig) {
        debounceRunnable?.let { handler.removeCallbacks(it) }
        pendingPkg = pkg
        pendingConfig = cfg
        val r = Runnable {
            debounceRunnable = null
            val p = pendingPkg ?: return@Runnable
            val c = pendingConfig ?: return@Runnable
            pendingPkg = null
            pendingConfig = null
            Log.d(TAG, "Debounce fired for $p")
            doProcess(p, c)
        }
        debounceRunnable = r
        val delay = cfg.voiceDebounceMs.coerceIn(300L, 5000L)
        handler.postDelayed(r, delay)
    }

    private fun flushDebounce() {
        debounceRunnable?.let {
            handler.removeCallbacks(it)
            debounceRunnable = null
            val p = pendingPkg
            val c = pendingConfig
            pendingPkg = null
            pendingConfig = null
            if (p != null && c != null) {
                Log.d(TAG, "Debounce flush for $p")
                doProcess(p, c)
            }
        }
    }

    private fun cancelDebounce() {
        debounceRunnable?.let { handler.removeCallbacks(it) }
        debounceRunnable = null
        pendingPkg = null
        pendingConfig = null
    }

    private fun scheduleDeleteOptimize(pkg: String, cfg: MiaoConfig) {
        deleteOptimizeRunnable?.let { handler.removeCallbacks(it) }
        deleteOptimizePendingPkg = pkg
        deleteOptimizePendingCfg = cfg
        val r = Runnable {
            deleteOptimizeRunnable = null
            deleteOptimizePendingPkg = null
            deleteOptimizePendingCfg = null
            Log.d(TAG, "Delete optimize delay fired for $pkg, clearing delete mode")
            DebugLog.d(TAG, "删除优化延迟触发: $pkg，清除删除模式")
            // 超时后清除删除模式，允许下一次输入正常处理
            deleteOptimizeActive.remove(pkg)
        }
        deleteOptimizeRunnable = r
        handler.postDelayed(r, 1000L)
    }

    private fun cancelDeleteOptimize() {
        deleteOptimizeRunnable?.let { handler.removeCallbacks(it) }
        deleteOptimizeRunnable = null
        deleteOptimizePendingPkg = null
        deleteOptimizePendingCfg = null
    }

    /**
     * 删除后自动恢复：用户删除喵/颜文字后，停顿0.5秒自动调用doProcess加回
     * 每次新的删除会重置计时器
     */
    private fun scheduleDeleteRecovery(pkg: String, cfg: MiaoConfig) {
        deleteRecoveryRunnable?.let { handler.removeCallbacks(it) }
        deleteRecoveryPkg = pkg
        deleteRecoveryCfg = cfg
        val r = Runnable {
            deleteRecoveryRunnable = null
            deleteRecoveryPkg = null
            deleteRecoveryCfg = null
            Log.d(TAG, "Delete recovery fired for $pkg, re-processing")
            DebugLog.d(TAG, "删除恢复触发: $pkg，重新处理")
            doProcess(pkg, cfg)
        }
        deleteRecoveryRunnable = r
        handler.postDelayed(r, 500L)
    }

    private fun cancelDeleteRecovery() {
        deleteRecoveryRunnable?.let { handler.removeCallbacks(it) }
        deleteRecoveryRunnable = null
        deleteRecoveryPkg = null
        deleteRecoveryCfg = null
    }

    private fun resetState(pkg: String) {
        cancelDebounce()
        cancelDeleteOptimize()
        cancelDeleteRecovery()
        userOriginalMap.remove(pkg)
        lastSetMap.remove(pkg)
        lastWriteTimeMap.remove(pkg)
        lastContentChangeText = ""
        lastRawTextMap.remove(pkg)
        deleteOptimizeActive.remove(pkg)
    }

    fun processNow() {
        val cfg = cachedConfig ?: MiaoConfig.load(this)
        val pkg = currentPkg
        if (pkg.isEmpty()) return
        Log.d(TAG, "processNow triggered for $pkg")
        doProcess(pkg, cfg)
    }

    private fun doProcess(pkg: String, cfg: MiaoConfig) {
        if (processing) return
        processing = true

        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (processing) {
                Log.w(TAG, "Processing timeout, force reset")
                DebugLog.w(TAG, "处理超时，强制重置")
                processing = false
            }
        }, PROCESSING_TIMEOUT)

        val root = getBestRoot() ?: run {
            Log.w(TAG, "getBestRoot returned null for $pkg")
            DebugLog.e(TAG, "getBestRoot失败，无法获取窗口根节点")
            processing = false
            return
        }
        DebugLog.d(TAG, "getBestRoot成功")
        val inp = findEditable(root, pkg) ?: run {
            Log.w(TAG, "No editable found in $pkg")
            DebugLog.e(TAG, "找不到输入框 pkg=$pkg")
            root.recycle()
            processing = false
            return
        }
        DebugLog.d(TAG, "找到输入框 pkg=$pkg")

        val cs = inp.text
        if (cs.isNullOrEmpty()) {
            inp.recycle()
            root.recycle()
            processing = false
            resetState(pkg)
            return
        }

        val raw = cs.toString().trim()
        if (raw.isEmpty()) {
            inp.recycle()
            root.recycle()
            processing = false
            resetState(pkg)
            return
        }

        val hintText = try { inp.hintText?.toString()?.trim() } catch (_: Exception) { null }
        if (!hintText.isNullOrEmpty() && raw == hintText) {
            Log.d(TAG, "Text is hint placeholder, skipping: $raw")
            inp.recycle()
            root.recycle()
            processing = false
            resetState(pkg)
            return
        }

        val now = System.currentTimeMillis()
        val lastWriteTime = lastWriteTimeMap[pkg] ?: 0L
        val lastSet = lastSetMap[pkg] ?: ""

        if (lastWriteTime > 0 && now - lastWriteTime < ECHO_DETECT_MS && raw == lastSet) {
            Log.d(TAG, "Echo skip in $pkg")
            lastWriteTimeMap[pkg] = 0L
            lastRawTextMap[pkg] = raw  // 同步更新，避免删除检测误判
            inp.recycle()
            root.recycle()
            processing = false
            return
        }

        // 过期回声/文本未变化：raw == lastSet 但超出回声窗口，跳过处理
        // 防止随机颜文字每次不同导致的循环写入
        if (lastSet.isNotEmpty() && raw == lastSet) {
            Log.d(TAG, "Stale echo or unchanged text in $pkg, skip")
            lastWriteTimeMap[pkg] = 0L
            lastRawTextMap[pkg] = raw
            inp.recycle()
            root.recycle()
            processing = false
            return
        }

        // 内容删除优化：如果处于删除模式，跳过写入
        if (cfg.deleteOptimize && deleteOptimizeActive[pkg] == true) {
            Log.d(TAG, "doProcess: delete optimize active, skip writing")
            inp.recycle()
            root.recycle()
            processing = false
            return
        }

        // 内容删除优化（开启时生效）：文本变短说明用户在删除，不重新处理
        // 关闭时走老的 stripAll 逻辑
        if (cfg.deleteOptimize && lastSet.isNotEmpty() && raw.length < lastSet.length) {
            if (lastSet.startsWith(raw)) {
                Log.d(TAG, "Suffix deletion detected in $pkg (last=${lastSet.length} cur=${raw.length}), skip")
                lastSetMap[pkg] = raw
                lastRawTextMap[pkg] = raw
                userOriginalMap[pkg] = stripAll(raw, cfg)
                // 实时模式：启动删除恢复计时器，0.5秒后自动加回喵和颜文字
                if (cfg.processingMode == MiaoConfig.MODE_REALTIME && (cfg.enableAppend || cfg.enableRandomEmoticon)) {
                    scheduleDeleteRecovery(pkg, cfg)
                }
                inp.recycle()
                root.recycle()
                processing = false
                return
            }
        }

        // 用户输入了新文本（非删除），取消删除恢复计时器
        cancelDeleteRecovery()

        val isRealtime = cfg.processingMode == MiaoConfig.MODE_REALTIME

        val rawForTracking = if (cfg.protectSuffix && cfg.enableAppend && cfg.appendText.isNotEmpty() && raw.endsWith(cfg.appendText)) {
            raw.substring(0, raw.length - cfg.appendText.length)
        } else raw

        var userOriginal = userOriginalMap[pkg] ?: ""

        if (cfg.protectSuffix) {
            userOriginal = stripAll(rawForTracking, cfg)
        } else if (!isRealtime && lastSet.isEmpty()) {
            userOriginal = stripAll(raw, cfg)
        } else if (lastSet.isEmpty() || !raw.startsWith(lastSet)) {
            userOriginal = stripAll(raw, cfg)
        } else {
            val added = raw.substring(lastSet.length)
            userOriginal += added
        }

        userOriginalMap[pkg] = userOriginal

        if (userOriginal.isEmpty()) {
            inp.recycle()
            root.recycle()
            processing = false
            return
        }

        val isPunctuationMode = cfg.processingMode == MiaoConfig.MODE_PUNCTUATION
        val isFloatingMode = cfg.processingMode == MiaoConfig.MODE_FLOATING_WINDOW
        // QQ猫爪：仅在QQ中，标点触发和悬浮窗模式下，在原始文本最前和最后加上猫爪印记
        val catPawEnabled = cfg.qqCatPaw != "off" && (isPunctuationMode || isFloatingMode) && (pkg == "com.tencent.mobileqq" || pkg == "com.tencent.wetype")

        // 猫爪字节: 14 C7 BF 0F 43 00
        val DEFAULT_CAT_PAW = String(byteArrayOf(0x14, 0xC7.toByte(), 0xBF.toByte(), 0x0F, 0x43, 0x00), Charsets.UTF_8)
        // 根据配置选择猫爪印记：默认用内置猫爪，自定义用用户输入的值
        val catPawMark = when {
            cfg.qqCatPaw == "default" -> DEFAULT_CAT_PAW
            cfg.qqCatPaw.startsWith("hex:") -> {
                try {
                    val hex = cfg.qqCatPaw.removePrefix("hex:")
                    val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                    String(bytes, Charsets.UTF_8)
                } catch (_: Exception) { DEFAULT_CAT_PAW }
            }
            else -> cfg.qqCatPaw  // 直接使用用户输入的 emoji/字符
        }

        // 如果已有猫爪，先去掉再重新处理
        val cleanText = if (catPawEnabled && userOriginal.startsWith(catPawMark) && userOriginal.endsWith(catPawMark) && userOriginal.length > catPawMark.length * 2) {
            userOriginal.substring(catPawMark.length, userOriginal.length - catPawMark.length)
        } else {
            userOriginal
        }

        // 先进行文本处理（加喵、颜文字等）
        var processed = TextProcessor.process(cleanText, cfg, punctuationMode = isPunctuationMode, protectSuffix = cfg.protectSuffix)

        // 猫爪模式：在处理后的文本前后加猫爪印记
        var target = if (catPawEnabled) {
            "${catPawMark}${processed}${catPawMark}"
        } else {
            processed
        }

        val cursorPos = if (cfg.protectSuffix) {
            var textAfterRules = userOriginal
            for (rule in cfg.rules) {
                if (rule.from.isNotEmpty()) {
                    textAfterRules = textAfterRules.replace(rule.from, rule.to)
                }
            }
            textAfterRules.length
        } else {
            target.length
        }

        if (target != raw) {
            Log.d(TAG, "Write in $pkg: raw=[$raw] target=[$target]")
            DebugLog.d(TAG, "改写: raw=[$raw] -> target=[$target]")
            val ok = setText(inp, target, cursorPos)
            if (ok) {
                lastSetMap[pkg] = target
                lastWriteTimeMap[pkg] = System.currentTimeMillis()
                lastContentChangeText = target
                lastRawTextMap[pkg] = target  // 同步更新，避免删除检测误判
                Log.d(TAG, "Write success in $pkg")
                DebugLog.d(TAG, "写入成功")
            } else {
                Log.w(TAG, "Write FAILED in $pkg")
                DebugLog.e(TAG, "写入失败! pkg=$pkg")
            }
        } else {
            lastSetMap[pkg] = target
            DebugLog.d(TAG, "无需改写 target==raw")
        }

        inp.recycle()
        root.recycle()
        processing = false
    }

    private fun stripAll(text: String, cfg: MiaoConfig): String {
        if (text.isEmpty()) return ""
        var result = text
        val emotes = cfg.getActiveEmoticons().sortedByDescending { it.length }
        for (em in emotes) {
            if (em.isEmpty()) continue
            var idx: Int
            while (result.indexOf(em).also { idx = it } >= 0) {
                val st = if (idx > 0 && result[idx - 1] == ' ') idx - 1 else idx
                result = result.substring(0, st) + result.substring(idx + em.length)
            }
        }
        return result.replace(Regex("\\s*[\\p{S}\\p{So}\\p{Sm}\\p{Sk}\\p{P}]{3,}\\s*"), " ").trim()
    }

    private fun findEditable(node: AccessibilityNodeInfo, pkg: String): AccessibilityNodeInfo? {
        if (pkg == "com.tencent.mm") {
            // 方法1: 按已知 ID 查找
            for (editId in WECHAT_EDIT_IDS) {
                try {
                    val nodes = node.findAccessibilityNodeInfosByViewId(editId)
                    if (!nodes.isNullOrEmpty()) {
                        for (n in nodes) {
                            if (n.isEditable || isEditTextClass(n.className)) {
                                val result = AccessibilityNodeInfo.obtain(n)
                                nodes.forEach { it.recycle() }
                                Log.d(TAG, "Found WeChat edit by ID: $editId")
                                return result
                            }
                        }
                        nodes.forEach { it.recycle() }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "findAccessibilityNodeInfosByViewId failed for $editId", e)
                }
            }
            Log.d(TAG, "WeChat ID lookup failed, trying recursive search")

            // 方法2: 递归找 isEditable
            val editable = findEditableRecursive(node)
            if (editable != null) {
                Log.d(TAG, "Found WeChat edit by isEditable")
                return editable
            }

            // 方法3: 按类名找 EditText
            Log.d(TAG, "No isEditable found, trying className EditText search")
            val byClass = findEditTextByClass(node)
            if (byClass != null) {
                Log.d(TAG, "Found WeChat edit by className")
                return byClass
            }

            // 方法4: 微信可能用自定义 View，找 enabled 的可聚焦输入型节点
            Log.d(TAG, "Trying focusable+enabled node search for WeChat")
            val byFocus = findFocusableInput(node)
            if (byFocus != null) {
                Log.d(TAG, "Found WeChat edit by focusable")
                return byFocus
            }

            // 方法5: 找任何有文本且可点击/聚焦的叶子节点（微信自定义输入框兜底）
            Log.d(TAG, "Trying leaf node with text search for WeChat")
            val byLeaf = findTextInputLeaf(node)
            if (byLeaf != null) {
                Log.d(TAG, "Found WeChat edit by leaf text node")
                return byLeaf
            }

            // 方法6: 找当前聚焦的节点
            Log.d(TAG, "Trying focused node search for WeChat")
            val byFocused = findFocusedNode(node)
            if (byFocused != null) {
                Log.d(TAG, "Found WeChat edit by focused node")
                return byFocused
            }

            Log.w(TAG, "All WeChat edit detection methods failed!")
            DebugLog.e(TAG, "微信输入框查找全部失败! 打印节点树...")
            dumpNodeTree(node, 0)
            return null
        }

        val editable = findEditableRecursive(node)
        if (editable != null) return editable

        Log.d(TAG, "No isEditable found, trying className EditText search")
        return findEditTextByClass(node)
    }

    /**
     * 微信兜底：找 enabled + focusable 的节点（微信自定义输入框可能不报告 isEditable）
     */
    private fun findFocusableInput(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEnabled && node.isFocusable) {
            val cls = node.className?.toString() ?: ""
            if (cls.contains("EditText") || cls.contains("Input") ||
                cls.contains("Editor") || cls.contains("Chat") ||
                cls.contains("RichText") || cls.contains("Compose") ||
                (node.childCount == 0 && node.isClickable)) {
                return AccessibilityNodeInfo.obtain(node)
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFocusableInput(child)
            child.recycle()
            if (result != null) return result
        }
        return null
    }

    /**
     * 兜底：找任何叶子节点中看起来像输入框的（有文本、无子节点、可聚焦或可点击）
     */
    private fun findTextInputLeaf(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.childCount == 0) {
            val cls = node.className?.toString() ?: ""
            val isInputLike = cls.contains("Edit") || cls.contains("Input") ||
                    cls.contains("Text") || cls.contains("View") ||
                    cls.contains("Widget") || cls.contains("Layout")
            if (isInputLike && (node.isFocusable || node.isClickable || node.isSelected)) {
                return AccessibilityNodeInfo.obtain(node)
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findTextInputLeaf(child)
            child.recycle()
            if (result != null) return result
        }
        return null
    }

    /**
     * 兜底：找当前聚焦的节点
     */
    private fun findFocusedNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFocusedNode(child)
            child.recycle()
            if (result != null) return result
        }
        return null
    }

    /**
     * 调试用：打印节点树，同时写入 DebugLog
     */
    private fun dumpNodeTree(node: AccessibilityNodeInfo, depth: Int) {
        if (depth > 10) return
        val indent = "  ".repeat(depth)
        val cls = node.className?.toString()?.substringAfterLast('.') ?: "?"
        val id = node.viewIdResourceName ?: ""
        val editable = node.isEditable
        val enabled = node.isEnabled
        val focusable = node.isFocusable
        val clickable = node.isClickable
        val text = node.text?.toString()?.take(30) ?: ""
        val line = "${indent}$cls id=$id ed=$editable en=$enabled fc=$focusable cl=$clickable t='$text'"
        Log.d(TAG, line)
        DebugLog.d(TAG, line)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            dumpNodeTree(child, depth + 1)
            child.recycle()
        }
    }

    private fun findEditableRecursive(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return AccessibilityNodeInfo.obtain(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findEditableRecursive(child)
            child.recycle()
            if (result != null) return result
        }
        return null
    }

    private fun findEditTextByClass(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val className = node.className?.toString() ?: ""
        if (isEditTextClass(className) && node.isEnabled) {
            Log.d(TAG, "Found EditText by class: $className")
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findEditTextByClass(child)
            child.recycle()
            if (result != null) return result
        }
        return null
    }

    private fun isEditTextClass(className: CharSequence?): Boolean {
        if (className == null) return false
        val cls = className.toString()
        return cls.contains("EditText") || cls.contains("TextInputEditText") || cls.contains("ChatEditText")
    }

    /**
     * 优先 ACTION_SET_TEXT；微信等 App 可能不响应，降级到剪贴板粘贴
     */
    private fun setText(node: AccessibilityNodeInfo, text: String, cursorPos: Int): Boolean {
        return try {
            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
            }
            val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (ok) {
                try {
                    val selArgs = Bundle().apply {
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursorPos)
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursorPos)
                    }
                    node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
                } catch (_: Exception) {}
                return true
            }
            // 降级：聚焦 + 全选 + 剪贴板粘贴
            Log.d(TAG, "SET_TEXT failed, falling back to clipboard paste")
            setTextByPaste(node, text)
        } catch (e: Exception) {
            Log.e(TAG, "setText failed", e)
            false
        }
    }

    private fun setTextByPaste(node: AccessibilityNodeInfo, text: String): Boolean {
        return try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
            val oldClip = cm.primaryClip
            cm.setPrimaryClip(ClipData.newPlainText("miao", text))
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val curLen = node.text?.length ?: 0
            val selArgs = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, curLen)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
            val ok = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            // 恢复原剪贴板
            try {
                if (oldClip != null) cm.setPrimaryClip(oldClip)
                else cm.setPrimaryClip(ClipData.newPlainText("", ""))
            } catch (_: Exception) {}
            ok
        } catch (e: Exception) {
            Log.e(TAG, "setTextByPaste failed", e)
            false
        }
    }

    override fun onInterrupt() {
        processing = false
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        instance = null

        processing = false
        handler.removeCallbacksAndMessages(null)
        userOriginalMap.clear()
        lastSetMap.clear()
        lastWriteTimeMap.clear()
        lastRawTextMap.clear()
        // 通知悬浮窗更新状态
        FloatingWindowService.onStatusChanged?.invoke()
    }
}
