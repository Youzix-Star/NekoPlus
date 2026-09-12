package love.miao.yun.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import love.miao.yun.BuildConfig
import love.miao.yun.R
import love.miao.yun.ui.theme.MiaoColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var easterEgg by remember { mutableStateOf(false) }
    var showSponsorDialog by remember { mutableStateOf(false) }
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }

    val miaoBlue = MiaoColors.blue()
    val textPrimary = MiaoColors.textPrimary()
    val textSecondary = MiaoColors.textSecondary()
    val textTertiary = MiaoColors.textTertiary()
    val cardBg = MiaoColors.cardBg()
    val bg = MiaoColors.bg()
    val border = MiaoColors.border()

    fun onSecretTap() {
        val now = System.currentTimeMillis()
        if (now - lastTapTime > 1500) tapCount = 0
        lastTapTime = now
        tapCount++
        if (tapCount >= 3) {
            easterEgg = true
            tapCount = 0
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于") },
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
            // App info header
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, border)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_icon),
                        contentDescription = "App Icon",
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "喵喵助手",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        modifier = Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onSecretTap() }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "v${BuildConfig.VERSION_NAME}",
                        fontSize = 13.sp,
                        color = textSecondary,
                        modifier = Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onSecretTap() }
                    )
                }
            }

            // GitHub card
            AboutCard(
                icon = Icons.Default.Code,
                iconColor = textPrimary,
                title = "GitHub",
                subtitle = "https://github.com/Xiao-youyu/miao.git",
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                cardBg = cardBg,
                border = border,
                onClick = {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Xiao-youyu/miao.git"))
                        )
                    } catch (_: Exception) {}
                }
            )

            // Email card
            AboutCard(
                icon = Icons.Default.Email,
                iconColor = Color(0xFF1E88E5),
                title = "Email",
                subtitle = "378242636@qq.com",
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                cardBg = cardBg,
                border = border,
                onClick = {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:378242636@qq.com"))
                        )
                    } catch (_: Exception) {}
                }
            )

            // Sponsor card
            AboutCard(
                icon = Icons.Default.Favorite,
                iconColor = Color(0xFFE91E63),
                title = "赞助",
                subtitle = "支持一下作者～",
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                cardBg = cardBg,
                border = border,
                onClick = { showSponsorDialog = true }
            )

            // Build info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, border)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Build, null, tint = textTertiary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("构建信息", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                    }
                    Spacer(Modifier.height(12.dp))
                    BuildInfoRow("包名", "love.miao.yun", textSecondary, textTertiary)
                    BuildInfoRow("版本", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", textSecondary, textTertiary)
                    BuildInfoRow("最低支持", "Android 8.0 (API 26)", textSecondary, textTertiary)
                    BuildInfoRow("目标版本", "Android 16 (API 35)", textSecondary, textTertiary)
                    BuildInfoRow("构建类型", "Debug", textSecondary, textTertiary)
                    BuildInfoRow("语言", "Kotlin + Jetpack Compose", textSecondary, textTertiary)
                    BuildInfoRow("UI 框架", "Material3", textSecondary, textTertiary)
                }
            }

            // Disclaimer card
            var showDisclaimerDialog by remember { mutableStateOf(false) }
            AboutCard(
                icon = Icons.Default.Gavel,
                iconColor = MiaoColors.Orange,
                title = "免责声明",
                subtitle = "查看软件免责声明",
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                cardBg = cardBg,
                border = border,
                onClick = { showDisclaimerDialog = true }
            )

            if (showDisclaimerDialog) {
                AlertDialog(
                    onDismissRequest = { showDisclaimerDialog = false },
                    containerColor = cardBg,
                    shape = RoundedCornerShape(20.dp),
                    title = {
                        Text("免责声明", fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
                                color = textTertiary,
                                lineHeight = 20.sp
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showDisclaimerDialog = false }) {
                            Text("确定", color = miaoBlue)
                        }
                    }
                )
            }
        }
    }

    // 彩蛋弹窗
    if (easterEgg) {
        var showUpdateConfig by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { easterEgg = false },
            containerColor = cardBg,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("彩蛋", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Text(
                    "爱情 并非 互相 凝视\n而是朝同一个方向去仰望\n永恒 不是 钻石\n可悲的心灵才需要颗石头做奖状",
                    fontSize = 16.sp,
                    color = textTertiary,
                    lineHeight = 26.sp
                )
            },
            dismissButton = {
                TextButton(onClick = { showUpdateConfig = true }) {
                    Text("更新配置", color = textSecondary)
                }
            },
            confirmButton = {
                TextButton(onClick = { easterEgg = false }) {
                    Text("确定", color = miaoBlue)
                }
            }
        )

        if (showUpdateConfig) {
            UpdateConfigDialog(
                onDismiss = { showUpdateConfig = false },
                miaoBlue = miaoBlue,
                cardBg = cardBg,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                textTertiary = textTertiary,
                border = border
            )
        }
    }

    // 赞助弹窗
    if (showSponsorDialog) {
        var sponsorTab by remember { mutableIntStateOf(0) } // 0=微信, 1=支付宝
        AlertDialog(
            onDismissRequest = { showSponsorDialog = false },
            containerColor = cardBg,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("赞助作者", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "感谢您的赞助噢～\n赞助不会有特殊的功能噢～\n如果你觉得好用的话 多多支持呀！",
                        fontSize = 14.sp,
                        color = textTertiary,
                        lineHeight = 22.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    // 微信/支付宝切换
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = sponsorTab == 0,
                            onClick = { sponsorTab = 0 },
                            label = { Text("微信", fontSize = 13.sp) },
                            leadingIcon = { if (sponsorTab == 0) Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) },
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = border,
                                selectedBorderColor = miaoBlue,
                                enabled = true,
                                selected = sponsorTab == 0
                            ),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = miaoBlue.copy(alpha = 0.12f),
                                selectedLabelColor = miaoBlue
                            )
                        )
                        FilterChip(
                            selected = sponsorTab == 1,
                            onClick = { sponsorTab = 1 },
                            label = { Text("支付宝", fontSize = 13.sp) },
                            leadingIcon = { if (sponsorTab == 1) Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) },
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = border,
                                selectedBorderColor = miaoBlue,
                                enabled = true,
                                selected = sponsorTab == 1
                            ),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = miaoBlue.copy(alpha = 0.12f),
                                selectedLabelColor = miaoBlue
                            )
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Image(
                        painter = painterResource(id = if (sponsorTab == 0) R.drawable.sponsor_qrcode else R.drawable.sponsor_alipay),
                        contentDescription = "赞助二维码",
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSponsorDialog = false }) {
                    Text("关闭", color = miaoBlue)
                }
            }
        )
    }
}

@Composable
private fun AboutCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    textPrimary: Color,
    textSecondary: Color,
    cardBg: Color,
    border: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, border),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                    Text(subtitle, fontSize = 12.sp, color = textSecondary)
                }
            }
            Icon(Icons.Default.OpenInNew, null, tint = textSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun BuildInfoRow(label: String, value: String, labelColor: Color, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = labelColor)
        Text(value, fontSize = 13.sp, color = valueColor)
    }
}

// ── 更新配置弹窗 ──
@Composable
private fun UpdateConfigDialog(
    onDismiss: () -> Unit,
    miaoBlue: Color,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    textTertiary: Color,
    border: Color
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("miao_config", android.content.Context.MODE_PRIVATE) }
    var stableUrl by remember { mutableStateOf(prefs.getString("update_stable_url", "") ?: "") }
    var betaUrl by remember { mutableStateOf(prefs.getString("update_beta_url", "") ?: "") }
    var channel by remember { mutableStateOf(prefs.getString("update_channel", "off") ?: "off") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cardBg,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text("更新配置", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = textPrimary)
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("配置版本检查的 JSON 链接，仅一个渠道生效：", fontSize = 13.sp, color = textSecondary)
                Spacer(Modifier.height(16.dp))

                // 正式版
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = channel == "stable",
                        onClick = {
                            channel = "stable"
                            if (betaUrl.isNotBlank()) betaUrl = betaUrl // keep text but channel switches
                        },
                        colors = RadioButtonDefaults.colors(selectedColor = miaoBlue)
                    )
                    Text("正式版", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                }
                OutlinedTextField(
                    value = stableUrl,
                    onValueChange = { stableUrl = it },
                    label = { Text("正式版 JSON 链接") },
                    placeholder = { Text("https://example.com/stable.json") },
                    singleLine = true,
                    enabled = channel == "stable",
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = miaoBlue,
                        unfocusedBorderColor = border
                    )
                )
                Spacer(Modifier.height(16.dp))

                // 测试版
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = channel == "beta",
                        onClick = { channel = "beta" },
                        colors = RadioButtonDefaults.colors(selectedColor = miaoBlue)
                    )
                    Text("测试版", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                }
                OutlinedTextField(
                    value = betaUrl,
                    onValueChange = { betaUrl = it },
                    label = { Text("测试版 JSON 链接") },
                    placeholder = { Text("https://example.com/beta.json") },
                    singleLine = true,
                    enabled = channel == "beta",
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = miaoBlue,
                        unfocusedBorderColor = border
                    )
                )
                Spacer(Modifier.height(16.dp))

                // 关闭更新
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = channel == "off",
                        onClick = { channel = "off" },
                        colors = RadioButtonDefaults.colors(selectedColor = miaoBlue)
                    )
                    Text("关闭自动检查", fontSize = 14.sp, color = textSecondary)
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "JSON 格式示例：\n{\"versionCode\":10,\"versionName\":\"1.1.9\",\"url\":\"https://...apk\"}",
                    fontSize = 11.sp,
                    color = textTertiary,
                    lineHeight = 16.sp
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = textSecondary)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                prefs.edit().apply {
                    putString("update_channel", channel)
                    putString("update_stable_url", stableUrl.trim())
                    putString("update_beta_url", betaUrl.trim())
                    apply()
                }
                onDismiss()
            }) {
                Text("保存", color = miaoBlue)
            }
        }
    )
}

// ── 启动时自动检查更新（供 MainActivity 调用） ──
@Composable
fun UpdateCheckGate() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("miao_config", android.content.Context.MODE_PRIVATE) }
    val channel = remember { prefs.getString("update_channel", "off") ?: "off" }
    val url = remember {
        when (channel) {
            "stable" -> prefs.getString("update_stable_url", "") ?: ""
            "beta" -> prefs.getString("update_beta_url", "") ?: ""
            else -> ""
        }
    }

    var showDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }

    val miaoBlue = MiaoColors.blue()
    val cardBg = MiaoColors.cardBg()
    val textPrimary = MiaoColors.textPrimary()
    val textSecondary = MiaoColors.textSecondary()
    val textTertiary = MiaoColors.textTertiary()
    val border = MiaoColors.border()

    LaunchedEffect(Unit) {
        if (channel == "off" || url.isBlank()) return@LaunchedEffect
        try {
            val info = withContext(kotlinx.coroutines.Dispatchers.IO) {
                checkForUpdate(url)
            }
            if (info != null && info.versionCode > BuildConfig.VERSION_CODE) {
                updateInfo = info
                showDialog = true
            }
        } catch (_: Exception) { }
    }

    if (showDialog && updateInfo != null) {
        val info = updateInfo!!
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = cardBg,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("发现新版本", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Black)
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    // 版本号突出显示
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = miaoBlue.copy(alpha = 0.08f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "v${info.versionName}",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = miaoBlue
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "版本号 ${info.versionCode}  ·  当前 v${BuildConfig.VERSION_NAME}",
                                    fontSize = 12.sp,
                                    color = textTertiary
                                )
                                Row {
                                    if (info.releaseDate.isNotBlank()) {
                                        Text(info.releaseDate, fontSize = 12.sp, color = textTertiary)
                                    }
                                    if (info.releaseDate.isNotBlank() && info.size.isNotBlank()) {
                                        Text("  ·  ", fontSize = 12.sp, color = textTertiary)
                                    }
                                    if (info.size.isNotBlank()) {
                                        Text(info.size, fontSize = 12.sp, color = textTertiary)
                                    }
                                }
                            }
                        }
                    }

                    // 更新日志
                    if (info.changelog.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        Text("更新内容", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = textSecondary)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            info.changelog,
                            fontSize = 14.sp,
                            color = textPrimary,
                            lineHeight = 22.sp
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("稍后提醒", color = textSecondary)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDialog = false
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.url)))
                        } catch (_: Exception) {}
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = miaoBlue)
                ) {
                    Text("立即更新", color = Color.White)
                }
            }
        )
    }
}

private data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val url: String,
    val size: String = "",
    val releaseDate: String = "",
    val changelog: String = ""
)

private fun checkForUpdate(url: String): UpdateInfo? {
    val encodedUrl = url.replace(Regex("[^\u0020-\u007E]")) { match ->
        java.net.URLEncoder.encode(match.value, "UTF-8")
    }
    val connection = java.net.URL(encodedUrl).openConnection() as java.net.HttpURLConnection
    connection.setRequestProperty("User-Agent", "MiaoAssistant/${BuildConfig.VERSION_NAME}")
    connection.connectTimeout = 15000
    connection.readTimeout = 15000
    connection.instanceFollowRedirects = true
    try {
        val code = connection.responseCode
        if (code != 200) return null
        val body = connection.inputStream.bufferedReader().readText()
        val json = org.json.JSONObject(body)
        return UpdateInfo(
            versionCode = json.optInt("versionCode", 0),
            versionName = json.optString("versionName", ""),
            url = json.optString("url", ""),
            size = json.optString("size", ""),
            releaseDate = json.optString("releaseDate", ""),
            changelog = json.optString("changelog", "")
        )
    } finally {
        connection.disconnect()
    }
}
