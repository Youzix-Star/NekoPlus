/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 */

package love.miao.yun.ui.material3.text

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import love.miao.yun.text.TextDefaults
import love.miao.yun.text.TextEngine
import love.miao.yun.text.TextPack
import love.miao.yun.text.TextPrefs
import love.miao.yun.text.TextRuleText
import love.miao.yun.text.TextRules
import love.miao.yun.ui.AppIcons
import love.miao.yun.ui.material3.blur.material3AppBarColor
import love.miao.yun.ui.material3.blur.material3BlurEffect
import love.miao.yun.ui.material3.blur.layerBackdrop
import love.miao.yun.ui.material3.blur.rememberMaterial3BlurBackdrop
import love.miao.yun.ui.material3.widgets.BaseItemContainer
import love.miao.yun.ui.material3.widgets.BaseWidget
import love.miao.yun.ui.material3.widgets.NavigationItemWidget
import love.miao.yun.ui.material3.widgets.SegmentedColumn
import love.miao.yun.ui.material3.widgets.SwitchWidget

/**
 * The text-replacement page for the Material engine.
 *
 * The same split as the miuix engine's: the list is a view of the rules with a switch each, and the
 * rules themselves are edited as the text that is stored. One language, one editor — and the text
 * is something the user can paste into a chat to share.
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

    var showEditor by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var showPacks by remember { mutableStateOf(false) }
    var packName by remember { mutableStateOf("") }
    var sample by remember { mutableStateOf("今天我很好，你准备好了吗？") }
    // Re-runs the preview: it uses an engine of its own, so trying a rule out never advances a
    // 每 N 次 counter that the real one is counting.
    var trialKey by remember { mutableIntStateOf(0) }
    // What the parser could not read in the last text that was saved: shown where the user can see
    // it, instead of a rule vanishing quietly.
    var problems by remember { mutableStateOf<List<TextRuleText.Problem>>(emptyList()) }

    fun update(next: TextRules) {
        rules = next
        TextPrefs.save(context, next)
    }

    val preview = remember(rules, sample, trialKey) {
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
                            description = "模型写完，规则收尾",
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
                SegmentedColumn(title = "规则（${rules.rules.size} 条）") {
                    if (rules.rules.isEmpty()) {
                        item {
                            BaseItemContainer {
                                Text(
                                    text = "还没有规则。一行一条：\n你好 = 您好\n当 含\"？\" 则 末尾加\"{后缀}\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        }
                    }
                    rules.rules.forEachIndexed { index, rule ->
                        item {
                            BaseWidget(
                                icon = AppIcons.Rule,
                                title = TextRuleText.render(rule).removeSuffix("  # 已停用"),
                                description = if (rule.enabled) "点按编辑全部规则" else "已停用",
                                onClick = {
                                    draft = TextRuleText.render(rules)
                                    showEditor = true
                                },
                                trailingContent = { _ ->
                                    Switch(
                                        checked = rule.enabled,
                                        onCheckedChange = { on ->
                                            update(
                                                rules.copy(
                                                    rules = rules.rules.mapIndexed { at, entry ->
                                                        if (at == index) entry.copy(enabled = on) else entry
                                                    },
                                                ),
                                            )
                                        },
                                    )
                                },
                            )
                        }
                    }
                    if (problems.isNotEmpty()) {
                        item {
                            BaseItemContainer {
                                Text(
                                    text = "有 ${problems.size} 行没读懂，保存时会跳过：\n" +
                                        problems.joinToString("\n") { "第 ${it.line} 行：${it.message}" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        }
                    }
                }
            }

            item {
                SegmentedColumn(title = "编辑") {
                    item {
                        NavigationItemWidget(
                            icon = AppIcons.Tune,
                            title = "编辑规则",
                            description = "用文本编辑全部规则和变量",
                            onClick = {
                                draft = TextRuleText.render(rules)
                                showEditor = true
                            },
                        )
                    }
                    item {
                        NavigationItemWidget(
                            icon = AppIcons.License,
                            title = "规则包",
                            description = if (packs.isEmpty()) "只有内置的猫化、猫爪" else "已存 ${packs.size} 个",
                            onClick = { showPacks = true },
                        )
                    }
                    if (hasLegacy) {
                        item {
                            NavigationItemWidget(
                                icon = AppIcons.Refresh,
                                title = "从 1.1.8 导入",
                                description = "读它的旧设置，翻译成规则",
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

            item {
                SegmentedColumn(title = "试跑") {
                    item {
                        BaseItemContainer {
                            OutlinedTextField(
                                value = sample,
                                onValueChange = { sample = it },
                                label = { Text("随便写一句") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                            Text(
                                text = preview.ifEmpty { "（结果是空的）" },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }
                    item {
                        NavigationItemWidget(
                            icon = AppIcons.Refresh,
                            title = "再跑一次",
                            description = "随机与每 N 次的计数会变",
                            onClick = {
                                trialKey++
                                onNotify("试跑了一次（每 N 次的计数不受影响）")
                            },
                        )
                    }
                }
            }
        }
    }

    if (showEditor) {
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text("编辑规则") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "一行一条。A = B 是替换；当 含\"？\" 则 末尾加\"喵\" 是条件；" +
                            "{后缀} = 喵 是变量。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = { Text("规则") },
                        textStyle = MaterialTheme.typography.bodySmall
                            .copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    )
                    Text("插入模板", style = MaterialTheme.typography.labelLarge)
                    TEMPLATES.forEach { template ->
                        TextButton(onClick = { draft = draft.trimEnd() + "\n" + template }) {
                            Text(template)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
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
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditor = false }) { Text("取消") }
            },
        )
    }

    if (showPacks) {
        AlertDialog(
            onDismissRequest = { showPacks = false },
            title = { Text("规则包") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    BUILT_IN.forEach { (name, pack) ->
                        TextButton(onClick = {
                            update(pack)
                            showPacks = false
                            onNotify("已载入「$name」")
                        }) {
                            Text("载入 $name（${pack.rules.size} 条）")
                        }
                    }
                    OutlinedTextField(
                        value = packName,
                        onValueChange = { packName = it },
                        label = { Text("把当前规则存为") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    )
                    TextButton(onClick = {
                        val name = packName.trim()
                        if (name.isEmpty()) {
                            onNotify("先给规则包起个名字")
                        } else {
                            TextPrefs.savePack(context, name, rules)
                            packs = TextPrefs.loadPacks(context)
                            packName = ""
                            onNotify("已保存「$name」")
                        }
                    }) {
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
            },
            confirmButton = {
                TextButton(onClick = { showPacks = false }) { Text("关闭") }
            },
        )
    }
}

/** One saved pack: tap the name to load it, and a button to throw it away. */
@Composable
private fun SavedPackRow(
    pack: TextPack,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
) {
    Column {
        TextButton(onClick = onLoad) { Text("载入「${pack.name}」（${pack.rules.rules.size} 条）") }
        TextButton(onClick = onDelete) { Text("删除「${pack.name}」") }
    }
}

/** The lines a user is most likely to want, so the syntax does not have to be learned first. */
private val TEMPLATES = listOf(
    "末尾加\"{后缀}\"",
    "每句末尾加\"{后缀}\"（空格不加）",
    "首尾包裹\"{猫爪}\"",
    "去掉末尾标点",
    "加颜文字",
    "当 随机 30% 则 加颜文字",
    "你好 = 您好",
)

/** 1.1.8's behaviour, kept as something one tap can bring back. */
private val BUILT_IN: List<Pair<String, TextRules>> = listOf(
    "猫化" to TextDefaults.meow(),
    "猫爪" to TextDefaults.catPaw(),
)
