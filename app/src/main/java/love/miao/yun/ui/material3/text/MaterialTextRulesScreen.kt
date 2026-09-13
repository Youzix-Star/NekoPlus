/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 */

package love.miao.yun.ui.material3.text

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import love.miao.yun.text.TextAction
import love.miao.yun.text.TextDefaults
import love.miao.yun.text.TextEngine
import love.miao.yun.text.TextPrefs
import love.miao.yun.text.TextRuleText
import love.miao.yun.text.TextRules
import love.miao.yun.text.TextSwitches
import love.miao.yun.ui.AppIcons
import love.miao.yun.ui.material3.material3AppBarColor
import love.miao.yun.ui.material3.material3BlurEffect
import love.miao.yun.ui.material3.rememberMaterial3BlurBackdrop
import love.miao.yun.ui.material3.widgets.BaseItemContainer
import love.miao.yun.ui.material3.widgets.BaseWidget
import love.miao.yun.ui.material3.widgets.FormField
import love.miao.yun.ui.material3.widgets.NavigationItemWidget
import love.miao.yun.ui.material3.widgets.SegmentedColumn
import love.miao.yun.ui.material3.widgets.SwitchWidget
import top.yukonga.miuix.kmp.blur.layerBackdrop

/**
 * The text-replacement page for the Material engine, in 1.1.8's shape: three switches and a
 * find-and-replace list. The rest of the engine is parked, deliberately.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialTextRulesScreen(
    onBack: () -> Unit,
    useBlur: Boolean,
    onNotify: (String) -> Unit,
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val backdrop = rememberMaterial3BlurBackdrop(useBlur)

    var rules by remember { mutableStateOf(TextPrefs.load(context)) }
    var autoAfterAi by remember { mutableStateOf(TextPrefs.loadAutoAfterAi(context)) }
    var sample by remember { mutableStateOf(DEFAULT_SAMPLE) }

    var showEdit by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(NEW_RULE) }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }

    fun update(next: TextRules) {
        rules = next
        TextPrefs.save(context, next)
    }

    val replacements = TextSwitches.replacements(rules)
    val preview = remember(rules, sample) {
        runCatching { TextEngine().apply(sample, rules, null) }.getOrDefault("")
    }

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                modifier = Modifier.material3BlurEffect(backdrop),
                title = { Text("文本替换", modifier = Modifier.padding(start = 12.dp)) },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
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
            contentPadding = paddingValues + PaddingValues(bottom = 24.dp),
        ) {
            item {
                SegmentedColumn(title = "开关") {
                    item {
                        SwitchWidget(
                            icon = AppIcons.Sparkle,
                            title = "AI 修改后自动套用",
                            description = "关掉就只在你点「套用规则」时生效",
                            checked = autoAfterAi,
                            onCheckedChange = { value ->
                                autoAfterAi = value
                                TextPrefs.saveAutoAfterAi(context, value)
                            },
                        )
                    }
                }
            }

            item {
                SegmentedColumn(title = "文字处理") {
                    TextSwitches.Toggle.entries.forEach { toggle ->
                        item {
                            SwitchWidget(
                                icon = AppIcons.Rule,
                                title = toggle.label,
                                description = toggle.summary,
                                checked = TextSwitches.isOn(rules, toggle),
                                onCheckedChange = { on -> update(TextSwitches.set(rules, toggle, on)) },
                            )
                        }
                    }
                }
            }

            item {
                SegmentedColumn(title = "替换规则") {
                    if (replacements.isEmpty()) {
                        item {
                            BaseItemContainer {
                                Text(
                                    text = "还没有替换规则。",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        }
                    }
                    replacements.forEachIndexed { at, rule ->
                        item {
                            BaseWidget(
                                icon = AppIcons.Rule,
                                title = TextRuleText.render(rule),
                                description = if (rule.enabled) "点按修改" else "已停用",
                                onClick = {
                                    val replace = rule.action as? TextAction.Replace
                                    if (replace != null) {
                                        from = replace.from
                                        to = replace.to
                                        editing = at
                                        showEdit = true
                                    }
                                },
                                trailingContent = { _ ->
                                    TextButton(onClick = {
                                        update(
                                            TextSwitches.setReplacements(
                                                rules,
                                                replacements.filterIndexed { i, _ -> i != at },
                                            ),
                                        )
                                    }) {
                                        Text("删除")
                                    }
                                },
                            )
                        }
                    }
                    item {
                        NavigationItemWidget(
                            icon = AppIcons.Tune,
                            title = "新增替换规则",
                            description = "也可以粘一整行「原文 = 替换为」",
                            onClick = {
                                from = ""
                                to = ""
                                editing = NEW_RULE
                                showEdit = true
                            },
                        )
                    }
                }
            }

            item {
                SegmentedColumn(title = "自定义颜文字") {
                    item {
                        BaseItemContainer {
                            FormField(
                                label = "一行一个，留空用内置的 ${TextDefaults.EMOTICONS.size} 个",
                                value = TextSwitches.emoticons(rules),
                                maxLines = 8,
                                onValueChange = { update(TextSwitches.setEmoticons(rules, it)) },
                            )
                        }
                    }
                }
            }

            item {
                SegmentedColumn(title = "试跑") {
                    item {
                        BaseItemContainer {
                            FormField(
                                label = "",
                                value = sample,
                                singleLine = true,
                                onValueChange = { sample = it },
                            )
                            Text(
                                text = preview.ifEmpty { "（空的）" },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEdit) {
        AlertDialog(
            onDismissRequest = { showEdit = false },
            title = { Text(if (editing < 0) "新增替换规则" else "修改替换规则") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    FormField(
                        label = "原文",
                        value = from,
                        singleLine = true,
                        onValueChange = { from = it },
                    )
                    FormField(
                        label = "替换为",
                        value = to,
                        singleLine = true,
                        onValueChange = { to = it },
                    )
                    Text(
                        text = "也可以从别处复制一整行「原文 = 替换为」，粘进来。",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    TextButton(onClick = {
                        val pasted = TextRuleText.parse(clipboardText(context)).config.rules.firstOrNull()
                        val replace = pasted?.action as? TextAction.Replace
                        if (replace == null) {
                            onNotify("剪贴板里没有一行「原文 = 替换为」")
                        } else {
                            from = replace.from
                            to = replace.to
                        }
                    }) {
                        Text("粘贴一行")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (from.isEmpty()) {
                        onNotify("原文不能是空的")
                    } else {
                        val next = replacements.toMutableList()
                        val rule = TextSwitches.replacement(from, to)
                        if (editing < 0) next += rule else next[editing] = rule
                        update(TextSwitches.setReplacements(rules, next))
                        showEdit = false
                    }
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEdit = false }) { Text("取消") }
            },
        )
    }
}

/** The clipboard as text. Empty when there is nothing to paste. */
private fun clipboardText(context: Context): String = runCatching {
    val manager = context.getSystemService(ClipboardManager::class.java)
    manager?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
}.getOrNull().orEmpty()

/** The index a rule gets before it exists. */
private const val NEW_RULE = -1

/** The sentence the trial box starts with. */
private const val DEFAULT_SAMPLE = "今天我很好，你准备好了吗？"
