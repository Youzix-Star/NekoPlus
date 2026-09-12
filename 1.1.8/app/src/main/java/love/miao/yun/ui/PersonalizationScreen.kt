package love.miao.yun.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import love.miao.yun.ui.theme.MiaoColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import love.miao.yun.util.DebugLog
import love.miao.yun.util.MiaoConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalizationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val config = remember { MiaoConfig.load(context) }

    var spaceNoMiao by remember { mutableStateOf(config.spaceNoMiao) }
    var emoticonTriggerEnabled by remember { mutableStateOf(config.emoticonTriggerMode != MiaoConfig.EMOTICON_TRIGGER_OFF) }
    var emoticonTriggerMode by remember { mutableStateOf(config.emoticonTriggerMode) }
    var emoticonProbability by remember { mutableFloatStateOf(config.emoticonProbability) }
    var emoticonInterval by remember { mutableIntStateOf(config.emoticonInterval) }
    var deleteOptimize by remember { mutableStateOf(config.deleteOptimize) }
    var qqCatPaw by remember { mutableStateOf(config.qqCatPaw) }
    // 兼容旧版 Boolean 格式
    LaunchedEffect(Unit) {
        if (qqCatPaw == "true") qqCatPaw = "default"
        else if (qqCatPaw == "false") qqCatPaw = "off"
    }
    var punctuationOptimize by remember { mutableStateOf(config.punctuationOptimize) }

    // 在线预设下载
    val disclaimerPrefs = remember { context.getSharedPreferences("online_preset_disclaimer", android.content.Context.MODE_PRIVATE) }
    var showOnlinePresets by remember { mutableStateOf(false) }
    var disclaimerAccepted by remember { mutableStateOf(disclaimerPrefs.getBoolean("accepted", false)) }
    var showDisclaimerDialog by remember { mutableStateOf(false) }
    var onlinePresetList by remember { mutableStateOf<List<OnlinePreset>>(emptyList()) }
    var isLoadingPresets by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var previewPreset by remember { mutableStateOf<OnlinePreset?>(null) }
    var presets by remember { mutableStateOf(MiaoConfig.getPresets(context)) }
    var selectedRoute by remember { mutableStateOf("github") } // "github" or "tencent" or "custom"
    var customPresetUrl by remember { mutableStateOf("") }

    val miaoBlue = MiaoColors.blue()
    val textPrimary = MiaoColors.textPrimary()
    val textSecondary = MiaoColors.textSecondary()
    val textTertiary = MiaoColors.textTertiary()
    val cardBg = MiaoColors.cardBg()
    val bg = MiaoColors.bg()
    val border = MiaoColors.border()
    val switchOff = MiaoColors.switchOff()

    fun saveConfig() {
        config.spaceNoMiao = spaceNoMiao
        config.emoticonTriggerMode = if (emoticonTriggerEnabled) emoticonTriggerMode else MiaoConfig.EMOTICON_TRIGGER_OFF
        config.emoticonProbability = emoticonProbability
        config.emoticonInterval = emoticonInterval
        config.deleteOptimize = deleteOptimize
        config.qqCatPaw = qqCatPaw
        config.punctuationOptimize = punctuationOptimize
        config.save(context)
    }

    DisposableEffect(Unit) {
        onDispose { saveConfig() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("个性化设置") },
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
            // ── 空格不加喵 ──
            Text("文本处理", fontSize = 13.sp, color = textSecondary, fontWeight = FontWeight.Medium)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, border)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SpaceBar, null, tint = MiaoColors.Green, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("空格不加喵", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                                Text("空格分隔的词语中间不加喵", fontSize = 12.sp, color = textSecondary)
                            }
                        }
                        Switch(
                            checked = spaceNoMiao,
                            onCheckedChange = { spaceNoMiao = it; saveConfig() },
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
                        if (spaceNoMiao) "开启后:\"你好 我是开发者\" → \"你好 我是开发者喵\""
                        else "关闭后:\"你好 我是开发者\" → \"你好喵 我是开发者喵\"",
                        fontSize = 12.sp, color = textSecondary, lineHeight = 18.sp
                    )
                }
            }

            // ── 标点模式优化 ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, border)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoFixHigh, null, tint = Color(0xFF1E88E5), modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("标点模式优化", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                                Text("仅标点模式生效", fontSize = 12.sp, color = textSecondary)
                            }
                        }
                        Switch(
                            checked = punctuationOptimize,
                            onCheckedChange = { punctuationOptimize = it; saveConfig() },
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
                        if (punctuationOptimize) "开启后：\"测试，你好。喵\" → \"测试，你好 喵\""
                        else "关闭后：\"测试，你好。喵\"（保留原始标点）",
                        fontSize = 12.sp, color = textSecondary, lineHeight = 18.sp
                    )
                }
            }

            // ── 颜文字随机触发 ──
            Text("颜文字触发", fontSize = 13.sp, color = textSecondary, fontWeight = FontWeight.Medium)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, border)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.EmojiEmotions, null, tint = MiaoColors.Orange, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("颜文字随机触发", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                                Text("控制句末颜文字的触发条件", fontSize = 12.sp, color = textSecondary)
                            }
                        }
                        Switch(
                            checked = emoticonTriggerEnabled,
                            onCheckedChange = {
                                emoticonTriggerEnabled = it
                                if (it && emoticonTriggerMode == MiaoConfig.EMOTICON_TRIGGER_OFF) {
                                    emoticonTriggerMode = MiaoConfig.EMOTICON_TRIGGER_RANDOM
                                }
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

                    if (emoticonTriggerEnabled) {
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ModeChip("概率", emoticonTriggerMode == MiaoConfig.EMOTICON_TRIGGER_PROBABILITY, miaoBlue, border) {
                                emoticonTriggerMode = MiaoConfig.EMOTICON_TRIGGER_PROBABILITY; saveConfig()
                            }
                            ModeChip("随机", emoticonTriggerMode == MiaoConfig.EMOTICON_TRIGGER_RANDOM, miaoBlue, border) {
                                emoticonTriggerMode = MiaoConfig.EMOTICON_TRIGGER_RANDOM; saveConfig()
                            }
                            ModeChip("间隔", emoticonTriggerMode == MiaoConfig.EMOTICON_TRIGGER_INTERVAL, miaoBlue, border) {
                                emoticonTriggerMode = MiaoConfig.EMOTICON_TRIGGER_INTERVAL; saveConfig()
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when (emoticonTriggerMode) {
                                MiaoConfig.EMOTICON_TRIGGER_PROBABILITY -> "概率:按设定概率随机触发颜文字"
                                MiaoConfig.EMOTICON_TRIGGER_RANDOM -> "随机:50%概率触发颜文字"
                                MiaoConfig.EMOTICON_TRIGGER_INTERVAL -> "间隔:每间隔几句触发一次颜文字"
                                else -> ""
                            },
                            fontSize = 12.sp, color = textSecondary, lineHeight = 18.sp
                        )

                        // ── 概率模式 拖动滑条 ──
                        if (emoticonTriggerMode == MiaoConfig.EMOTICON_TRIGGER_PROBABILITY) {
                            Spacer(Modifier.height(16.dp))
                            val minVal = 5f
                            val maxVal = 100f
                            var dragProgress by remember { mutableFloatStateOf((emoticonProbability * 100f - minVal) / (maxVal - minVal)) }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("触发概率", fontSize = 13.sp, color = textTertiary)
                                Text("${(minVal + dragProgress * (maxVal - minVal)).toInt()}%", fontSize = 14.sp, color = miaoBlue, fontWeight = FontWeight.Medium)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("概率越高,颜文字出现越频繁", fontSize = 11.sp, color = textSecondary, lineHeight = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .pointerInput(Unit) {
                                        detectHorizontalDragGestures(
                                            onDragStart = { offset ->
                                                val w = size.width.toFloat()
                                                if (w > 0) dragProgress = (offset.x / w).coerceIn(0f, 1f)
                                            },
                                            onHorizontalDrag = { _, dragAmount ->
                                                val w = size.width.toFloat()
                                                if (w > 0) dragProgress = (dragProgress + dragAmount / w).coerceIn(0f, 1f)
                                            },
                                            onDragEnd = {
                                                val raw = minVal + dragProgress * (maxVal - minVal)
                                                val stepped = (raw.toInt() / 5) * 5f
                                                emoticonProbability = (stepped / 100f).coerceIn(0.05f, 1f)
                                                dragProgress = (stepped - minVal) / (maxVal - minVal)
                                                saveConfig()
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Box(modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(border))
                                Box(modifier = Modifier.fillMaxWidth(dragProgress.coerceAtLeast(0.01f)).height(12.dp).clip(RoundedCornerShape(6.dp)).background(miaoBlue))
                                Box(modifier = Modifier.fillMaxWidth(dragProgress).wrapContentWidth(Alignment.End)) {
                                    Box(modifier = Modifier.size(24.dp).shadow(4.dp, RoundedCornerShape(12.dp)).background(miaoBlue, RoundedCornerShape(12.dp)))
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("5%", fontSize = 11.sp, color = textSecondary)
                                Text("100%", fontSize = 11.sp, color = textSecondary)
                            }
                        }

                        // ── 间隔模式 拖动滑条 ──
                        if (emoticonTriggerMode == MiaoConfig.EMOTICON_TRIGGER_INTERVAL) {
                            Spacer(Modifier.height(16.dp))
                            val minVal = 2f
                            val maxVal = 20f
                            var dragProgress by remember { mutableFloatStateOf((emoticonInterval.toFloat() - minVal) / (maxVal - minVal)) }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("间隔句数", fontSize = 13.sp, color = textTertiary)
                                Text("每 ${(minVal + dragProgress * (maxVal - minVal)).toInt()} 句触发一次", fontSize = 14.sp, color = miaoBlue, fontWeight = FontWeight.Medium)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("数值越大,颜文字出现越少", fontSize = 11.sp, color = textSecondary, lineHeight = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .pointerInput(Unit) {
                                        detectHorizontalDragGestures(
                                            onDragStart = { offset ->
                                                val w = size.width.toFloat()
                                                if (w > 0) dragProgress = (offset.x / w).coerceIn(0f, 1f)
                                            },
                                            onHorizontalDrag = { _, dragAmount ->
                                                val w = size.width.toFloat()
                                                if (w > 0) dragProgress = (dragProgress + dragAmount / w).coerceIn(0f, 1f)
                                            },
                                            onDragEnd = {
                                                val raw = minVal + dragProgress * (maxVal - minVal)
                                                emoticonInterval = raw.toInt().coerceIn(2, 20)
                                                dragProgress = (emoticonInterval.toFloat() - minVal) / (maxVal - minVal)
                                                saveConfig()
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Box(modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(border))
                                Box(modifier = Modifier.fillMaxWidth(dragProgress.coerceAtLeast(0.01f)).height(12.dp).clip(RoundedCornerShape(6.dp)).background(miaoBlue))
                                Box(modifier = Modifier.fillMaxWidth(dragProgress).wrapContentWidth(Alignment.End)) {
                                    Box(modifier = Modifier.size(24.dp).shadow(4.dp, RoundedCornerShape(12.dp)).background(miaoBlue, RoundedCornerShape(12.dp)))
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("2句", fontSize = 11.sp, color = textSecondary)
                                Text("20句", fontSize = 11.sp, color = textSecondary)
                            }
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text("关闭:句末颜文字始终触发(需先开启句末颜文字)", fontSize = 12.sp, color = textSecondary, lineHeight = 18.sp)
                    }
                }
            }

            // ── 内容删除优化 ──
            Text("输入优化", fontSize = 13.sp, color = textSecondary, fontWeight = FontWeight.Medium)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, border)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.EditOff, null, tint = MiaoColors.Red, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("内容删除优化", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                                Text("删除或修改内容时暂停自动处理", fontSize = 12.sp, color = textSecondary)
                            }
                        }
                        Switch(
                            checked = deleteOptimize,
                            onCheckedChange = { deleteOptimize = it; saveConfig() },
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
                        "开启后(仅实时模式生效):\n• 删除内容时不会自动加喵和颜文字\n• 修改内容时不会自动加喵和颜文字\n• 输入框有内容且未修改时,1秒后恢复自动处理",
                        fontSize = 12.sp, color = textSecondary, lineHeight = 18.sp
                    )
                }
            }

            // ── QQ猫爪 ──
            Text("趣味功能", fontSize = 13.sp, color = textSecondary, fontWeight = FontWeight.Medium)

            var catPawEnabled by remember { mutableStateOf(qqCatPaw != "off") }
            var showCatPawCustomDialog by remember { mutableStateOf(false) }
            var catPawCustomInput by remember { mutableStateOf("") }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, border)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Pets, null, tint = Color(0xFFFF6F00), modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("QQ猫爪", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                                Text("句子首尾加上猫爪印记", fontSize = 12.sp, color = textSecondary)
                            }
                        }
                        Switch(
                            checked = catPawEnabled,
                            onCheckedChange = {
                                catPawEnabled = it
                                if (it) {
                                    if (qqCatPaw == "off") qqCatPaw = "default"
                                } else {
                                    qqCatPaw = "off"
                                }
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

                    if (catPawEnabled) {
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ModeChip("默认猫爪", qqCatPaw == "default", miaoBlue, border) {
                                qqCatPaw = "default"; saveConfig()
                            }
                            ModeChip("自定义", qqCatPaw != "off" && qqCatPaw != "default", miaoBlue, border) {
                                catPawCustomInput = if (qqCatPaw.startsWith("hex:")) qqCatPaw.removePrefix("hex:") else if (qqCatPaw != "off" && qqCatPaw != "default") qqCatPaw else ""
                                showCatPawCustomDialog = true
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when (qqCatPaw) {
                                "default" -> "仅在QQ中，标点触发和悬浮窗模式下，在句子最前和最后加上默认猫爪印记"
                                else -> "自定义: $qqCatPaw"
                            },
                            fontSize = 12.sp, color = textSecondary, lineHeight = 18.sp
                        )
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text("关闭:不添加猫爪印记", fontSize = 12.sp, color = textSecondary, lineHeight = 18.sp)
                    }
                }
            }

            // 自定义猫爪弹窗
            if (showCatPawCustomDialog) {
                AlertDialog(
                    onDismissRequest = { showCatPawCustomDialog = false },
                    containerColor = cardBg,
                    shape = RoundedCornerShape(20.dp),
                    title = { Text("自定义猫爪印记", fontWeight = FontWeight.Bold, color = textPrimary) },
                    text = {
                        Column {
                            Text("输入自定义的猫爪效果，支持：", fontSize = 14.sp, color = textTertiary)
                            Spacer(Modifier.height(8.dp))
                            Text("• Emoji 或任意字符，如 🐾 或 ♡", fontSize = 13.sp, color = textSecondary)
                            Text("• 十六进制（前缀 hex:），如 hex:E280BF", fontSize = 13.sp, color = textSecondary)
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = catPawCustomInput,
                                onValueChange = { catPawCustomInput = it },
                                label = { Text("输入 emoji 或 hex:XXXX") },
                                placeholder = { Text("🐾") },
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
                        TextButton(onClick = {
                            val input = catPawCustomInput.trim()
                            if (input.isNotEmpty()) {
                                qqCatPaw = if (input.startsWith("hex:")) input else input
                                saveConfig()
                                showCatPawCustomDialog = false
                            }
                        }) { Text("确定", color = miaoBlue) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCatPawCustomDialog = false }) {
                            Text("取消", color = textSecondary)
                        }
                    }
                )
            }

            // ── 替换词预设下载 ──
            Text("在线预设", fontSize = 13.sp, color = textSecondary, fontWeight = FontWeight.Medium)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, border),
                onClick = {
                    if (disclaimerAccepted) {
                        showOnlinePresets = true
                    } else {
                        showDisclaimerDialog = true
                    }
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDownload, null, tint = Color(0xFF00897B), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("替换词预设下载", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                            Text("从云端下载社区预设规则", fontSize = 12.sp, color = textSecondary)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = textSecondary)
                }
            }

            // ── 数据保存 ──
            Text("数据保存", fontSize = 13.sp, color = textSecondary, fontWeight = FontWeight.Medium)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, border)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    // ── 数据保存按钮 ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Save, null, tint = Color(0xFF1E88E5), modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("数据保存", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                                Text("保存数据到下载目录/miao文件夹", fontSize = 12.sp, color = textSecondary)
                            }
                        }
                        var saveStatus by remember { mutableStateOf("") }
                        val saveLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.StartActivityForResult()
                        ) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                if (Environment.isExternalStorageManager()) {
                                    val result = saveMiaoData(context, config)
                                    saveStatus = result
                                    Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "需要存储权限才能保存数据", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        val savePermissionLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.RequestPermission()
                        ) { granted ->
                            if (granted) {
                                val result = saveMiaoData(context, config)
                                saveStatus = result
                                Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "需要存储权限才能保存数据", Toast.LENGTH_SHORT).show()
                            }
                        }
                        Button(
                            onClick = {
                                if (hasStoragePermission(context)) {
                                    val result = saveMiaoData(context, config)
                                    saveStatus = result
                                    Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                                } else {
                                    requestStoragePermission(context, savePermissionLauncher, saveLauncher)
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("保存", fontSize = 13.sp, color = Color.White)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // ── 生效数据按钮 ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FileDownload, null, tint = MiaoColors.Green, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("生效数据", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                                Text("从下载目录/miao文件夹导入数据", fontSize = 12.sp, color = textSecondary)
                            }
                        }
                        var loadStatus by remember { mutableStateOf("") }
                        val loadLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.StartActivityForResult()
                        ) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                if (Environment.isExternalStorageManager()) {
                                    val result = loadMiaoData(context, config)
                                    loadStatus = result
                                    Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "需要存储权限才能导入数据", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        val loadPermissionLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.RequestPermission()
                        ) { granted ->
                            if (granted) {
                                val result = loadMiaoData(context, config)
                                loadStatus = result
                                Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "需要存储权限才能导入数据", Toast.LENGTH_SHORT).show()
                            }
                        }
                        Button(
                            onClick = {
                                if (hasStoragePermission(context)) {
                                    val result = loadMiaoData(context, config)
                                    loadStatus = result
                                    Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                                } else {
                                    requestStoragePermission(context, loadPermissionLauncher, loadLauncher)
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MiaoColors.Green),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("导入", fontSize = 13.sp, color = Color.White)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // ── 免责声明弹窗 ──
    if (showDisclaimerDialog) {
        AlertDialog(
            onDismissRequest = { showDisclaimerDialog = false },
            containerColor = cardBg,
            shape = RoundedCornerShape(20.dp),
            title = { Text("联网权限说明", fontWeight = FontWeight.Bold, color = textPrimary) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "本功能需要联网权限，仅用于以下用途：\n\n" +
                                "• 从 GitHub/腾讯云下载社区分享的替换词预设\n" +
                                "• 从自定义 JSON 链接下载预设\n" +
                                "• 仅在您主动点击时才会联网\n" +
                                "• 不收集、不上传任何个人信息\n" +
                                "• 不会后台自动联网\n\n" +
                                "数据来源：github.com/Xiao-youyu/miaoyun",
                        fontSize = 14.sp,
                        color = textTertiary,
                        lineHeight = 22.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    disclaimerPrefs.edit().putBoolean("accepted", true).apply()
                    disclaimerAccepted = true
                    showDisclaimerDialog = false
                    showOnlinePresets = true
                }) { Text("确定", color = miaoBlue) }
            },
            dismissButton = {
                TextButton(onClick = { showDisclaimerDialog = false }) {
                    Text("取消", color = textSecondary)
                }
            }
        )
    }

    // ── 在线预设列表弹窗 ──
    if (showOnlinePresets) {
        // 加载预设列表
        LaunchedEffect(selectedRoute) {
            onlinePresetList = emptyList()
            isLoadingPresets = true
            loadError = null
            try {
                val list = withContext(Dispatchers.IO) {
                    when (selectedRoute) {
                        "github" -> loadOnlinePresetList()
                        "tencent" -> loadTencentPresetList()
                        "custom" -> loadCustomPresetList(customPresetUrl)
                        else -> emptyList()
                    }
                }
                onlinePresetList = list
                if (list.isEmpty()) {
                    loadError = "未找到可用的预设文件"
                }
            } catch (e: Exception) {
                val routeName = when (selectedRoute) {
                    "github" -> "GitHub"
                    "tencent" -> "腾讯云"
                    "custom" -> "自定义"
                    else -> ""
                }
                loadError = "[$routeName] 加载失败: ${e.message ?: "请检查网络后重试"}"
            }
            isLoadingPresets = false
        }

        AlertDialog(
            onDismissRequest = { showOnlinePresets = false },
            containerColor = cardBg,
            shape = RoundedCornerShape(20.dp),
            title = { Text("在线预设", fontWeight = FontWeight.Bold, color = textPrimary) },
            text = {
                Column(modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    // 线路选择
                    Text("选择线路：", fontSize = 13.sp, color = textSecondary)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedRoute == "github",
                            onClick = { selectedRoute = "github" },
                            label = { Text("GitHub", fontSize = 13.sp) },
                            leadingIcon = { if (selectedRoute == "github") Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) },
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = border,
                                selectedBorderColor = miaoBlue,
                                enabled = true,
                                selected = selectedRoute == "github"
                            ),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = miaoBlue.copy(alpha = 0.12f),
                                selectedLabelColor = miaoBlue
                            )
                        )
                        FilterChip(
                            selected = selectedRoute == "tencent",
                            onClick = { selectedRoute = "tencent" },
                            label = { Text("腾讯云", fontSize = 13.sp) },
                            leadingIcon = { if (selectedRoute == "tencent") Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) },
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = border,
                                selectedBorderColor = miaoBlue,
                                enabled = true,
                                selected = selectedRoute == "tencent"
                            ),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = miaoBlue.copy(alpha = 0.12f),
                                selectedLabelColor = miaoBlue
                            )
                        )
                        FilterChip(
                            selected = selectedRoute == "custom",
                            onClick = { selectedRoute = "custom" },
                            label = { Text("自定义", fontSize = 13.sp) },
                            leadingIcon = { if (selectedRoute == "custom") Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) },
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = border,
                                selectedBorderColor = miaoBlue,
                                enabled = true,
                                selected = selectedRoute == "custom"
                            ),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = miaoBlue.copy(alpha = 0.12f),
                                selectedLabelColor = miaoBlue
                            )
                        )
                    }
                    // 自定义 URL 输入
                    if (selectedRoute == "custom") {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = customPresetUrl,
                            onValueChange = { customPresetUrl = it },
                            label = { Text("JSON 链接") },
                            placeholder = { Text("https://example.com/presets.json") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = miaoBlue,
                                unfocusedBorderColor = border
                            )
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "JSON 格式示例：\n[{\"name\":\"猫猫\",\"url\":\"https://...txt\"}]",
                            fontSize = 11.sp,
                            color = textSecondary,
                            lineHeight = 16.sp
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    if (isLoadingPresets) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = miaoBlue, strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("正在加载...", fontSize = 14.sp, color = textSecondary)
                        }
                    } else if (loadError != null) {
                        Text(loadError!!, fontSize = 14.sp, color = MiaoColors.Red)
                    } else if (onlinePresetList.isEmpty()) {
                        Text("暂无可用预设", fontSize = 14.sp, color = textSecondary)
                    } else {
                        Text("点击预览，长按下载", fontSize = 12.sp, color = textSecondary)
                        Spacer(Modifier.height(12.dp))
                        for (preset in onlinePresetList) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = { previewPreset = preset },
                                            onLongPress = {
                                                MiaoConfig.savePreset(context, preset.name, preset.content)
                                                presets = MiaoConfig.getPresets(context)
                                                showOnlinePresets = false
                                                Toast.makeText(context, "已下载预设\"${preset.name}\"", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = bg),
                                border = androidx.compose.foundation.BorderStroke(1.dp, border)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(preset.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                                        Text(
                                            "${preset.content.split("\n").count { it.isNotBlank() }} 条规则",
                                            fontSize = 12.sp,
                                            color = textSecondary
                                        )
                                    }
                                    Row {
                                        IconButton(
                                            onClick = { previewPreset = preset },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.Visibility, "预览", tint = miaoBlue, modifier = Modifier.size(20.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                MiaoConfig.savePreset(context, preset.name, preset.content)
                                                presets = MiaoConfig.getPresets(context)
                                                showOnlinePresets = false
                                                Toast.makeText(context, "已下载预设\"${preset.name}\"", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.FileDownload, "下载", tint = MiaoColors.Green, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showOnlinePresets = false }) {
                    Text("关闭", color = miaoBlue)
                }
            }
        )
    }

    // ── 预设预览弹窗 ──
    if (previewPreset != null) {
        AlertDialog(
            onDismissRequest = { previewPreset = null },
            containerColor = cardBg,
            shape = RoundedCornerShape(20.dp),
            title = { Text(previewPreset!!.name, fontWeight = FontWeight.Bold, color = textPrimary) },
            text = {
                Column(modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        previewPreset!!.content,
                        fontSize = 13.sp,
                        color = textTertiary,
                        lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    MiaoConfig.savePreset(context, previewPreset!!.name, previewPreset!!.content)
                    presets = MiaoConfig.getPresets(context)
                    Toast.makeText(context, "已下载预设\"${previewPreset!!.name}\"", Toast.LENGTH_SHORT).show()
                    previewPreset = null
                }) { Text("下载", color = miaoBlue) }
            },
            dismissButton = {
                TextButton(onClick = { previewPreset = null }) {
                    Text("关闭", color = textSecondary)
                }
            }
        )
    }
}

/**
 * 在线预设数据类
 */
private data class OnlinePreset(val name: String, val content: String)

/**
 * 从 GitHub 仓库加载预设列表
 */
private fun loadOnlinePresetList(): List<OnlinePreset> {
    val repoApiUrl = "https://api.github.com/repos/Xiao-youyu/miaoyun/git/trees/main?recursive=1"
    DebugLog.d("PresetDL", "GitHub: fetching tree...")
    val connection = java.net.URL(repoApiUrl).openConnection() as java.net.HttpURLConnection
    connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
    connection.connectTimeout = 20000
    connection.readTimeout = 20000
    val json = try {
        val text = connection.inputStream.bufferedReader().readText()
        DebugLog.d("PresetDL", "GitHub tree OK, ${text.length} chars")
        text
    } catch (e: Exception) {
        DebugLog.e("PresetDL", "GitHub tree failed: ${e.message}")
        throw Exception("无法连接 GitHub，请检查网络或尝试切换线路")
    } finally {
        connection.disconnect()
    }
    val jsonObj = org.json.JSONObject(json)
    val tree = jsonObj.getJSONArray("tree")

    val presets = mutableListOf<OnlinePreset>()
    for (i in 0 until tree.length()) {
        val item = tree.getJSONObject(i)
        val path = item.getString("path")
        val type = item.getString("type")
        val sha = item.getString("sha")
        if (type == "blob" && path.endsWith(".txt")) {
            val name = path.removeSuffix(".txt")
            try {
                // 使用 GitHub API 获取文件内容（base64），避免 raw.githubusercontent.com 被墙
                val blobUrl = "https://api.github.com/repos/Xiao-youyu/miaoyun/git/blobs/$sha"
                val blobConn = java.net.URL(blobUrl).openConnection() as java.net.HttpURLConnection
                blobConn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                blobConn.connectTimeout = 15000
                blobConn.readTimeout = 15000
                val blobJson = try {
                    blobConn.inputStream.bufferedReader().readText()
                } finally {
                    blobConn.disconnect()
                }
                val blobObj = org.json.JSONObject(blobJson)
                val content = blobObj.getString("content")
                val encoding = blobObj.optString("encoding", "base64")
                val decoded = if (encoding == "base64") {
                    String(android.util.Base64.decode(content.replace("\n", ""), android.util.Base64.DEFAULT))
                } else {
                    content
                }
                if (decoded.isNotBlank()) {
                    presets.add(OnlinePreset(name, decoded.trim()))
                }
            } catch (_: Exception) {}
        }
    }
    return presets
}

/**
 * 从腾讯云 COS 加载预设列表
 */
private fun loadTencentPresetList(): List<OnlinePreset> {
    val presets = mutableListOf<OnlinePreset>()
    val urls = listOf(
        "小鬼" to "https://yunmiao-1423290280.cos.ap-guangzhou.myqcloud.com/小鬼.txt",
        "恶堕" to "https://yunmiao-1423290280.cos.ap-guangzhou.myqcloud.com/恶堕.txt",
        "文言文" to "https://yunmiao-1423290280.cos.ap-guangzhou.myqcloud.com/文言文.txt",
        "狗狗" to "https://yunmiao-1423290280.cos.ap-guangzhou.myqcloud.com/狗狗.txt",
        "猫猫" to "https://yunmiao-1423290280.cos.ap-guangzhou.myqcloud.com/猫猫.txt"
    )
    var lastError: Exception? = null
    for ((name, url) in urls) {
        try {
            val content = httpGet(url)
            if (content.isNotBlank()) {
                presets.add(OnlinePreset(name, content.trim()))
            }
        } catch (e: Exception) {
            lastError = e
        }
    }
    if (presets.isEmpty() && lastError != null) {
        throw lastError!!
    }
    return presets
}

/**
 * 从自定义 JSON 链接加载预设列表
 * JSON 格式：[{"name": "猫猫", "url": "https://...txt"}, ...]
 * 也可以是 {"presets": [{"name": "...", "url": "..."}]}
 * 兼容旧格式：[{"name": "...", "rules": "..."}]
 */
private fun loadCustomPresetList(url: String): List<OnlinePreset> {
    if (url.isBlank()) throw Exception("请输入 JSON 链接")
    DebugLog.d("PresetDL", "Custom: fetching $url")
    val json = httpGet(url)

    val presets = mutableListOf<OnlinePreset>()
    try {
        val trimmed = json.trim()
        val items = when {
            trimmed.startsWith("[") -> {
                val arr = JSONArray(trimmed)
                (0 until arr.length()).map { arr.getJSONObject(it) }
            }
            trimmed.startsWith("{") -> {
                val obj = JSONObject(trimmed)
                if (obj.has("presets")) {
                    val arr = obj.getJSONArray("presets")
                    (0 until arr.length()).map { arr.getJSONObject(it) }
                } else listOf(obj)
            }
            else -> throw Exception("JSON 格式不正确")
        }
        for (item in items) {
            val name = item.optString("name", "预设")
            if (item.has("rules")) {
                // 旧格式：直接包含 rules 内容
                val rules = item.getString("rules")
                if (rules.isNotBlank()) presets.add(OnlinePreset(name, rules.trim()))
            } else if (item.has("url")) {
                // 新格式：包含 url，需要二次下载
                val presetUrl = item.getString("url")
                try {
                    val content = httpGet(presetUrl)
                    if (content.isNotBlank()) presets.add(OnlinePreset(name, content.trim()))
                } catch (_: Exception) { /* 跳过下载失败的预设 */ }
            }
        }
    } catch (e: Exception) {
        if (e.message?.contains("JSON") == true) throw e
        throw Exception("JSON 解析失败: ${e.message}")
    }
    return presets
}

/** 通用 HTTP GET，自动处理中文 URL 编码 */
private fun httpGet(urlStr: String): String {
    // 处理中文字符编码
    val encodedUrl = urlStr.replace(Regex("[^\u0020-\u007E]")) { match ->
        java.net.URLEncoder.encode(match.value, "UTF-8")
    }
    DebugLog.d("PresetDL", "GET $encodedUrl")
    val connection = java.net.URL(encodedUrl).openConnection() as java.net.HttpURLConnection
    connection.setRequestProperty("User-Agent", "MiaoAssistant/1.1.8")
    connection.connectTimeout = 20000
    connection.readTimeout = 20000
    connection.instanceFollowRedirects = true
    try {
        val code = connection.responseCode
        DebugLog.d("PresetDL", "HTTP $code <- $encodedUrl")
        if (code != 200) throw Exception("HTTP $code")
        val body = connection.inputStream.bufferedReader().readText()
        DebugLog.d("PresetDL", "OK ${body.length} chars <- $encodedUrl")
        return body
    } catch (e: Exception) {
        val errDetail = e.message ?: e.javaClass.simpleName
        DebugLog.e("PresetDL", "FAIL: $errDetail <- $encodedUrl")
        throw Exception("网络请求失败: $errDetail")
    } finally {
        connection.disconnect()
    }
}

private fun hasStoragePermission(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}

private fun requestStoragePermission(
    context: android.content.Context,
    permissionLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    intentLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            intentLauncher.launch(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            )
        } catch (e: Exception) {
            intentLauncher.launch(
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            )
        }
    } else {
        permissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
}

private const val MIAO_DATA_FILE = "miao_data.json"

/**
 * 从 Download/miao/ 读取数据文件。
 * Android 10+ 使用 MediaStore，低版本直接读文件。
 */
private fun readMiaoDataFile(context: android.content.Context): String? {
    // 有 MANAGE_EXTERNAL_STORAGE 权限时，优先直接读文件
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
        val directFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "miao/$MIAO_DATA_FILE")
        if (directFile.exists()) {
            try {
                return directFile.readText()
            } catch (_: Exception) {}
        }
    }
    // 普通直接读文件尝试
    val directFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "miao/$MIAO_DATA_FILE")
    if (directFile.exists()) {
        try {
            return directFile.readText()
        } catch (_: Exception) {}
    }
    // Android 10+ 降级用 MediaStore
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
            val resolver = context.contentResolver
            val collection = android.provider.MediaStore.Downloads.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val selection = "${android.provider.MediaStore.Downloads.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(MIAO_DATA_FILE)
            resolver.query(collection, arrayOf(android.provider.MediaStore.Downloads._ID, android.provider.MediaStore.Downloads.RELATIVE_PATH), selection, selectionArgs, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val path = cursor.getString(1) ?: ""
                    if (path.contains("miao", ignoreCase = true)) {
                        val uri = android.content.ContentUris.withAppendedId(collection, id)
                        resolver.openInputStream(uri)?.use { return it.bufferedReader().readText() }
                    }
                }
            }
        } catch (_: Exception) {}
    }
    return null
}

/**
 * 写入数据到 Download/miao/ 目录。
 * 优先直接写文件，失败则 MediaStore，再失败用 app 私有目录。
 */
private fun writeMiaoDataFile(context: android.content.Context, content: String): Boolean {
    // 策略1：直接写文件（有权限时最可靠）
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
        return writeDirect(content)
    }
    // Android 10 有 WRITE_EXTERNAL_STORAGE 时也可以直接写
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        if (context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return writeDirect(content)
        }
    }
    // 策略2：MediaStore（Android 10+）
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val result = writeViaMediaStore(context, content)
        if (result) return true
        DebugLog.e("SaveData", "MediaStore 失败，降级到 app 私有目录")
    }
    // 策略3：app 私有目录兜底（无需权限）
    return writeToFallbackDir(context, content)
}

private fun writeDirect(content: String): Boolean {
    return try {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "miao")
        if (!dir.exists() && !dir.mkdirs()) {
            DebugLog.e("SaveData", "mkdirs failed: ${dir.absolutePath}")
            return false
        }
        val file = File(dir, MIAO_DATA_FILE)
        file.writeText(content)
        DebugLog.d("SaveData", "直接写入成功: ${file.absolutePath}, size=${content.length}")
        true
    } catch (e: Exception) {
        DebugLog.e("SaveData", "直接写入异常: ${e.message}")
        false
    }
}

private fun writeViaMediaStore(context: android.content.Context, content: String): Boolean {
    return try {
        val resolver = context.contentResolver
        val collection = android.provider.MediaStore.Downloads.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)

        // 先查已有文件
        val queryUri = queryExistingFileUri(resolver, collection)
        if (queryUri != null) {
            // 已有文件 → 删除后重新插入（避免 update 在某些设备上失败）
            resolver.delete(queryUri, null, null)
        } else {
            // 没有旧文件 → 尝试删除残留（按名字）
            val sel = "${android.provider.MediaStore.Downloads.DISPLAY_NAME} = ?"
            resolver.delete(collection, sel, arrayOf(MIAO_DATA_FILE))
        }

        // 插入新文件
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, MIAO_DATA_FILE)
            put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/json")
            put(android.provider.MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/miao/")
            put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
        if (uri == null) {
            DebugLog.e("SaveData", "MediaStore insert 返回 null, API=${Build.VERSION.SDK_INT}")
            return false
        }
        resolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
        resolver.update(uri, android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
        }, null, null)
        DebugLog.d("SaveData", "MediaStore 写入成功: $uri")
        true
    } catch (e: Exception) {
        DebugLog.e("SaveData", "MediaStore 写入异常: ${e.message}")
        false
    }
}

/** 查询已存在的 miao_data.json 文件 URI */
private fun queryExistingFileUri(
    resolver: android.content.ContentResolver,
    collection: android.net.Uri
): android.net.Uri? {
    return try {
        val projection = arrayOf(android.provider.MediaStore.Downloads._ID)
        val selection = "${android.provider.MediaStore.Downloads.DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(MIAO_DATA_FILE)
        resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                android.net.Uri.withAppendedPath(collection, id.toString())
            } else null
        }
    } catch (_: Exception) { null }
}

/** 兜底：写入 app 私有目录（无需任何权限） */
private fun writeToFallbackDir(context: android.content.Context, content: String): Boolean {
    return try {
        val dir = File(context.getExternalFilesDir(null), "miao")
        if (!dir.exists() && !dir.mkdirs()) {
            DebugLog.e("SaveData", "fallback mkdirs failed")
            return false
        }
        val file = File(dir, MIAO_DATA_FILE)
        file.writeText(content)
        DebugLog.d("SaveData", "Fallback 写入成功: ${file.absolutePath}")
        true
    } catch (e: Exception) {
        DebugLog.e("SaveData", "Fallback 写入异常: ${e.message}")
        false
    }
}

private fun saveMiaoData(context: android.content.Context, config: MiaoConfig): String {
    return try {
        val json = JSONObject().apply {
            put("rules", MiaoConfig.rulesToString(config.rules))
            put("customEmoticons", JSONArray(config.customEmoticons))
            put("enabledApps", JSONArray(config.enabledApps.toList()))
            put("enableAppend", config.enableAppend)
            put("appendText", config.appendText)
            put("enableRandomEmoticon", config.enableRandomEmoticon)
            put("protectSuffix", config.protectSuffix)
            put("processingMode", config.processingMode)
            put("voiceInputOptimize", config.voiceInputOptimize)
            put("voiceDebounceMs", config.voiceDebounceMs)
            put("spaceNoMiao", config.spaceNoMiao)
            put("emoticonTriggerMode", config.emoticonTriggerMode)
            put("emoticonProbability", config.emoticonProbability)
            put("emoticonInterval", config.emoticonInterval)
            put("deleteOptimize", config.deleteOptimize)
            put("qqCatPaw", config.qqCatPaw)
            put("punctuationOptimize", config.punctuationOptimize)
        }
        if (writeMiaoDataFile(context, json.toString(2))) {
            "数据已保存到 Download/miao/$MIAO_DATA_FILE"
        } else {
            "保存失败: 无法写入文件"
        }
    } catch (e: Exception) {
        "保存失败: ${e.message}"
    }
}

private fun loadMiaoData(context: android.content.Context, config: MiaoConfig): String {
    return try {
        val content = readMiaoDataFile(context)
            ?: return "未找到数据文件，请先保存数据"
        val json = JSONObject(content)
        val rulesStr = json.optString("rules", "")
        if (rulesStr.isNotBlank()) {
            config.rules = rulesStr.split("\n").mapNotNull { MiaoConfig.parseRule(it) }
        }
        if (json.has("customEmoticons")) {
            val arr = json.getJSONArray("customEmoticons")
            config.customEmoticons = (0 until arr.length()).map { arr.getString(it) }
        }
        if (json.has("enabledApps")) {
            val arr = json.getJSONArray("enabledApps")
            config.enabledApps = (0 until arr.length()).map { arr.getString(it) }.toSet()
        }
        if (json.has("enableAppend")) config.enableAppend = json.getBoolean("enableAppend")
        if (json.has("appendText")) config.appendText = json.getString("appendText")
        if (json.has("enableRandomEmoticon")) config.enableRandomEmoticon = json.getBoolean("enableRandomEmoticon")
        if (json.has("protectSuffix")) config.protectSuffix = json.getBoolean("protectSuffix")
        if (json.has("processingMode")) config.processingMode = json.getString("processingMode")
        if (json.has("voiceInputOptimize")) config.voiceInputOptimize = json.getBoolean("voiceInputOptimize")
        if (json.has("voiceDebounceMs")) config.voiceDebounceMs = json.getLong("voiceDebounceMs")
        if (json.has("spaceNoMiao")) config.spaceNoMiao = json.getBoolean("spaceNoMiao")
        if (json.has("emoticonTriggerMode")) config.emoticonTriggerMode = json.getString("emoticonTriggerMode")
        if (json.has("emoticonProbability")) config.emoticonProbability = json.getDouble("emoticonProbability").toFloat()
        if (json.has("emoticonInterval")) config.emoticonInterval = json.getInt("emoticonInterval")
        if (json.has("deleteOptimize")) config.deleteOptimize = json.getBoolean("deleteOptimize")
        if (json.has("qqCatPaw")) {
            val v = json.get("qqCatPaw")
            config.qqCatPaw = when (v) {
                is Boolean -> if (v) "default" else "off"
                is String -> v
                else -> "off"
            }
        }
        if (json.has("punctuationOptimize")) config.punctuationOptimize = json.getBoolean("punctuationOptimize")
        config.save(context)
        "数据已导入并生效"
    } catch (e: Exception) {
        "导入失败: ${e.message}"
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, miaoBlue: Color, border: Color, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 13.sp) },
        leadingIcon = { if (selected) Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) },
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
