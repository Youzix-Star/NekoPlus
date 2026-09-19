/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.nekoplus.ui.miuix.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import top.youzix.nekoplus.util.CrashHandler
import top.youzix.nekoplus.util.UpdateChecker
import top.youzix.nekoplus.util.UpdateResult
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.youzix.nekoplus.BuildConfig
import top.youzix.nekoplus.MiaoState
import top.youzix.nekoplus.ui.AppIcons
import top.youzix.nekoplus.ui.AppIconText
import top.youzix.nekoplus.ui.UiEnginePrefs
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

private const val REPOSITORY_URL = "https://github.com/Youzix-Star/NekoPlus"
private const val DEVELOPER_URL = "https://github.com/Youzix-Star"
private const val AUTHOR_URL = "https://github.com/Xiao-youyu"

@Composable
fun AboutScreen(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    onOpenLicenses: () -> Unit,
    onNotify: (String) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var crashLog by remember { mutableStateOf(CrashHandler.read(context)) }
    var showCrash by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<UpdateResult.Available?>(null) }
    var versionTapCount by remember { mutableStateOf(0) }
    var lastVersionTapTime by remember { mutableStateOf(0L) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .overScrollVertical(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            AppHeader(onVersionTap = {
                val now = System.currentTimeMillis()
                if (now - lastVersionTapTime > 1000) versionTapCount = 0
                lastVersionTapTime = now
                versionTapCount++
                if (versionTapCount >= 3) {
                    versionTapCount = 0
                    MiaoState.developerMode = !MiaoState.developerMode
                    UiEnginePrefs.saveDeveloperMode(context, MiaoState.developerMode)
                    onNotify(if (MiaoState.developerMode) "开发者模式已开启" else "开发者模式已关闭")
                }
            })
        }

        item(key = "about") {
            Column {
                SmallTitle(text = "关于")
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "获取源代码",
                        summary = "GitHub 上的源码",
                        startAction = {
                            Icon(
                                imageVector = AppIcons.SourceCode,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        onClick = { uriHandler.openUri(REPOSITORY_URL) },
                    )
                    ArrowPreference(
                        title = "开源许可",
                        summary = "依赖的许可证",
                        startAction = {
                            Icon(
                                imageVector = AppIcons.License,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        onClick = onOpenLicenses,
                    )
                    ArrowPreference(
                        title = "新手引导",
                        summary = "再看一遍怎么用",
                        startAction = {
                            Icon(
                                imageVector = AppIcons.Sparkle,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        onClick = {
                            MiaoState.showOnboarding = true
                            onNotify("已打开新手引导")
                        },
                    )
                    ArrowPreference(
                        title = "崩溃日志",
                        summary = if (crashLog.isNullOrBlank()) "没有记录" else "有一条记录，点按查看",
                        startAction = {
                            Icon(
                                imageVector = AppIcons.Rule,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        onClick = {
                            crashLog = CrashHandler.read(context)
                            showCrash = true
                        },
                    )
                    ArrowPreference(
                        title = "检查更新",
                        summary = if (checking) "检查中…" else "当前 v${BuildConfig.VERSION_NAME}",
                        startAction = {
                            Icon(
                                imageVector = AppIcons.Update,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        },
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
                        startAction = {
                            Icon(
                                imageVector = AppIcons.Developer,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        onClick = { uriHandler.openUri(AUTHOR_URL) },
                    )
                    ArrowPreference(
                        title = "Youzix-Star",
                        summary = "重构与维护",
                        startAction = {
                            Icon(
                                imageVector = AppIcons.Developer,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        onClick = { uriHandler.openUri(DEVELOPER_URL) },
                    )
                }
            }
        }

        item(key = "footer") {
            Text(
                text = "暂未实现。",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 20.dp),
            )
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
                    Text("这个版本没有写更新说明。")
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = available.notes,
                            style = MiuixTheme.textStyles.footnote1,
                        )
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
                Button(
                    onClick = {
                        UpdateChecker.openReleasePage(context)
                        update = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("在浏览器中查看")
                }
            }
        }
    }

    if (showCrash) {
        val report = crashLog
        OverlayDialog(
            show = true,
            title = "崩溃日志",
            onDismissRequest = { showCrash = false },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (report.isNullOrBlank()) {
                    Text("没有崩溃记录。")
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = report,
                            style = MiuixTheme.textStyles.footnote1,
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
}

@Composable
private fun AppHeader(onVersionTap: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The mark is drawn as text, not as the launcher bitmap: the launcher resource is an
        // adaptive icon, which `painterResource` cannot load -- that mismatch is what used to
        // take this page down. Text also follows the theme's ink instead of baking one in.
        Text(
            text = AppIconText,
            fontSize = 52.sp,
            color = MiuixTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "喵喵助手",
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
        Text(
            text = "Ciallo～(∠・ω c)⌒★",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
            modifier = Modifier.clickable { onVersionTap() },
        )
    }
}
