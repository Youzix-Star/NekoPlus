/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * The flow and the transitions live in `GuideWizard.kt`, ported from HyperCeiler's provisioning
 * module (library/provision, AGPL-3.0-only); this file is the Material-drawn furniture of each page.
 */

package top.youzix.nekoplus.ui.onboarding

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.youzix.nekoplus.ui.AppIconText
import top.youzix.nekoplus.ui.AppIcons

/**
 * The first-run guide, drawn over everything else while it is open.
 *
 * It exists because the app needs two system switches before it does anything useful, and finding
 * that out by trial and error is the worst possible introduction. The About page can bring it back
 * at any time.
 */
@Composable
fun MaterialOnboarding(
    accessibilityEnabled: Boolean,
    hasOverlayPermission: Boolean,
    onOpenAccessibility: () -> Unit,
    onRequestOverlay: () -> Unit,
    onFinish: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val palette = glowPaletteOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.surface)

    BackHandler { onFinish() }

    GuideWizard(pages = GuidePages, palette = palette, onFinish = onFinish) { nav ->
        GuidePageFrame(
            nav = nav,
            dark = dark,
            accessibilityEnabled = accessibilityEnabled,
            hasOverlayPermission = hasOverlayPermission,
            onOpenAccessibility = onOpenAccessibility,
            onRequestOverlay = onRequestOverlay,
        )
    }
}

/** One whole page: the bar on top, the step's own content, and the one action button at the bottom. */
@Composable
private fun GuidePageFrame(
    nav: GuideNav,
    dark: Boolean,
    accessibilityEnabled: Boolean,
    hasOverlayPermission: Boolean,
    onOpenAccessibility: () -> Unit,
    onRequestOverlay: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        GuideActionBar(
            canGoBack = !nav.isFirst,
            onBack = nav.onBack,
            onSkip = nav.onSkip,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when (nav.page.step) {
                GuideStep.Welcome -> WelcomeStep(nav)
                GuideStep.Permissions -> PermissionsStep(
                    nav = nav,
                    accessibilityEnabled = accessibilityEnabled,
                    hasOverlayPermission = hasOverlayPermission,
                    onOpenAccessibility = onOpenAccessibility,
                    onRequestOverlay = onRequestOverlay,
                    dark = dark,
                )

                GuideStep.Done -> DoneStep(nav, dark)
                else -> RowsStep(nav, dark)
            }
        }

        // The welcome page brings its own round button; every other step ends on this one.
        if (nav.page.step != GuideStep.Welcome) {
            GuideActionButton(
                text = if (nav.isLast) "开始使用" else "继续",
                enabled = !nav.busy,
                onClick = nav.onNext,
            )
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
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "跳过",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onSkip)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

/** The opening step: the mark, the name, and the round button that opens the next page. */
@Composable
private fun WelcomeStep(nav: GuideNav) {
    // A page on its way out is drawn settled: upstream slides the whole window away, it does not
    // replay the entrance on the way.
    val rise = if (nav.leaving) 1f else enterProgress()
    val button = if (nav.leaving) 1f else enterProgress(delayMillis = 900, durationMillis = 450)

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
            AppMark(modifier = nav.reportGlowAnchor(Modifier))
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = nav.page.title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = nav.page.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.weight(40f))

        // While the page is on its way out the button is hidden (upstream hides it the moment the
        // scale-up starts); when it is done, the button is back — so coming back here still works.
        Box(
            modifier = Modifier
                .alpha(if (nav.leaving) 0f else 1f)
                .graphicsLayer {
                    alpha = button
                    scaleX = 0.9f + 0.1f * button
                    scaleY = 0.9f + 0.1f * button
                },
        ) {
            RoundStartButton(
                onClick = nav.onNext,
                modifier = nav.reportStartCircle(Modifier),
            )
        }

        Spacer(modifier = Modifier.weight(20f))
    }
}

/** The closing step: the same mark, one line, one last hint, and the button below it. */
@Composable
private fun DoneStep(nav: GuideNav, dark: Boolean) {
    val rise = if (nav.leaving) 1f else enterProgress()

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
        AppMark(modifier = nav.reportGlowAnchor(Modifier))
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = nav.page.title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = nav.page.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(44.dp))
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            nav.page.rows.forEach { row ->
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
private fun RowsStep(nav: GuideNav, dark: Boolean) {
    val rise = if (nav.leaving) 1f else enterProgress()

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
        StepTitle(nav.page)
        Spacer(modifier = Modifier.height(20.dp))
        nav.page.rows.forEach { row ->
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
    nav: GuideNav,
    accessibilityEnabled: Boolean,
    hasOverlayPermission: Boolean,
    onOpenAccessibility: () -> Unit,
    onRequestOverlay: () -> Unit,
    dark: Boolean,
) {
    val rise = if (nav.leaving) 1f else enterProgress()

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
        StepTitle(nav.page)
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
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = page.subtitle,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
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
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** The round button the opening step ends on — the thing the scale-up opens out of. */
@Composable
private fun RoundStartButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
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
private fun GuideActionButton(text: String, enabled: Boolean, onClick: () -> Unit) {
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
                .background(Color.Black.copy(alpha = if (enabled) 0.60f else 0.30f))
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
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

/** The app's mark: text, because the launcher icon is an adaptive icon `painterResource` cannot load. */
@Composable
private fun AppMark(modifier: Modifier = Modifier) {
    Text(
        text = AppIconText,
        fontSize = 52.sp,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}
