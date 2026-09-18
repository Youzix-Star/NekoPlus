/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package love.miao.yun.ui.miuix.floating

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import love.miao.yun.MiaoState
import love.miao.yun.R
import love.miao.yun.floating.FloatingAction
import love.miao.yun.floating.FloatingIcon
import love.miao.yun.floating.FloatingItem
import love.miao.yun.floating.FloatingOptions
import love.miao.yun.floating.FloatingShape
import love.miao.yun.floating.FloatingWindowPrefs
import love.miao.yun.sendassist.SEND_TARGETS
import love.miao.yun.sendassist.SendAssistAction
import love.miao.yun.sendassist.SendAssistConfig
import love.miao.yun.sendassist.sendTargetFor
import love.miao.yun.sendassist.SendAssistPrefs
import love.miao.yun.ui.AppIcons
import love.miao.yun.ui.FloatingColorSource
import love.miao.yun.ui.SendAssistPreview
import love.miao.yun.ui.miuix.miaoTextFieldColors
import love.miao.yun.ui.UiEnginePrefs
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun FloatingScreen(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    floatingRunning: Boolean,
    onToggleFloating: () -> Unit,
    onNotify: (String) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density

    var items by remember { mutableStateOf(FloatingWindowPrefs.load(context)) }
    var options by remember { mutableStateOf(FloatingWindowPrefs.loadOptions(context)) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editingTarget by remember { mutableStateOf<String?>(null) }
    var assistConfigs by remember {
        mutableStateOf(
            SEND_TARGETS.associate { it.packageName to SendAssistPrefs.load(context, it.packageName) },
        )
    }

    /** One funnel for assistant edits too: kept in state so the preview redraws as it is changed. */
    fun updateAssist(config: SendAssistConfig) {
        SendAssistPrefs.save(context, config)
        assistConfigs = assistConfigs + (config.packageName to config)
    }

    /** One funnel for edits, so the on-screen buttons and this list never drift apart. */
    fun persist(next: List<FloatingItem>) {
        items = next
        FloatingWindowPrefs.save(context, next)
    }

    fun persistOptions(next: FloatingOptions) {
        options = next
        FloatingWindowPrefs.saveOptions(context, next)
    }

    val floatingColorItems = remember {
        FloatingColorSource.entries.map { DropdownItem(text = it.label) }
    }
    val assistActionItems = remember {
        SendAssistAction.entries.map { DropdownItem(text = it.label) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .overScrollVertical(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "window") {
            Column {
                SmallTitle(text = "悬浮窗")
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = if (floatingRunning) "收起悬浮窗" else "启动悬浮窗",
                        summary = if (floatingRunning) "${items.count { it.enabled }} 个按钮" else "未启动",
                        startAction = {
                            Icon(
                                imageVector = AppIcons.Floating,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        onClick = {
                            onToggleFloating()
                            onNotify(if (floatingRunning) "悬浮窗已收起" else "悬浮窗已启动")
                        },
                    )
                }
            }
        }

        item(key = "drag") {
            Column {
                SmallTitle(text = "拖动")
                Card(modifier = Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = "贴边吸附",
                        summary = "松手后吸到屏幕边缘",
                        checked = options.snapToEdge,
                        onCheckedChange = { persistOptions(options.copy(snapToEdge = it)) },
                    )
                    SwitchPreference(
                        title = "拖动反馈",
                        summary = "开始拖动时轻微震动",
                        checked = options.dragHaptic,
                        onCheckedChange = { persistOptions(options.copy(dragHaptic = it)) },
                    )
                }
            }
        }

        // Every button is its own thing. This list is the only place they are configured.
        item(key = "buttons") {
            Column {
                SmallTitle(text = "悬浮窗按钮")
                Card(modifier = Modifier.fillMaxWidth()) {
                    items.forEach { item ->
                        ArrowPreference(
                            title = item.displayName + "  ·  " + item.actionSummary,
                            summary = item.summary,
                            startAction = {
                                if (item.showsText || !item.showIcon) {
                                    Text(item.label, style = MiuixTheme.textStyles.title4)
                                } else {
                                    Image(
                                        painter = painterResource(item.iconEntry.res),
                                        contentDescription = item.iconEntry.label,
                                        colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.primary),
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            },
                            // A button can be parked without being deleted: the switch takes it off
                            // the screen and leaves every one of its settings alone.
                            endActions = {
                                Switch(
                                    checked = item.enabled,
                                    onCheckedChange = { enabled ->
                                        persist(
                                            items.map {
                                                if (it.id == item.id) {
                                                    it.copy(enabled = enabled)
                                                } else {
                                                    it
                                                }
                                            },
                                        )
                                    },
                                )
                            },
                            onClick = { editingId = item.id },
                        )
                    }
                    ArrowPreference(
                        title = "添加悬浮窗",
                        summary = "再放一个按钮",
                        onClick = {
                            val item = FloatingWindowPrefs.newItem(items, density)
                            persist(items + item)
                            editingId = item.id
                        },
                    )
                }
            }
        }

        // The chat-app assistants live here because they are the same kind of thing: overlays this
        // app draws over other apps, sharing the floating window's colour source. One row per app,
        // each with its own switch, exactly like the floating buttons above.
        item(key = "send-assist") {
            Column {
                SmallTitle(text = "发送按钮助手")
                Card(modifier = Modifier.fillMaxWidth()) {
                    SEND_TARGETS.forEach { target ->
                        val config = assistConfigs[target.packageName]
                            ?: SendAssistConfig(target.packageName)
                        ArrowPreference(
                            title = target.label,
                            summary = if (config.enabled) config.summary else "已关闭",
                            startAction = {
                                Image(
                                    painter = painterResource(R.drawable.ic_ball_auto_awesome),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.primary),
                                    modifier = Modifier.size(22.dp),
                                )
                            },
                            endActions = {
                                Switch(
                                    checked = config.enabled,
                                    onCheckedChange = { enabled ->
                                        updateAssist(config.copy(enabled = enabled))
                                    },
                                )
                            },
                            onClick = { editingTarget = target.packageName },
                        )
                    }
                }
            }
        }

        item(key = "appearance") {
            Column {
                SmallTitle(text = "外观")
                Card(modifier = Modifier.fillMaxWidth()) {
                    WindowSpinnerPreference(
                        title = "取色来源",
                        summary = "悬浮窗的取色来源",
                        items = floatingColorItems,
                        selectedIndex = FloatingColorSource.entries
                            .indexOf(MiaoState.floatingColorSource).coerceAtLeast(0),
                        onSelectedIndexChange = { index ->
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

    val assistTarget = sendTargetFor(editingTarget)
    if (assistTarget != null) {
        val config = assistConfigs[assistTarget.packageName]
            ?: SendAssistConfig(assistTarget.packageName)
        OverlayDialog(
            show = true,
            title = "发送按钮助手 · ${assistTarget.label}",
            onDismissRequest = { editingTarget = null },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SendAssistPreview(
                    config = config,
                    surfaceColor = MiuixTheme.colorScheme.surfaceContainer,
                    fieldColor = MiuixTheme.colorScheme.surfaceContainerHigh,
                    accentColor = MiuixTheme.colorScheme.primary,
                    onAccentColor = MiuixTheme.colorScheme.onPrimary,
                    labelColor = MiuixTheme.colorScheme.onSurface,
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = "启用",
                        summary = "在 ${assistTarget.label} 的发送按钮上方显示",
                        checked = config.enabled,
                        onCheckedChange = { updateAssist(config.copy(enabled = it)) },
                    )
                    WindowSpinnerPreference(
                        title = "点它做什么",
                        summary = "做完后可以选择替你按下发送",
                        items = assistActionItems,
                        selectedIndex = SendAssistAction.entries
                            .indexOf(config.actionEntry)
                            .coerceAtLeast(0),
                        onSelectedIndexChange = { index ->
                            SendAssistAction.entries.getOrNull(index)?.let {
                                updateAssist(config.copy(action = it.id))
                            }
                        },
                    )
                    SwitchPreference(
                        title = "做完自动发送",
                        summary = "改写失败时绝不会发送",
                        checked = config.autoSend,
                        onCheckedChange = { updateAssist(config.copy(autoSend = it)) },
                    )
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    SliderPreference(
                        value = config.sizeDp.toFloat(),
                        onValueChange = { updateAssist(config.copy(sizeDp = it.roundToInt())) },
                        title = "大小",
                        valueText = "${config.sizeDp} dp",
                        valueRange = SendAssistPrefs.MIN_SIZE_DP.toFloat()..SendAssistPrefs.MAX_SIZE_DP.toFloat(),
                        steps = SendAssistPrefs.MAX_SIZE_DP - SendAssistPrefs.MIN_SIZE_DP - 1,
                    )
                    SliderPreference(
                        value = config.effectiveCornerDp.toFloat(),
                        onValueChange = { updateAssist(config.copy(cornerDp = it.roundToInt())) },
                        title = "圆角",
                        valueText = if (config.effectiveCornerDp >= config.sizeDp / 2) {
                            "胶囊"
                        } else {
                            "${config.effectiveCornerDp} dp"
                        },
                        valueRange = 0f..(config.sizeDp / 2).toFloat(),
                        steps = (config.sizeDp / 2 - 1).coerceAtLeast(0),
                    )
                    SliderPreference(
                        value = config.opacity.toFloat(),
                        onValueChange = { updateAssist(config.copy(opacity = it.roundToInt())) },
                        title = "不透明度",
                        valueText = "${config.opacity}%",
                        valueRange = SendAssistPrefs.MIN_OPACITY.toFloat()..SendAssistPrefs.MAX_OPACITY.toFloat(),
                        steps = SendAssistPrefs.MAX_OPACITY - SendAssistPrefs.MIN_OPACITY - 1,
                    )
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    SliderPreference(
                        value = config.offsetXDp.toFloat(),
                        onValueChange = { updateAssist(config.copy(offsetXDp = it.roundToInt())) },
                        title = "水平偏移",
                        valueText = signed(config.offsetXDp),
                        valueRange = -SendAssistPrefs.MAX_OFFSET_DP.toFloat()..SendAssistPrefs.MAX_OFFSET_DP.toFloat(),
                        steps = SendAssistPrefs.MAX_OFFSET_DP * 2 - 1,
                    )
                    SliderPreference(
                        value = config.offsetYDp.toFloat(),
                        onValueChange = { updateAssist(config.copy(offsetYDp = it.roundToInt())) },
                        title = "垂直偏移",
                        valueText = signed(config.offsetYDp),
                        valueRange = -SendAssistPrefs.MAX_OFFSET_DP.toFloat()..SendAssistPrefs.MAX_OFFSET_DP.toFloat(),
                        steps = SendAssistPrefs.MAX_OFFSET_DP * 2 - 1,
                    )
                }

                Button(onClick = { editingTarget = null }, modifier = Modifier.fillMaxWidth()) {
                    Text("完成")
                }
            }
        }
    }

    val editing = items.firstOrNull { it.id == editingId }
    if (editing != null) {
        FloatingItemDialog(
            item = editing,
            resetIndex = items.indexOfFirst { it.id == editing.id },
            canDelete = items.size > 1,
            onChange = { updated -> persist(items.map { if (it.id == updated.id) updated else it }) },
            onDelete = {
                persist(items.filterNot { it.id == editing.id })
                editingId = null
            },
            onDismiss = { editingId = null },
            onNotify = onNotify,
        )
    }
}

/**
 * The editor for one button.
 *
 * Every change is written straight through, so the button on screen updates as the settings are
 * changed — which is the point of being able to configure it at all.
 */
@Composable
private fun FloatingItemDialog(
    item: FloatingItem,
    resetIndex: Int,
    canDelete: Boolean,
    onChange: (FloatingItem) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    onNotify: (String) -> Unit,
) {
    val context = LocalContext.current
    OverlayDialog(
        show = true,
        title = "编辑悬浮窗",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // The editor is taller than a short screen: bound it and let it scroll rather
                // than let the last row fall off the bottom.
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SmallTitle(text = "图标")
            IconGrid(
                selected = item.iconEntry,
                onSelect = { icon -> onChange(item.copy(icon = icon.id)) },
            )

            if (item.showsText) {
                TextField(
                    colors = miaoTextFieldColors(),
                    value = item.text,
                    onValueChange = { onChange(item.copy(text = it)) },
                    label = "按钮文字",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            ActionChain(
                title = "点击",
                actions = item.actionEntries,
                allowNone = false,
                onChange = { chain ->
                    onChange(item.copy(actions = chain.map { it.id }))
                },
            )

            ActionChain(
                title = "长按",
                actions = item.holdActionEntries,
                allowNone = true,
                onChange = { chain ->
                    onChange(item.copy(holdActions = chain.map { it.id }))
                },
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchPreference(
                    title = "在屏幕上显示",
                    summary = "关掉就把它收起来，设置都留着",
                    checked = item.enabled,
                    onCheckedChange = { enabled -> onChange(item.copy(enabled = enabled)) },
                )
                if (!item.showsText) {
                    SwitchPreference(
                        title = "显示图标",
                        summary = if (item.iconCramped && item.showIcon) {
                            "当前尺寸下图标会偏小或被压扁"
                        } else {
                            "关掉就是一条纯色块"
                        },
                        checked = item.showIcon,
                        onCheckedChange = { show -> onChange(item.copy(showIcon = show)) },
                    )
                }
            }

            SmallTitle(text = "形状")
            TextChips(
                labels = FloatingShape.entries.map { it.label },
                // Nothing is highlighted for a hand-made size, which is the honest answer.
                selectedIndex = FloatingShape.of(item)
                    ?.let { FloatingShape.entries.indexOf(it) }
                    ?: -1,
                onSelect = { index ->
                    FloatingShape.entries.getOrNull(index)?.let { shape ->
                        onChange(shape.apply(item))
                    }
                },
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                SliderPreference(
                    value = item.widthDp.toFloat(),
                    onValueChange = { width ->
                        val next = width.roundToInt()
                        onChange(
                            item.copy(
                                widthDp = next,
                                cornerDp = item.cornerDp
                                    .coerceAtMost(minOf(next, item.heightDp) / 2),
                            ),
                        )
                    },
                    title = "宽度",
                    valueText = "${item.widthDp} dp",
                    valueRange = FloatingWindowPrefs.MIN_SIZE_DP.toFloat()..FloatingWindowPrefs.MAX_SIZE_DP.toFloat(),
                    steps = FloatingWindowPrefs.MAX_SIZE_DP - FloatingWindowPrefs.MIN_SIZE_DP - 1,
                )
                SliderPreference(
                    value = item.heightDp.toFloat(),
                    onValueChange = { height ->
                        val next = height.roundToInt()
                        onChange(
                            item.copy(
                                heightDp = next,
                                cornerDp = item.cornerDp
                                    .coerceAtMost(minOf(item.widthDp, next) / 2),
                            ),
                        )
                    },
                    title = "高度",
                    valueText = "${item.heightDp} dp",
                    valueRange = FloatingWindowPrefs.MIN_SIZE_DP.toFloat()..FloatingWindowPrefs.MAX_SIZE_DP.toFloat(),
                    steps = FloatingWindowPrefs.MAX_SIZE_DP - FloatingWindowPrefs.MIN_SIZE_DP - 1,
                )
                SliderPreference(
                    value = item.effectiveCornerDp.toFloat(),
                    onValueChange = { corner -> onChange(item.copy(cornerDp = corner.roundToInt())) },
                    title = "圆角",
                    valueText = if (item.effectiveCornerDp >= item.shortEdgeDp / 2) {
                        "胶囊"
                    } else {
                        "${item.cornerDp} dp"
                    },
                    valueRange = 0f..(item.shortEdgeDp / 2).toFloat(),
                    steps = (item.shortEdgeDp / 2 - 1).coerceAtLeast(0),
                )
                SliderPreference(
                    value = item.opacity.toFloat(),
                    onValueChange = { opacity -> onChange(item.copy(opacity = opacity.roundToInt())) },
                    title = "不透明度",
                    valueText = "${item.opacity}%",
                    valueRange = FloatingWindowPrefs.MIN_OPACITY.toFloat()..
                        FloatingWindowPrefs.MAX_OPACITY.toFloat(),
                    steps = FloatingWindowPrefs.MAX_OPACITY - FloatingWindowPrefs.MIN_OPACITY - 1,
                )
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = "重置位置",
                    summary = "放回屏幕左上角那一列",
                    onClick = {
                        val index = resetIndex.coerceAtLeast(0)
                        val (x, y) = FloatingWindowPrefs.startPosition(context, index)
                        onChange(item.copy(x = x, y = y))
                    },
                )
                Button(
                    onClick = {
                        if (canDelete) onDelete() else onNotify("至少要留一个悬浮窗")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("删除这个悬浮窗")
                }
            }

            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("完成")
            }
        }
    }
}

/** The icon set, wrapped into rows so all of it is visible at once. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconGrid(selected: FloatingIcon, onSelect: (FloatingIcon) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FloatingIcon.entries.forEach { icon ->
            ChoiceCell(selected = icon == selected, onClick = { onSelect(icon) }) {
                if (icon.isText) {
                    Text(text = "文", style = MiuixTheme.textStyles.body2)
                } else {
                    Image(
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallTitle(text = title)

        val labels = FloatingAction.entries.map { it.label }

        if (actions.isEmpty() && allowNone) {
            // A hold gesture that is switched off: the first step still has to be on screen, or
            // there would be no way to switch it back on. The add button below stays on screen
            // too — hiding it here is what made the hold chain look like it could only ever hold
            // the one step.
            Text(
                text = "第 1 步",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            TextChips(
                labels = listOf("无") + labels,
                selectedIndex = 0,
                onSelect = { index ->
                    FloatingAction.entries.getOrNull(index - 1)?.let { onChange(listOf(it)) }
                },
            )
        } else {
            actions.forEachIndexed { index, action ->
                Text(
                    text = "第 ${index + 1} 步",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                TextChips(
                    labels = if (allowNone && index == 0) listOf("无") + labels else labels,
                    selectedIndex = if (allowNone && index == 0) {
                        FloatingAction.entries.indexOf(action) + 1
                    } else {
                        FloatingAction.entries.indexOf(action)
                    },
                    onSelect = { choice ->
                        if (allowNone && index == 0 && choice == 0) {
                            onChange(emptyList())
                        } else {
                            val picked = FloatingAction.entries.getOrNull(
                                if (allowNone && index == 0) choice - 1 else choice,
                            ) ?: return@TextChips
                            onChange(actions.toMutableList().also { it[index] = picked })
                        }
                    },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (actions.size < FloatingWindowPrefs.MAX_CHAIN) {
                ChoiceCell(
                    selected = false,
                    onClick = { onChange(actions + FloatingAction.entries.first()) },
                    wide = true,
                ) {
                    Text("＋ 添加一步", style = MiuixTheme.textStyles.footnote1)
                }
            }
            // The tap chain always keeps one step; a hold chain can be emptied right out.
            if (actions.size > if (allowNone) 0 else 1) {
                ChoiceCell(
                    selected = false,
                    onClick = { onChange(actions.dropLast(1)) },
                    wide = true,
                ) {
                    Text("− 删掉最后一步", style = MiuixTheme.textStyles.footnote1)
                }
            }
        }
    }
}

/** A row of worded choices; "无" is prepended by the caller where that makes sense. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextChips(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { index, label ->
            ChoiceCell(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                wide = true,
            ) {
                Text(text = label, style = MiuixTheme.textStyles.footnote1)
            }
        }
    }
}

/** One selectable cell: a card that reads as pressed when it is the active choice. */
@Composable
private fun ChoiceCell(
    selected: Boolean,
    onClick: () -> Unit,
    wide: Boolean = false,
    content: @Composable () -> Unit,
) {
    val containerColor = if (selected) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (selected) {
        MiuixTheme.colorScheme.onPrimary
    } else {
        MiuixTheme.colorScheme.onSecondaryContainer
    }

    Box(modifier = Modifier.padding(vertical = 1.dp)) {
        Card(
            insideMargin = PaddingValues(0.dp),
            colors = CardDefaults.defaultColors(
                color = containerColor,
                contentColor = contentColor,
            ),
            onClick = onClick,
            showIndication = true,
            pressFeedbackType = PressFeedbackType.Tilt,
        ) {
            Row(
                modifier = Modifier
                    .then(if (wide) Modifier.padding(horizontal = 16.dp, vertical = 9.dp) else Modifier)
                    .then(if (wide) Modifier else Modifier.size(44.dp)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                content()
            }
        }
    }
}

/** Offsets read better with an explicit sign: "+8 dp" rather than "8 dp". */
private fun signed(value: Int): String = if (value > 0) "+$value dp" else "$value dp"

/** One line describing a button's shape, so the list stays readable. */
