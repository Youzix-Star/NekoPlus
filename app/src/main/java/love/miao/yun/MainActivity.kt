/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0
 */

package love.miao.yun

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import love.miao.yun.service.FloatingWindowService
import love.miao.yun.ui.AppIcons
import love.miao.yun.ui.MiaoTheme
import love.miao.yun.ui.about.AboutScreen
import love.miao.yun.ui.floating.FloatingScreen
import love.miao.yun.ui.home.HomeScreen
import love.miao.yun.ui.liquid.FloatingBottomBar
import love.miao.yun.ui.settings.SettingsScreen
import kotlin.coroutines.cancellation.CancellationException

private const val TAB_HOME = 0
private const val TAB_FLOATING = 1
private const val TAB_SETTINGS = 2
private const val TAB_ABOUT = 3

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { MiaoApp() }
    }
}

/**
 * Application shell: a Miuix theme, a pager of tabs, and the liquid-glass floating bottom bar.
 *
 * Every tab is a UI skeleton — nothing is persisted and nothing is processed. The only live state
 * is whether the floating window is on screen.
 */
@Composable
fun MiaoApp() {
    var colorSchemeMode by remember { mutableStateOf(ColorSchemeMode.System) }
    var useLiquidGlass by remember { mutableStateOf(true) }

    MiaoTheme(colorSchemeMode = colorSchemeMode) {
        MiaoShell(
            colorSchemeMode = colorSchemeMode,
            onColorSchemeModeChange = { colorSchemeMode = it },
            useLiquidGlass = useLiquidGlass,
            onUseLiquidGlassChange = { useLiquidGlass = it },
        )
    }
}

@Composable
private fun MiaoShell(
    colorSchemeMode: ColorSchemeMode,
    onColorSchemeModeChange: (ColorSchemeMode) -> Unit,
    useLiquidGlass: Boolean,
    onUseLiquidGlassChange: (Boolean) -> Unit,
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

    val pagerState = rememberPagerState(pageCount = { navigationItems.size })
    val currentPage = pagerState.currentPage
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

    // Re-check the overlay permission whenever the tab changes, so returning from system settings
    // is reflected without restarting the app.
    LaunchedEffect(currentPage) {
        hasOverlayPermission = Settings.canDrawOverlays(context)
    }

    // ---- predictive back: any tab returns to the home tab ----
    val backProgress = remember { Animatable(0f) }
    var backSwipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    val settleScope = rememberCoroutineScope()

    PredictiveBackHandler(enabled = currentPage != TAB_HOME) { progress ->
        try {
            progress.collect { event ->
                backSwipeEdge = event.swipeEdge
                backProgress.snapTo(event.progress)
            }
            settleScope.launch {
                backProgress.animateTo(1f, tween(durationMillis = 140))
                pagerState.scrollToPage(TAB_HOME)
                backProgress.animateTo(0f, tween(durationMillis = 220))
            }
        } catch (cancelled: CancellationException) {
            settleScope.launch {
                backProgress.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
            }
            throw cancelled
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val amount = backProgress.value
                    val scale = 1f - 0.10f * amount
                    scaleX = scale
                    scaleY = scale
                    val drift = 32.dp.toPx() * amount
                    translationX = if (backSwipeEdge == BackEventCompat.EDGE_RIGHT) -drift else drift
                    alpha = 1f - 0.30f * amount
                },
        ) {
            Scaffold(
                bottomBar = {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        FloatingBottomBar(
                            items = navigationItems,
                            selectedIndex = currentPage,
                            onItemClick = { index ->
                                coroutineScope.launch { pagerState.animateScrollToPage(index) }
                            },
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
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        when (page) {
                            TAB_HOME -> HomeScreen(
                                contentPadding = pagePadding,
                                floatingRunning = MiaoState.floatingRunning,
                                hasOverlayPermission = hasOverlayPermission,
                                onToggleFloating = toggleFloating,
                                onRequestOverlay = requestOverlay,
                                onNotify = notify,
                            )

                            TAB_FLOATING -> FloatingScreen(
                                contentPadding = pagePadding,
                                floatingRunning = MiaoState.floatingRunning,
                                onToggleFloating = toggleFloating,
                                onNotify = notify,
                            )

                            TAB_SETTINGS -> SettingsScreen(
                                contentPadding = pagePadding,
                                colorSchemeMode = colorSchemeMode,
                                onColorSchemeModeChange = onColorSchemeModeChange,
                                useLiquidGlass = useLiquidGlass,
                                onUseLiquidGlassChange = onUseLiquidGlassChange,
                                onNotify = notify,
                            )

                            else -> AboutScreen(
                                contentPadding = pagePadding,
                                onNotify = notify,
                            )
                        }
                    }
                }
            }
        }
    }
}
