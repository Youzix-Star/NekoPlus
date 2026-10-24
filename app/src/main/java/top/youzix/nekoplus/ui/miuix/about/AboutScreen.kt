/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.nekoplus.ui.miuix.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.youzix.nekoplus.BuildConfig
import top.youzix.nekoplus.MiaoState
import top.youzix.nekoplus.crash.CrashHandler
import top.youzix.nekoplus.ui.AppIcons
import top.youzix.nekoplus.ui.AppMark
import top.youzix.nekoplus.ui.UiEnginePrefs
import top.youzix.nekoplus.ui.miuix.components.BlurredBar
import top.youzix.nekoplus.ui.rememberBackupActions
import top.youzix.nekoplus.util.DebugDump
import top.youzix.nekoplus.util.DeviceInfo
import top.youzix.nekoplus.util.UpdateChecker
import top.youzix.nekoplus.util.UpdateResult
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

private const val REPOSITORY_URL = "https://github.com/Youzix-Star/NekoPlus"
private const val DEVELOPER_URL = "https://github.com/Youzix-Star"
private const val AUTHOR_URL = "https://github.com/Xiao-youyu"

/**
 * 关于 — the identity page: what this build is, where it came from, and what it wrote down when it
 * died. It is the fourth tab, so it has no back arrow.
 *
 * The crash report and the backup live here because this is the page people open when they need to
 * diagnose or move the app; the diagnostic rows themselves only appear once developer mode is
 * unlocked (three taps on the Ciallo line below the version).
 */
@Composable
fun AboutScreen(
    bottomPadding: Dp,
    onOpenLicenses: () -> Unit,
    onNotify: (String) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            BlurredBar(backdrop = null) {
                TopAppBar(title = "关于", scrollBehavior = scrollBehavior)
            }
        },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        AboutContent(
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() + bottomPadding,
            ),
            scrollBehavior = scrollBehavior,
            onOpenLicenses = onOpenLicenses,
            onNotify = onNotify,
        )
    }
}

@Composable
private fun AboutContent(
    contentPadding: PaddingValues,
    scrollBehavior: top.yukonga.miuix.kmp.basic.ScrollBehavior,
    onOpenLicenses: () -> Unit,
    onNotify: (String) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val backup = rememberBackupActions(onNotify)

    var crashLog by remember { mutableStateOf(CrashHandler.read(context)) }
    var showCrash by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<UpdateResult.Available?>(null) }
    var deviceInfo by remember { mutableStateOf<String?>(null) }
    var dump by remember { mutableStateOf(DebugDump.read(context)) }
    var showDump by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .overScrollVertical(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            AppHeader(onNotify = onNotify)
        }

        item(key = "about") {
            Column {
                SmallTitle(text = "关于")
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "获取源代码",
                        summary = "GitHub 上的 NekoPlus",
                        startAction = { Icon(AppIcons.SourceCode, null, Modifier.size(22.dp)) },
                        onClick = { uriHandler.openUri(REPOSITORY_URL) },
                    )
                    ArrowPreference(
                        title = "开源许可",
                        summary = "依赖的许可",
                        startAction = { Icon(AppIcons.License, null, Modifier.size(22.dp)) },
                        onClick = onOpenLicenses,
                    )
                    ArrowPreference(
                        title = "新手引导",
                        summary = "再看一遍怎么用",
                        startAction = { Icon(AppIcons.Sparkle, null, Modifier.size(22.dp)) },
                        onClick = {
                            MiaoState.showOnboarding = true
                            onNotify("已打开新手引导")
                        },
                    )
                    ArrowPreference(
                        title = "崩溃日志",
                        summary = if (crashLog.isNullOrBlank()) "没有记录" else "有一条记录，点按查看",
                        startAction = { Icon(AppIcons.Debug, null, Modifier.size(22.dp)) },
                        onClick = {
                            crashLog = CrashHandler.read(context)
                            showCrash = true
                        },
                    )
                    ArrowPreference(
                        title = "设备信息",
                        summary = "机型、系统与屏幕",
                        startAction = { Icon(AppIcons.Phones, null, Modifier.size(22.dp)) },
                        onClick = { deviceInfo = DeviceInfo.snapshot(context) },
                    )
                    ArrowPreference(
                        title = "检查更新",
                        summary = if (checking) "检查中…" else "当前 v${BuildConfig.VERSION_NAME}",
                        startAction = { Icon(AppIcons.Update, null, Modifier.size(22.dp)) },
                        onClick = {
                            if (checking) return@ArrowPreference
                            checking = true
                            UpdateChecker.check { result ->
                                checking = false
                                when (result) {
                                    is UpdateResult.Available -> update = result
                                    UpdateResult.UpToDate -> onNotify("已是最新版本")
                                    is UpdateResult.Failed -> onNotify("检查失败：" + result.message)
                                }
                            }
                        },
                    )
                }
            }
        }

        item(key = "developer") {
            Column {
                SmallTitle(text = "开发者")
                Card(modifier = Modifier.fillMaxWidth()) {
                    // The original author of 喵喵助手, whose 1.1.8 this project is a rebuild of.
                    ArrowPreference(
                        title = "Xiao-youyu",
                        summary = "原作者",
                        startAction = { Icon(AppIcons.Developer, null, Modifier.size(22.dp)) },
                        onClick = { uriHandler.openUri(AUTHOR_URL) },
                    )
                    ArrowPreference(
                        title = "Youzix-Star",
                        summary = "重构与维护",
                        startAction = { Icon(AppIcons.Developer, null, Modifier.size(22.dp)) },
                        onClick = { uriHandler.openUri(DEVELOPER_URL) },
                    )
                }
            }
        }

        item(key = "backup") {
            Column {
                SmallTitle(text = "备份")
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "导出配置",
                        summary = "全部设置存成一个 JSON",
                        startAction = { Icon(AppIcons.Update, null, Modifier.size(22.dp)) },
                        onClick = backup.export,
                    )
                    ArrowPreference(
                        title = "导入配置",
                        summary = "从 JSON 恢复，注意文件里含 API Key",
                        startAction = { Icon(AppIcons.Refresh, null, Modifier.size(22.dp)) },
                        onClick = backup.import,
                    )
                }
            }
        }

        if (MiaoState.developerMode) {
            item(key = "debug") {
                Column {
                    SmallTitle(text = "调试")
                    Card(modifier = Modifier.fillMaxWidth()) {
                        SwitchPreference(
                            title = "调试模式",
                            summary = "排查「抓不到输入框」这类问题",
                            checked = MiaoState.debugMode,
                            onCheckedChange = {
                                MiaoState.debugMode = it
                                UiEnginePrefs.saveDebugMode(context, it)
                            },
                        )
                        if (MiaoState.debugMode) {
                            ArrowPreference(
                                title = "查看最近一次抓取",
                                summary = dump?.let { "共 ${it.lineSequence().count()} 行" }
                                    ?: "还没有抓取过",
                                startAction = { Icon(AppIcons.Rule, null, Modifier.size(22.dp)) },
                                onClick = {
                                    dump = DebugDump.read(context)
                                    showDump = true
                                },
                            )
                            // Verifying the crash screen needs a crash, and waiting for a real bug
                            // to happen is not a test. Thrown on the main thread so the uncaught
                            // handler sees it exactly like a real one.
                            ArrowPreference(
                                title = "模拟崩溃",
                                summary = "崩一次，看看报告页",
                                startAction = { Icon(AppIcons.Debug, null, Modifier.size(22.dp)) },
                                onClick = {
                                    throw IllegalStateException("模拟崩溃：这是调试里手动触发的")
                                },
                            )
                        }
                    }
                }
            }
        }

        item(key = "footer") {
            Text(
                text = "AGPL-3.0 · Ciallo～(∠・ω c)⌒★",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 20.dp),
            )
        }
    }

    if (showCrash) {
        val report = crashLog
        OverlayDialog(
            show = true,
            title = "崩溃日志",
            summary = runCatching { CrashHandler.file(context).absolutePath }.getOrDefault("?"),
            onDismissRequest = { showCrash = false },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (report.isNullOrBlank()) {
                    Text("没有崩溃记录。", style = MiuixTheme.textStyles.body2)
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = report,
                            style = MiuixTheme.textStyles.footnote2,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Button(
                        onClick = {
                            clipboard.setText(AnnotatedString(report))
                            onNotify("已复制崩溃日志")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("复制")
                    }
                    Button(
                        onClick = {
                            CrashHandler.clear(context)
                            crashLog = null
                            showCrash = false
                            onNotify("已清空")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("清空")
                    }
                }
                Button(onClick = { showCrash = false }, modifier = Modifier.fillMaxWidth()) {
                    Text("关闭")
                }
            }
        }
    }

    deviceInfo?.let { info ->
        OverlayDialog(
            show = true,
            title = "设备信息",
            onDismissRequest = { deviceInfo = null },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = info,
                        style = MiuixTheme.textStyles.footnote2,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Button(
                    onClick = {
                        clipboard.setText(AnnotatedString(info))
                        onNotify("已复制设备信息")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("复制")
                }
                Button(onClick = { deviceInfo = null }, modifier = Modifier.fillMaxWidth()) {
                    Text("关闭")
                }
            }
        }
    }

    if (showDump) {
        val text = dump
        OverlayDialog(
            show = true,
            title = "最近一次抓取",
            summary = DebugDump.location(context),
            onDismissRequest = { showDump = false },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (text.isNullOrBlank()) {
                    Text(
                        "还没有抓取过。把「导出界面元素」设成某个悬浮窗按钮的动作，在目标应用里点一下即可。",
                        style = MiuixTheme.textStyles.body2,
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = text,
                            style = MiuixTheme.textStyles.footnote2,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Button(
                        onClick = {
                            clipboard.setText(AnnotatedString(text))
                            onNotify("已复制抓取结果")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("复制")
                    }
                    Button(
                        onClick = {
                            DebugDump.clear(context)
                            dump = null
                            showDump = false
                            onNotify("已清空")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("清空")
                    }
                }
                Button(onClick = { showDump = false }, modifier = Modifier.fillMaxWidth()) {
                    Text("关闭")
                }
            }
        }
    }

    update?.let { available ->
        OverlayDialog(
            show = true,
            title = "发现新版本 v${available.version}",
            onDismissRequest = { update = null },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (available.notes.isBlank()) {
                    Text("这个版本没有写更新说明。", style = MiuixTheme.textStyles.body2)
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(available.notes, style = MiuixTheme.textStyles.footnote1)
                    }
                }
                Button(
                    onClick = {
                        val target = available.apkUrl
                        if (target != null) {
                            uriHandler.openUri(target)
                        } else {
                            UpdateChecker.openReleasePage(context)
                        }
                        update = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (available.apkUrl != null) "下载" else "打开发布页")
                }
                Button(onClick = { update = null }, modifier = Modifier.fillMaxWidth()) {
                    Text("关闭")
                }
            }
        }
    }
}

/** The brand block: the mark, the name, the version, and the signature that unlocks 调试. */
@Composable
private fun AppHeader(onNotify: (String) -> Unit) {
    val context = LocalContext.current
    var taps by remember { mutableIntStateOf(0) }
    var lastTap by remember { mutableStateOf(0L) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppMark(
            color = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "NekoPlus",
            style = MiuixTheme.textStyles.title1,
            color = MiuixTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        // Three taps on the signature line unlock the diagnostic rows; nothing says so.
        Text(
            text = "Ciallo～(∠・ω c)⌒★",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.clickable {
                val now = System.currentTimeMillis()
                if (now - lastTap > 1_000) taps = 0
                lastTap = now
                taps += 1
                if (taps >= 3) {
                    taps = 0
                    MiaoState.developerMode = !MiaoState.developerMode
                    UiEnginePrefs.saveDeveloperMode(context, MiaoState.developerMode)
                    onNotify(
                        if (MiaoState.developerMode) "开发者模式已开启" else "开发者模式已关闭",
                    )
                }
            },
        )
    }
}
