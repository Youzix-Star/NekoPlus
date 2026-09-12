/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Rows are built from the segmented-column widgets ported from InstallerX-Revived (GPL-3.0).
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package love.miao.yun.ui.material3.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import love.miao.yun.BuildConfig
import love.miao.yun.MiaoState
import love.miao.yun.ui.AppIcons
import love.miao.yun.ui.AppIconText
import love.miao.yun.ui.material3.material3AppBarColor
import love.miao.yun.ui.material3.material3BlurEffect
import love.miao.yun.ui.material3.rememberMaterial3BlurBackdrop
import love.miao.yun.ui.material3.widgets.NavigationItemWidget
import love.miao.yun.ui.material3.widgets.SegmentedColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import love.miao.yun.util.CrashHandler
import top.yukonga.miuix.kmp.blur.layerBackdrop

private const val REPOSITORY_URL = "https://github.com/Youzix-Star/NekoPlus"
private const val DEVELOPER_URL = "https://github.com/Youzix-Star"

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
    var crashLog by remember { mutableStateOf(CrashHandler.read(context)) }
    var showCrash by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val backdrop = rememberMaterial3BlurBackdrop(useBlur)

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeFlexibleTopAppBar(
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
            contentPadding = paddingValues + outerPadding,
        ) {
            item { AppHeader() }

            item {
                SegmentedColumn(title = "关于") {
                    item {
                        NavigationItemWidget(
                            icon = AppIcons.SourceCode,
                            title = "获取源代码",
                            description = "GitHub 上的源码",
                            onClick = { uriHandler.openUri(REPOSITORY_URL) },
                        )
                    }
                    item {
                        NavigationItemWidget(
                            icon = AppIcons.License,
                            title = "开源许可",
                            description = "依赖的许可证",
                            onClick = onOpenLicenses,
                        )
                    }
                    item {
                        NavigationItemWidget(
                            icon = AppIcons.Sparkle,
                            title = "新手引导",
                            description = "再看一遍怎么用",
                            onClick = {
                                MiaoState.showOnboarding = true
                                onNotify("已打开新手引导")
                            },
                        )
                    }
                    item {
                        NavigationItemWidget(
                            icon = AppIcons.Rule,
                            title = "崩溃日志",
                            description = if (crashLog.isNullOrBlank()) {
                                "没有记录"
                            } else {
                                "有一条记录，点按查看"
                            },
                            onClick = {
                                crashLog = CrashHandler.read(context)
                                showCrash = true
                            },
                        )
                    }
                    item {
                        NavigationItemWidget(
                            icon = AppIcons.Update,
                            title = "检查更新",
                            description = "尚未接入",
                            onClick = { onNotify("暂未实现更新检查") },
                        )
                    }
                }
            }

            item {
                SegmentedColumn(title = "开发者") {
                    item {
                        NavigationItemWidget(
                            icon = AppIcons.Developer,
                            title = "Youzix-Star",
                            description = "github.com/Youzix-Star",
                            onClick = { uriHandler.openUri(DEVELOPER_URL) },
                        )
                    }
                }
            }

            item {
                Text(
                    text = "暂未实现。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 24.dp),
                )
            }
        }
    }

    val report = crashLog
    if (showCrash) {
        AlertDialog(
            onDismissRequest = { showCrash = false },
            title = { Text("崩溃日志") },
            text = {
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
            },
            confirmButton = {
                if (!report.isNullOrBlank()) {
                    TextButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(report))
                            onNotify("已复制崩溃日志")
                        },
                    ) {
                        Text("复制")
                    }
                }
            },
            dismissButton = {
                if (report.isNullOrBlank()) {
                    TextButton(onClick = { showCrash = false }) { Text("关闭") }
                } else {
                    TextButton(
                        onClick = {
                            CrashHandler.clear(context)
                            crashLog = null
                            showCrash = false
                            onNotify("已清空")
                        },
                    ) {
                        Text("清空")
                    }
                }
            },
        )
    }
}

@Composable
private fun AppHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The mark is drawn as text, not as the launcher bitmap: the launcher resource is an
        // adaptive icon, which `painterResource` cannot load -- that mismatch is what used to
        // take this page down. Text also follows the theme's ink instead of baking one in.
        Text(
            text = AppIconText,
            fontSize = 52.sp,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(text = "喵喵助手", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "用 Material Design 拼起来的悬浮窗助手",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}