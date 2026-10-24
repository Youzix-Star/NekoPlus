/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * The shell's navigation is SukiSU-Ultra's (GPL-3.0): miuix-nav's `NavDisplay` owns the back stack
 * and the platform predictive-back gesture, so there is no hand-written back animation here.
 *
 * Layout note: every tab owns its own top bar (the SukiSU pattern), so the outer Scaffold owns
 * only the bottom bar and the horizontal insets.
 */

package top.youzix.nekoplus.ui.miuix

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.youzix.nekoplus.MiaoState
import top.youzix.nekoplus.crash.CrashHandler
import top.youzix.nekoplus.service.FloatingWindowService
import top.youzix.nekoplus.ui.AppIcons
import top.youzix.nekoplus.ui.UiEngine
import top.youzix.nekoplus.ui.UiEnginePrefs
import top.youzix.nekoplus.ui.miuix.about.AboutScreen
import top.youzix.nekoplus.ui.miuix.ai.AiConfigScreen
import top.youzix.nekoplus.ui.miuix.components.BlurredBar
import top.youzix.nekoplus.ui.miuix.floating.FloatingScreen
import top.youzix.nekoplus.ui.miuix.home.HomeScreen
import top.youzix.nekoplus.ui.miuix.licenses.LicensesScreen
import top.youzix.nekoplus.ui.miuix.liquid.FloatingBottomBar
import top.youzix.nekoplus.ui.miuix.settings.SettingsScreen
import top.youzix.nekoplus.ui.miuix.text.TextRulesScreen
import top.youzix.nekoplus.ui.nav.Route
import top.youzix.nekoplus.ui.provision.ProvisionGuide
import top.youzix.nekoplus.ui.rememberMainPagerState
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
import top.yukonga.miuix.kmp.nav.core.NavController
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.navBackStackOf
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.theme.MiuixTheme

const val TAB_HOME = 0
const val TAB_FLOATING = 1
const val TAB_SETTINGS = 2
const val TAB_ABOUT = 3

/** The miuix engine: a pager of four tabs, each owning its own top bar. */
@Composable
fun MiuixApp() {
    val context = LocalContext.current
    MiuixAppTheme(colorMode = MiaoState.colorMode) {
        MiuixShell(
            engine = MiaoState.engine,
            onEngineChange = {
                MiaoState.engine = it
                UiEnginePrefs.save(context, it)
            },
        )
    }
}

@Composable
fun MiuixShell(
    engine: UiEngine,
    onEngineChange: (UiEngine) -> Unit,
) {
    val context = LocalContext.current
    val useLiquidGlass = MiaoState.useBlur

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
    val titles = remember { listOf("NekoPlus", "悬浮窗", "设置", "关于") }

    val pagerState = rememberPagerState(pageCount = { navigationItems.size })
    val mainPagerState = rememberMainPagerState(pagerState)
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

    val backStack = remember { navBackStackOf(Route.Main) }
    val navigator = remember { NavController(backStack) }

    // Adopt the pager's position after the user swipes between tabs by hand.
    LaunchedEffect(pagerState.currentPage) {
        mainPagerState.syncPage()
        hasOverlayPermission = Settings.canDrawOverlays(context)
        CrashHandler.note("页签 ${titles.getOrNull(pagerState.currentPage) ?: pagerState.currentPage}")
    }

    // ---- level-1 back: no second-level page is open, so back returns to the first tab ----
    BackHandler(enabled = backStack.size == 1 && mainPagerState.selectedPage != 0) {
        mainPagerState.animateToPage(0)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavDisplay(
            navController = navigator,
            effects = NavDisplayEffects(cornerClipRadius = rememberNavSystemCornerRadius()),
            onBack = { navigator.pop() },
        ) {
            entry<Route.Main> {
                MiuixTabs(
                    titles = titles,
                    navigationItems = navigationItems,
                    pagerState = pagerState,
                    selectedPage = mainPagerState.selectedPage,
                    backdrop = backdrop,
                    useLiquidGlass = useLiquidGlass,
                    engine = engine,
                    onEngineChange = onEngineChange,
                    hasOverlayPermission = hasOverlayPermission,
                    onToggleFloating = toggleFloating,
                    onRequestOverlay = requestOverlay,
                    onOpenAiConfig = { navigator.push(Route.AiConfig) },
                    onOpenTextRules = { navigator.push(Route.TextRules) },
                    onOpenLicenses = { navigator.push(Route.Licenses) },
                    onNotify = notify,
                    onTabSelected = { index -> mainPagerState.animateToPage(index) },
                )
            }

            entry<Route.AiConfig> {
                LaunchedEffect(Unit) { CrashHandler.note("二级页面 AI 配置") }
                SubPage(title = "AI 配置", onBack = { navigator.pop() }) { padding, scrollBehavior ->
                    AiConfigScreen(
                        contentPadding = padding,
                        scrollBehavior = scrollBehavior,
                        onNotify = notify,
                    )
                }
            }

            entry<Route.TextRules> {
                LaunchedEffect(Unit) { CrashHandler.note("二级页面 文本替换") }
                SubPage(title = "文本替换", onBack = { navigator.pop() }) { padding, scrollBehavior ->
                    TextRulesScreen(
                        contentPadding = padding,
                        scrollBehavior = scrollBehavior,
                        onNotify = notify,
                    )
                }
            }

            entry<Route.Licenses> {
                LaunchedEffect(Unit) { CrashHandler.note("二级页面 开源许可") }
                LicensesScreen(onBack = { navigator.pop() })
            }
        }

        // The host lives outside the page scaffolds: a message posted from a second-level page
        // must sit above that page, the way a toast would.
        SnackbarHost(
            state = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    bottom = 84.dp +
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
        )
    }

    // About asks for the guide by setting this flag. The guide is HyperCeiler's provisioning flow,
    // ported as its own activities, so the flag hands the launch over to it and is cleared on the
    // way out. The Compose guide in `top.youzix.nekoplus.ui.onboarding` stayed in the tree,
    // untouched, until the ported one has been seen on a device.
    LaunchedEffect(MiaoState.showOnboarding) {
        if (MiaoState.showOnboarding) {
            MiaoState.showOnboarding = false
            ProvisionGuide.launch(context)
        }
    }
}

/**
 * A second-level page: one app bar with a back arrow, then the page's own scrolling content.
 *
 * The business screens were written against the old shell, which handed them a `contentPadding`
 * and a `scrollBehavior`; this keeps that contract instead of rewriting every page. One scroll
 * behaviour is used for the bar *and* the list, otherwise the bar never collapses and the list
 * swallows the scroll.
 */
@Composable
private fun SubPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues, ScrollBehavior) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            BlurredBar(backdrop = null) {
                TopAppBar(
                    title = title,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = AppIcons.Back, contentDescription = "返回")
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val padding = PaddingValues(
            start = innerPadding.calculateStartPadding(layoutDirection) + 12.dp,
            top = innerPadding.calculateTopPadding() + 12.dp,
            end = innerPadding.calculateEndPadding(layoutDirection) + 12.dp,
            bottom = innerPadding.calculateBottomPadding() + 24.dp,
        )
        content(padding, scrollBehavior)
    }
}

/** Level one: the four tabs. Switching between them is a pager animation, nothing more. */
@Composable
private fun MiuixTabs(
    titles: List<String>,
    navigationItems: List<NavigationItem>,
    pagerState: PagerState,
    selectedPage: Int,
    backdrop: LayerBackdrop,
    useLiquidGlass: Boolean,
    engine: UiEngine,
    onEngineChange: (UiEngine) -> Unit,
    hasOverlayPermission: Boolean,
    onToggleFloating: () -> Unit,
    onRequestOverlay: () -> Unit,
    onOpenAiConfig: () -> Unit,
    onOpenTextRules: () -> Unit,
    onOpenLicenses: () -> Unit,
    onNotify: (String) -> Unit,
    onTabSelected: (Int) -> Unit,
) {
    // Each page owns its own top bar, so this Scaffold only owns the bottom bar and the
    // horizontal insets.
    Scaffold(
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
        bottomBar = {
            // The bar belongs to the bottom edge of the screen, keyboard or no keyboard: it
            // consumes the IME inset here so that nothing above it can lift it out of place.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .consumeWindowInsets(WindowInsets.ime),
            ) {
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
    ) { innerPadding ->
        val bottomPadding = innerPadding.calculateBottomPadding() + 12.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                // The glass bottom bar samples this layer.
                .layerBackdrop(backdrop),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = true,
            ) { page ->
                when (page) {
                    TAB_HOME -> HomeScreen(
                        bottomPadding = bottomPadding,
                        floatingRunning = MiaoState.floatingRunning,
                        hasOverlayPermission = hasOverlayPermission,
                        onToggleFloating = onToggleFloating,
                        onRequestOverlay = onRequestOverlay,
                        onNotify = onNotify,
                    )

                    TAB_FLOATING -> FloatingTab(
                        title = titles[TAB_FLOATING],
                        bottomPadding = bottomPadding,
                        floatingRunning = MiaoState.floatingRunning,
                        onToggleFloating = onToggleFloating,
                        onNotify = onNotify,
                    )

                    TAB_SETTINGS -> SettingsScreen(
                        bottomPadding = bottomPadding,
                        engine = engine,
                        onEngineChange = onEngineChange,
                        onOpenAiConfig = onOpenAiConfig,
                        onOpenTextRules = onOpenTextRules,
                        onNotify = onNotify,
                    )

                    else -> AboutScreen(
                        bottomPadding = bottomPadding,
                        onOpenLicenses = onOpenLicenses,
                        onNotify = onNotify,
                    )
                }
            }
        }
    }
}

/** The floating-window tab: the business screen, wrapped in the shell's own app bar. */
@Composable
private fun FloatingTab(
    title: String,
    bottomPadding: Dp,
    floatingRunning: Boolean,
    onToggleFloating: () -> Unit,
    onNotify: (String) -> Unit,
) {
    val pageScrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            BlurredBar(backdrop = null) {
                TopAppBar(
                    title = title,
                    largeTitle = title,
                    scrollBehavior = pageScrollBehavior,
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val padding = PaddingValues(
            start = innerPadding.calculateStartPadding(layoutDirection) + 12.dp,
            top = innerPadding.calculateTopPadding() + 12.dp,
            end = innerPadding.calculateEndPadding(layoutDirection) + 12.dp,
            bottom = innerPadding.calculateBottomPadding() + bottomPadding,
        )
        FloatingScreen(
            contentPadding = padding,
            scrollBehavior = pageScrollBehavior,
            floatingRunning = floatingRunning,
            onToggleFloating = onToggleFloating,
            onNotify = onNotify,
        )
    }
}
