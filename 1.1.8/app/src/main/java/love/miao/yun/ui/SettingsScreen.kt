package love.miao.yun.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import love.miao.yun.service.FloatingButtonService
import love.miao.yun.service.FloatingWindowService
import love.miao.yun.service.LogFloatingService
import love.miao.yun.ui.theme.MiaoColors
import love.miao.yun.util.DebugLog
import love.miao.yun.util.MiaoConfig

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val config = remember { MiaoConfig.load(context) }
    var enableAppend by remember { mutableStateOf(config.enableAppend) }
    var enableRandomEmoticon by remember { mutableStateOf(config.enableRandomEmoticon) }
    var voiceInputOptimize by remember { mutableStateOf(config.voiceInputOptimize) }
    var voiceDebounceMs by remember { mutableFloatStateOf(config.voiceDebounceMs.toFloat()) }
    var processingMode by remember { mutableStateOf(config.processingMode) }
    var appendText by remember { mutableStateOf(config.appendText) }
    var rulesText by remember { mutableStateOf(MiaoConfig.rulesToString(config.rules)) }
    var customEmoticonsText by remember { mutableStateOf(config.customEmoticons.joinToString("\n")) }
    var showTestDialog by remember { mutableStateOf(false) }
    var floatingEnabled by remember { mutableStateOf(FloatingWindowService.isEnabled(context)) }
    var pendingFloatingMode by remember { mutableStateOf(false) }
    var presets by remember { mutableStateOf(MiaoConfig.getPresets(context)) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var showManagePresetsDialog by remember { mutableStateOf(false) }

    val miaoBlue = MiaoColors.blue()
    val textPrimary = MiaoColors.textPrimary()
    val textSecondary = MiaoColors.textSecondary()
    val textTertiary = MiaoColors.textTertiary()
    val cardBg = MiaoColors.cardBg()
    val bg = MiaoColors.bg()
    val border = MiaoColors.border()
    val switchOff = MiaoColors.switchOff()

    fun saveConfig() {
        config.enableAppend = enableAppend
        config.enableRandomEmoticon = enableRandomEmoticon
        config.voiceInputOptimize = voiceInputOptimize
        config.voiceDebounceMs = voiceDebounceMs.toLong()
        config.processingMode = processingMode
        config.appendText = appendText
        config.rules = rulesText.split("\n").mapNotNull { MiaoConfig.parseRule(it) }
        config.customEmoticons = customEmoticonsText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        config.save(context)
    }

    // 监听从权限页面返回
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (pendingFloatingMode && android.provider.Settings.canDrawOverlays(context)) {
                    pendingFloatingMode = false
                    FloatingButtonService.start(context)
                    Toast.makeText(context, "悬浮窗权限已授予", Toast.LENGTH_SHORT).show()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 离开页面时自动保存（包括系统返回键）
    DisposableEffect(Unit) {
        onDispose {
            // 检查调试日志暗号
            val emoticons = customEmoticonsText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            if (emoticons.contains("5201110")) {
                // 激活调试日志模式，移除暗号
                DebugLog.setEnabled(context, true)
                customEmoticonsText = emoticons.filter { it != "5201110" }.joinToString("\n")
                config.customEmoticons = customEmoticonsText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                saveConfig()
                // 启动日志悬浮窗
                if (android.provider.Settings.canDrawOverlays(context)) {
                    LogFloatingService.start(context)
                }
                android.widget.Toast.makeText(context, "调试日志模式已激活", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                saveConfig()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("更多设置") },
                navigationIcon = {
                    IconButton(onClick = { saveConfig(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MiaoColors.topBar())
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bg)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Processing mode section
            Text("处理模式", fontSize = 13.sp, color = textSecondary, fontWeight = FontWeight.Medium)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, border)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Speed, null, tint = miaoBlue, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("处理模式", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                    }
                    Spacer(Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ModeChip("标点触发", processingMode == MiaoConfig.MODE_PUNCTUATION, miaoBlue, border) {
                                processingMode = MiaoConfig.MODE_PUNCTUATION
                                manageFloatingButton(context, false)
                                saveConfig()
                            }
                            ModeChip("实时处理", processingMode == MiaoConfig.MODE_REALTIME, miaoBlue, border) {
                                processingMode = MiaoConfig.MODE_REALTIME
                                manageFloatingButton(context, false)
                                saveConfig()
                            }
                            ModeChip("悬浮窗", processingMode == MiaoConfig.MODE_FLOATING_WINDOW, miaoBlue, border) {
                                processingMode = MiaoConfig.MODE_FLOATING_WINDOW
                                if (android.provider.Settings.canDrawOverlays(context)) {
                                    manageFloatingButton(context, true)
                                    saveConfig()
                                } else {
                                    pendingFloatingMode = true
                                    Toast.makeText(context, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
                                    try {
                                        context.startActivity(
                                            android.content.Intent(
                                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                android.net.Uri.parse("package:${context.packageName}")
                                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    } catch (_: Exception) {
                                        pendingFloatingMode = false
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when (processingMode) {
                            MiaoConfig.MODE_PUNCTUATION -> "标点触发：输入标点符号后自动追加颜文字"
                            MiaoConfig.MODE_FLOATING_WINDOW -> "悬浮窗：输入完成后点击悬浮窗添加喵和颜文字"
                            else -> "实时处理：输入时自动追加文本和颜文字（默认）"
                        },
                        fontSize = 12.sp,
                        color = textSecondary,
                        lineHeight = 18.sp
                    )
                }
            }

            // Function toggles
            Text("功能开关", fontSize = 13.sp, color = textSecondary, fontWeight = FontWeight.Medium)

            // Append toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, border)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TextFields, null, tint = MiaoColors.Green, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("断句追加", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                                Text("在每句话后追加指定文本", fontSize = 12.sp, color = textSecondary)
                            }
                        }
                        Switch(
                            checked = enableAppend,
                            onCheckedChange = {
                                enableAppend = it
                                saveConfig()
                            },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = miaoBlue,
                                checkedThumbColor = Color.White,
                                uncheckedTrackColor = switchOff,
                                uncheckedThumbColor = Color.White
                            )
                        )
                    }
                    if (enableAppend) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = appendText,
                            onValueChange = {
                                appendText = it
                                config.appendText = it
                                saveConfig()
                            },
                            label = { Text("追加文本") },
                            placeholder = { Text("例如：喵") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = miaoBlue,
                                unfocusedBorderColor = border
                            )
                        )
                    }
                }
            }

            // Emoticon toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, border)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EmojiEmotions, null, tint = MiaoColors.Orange, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("句末颜文字", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                            Text("在消息末尾附加随机颜文字", fontSize = 12.sp, color = textSecondary)
                        }
                    }
                    Switch(
                        checked = enableRandomEmoticon,
                        onCheckedChange = {
                            enableRandomEmoticon = it
                            saveConfig()
                        },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = miaoBlue,
                            checkedThumbColor = Color.White,
                            uncheckedTrackColor = switchOff,
                            uncheckedThumbColor = Color.White
                        )
                    )
                }
            }

            // 语音输入优化
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, border)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Mic, null, tint = MiaoColors.Teal, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("语音输入优化", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                                Text("避免语音输入时中途被打断", fontSize = 12.sp, color = textSecondary)
                            }
                        }
                        Switch(
                            checked = voiceInputOptimize,
                            onCheckedChange = {
                                voiceInputOptimize = it
                                saveConfig()
                            },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = miaoBlue,
                                checkedThumbColor = Color.White,
                                uncheckedTrackColor = switchOff,
                                uncheckedThumbColor = Color.White
                            )
                        )
                    }
                    if (voiceInputOptimize) {
                        val minVal = 300f
                        val maxVal = 5000f
                        var dragProgress by remember { mutableFloatStateOf((voiceDebounceMs - minVal) / (maxVal - minVal)) }
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("等待时间", fontSize = 13.sp, color = textTertiary)
                            Text(
                                "${"%.1f".format((minVal + dragProgress * (maxVal - minVal)) / 1000f)}秒",
                                fontSize = 14.sp,
                                color = miaoBlue,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "语音说完后等待这么久再处理，太短会打断语音，太长会感觉延迟",
                            fontSize = 11.sp,
                            color = textSecondary,
                            lineHeight = 16.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures(
                                        onDragStart = { offset ->
                                            val w = size.width.toFloat()
                                            if (w > 0) {
                                                dragProgress = (offset.x / w).coerceIn(0f, 1f)
                                            }
                                        },
                                        onHorizontalDrag = { _, dragAmount ->
                                            val w = size.width.toFloat()
                                            if (w > 0) {
                                                dragProgress = (dragProgress + dragAmount / w).coerceIn(0f, 1f)
                                            }
                                        },
                                        onDragEnd = {
                                            val raw = minVal + dragProgress * (maxVal - minVal)
                                            voiceDebounceMs = (raw / 100f).toInt() * 100f
                                            dragProgress = (voiceDebounceMs - minVal) / (maxVal - minVal)
                                            saveConfig()
                                        }
                                    )
                                },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(border)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(dragProgress.coerceAtLeast(0.01f))
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(miaoBlue)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(dragProgress)
                                    .wrapContentWidth(Alignment.End)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .shadow(4.dp, RoundedCornerShape(12.dp))
                                        .background(miaoBlue, RoundedCornerShape(12.dp))
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("0.3秒", fontSize = 11.sp, color = textSecondary)
                            Text("5秒", fontSize = 11.sp, color = textSecondary)
                        }
                    }
                }
            }

            // Text replacement rules
            Text("悬浮窗", fontSize = 13.sp, color = textSecondary, fontWeight = FontWeight.Medium)

            // Floating window toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, border)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PictureInPicture, null, tint = MiaoColors.Teal, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("悬浮窗", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                                Text("在屏幕上显示状态指示器", fontSize = 12.sp, color = textSecondary)
                            }
                        }
                        Switch(
                            checked = floatingEnabled,
                            onCheckedChange = {
                                floatingEnabled = it
                                if (it) {
                                    if (!android.provider.Settings.canDrawOverlays(context)) {
                                        Toast.makeText(context, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
                                        try {
                                            context.startActivity(
                                                android.content.Intent(
                                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                    android.net.Uri.parse("package:${context.packageName}")
                                                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        } catch (_: Exception) {}
                                        floatingEnabled = false
                                    } else {
                                        FloatingWindowService.start(context)
                                    }
                                } else {
                                    FloatingWindowService.stop(context)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = miaoBlue,
                                checkedThumbColor = Color.White,
                                uncheckedTrackColor = switchOff,
                                uncheckedThumbColor = Color.White
                            )
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "开启后屏幕上会出现可拖动的小窗口，点击可快速切换开关状态",
                        fontSize = 12.sp,
                        color = textSecondary,
                        lineHeight = 18.sp
                    )
                }
            }

            // Text replacement rules
            Text("文本替换规则", fontSize = 13.sp, color = textSecondary, fontWeight = FontWeight.Medium)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, border)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SwapHoriz, null, tint = Color(0xFF1E88E5), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("替换规则", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "每行一条，格式：原词=替换词\n支持 = ＝ → 作为分隔符\n长词优先匹配，如\"吃饭\"优先于\"吃\"",
                        fontSize = 12.sp,
                        color = textSecondary,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = rulesText,
                        onValueChange = { rulesText = it },
                        placeholder = { Text("我=本喵\n你=主人") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = miaoBlue,
                            unfocusedBorderColor = border
                        )
                    )
                    Spacer(Modifier.height(12.dp))
                    // 预设按钮行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showSavePresetDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, miaoBlue),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.BookmarkAdd, null, tint = miaoBlue, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("保存为预设", fontSize = 13.sp, color = miaoBlue)
                        }
                        if (presets.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { showManagePresetsDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, textSecondary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.FolderOpen, null, tint = textTertiary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("管理预设", fontSize = 13.sp, color = textTertiary)
                            }
                        }
                    }
                    // 预设快捷标签
                    if (presets.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("快速加载：", fontSize = 12.sp, color = textSecondary)
                        Spacer(Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (preset in presets) {
                                AssistChip(
                                    onClick = {
                                        rulesText = preset.rulesText
                                        saveConfig()
                                        Toast.makeText(context, "已加载预设\"${preset.name}\"", Toast.LENGTH_SHORT).show()
                                    },
                                    label = { Text(preset.name, fontSize = 13.sp) },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, border),
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = cardBg
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Custom emoticons
            Text("自定义颜文字", fontSize = 13.sp, color = textSecondary, fontWeight = FontWeight.Medium)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, border)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EmojiSymbols, null, tint = MiaoColors.Purple, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("自定义颜文字", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "每行一个，留空则使用内置库（50+个）",
                        fontSize = 12.sp,
                        color = textSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customEmoticonsText,
                        onValueChange = { customEmoticonsText = it },
                        placeholder = { Text("(=^w^=)\nฅ^•ﻌ•^ฅ") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = miaoBlue,
                            unfocusedBorderColor = border
                        )
                    )
                }
            }

            // Test button
            Button(
                onClick = { showTestDialog = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = miaoBlue)
            ) {
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("测试当前配置", fontSize = 16.sp, color = Color.White)
            }

            // Save config button
            Button(
                onClick = {
                    saveConfig()
                    Toast.makeText(context, "配置已保存并生效", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MiaoColors.Green)
            ) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("保存配置", fontSize = 16.sp, color = Color.White)
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // Test dialog
    if (showTestDialog) {
        val testCfg = config.clone()
        testCfg.appendText = appendText
        testCfg.rules = rulesText.split("\n").mapNotNull { MiaoConfig.parseRule(it) }
        testCfg.customEmoticons = customEmoticonsText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val sample = "今天我很好，你准备好了吗？我们去公园玩吧"
        val processed = love.miao.yun.util.TextProcessor.process(sample, testCfg, punctuationMode = testCfg.processingMode == MiaoConfig.MODE_PUNCTUATION)
        AlertDialog(
            onDismissRequest = { showTestDialog = false },
            containerColor = cardBg,
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(20.dp),
            title = { Text("效果预览", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("断句追加：${if (testCfg.enableAppend) "开（${testCfg.appendText}）" else "关"}")
                    Text("句末颜文字：${if (testCfg.enableRandomEmoticon) "开" else "关"}")
                    Text("替换规则：${testCfg.rules.size} 条")
                    Text("自定义颜文字：${if (testCfg.customEmoticons.isNotEmpty()) "${testCfg.customEmoticons.size}个" else "使用内置"}")
                    Spacer(Modifier.height(12.dp))
                    Text("原始：", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text(sample, fontSize = 14.sp, color = textSecondary)
                    Spacer(Modifier.height(8.dp))
                    Text("处理后：", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text(processed, fontSize = 14.sp, color = miaoBlue)
                }
            },
            confirmButton = {
                TextButton(onClick = { showTestDialog = false }) {
                    Text("好的", color = miaoBlue)
                }
            }
        )
    }

    // 保存预设弹窗
    if (showSavePresetDialog) {
        var presetName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            containerColor = cardBg,
            shape = RoundedCornerShape(20.dp),
            title = { Text("保存为预设", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("将当前替换规则保存为预设，方便以后快速加载", fontSize = 14.sp, color = textTertiary)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it },
                        label = { Text("预设名称") },
                        placeholder = { Text("例如：卖萌模式") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = miaoBlue,
                            unfocusedBorderColor = border
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = presetName.trim()
                        if (name.isNotEmpty()) {
                            if (presets.any { it.name == name }) {
                                Toast.makeText(context, "预设\"$name\"已存在，请换个名称", Toast.LENGTH_SHORT).show()
                            } else {
                                MiaoConfig.savePreset(context, name, rulesText)
                                presets = MiaoConfig.getPresets(context)
                                showSavePresetDialog = false
                                Toast.makeText(context, "预设\"$name\"已保存", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) { Text("保存", color = miaoBlue) }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) {
                    Text("取消", color = textSecondary)
                }
            }
        )
    }

    // 管理预设弹窗
    if (showManagePresetsDialog) {
        AlertDialog(
            onDismissRequest = { showManagePresetsDialog = false },
            containerColor = cardBg,
            shape = RoundedCornerShape(20.dp),
            title = { Text("管理预设", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    if (presets.isEmpty()) {
                        Text("暂无保存的预设", fontSize = 14.sp, color = textSecondary)
                    } else {
                        Text("点击加载，长按删除", fontSize = 12.sp, color = textSecondary)
                        Spacer(Modifier.height(8.dp))
                        for (preset in presets) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = bg)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(preset.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                                        val ruleCount = preset.rulesText.split("\n").filter { line -> line.isNotBlank() }.size
                                        Text("$ruleCount 条规则", fontSize = 12.sp, color = textSecondary)
                                    }
                                    Row {
                                        IconButton(
                                            onClick = {
                                                rulesText = preset.rulesText
                                                saveConfig()
                                                showManagePresetsDialog = false
                                                Toast.makeText(context, "已加载预设\"${preset.name}\"", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.FileDownload, "加载", tint = miaoBlue, modifier = Modifier.size(20.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                MiaoConfig.deletePreset(context, preset.name)
                                                presets = MiaoConfig.getPresets(context)
                                                Toast.makeText(context, "已删除预设\"${preset.name}\"", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, "删除", tint = MiaoColors.Red, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showManagePresetsDialog = false }) {
                    Text("关闭", color = miaoBlue)
                }
            }
        )
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, miaoBlue: Color, border: Color, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 13.sp) },
        leadingIcon = {
            if (selected) Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
        },
        border = FilterChipDefaults.filterChipBorder(
            borderColor = border,
            selectedBorderColor = miaoBlue,
            enabled = true,
            selected = selected
        ),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = miaoBlue.copy(alpha = 0.12f),
            selectedLabelColor = miaoBlue
        )
    )
}

private fun manageFloatingButton(context: android.content.Context, enable: Boolean) {
    if (enable) {
        if (android.provider.Settings.canDrawOverlays(context)) {
            love.miao.yun.service.FloatingButtonService.start(context)
        }
    } else {
        love.miao.yun.service.FloatingButtonService.stop(context)
    }
}
