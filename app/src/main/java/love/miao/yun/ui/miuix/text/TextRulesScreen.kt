/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 */

package love.miao.yun.ui.miuix.text

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import love.miao.yun.text.TextAction
import love.miao.yun.text.TextDefaults
import love.miao.yun.text.TextEngine
import love.miao.yun.text.TextPrefs
import love.miao.yun.text.TextRuleText
import love.miao.yun.text.TextRules
import love.miao.yun.text.TextSwitches
import love.miao.yun.ui.miuix.miaoTextFieldColors
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

/**
 * The text-replacement page for the miuix engine, in 1.1.8's shape.
 *
 * Three switches and a find-and-replace list, which is what 1.1.8 offered: 加喵, 颜文字, QQ 猫爪, and
 * `A = B` rules. The engine underneath can do far more — conditions, regex, variables, packs,
 * per-app rules — and none of it is on this page yet. Hiding it is deliberate: the version that
 * showed everything at once was unusable.
 */
@Composable
fun TextRulesScreen(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    onNotify: (String) -> Unit,
) {
    val context = LocalContext.current

    var rules by remember { mutableStateOf(TextPrefs.load(context)) }
    var autoAfterAi by remember { mutableStateOf(TextPrefs.loadAutoAfterAi(context)) }
    var sample by remember { mutableStateOf(DEFAULT_SAMPLE) }

    // The rules are edited as text, one per line: pasting a whole set in is the point, and 1.1.8
    // did the same. A form per rule would be a second, subtler copy of the syntax.
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "toggles-title") {
            SmallTitle(text = "处理")
        }
        item(key = "toggles") {
            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchPreference(
                    title = "AI 改完自动套用",
                    summary = "关掉只在点「套用规则」时生效",
                    checked = autoAfterAi,
                    onCheckedChange = { value ->
                        autoAfterAi = value
                        TextPrefs.saveAutoAfterAi(context, value)
                    },
                )
                TextSwitches.Toggle.entries.forEach { toggle ->
                    SwitchPreference(
                        title = toggle.label,
                        summary = toggle.summary,
                        checked = TextSwitches.isOn(rules, toggle),
                        onCheckedChange = { on -> update(TextSwitches.set(rules, toggle, on)) },
                    )
                }
            }
        }

        item(key = "rules-title") {
            SmallTitle(text = "替换规则")
        }
        item(key = "rules") {
            Card(modifier = Modifier.fillMaxWidth()) {
                if (replacements.isEmpty()) {
                    Text(
                        text = "还没有规则。",
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
                // No trailing button on these rows: a wide control in the end slot leaves the title
                // squeezed against the card's edge, which is what "the text is being eaten" was.
                // Deleting lives in the editor, where there is room to say what it does.
                replacements.forEachIndexed { at, rule ->
                    ArrowPreference(
                        title = TextRuleText.render(rule),
                        onClick = {
                            draft = TextRuleText.render(rule)
                            editing = at
                            showEdit = true
                        },
                    )
                }
                ArrowPreference(
                    title = "批量添加",
                    summary = "一行一条，粘贴文本即可",
                    onClick = {
                        draft = ""
                        editing = NEW_RULE
                        showEdit = true
                    },
                )
            }
        }

        item(key = "emoticons-title") {
            SmallTitle(text = "颜文字")
        }
        // No Card around it: miuix's TextField draws its own rounded surface, and wrapping it in a
        // second one puts the text against two borders at once.
        item(key = "emoticons") {
            TextField(
                colors = miaoTextFieldColors(),
                value = TextSwitches.emoticons(rules),
                onValueChange = { update(TextSwitches.setEmoticons(rules, it)) },
                label = "一行一个，留空用内置的 ${TextDefaults.EMOTICONS.size} 个",
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item(key = "trial-title") {
            SmallTitle(text = "试跑")
        }
        item(key = "trial-sample") {
            TextField(
                colors = miaoTextFieldColors(),
                value = sample,
                onValueChange = { sample = it },
                label = "写一句试试",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "trial-result") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = preview.ifEmpty { "（空的）" },
                    fontSize = 15.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
        }
    }

    if (showEdit) {
        OverlayDialog(
            show = true,
            title = "替换规则",
            onDismissRequest = { showEdit = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextField(
                    colors = miaoTextFieldColors(),
                    value = draft,
                    onValueChange = { draft = it },
                    label = "一行一条，例如 你好 = 您好",
                    maxLines = 12,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp),
                )
                if (editing >= 0) {
                    Button(
                        onClick = {
                            update(
                                TextSwitches.setReplacements(
                                    rules,
                                    replacements.filterIndexed { i, _ -> i != editing },
                                ),
                            )
                            showEdit = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("删除这条规则")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
        }
    }
}

/** The clipboard as text. Empty when there is nothing to paste. */
private fun clipboardText(context: Context): String = runCatching {
    val manager = context.getSystemService(ClipboardManager::class.java)
    manager?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
}.getOrNull().orEmpty()

/** The index a rule gets before it exists, so the editor can tell "add" from "change". */
private const val NEW_RULE = -1

/** The sentence the trial box starts with. */
private const val DEFAULT_SAMPLE = "今天我很好，你准备好了吗？"
