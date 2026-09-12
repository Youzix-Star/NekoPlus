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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import love.miao.yun.MiaoState
import love.miao.yun.service.FloatingWindowService
import love.miao.yun.ui.AppIcons
import love.miao.yun.ui.material3.about.MaterialAboutScreen
import love.miao.yun.ui.material3.ai.MaterialAiConfigScreen
import love.miao.yun.ui.material3.floating.MaterialFloatingScreen
import love.miao.yun.ui.material3.home.MaterialHomeScreen
import love.miao.yun.ui.material3.licenses.MaterialLicensesScreen
import love.miao.yun.ui.material3.settings.MaterialSettingsScreen
import love.miao.yun.ui.material3.widgets.SwipeableSnackbarHost
import love.miao.yun.ui.predictiveback.cancelSpec
import love.miao.yun.ui.predictiveback.commitSpec
import love.miao.yun.ui.predictiveback.navTransition
import love.miao.yun.ui.predictiveback.rememberPredictiveBackScope
import love.miao.yun.ui.predictiveback.rememberPredictiveBackState
import love.miao.yun.ui.rememberMainPagerState
import top.yukonga.miuix.kmp.nav.transition.NavSwipeEdge

private const val TAB_HOME = 0
private const val TAB_FLOATING = 1
private const val TAB_SETTINGS = 2
private const val TAB_ABOUT = 3

/**
 * Second-level pages. Only these take part in predictive back: switching between the bottom
 * navigation tabs moves between siblings, so there is no parent screen to preview.
 */
private enum class MaterialSubPage(val title: String) {
    AiConfig("AI 配置"),
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

    // ---- second-level pages ----
    // Entering and leaving play the same slide as before; the back gesture and its release are
    // handed to whichever predictive-back style the user picked, so both layers are animated by
    // the reference project's transition code.
    val subEnter = remember { Animatable(0f) }
    val back = rememberPredictiveBackState()
    val settleScope = rememberCoroutineScope()
    val backTransition = MiaoState.predictiveBackStyle.transition

    val closeSubPage: () -> Unit = {
        settleScope.launch {
            back.forceReset()
            subEnter.animateTo(0f, tween(durationMillis = 200, easing = FastOutSlowInEasing))
            subPage = null
        }
    }

    LaunchedEffect(subPage) {
        if (subPage != null) {
            back.forceReset()
            subEnter.snapTo(0f)
            subEnter.animateTo(1f, tween(durationMillis = 280, easing = FastOutSlowInEasing))
        }
    }

    PredictiveBackHandler(enabled = subPage != null) { progress ->
        back.onGestureStart()
        back.onDismissed = {
            subPage = null
            subEnter.snapTo(0f)
            back.forceReset()
        }
        try {
            progress.collect { event ->
                back.onGestureProgress(
                    edge = if (event.swipeEdge == BackEventCompat.EDGE_RIGHT) {
                        NavSwipeEdge.Right
                    } else {
                        NavSwipeEdge.Left
                    },
                    touchY = event.touchY,
                    fraction = event.progress,
                )
            }
            back.settleTo(commit = true, spec = backTransition.commitSpec())
        } catch (cancelled: CancellationException) {
            back.settleTo(commit = false, spec = backTransition.cancelSpec())
            throw cancelled
        }
    }

    // Both layers feed the same transition: the level-one content plays the "covered" role while
    // the gesture is live, exactly as the reference project's stack does.
    val gestureActive = back.value > 0f || back.settle != null
    val coveredScope = rememberPredictiveBackScope(
        dismissProgress = { back.value },
        covered = true,
        layerSize = { back.layerSize },
        gesture = { back.gesture },
        settle = { back.settle },
    )
    val outgoingScope = rememberPredictiveBackScope(
        dismissProgress = { back.value },
        covered = false,
        layerSize = { back.layerSize },
        gesture = { back.gesture },
        settle = { back.settle },
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { back.layerSize = it },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (gestureActive) {
                        Modifier.navTransition(backTransition, coveredScope)
                    } else {
                        Modifier
                    },
                ),
        ) {
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
            snackbarHost = { SwipeableSnackbarHost(hostState = snackbarHostState) },
        ) { outerPadding ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = true,
            ) { page ->
                when (page) {
                    TAB_HOME -> MaterialHomeScreen(
                        outerPadding = outerPadding,
                        useBlur = MiaoState.useBlur,
                        floatingRunning = MiaoState.floatingRunning,
                        hasOverlayPermission = hasOverlayPermission,
                        onToggleFloating = toggleFloating,
                        onRequestOverlay = requestOverlay,
                        onNotify = notify,
                    )

                    TAB_FLOATING -> MaterialFloatingScreen(
                        outerPadding = outerPadding,
                        useBlur = MiaoState.useBlur,
                        floatingRunning = MiaoState.floatingRunning,
                        onToggleFloating = toggleFloating,
                        onNotify = notify,
                    )

                    TAB_SETTINGS -> MaterialSettingsScreen(
                        outerPadding = outerPadding,
                        useBlur = MiaoState.useBlur,
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        dynamicColor = dynamicColor,
                        onDynamicColorChange = onDynamicColorChange,
                        onOpenAiConfig = { subPage = MaterialSubPage.AiConfig },
                        onNotify = notify,
                    )

                    else -> MaterialAboutScreen(
                        outerPadding = outerPadding,
                        useBlur = MiaoState.useBlur,
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
                    .then(
                        if (gestureActive) {
                            Modifier.navTransition(backTransition, outgoingScope)
                        } else {
                            Modifier.graphicsLayer {
                                translationX = (1f - subEnter.value) * size.width
                                alpha = 0.5f + 0.5f * subEnter.value
                            }
                        },
                    ),
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
                        MaterialSubPage.AiConfig -> MaterialAiConfigScreen(
                            outerPadding = paddingValues,
                            useBlur = MiaoState.useBlur,
                            onNotify = notify,
                        )

                        MaterialSubPage.Licenses -> MaterialLicensesScreen(outerPadding = paddingValues)
                    }
                }
            }
        }
        }
    }
}
