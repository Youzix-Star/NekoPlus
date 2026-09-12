/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0
 *
 * The shell follows InstallerX-Revived's Material 3 layout (GPL-3.0): an outer Scaffold that owns
 * only the bottom navigation, with each page carrying its own large top app bar.
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package love.miao.yun.ui.material3

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import love.miao.yun.MiaoState
import love.miao.yun.service.FloatingWindowService
import love.miao.yun.ui.AppIcons
import love.miao.yun.ui.rememberMainPagerState
import love.miao.yun.ui.aospPredictiveBack
import love.miao.yun.ui.material3.about.MaterialAboutScreen
import love.miao.yun.ui.material3.floating.MaterialFloatingScreen
import love.miao.yun.ui.material3.home.MaterialHomeScreen
import love.miao.yun.ui.material3.licenses.MaterialLicensesScreen
import love.miao.yun.ui.material3.settings.MaterialSettingsScreen
import kotlin.coroutines.cancellation.CancellationException

private const val TAB_HOME = 0
private const val TAB_FLOATING = 1
private const val TAB_SETTINGS = 2
private const val TAB_ABOUT = 3

/**
 * Second-level pages. Only these take part in predictive back: switching between the bottom
 * navigation tabs moves between siblings, so there is no parent screen to preview.
 */
private enum class MaterialSubPage(val title: String) {
    Licenses("开源许可"),
}

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

@Composable
private fun MaterialShell(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var subPage by remember { mutableStateOf<MaterialSubPage?>(null) }

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

    val labels = listOf("首页", "悬浮窗", "设置", "关于")
    val icons: List<ImageVector> = listOf(
        AppIcons.Home,
        AppIcons.Floating,
        AppIcons.Settings,
        AppIcons.About,
    )

    val pagerState = rememberPagerState(pageCount = { labels.size })
    val mainPagerState = rememberMainPagerState(pagerState)
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val notify: (String) -> Unit = { message ->
        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
    }

    // Adopt the pager's position after the user swipes between tabs by hand.
    LaunchedEffect(pagerState.currentPage) {
        mainPagerState.syncPage()
    }

    LaunchedEffect(pagerState.currentPage) {
        hasOverlayPermission = Settings.canDrawOverlays(context)
    }

    // ---- level-1 back: no predictive visual, just the tab-switch animation back to home ----
    // Only armed while a tab is showing and it is not the first one, mirroring the reference
    // project's "we are on the main route and the back stack is empty" condition. When a
    // second-level page is open, its own PredictiveBackHandler owns the gesture instead.
    BackHandler(enabled = subPage == null && mainPagerState.selectedPage != 0) {
        mainPagerState.animateToPage(0)
    }

    // ---- second-level pages: slide in, and follow the back gesture the AOSP way ----
    val subEnter = remember { Animatable(0f) }
    val subBack = remember { Animatable(0f) }
    var backSwipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var backTouchDeltaY by remember { mutableFloatStateOf(0f) }
    var backStartTouchY by remember { mutableFloatStateOf(Float.NaN) }
    val settleScope = rememberCoroutineScope()

    val closeSubPage: () -> Unit = {
        settleScope.launch {
            subBack.snapTo(0f)
            subEnter.animateTo(0f, tween(durationMillis = 200, easing = FastOutSlowInEasing))
            subPage = null
        }
    }

    LaunchedEffect(subPage) {
        if (subPage != null) {
            subBack.snapTo(0f)
            subEnter.snapTo(0f)
            subEnter.animateTo(1f, tween(durationMillis = 280, easing = FastOutSlowInEasing))
        }
    }

    PredictiveBackHandler(enabled = subPage != null) { progress ->
        try {
            progress.collect { event ->
                backSwipeEdge = event.swipeEdge
                if (backStartTouchY.isNaN()) backStartTouchY = event.touchY
                backTouchDeltaY = event.touchY - backStartTouchY
                subBack.snapTo(event.progress)
            }
            settleScope.launch {
                subBack.animateTo(1f, tween(durationMillis = 140))
                subPage = null
                subBack.snapTo(0f)
                subEnter.snapTo(0f)
                backStartTouchY = Float.NaN
                backTouchDeltaY = 0f
            }
        } catch (cancelled: CancellationException) {
            settleScope.launch {
                subBack.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                backStartTouchY = Float.NaN
                backTouchDeltaY = 0f
            }
            throw cancelled
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            bottomBar = {
                NavigationBar {
                    labels.forEachIndexed { index, label ->
                        NavigationBarItem(
                            // Driven by selectedPage, not the pager's currentPage, so the
                            // highlight lands the moment the tab is tapped.
                            selected = mainPagerState.selectedPage == index,
                            onClick = { mainPagerState.animateToPage(index) },
                            icon = { Icon(imageVector = icons[index], contentDescription = label) },
                            label = { Text(label) },
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { outerPadding ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = true,
            ) { page ->
                when (page) {
                    TAB_HOME -> MaterialHomeScreen(
                        outerPadding = outerPadding,
                        floatingRunning = MiaoState.floatingRunning,
                        hasOverlayPermission = hasOverlayPermission,
                        onToggleFloating = toggleFloating,
                        onRequestOverlay = requestOverlay,
                        onNotify = notify,
                    )

                    TAB_FLOATING -> MaterialFloatingScreen(
                        outerPadding = outerPadding,
                        floatingRunning = MiaoState.floatingRunning,
                        onToggleFloating = toggleFloating,
                        onNotify = notify,
                    )

                    TAB_SETTINGS -> MaterialSettingsScreen(
                        outerPadding = outerPadding,
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        dynamicColor = dynamicColor,
                        onDynamicColorChange = onDynamicColorChange,
                        onNotify = notify,
                    )

                    else -> MaterialAboutScreen(
                        outerPadding = outerPadding,
                        onOpenLicenses = { subPage = MaterialSubPage.Licenses },
                        onNotify = notify,
                    )
                }
            }
        }

        val openSubPage = subPage
        if (openSubPage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val dragged = subBack.value
                        if (dragged > 0f) {
                            aospPredictiveBack(
                                progress = dragged,
                                swipeEdge = backSwipeEdge,
                                touchDeltaY = backTouchDeltaY,
                            )
                            alpha = 1f
                        } else {
                            translationX = (1f - subEnter.value) * size.width
                            alpha = 0.5f + 0.5f * subEnter.value
                        }
                    },
            ) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    topBar = {
                        LargeFlexibleTopAppBar(
                            title = { Text(openSubPage.title) },
                            navigationIcon = {
                                IconButton(onClick = closeSubPage) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "返回",
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                titleContentColor = MaterialTheme.colorScheme.onBackground,
                            ),
                        )
                    },
                ) { paddingValues ->
                    when (openSubPage) {
                        MaterialSubPage.Licenses -> MaterialLicensesScreen(outerPadding = paddingValues)
                    }
                }
            }
        }
    }
}
