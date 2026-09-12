/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0
 *
 * Rows are built from the segmented-column widgets ported from InstallerX-Revived (GPL-3.0).
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package love.miao.yun.ui.material3.floating

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import love.miao.yun.ui.material3.material3AppBarColor
import love.miao.yun.ui.material3.material3BlurEffect
import love.miao.yun.ui.material3.rememberMaterial3BlurBackdrop
import love.miao.yun.ui.material3.widgets.BaseItemContainer
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

    /** Which button's editor is open; editing happens in place, under the row itself. */
    var expandedId by remember { mutableStateOf<String?>(null) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val backdrop = rememberMaterial3BlurBackdrop(useBlur)

    /** One funnel for edits, so the on-screen buttons and this list never drift apart. */
    fun persist(next: List<FloatingItem>) {
        items = next
        FloatingWindowPrefs.save(context, next)
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
                            description = if (floatingRunning) {
                                "当前屏幕上有 ${items.size} 个按钮"
                            } else {
                                "还没有启动"
                            },
                            onClick = {
                                onToggleFloating()
                                onNotify(if (floatingRunning) "悬浮窗已收起" else "悬浮窗已启动")
                            },
                        )
                    }
                }
            }

            // Each button is its own thing: its own icon or label, its own action, its own size,
            // its own place. Editing happens in place so the change can be seen on screen as it is
            // made, rather than being buried in a dialog.
            item {
                SegmentedColumn(title = "悬浮窗按钮") {
                    items.forEach { entry ->
                        item(key = entry.id) {
                            NavigationItemWidget(
                                icon = AppIcons.Floating,
                                title = entry.label + "  ·  " + entry.actionEntry.label,
                                description = "${entry.sizeDp} dp" +
                                    (if (entry.round) " · 圆形" else " · 方形") +
                                    " · 点按编辑",
                                onClick = {
                                    expandedId = if (expandedId == entry.id) null else entry.id
                                },
                            )
                        }

                        if (expandedId == entry.id) {
                            item(key = entry.id + "-icon") {
                                BaseItemContainer {
                                    ChoiceRow(
                                        labels = FloatingIcon.entries.map { icon ->
                                            if (icon == FloatingIcon.Text) "文" else icon.glyph
                                        },
                                        selectedIndex = FloatingIcon.entries
                                            .indexOf(entry.iconEntry)
                                            .coerceAtLeast(0),
                                        onSelect = { index ->
                                            FloatingIcon.entries.getOrNull(index)?.let { icon ->
                                                persist(
                                                    items.map {
                                                        if (it.id == entry.id) {
                                                            it.copy(icon = icon.id)
                                                        } else {
                                                            it
                                                        }
                                                    },
                                                )
                                            }
                                        },
                                    )
                                }
                            }

                            if (entry.iconEntry == FloatingIcon.Text) {
                                item(key = entry.id + "-text") {
                                    BaseItemContainer {
                                        OutlinedTextField(
                                            value = entry.text,
                                            onValueChange = { text ->
                                                persist(
                                                    items.map {
                                                        if (it.id == entry.id) {
                                                            it.copy(text = text)
                                                        } else {
                                                            it
                                                        }
                                                    },
                                                )
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
                                    ChoiceRow(
                                        labels = FloatingAction.entries.map { it.label },
                                        selectedIndex = FloatingAction.entries
                                            .indexOf(entry.actionEntry)
                                            .coerceAtLeast(0),
                                        onSelect = { index ->
                                            FloatingAction.entries.getOrNull(index)?.let { action ->
                                                persist(
                                                    items.map {
                                                        if (it.id == entry.id) {
                                                            it.copy(action = action.id)
                                                        } else {
                                                            it
                                                        }
                                                    },
                                                )
                                            }
                                        },
                                    )
                                }
                            }

                            item(key = entry.id + "-size") {
                                BaseItemContainer {
                                    IntNumberPickerWidget(
                                        title = "按钮大小",
                                        value = entry.sizeDp,
                                        startInt = FloatingWindowPrefs.MIN_SIZE_DP,
                                        endInt = FloatingWindowPrefs.MAX_SIZE_DP,
                                        valueSuffix = " dp",
                                        onValueChange = { size ->
                                            persist(
                                                items.map {
                                                    if (it.id == entry.id) {
                                                        it.copy(sizeDp = size)
                                                    } else {
                                                        it
                                                    }
                                                },
                                            )
                                        },
                                    )
                                }
                            }

                            item(key = entry.id + "-shape") {
                                SwitchWidget(
                                    icon = AppIcons.Tune,
                                    title = "圆形按钮",
                                    description = "关掉就是一个圆角方形",
                                    checked = entry.round,
                                    onCheckedChange = { round ->
                                        persist(
                                            items.map {
                                                if (it.id == entry.id) {
                                                    it.copy(round = round)
                                                } else {
                                                    it
                                                }
                                            },
                                        )
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
                            description = "再放一个按钮到屏幕上，图标、动作、大小都各自独立",
                            onClick = {
                                val item = FloatingWindowPrefs.newItem(
                                    items,
                                    x = (24 * density).roundToInt(),
                                    y = (240 * density).roundToInt(),
                                )
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
                            description = "悬浮窗是独立于界面的悬浮层，取色可以单独选择",
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

/**
 * A row of choices, showing all of them at once.
 *
 * A dropdown would hide the alternatives behind a tap, which is wrong for a set this short: the
 * icons only mean anything next to each other, and the active one has to be visible.
 */
@Composable
private fun ChoiceRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Surface(
                onClick = { onSelect(index) },
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
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}
