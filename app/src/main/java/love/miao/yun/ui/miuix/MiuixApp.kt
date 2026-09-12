/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0
 */

package love.miao.yun.ui.miuix

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import love.miao.yun.MiaoState
import love.miao.yun.service.FloatingWindowService
import love.miao.yun.ui.AppIcons
import love.miao.yun.ui.UiEngine
import love.miao.yun.ui.UiEnginePrefs
import love.miao.yun.ui.aospPredictiveBack
import love.miao.yun.ui.miuix.about.AboutScreen
import love.miao.yun.ui.miuix.floating.FloatingScreen
import love.miao.yun.ui.miuix.home.HomeScreen
import love.miao.yun.ui.miuix.liquid.FloatingBottomBar
import love.miao.yun.ui.miuix.settings.SettingsScreen
import kotlin.coroutines.cancellation.CancellationException

private const val TAB_HOME = 0
private const val TAB_FLOATING = 1
private const val TAB_SETTINGS = 2
private const val TAB_ABOUT = 3

/**
 * The miuix engine: a large-title app bar per page, a pager of four tabs, the liquid-glass
 * floating bottom bar, and AOSP's predictive back.
 */
@Composable
fun MiuixApp() {
    // Monet by default: the app follows the wallpaper unless the user picks otherwise.
    var colorSchemeMode by remember { mutableStateOf(ColorSchemeMode.MonetSystem) }
    var useLiquidGlass by remember { mutableStateOf(true) }
    val context = LocalContext.current

    MiuixAppTheme(colorSchemeMode = colorSchemeMode) {
        MiaoShell(
            colorSchemeMode = colorSchemeMode,
            onColorSchemeModeChange = { colorSchemeMode = it },
            useLiquidGlass = useLiquidGlass,
            onUseLiquidGlassChange = { useLiquidGlass = it },
            engine = MiaoState.engine,
            onEngineChange = {
                MiaoState.engine = it
                UiEnginePrefs.save(context, it)
            },
        )
    }
}

@Composable
fun MiaoShell(
    colorSchemeMode: ColorSchemeMode,
    onColorSchemeModeChange: (ColorSchemeMode) -> Unit,
    useLiquidGlass: Boolean,
    onUseLiquidGlassChange: (Boolean) -> Unit,
    engine: UiEngine,
    onEngineChange: (UiEngine) -> Unit,
) {
    val context = LocalContext.current
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

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

    val navigationItems = remember {
        listOf(
            NavigationItem(label = "首页", icon = AppIcons.Home),
            NavigationItem(label = "悬浮窗", icon = AppIcons.Floating),
            NavigationItem(label = "设置", icon = AppIcons.Settings),
            NavigationItem(label = "关于", icon = AppIcons.About),
        )
    }
    val titles = remember { listOf("喵喵助手", "悬浮窗", "设置", "关于") }

    val pagerState = rememberPagerState(pageCount = { navigationItems.size })
    val currentPage = pagerState.currentPage
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val notify: (String) -> Unit = { message ->
        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
    }

    val scrollBehavior = MiuixScrollBehavior()

    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }

    LaunchedEffect(currentPage) {
        hasOverlayPermission = Settings.canDrawOverlays(context)
    }

    // ---- predictive back, following AOSP's cross-activity transition ----
    val backProgress = remember { Animatable(0f) }
    var backSwipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var backTouchDeltaY by remember { mutableFloatStateOf(0f) }
    var backStartTouchY by remember { mutableFloatStateOf(Float.NaN) }
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
    // AOSP reveals the screen underneath while the top one shrinks away.
    val revealScrim = 0.45f * (1f - backAmount)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background),
    ) {
        if (backAmount > 0f && currentPage != TAB_HOME) {
            Box(modifier = Modifier.fillMaxSize()) {
                MiaoPage(
                    page = TAB_HOME,
                    title = titles[TAB_HOME],
                    navigationItems = navigationItems,
                    pagerState = pagerState,
                    currentPage = TAB_HOME,
                    scrollBehavior = MiuixScrollBehavior(),
                    backdrop = backdrop,
                    useLiquidGlass = useLiquidGlass,
                    engine = engine,
                    onEngineChange = onEngineChange,
                    snackbarHostState = snackbarHostState,
                    colorSchemeMode = colorSchemeMode,
                    onColorSchemeModeChange = onColorSchemeModeChange,
                    useLiquidGlassChange = onUseLiquidGlassChange,
                    hasOverlayPermission = hasOverlayPermission,
                    onToggleFloating = toggleFloating,
                    onRequestOverlay = requestOverlay,
                    onNotify = notify,
                    onSwipe = {},
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
            MiaoPage(
                page = currentPage,
                title = titles[currentPage],
                navigationItems = navigationItems,
                pagerState = pagerState,
                currentPage = currentPage,
                scrollBehavior = scrollBehavior,
                backdrop = backdrop,
                useLiquidGlass = useLiquidGlass,
                engine = engine,
                onEngineChange = onEngineChange,
                snackbarHostState = snackbarHostState,
                colorSchemeMode = colorSchemeMode,
                onColorSchemeModeChange = onColorSchemeModeChange,
                useLiquidGlassChange = onUseLiquidGlassChange,
                hasOverlayPermission = hasOverlayPermission,
                onToggleFloating = toggleFloating,
                onRequestOverlay = requestOverlay,
                onNotify = notify,
                onSwipe = { index -> coroutineScope.launch { pagerState.animateScrollToPage(index) } },
            )
        }
    }
}

/**
 * One screen of the app: a large-title app bar, a pager of tabs, and the liquid-glass bottom bar.
 *
 * The home tab is also rendered behind the current tab during a back gesture, which is why this is
 * a standalone composable rather than being inlined into the shell.
 */
@Composable
fun MiaoPage(
    page: Int,
    title: String,
    navigationItems: List<NavigationItem>,
    pagerState: PagerState,
    currentPage: Int,
    scrollBehavior: ScrollBehavior,
    backdrop: LayerBackdrop,
    useLiquidGlass: Boolean,
    engine: UiEngine,
    onEngineChange: (UiEngine) -> Unit,
    snackbarHostState: SnackbarHostState,
    colorSchemeMode: ColorSchemeMode,
    onColorSchemeModeChange: (ColorSchemeMode) -> Unit,
    useLiquidGlassChange: (Boolean) -> Unit,
    hasOverlayPermission: Boolean,
    onToggleFloating: () -> Unit,
    onRequestOverlay: () -> Unit,
    onNotify: (String) -> Unit,
    onSwipe: (Int) -> Unit,
) {
    Scaffold(
        topBar = {
            // The large title gives the page room to breathe instead of cramming rows against the
            // status bar, and collapses into the small title as the list scrolls.
            TopAppBar(
                title = title,
                largeTitle = title,
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            Box(modifier = Modifier.fillMaxWidth()) {
                FloatingBottomBar(
                    items = navigationItems,
                    selectedIndex = currentPage,
                    onItemClick = onSwipe,
                    backdrop = backdrop,
                    isBlurActive = useLiquidGlass,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            bottom = 12.dp +
                                WindowInsets.navigationBars
                                    .asPaddingValues()
                                    .calculateBottomPadding(),
                        ),
                )
            }
        },
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val pagePadding = PaddingValues(
            start = innerPadding.calculateStartPadding(layoutDirection) + 12.dp,
            top = innerPadding.calculateTopPadding() + 12.dp,
            end = innerPadding.calculateEndPadding(layoutDirection) + 12.dp,
            bottom = innerPadding.calculateBottomPadding() + 12.dp,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .imePadding(),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = true,
            ) { target ->
                when (target) {
                    TAB_HOME -> HomeScreen(
                        contentPadding = pagePadding,
                        scrollBehavior = scrollBehavior,
                        floatingRunning = MiaoState.floatingRunning,
                        hasOverlayPermission = hasOverlayPermission,
                        onToggleFloating = onToggleFloating,
                        onRequestOverlay = onRequestOverlay,
                        onNotify = onNotify,
                    )

                    TAB_FLOATING -> FloatingScreen(
                        contentPadding = pagePadding,
                        scrollBehavior = scrollBehavior,
                        floatingRunning = MiaoState.floatingRunning,
                        onToggleFloating = onToggleFloating,
                        onNotify = onNotify,
                    )

                    TAB_SETTINGS -> SettingsScreen(
                        contentPadding = pagePadding,
                        scrollBehavior = scrollBehavior,
                        colorSchemeMode = colorSchemeMode,
                        onColorSchemeModeChange = onColorSchemeModeChange,
                        useLiquidGlass = useLiquidGlass,
                        onUseLiquidGlassChange = useLiquidGlassChange,
                        onNotify = onNotify,
                    )

                    else -> AboutScreen(
                        contentPadding = pagePadding,
                        scrollBehavior = scrollBehavior,
                        onNotify = onNotify,
                    )
                }
            }
        }
    }
}
