/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0
 */

package love.miao.yun.ui.material3

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import love.miao.yun.MiaoState
import love.miao.yun.R
import love.miao.yun.service.FloatingWindowService
import love.miao.yun.ui.AppIcons
import love.miao.yun.ui.UiEngine
import love.miao.yun.ui.UiEnginePrefs
import love.miao.yun.ui.aospPredictiveBack
import love.miao.yun.ui.material3.about.MaterialAboutScreen
import love.miao.yun.ui.material3.floating.MaterialFloatingScreen
import love.miao.yun.ui.material3.home.MaterialHomeScreen
import love.miao.yun.ui.material3.settings.MaterialSettingsScreen
import kotlin.coroutines.cancellation.CancellationException

private const val TAB_HOME = 0

/**
 * The Material Design engine: the same four screens as the miuix engine, rebuilt from Material 3
 * components, with the same AOSP predictive back behaviour so the two feel alike during a gesture.
 */
@Composable
fun MaterialApp() {
    var themeMode by remember { mutableStateOf(ThemeMode.System) }
    var dynamicColor by remember { mutableStateOf(true) }
    val darkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    MaterialAppTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
        MaterialShell(
            themeMode = themeMode,
            onThemeModeChange = { themeMode = it },
            dynamicColor = dynamicColor,
            onDynamicColorChange = { dynamicColor = it },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaterialShell(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var backSwipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var backTouchDeltaY by remember { mutableFloatStateOf(0f) }
    var backStartTouchY by remember { mutableFloatStateOf(Float.NaN) }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        hasOverlayPermission = Settings.canDrawOverlays(context)
    }

    val requestOverlay: () -> Unit = {
        overlayPermissionLauncher.launch(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            ),
        )
    }

    val toggleFloating: () -> Unit = {
        when {
            !Settings.canDrawOverlays(context) -> requestOverlay()
            MiaoState.floatingRunning ->
                context.stopService(Intent(context, FloatingWindowService::class.java))
            else ->
                context.startForegroundService(Intent(context, FloatingWindowService::class.java))
        }
    }

    val labels = listOf(
        stringResource(R.string.tab_home),
        stringResource(R.string.tab_floating),
        stringResource(R.string.tab_settings),
        stringResource(R.string.tab_about),
    )
    val icons = listOf(AppIcons.Home, AppIcons.Floating, AppIcons.Settings, AppIcons.About)

    val pagerState = rememberPagerState(pageCount = { labels.size })
    val currentPage = pagerState.currentPage
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val notify: (String) -> Unit = { message ->
        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(currentPage) {
        hasOverlayPermission = Settings.canDrawOverlays(context)
    }

    val backProgress = remember { Animatable(0f) }
    val settleScope = rememberCoroutineScope()

    PredictiveBackHandler(enabled = currentPage != TAB_HOME) { progress ->
        try {
            progress.collect { event ->
                backSwipeEdge = event.swipeEdge
                if (backStartTouchY.isNaN()) backStartTouchY = event.touchY
                backTouchDeltaY = event.touchY - backStartTouchY
                backProgress.snapTo(event.progress)
            }
            settleScope.launch {
                backProgress.animateTo(1f, tween(durationMillis = 140))
                pagerState.scrollToPage(TAB_HOME)
                backStartTouchY = Float.NaN
                backTouchDeltaY = 0f
                backProgress.animateTo(0f, tween(durationMillis = 220))
            }
        } catch (cancelled: CancellationException) {
            settleScope.launch {
                backProgress.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                backStartTouchY = Float.NaN
                backTouchDeltaY = 0f
            }
            throw cancelled
        }
    }

    val backAmount = backProgress.value
    val revealScrim = 0.45f * (1f - backAmount)

    Box(modifier = Modifier.fillMaxSize()) {
        if (backAmount > 0f && currentPage != TAB_HOME) {
            Box(modifier = Modifier.fillMaxSize()) {
                MaterialPage(
                    labels = labels,
                    icons = icons,
                    pagerState = pagerState,
                    currentPage = TAB_HOME,
                    scrollBehavior = scrollBehavior,
                    snackbarHostState = snackbarHostState,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    dynamicColor = dynamicColor,
                    onDynamicColorChange = onDynamicColorChange,
                    hasOverlayPermission = hasOverlayPermission,
                    onToggleFloating = toggleFloating,
                    onRequestOverlay = requestOverlay,
                    onNotify = notify,
                    onTabSelected = {},
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = revealScrim)),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    aospPredictiveBack(
                        progress = backAmount,
                        swipeEdge = backSwipeEdge,
                        touchDeltaY = backTouchDeltaY,
                    )
                },
        ) {
            MaterialPage(
                labels = labels,
                icons = icons,
                pagerState = pagerState,
                currentPage = currentPage,
                scrollBehavior = scrollBehavior,
                snackbarHostState = snackbarHostState,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                dynamicColor = dynamicColor,
                onDynamicColorChange = onDynamicColorChange,
                hasOverlayPermission = hasOverlayPermission,
                onToggleFloating = toggleFloating,
                onRequestOverlay = requestOverlay,
                onNotify = notify,
                onTabSelected = { index ->
                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaterialPage(
    labels: List<String>,
    icons: List<ImageVector>,
    pagerState: PagerState,
    currentPage: Int,
    scrollBehavior: TopAppBarScrollBehavior,
    snackbarHostState: SnackbarHostState,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    hasOverlayPermission: Boolean,
    onToggleFloating: () -> Unit,
    onRequestOverlay: () -> Unit,
    onNotify: (String) -> Unit,
    onTabSelected: (Int) -> Unit,
) {
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(labels[currentPage]) },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            NavigationBar {
                labels.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = currentPage == index,
                        onClick = { onTabSelected(index) },
                        icon = { Icon(imageVector = icons[index], contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val pagePadding = PaddingValues(
            start = innerPadding.calculateStartPadding(layoutDirection) + 12.dp,
            top = innerPadding.calculateTopPadding(),
            end = innerPadding.calculateEndPadding(layoutDirection) + 12.dp,
            bottom = innerPadding.calculateBottomPadding() + 12.dp,
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = true,
        ) { page ->
            when (page) {
                TAB_HOME -> MaterialHomeScreen(
                    contentPadding = pagePadding,
                    scrollBehavior = scrollBehavior,
                    floatingRunning = MiaoState.floatingRunning,
                    hasOverlayPermission = hasOverlayPermission,
                    onToggleFloating = onToggleFloating,
                    onRequestOverlay = onRequestOverlay,
                    onNotify = onNotify,
                )

                1 -> MaterialFloatingScreen(
                    contentPadding = pagePadding,
                    scrollBehavior = scrollBehavior,
                    floatingRunning = MiaoState.floatingRunning,
                    onToggleFloating = onToggleFloating,
                    onNotify = onNotify,
                )

                2 -> MaterialSettingsScreen(
                    contentPadding = pagePadding,
                    scrollBehavior = scrollBehavior,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    dynamicColor = dynamicColor,
                    onDynamicColorChange = onDynamicColorChange,
                    onNotify = onNotify,
                )

                else -> MaterialAboutScreen(
                    contentPadding = pagePadding,
                    scrollBehavior = scrollBehavior,
                    onNotify = onNotify,
                )
            }
        }
    }
}
