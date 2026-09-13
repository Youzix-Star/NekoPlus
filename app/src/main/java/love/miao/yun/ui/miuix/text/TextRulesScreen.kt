/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 */

package love.miao.yun.ui.miuix.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import love.miao.yun.text.TextDefaults
import love.miao.yun.text.TextEngine
import love.miao.yun.text.TextPack
import love.miao.yun.text.TextPrefs
import love.miao.yun.text.TextRuleText
import love.miao.yun.text.TextRule
import love.miao.yun.text.TextRuleForm
import love.miao.yun.text.TextRules
import love.miao.yun.ui.miuix.miaoTextFieldColors
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The text-replacement page for the miuix engine.
 *
 * The rules themselves are edited as **text**, the same text that is stored: the list above is a
 * view of it, with a switch per rule and a count of what the parser could not read. That is a
 * deliberate split — a form for every condition and action would be a second, subtler copy of the
 * language, and the text is what the user can paste into a chat to share.
 *
 * The template buttons in the editor exist because "末尾加「喵」" should not require knowing the
 * syntax first.
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
    var packs by remember { mutableStateOf(TextPrefs.loadPacks(context)) }
    var hasLegacy by remember { mutableStateOf(TextPrefs.hasLegacyConfig(context)) }

    // The form editor's dialog: null is the rule list, -1 is a new rule, anything else is the rule
    // at that index. One dialog with two faces, so adding a rule does not open a dialog on top of a
    // dialog — which is what the platform's own dialogs do badly.
    var showGui by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var form by remember { mutableStateOf(TextRuleForm.Form()) }
    var appsText by remember { mutableStateOf("") }
    var showEditor by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var showPacks by remember { mutableStateOf(false) }
    var packName by remember { mutableStateOf("") }
    var sample by remember { mutableStateOf("今天我很好，你准备好了吗？") }
    // Bumped when the sample is re-run: the preview uses an engine of its own so that trying a rule
    // out never advances a 每 N 次 counter that the real one is counting.
    var trialKey by remember { mutableIntStateOf(0) }
    // What the parser could not read in the last text that was saved: shown where the user can see
    // it, instead of a rule vanishing quietly.
    var problems by remember { mutableStateOf<List<TextRuleText.Problem>>(emptyList()) }

    fun update(next: TextRules) {
        rules = next
        TextPrefs.save(context, next)
    }

    /** Opens the form for one rule; `null` starts a new one, seeded with the suffix rule. */
    fun edit(index: Int?) {
        form = if (index == null) {
            TextRuleForm.Form(
                action = TextRuleForm.Action.Suffix,
                first = "{" + TextDefaults.SUFFIX_NAME + "}",
            )
        } else {
            TextRuleForm.form(rules.rules[index])
        }
        appsText = if (index == null) "" else rules.rules[index].apps.joinToString(", ")
        editing = index ?: NEW_RULE
    }

    val preview = remember(rules, sample, trialKey) {
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
            Column {
                SmallTitle(text = "开关")
                Card(modifier = Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = "AI 修改后自动套用",
                        summary = "模型写完，规则收尾；关掉就只在你点「套用规则」时生效",
                        checked = autoAfterAi,
                        onCheckedChange = { value ->
                            autoAfterAi = value
                            TextPrefs.saveAutoAfterAi(context, value)
                        },
                    )
                }
            }
        }

        item(key = "rules-title") {
            SmallTitle(text = "规则（${rules.rules.size} 条）")
        }
        if (rules.rules.isEmpty()) {
            item(key = "rules-empty") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "还没有规则。规则是一行一条的文字：\n" +
                            "你好 = 您好\n" +
                            "当 含\"？\" 则 末尾加\"{后缀}\"",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        rules.rules.forEachIndexed { index, rule ->
            item(key = "rule-$index") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = TextRuleText.render(rule).removeSuffix("  # 已停用"),
                        endActions = {
                            Switch(
                                checked = rule.enabled,
                                onCheckedChange = { on ->
                                    update(
                                        rules.copy(
                                            rules = rules.rules.mapIndexed { at, item ->
                                                if (at == index) item.copy(enabled = on) else item
                                            },
                                        ),
                                    )
                                },
                            )
                        },
                        summary = if (rule.enabled) "点按用表单修改" else "已停用 · 点按用表单修改",
                        onClick = {
                            edit(index)
                            showGui = true
                        },
                    )
                }
            }
        }
        if (problems.isNotEmpty()) {
            item(key = "problems") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "有 ${problems.size} 行没读懂，保存时会跳过：\n" +
                            problems.joinToString("\n") { "第 ${it.line} 行：${it.message}" },
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item(key = "edit") {
            Column {
                SmallTitle(text = "编辑")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            editing = null
                            showGui = true
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("表单编辑")
                    }
                    Button(
                        onClick = {
                            draft = TextRuleText.render(rules)
                            showEditor = true
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("文本编辑")
                    }
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = "规则包",
                    summary = if (packs.isEmpty()) "只有内置的猫化、猫爪" else "已存 ${packs.size} 个",
                    onClick = { showPacks = true },
                )
                if (hasLegacy) {
                    ArrowPreference(
                        title = "从 1.1.8 导入",
                        summary = "读它的旧设置，翻译成规则",
                        onClick = {
                            val imported = TextPrefs.importLegacy(context)
                            if (imported == null) {
                                onNotify("没有找到 1.1.8 的旧设置")
                            } else {
                                update(imported)
                                problems = emptyList()
                                hasLegacy = false
                                onNotify("已导入 ${imported.rules.size} 条规则")
                            }
                        },
                    )
                }
                }
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
                label = "随便写一句",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "trial-result") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = preview.ifEmpty { "（结果是空的）" },
                    fontSize = 15.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item(key = "trial-run") {
            Button(
                onClick = {
                    trialKey++
                    onNotify("试跑了一次（每 N 次的计数不受影响）")
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("再跑一次（随机与每 N 次会变）")
            }
        }
    }

    if (showGui) {
        OverlayDialog(
            show = true,
            title = "编辑规则",
            onDismissRequest = {
                showGui = false
                editing = null
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val index = editing
                if (index == null) {
                    // ---- the rule list: this is what "表单编辑" opens
                    if (rules.rules.isEmpty()) {
                        Text(text = "还没有规则，点下面的按钮加一条。", fontSize = 13.sp)
                    }
                    rules.rules.forEachIndexed { at, rule ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            ArrowPreference(
                                title = TextRuleText.render(rule).removeSuffix("  # 已停用"),
                                summary = if (rule.enabled) "点按修改" else "已停用 · 点按修改",
                                endActions = {
                                    Button(
                                        onClick = {
                                            update(
                                                rules.copy(
                                                    rules = rules.rules.filterIndexed { i, _ -> i != at },
                                                ),
                                            )
                                        },
                                    ) {
                                        Text("删除")
                                    }
                                },
                                onClick = { edit(at) },
                            )
                        }
                    }
                    Button(onClick = { edit(null) }, modifier = Modifier.fillMaxWidth()) {
                        Text("添加一条规则")
                    }

                    SmallTitle(text = "变量（规则里写成 {名字}）")
                    rules.variables.forEach { (name, value) ->
                        TextField(
                            colors = miaoTextFieldColors(),
                            value = value,
                            onValueChange = { text ->
                                update(rules.copy(variables = rules.variables + (name to text)))
                            },
                            label = "{$name}",
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    // ---- the form for one rule
                    Card(modifier = Modifier.fillMaxWidth()) {
                        WindowSpinnerPreference(
                            title = "条件",
                            items = TextRuleForm.Condition.entries.map { DropdownItem(text = it.label) },
                            selectedIndex = form.condition.ordinal,
                            onSelectedIndexChange = { at ->
                                form = form.copy(condition = TextRuleForm.Condition.entries[at])
                            },
                        )
                    }
                    if (form.conditionWantsText) {
                        TextField(
                            colors = miaoTextFieldColors(),
                            value = form.conditionText,
                            onValueChange = { form = form.copy(conditionText = it) },
                            label = "条件里的字",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (form.conditionWantsNumber) {
                        TextField(
                            colors = miaoTextFieldColors(),
                            value = form.conditionNumber.toString(),
                            onValueChange = { text ->
                                text.toIntOrNull()?.let { form = form.copy(conditionNumber = it) }
                            },
                            label = "数值",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (form.condition.canNegate) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            SwitchPreference(
                                title = "反转",
                                summary = "不满足条件时才执行",
                                checked = form.negated,
                                onCheckedChange = { form = form.copy(negated = it) },
                            )
                        }
                    }

                    Card(modifier = Modifier.fillMaxWidth()) {
                        WindowSpinnerPreference(
                            title = "动作",
                            items = TextRuleForm.Action.entries.map { DropdownItem(text = it.label) },
                            selectedIndex = form.action.ordinal,
                            onSelectedIndexChange = { at ->
                                form = form.copy(action = TextRuleForm.Action.entries[at])
                            },
                        )
                    }
                    if (form.action.values >= 1) {
                        TextField(
                            colors = miaoTextFieldColors(),
                            value = form.first,
                            onValueChange = { form = form.copy(first = it) },
                            label = FIRST_LABEL[form.action] ?: "内容",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (form.action.values >= 2) {
                        TextField(
                            colors = miaoTextFieldColors(),
                            value = form.second,
                            onValueChange = { form = form.copy(second = it) },
                            label = "变成",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (form.action.hasSkipSpaced || form.action == TextRuleForm.Action.Replace) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            if (form.action.hasSkipSpaced) {
                                SwitchPreference(
                                    title = "空格不加",
                                    summary = "整段当成一句话，只在末尾加一次",
                                    checked = form.skipSpaced,
                                    onCheckedChange = { form = form.copy(skipSpaced = it) },
                                )
                            }
                            if (form.action == TextRuleForm.Action.Replace) {
                                SwitchPreference(
                                    title = "只替换第一个",
                                    checked = form.firstOnly,
                                    onCheckedChange = { form = form.copy(firstOnly = it) },
                                )
                            }
                        }
                    }

                    Text(text = "生效范围（留空是全部应用）", fontSize = 13.sp)
                    TextField(
                        colors = miaoTextFieldColors(),
                        value = appsText,
                        onValueChange = { appsText = it },
                        label = "包名，逗号分隔",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextRuleForm.KNOWN_APPS.forEach { (packageName, label) ->
                        Button(
                            onClick = {
                                val current = appsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                if (packageName !in current) {
                                    appsText = (current + packageName).joinToString(", ")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("加上 $label")
                        }
                    }

                    Button(
                        onClick = {
                            val apps = appsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                            val next = rules.rules.toMutableList()
                            if (index < 0) {
                                next += TextRule(
                                    condition = TextRuleForm.condition(form),
                                    action = TextRuleForm.action(form),
                                    apps = apps,
                                )
                            } else {
                                next[index] = TextRuleForm.rule(form, rules.rules[index]).copy(apps = apps)
                            }
                            update(rules.copy(rules = next))
                            editing = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (index < 0) "添加" else "保存")
                    }
                    if (index >= 0) {
                        Button(
                            onClick = {
                                update(
                                    rules.copy(
                                        rules = rules.rules.filterIndexed { at, _ -> at != index },
                                    ),
                                )
                                editing = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("删除这条规则")
                        }
                    }
                    Button(onClick = { editing = null }, modifier = Modifier.fillMaxWidth()) {
                        Text("返回列表")
                    }
                }

                Button(
                    onClick = {
                        showGui = false
                        editing = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("完成")
                }
            }
        }
    }

    if (showEditor) {
        OverlayDialog(
            show = true,
            title = "编辑规则",
            onDismissRequest = { showEditor = false },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "一行一条。`A = B` 是替换；`当 含\"？\" 则 末尾加\"喵\"` 是条件；" +
                        "`{后缀} = 喵` 是变量。",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                TextField(
                    colors = miaoTextFieldColors(),
                    value = draft,
                    onValueChange = { draft = it },
                    label = "规则",
                    maxLines = 14,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp),
                )
                Text(text = "插入模板", fontSize = 13.sp)
                TEMPLATES.forEach { template ->
                    Button(
                        onClick = {
                            draft = draft.trimEnd() + "\n" + template
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(template)
                    }
                }
                Button(
                    onClick = {
                        val parsed = TextRuleText.parse(draft)
                        update(parsed.config)
                        problems = parsed.problems
                        showEditor = false
                        onNotify(
                            if (parsed.problems.isEmpty()) {
                                "已保存 ${parsed.config.rules.size} 条规则"
                            } else {
                                "已保存，${parsed.problems.size} 行没读懂被跳过"
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("保存")
                }
            }
        }
    }

    if (showPacks) {
        OverlayDialog(
            show = true,
            title = "规则包",
            onDismissRequest = { showPacks = false },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BUILT_IN.forEach { (name, pack) ->
                    Button(
                        onClick = {
                            update(pack)
                            showPacks = false
                            onNotify("已载入「$name」")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("载入 $name（${pack.rules.size} 条）")
                    }
                }

                TextField(
                    colors = miaoTextFieldColors(),
                    value = packName,
                    onValueChange = { packName = it },
                    label = "把当前规则存为",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        val name = packName.trim()
                        if (name.isEmpty()) {
                            onNotify("先给规则包起个名字")
                        } else {
                            TextPrefs.savePack(context, name, rules)
                            packs = TextPrefs.loadPacks(context)
                            packName = ""
                            onNotify("已保存「$name」")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("保存当前规则")
                }

                packs.forEach { pack ->
                    SavedPackRow(
                        pack = pack,
                        onLoad = {
                            update(pack.rules)
                            showPacks = false
                            onNotify("已载入「${pack.name}」")
                        },
                        onDelete = {
                            TextPrefs.deletePack(context, pack.name)
                            packs = TextPrefs.loadPacks(context)
                            onNotify("已删除「${pack.name}」")
                        },
                    )
                }
            }
        }
    }
}

/** One saved pack: tap to load, and a button to throw it away. */
@Composable
private fun SavedPackRow(
    pack: TextPack,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        ArrowPreference(
            title = pack.name,
            summary = "${pack.rules.rules.size} 条规则",
            onClick = onLoad,
        )
        Button(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
            Text("删除「${pack.name}」")
        }
    }
}

/** The index the form uses for a rule that does not exist yet. */
private const val NEW_RULE = -1

/** What the first value of each action means, so the field says it instead of "内容". */
private val FIRST_LABEL: Map<TextRuleForm.Action, String> = mapOf(
    TextRuleForm.Action.Replace to "把",
    TextRuleForm.Action.Delete to "删掉",
    TextRuleForm.Action.Prefix to "加在开头",
    TextRuleForm.Action.Suffix to "加在末尾",
    TextRuleForm.Action.PerSentence to "每句末尾加",
    TextRuleForm.Action.Wrap to "首尾都加",
    TextRuleForm.Action.Regex to "正则",
)

/** The lines a user is most likely to want, so the syntax does not have to be learned first. */
private val TEMPLATES = listOf(
    "末尾加\"{后缀}\"",
    "每句末尾加\"{后缀}\"（空格不加）",
    "首尾包裹\"{猫爪}\"",
    "去掉末尾标点",
    "加颜文字",
    "当 随机 30% 则 加颜文字",
    "你好 = 您好",
    // The app scope is a package name. These two are the ones this feature exists for, but the
    // syntax takes any of them — a picker belongs with the form editor, which is not built yet.
    "末尾加\"{后缀}\" @com.tencent.mm",
    "首尾包裹\"{猫爪}\" @com.tencent.mobileqq",
)

/** 1.1.8's behaviour, kept as something one tap can bring back. */
private val BUILT_IN: List<Pair<String, TextRules>> = listOf(
    "猫化" to love.miao.yun.text.TextDefaults.meow(),
    "猫爪" to love.miao.yun.text.TextDefaults.catPaw(),
)
