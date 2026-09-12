/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 */

package love.miao.yun.ui.miuix.about

import androidx.compose.foundation.Image
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import love.miao.yun.BuildConfig
import love.miao.yun.MiaoState
import love.miao.yun.R
import love.miao.yun.ui.AppIcons
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

@Composable
fun AboutScreen(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    onOpenLicenses: () -> Unit,
    onNotify: (String) -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .overScrollVertical(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            AppHeader()
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
                        title = "检查更新",
                        summary = "尚未接入",
                        startAction = {
                            Icon(
                                imageVector = AppIcons.Update,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        onClick = { onNotify("暂未实现更新检查") },
                    )
                }
            }
        }

        item(key = "developer") {
            Column {
                SmallTitle(text = "开发者")
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "Youzix-Star",
                        summary = "github.com/Youzix-Star",
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

}

@Composable
private fun AppHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher),
            contentDescription = null,
            modifier = Modifier.size(96.dp),
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
            text = "用 miuix 拼起来的悬浮窗助手",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
    }
}
