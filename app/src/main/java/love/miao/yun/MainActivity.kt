package love.miao.yun

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import love.miao.yun.ui.AboutScreen
import love.miao.yun.ui.AppSelectScreen
import love.miao.yun.ui.KeepAliveScreen
import love.miao.yun.ui.MainScreen
import love.miao.yun.ui.SettingsScreen
import love.miao.yun.ui.PersonalizationScreen
import love.miao.yun.ui.UpdateCheckGate
import love.miao.yun.ui.theme.MiaoAssistantTheme
import love.miao.yun.ui.theme.MiaoColors
import love.miao.yun.service.FloatingButtonService
import love.miao.yun.service.FloatingWindowService
import love.miao.yun.service.LogFloatingService
import love.miao.yun.util.CrashHandler
import love.miao.yun.util.DebugLog
import love.miao.yun.util.MiaoConfig
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    // 分享导入的规则文本（Compose 可观察状态）
    val sharedRulesText = mutableStateOf<String?>(null)
    // 标记是否需要延迟启动服务（避免从无障碍设置返回时触发系统杀服务）
    private var pendingServiceStart = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 处理分享导入的文本文件
        sharedRulesText.value = readSharedText(intent)

        // 延迟启动服务：不在 onCreate 中直接调用 startForegroundService
        // 部分手机（vivo 等）在无障碍设置返回时 Activity 重建，
        // 此时调用 startForegroundService 会触发系统安全机制杀掉无障碍服务
        pendingServiceStart = true

        setContent {
            MiaoAssistantTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AnnouncementGate {
                        CrashDialogGate {
                            UpdateCheckGate()
                            val ctx = LocalContext.current
                            val sharedTextState = (ctx as? MainActivity)?.sharedRulesText
                            ShareImportGate(
                                sharedText = sharedTextState?.value,
                                onConsumed = { sharedTextState?.value = null }
                            ) {
                                AppNavHost()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sharedRulesText.value = readSharedText(intent)
    }

    override fun onResume() {
        super.onResume()
        // 延迟到 Activity 完全可见后再启动服务
        // 避免从无障碍设置返回时 startForegroundService 触发系统杀服务
        if (pendingServiceStart) {
            pendingServiceStart = false
            handler.postDelayed({
                startPendingServices()
            }, 500L)
        }
    }

    private fun startPendingServices() {
        // 如果之前开启了悬浮窗，自动恢复
        if (FloatingWindowService.isEnabled(this) && android.provider.Settings.canDrawOverlays(this)) {
            FloatingWindowService.start(this)
        }
        // 如果当前模式是悬浮按钮，自动启动悬浮按钮
        val cfg = MiaoConfig.load(this)
        if (cfg.processingMode == MiaoConfig.MODE_FLOATING_WINDOW && FloatingButtonService.isEnabled(this) && android.provider.Settings.canDrawOverlays(this)) {
            FloatingButtonService.start(this)
        }
        // 如果调试日志模式已激活，自动启动日志悬浮窗
        if (DebugLog.isEnabled(this) && android.provider.Settings.canDrawOverlays(this)) {
            LogFloatingService.start(this)
        }
    }

    private fun readSharedText(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) {
            android.util.Log.d("MiaoShare", "Not ACTION_SEND, action=${intent?.action}")
            return null
        }
        android.util.Log.d("MiaoShare", "Received share: type=${intent.type}, hasStream=${intent.hasExtra(Intent.EXTRA_STREAM)}")
        return try {
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (uri != null) {
                android.util.Log.d("MiaoShare", "Reading from URI: $uri")
                contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
            } else {
                android.util.Log.d("MiaoShare", "No URI, reading EXTRA_TEXT")
                intent.getStringExtra(Intent.EXTRA_TEXT)
            }
        } catch (e: Exception) {
            android.util.Log.e("MiaoShare", "Failed to read share", e)
            try { intent.getStringExtra(Intent.EXTRA_TEXT) } catch (_: Exception) { null }
        }
    }
}

/**
 * 本地公告弹窗，点击确定后不再弹出
 */
@Composable
private fun AnnouncementGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("miao_config", android.content.Context.MODE_PRIVATE) }
    var disclaimerDismissed by remember { mutableStateOf(prefs.getBoolean("disclaimer_dismissed", false)) }
    var announcementDismissed by remember { mutableStateOf(prefs.getBoolean("announcement_dismissed", false)) }

    content()

    // 第一次打开：先弹免责声明
    if (!disclaimerDismissed) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = MiaoColors.cardBg(),
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("免责声明", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MiaoColors.textPrimary())
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = """一、软件性质与用途声明

本软件"喵喵助手"（以下简称"本软件"）是一款基于Android无障碍服务（Accessibility
Service）API开发的文本辅助处理工具。本软件的核心功能为：在用户授权的前提下，
对用户指定应用中的输入文本进行自动化替换、后缀追加及表情插入等处理操作。

本软件按"现状"提供，开发者不对软件的功能完整性、运行稳定性、兼容性或适用
于特定目的作出任何明示或暗示的保证。


二、用户权利与义务

2.1 用户在安装、复制或使用本软件前，应仔细阅读本声明的全部内容。用户一旦
    安装或使用本软件，即视为已充分理解并同意接受本声明的全部条款。

2.2 用户应确保本软件的使用行为符合其所在国家或地区的法律法规、行业规范及
    平台服务协议。用户因使用本软件而违反上述规定所产生的一切法律责任，由
    用户自行承担，与本软件开发者无关。

2.3 用户应自行判断本软件的使用场景及方式，并对使用本软件所产生的一切后果
    承担全部责任。


三、无障碍服务权限说明

3.1 本软件运行所必需的Android无障碍服务权限，系经用户在系统设置中手动开启
    后方可生效。该权限的授予完全出于用户自愿，本软件不会以任何方式强制或
    诱导用户开启此权限。

3.2 本软件承诺：无障碍服务权限仅用于实现本声明第一条所述的文本处理功能，
    不会用于读取、收集、存储或传输用户在其他应用中的个人隐私信息、通讯内
    容、账户数据或其他敏感信息。

3.3 用户理解并确认：开启无障碍服务可能影响其他应用的正常运行，或与同类功
    能的应用产生冲突。由此引发的任何问题，本软件开发者不承担任何责任。


四、第三方平台合规风险提示

4.1 本软件的文本处理功能可能涉及对第三方应用（包括但不限于微信、QQ等即时
    通讯工具）输入框内容的读取与修改。用户应自行了解并遵守相关第三方平台的
    用户协议、服务条款及社区规范。

4.2 用户因使用本软件而导致其第三方平台账户受到限制、封禁或其他处罚的，系
    用户自身使用行为所致，本软件开发者对此不承担任何赔偿或补偿责任。

4.3 本软件不隶属于、不受认可或不受任何第三方平台的关联。本软件中提及的第
    三方平台名称、标识等均为其各自所有者的财产。


五、免责声明与责任限制

5.1 在适用法律允许的最大范围内，本软件开发者不对以下情形承担任何责任：

    （a）因用户使用或无法使用本软件所导致的任何直接、间接、附带、特殊、
         惩戒性或后果性损害，包括但不限于利润损失、数据丢失、业务中断、
         商誉损失等；

    （b）因本软件与用户设备系统、其他应用程序之间的兼容性问题所导致的任
         何损害；

    （c）因用户设备故障、系统异常、网络中断或不可抗力等非本软件原因所导
         致的任何损害；

    （d）因用户违反本声明或相关法律法规使用本软件所导致的任何损害；

    （e）因第三方平台政策变更、系统更新等原因导致本软件功能失效或受限。

5.2 本软件不保证在所有设备和系统版本上均能正常运行。本软件的最低运行要求
    为Android 8.0（API 26）及以上版本，实际运行效果可能因设备型号、系统版
    本、已安装应用等因素而存在差异。

5.3 本软件可能因系统升级、第三方平台策略调整或技术原因进行功能变更、暂停
    或终止服务，届时恕不另行通知。


六、知识产权

6.1 本软件的源代码、界面设计、图标、文档及其他相关材料的知识产权归开发者
    所有。未经开发者书面许可，任何人不得对本软件进行反向工程、反编译、反
    汇编或以其他方式试图获取本软件的源代码。

6.2 本软件中使用的开源组件遵循其各自的开源许可协议。


七、隐私保护

7.1 本软件不收集、不存储、不传输用户的任何个人信息。本软件的所有配置数据
    均存储于用户设备本地，不会上传至任何服务器。

7.2 本软件的无障碍服务所读取的文本内容仅在设备内存中临时处理，处理完成后
    即被释放，不会持久化存储或以任何形式外传。


八、未成年人使用

未成年人应在法定监护人的指导下阅读本声明并使用本软件。未成年人使用本软件
的行为，视为已获得其法定监护人的同意。


九、声明的修改

开发者保留随时修改本声明的权利。修改后的声明将在本软件内或开发者指定的渠
道公布。用户在声明修改后继续使用本软件的，视为同意修改后的声明内容。


十、法律适用与争议解决

10.1 本声明的解释、效力及争议的解决，适用中华人民共和国法律（不含冲突法规
     则）。

10.2 因本声明或本软件使用所产生的任何争议，双方应首先协商解决；协商不成的，
     任何一方均有权向本软件开发者所在地有管辖权的人民法院提起诉讼。


十一、其他

11.1 本声明中任何条款被认定为无效或不可执行的，不影响其余条款的效力。

11.2 本声明构成用户与本软件开发者之间就本软件使用事宜的完整协议，取代此前
     就同一事项所达成的一切口头或书面协议。""",
                        fontSize = 13.sp,
                        color = MiaoColors.textTertiary(),
                        lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.edit().putBoolean("disclaimer_dismissed", true).apply()
                    disclaimerDismissed = true
                }) {
                    Text("确定", color = Color(0xFF2196F3))
                }
            }
        )
    }

    // 免责声明确认后再弹欢迎弹窗
    if (disclaimerDismissed && !announcementDismissed) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = MiaoColors.cardBg(),
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("欢迎使用", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Text(
                    "欢迎使用本应用喵～\n\n" +
                            "抖音号：youyunya\n" +
                            "反馈/交流群：1106258940\n" +
                            "作者邮箱：3782426036@qq.com\n\n" +
                            "如软件您发现软件有问题，欢迎加群反馈～",
                    fontSize = 14.sp,
                    color = MiaoColors.textTertiary(),
                    lineHeight = 22.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.edit().putBoolean("announcement_dismissed", true).apply()
                    announcementDismissed = true
                }) {
                    Text("确定", color = Color(0xFF2196F3))
                }
            }
        )
    }
}

/**
 * 启动时检查是否有崩溃日志，有则弹窗展示
 */
@Composable
private fun CrashDialogGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var crashLog by remember { mutableStateOf(CrashHandler.getCrashLog(context)) }
    var showDialog by remember { mutableStateOf(crashLog != null) }

    content()

    if (showDialog && crashLog != null) {
        AlertDialog(
            onDismissRequest = {
                showDialog = false
                CrashHandler.clearCrashLogs(context)
            },
            containerColor = MiaoColors.cardBg(),
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("上次发生了崩溃", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column {
                    Text(
                        "应用在上次运行时发生了异常，以下是错误信息。你可以复制后反馈给开发者。",
                        fontSize = 14.sp,
                        color = MiaoColors.textTertiary()
                    )
                    Spacer(Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MiaoColors.bg())
                    ) {
                        Text(
                            text = crashLog ?: "",
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState()),
                            fontSize = 11.sp,
                            color = MiaoColors.textPrimary(),
                            lineHeight = 16.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(crashLog ?: ""))
                    showDialog = false
                    CrashHandler.clearCrashLogs(context)
                }) {
                    Text("复制并关闭", color = Color(0xFF2196F3))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDialog = false
                    CrashHandler.clearCrashLogs(context)
                }) {
                    Text("关闭", color = MiaoColors.textSecondary())
                }
            }
        )
    }
}

@Composable
fun ShareImportGate(
    sharedText: String?,
    onConsumed: () -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var show by remember { mutableStateOf(sharedText != null) }
    var rulesText by remember { mutableStateOf(sharedText ?: "") }
    var presetName by remember { mutableStateOf("") }

    content()

    if (show && rulesText.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { show = false; onConsumed() },
            containerColor = MiaoColors.cardBg(),
            shape = RoundedCornerShape(20.dp),
            title = { Text("导入替换规则", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("收到替换规则文件，请为预设命名：", fontSize = 14.sp, color = MiaoColors.textTertiary())
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it },
                        label = { Text("预设名称") },
                        placeholder = { Text("例如：自定义规则") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF2196F3),
                            unfocusedBorderColor = Color(0xFFE0E0E0)
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    val ruleCount = rulesText.split("\n").count { it.isNotBlank() }
                    Text("共 $ruleCount 条规则", fontSize = 12.sp, color = MiaoColors.textSecondary())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = presetName.trim()
                    if (name.isEmpty()) {
                        Toast.makeText(context, "请输入预设名称", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    MiaoConfig.savePreset(context, name, rulesText)
                    show = false
                    onConsumed()
                    Toast.makeText(context, "预设“$name”已导入", Toast.LENGTH_SHORT).show()
                }) {
                    Text("导入", color = Color(0xFF2196F3))
                }
            },
            dismissButton = {
                TextButton(onClick = { show = false; onConsumed() }) {
                    Text("取消", color = MiaoColors.textSecondary())
                }
            }
        )
    }
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home",
        enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
        exitTransition = { slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300)) },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) }
    ) {
        composable("home") {
            MainScreen(
                onNavigateApps = { navController.navigate("apps") { launchSingleTop = true } },
                onNavigateSettings = { navController.navigate("settings") { launchSingleTop = true } },
                onNavigateAbout = { navController.navigate("about") { launchSingleTop = true } },
                onNavigateKeepAlive = { navController.navigate("keepalive") { launchSingleTop = true } },
                onNavigatePersonalization = { navController.navigate("personalization") { launchSingleTop = true } }
            )
        }
        composable("apps") {
            AppSelectScreen(onBack = { navController.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable("about") {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable("keepalive") {
            KeepAliveScreen(onBack = { navController.popBackStack() })
        }
        composable("personalization") {
            PersonalizationScreen(onBack = { navController.popBackStack() })
        }
    }
}
