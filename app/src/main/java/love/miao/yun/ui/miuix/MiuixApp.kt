/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0
 */

package love.miao.yun.ui.miuix

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import love.miao.yun.MiaoState
import love.miao.yun.service.FloatingWindowService
import love.miao.yun.ui.AppIcons
import love.miao.yun.ui.UiEngine
import love.miao.yun.ui.UiEnginePrefs
import love.miao.yun.ui.miuix.about.AboutScreen
import love.miao.yun.ui.miuix.ai.AiConfigScreen
import love.miao.yun.ui.miuix.floating.FloatingScreen
import love.miao.yun.ui.miuix.home.HomeScreen
import love.miao.yun.ui.miuix.licenses.LicensesScreen
import love.miao.yun.ui.miuix.liquid.FloatingBottomBar
import love.miao.yun.ui.miuix.settings.SettingsScreen
import love.miao.yun.ui.predictiveback.cancelSpec
import love.miao.yun.ui.predictiveback.commitSpec
import love.miao.yun.ui.predictiveback.navTransition
import love.miao.yun.ui.predictiveback.rememberPredictiveBackScope
import love.miao.yun.ui.predictiveback.rememberPredictiveBackState
import love.miao.yun.ui.rememberMainPagerState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
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
import top.yukonga.miuix.kmp.nav.transition.NavSwipeEdge
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme

const val TAB_HOME = 0
const val TAB_FLOATING = 1
const val TAB_SETTINGS = 2
const val TAB_ABOUT = 3

/**
 * Second-level pages.
 *
 * Only these respond to a back gesture: a tab switch moves between siblings, so the platform
 * transition (which previews the *parent* you are returning to) has no meaning there.
 */
enum class MiuixSubPage(val title: String) {
    AiConfig("AI 配置"),
    Licenses("开源许可"),
}

/** The miuix engine: large-title app bar, a pager of tabs, and the liquid-glass bottom bar. */
@Composable
fun MiuixApp() {
    var colorSchemeMode by remember { mutableStateOf(ColorSchemeMode.MonetSystem) }
    val context = LocalContext.current

    // One persisted switch drives translucency in both engines: this engine's liquid-glass
    // bottom bar, and the Material 3 engine's blurred top bar.
    val useLiquidGlass = MiaoState.useBlur

    MiuixAppTheme(colorSchemeMode = colorSchemeMode) {
        MiaoShell(
            colorSchemeMode = colorSchemeMode,
            onColorSchemeModeChange = { colorSchemeMode = it },
            useLiquidGlass = useLiquidGlass,
            onUseLiquidGlassChange = {
                MiaoState.useBlur = it
                UiEnginePrefs.saveUseBlur(context, it)
            },
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
    var subPage by remember { mutableStateOf<MiuixSubPage?>(null) }

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
    val mainPagerState = rememberMainPagerState(pagerState)
    val mainScrollBehavior = MiuixScrollBehavior()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val notify: (String) -> Unit = { message ->
        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
    }

    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }

    // Adopt the pager's position after the user swipes between tabs by hand.
    LaunchedEffect(pagerState.currentPage) {
        mainPagerState.syncPage()
    }

    LaunchedEffect(pagerState.currentPage) {
        hasOverlayPermission = Settings.canDrawOverlays(context)
    }

    // ---- level-1 back: no predictive visual, just the tab-switch animation back to home ----
    // Armed only while a tab is showing and it is not the first one, mirroring the reference
    // project's "we are on the main route and the back stack is empty" condition. While a
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
    val backStyle = MiaoState.predictiveBackStyle
    val backTransition = backStyle.transition

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

    // Enabled only while a second-level page is open.
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
        MiaoTabs(
            titles = titles,
            navigationItems = navigationItems,
            pagerState = pagerState,
            selectedPage = mainPagerState.selectedPage,
            scrollBehavior = mainScrollBehavior,
            backdrop = backdrop,
            useLiquidGlass = useLiquidGlass,
            snackbarHostState = snackbarHostState,
            colorSchemeMode = colorSchemeMode,
            onColorSchemeModeChange = onColorSchemeModeChange,
            useLiquidGlassChange = onUseLiquidGlassChange,
            engine = engine,
            onEngineChange = onEngineChange,
            hasOverlayPermission = hasOverlayPermission,
            onToggleFloating = toggleFloating,
            onRequestOverlay = requestOverlay,
            onNotify = notify,
            onOpenAiConfig = { subPage = MiuixSubPage.AiConfig },
            onOpenLicenses = { subPage = MiuixSubPage.Licenses },
            onTabSelected = { index -> mainPagerState.animateToPage(index) },
        )

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
                    topBar = {
                        TopAppBar(
                            title = openSubPage.title,
                            navigationIcon = {
                                IconButton(onClick = closeSubPage) {
                                    Icon(
                                        imageVector = AppIcons.Back,
                                        contentDescription = "返回",
                                    )
                                }
                            },
                        )
                    },
                ) { innerPadding ->
                    val layoutDirection = LocalLayoutDirection.current
                    val subPadding = PaddingValues(
                        start = innerPadding.calculateStartPadding(layoutDirection) + 12.dp,
                        top = innerPadding.calculateTopPadding() + 12.dp,
                        end = innerPadding.calculateEndPadding(layoutDirection) + 12.dp,
                        bottom = innerPadding.calculateBottomPadding() + 24.dp,
                    )
                    when (openSubPage) {
                        MiuixSubPage.AiConfig -> AiConfigScreen(
                            contentPadding = subPadding,
                            scrollBehavior = MiuixScrollBehavior(),
                            onNotify = notify,
                        )

                        MiuixSubPage.Licenses -> LicensesScreen(contentPadding = subPadding)
                    }
                }
            }
        }
        }
    }
}

/** Level one: the four tabs. Switching between them is a pager animation, nothing more. */
@Composable
private fun MiaoTabs(
    titles: List<String>,
    navigationItems: List<NavigationItem>,
    pagerState: PagerState,
    selectedPage: Int,
    scrollBehavior: ScrollBehavior,
    backdrop: LayerBackdrop,
    useLiquidGlass: Boolean,
    snackbarHostState: SnackbarHostState,
    colorSchemeMode: ColorSchemeMode,
    onColorSchemeModeChange: (ColorSchemeMode) -> Unit,
    useLiquidGlassChange: (Boolean) -> Unit,
    engine: UiEngine,
    onEngineChange: (UiEngine) -> Unit,
    hasOverlayPermission: Boolean,
    onToggleFloating: () -> Unit,
    onRequestOverlay: () -> Unit,
    onNotify: (String) -> Unit,
    onOpenAiConfig: () -> Unit,
    onOpenLicenses: () -> Unit,
    onTabSelected: (Int) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                // Follows the requested tab, not the pager, so the title flips together
                // with the bottom-bar highlight the instant a tab is tapped.
                title = titles[selectedPage],
                largeTitle = titles[selectedPage],
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            Box(modifier = Modifier.fillMaxWidth()) {
                FloatingBottomBar(
                    items = navigationItems,
                    selectedIndex = selectedPage,
                    onItemClick = onTabSelected,
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
            ) { page ->
                when (page) {
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
                        engine = engine,
                        onEngineChange = onEngineChange,
                        onOpenAiConfig = onOpenAiConfig,
                        onNotify = onNotify,
                    )

                    else -> AboutScreen(
                        contentPadding = pagePadding,
                        scrollBehavior = scrollBehavior,
                        onOpenLicenses = onOpenLicenses,
                        onNotify = onNotify,
                    )
                }
            }
        }
    }
}
