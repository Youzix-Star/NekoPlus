/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package love.miao.yun.ui.miuix.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import love.miao.yun.ai.AiManager
import love.miao.yun.ui.miuix.miaoTextFieldColors
import love.miao.yun.ai.TokenStats
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The AI configuration page for the miuix engine.
 *
 * Every edit is mirrored into [AiManager.save] immediately, so there is no save button to
 * forget and leaving the page never drops a prompt that was just typed.
 */
@Composable
fun AiConfigScreen(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    onNotify: (String) -> Unit,
) {
    val context = LocalContext.current

    var config by remember { mutableStateOf(AiManager.load(context)) }
    var stats by remember { mutableStateOf(TokenStats.query(context, 0L, null)) }
    var showPresetPicker by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }
    var fetching by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var showModelPicker by remember { mutableStateOf(false) }

    val presetNames = remember { AiManager.getAllPresetNames(context) }
    /** A single funnel for edits, so the local copy and SharedPreferences never drift apart. */
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // miuix's TextField draws its own squircle background, so it is already a card.
        // Wrapping a stack of them in another Card nested two rounded surfaces with no gap
        // between them, which is what made this section read as one indistinct block.
        item(key = "endpoint-title") {
            SmallTitle(text = "接口")
        }
        item(key = "base-url") {
            TextField(
                colors = miaoTextFieldColors(),
                value = config.baseUrl.orEmpty(),
                onValueChange = { text -> edit { it.baseUrl = text } },
                label = "接口地址",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "api-key") {
            TextField(
                colors = miaoTextFieldColors(),
                value = config.apiKey.orEmpty(),
                onValueChange = { text -> edit { it.apiKey = text } },
                label = "API Key",
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "model") {
            TextField(
                colors = miaoTextFieldColors(),
                value = config.model.orEmpty(),
                onValueChange = { text -> edit { it.model = text } },
                label = "模型",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item(key = "fetch") {
            Column {
                SmallTitle(text = "连接")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            if (fetching) return@Button
                            fetching = true
                            AiManager.listModels(
                                config,
                                object : AiManager.ListCallback {
                                    override fun onSuccess(found: List<String>) {
                                        fetching = false
                                        // Straight into a picker: the list is the point of the
                                        // request, and typing a model name by hand is exactly the
                                        // busywork this saves.
                                        if (found.isEmpty()) {
                                            onNotify("连通，但接口没有返回模型")
                                        } else {
                                            models = found
                                            showModelPicker = true
                                        }
                                    }

                                    override fun onError(message: String) {
                                        fetching = false
                                        onNotify("连接失败：" + message)
                                    }
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (fetching) "获取中…" else "获取模型列表")
                    }
                }
            }
        }

        item(key = "prompt-title") {
            SmallTitle(text = "提示词")
        }
        item(key = "system-prompt") {
            TextField(
                colors = miaoTextFieldColors(),
                value = config.systemPrompt.orEmpty(),
                onValueChange = { text -> edit { it.systemPrompt = text } },
                label = "系统提示词",
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "prompt") {
            TextField(
                colors = miaoTextFieldColors(),
                value = config.prompt.orEmpty(),
                onValueChange = { text -> edit { it.prompt = text } },
                label = "用户提示词",
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item(key = "preset") {
            Column {
                SmallTitle(text = "预设")
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "套用预设",
                        summary = "内置三个预设",
                        onClick = { showPresetPicker = true },
                    )
                    ArrowPreference(
                        title = "保存为预设",
                        summary = "存下当前的配置",
                        onClick = {
                            presetName = ""
                            showSaveDialog = true
                        },
                    )
                }
            }
        }

        item(key = "stats") {
            Column {
                SmallTitle(text = "用量统计")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "累计 ${stats.totalCalls} 次调用",
                            style = MiuixTheme.textStyles.title4,
                        )
                        Text(
                            text = "输入 ${stats.totalPromptTokens} · 输出 " +
                                "${stats.totalCompletionTokens} · 合计 ${stats.totalTokens}",
                            style = MiuixTheme.textStyles.body2,
                        )
                        Text(
                            text = "缓存命中 ${stats.cachedTokens} tokens" +
                                "（${stats.cacheHitPercent()}% 的调用命中）",
                            style = MiuixTheme.textStyles.body2,
                        )
                    }
                }
            }
        }

        item(key = "refresh") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { stats = TokenStats.query(context, 0L, null) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("刷新统计")
                }
            }
        }
    }

    OverlayDialog(
        show = showModelPicker,
        title = "选择模型",
        summary = "共 ${models.size} 个",
        onDismissRequest = { showModelPicker = false },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            models.forEach { model ->
                ArrowPreference(
                    title = model,
                    summary = if (model == config.model) "当前使用" else null,
                    onClick = {
                        edit { it.model = model }
                        showModelPicker = false
                        onNotify("已选择 $model")
                    },
                )
            }
        }
    }

    OverlayDialog(
        show = showPresetPicker,
        title = "套用预设",
        onDismissRequest = { showPresetPicker = false },
    ) {
        Column {
            presetNames.forEach { name ->
                ArrowPreference(
                    title = name,
                    onClick = {
                        val preset = AiManager.loadPreset(context, name)
                        // A builtin preset only carries a prompt: blank fields mean "keep what
                        // is already here", not "clear it".
                        val merged = AiManager.Config().also {
                            it.baseUrl = preset.baseUrl?.takeIf { v -> v.isNotBlank() }
                                ?: config.baseUrl
                            it.apiKey = preset.apiKey?.takeIf { v -> v.isNotBlank() }
                                ?: config.apiKey
                            it.model = preset.model?.takeIf { v -> v.isNotBlank() }
                                ?: config.model
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

    OverlayDialog(
        show = showSaveDialog,
        title = "保存为预设",
        onDismissRequest = { showSaveDialog = false },
    ) {
        Column {
            TextField(
                colors = miaoTextFieldColors(),
                value = presetName,
                onValueChange = { presetName = it },
                label = "预设名",
                singleLine = true,
            )
            Button(
                onClick = {
                    val name = presetName.trim()
                    if (name.isNotEmpty()) {
                        AiManager.savePreset(context, name, config)
                        onNotify("已保存预设「$name」")
                    }
                    showSaveDialog = false
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存")
            }
        }
    }
}
