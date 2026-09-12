package love.miao.yun.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import love.miao.yun.service.FloatingWindowService
import love.miao.yun.service.MiaoAccessibilityService
import love.miao.yun.ui.theme.MiaoColors
import love.miao.yun.util.MiaoConfig

@Composable
fun MainScreen(
    onNavigateApps: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateAbout: () -> Unit,
    onNavigateKeepAlive: () -> Unit,
    onNavigatePersonalization: () -> Unit
) {
    val context = LocalContext.current
    var isServiceRunning by remember { mutableStateOf(MiaoAccessibilityService.isRunning) }
    var isUserEnabled by remember { mutableStateOf(MiaoAccessibilityService.isEnabled(context)) }
    var enabledAppCount by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isServiceRunning = MiaoAccessibilityService.isRunning
                isUserEnabled = MiaoAccessibilityService.isEnabled(context)
                enabledAppCount = MiaoConfig.load(context).enabledApps.size
                // 延迟二次检查：部分手机无障碍服务状态有延迟
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    isServiceRunning = MiaoAccessibilityService.isRunning
                }, 1000)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 开关状态以用户设置和系统无障碍状态为准，不依赖 isRunning（部分手机服务连接有延迟）
    val switchState = isUserEnabled && isAccessibilityServiceEnabled(context)
    val miaoBlue = MiaoColors.blue()
    val textPrimary = MiaoColors.textPrimary()
    val textSecondary = MiaoColors.textSecondary()
    val cardBg = MiaoColors.cardBg()
    val bg = MiaoColors.bg()
    val border = MiaoColors.border()
    val switchOff = MiaoColors.switchOff()

    Box(modifier = Modifier.fillMaxSize().background(bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 44.dp, start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header card with toggle
            Card(
                modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black.copy(alpha = 0.04f)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, border)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("喵喵助手", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text("让你的聊天更喵喵～", fontSize = 13.sp, color = textSecondary)
                    }
                    Switch(
                        checked = switchState,
                        onCheckedChange = { newValue ->
                            if (newValue) {
                                if (!isAccessibilityServiceEnabled(context)) {
                                    Toast.makeText(context, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
                                    openAccessibilitySettings(context)
                                } else {
                                    MiaoAccessibilityService.setEnabled(context, true)
                                    isUserEnabled = true
                                    isServiceRunning = true
                                    FloatingWindowService.onStatusChanged?.invoke()
                                    Toast.makeText(context, "喵喵喵喵喵", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                MiaoAccessibilityService.setEnabled(context, false)
                                isUserEnabled = false
                                FloatingWindowService.onStatusChanged?.invoke()
                                Toast.makeText(context, "喵喵已暂停", Toast.LENGTH_SHORT).show()
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
            }

            // App selector card
            Card(
                modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black.copy(alpha = 0.04f)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, border),
                onClick = onNavigateApps
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Apps, null, tint = miaoBlue, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("应用选择", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                            Text("已选择 $enabledAppCount 个应用", fontSize = 12.sp, color = textSecondary)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = textSecondary)
                }
            }

            // Keep-alive card
            Card(
                modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black.copy(alpha = 0.04f)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, border),
                onClick = onNavigateKeepAlive
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BatteryChargingFull, null, tint = MiaoColors.Green, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("保活设置", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                            Text("防止后台服务被系统清理", fontSize = 12.sp, color = textSecondary)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = textSecondary)
                }
            }

            // Settings card
            Card(
                modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black.copy(alpha = 0.04f)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, border),
                onClick = onNavigateSettings
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, null, tint = MiaoColors.textTertiary(), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("更多设置", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = textSecondary)
                }
            }

            // Personalization card
            Card(
                modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black.copy(alpha = 0.04f)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, border),
                onClick = onNavigatePersonalization
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, null, tint = MiaoColors.Purple, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("个性化设置", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = textSecondary)
                }
            }

            // About card
            Card(
                modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black.copy(alpha = 0.04f)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, border),
                onClick = onNavigateAbout
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = MiaoColors.textTertiary(), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("关于", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = textSecondary)
                }
            }

            // Tips
            Text(
                "提示：开启无障碍服务后，在选定的应用中输入文字即可自动改写",
                fontSize = 12.sp,
                color = textSecondary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
        }
    }
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    return try {
        // 服务伪装成了系统无障碍服务，不能用 context.packageName 匹配
        // 直接检查服务是否在运行（最可靠）
        if (MiaoAccessibilityService.isRunning) return true

        // 方法1: 直接读系统设置，匹配服务类名
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        if (enabledServices.contains("selecttospeak", ignoreCase = true) ||
            enabledServices.contains("MiaoAccessibilityService", ignoreCase = true) ||
            enabledServices.contains("love.miao.yun", ignoreCase = true)) {
            return true
        }
        // 方法2: 通过 AccessibilityManager 查询完整服务列表
        val accessibilityEnabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED, 0
        )
        if (accessibilityEnabled == 1) {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val services = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            return services.any {
                val name = it.resolveInfo?.serviceInfo?.name ?: ""
                name.contains("selecttospeak", ignoreCase = true) ||
                name.contains("MiaoAccessibility", ignoreCase = true)
            }
        }
        false
    } catch (e: Exception) {
        // 兜底：如果服务实例存在就认为已启用
        MiaoAccessibilityService.isRunning
    }
}

private fun openAccessibilitySettings(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        Toast.makeText(context, "无法打开设置", Toast.LENGTH_SHORT).show()
    }
}
