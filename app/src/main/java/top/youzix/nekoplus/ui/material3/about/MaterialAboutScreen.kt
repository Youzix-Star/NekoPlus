/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.nekoplus.ui.material3.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.unit.dp
import top.youzix.nekoplus.BuildConfig
import top.youzix.nekoplus.MiaoState
import top.youzix.nekoplus.crash.CrashHandler
import top.youzix.nekoplus.ui.AppIcons
import top.youzix.nekoplus.ui.AppMark
import top.youzix.nekoplus.ui.UiEnginePrefs
import top.youzix.nekoplus.ui.material3.components.AppDialog
import top.youzix.nekoplus.ui.material3.components.IconTile
import top.youzix.nekoplus.ui.material3.components.SegmentedColumn
import top.youzix.nekoplus.ui.material3.components.SegmentedListItem
import top.youzix.nekoplus.ui.material3.components.SegmentedSwitchItem
import top.youzix.nekoplus.ui.material3.material3AppBarColor
import top.youzix.nekoplus.ui.material3.material3BlurEffect
import top.youzix.nekoplus.ui.material3.rememberMaterial3BlurBackdrop
import top.youzix.nekoplus.ui.rememberBackupActions
import top.youzix.nekoplus.util.DebugDump
import top.youzix.nekoplus.util.DeviceInfo
import top.youzix.nekoplus.util.UpdateChecker
import top.youzix.nekoplus.util.UpdateResult
import top.yukonga.miuix.kmp.blur.layerBackdrop

private const val REPOSITORY_URL = "https://github.com/Youzix-Star/NekoPlus"
private const val DEVELOPER_URL = "https://github.com/Youzix-Star"
private const val AUTHOR_URL = "https://github.com/Xiao-youyu"

/**
 * 关于 — the identity page, and the fourth tab, so it has no back arrow.
 *
 * The crash report and the backup live here because this is the page people open when they need to
 * diagnose or move the app; the diagnostic rows themselves only appear once developer mode is
 * unlocked (three taps on the Ciallo line below the version).
 */
@Composable
fun MaterialAboutScreen(
    outerPadding: PaddingValues,
    useBlur: Boolean,
    onOpenLicenses: () -> Unit,
    onNotify: (String) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val backdrop = rememberMaterial3BlurBackdrop(useBlur)
    val backup = rememberBackupActions(onNotify)

    var crashLog by remember { mutableStateOf(CrashHandler.read(context)) }
    var showCrash by remember { mutableStateOf(false) }
    var deviceInfo by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<UpdateResult.Available?>(null) }
    var dump by remember { mutableStateOf(DebugDump.read(context)) }
    var showDump by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                modifier = Modifier.material3BlurEffect(backdrop),
                title = { Text("关于", modifier = Modifier.padding(start = 12.dp)) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backdrop.material3AppBarColor(),
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    scrolledContainerColor = backdrop.material3AppBarColor(),
                ),
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(backdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier),
            contentPadding = paddingValues + PaddingValues(top = 8.dp, bottom = outerPadding.calculateBottomPadding() + 24.dp),
        ) {
            item { AppHeader(onNotify = onNotify) }

            item {
                SegmentedColumn(
                    title = "关于",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    item {
                        SegmentedListItem(
                            onClick = { uriHandler.openUri(REPOSITORY_URL) },
                            headlineContent = { Text("获取源代码") },
                            supportingContent = { Text("GitHub 上的 NekoPlus") },
                            leadingContent = { IconTile(AppIcons.SourceCode, "获取源代码") },
                            trailingContent = { androidx.compose.material3.Icon(AppIcons.ChevronRight, null) },
                        )
                    }
                    item {
                        SegmentedListItem(
                            onClick = onOpenLicenses,
                            headlineContent = { Text("开源许可") },
                            supportingContent = { Text("依赖的许可") },
                            leadingContent = { IconTile(AppIcons.License, "开源许可") },
                            trailingContent = { androidx.compose.material3.Icon(AppIcons.ChevronRight, null) },
                        )
                    }
                    item {
                        SegmentedListItem(
                            onClick = {
                                MiaoState.showOnboarding = true
                                onNotify("已打开新手引导")
                            },
                            headlineContent = { Text("新手引导") },
                            supportingContent = { Text("再看一遍怎么用") },
                            leadingContent = { IconTile(AppIcons.Sparkle, "新手引导") },
                            trailingContent = { androidx.compose.material3.Icon(AppIcons.ChevronRight, null) },
                        )
                    }
                    item {
                        SegmentedListItem(
                            onClick = {
                                crashLog = CrashHandler.read(context)
                                showCrash = true
                            },
                            headlineContent = { Text("崩溃日志") },
                            supportingContent = {
                                Text(
                                    if (crashLog.isNullOrBlank()) "没有记录"
                                    else "有一条记录，点按查看",
                                )
                            },
                            leadingContent = { IconTile(AppIcons.Debug, "崩溃日志") },
                            trailingContent = { androidx.compose.material3.Icon(AppIcons.ChevronRight, null) },
                        )
                    }
                    item {
                        SegmentedListItem(
                            onClick = { deviceInfo = DeviceInfo.snapshot(context) },
                            headlineContent = { Text("设备信息") },
                            supportingContent = { Text("机型、系统与屏幕") },
                            leadingContent = { IconTile(AppIcons.Phones, "设备信息") },
                            trailingContent = { androidx.compose.material3.Icon(AppIcons.ChevronRight, null) },
                        )
                    }
                    item {
                        SegmentedListItem(
                            onClick = {
                                if (!checking) {
                                    checking = true
                                    UpdateChecker.check { result ->
                                        checking = false
                                        when (result) {
                                            is UpdateResult.Available -> update = result
                                            UpdateResult.UpToDate -> onNotify("已是最新版本")
                                            is UpdateResult.Failed -> onNotify("检查失败：" + result.message)
                                        }
                                    }
                                }
                            },
                            headlineContent = { Text("检查更新") },
                            supportingContent = {
                                Text(if (checking) "检查中…" else "当前 v${BuildConfig.VERSION_NAME}")
                            },
                            leadingContent = { IconTile(AppIcons.Update, "检查更新") },
                            trailingContent = { androidx.compose.material3.Icon(AppIcons.ChevronRight, null) },
                        )
                    }
                }
            }

            item {
                SegmentedColumn(
                    title = "开发者",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    item {
                        SegmentedListItem(
                            onClick = { uriHandler.openUri(AUTHOR_URL) },
                            headlineContent = { Text("Xiao-youyu") },
                            supportingContent = { Text("原作者") },
                            leadingContent = { IconTile(AppIcons.Developer, "Xiao-youyu") },
                            trailingContent = { androidx.compose.material3.Icon(AppIcons.ChevronRight, null) },
                        )
                    }
                    item {
                        SegmentedListItem(
                            onClick = { uriHandler.openUri(DEVELOPER_URL) },
                            headlineContent = { Text("Youzix-Star") },
                            supportingContent = { Text("重构与维护") },
                            leadingContent = { IconTile(AppIcons.Developer, "Youzix-Star") },
                            trailingContent = { androidx.compose.material3.Icon(AppIcons.ChevronRight, null) },
                        )
                    }
                }
            }

            item {
                SegmentedColumn(
                    title = "备份",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    item {
                        SegmentedListItem(
                            onClick = backup.export,
                            headlineContent = { Text("导出配置") },
                            supportingContent = { Text("全部设置存成一个 JSON") },
                            leadingContent = { IconTile(AppIcons.Update, "导出配置") },
                        )
                    }
                    item {
                        SegmentedListItem(
                            onClick = backup.import,
                            headlineContent = { Text("导入配置") },
                            supportingContent = { Text("从 JSON 恢复，注意文件里含 API Key") },
                            leadingContent = { IconTile(AppIcons.Refresh, "导入配置") },
                        )
                    }
                }
            }

            if (MiaoState.developerMode) {
                item {
                    SegmentedColumn(
                        title = "调试",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        item {
                            SegmentedSwitchItem(
                                icon = AppIcons.Debug,
                                title = "调试模式",
                                summary = "排查「抓不到输入框」这类问题",
                                checked = MiaoState.debugMode,
                                onCheckedChange = {
                                    MiaoState.debugMode = it
                                    UiEnginePrefs.saveDebugMode(context, it)
                                },
                            )
                        }
                        if (MiaoState.debugMode) {
                            item {
                                SegmentedListItem(
                                    onClick = {
                                        dump = DebugDump.read(context)
                                        showDump = true
                                    },
                                    headlineContent = { Text("查看最近一次抓取") },
                                    supportingContent = {
                                        Text(
                                            dump?.let { "共 ${it.lineSequence().count()} 行" }
                                                ?: "还没有抓取过",
                                        )
                                    },
                                    leadingContent = { IconTile(AppIcons.Rule, "查看最近一次抓取") },
                                )
                            }
                            item {
                                // Verifying the crash screen needs a crash, and waiting for a real
                                // bug to happen is not a test.
                                SegmentedListItem(
                                    onClick = {
                                        throw IllegalStateException("模拟崩溃：这是调试里手动触发的")
                                    },
                                    headlineContent = { Text("模拟崩溃") },
                                    supportingContent = { Text("崩一次，看看报告页") },
                                    leadingContent = { IconTile(AppIcons.Debug, "模拟崩溃") },
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "AGPL-3.0 · Ciallo～(∠・ω c)⌒★",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                )
            }
        }
    }

    val report = crashLog
    if (showCrash) {
        AppDialog(title = "崩溃日志", onDismissRequest = { showCrash = false }) {
            if (report.isNullOrBlank()) {
                Text("没有崩溃记录。")
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = report,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!report.isNullOrBlank()) {
                    TextButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(report))
                            onNotify("已复制崩溃日志")
                        },
                    ) { Text("复制") }
                    TextButton(
                        onClick = {
                            CrashHandler.clear(context)
                            crashLog = null
                            showCrash = false
                            onNotify("已清空")
                        },
                    ) { Text("清空") }
                }
                TextButton(onClick = { showCrash = false }) { Text("关闭") }
            }
        }
    }

    val info = deviceInfo
    if (info != null) {
        AppDialog(title = "设备信息", onDismissRequest = { deviceInfo = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = info,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(info))
                        onNotify("已复制设备信息")
                        deviceInfo = null
                    },
                ) { Text("复制") }
                TextButton(onClick = { deviceInfo = null }) { Text("关闭") }
            }
        }
    }

    val dumpText = dump
    if (showDump) {
        AppDialog(title = "最近一次抓取", onDismissRequest = { showDump = false }) {
            if (dumpText.isNullOrBlank()) {
                Text("还没有抓取过。把「导出界面元素」设成某个悬浮窗按钮的动作，在目标应用里点一下即可。")
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = dumpText,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!dumpText.isNullOrBlank()) {
                    TextButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(dumpText))
                            onNotify("已复制抓取结果")
                        },
                    ) { Text("复制") }
                    TextButton(
                        onClick = {
                            DebugDump.clear(context)
                            dump = null
                            showDump = false
                            onNotify("已清空")
                        },
                    ) { Text("清空") }
                }
                TextButton(onClick = { showDump = false }) { Text("关闭") }
            }
        }
    }

    update?.let { available ->
        AppDialog(title = "发现新版本 v${available.version}", onDismissRequest = { update = null }) {
            if (available.notes.isBlank()) {
                Text("这个版本没有写更新说明。")
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(text = available.notes, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        val target = available.apkUrl
                        if (target != null) {
                            uriHandler.openUri(target)
                        } else {
                            UpdateChecker.openReleasePage(context)
                        }
                        update = null
                    },
                ) { Text(if (available.apkUrl != null) "下载" else "打开发布页") }
                TextButton(onClick = { update = null }) { Text("关闭") }
            }
        }
    }
}

/** The brand block: the mark, the name, the version, and the signature that unlocks 调试. */
@Composable
private fun AppHeader(onNotify: (String) -> Unit) {
    val context = LocalContext.current
    var taps by remember { mutableIntStateOf(0) }
    var lastTap by remember { mutableLongStateOf(0L) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppMark(
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(text = "NekoPlus", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        // Three taps on the signature line unlock the diagnostic rows; nothing says so.
        Text(
            text = "Ciallo～(∠・ω c)⌒★",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
