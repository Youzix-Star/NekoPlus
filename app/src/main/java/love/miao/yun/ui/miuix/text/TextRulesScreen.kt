/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 */

package love.miao.yun.ui.miuix.text

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import top.yukonga.miuix.kmp.theme.MiuixTheme

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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "switch") {
            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchPreference(
                    title = "AI 修改后自动套用",
                    summary = "关掉就只在你点「套用规则」时生效",
                    checked = autoAfterAi,
                    onCheckedChange = { value ->
                        autoAfterAi = value
                        TextPrefs.saveAutoAfterAi(context, value)
                    },
                )
            }
        }

        item(key = "toggles-title") {
            SmallTitle(text = "文字处理")
        }
        TextSwitches.Toggle.entries.forEach { toggle ->
            item(key = "toggle-${toggle.name}") {
                Card(modifier = Modifier.fillMaxWidth()) {
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
        if (replacements.isEmpty()) {
            item(key = "rules-empty") {
                Text(text = "还没有替换规则。", fontSize = 13.sp)
            }
        }
        replacements.forEachIndexed { at, rule ->
            item(key = "rule-$at") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = TextRuleText.render(rule),
                        endActions = {
                            Button(
                                onClick = {
                                    update(
                                        TextSwitches.setReplacements(
                                            rules,
                                            replacements.filterIndexed { i, _ -> i != at },
                                        ),
                                    )
                                },
                            ) {
                                Text("删除")
                            }
                        },
                        onClick = {
                            val replace = rule.action as? TextAction.Replace ?: return@ArrowPreference
                            from = replace.from
                            to = replace.to
                            editing = at
                            showEdit = true
                        },
                    )
                }
            }
        }
        item(key = "rules-add") {
            Button(
                onClick = {
                    from = ""
                    to = ""
                    editing = NEW_RULE
                    showEdit = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("新增替换规则")
            }
        }

        item(key = "emoticons-title") {
            SmallTitle(text = "自定义颜文字")
        }
        item(key = "emoticons") {
            Card(modifier = Modifier.fillMaxWidth()) {
                TextField(
                    colors = miaoTextFieldColors(),
                    value = TextSwitches.emoticons(rules),
                    onValueChange = { update(TextSwitches.setEmoticons(rules, it)) },
                    label = "一行一个，留空用内置的 ${TextDefaults.EMOTICONS.size} 个",
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item(key = "trial-title") {
            SmallTitle(text = "试跑")
        }
        item(key = "trial-sample") {
            TextField(
                colors = miaoTextFieldColors(),
                value = sample,
                onValueChange = { sample = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "trial-result") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(text = preview.ifEmpty { "（空的）" }, fontSize = 15.sp)
            }
        }
    }

    if (showEdit) {
        OverlayDialog(
            show = true,
            title = if (editing < 0) "新增替换规则" else "修改替换规则",
            onDismissRequest = { showEdit = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextField(
                    colors = miaoTextFieldColors(),
                    value = from,
                    onValueChange = { from = it },
                    label = "原文",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    colors = miaoTextFieldColors(),
                    value = to,
                    onValueChange = { to = it },
                    label = "替换为",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "也可以从别处复制一整行 `原文 = 替换为`，粘进来。",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Button(
                    onClick = {
                        val paste = clipboardText(context)
                        val pasted = TextRuleText.parse(paste).config.rules.firstOrNull()
                        val replace = pasted?.action as? TextAction.Replace
                        if (replace == null) {
                            onNotify("剪贴板里没有一行「原文 = 替换为」")
                        } else {
                            from = replace.from
                            to = replace.to
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("粘贴一行")
                }
                Button(
                    onClick = {
                        if (from.isEmpty()) {
                            onNotify("原文不能是空的")
                        } else {
                            val next = replacements.toMutableList()
                            val rule = TextSwitches.replacement(from, to)
                            if (editing < 0) next += rule else next[editing] = rule
                            update(TextSwitches.setReplacements(rules, next))
                            showEdit = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("保存")
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
