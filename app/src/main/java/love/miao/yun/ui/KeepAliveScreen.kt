package love.miao.yun.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import love.miao.yun.ui.theme.MiaoColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeepAliveScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var batteryIgnored by remember { mutableStateOf(isBatteryOptimizationIgnored(context)) }
    var notificationEnabled by remember { mutableStateOf(areNotificationsEnabled(context)) }

    val miaoBlue = MiaoColors.blue()
    val textPrimary = MiaoColors.textPrimary()
    val textSecondary = MiaoColors.textSecondary()
    val textTertiary = MiaoColors.textTertiary()
    val cardBg = MiaoColors.cardBg()
    val bg = MiaoColors.bg()
    val border = MiaoColors.border()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryIgnored = isBatteryOptimizationIgnored(context)
                notificationEnabled = areNotificationsEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("保活设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            // 说明卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = bg),
                border = BorderStroke(1.dp, border)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.Info, null, tint = miaoBlue, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "部分系统（如 MIUI、HyperOS、ColorOS 等）会在后台清理应用，导致无障碍服务失效。请根据下方指引授予相关权限，确保服务稳定运行。",
                        fontSize = 13.sp,
                        color = textTertiary,
                        lineHeight = 20.sp
                    )
                }
            }

            // 电池优化
            KeepAliveCard(
                icon = Icons.Default.BatteryChargingFull,
                iconColor = MiaoColors.Green,
                title = "电池优化",
                subtitle = if (batteryIgnored) "已忽略" else "未忽略",
                subtitleColor = if (batteryIgnored) MiaoColors.Green else MiaoColors.Red,
                buttonText = if (batteryIgnored) "已完成" else "去设置",
                isDone = batteryIgnored,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                cardBg = cardBg,
                border = border,
                miaoBlue = miaoBlue,
                onClick = {
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        try {
                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        } catch (_: Exception) {
                            Toast.makeText(context, "无法打开电池优化设置", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )

            // 通知权限
            KeepAliveCard(
                icon = Icons.Default.Notifications,
                iconColor = MiaoColors.Orange,
                title = "通知权限",
                subtitle = if (notificationEnabled) "已开启" else "未开启",
                subtitleColor = if (notificationEnabled) MiaoColors.Green else MiaoColors.Red,
                buttonText = if (notificationEnabled) "已完成" else "去设置",
                isDone = notificationEnabled,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                cardBg = cardBg,
                border = border,
                miaoBlue = miaoBlue,
                onClick = {
                    try {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, "无法打开通知设置", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            // 自启动权限（厂商特定）
            KeepAliveCard(
                icon = Icons.Default.Autorenew,
                iconColor = Color(0xFF1E88E5),
                title = "自启动权限",
                subtitle = "部分系统需要手动开启",
                subtitleColor = textSecondary,
                buttonText = "去设置",
                isDone = false,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                cardBg = cardBg,
                border = border,
                miaoBlue = miaoBlue,
                onClick = { openAutoStartSettings(context) }
            )

            // 后台运行权限
            KeepAliveCard(
                icon = Icons.Default.DirectionsRun,
                iconColor = MiaoColors.Purple,
                title = "后台运行",
                subtitle = "允许应用在后台运行",
                subtitleColor = textSecondary,
                buttonText = "去设置",
                isDone = false,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                cardBg = cardBg,
                border = border,
                miaoBlue = miaoBlue,
                onClick = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        })
                    } catch (_: Exception) {
                        Toast.makeText(context, "无法打开应用详情", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            // 锁定后台（小米/OPPO等）
            KeepAliveCard(
                icon = Icons.Default.Lock,
                iconColor = MiaoColors.Red,
                title = "锁定后台任务",
                subtitle = "在最近任务中锁定本应用",
                subtitleColor = textSecondary,
                buttonText = "查看指引",
                isDone = false,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                cardBg = cardBg,
                border = border,
                miaoBlue = miaoBlue,
                onClick = {
                    Toast.makeText(context, "请在最近任务界面长按本应用，点击锁定", Toast.LENGTH_LONG).show()
                    try {
                        context.startActivity(Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                        })
                    } catch (_: Exception) {}
                }
            )

            // 补充说明
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = bg),
                border = BorderStroke(1.dp, border)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lightbulb, null, tint = MiaoColors.Orange, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("小贴士", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "• 不同品牌手机设置项名称可能不同\n" +
                                "• 如果服务仍然失效，请尝试重启手机\n" +
                                "• 建议将本应用加入电池白名单",
                        fontSize = 13.sp,
                        color = textTertiary,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun KeepAliveCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    subtitleColor: Color,
    buttonText: String,
    isDone: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    cardBg: Color,
    border: Color,
    miaoBlue: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, border)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 12.sp, color = subtitleColor)
            }
            OutlinedButton(
                onClick = onClick,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (isDone) border else miaoBlue),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isDone) textSecondary else miaoBlue
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(buttonText, fontSize = 13.sp)
            }
        }
    }
}

private fun isBatteryOptimizationIgnored(context: Context): Boolean {
    return try {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.isIgnoringBatteryOptimizations(context.packageName)
    } catch (_: Exception) {
        false
    }
}

private fun areNotificationsEnabled(context: Context): Boolean {
    return try {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.areNotificationsEnabled()
    } catch (_: Exception) {
        true
    }
}

private fun openAutoStartSettings(context: Context) {
    // 尝试打开各厂商的自启动设置
    val intents = listOf(
        // 小米
        Intent().apply {
            component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
        },
        // OPPO
        Intent().apply {
            component = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")
        },
        // vivo
        Intent().apply {
            component = ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
        },
        // 华为
        Intent().apply {
            component = ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
        },
        // 三星
        Intent().apply {
            component = ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")
        },
        // 通用应用详情
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    )

    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: Exception) {
            continue
        }
    }
    Toast.makeText(context, "无法找到自启动设置，请在系统设置中手动查找", Toast.LENGTH_LONG).show()
}
