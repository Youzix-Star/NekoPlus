/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Rows are built from the segmented-column widgets ported from InstallerX-Revived (GPL-3.0).
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package love.miao.yun.ui.material3.floating

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import love.miao.yun.MiaoState
import love.miao.yun.floating.FloatingAction
import love.miao.yun.floating.FloatingIcon
import love.miao.yun.floating.FloatingItem
import love.miao.yun.floating.FloatingOptions
import love.miao.yun.floating.FloatingShape
import love.miao.yun.floating.FloatingWindowPrefs
import love.miao.yun.ui.AppIcons
import love.miao.yun.ui.FloatingColorSource
import love.miao.yun.ui.UiEnginePrefs
import love.miao.yun.ui.material3.material3AppBarColor
import love.miao.yun.ui.material3.material3BlurEffect
import love.miao.yun.ui.material3.rememberMaterial3BlurBackdrop
import love.miao.yun.ui.material3.widgets.BaseItemContainer
import love.miao.yun.ui.material3.widgets.BaseWidget
import love.miao.yun.ui.material3.widgets.DropDownMenuWidget
import love.miao.yun.ui.material3.widgets.IntNumberPickerWidget
import love.miao.yun.ui.material3.widgets.NavigationItemWidget
import love.miao.yun.ui.material3.widgets.SegmentedColumn
import love.miao.yun.ui.material3.widgets.SwitchWidget
import top.yukonga.miuix.kmp.blur.layerBackdrop

@Composable
fun MaterialFloatingScreen(
    outerPadding: PaddingValues,
    useBlur: Boolean,
    floatingRunning: Boolean,
    onToggleFloating: () -> Unit,
    onNotify: (String) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density

    var items by remember { mutableStateOf(FloatingWindowPrefs.load(context)) }
    var options by remember { mutableStateOf(FloatingWindowPrefs.loadOptions(context)) }

    /** Which button's editor is open; editing happens in place, under the row itself. */
    var expandedId by remember { mutableStateOf<String?>(null) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val backdrop = rememberMaterial3BlurBackdrop(useBlur)

    /** One funnel for edits, so the on-screen buttons and this list never drift apart. */
    fun persist(next: List<FloatingItem>) {
        items = next
        FloatingWindowPrefs.save(context, next)
    }

    fun persistOptions(next: FloatingOptions) {
        options = next
        FloatingWindowPrefs.saveOptions(context, next)
    }

    fun replace(id: String, transform: (FloatingItem) -> FloatingItem) {
        persist(items.map { if (it.id == id) transform(it) else it })
    }

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.material3BlurEffect(backdrop),
                title = { Text("悬浮窗", modifier = Modifier.padding(start = 12.dp)) },
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
                SegmentedColumn(title = "悬浮窗") {
                    item {
                        NavigationItemWidget(
                            icon = AppIcons.Floating,
                            title = if (floatingRunning) "收起悬浮窗" else "启动悬浮窗",
                            description = if (floatingRunning) "${items.count { it.enabled }} 个按钮" else "未启动",
                            onClick = {
                                onToggleFloating()
                                onNotify(if (floatingRunning) "悬浮窗已收起" else "悬浮窗已启动")
                            },
                        )
                    }
                }
            }

            item {
                SegmentedColumn(title = "拖动") {
                    item {
                        SwitchWidget(
                            icon = AppIcons.Tune,
                            title = "贴边吸附",
                            description = "松手后吸到屏幕边缘",
                            checked = options.snapToEdge,
                            onCheckedChange = {
                                persistOptions(options.copy(snapToEdge = it))
                            },
                        )
                    }
                    item {
                        SwitchWidget(
                            icon = AppIcons.Tune,
                            title = "拖动反馈",
                            description = "开始拖动时轻微震动",
                            checked = options.dragHaptic,
                            onCheckedChange = {
                                persistOptions(options.copy(dragHaptic = it))
                            },
                        )
                    }
                }
            }

            // Each button is its own thing: its own icon or label, its own tap and hold actions,
            // its own size, corner and opacity. Editing happens in place, so a change can be seen
            // on screen as it is made instead of being buried behind a dialog.
            item {
                SegmentedColumn(title = "悬浮窗按钮") {
                    items.forEach { entry ->
                        item(key = entry.id) {
                            // A button can be parked without being deleted: the switch takes it off
                            // the screen and leaves every one of its settings alone.
                            BaseWidget(
                                icon = AppIcons.Floating,
                                title = entry.displayName + "  ·  " + entry.actionSummary,
                                description = entry.summary + " · 点按编辑",
                                onClick = {
                                    expandedId = if (expandedId == entry.id) null else entry.id
                                },
                                trailingContent = { _ ->
                                    Switch(
                                        checked = entry.enabled,
                                        onCheckedChange = { enabled ->
                                            replace(entry.id) { it.copy(enabled = enabled) }
                                        },
                                    )
                                },
                            )
                        }

                        if (expandedId == entry.id) {
                            item(key = entry.id + "-icon") {
                                BaseItemContainer {
                                    IconGrid(
                                        selected = entry.iconEntry,
                                        onSelect = { icon ->
                                            replace(entry.id) { it.copy(icon = icon.id) }
                                        },
                                    )
                                }
                            }

                            if (entry.showsText) {
                                item(key = entry.id + "-text") {
                                    BaseItemContainer {
                                        OutlinedTextField(
                                            value = entry.text,
                                            onValueChange = { text ->
                                                replace(entry.id) { it.copy(text = text) }
                                            },
                                            label = { Text("按钮文字") },
                                            singleLine = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                        )
                                    }
                                }
                            }

                            item(key = entry.id + "-action") {
                                BaseItemContainer {
                                    ActionChain(
                                        title = "点击",
                                        actions = entry.actionEntries,
                                        allowNone = false,
                                        onChange = { chain ->
                                            replace(entry.id) { it.copy(actions = chain.map { a -> a.id }) }
                                        },
                                    )
                                }
                            }

                            item(key = entry.id + "-hold") {
                                BaseItemContainer {
                                    ActionChain(
                                        title = "长按",
                                        actions = entry.holdActionEntries,
                                        allowNone = true,
                                        onChange = { chain ->
                                            replace(entry.id) {
                                                it.copy(holdActions = chain.map { a -> a.id })
                                            }
                                        },
                                    )
                                }
                            }

                            item(key = entry.id + "-enabled") {
                                SwitchWidget(
                                    icon = AppIcons.Tune,
                                    title = "在屏幕上显示",
                                    description = "关掉就把它收起来，设置都留着",
                                    checked = entry.enabled,
                                    onCheckedChange = { enabled ->
                                        replace(entry.id) { it.copy(enabled = enabled) }
                                    },
                                )
                            }

                            if (!entry.showsText) {
                                item(key = entry.id + "-show-icon") {
                                    SwitchWidget(
                                        icon = AppIcons.Tune,
                                        title = "显示图标",
                                        description = if (entry.iconCramped && entry.showIcon) {
                                            "当前尺寸下图标会偏小或被压扁"
                                        } else {
                                            "关掉就是一条纯色块"
                                        },
                                        checked = entry.showIcon,
                                        onCheckedChange = { show ->
                                            replace(entry.id) { it.copy(showIcon = show) }
                                        },
                                    )
                                }
                            }

                            item(key = entry.id + "-shape") {
                                BaseItemContainer {
                                    TextChips(
                                        labels = FloatingShape.entries.map { it.label },
                                        // Nothing highlighted for a hand-made size, which is the
                                        // honest answer.
                                        selectedIndex = FloatingShape.of(entry)
                                            ?.let { FloatingShape.entries.indexOf(it) }
                                            ?: -1,
                                        onSelect = { index ->
                                            FloatingShape.entries.getOrNull(index)?.let { shape ->
                                                replace(entry.id) { shape.apply(it) }
                                            }
                                        },
                                    )
                                }
                            }

                            item(key = entry.id + "-width") {
                                BaseItemContainer {
                                    IntNumberPickerWidget(
                                        title = "宽度",
                                        value = entry.widthDp,
                                        startInt = FloatingWindowPrefs.MIN_SIZE_DP,
                                        endInt = FloatingWindowPrefs.MAX_SIZE_DP,
                                        valueSuffix = " dp",
                                        onValueChange = { width ->
                                            replace(entry.id) {
                                                it.copy(
                                                    widthDp = width,
                                                    cornerDp = it.cornerDp
                                                        .coerceAtMost(minOf(width, it.heightDp) / 2),
                                                )
                                            }
                                        },
                                    )
                                }
                            }

                            item(key = entry.id + "-height") {
                                BaseItemContainer {
                                    IntNumberPickerWidget(
                                        title = "高度",
                                        value = entry.heightDp,
                                        startInt = FloatingWindowPrefs.MIN_SIZE_DP,
                                        endInt = FloatingWindowPrefs.MAX_SIZE_DP,
                                        valueSuffix = " dp",
                                        onValueChange = { height ->
                                            replace(entry.id) {
                                                it.copy(
                                                    heightDp = height,
                                                    cornerDp = it.cornerDp
                                                        .coerceAtMost(minOf(it.widthDp, height) / 2),
                                                )
                                            }
                                        },
                                    )
                                }
                            }

                            item(key = entry.id + "-corner") {
                                BaseItemContainer {
                                    IntNumberPickerWidget(
                                        title = "圆角",
                                        value = entry.effectiveCornerDp,
                                        startInt = 0,
                                        endInt = (entry.shortEdgeDp / 2).coerceAtLeast(1),
                                        valueSuffix = " dp",
                                        onValueChange = { corner ->
                                            replace(entry.id) { it.copy(cornerDp = corner) }
                                        },
                                    )
                                }
                            }

                            item(key = entry.id + "-opacity") {
                                BaseItemContainer {
                                    IntNumberPickerWidget(
                                        title = "不透明度",
                                        value = entry.opacity,
                                        startInt = FloatingWindowPrefs.MIN_OPACITY,
                                        endInt = FloatingWindowPrefs.MAX_OPACITY,
                                        valueSuffix = " %",
                                        onValueChange = { opacity ->
                                            replace(entry.id) { it.copy(opacity = opacity) }
                                        },
                                    )
                                }
                            }

                            item(key = entry.id + "-position") {
                                NavigationItemWidget(
                                    icon = AppIcons.Tune,
                                    title = "重置位置",
                                    description = "放回屏幕左上角那一列",
                                    onClick = {
                                        val (x, y) = FloatingWindowPrefs.startPosition(
                                            context,
                                            items.indexOfFirst { it.id == entry.id }
                                                .coerceAtLeast(0),
                                        )
                                        replace(entry.id) { it.copy(x = x, y = y) }
                                    },
                                )
                            }

                            item(key = entry.id + "-delete") {
                                NavigationItemWidget(
                                    icon = AppIcons.Tune,
                                    title = "删除这个悬浮窗",
                                    description = "把它从屏幕上拿掉",
                                    onClick = {
                                        if (items.size > 1) {
                                            persist(items.filterNot { it.id == entry.id })
                                            expandedId = null
                                        } else {
                                            onNotify("至少要留一个悬浮窗")
                                        }
                                    },
                                )
                            }
                        }
                    }

                    item {
                        NavigationItemWidget(
                            icon = AppIcons.Update,
                            title = "添加悬浮窗",
                            description = "再放一个按钮",
                            onClick = {
                                val item = FloatingWindowPrefs.newItem(items, density)
                                persist(items + item)
                                expandedId = item.id
                            },
                        )
                    }
                }
            }

            item {
                SegmentedColumn(title = "外观") {
                    item {
                        DropDownMenuWidget(
                            icon = AppIcons.Floating,
                            title = "取色来源",
                            description = "悬浮窗的取色来源",
                            choice = FloatingColorSource.entries
                                .indexOf(MiaoState.floatingColorSource).coerceAtLeast(0),
                            data = FloatingColorSource.entries.map { it.label },
                            onChoiceChange = { index ->
                                FloatingColorSource.entries.getOrNull(index)?.let {
                                    MiaoState.floatingColorSource = it
                                    UiEnginePrefs.saveFloatingColor(context, it)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** The icon set, wrapped into rows so all of it is visible at once. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconGrid(selected: FloatingIcon, onSelect: (FloatingIcon) -> Unit) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FloatingIcon.entries.forEach { icon ->
            ChoiceCell(selected = icon == selected, onClick = { onSelect(icon) }) {
                if (icon.isText) {
                    Text(text = "文", style = MaterialTheme.typography.titleMedium)
                } else {
                    Icon(
                        painter = painterResource(icon.res),
                        contentDescription = icon.label,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

/**
 * The steps one gesture runs, in order.
 *
 * A chain rather than a single action, because the useful combinations are sequential: rewrite
 * with the model first and post-process the result afterwards, for instance. [allowNone] adds a
 * "无" choice in front of the very first step — that is how a hold gesture is switched off.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionChain(
    title: String,
    actions: List<FloatingAction>,
    allowNone: Boolean,
    onChange: (List<FloatingAction>) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextChips(
            labels = FloatingAction.entries.map { it.label },
            selectedIndex = when {
                actions.isEmpty() -> if (allowNone) 0 else -1
                allowNone -> FloatingAction.entries.indexOf(actions.first()) + 1
                else -> FloatingAction.entries.indexOf(actions.first())
            },
            onSelect = { choice ->
                val picked = FloatingAction.entries.getOrNull(
                    if (allowNone) choice - 1 else choice,
                )
                when {
                    allowNone && choice == 0 -> onChange(emptyList())
                    picked != null -> onChange(listOf(picked) + actions.drop(1))
                }
            },
            noneLabel = allowNone,
            pad = false,
        )

        actions.drop(1).forEachIndexed { index, action ->
            TextChips(
                labels = FloatingAction.entries.map { it.label },
                selectedIndex = FloatingAction.entries.indexOf(action),
                onSelect = { choice ->
                    FloatingAction.entries.getOrNull(choice)?.let { picked ->
                        val next = actions.toMutableList()
                        next[index + 1] = picked
                        onChange(next)
                    }
                },
                noneLabel = false,
                pad = false,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (actions.size < FloatingWindowPrefs.MAX_CHAIN) {
                ChoiceCell(
                    selected = false,
                    onClick = { onChange(actions + FloatingAction.entries.first()) },
                    wide = true,
                ) {
                    Text("＋ 添加一步", style = MaterialTheme.typography.labelLarge)
                }
            }
            // The tap chain always keeps one step; a hold chain can be emptied right out.
            if (actions.size > if (allowNone) 0 else 1) {
                ChoiceCell(
                    selected = false,
                    onClick = { onChange(actions.dropLast(1)) },
                    wide = true,
                ) {
                    Text("− 删掉最后一步", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** A row of worded choices wrapping onto as many lines as it needs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextChips(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    noneLabel: Boolean = false,
    pad: Boolean = true,
) {
    val options = if (noneLabel) listOf("无") + labels else labels
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (pad) Modifier.padding(horizontal = 16.dp, vertical = 12.dp) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { index, label ->
            ChoiceCell(selected = index == selectedIndex, onClick = { onSelect(index) }, wide = true) {
                Text(text = label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** One selectable cell: filled when it is the active choice. */
@Composable
private fun ChoiceCell(
    selected: Boolean,
    onClick: () -> Unit,
    wide: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceBright
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = if (wide) Modifier else Modifier.size(44.dp),
    ) {
        Box(
            modifier = Modifier.padding(
                horizontal = if (wide) 16.dp else 0.dp,
                vertical = if (wide) 9.dp else 0.dp,
            ),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
