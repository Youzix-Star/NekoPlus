/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 */

package love.miao.yun.ui.material3.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import love.miao.yun.text.TextDefaults
import love.miao.yun.text.TextEngine
import love.miao.yun.text.TextPack
import love.miao.yun.text.TextPrefs
import love.miao.yun.text.TextRule
import love.miao.yun.text.TextRuleForm
import love.miao.yun.text.TextRuleText
import love.miao.yun.text.TextRules
import love.miao.yun.ui.AppIcons
import love.miao.yun.ui.material3.material3AppBarColor
import love.miao.yun.ui.material3.material3BlurEffect
import love.miao.yun.ui.material3.rememberMaterial3BlurBackdrop
import love.miao.yun.ui.material3.widgets.BaseItemContainer
import love.miao.yun.ui.material3.widgets.BaseWidget
import love.miao.yun.ui.material3.widgets.DropDownMenuWidget
import love.miao.yun.ui.material3.widgets.FormField
import love.miao.yun.ui.material3.widgets.SegmentedColumn
import love.miao.yun.ui.material3.widgets.SwitchWidget
import top.yukonga.miuix.kmp.blur.layerBackdrop

/**
 * The text-replacement page for the Material engine.
 *
 * The same three things as the miuix engine's page and no more: one switch, the rules, two buttons.
 * Everything else moved into the editor it belongs to.
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
    var packs by remember { mutableStateOf(TextPrefs.loadPacks(context)) }
    var hasLegacy by remember { mutableStateOf(TextPrefs.hasLegacyConfig(context)) }

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

            if (rules.rules.isNotEmpty()) {
                item {
                    SegmentedColumn(title = "规则") {
                        rules.rules.forEachIndexed { at, rule ->
                            item {
                                BaseWidget(
                                    icon = AppIcons.Rule,
                                    title = TextRuleText.render(rule).removeSuffix("  # 已停用"),
                                    description = if (rule.enabled) "点按修改" else "已停用 · 点按修改",
                                    onClick = { open(at) },
                                    trailingContent = { _ ->
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
                                )
                            }
                        }
                    }
                }
            }

            item {
                BaseItemContainer {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = { face = Face.List; showForm = true },
                            modifier = Modifier.weight(1f),
                        ) {
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
        }
    }

    if (showForm) {
        AlertDialog(
            onDismissRequest = { showForm = false },
            title = {
                Text(
                    when (face) {
                        Face.Rule -> if (editing < 0) "添加规则" else "修改规则"
                        else -> "编辑规则"
                    },
                )
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        when (face) {
                            Face.List -> ListFace(
                                rules = rules,
                                onEdit = { open(it) },
                                onAdd = { open(null) },
                                onDelete = { at ->
                                    update(
                                        rules.copy(rules = rules.rules.filterIndexed { i, _ -> i != at }),
                                    )
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

                            Face.Rule -> RuleFace(
                                form = form,
                                onChange = { form = it },
                                appsText = appsText,
                                onAppsChange = { appsText = it },
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
                            )

                            Face.Packs -> PacksFace(
                                packs = packs,
                                name = packName,
                                onNameChange = { packName = it },
                                onSaveCurrent = {
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
                            )
                        }
                    }
                }
            },
            confirmButton = {
                when (face) {
                    Face.Rule -> TextButton(onClick = { save() }) { Text("保存") }
                    else -> TextButton(onClick = { showForm = false }) { Text("完成") }
                }
            },
            dismissButton = {
                when (face) {
                    Face.List -> Unit
                    else -> TextButton(onClick = { face = Face.List }) { Text("返回") }
                }
            },
        )
    }

    if (showText) {
        AlertDialog(
            onDismissRequest = { showText = false },
            title = { Text("文本编辑") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "一行一条：你好 = 您好；当 含\"？\" 则 末尾加\"喵\"；{猫爪} = ฅ",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = { Text("规则") },
                        minLines = 6,
                        maxLines = 12,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    )
                    if (problems > 0) {
                        Text(
                            text = "有 $problems 行没读懂，保存时会跳过。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(text = "试跑", style = MaterialTheme.typography.labelLarge)
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
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TEMPLATES.forEach { template ->
                            TextButton(
                                onClick = { draft = draft.trimEnd() + "\n" + template },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(template, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = TextRuleText.parse(draft)
                    update(parsed.config)
                    problems = parsed.problems.size
                    if (parsed.problems.isEmpty()) {
                        showText = false
                        onNotify("已保存 ${parsed.config.rules.size} 条规则")
                    } else {
                        onNotify("已保存，${parsed.problems.size} 行没读懂被跳过")
                    }
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showText = false }) { Text("取消") }
            },
        )
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
    Column(modifier = Modifier.fillMaxWidth()) {
        if (rules.rules.isEmpty()) {
            Text(text = "还没有规则。", style = MaterialTheme.typography.bodySmall)
        }
        rules.rules.forEachIndexed { at, rule ->
            Column {
                TextButton(onClick = { onEdit(at) }) {
                    Text(
                        TextRuleText.render(rule).removeSuffix("  # 已停用"),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(onClick = { onDelete(at) }) { Text("删除这条") }
            }
        }
        TextButton(onClick = onAdd) { Text("添加一条规则") }
        TextButton(onClick = onPacks) { Text("规则包") }
        if (hasLegacy) {
            TextButton(onClick = onImport) { Text("从 1.1.8 导入") }
        }

        Text(
            text = "变量（规则里写成 {名字}）",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 8.dp),
        )
        rules.variables.forEach { (name, value) ->
            FormField(
                label = "{$name}",
                value = value,
                maxLines = 4,
                onValueChange = { text -> onVariables(rules.variables + (name to text)) },
            )
        }
    }
}

@Composable
private fun RuleFace(
    form: TextRuleForm.Form,
    onChange: (TextRuleForm.Form) -> Unit,
    appsText: String,
    onAppsChange: (String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        DropDownMenuWidget(
            icon = AppIcons.Tune,
            title = "条件",
            choice = form.condition.ordinal,
            data = TextRuleForm.Condition.entries.map { it.label },
            onChoiceChange = { at -> onChange(form.copy(condition = TextRuleForm.Condition.entries[at])) },
        )
        if (form.conditionWantsText) {
            FormField(
                label = "条件里的字",
                value = form.conditionText,
                singleLine = true,
                onValueChange = { onChange(form.copy(conditionText = it)) },
            )
        }
        if (form.conditionWantsNumber) {
            FormField(
                label = "数值",
                value = form.conditionNumber.toString(),
                singleLine = true,
                onValueChange = { text ->
                    text.toIntOrNull()?.let { onChange(form.copy(conditionNumber = it)) }
                },
            )
        }

        DropDownMenuWidget(
            icon = AppIcons.Tune,
            title = "动作",
            choice = form.action.ordinal,
            data = TextRuleForm.Action.entries.map { it.label },
            onChoiceChange = { at -> onChange(form.copy(action = TextRuleForm.Action.entries[at])) },
        )
        if (form.action.values >= 1) {
            FormField(
                label = FIRST_LABEL[form.action] ?: "内容",
                value = form.first,
                singleLine = true,
                onValueChange = { onChange(form.copy(first = it)) },
            )
        }
        if (form.action.values >= 2) {
            FormField(
                label = "变成",
                value = form.second,
                singleLine = true,
                onValueChange = { onChange(form.copy(second = it)) },
            )
        }
        if (form.action.hasSkipSpaced) {
            SwitchWidget(
                icon = AppIcons.Tune,
                title = "空格不加",
                description = "整段当成一句话",
                checked = form.skipSpaced,
                onCheckedChange = { onChange(form.copy(skipSpaced = it)) },
            )
        }

        FormField(
            label = "只在哪些应用生效（如 com.tencent.mm）",
            value = appsText,
            singleLine = true,
            onValueChange = onAppsChange,
        )
        if (onDelete != null) {
            TextButton(onClick = onDelete) { Text("删除这条规则") }
        }
    }
}

@Composable
private fun PacksFace(
    packs: List<TextPack>,
    name: String,
    onNameChange: (String) -> Unit,
    onSaveCurrent: () -> Unit,
    onLoad: (TextPack) -> Unit,
    onDelete: (TextPack) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = "内置：猫化 = 1.1.8 的加喵 + 30% 颜文字。", style = MaterialTheme.typography.bodySmall)
        BUILT_IN.forEach { (label, pack) ->
            TextButton(onClick = { onLoad(pack) }) { Text(label) }
        }
        packs.forEach { pack ->
            Column {
                TextButton(onClick = { onLoad(pack) }) {
                    Text("载入「${pack.name}」（${pack.rules.rules.size} 条）")
                }
                TextButton(onClick = { onDelete(pack) }) { Text("删除「${pack.name}」") }
            }
        }
        FormField(
            label = "把当前规则存为",
            value = name,
            singleLine = true,
            onValueChange = onNameChange,
        )
        TextButton(onClick = onSaveCurrent) { Text("保存当前规则") }
    }
}

/** Which face of the editor dialog is showing. */
private enum class Face { List, Rule, Packs }

/** The index a rule gets before it exists, so the form can tell "add" from "change". */
private const val NEW_RULE = -1

/** The sentence the trial box starts with. */
private const val DEFAULT_SAMPLE = "今天我很好，你准备好了吗？"

/** The three lines worth a button. */
private val TEMPLATES = listOf("+喵", "+喵（空格不加）", "+猫爪")

/** What the first value of each action means. */
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
private val BUILT_IN: List<Pair<String, TextRules>> = listOf(
    "载入 猫化" to TextDefaults.meow(),
    "载入 猫爪" to TextDefaults.catPaw(),
)
