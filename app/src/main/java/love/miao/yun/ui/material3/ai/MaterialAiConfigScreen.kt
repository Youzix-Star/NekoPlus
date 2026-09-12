/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0
 *
 * Uses the same widget set that the rest of this engine took from InstallerX-Revived.
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package love.miao.yun.ui.material3.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import love.miao.yun.BuildConfig
import love.miao.yun.ai.AiManager
import love.miao.yun.ai.TokenStats
import love.miao.yun.ui.AppIcons
import love.miao.yun.ui.material3.material3AppBarColor
import love.miao.yun.ui.material3.material3BlurEffect
import love.miao.yun.ui.material3.rememberMaterial3BlurBackdrop
import love.miao.yun.ui.material3.widgets.BaseItemContainer
import love.miao.yun.ui.material3.widgets.NavigationItemWidget
import love.miao.yun.ui.material3.widgets.SegmentedColumn
import top.yukonga.miuix.kmp.blur.layerBackdrop

/**
 * The AI configuration page for the Material Design engine.
 *
 * Edits go straight to [AiManager.save], so there is no save button and nothing is lost when
 * the page is left. Text entry uses [OutlinedTextField] wrapped in [BaseItemContainer], because
 * the ported widget set has no text-editing row of its own.
 */
@Composable
fun MaterialAiConfigScreen(
    outerPadding: PaddingValues,
    useBlur: Boolean,
    onNotify: (String) -> Unit,
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val backdrop = rememberMaterial3BlurBackdrop(useBlur)

    var config by remember { mutableStateOf(AiManager.load(context)) }
    var stats by remember { mutableStateOf(TokenStats.query(context, 0L, null)) }
    var showPresetPicker by remember { mutableStateOf(false) }
    var fetching by remember { mutableStateOf(false) }

    val presetNames = remember { AiManager.getAllPresetNames(context) }

    /** One funnel for edits, so the local copy and SharedPreferences never drift apart. */
    fun edit(block: (AiManager.Config) -> Unit) {
        val next = AiManager.Config().also {
            it.baseUrl = config.baseUrl
            it.apiKey = config.apiKey
            it.model = config.model
            it.systemPrompt = config.systemPrompt
            it.prompt = config.prompt
        }
        block(next)
        config = next
        AiManager.save(context, next)
    }

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.material3BlurEffect(backdrop),
                title = { Text("AI 配置", modifier = Modifier.padding(start = 12.dp)) },
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
            item {
                SegmentedColumn(title = "接口") {
                    item {
                        BaseItemContainer {
                            Field(
                                label = "接口地址",
                                value = config.baseUrl.orEmpty(),
                                singleLine = true,
                                onValueChange = { text -> edit { it.baseUrl = text } },
                            )
                        }
                    }
                    item {
                        BaseItemContainer {
                            Field(
                                label = "API Key",
                                value = config.apiKey.orEmpty(),
                                singleLine = true,
                                secret = true,
                                onValueChange = { text -> edit { it.apiKey = text } },
                            )
                        }
                    }
                    item {
                        BaseItemContainer {
                            Field(
                                label = "模型",
                                value = config.model.orEmpty(),
                                singleLine = true,
                                onValueChange = { text -> edit { it.model = text } },
                            )
                        }
                    }
                }
            }

            item {
                SegmentedColumn(title = "连接") {
                    item {
                        NavigationItemWidget(
                            icon = AppIcons.Update,
                            title = if (fetching) "获取中…" else "获取模型列表",
                            description = "用当前的接口地址与 API Key 请求 /models，同时验证连通性",
                            onClick = {
                                if (fetching) return@NavigationItemWidget
                                fetching = true
                                AiManager.listModels(
                                    config,
                                    object : AiManager.ListCallback {
                                        override fun onSuccess(models: List<String>) {
                                            fetching = false
                                            onNotify(
                                                "连通，共 ${models.size} 个模型，例如 " +
                                                    models.take(2).joinToString("、"),
                                            )
                                            // Only fill in a model when the field is empty, so a
                                            // deliberate choice is never overwritten.
                                            if (config.model.isNullOrBlank() && models.isNotEmpty()) {
                                                edit { it.model = models.first() }
                                            }
                                        }

                                        override fun onError(message: String) {
                                            fetching = false
                                            onNotify("连接失败：" + message)
                                        }
                                    },
                                )
                            },
                        )
                    }
                }
            }

            item {
                SegmentedColumn(title = "提示词") {
                    item {
                        BaseItemContainer {
                            Field(
                                label = "系统提示词",
                                value = config.systemPrompt.orEmpty(),
                                minLines = 2,
                                maxLines = 6,
                                onValueChange = { text -> edit { it.systemPrompt = text } },
                            )
                        }
                    }
                    item {
                        BaseItemContainer {
                            Field(
                                label = "用户提示词",
                                value = config.prompt.orEmpty(),
                                minLines = 3,
                                maxLines = 8,
                                supporting = "含 {text} 时替换为捕获文本，否则作为人设拼在正文前",
                                onValueChange = { text -> edit { it.prompt = text } },
                            )
                        }
                    }
                }
            }

            item {
                SegmentedColumn(title = "预设") {
                    item {
                        NavigationItemWidget(
                            icon = AppIcons.License,
                            title = "套用预设",
                            description = "内置微软式翻译、微软式中文、Emoji",
                            onClick = { showPresetPicker = !showPresetPicker },
                        )
                    }
                    if (showPresetPicker) {
                        presetNames.forEach { name ->
                            item(key = name) {
                                NavigationItemWidget(
                                    title = name,
                                    description = "点按套用这个预设",
                                    onClick = {
                                        val preset = AiManager.loadPreset(context, name)
                                        // A builtin preset only carries a prompt: blank fields
                                        // mean "keep what is here", not "clear it".
                                        val merged = AiManager.Config().also {
                                            it.baseUrl = preset.baseUrl
                                                ?.takeIf { v -> v.isNotBlank() } ?: config.baseUrl
                                            it.apiKey = preset.apiKey
                                                ?.takeIf { v -> v.isNotBlank() } ?: config.apiKey
                                            it.model = preset.model
                                                ?.takeIf { v -> v.isNotBlank() } ?: config.model
                                            it.systemPrompt = preset.systemPrompt
                                            it.prompt = preset.prompt
                                        }
                                        config = merged
                                        AiManager.save(context, merged)
                                        onNotify("已套用预设「$name」")
                                        showPresetPicker = false
                                    },
                                )
                            }
                        }
                    }
                    item {
                        NavigationItemWidget(
                            icon = AppIcons.Settings,
                            title = "恢复默认",
                            description = "回到 DeepSeek 与默认提示词，API Key 会一并清空",
                            onClick = {
                                val fresh = AiManager.Config()
                                config = fresh
                                AiManager.save(context, fresh)
                                onNotify("已恢复默认")
                            },
                        )
                    }
                }
            }

            item {
                SegmentedColumn(title = "用量统计") {
                    item {
                        NavigationItemWidget(
                            title = "累计 ${stats.totalCalls} 次调用",
                            description = "输入 ${stats.totalPromptTokens} · 输出 " +
                                "${stats.totalCompletionTokens} · 合计 ${stats.totalTokens} tokens",
                            onClick = { stats = TokenStats.query(context, 0L, null) },
                        )
                    }
                    item {
                        NavigationItemWidget(
                            title = "缓存命中 ${stats.cachedTokens} tokens",
                            description = "${stats.cacheHitPercent()}% 的调用命中了提示词缓存",
                            onClick = { stats = TokenStats.query(context, 0L, null) },
                        )
                    }
                }
            }

            item {
                Text(
                    text = "当前版本 v${BuildConfig.VERSION_NAME} · AI 调用会消耗你的额度",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 24.dp),
                )
            }
        }
    }
}

/** A labelled text field sized to sit inside a segmented column item. */
@Composable
private fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = false,
    secret: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    supporting: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            visualTransformation = if (secret) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            supportingText = if (supporting != null) {
                { Text(supporting) }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
