/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * The shape of this guide — the full-screen glow, the title block, the row list, the bottom action
 * button, the entrance animation — follows HyperCeiler's provisioning flow (library/provision,
 * AGPL-3.0-only). The words, the steps and the permission rows are this app's.
 */

package love.miao.yun.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import love.miao.yun.ui.AppIconText
import love.miao.yun.ui.AppIcons
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The first-run guide, drawn over everything else while it is open.
 *
 * It exists because the app needs two system switches before it does anything useful, and finding
 * that out by trial and error is the worst possible introduction. The About page can bring it back
 * at any time.
 */
@Composable
fun MiuixOnboarding(
    accessibilityEnabled: Boolean,
    hasOverlayPermission: Boolean,
    onOpenAccessibility: () -> Unit,
    onRequestOverlay: () -> Unit,
    onFinish: () -> Unit,
) {
    val pages = GuidePages
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val current = pages[pagerState.currentPage]
    val scheme = MiuixTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    // miuix has no plain `tertiary`, only its container — which is the tint we want here anyway.
    val palette = glowPaletteOf(scheme.primary, scheme.secondary, scheme.tertiaryContainer, scheme.surface)

    // Where the opening ring should be centred. Upstream points it at its logo rather than at the
    // middle of the screen, which is what makes the sweep feel like it belongs to the mark.
    val containerTop = remember { mutableFloatStateOf(0f) }
    val containerHeight = remember { mutableFloatStateOf(0f) }
    val markCentre = remember { mutableFloatStateOf(0f) }
    val circleYOffset = if (containerHeight.floatValue > 0f && markCentre.floatValue > 0f) {
        val middle = containerHeight.floatValue / 2f
        (middle - (markCentre.floatValue - containerTop.floatValue)) / containerHeight.floatValue
    } else {
        0f
    }

    fun goTo(index: Int) = scope.launch { pagerState.animateScrollToPage(index) }

    BackHandler { onFinish() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                containerTop.floatValue = coordinates.positionInWindow().y
                containerHeight.floatValue = coordinates.size.height.toFloat()
            },
    ) {
        // The ring only plays on the page the guide opens with: it belongs to the start of the
        // shader's clock, and on any later page it would look like a bug.
        GlowBackground(
            palette = palette,
            circleVisible = current.step == GuideStep.Welcome,
            circleYOffset = circleYOffset,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            GuideActionBar(
                canGoBack = pagerState.currentPage > 0,
                onBack = { goTo(pagerState.currentPage - 1) },
                onSkip = onFinish,
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { index ->
                when (pages[index].step) {
                    GuideStep.Welcome -> WelcomeStep(
                        page = pages[index],
                        onStart = { goTo(pagerState.currentPage + 1) },
                        onMarkPositioned = { markCentre.floatValue = it },
                    )

                    GuideStep.Permissions -> PermissionsStep(
                        page = pages[index],
                        accessibilityEnabled = accessibilityEnabled,
                        hasOverlayPermission = hasOverlayPermission,
                        onOpenAccessibility = onOpenAccessibility,
                        onRequestOverlay = onRequestOverlay,
                        dark = dark,
                    )

                    GuideStep.Done -> DoneStep(
                        page = pages[index],
                        onMarkPositioned = { markCentre.floatValue = it },
                        dark = dark,
                    )

                    else -> RowsStep(pages[index], dark)
                }
            }

            // The welcome page brings its own round button; every other step ends on this one.
            if (current.step != GuideStep.Welcome) {
                GuideActionButton(
                    text = if (current.step == GuideStep.Done) "开始使用" else "继续",
                    onClick = {
                        if (current.step == GuideStep.Done) {
                            onFinish()
                        } else {
                            goTo(pagerState.currentPage + 1)
                        }
                    },
                )
            }
        }
    }
}

/**
 * Upstream's entrance: the block rises and fades in, and the button arrives a beat later.
 *
 * The numbers follow its own animation helper: the text takes a beat to settle, and the button waits
 * until the opening ring has swept past before it shows up.
 */
@Composable
private fun enterProgress(delayMillis: Int = 0, durationMillis: Int = 1400): Float {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = durationMillis,
                delayMillis = delayMillis,
                easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
            ),
        )
    }
    return progress.value
}

/** Back on the left, skip on the right — the one bar every step shares. */
@Composable
private fun GuideActionBar(canGoBack: Boolean, onBack: () -> Unit, onSkip: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canGoBack) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AppIcons.Back,
                    contentDescription = "上一步",
                    tint = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "跳过",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onSkip)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

/** The opening step: the mark, the name, and one line about what this is. */
@Composable
private fun WelcomeStep(page: GuidePage, onStart: () -> Unit, onMarkPositioned: (Float) -> Unit) {
    val rise = enterProgress()
    val button = enterProgress(delayMillis = 900, durationMillis = 450)

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(30f))

        Column(
            modifier = Modifier.graphicsLayer {
                alpha = rise
                translationY = (1f - rise) * 90f
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppMark(onPositioned = onMarkPositioned)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = page.title,
                style = MiuixTheme.textStyles.title1,
                color = MiuixTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = page.subtitle,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.weight(40f))

        Box(
            modifier = Modifier.graphicsLayer {
                alpha = button
                scaleX = 0.9f + 0.1f * button
                scaleY = 0.9f + 0.1f * button
            },
        ) {
            RoundStartButton(onClick = onStart)
        }

        Spacer(modifier = Modifier.weight(20f))
    }
}

/** The closing step: the same mark, one line, one last hint, and the button below it. */
@Composable
private fun DoneStep(page: GuidePage, onMarkPositioned: (Float) -> Unit, dark: Boolean) {
    val rise = enterProgress()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .graphicsLayer {
                alpha = rise
                translationY = (1f - rise) * 40f
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // No `weight` spacers here: this column scrolls, and a weight in a scrollable column has no
        // space to divide.
        Spacer(modifier = Modifier.height(88.dp))
        AppMark(onPositioned = onMarkPositioned)
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = page.title,
            style = MiuixTheme.textStyles.title1,
            color = MiuixTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = page.subtitle,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(44.dp))
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            page.rows.forEach { row ->
                GuideRowView(
                    icon = row.icon,
                    title = row.title,
                    detail = row.detail,
                    trailing = null,
                    dark = dark,
                    onClick = null,
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** A step that is only a title, a subtitle and a list of rows. */
@Composable
private fun RowsStep(page: GuidePage, dark: Boolean) {
    val rise = enterProgress()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .graphicsLayer {
                alpha = rise
                translationY = (1f - rise) * 40f
            }
            .padding(horizontal = 20.dp),
    ) {
        Spacer(modifier = Modifier.height(36.dp))
        StepTitle(page)
        Spacer(modifier = Modifier.height(20.dp))
        page.rows.forEach { row ->
            GuideRowView(
                icon = row.icon,
                title = row.title,
                detail = row.detail,
                trailing = null,
                dark = dark,
                onClick = null,
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

/** The permission step: the same rows, but with the live state of the two switches. */
@Composable
private fun PermissionsStep(
    page: GuidePage,
    accessibilityEnabled: Boolean,
    hasOverlayPermission: Boolean,
    onOpenAccessibility: () -> Unit,
    onRequestOverlay: () -> Unit,
    dark: Boolean,
) {
    val rise = enterProgress()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .graphicsLayer {
                alpha = rise
                translationY = (1f - rise) * 40f
            }
            .padding(horizontal = 20.dp),
    ) {
        Spacer(modifier = Modifier.height(36.dp))
        StepTitle(page)
        Spacer(modifier = Modifier.height(16.dp))
        GuideRowView(
            icon = AppIcons.Grant,
            title = if (accessibilityEnabled) "无障碍服务已开启" else "无障碍服务未开启",
            detail = "读写输入框要靠它",
            trailing = if (accessibilityEnabled) "已开启" else "去开启",
            dark = dark,
            onClick = onOpenAccessibility,
        )
        Spacer(modifier = Modifier.height(10.dp))
        GuideRowView(
            icon = AppIcons.Floating,
            title = if (hasOverlayPermission) "悬浮窗权限已授予" else "悬浮窗权限未授予",
            detail = "显示那组按钮要靠它",
            trailing = if (hasOverlayPermission) "已授予" else "去授权",
            dark = dark,
            onClick = onRequestOverlay,
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun StepTitle(page: GuidePage) {
    Column {
        Text(
            text = page.title,
            style = MiuixTheme.textStyles.title1,
            color = MiuixTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = page.subtitle,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onBackground,
        )
    }
}

/** One rounded row. A row without an [onClick] is not clickable and carries no [trailing] text. */
@Composable
private fun GuideRowView(
    icon: ImageVector,
    title: String,
    detail: String,
    trailing: String?,
    dark: Boolean,
    onClick: (() -> Unit)?,
) {
    val fill = if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.34f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(fill)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onBackground,
            )
            Text(
                text = detail,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onBackground,
            )
        }
        if (trailing != null) {
            Text(
                text = trailing,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onBackground,
            )
        }
    }
}

/** The round button the welcome step ends on, the way upstream's flow starts. */
@Composable
private fun RoundStartButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(70.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.60f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = AppIcons.Forward,
            contentDescription = "开始",
            tint = Color.White,
            modifier = Modifier.size(30.dp),
        )
    }
}

/** The one action button, sized the same on every step so it never jumps. */
@Composable
private fun GuideActionButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 336.dp)
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.60f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
            )
        }
    }
}

/**
 * The app's mark: text, because the launcher icon is an adaptive icon `painterResource` cannot load.
 *
 * [onPositioned] reports the mark's vertical centre in window coordinates, which is what the opening
 * ring is aimed at.
 */
@Composable
private fun AppMark(onPositioned: (Float) -> Unit) {
    Text(
        text = AppIconText,
        fontSize = 52.sp,
        color = MiuixTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
        modifier = Modifier.onGloballyPositioned { coordinates ->
            onPositioned(coordinates.positionInWindow().y + coordinates.size.height / 2f)
        },
    )
}
