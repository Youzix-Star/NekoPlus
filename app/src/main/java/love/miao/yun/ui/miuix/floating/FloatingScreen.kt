/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0
 */

package love.miao.yun.ui.miuix.floating

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import love.miao.yun.MiaoState
import love.miao.yun.floating.FloatingAction
import love.miao.yun.floating.FloatingIcon
import love.miao.yun.floating.FloatingItem
import love.miao.yun.floating.FloatingWindowPrefs
import love.miao.yun.ui.AppIcons
import love.miao.yun.ui.FloatingColorSource
import love.miao.yun.ui.UiEnginePrefs
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
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
    var editingId by remember { mutableStateOf<String?>(null) }

    /** One funnel for edits, so the on-screen buttons and this list never drift apart. */
    fun persist(next: List<FloatingItem>) {
        items = next
        FloatingWindowPrefs.save(context, next)
    }

    val floatingColorItems = remember {
        FloatingColorSource.entries.map { DropdownItem(text = it.label) }
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
                        summary = if (floatingRunning) {
                            "当前屏幕上有 ${items.size} 个按钮"
                        } else {
                            "还没有启动"
                        },
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

        // Every button is its own thing: its own icon or label, its own action, its own size and
        // its own place on screen. This list is the only place they are configured.
        item(key = "buttons") {
            Column {
                SmallTitle(text = "悬浮窗按钮")
                Card(modifier = Modifier.fillMaxWidth()) {
                    items.forEach { item ->
                        ArrowPreference(
                            title = item.label + "  ·  " + item.actionEntry.label,
                            summary = "${item.sizeDp} dp" +
                                (if (item.round) " · 圆形" else " · 方形") +
                                " · 拖动可移动",
                            startAction = {
                                Text(
                                    text = item.label,
                                    style = MiuixTheme.textStyles.title4,
                                )
                            },
                            onClick = { editingId = item.id },
                        )
                    }
                    ArrowPreference(
                        title = "添加悬浮窗",
                        summary = "再放一个按钮到屏幕上，图标、动作、大小都各自独立",
                        onClick = {
                            val item = FloatingWindowPrefs.newItem(
                                items,
                                x = (24 * density).roundToInt(),
                                y = (240 * density).roundToInt(),
                            )
                            persist(items + item)
                            editingId = item.id
                        },
                    )
                }
            }
        }

        item(key = "appearance") {
            Column {
                SmallTitle(text = "外观")
                Card(modifier = Modifier.fillMaxWidth()) {
                    WindowSpinnerPreference(
                        title = "取色来源",
                        summary = "悬浮窗是独立于界面的悬浮层，取色可以单独选择",
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

    val editing = items.firstOrNull { it.id == editingId }
    if (editing != null) {
        FloatingItemDialog(
            item = editing,
            canDelete = items.size > 1,
            onChange = { updated ->
                persist(items.map { if (it.id == updated.id) updated else it })
            },
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
 * changed — which is the whole point of being able to configure it in the first place.
 */
@Composable
private fun FloatingItemDialog(
    item: FloatingItem,
    canDelete: Boolean,
    onChange: (FloatingItem) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    onNotify: (String) -> Unit,
) {
    OverlayDialog(
        show = true,
        title = "编辑悬浮窗",
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallTitle(text = "图标")
            ChipRow(
                labels = FloatingIcon.entries.map { icon ->
                    if (icon == FloatingIcon.Text) "文" else icon.glyph
                },
                selectedIndex = FloatingIcon.entries.indexOf(item.iconEntry).coerceAtLeast(0),
                onSelect = { index ->
                    FloatingIcon.entries.getOrNull(index)?.let { icon ->
                        onChange(item.copy(icon = icon.id))
                    }
                },
            )

            if (item.iconEntry == FloatingIcon.Text) {
                TextField(
                    value = item.text,
                    onValueChange = { onChange(item.copy(text = it)) },
                    label = "按钮文字",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SmallTitle(text = "点击动作")
            ChipRow(
                labels = FloatingAction.entries.map { it.label },
                selectedIndex = FloatingAction.entries.indexOf(item.actionEntry).coerceAtLeast(0),
                onSelect = { index ->
                    FloatingAction.entries.getOrNull(index)?.let { action ->
                        onChange(item.copy(action = action.id))
                    }
                },
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                SliderPreference(
                    value = item.sizeDp.toFloat(),
                    onValueChange = { size -> onChange(item.copy(sizeDp = size.roundToInt())) },
                    title = "按钮大小",
                    valueText = "${item.sizeDp} dp",
                    valueRange = FloatingWindowPrefs.MIN_SIZE_DP.toFloat()..FloatingWindowPrefs.MAX_SIZE_DP.toFloat(),
                    steps = FloatingWindowPrefs.MAX_SIZE_DP - FloatingWindowPrefs.MIN_SIZE_DP - 1,
                )
                SwitchPreference(
                    title = "圆形按钮",
                    summary = "关掉就是一个圆角方形",
                    checked = item.round,
                    onCheckedChange = { round -> onChange(item.copy(round = round)) },
                )
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        if (canDelete) {
                            onDelete()
                        } else {
                            onNotify("至少要留一个悬浮窗")
                        }
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

/**
 * A row of choices.
 *
 * Deliberately not a spinner: the values are short, few, and best compared side by side, and a row
 * of chips shows the whole set — including which one is active — without a second tap.
 */
@Composable
private fun ChipRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Card(
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
                colors = CardDefaults.defaultColors(
                    color = if (selected) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.secondaryContainer
                    },
                    contentColor = if (selected) {
                        MiuixTheme.colorScheme.onPrimary
                    } else {
                        MiuixTheme.colorScheme.onSecondaryContainer
                    },
                ),
                onClick = { onSelect(index) },
                showIndication = true,
                pressFeedbackType = PressFeedbackType.Tilt,
            ) {
                Text(text = label, style = MiuixTheme.textStyles.footnote1)
            }
        }
    }
}
