/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.nekoplus.ui.material3.text

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
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
import top.youzix.nekoplus.text.TextAction
import top.youzix.nekoplus.text.TextDefaults
import top.youzix.nekoplus.text.TextEngine
import top.youzix.nekoplus.text.TextPrefs
import top.youzix.nekoplus.text.TextRuleText
import top.youzix.nekoplus.text.TextRules
import top.youzix.nekoplus.text.TextSwitches
import top.youzix.nekoplus.ui.AppIcons
import top.youzix.nekoplus.ui.material3.material3AppBarColor
import top.youzix.nekoplus.ui.material3.material3BlurEffect
import top.youzix.nekoplus.ui.material3.rememberMaterial3BlurBackdrop
import top.youzix.nekoplus.ui.material3.widgets.BaseItemContainer
import top.youzix.nekoplus.ui.material3.widgets.BaseWidget
import top.youzix.nekoplus.ui.material3.widgets.FormField
import top.youzix.nekoplus.ui.material3.widgets.NavigationItemWidget
import top.youzix.nekoplus.ui.material3.widgets.SegmentedColumn
import top.youzix.nekoplus.ui.material3.widgets.SwitchWidget
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
    // What the box held before the last 套用, so 恢复 can put it back.
    var sampleBackup by remember { mutableStateOf<String?>(null) }

    // The rules are edited as text, one per line: pasting a whole set in is the point, and 1.1.8
    // did the same.
    var showEdit by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(NEW_RULE) }
    var draft by remember { mutableStateOf("") }

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
                SegmentedColumn(title = "处理") {
                    item {
                        SwitchWidget(
                            icon = AppIcons.Sparkle,
                            title = "AI 改完自动套用",
                            description = "关掉只在点「套用规则」时生效",
                            checked = autoAfterAi,
                            onCheckedChange = { value ->
                                autoAfterAi = value
                                TextPrefs.saveAutoAfterAi(context, value)
                            },
                        )
                    }
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
                    item {
                        BaseItemContainer {
                            if (replacements.isEmpty()) {
                                Text(
                                    text = "还没有规则。",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                )
                            }
                            replacements.forEachIndexed { at, rule ->
                                BaseWidget(
                                    icon = AppIcons.Rule,
                                    title = TextRuleText.render(rule),
                                    description = if (rule.enabled) "点按修改" else "已停用",
                                    onClick = {
                                        draft = TextRuleText.render(rule)
                                        editing = at
                                        showEdit = true
                                    },
                                )
                            }
                            NavigationItemWidget(
                                icon = AppIcons.Tune,
                                title = "批量添加",
                                description = "一行一条，粘贴文本即可",
                                onClick = {
                                    draft = ""
                                    editing = NEW_RULE
                                    showEdit = true
                                },
                            )
                        }
                    }
                }
            }

            item {
                SegmentedColumn(title = "颜文字") {
                    item {
                        BaseItemContainer {
                            FormField(
                                label = "一行一个，可增删（默认 ${TextDefaults.EMOTICONS.size} 个）",
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
                                label = "写一句试试",
                                value = sample,
                                singleLine = true,
                                onValueChange = { sample = it },
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Button(
                                    onClick = {
                                        if (sampleBackup == null) sampleBackup = sample
                                        sample = runCatching { TextEngine().apply(sample, rules, null) }
                                            .getOrDefault(sample)
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("套用")
                                }
                                Button(
                                    onClick = {
                                        sampleBackup?.let { sample = it }
                                        sampleBackup = null
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("恢复")
                                }
                            }
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
            title = { Text("替换规则") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = { Text("一行一条，例如 你好 = 您好") },
                        minLines = 6,
                        maxLines = 12,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (editing >= 0) {
                        TextButton(onClick = {
                            update(
                                TextSwitches.setReplacements(
                                    rules,
                                    replacements.filterIndexed { i, _ -> i != editing },
                                ),
                            )
                            showEdit = false
                        }) {
                            Text("删除这条规则")
                        }
                    }
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = { draft = clipboardText(context) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("粘贴")
                        }
                        Button(
                            onClick = {
                                val parsed = TextRuleText.parse(draft).config.rules
                                    .filter { it.action is TextAction.Replace }
                                if (parsed.isEmpty()) {
                                    onNotify("没读懂，写成一行的 原文 = 替换为")
                                } else {
                                    val next = replacements.toMutableList()
                                    if (editing < 0) {
                                        next += parsed
                                    } else {
                                        next.removeAt(editing)
                                        next.addAll(editing, parsed)
                                    }
                                    update(TextSwitches.setReplacements(rules, next))
                                    showEdit = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("保存")
                        }
                    }
                }
            },
            confirmButton = {
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
