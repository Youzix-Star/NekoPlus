/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * The shell's navigation is SukiSU-Ultra's (GPL-3.0): miuix-nav's `NavDisplay` owns the back stack
 * and the platform predictive-back gesture. Each page carries its own app bar, so this scaffold
 * owns only the bottom bar and no window insets.
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package top.youzix.nekoplus.ui.material3

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PictureInPicture
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.youzix.nekoplus.MiaoState
import top.youzix.nekoplus.crash.CrashHandler
import top.youzix.nekoplus.service.FloatingWindowService
import top.youzix.nekoplus.ui.UiEngine
import top.youzix.nekoplus.ui.UiEnginePrefs
import top.youzix.nekoplus.ui.material3.about.MaterialAboutScreen
import top.youzix.nekoplus.ui.material3.ai.MaterialAiConfigScreen
import top.youzix.nekoplus.ui.material3.floating.MaterialFloatingScreen
import top.youzix.nekoplus.ui.material3.home.MaterialHomeScreen
import top.youzix.nekoplus.ui.material3.licenses.MaterialLicensesScreen
import top.youzix.nekoplus.ui.material3.settings.MaterialSettingsScreen
import top.youzix.nekoplus.ui.material3.text.MaterialTextRulesScreen
import top.youzix.nekoplus.ui.material3.widgets.SwipeableSnackbarHost
import top.youzix.nekoplus.ui.nav.Route
import top.youzix.nekoplus.ui.provision.ProvisionGuide
import top.youzix.nekoplus.ui.rememberMainPagerState
import top.yukonga.miuix.kmp.nav.core.NavController
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.navBackStackOf
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius

private const val TAB_HOME = 0
private const val TAB_FLOATING = 1
private const val TAB_SETTINGS = 2
private const val TAB_ABOUT = 3

@Composable
fun MaterialApp() {
    val context = LocalContext.current
    MaterialAppTheme(colorMode = MiaoState.colorMode) {
        MaterialShell(
            engine = MiaoState.engine,
            onEngineChange = {
                MiaoState.engine = it
                UiEnginePrefs.save(context, it)
            },
        )
    }
}

@Composable
private fun MaterialShell(
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

    val labels = listOf("首页", "悬浮窗", "设置", "关于")
    // Filled icon while selected, outlined while not: SukiSU's bar switches between the two.
    val navItems = listOf(
        MaterialNavItem("首页", Icons.Filled.Home, Icons.Outlined.Home),
        MaterialNavItem("悬浮窗", Icons.Filled.PictureInPicture, Icons.Outlined.PictureInPicture),
        MaterialNavItem("设置", Icons.Filled.Settings, Icons.Outlined.Settings),
        MaterialNavItem("关于", Icons.Filled.Info, Icons.Outlined.Info),
    )

    val pagerState = rememberPagerState(pageCount = { labels.size })
    val mainPagerState = rememberMainPagerState(pagerState)
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val notify: (String) -> Unit = { message ->
        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
    }

    val toggleFloating: () -> Unit = {
        when {
            !Settings.canDrawOverlays(context) -> requestOverlay()
            MiaoState.floatingRunning ->
                context.stopService(Intent(context, FloatingWindowService::class.java))
            else -> {
                // Plain startService on purpose: the service promotes itself to a foreground
                // service when the system allows it. A refused promotion then leaves it running as
                // a background service, instead of tripping the startForegroundService contract
                // (which kills the process — that is the crash HyperOS caused on 2026-10-25).
                runCatching {
                    context.startService(Intent(context, FloatingWindowService::class.java))
                }.onFailure { onNotify("无法启动悬浮窗：" + it.message) }
            }
        }
    }

    val backStack = remember { navBackStackOf(Route.Main) }
    val navigator = remember { NavController(backStack) }

    // Adopt the pager's position after the user swipes between tabs by hand.
    LaunchedEffect(pagerState.currentPage) {
        mainPagerState.syncPage()
        hasOverlayPermission = Settings.canDrawOverlays(context)
        CrashHandler.note("页签 ${labels.getOrNull(pagerState.currentPage) ?: pagerState.currentPage}")
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
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    // Only the bottom bar's height leaves this scaffold: each page owns its own
                    // app bar and its own window insets, exactly like SukiSU's page scaffolds.
                    // Letting this one add the status-bar inset too is what put an extra
                    // status-bar-height gap under every page's title.
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        // Driven by selectedPage, not the pager's currentPage, so the highlight
                        // lands the moment the tab is tapped.
                        MaterialBottomBar(
                            items = navItems,
                            selectedIndex = mainPagerState.selectedPage,
                            onSelect = { mainPagerState.animateToPage(it) },
                        )
                    },
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
                                engine = engine,
                                onEngineChange = onEngineChange,
                                onOpenAiConfig = { navigator.push(Route.AiConfig) },
                                onOpenTextRules = { navigator.push(Route.TextRules) },
                                onNotify = notify,
                            )

                            else -> MaterialAboutScreen(
                                outerPadding = outerPadding,
                                useBlur = MiaoState.useBlur,
                                onOpenLicenses = { navigator.push(Route.Licenses) },
                                onNotify = notify,
                            )
                        }
                    }
                }
            }

            entry<Route.AiConfig> {
                LaunchedEffect(Unit) { CrashHandler.note("二级页面 AI 配置") }
                MaterialAiConfigScreen(
                    onBack = { navigator.pop() },
                    useBlur = MiaoState.useBlur,
                    onNotify = notify,
                )
            }

            entry<Route.TextRules> {
                LaunchedEffect(Unit) { CrashHandler.note("二级页面 文本替换") }
                MaterialTextRulesScreen(
                    onBack = { navigator.pop() },
                    useBlur = MiaoState.useBlur,
                    onNotify = notify,
                )
            }

            entry<Route.Licenses> {
                LaunchedEffect(Unit) { CrashHandler.note("二级页面 开源许可") }
                MaterialLicensesScreen(
                    onBack = { navigator.pop() },
                    useBlur = MiaoState.useBlur,
                )
            }
        }

        // The host lives outside the page scaffolds: a message posted from a second-level page
        // must sit above that page, the way a toast would.
        SwipeableSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    bottom = 88.dp +
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
