/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 */

package love.miao.yun.ui.miuix.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import love.miao.yun.text.TextDefaults
import love.miao.yun.text.TextEngine
import love.miao.yun.text.TextPack
import love.miao.yun.text.TextPrefs
import love.miao.yun.text.TextRule
import love.miao.yun.text.TextRuleForm
import love.miao.yun.text.TextRuleText
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
 * Three things, and nothing else: one switch, the rules, and two buttons. Everything that used to
 * sit here and did not earn its place — a trial box, the rule packs, the 1.1.8 import — moved into
 * the editor it belongs to, because a settings page that lists every capability is a settings page
 * nobody reads.
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

    // One dialog, three faces. Editing a rule, listing them and managing packs are the same job, and
    // stacking dialogs on dialogs is how the old version ended up with a page nobody could navigate.
    var showForm by remember { mutableStateOf(false) }
    var face by remember { mutableStateOf(Face.List) }
    var editing by remember { mutableStateOf(NEW_RULE) }
    var form by remember { mutableStateOf(TextRuleForm.Form()) }
    var appsText by remember { mutableStateOf("") }
    var packName by remember { mutableStateOf("") }

    var showText by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var problems by remember { mutableStateOf(0) }
    var sample by remember { mutableStateOf(DEFAULT_SAMPLE) }

    fun update(next: TextRules) {
        rules = next
        TextPrefs.save(context, next)
    }

    /** Opens the form for one rule; `null` is a new one, seeded with the suffix rule. */
    fun open(at: Int?) {
        form = if (at == null) {
            TextRuleForm.Form(
                action = TextRuleForm.Action.Suffix,
                first = "{" + TextDefaults.SUFFIX_NAME + "}",
            )
        } else {
            TextRuleForm.form(rules.rules[at])
        }
        appsText = if (at == null) "" else rules.rules[at].apps.joinToString(", ")
        editing = at ?: NEW_RULE
        face = Face.Rule
        showForm = true
    }

    fun save() {
        val apps = appsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val next = rules.rules.toMutableList()
        if (editing < 0) {
            next += TextRule(
                condition = TextRuleForm.condition(form),
                action = TextRuleForm.action(form),
                apps = apps,
            )
        } else {
            next[editing] = TextRuleForm.rule(form, rules.rules[editing]).copy(apps = apps)
        }
        update(rules.copy(rules = next))
        face = Face.List
    }

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

        if (rules.rules.isNotEmpty()) {
            item(key = "rules-title") {
                SmallTitle(text = "规则")
            }
            rules.rules.forEachIndexed { at, rule ->
                item(key = "rule-$at") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        ArrowPreference(
                            title = TextRuleText.render(rule).removeSuffix("  # 已停用"),
                            endActions = {
                                Switch(
                                    checked = rule.enabled,
                                    onCheckedChange = { on ->
                                        update(
                                            rules.copy(
                                                rules = rules.rules.mapIndexed { i, item ->
                                                    if (i == at) item.copy(enabled = on) else item
                                                },
                                            ),
                                        )
                                    },
                                )
                            },
                            onClick = { open(at) },
                        )
                    }
                }
            }
        }

        item(key = "edit") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { face = Face.List; showForm = true }, modifier = Modifier.weight(1f)) {
                    Text("表单编辑")
                }
                Button(
                    onClick = {
                        draft = TextRuleText.render(rules)
                        problems = 0
                        showText = true
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("文本编辑")
                }
            }
        }
    }

    if (showForm) {
        OverlayDialog(
            show = true,
            title = when (face) {
                Face.Rule -> if (editing < 0) "添加规则" else "修改规则"
                else -> "编辑规则"
            },
            onDismissRequest = { showForm = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (face) {
                    Face.List -> ListFace(
                        rules = rules,
                        onEdit = { open(it) },
                        onAdd = { open(null) },
                        onDelete = { at ->
                            update(rules.copy(rules = rules.rules.filterIndexed { i, _ -> i != at }))
                        },
                        onVariables = { update(rules.copy(variables = it)) },
                        onPacks = { face = Face.Packs },
                        onImport = {
                            val imported = TextPrefs.importLegacy(context)
                            if (imported == null) {
                                onNotify("没有找到 1.1.8 的旧设置")
                            } else {
                                update(imported)
                                hasLegacy = false
                                onNotify("已导入 ${imported.rules.size} 条规则")
                            }
                        },
                        hasLegacy = hasLegacy,
                    )

                    Face.Rule -> {
                        RuleFace(
                            form = form,
                            onChange = { form = it },
                            appsText = appsText,
                            onAppsChange = { appsText = it },
                            onSave = { save() },
                            onDelete = if (editing < 0) {
                                null
                            } else {
                                {
                                    update(
                                        rules.copy(
                                            rules = rules.rules.filterIndexed { i, _ -> i != editing },
                                        ),
                                    )
                                    face = Face.List
                                }
                            },
                            onBack = { face = Face.List },
                        )
                    }

                    Face.Packs -> PacksFace(
                        packs = packs,
                        name = packName,
                        onNameChange = { packName = it },
                        onSave = {
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
                        onLoad = { pack ->
                            update(pack.rules)
                            hasLegacy = false
                            face = Face.List
                            onNotify("已载入「${pack.name}」")
                        },
                        onDelete = { pack ->
                            TextPrefs.deletePack(context, pack.name)
                            packs = TextPrefs.loadPacks(context)
                        },
                        onBack = { face = Face.List },
                    )
                }
            }
        }
    }

    if (showText) {
        OverlayDialog(
            show = true,
            title = "文本编辑",
            onDismissRequest = { showText = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "一行一条：`你好 = 您好`、`当 含\"？\" 则 末尾加\"喵\"`、" +
                        "`{猫爪} = ฅ`。",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                TextField(
                    colors = miaoTextFieldColors(),
                    value = draft,
                    onValueChange = { draft = it },
                    label = "规则",
                    maxLines = 12,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp),
                )
                if (problems > 0) {
                    Text(
                        text = "有 $problems 行没读懂，保存时会跳过。",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }

                Text(text = "试跑", fontSize = 13.sp)
                TextField(
                    colors = miaoTextFieldColors(),
                    value = sample,
                    onValueChange = { sample = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(text = preview.ifEmpty { "（空的）" }, fontSize = 15.sp)

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TEMPLATES.forEach { template ->
                        Button(
                            onClick = { draft = draft.trimEnd() + "\n" + template },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(template)
                        }
                    }
                }

                Button(
                    onClick = {
                        val parsed = TextRuleText.parse(draft)
                        update(parsed.config)
                        problems = parsed.problems.size
                        if (parsed.problems.isEmpty()) {
                            showText = false
                            onNotify("已保存 ${parsed.config.rules.size} 条规则")
                        } else {
                            onNotify("已保存，${parsed.problems.size} 行没读懂被跳过")
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

// ------------------------------------------------------------------ the three faces

@Composable
private fun ListFace(
    rules: TextRules,
    onEdit: (Int) -> Unit,
    onAdd: () -> Unit,
    onDelete: (Int) -> Unit,
    onVariables: (Map<String, String>) -> Unit,
    onPacks: () -> Unit,
    onImport: () -> Unit,
    hasLegacy: Boolean,
) {
    if (rules.rules.isEmpty()) {
        Text(text = "还没有规则。", fontSize = 13.sp)
    }
    rules.rules.forEachIndexed { at, rule ->
        Card(modifier = Modifier.fillMaxWidth()) {
            ArrowPreference(
                title = TextRuleText.render(rule).removeSuffix("  # 已停用"),
                endActions = {
                    Button(onClick = { onDelete(at) }) { Text("删除") }
                },
                onClick = { onEdit(at) },
            )
        }
    }
    Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("添加一条规则") }

    Card(modifier = Modifier.fillMaxWidth()) {
        ArrowPreference(title = "规则包", onClick = onPacks)
        if (hasLegacy) {
            ArrowPreference(title = "从 1.1.8 导入", onClick = onImport)
        }
    }

    SmallTitle(text = "变量（规则里写成 {名字}）")
    rules.variables.forEach { (name, value) ->
        TextField(
            colors = miaoTextFieldColors(),
            value = value,
            onValueChange = { text -> onVariables(rules.variables + (name to text)) },
            label = "{$name}",
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RuleFace(
    form: TextRuleForm.Form,
    onChange: (TextRuleForm.Form) -> Unit,
    appsText: String,
    onAppsChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    onBack: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        WindowSpinnerPreference(
            title = "条件",
            items = TextRuleForm.Condition.entries.map { DropdownItem(text = it.label) },
            selectedIndex = form.condition.ordinal,
            onSelectedIndexChange = { at ->
                onChange(form.copy(condition = TextRuleForm.Condition.entries[at]))
            },
        )
    }
    if (form.conditionWantsText) {
        TextField(
            colors = miaoTextFieldColors(),
            value = form.conditionText,
            onValueChange = { onChange(form.copy(conditionText = it)) },
            label = "条件里的字",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (form.conditionWantsNumber) {
        TextField(
            colors = miaoTextFieldColors(),
            value = form.conditionNumber.toString(),
            onValueChange = { text -> text.toIntOrNull()?.let { onChange(form.copy(conditionNumber = it)) } },
            label = "数值",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        WindowSpinnerPreference(
            title = "动作",
            items = TextRuleForm.Action.entries.map { DropdownItem(text = it.label) },
            selectedIndex = form.action.ordinal,
            onSelectedIndexChange = { at ->
                onChange(form.copy(action = TextRuleForm.Action.entries[at]))
            },
        )
    }
    if (form.action.values >= 1) {
        TextField(
            colors = miaoTextFieldColors(),
            value = form.first,
            onValueChange = { onChange(form.copy(first = it)) },
            label = FIRST_LABEL[form.action] ?: "内容",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (form.action.values >= 2) {
        TextField(
            colors = miaoTextFieldColors(),
            value = form.second,
            onValueChange = { onChange(form.copy(second = it)) },
            label = "变成",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (form.action.hasSkipSpaced) {
        Card(modifier = Modifier.fillMaxWidth()) {
            SwitchPreference(
                title = "空格不加",
                summary = "整段当成一句话",
                checked = form.skipSpaced,
                onCheckedChange = { onChange(form.copy(skipSpaced = it)) },
            )
        }
    }

    // Empty means every app; the package name is typed, with the one everybody wants as the hint.
    TextField(
        colors = miaoTextFieldColors(),
        value = appsText,
        onValueChange = onAppsChange,
        label = "只在哪些应用生效（如 com.tencent.mm）",
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("保存") }
    if (onDelete != null) {
        Button(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("删除这条") }
    }
    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回") }
}

@Composable
private fun PacksFace(
    packs: List<TextPack>,
    name: String,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onLoad: (TextPack) -> Unit,
    onDelete: (TextPack) -> Unit,
    onBack: () -> Unit,
) {
    Text(text = "内置：载入「猫化」就是 1.1.8 的加喵 + 30% 颜文字。", fontSize = 12.sp)
    BUILT_IN.forEach { pack ->
        Button(onClick = { onLoad(pack) }, modifier = Modifier.fillMaxWidth()) {
            Text("载入 ${pack.name}")
        }
    }

    packs.forEach { pack ->
        Card(modifier = Modifier.fillMaxWidth()) {
            ArrowPreference(
                title = pack.name,
                summary = "${pack.rules.rules.size} 条规则",
                endActions = {
                    Button(onClick = { onDelete(pack) }) { Text("删除") }
                },
                onClick = { onLoad(pack) },
            )
        }
    }

    TextField(
        colors = miaoTextFieldColors(),
        value = name,
        onValueChange = onNameChange,
        label = "把当前规则存为",
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("保存当前规则") }
    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回") }
}

/** Which face of the editor dialog is showing. */
private enum class Face { List, Rule, Packs }

/** The index a rule gets before it exists, so the form can tell "add" from "change". */
private const val NEW_RULE = -1

/** The sentence the trial box starts with. */
private const val DEFAULT_SAMPLE = "今天我很好，你准备好了吗？"

/** The three lines worth a button; everything else is one edit away and in the README. */
private val TEMPLATES = listOf(
    "末尾加\"{后缀}\"",
    "每句末尾加\"{后缀}\"（空格不加）",
    "首尾包裹\"{猫爪}\"",
)

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

/** 1.1.8's behaviour, one tap away. */
private val BUILT_IN: List<TextPack> = listOf(
    TextPack("猫化", TextDefaults.meow()),
    TextPack("猫爪", TextDefaults.catPaw()),
)
