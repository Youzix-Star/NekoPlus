// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2023-2026 HyperCeiler Contributions
//
// Ported from HyperCeiler — https://github.com/ReChronoRain/HyperCeiler, library/provision.
// Upstream is AGPL-3.0-only.
//
// What was ported here, and from where:
//   * the flow itself — `state/StateMachine.java`: one step at a time, a single chain, forward and
//     back, nothing else;
//   * `res/anim/provision_slide_in_right.xml` + `provision_slide_out_left.xml` (and the left/right
//     mirror for going back): both pages travel a full width in 350 ms on
//     `@android:anim/accelerate_decelerate_interpolator`, so the incoming page pushes the outgoing
//     one off rather than cross-fading;
//   * the opening step's scale-up (`StartupFragment.launchPermissionPickPage` +
//     `utils/ViewUtils.captureRoundedBitmap`): the round button's circle is snapshotted, the button
//     hides (`exitStartedCallback`), and that circle is scaled up until it covers the screen — over
//     `#99000000` (`colors.xml: anim_foreground_color`), which is what makes the circle read as a
//     bright hole opening in a darkened screen. The button comes back when it finishes
//     (`exitFinishCallback`), so returning to the first page still has one.
//
// Not ported, deliberately: the ActivityOptions/PixelCopy plumbing (there is only one screen here, so
// the same picture is drawn directly), and upstream's own AIDL round trip to the HyperOS OOBE
// service, which is what plays the transition on real MIUI — its duration lives in that closed
// source, so `REVEAL_MILLIS` is our own pick from the same range as its other transitions.

package love.miao.yun.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.launch
import kotlin.math.hypot

/** `@android:anim/accelerate_decelerate_interpolator`, the easing every slide upstream uses. */
private val AccelerateDecelerate = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

/** `provision_slide_in_right.xml` / `provision_slide_out_left.xml`: 350 ms, a full width. */
private const val SLIDE_MILLIS = 350

/** The scale-up lives in upstream's closed-source helper; this is picked from its neighbours' range. */
private const val REVEAL_MILLIS = 420


/** `colors.xml: anim_foreground_color` — the screen darkens while the circle opens. */
private val RevealForeground = Color.Black.copy(alpha = 0x99 / 255f)

/**
 * The step chain, in the shape upstream keeps it: a single list walked one step at a time.
 *
 * Deliberately stateless and free of Compose so it can be unit-tested; the wizard owns the live index.
 */
internal class GuideFlow(private val stepCount: Int) {

    val size: Int get() = stepCount

    fun isFirst(index: Int): Boolean = index <= 0

    fun isLast(index: Int): Boolean = index >= stepCount - 1

    /** Upstream's `run(-1)`: the next step, or nothing when this was the last one. */
    fun forward(index: Int): Int? = if (isLast(index)) null else index + 1

    /** Upstream's `run(0)`: the step behind, or nothing when this was the first one. */
    fun backward(index: Int): Int? = if (isFirst(index)) null else index - 1
}

/** Where the round start button sits, in the wizard's own coordinates. */
internal class StartCircle(val centre: Offset, val radius: Float)

/**
 * What a page needs to draw itself and to move the flow along.
 *
 * [busy] is true while a transition is running, and while the page underneath is on its way out:
 * upstream debounces its round button and disables its action buttons the same way.
 */
internal class GuideNav(
    val page: GuidePage,
    val index: Int,
    val isFirst: Boolean,
    val isLast: Boolean,
    val busy: Boolean,
    /** True for the page underneath while it is on its way out. */
    val leaving: Boolean,
    val onNext: () -> Unit,
    val onBack: () -> Unit,
    val onSkip: () -> Unit,
    /** The opening page reports its round button so the circle can start exactly on top of it. */
    val reportStartCircle: (Modifier) -> Modifier,
    /** …and its mark, which is what the opening ring is centred on. */
    val reportGlowAnchor: (Modifier) -> Modifier,
)

private sealed interface Transition {
    val forward: Boolean

    class Slide(override val forward: Boolean) : Transition

    class Reveal(
        override val forward: Boolean,
        val from: StartCircle,
        val toRadius: Float,
    ) : Transition
}

/**
 * The guide, one step at a time, with upstream's own transitions.
 *
 * Every page is rendered by [page]; this owns the aurora (which never moves — upstream's two
 * activities both draw the same shader, so a shared one looks the same and never restarts), the
 * step index, and the animation between steps.
 */
@Composable
internal fun GuideWizard(
    pages: List<GuidePage>,
    palette: GlowPalette,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    page: @Composable (GuideNav) -> Unit,
) {
    val flow = remember(pages) { GuideFlow(pages.size) }
    val scope = rememberCoroutineScope()

    var index by remember { mutableIntStateOf(0) }
    var outgoing by remember { mutableStateOf<Int?>(null) }
    var transition by remember { mutableStateOf<Transition?>(null) }
    var startCircle by remember { mutableStateOf<StartCircle?>(null) }
    var markCentreY by remember { mutableStateOf(0f) }
    var containerTop by remember { mutableStateOf(0f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val progress = remember { Animatable(1f) }

    // Set before the coroutine runs, so two taps in the same frame cannot start two transitions.
    var busy by remember { mutableStateOf(false) }

    fun move(forward: Boolean, fromIndex: Int) {
        if (busy) return
        val target = if (forward) flow.forward(fromIndex) else flow.backward(fromIndex) ?: return
        val circle = startCircle

        // Leaving the first page is the one transition upstream animates as a scale-up of the round
        // button; everything else is a slide. Without a measured button (a race on the very first
        // frame), fall back to the slide rather than opening a circle from nowhere.
        val kind: Transition = if (forward && fromIndex == 0 && circle != null) {
            Transition.Reveal(
                forward = true,
                from = circle,
                toRadius = farthestCorner(circle.centre, containerSize),
            )
        } else {
            Transition.Slide(forward)
        }

        busy = true
        scope.launch {
            progress.snapTo(0f)
            outgoing = fromIndex
            index = target
            transition = kind
            progress.animateTo(
                targetValue = 1f,
                animationSpec = if (kind is Transition.Reveal) {
                    tween(REVEAL_MILLIS, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
                } else {
                    tween(SLIDE_MILLIS, easing = AccelerateDecelerate)
                },
            )
            outgoing = null
            transition = null
            busy = false
        }
    }

    fun nav(
        pageIndex: Int,
        active: Boolean,
        report: (Modifier) -> Modifier,
        reportAnchor: (Modifier) -> Modifier,
    ) = GuideNav(
        page = pages[pageIndex],
        index = pageIndex,
        isFirst = flow.isFirst(pageIndex),
        isLast = flow.isLast(pageIndex),
        busy = busy || !active,
        leaving = !active,
        onNext = { move(forward = true, fromIndex = pageIndex) },
        onBack = { move(forward = false, fromIndex = pageIndex) },
        onSkip = onFinish,
        reportStartCircle = report,
        reportGlowAnchor = reportAnchor,
    )

    val reportStart: (Modifier) -> Modifier = { base ->
        base.onGloballyPositioned { coordinates ->
            val position = coordinates.positionInRoot()
            val size = coordinates.size
            startCircle = StartCircle(
                centre = Offset(position.x + size.width / 2f, position.y + size.height / 2f),
                radius = size.width / 2f,
            )
        }
    }

    // Upstream centres the opening ring on its logo (`setCircleYOffsetWithView`), not on the middle
    // of the screen; the shader wants that as a fraction of the screen, positive meaning upwards.
    val reportAnchor: (Modifier) -> Modifier = { base ->
        base.onGloballyPositioned { coordinates ->
            markCentreY = coordinates.positionInRoot().y + coordinates.size.height / 2f
        }
    }

    val circleYOffset = if (containerSize.height > 0 && markCentreY > 0f) {
        val middle = containerSize.height / 2f
        (middle - (markCentreY - containerTop)) / containerSize.height
    } else {
        0f
    }

    val current = transition

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned {
                containerSize = it.size
                containerTop = it.positionInRoot().y
            },
    ) {
        GlowBackground(
            palette = palette,
            circleVisible = index == 0 && outgoing == null,
            circleYOffset = circleYOffset,
        )

        // The page on its way out. Upstream slides it a whole width away and leaves it there; the
        // scale-up instead keeps it in place, under the incoming page and the darkening.
        outgoing?.let { leaving ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        when (current) {
                            is Transition.Slide ->
                                translationX = (if (current.forward) -1f else 1f) * progress.value * size.width.toFloat()

                            else -> Unit
                        }
                    },
            ) {
                // Keyed by step: without it Compose would hand the incoming page the outgoing page's
                // remembered state and its entrance animation would not play again.
                key(leaving) {
                    page(nav(leaving, active = false, report = { it }, reportAnchor = { it }))
                }
            }
        }

        if (current is Transition.Reveal) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(RevealForeground),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    when (current) {
                        is Transition.Slide ->
                            translationX = (if (current.forward) 1f else -1f) * (1f - progress.value) * size.width.toFloat()

                        is Transition.Reveal -> {
                            val radius = current.from.radius +
                                (current.toRadius - current.from.radius) * progress.value
                            shape = CircleClip(current.from.centre, radius)
                            clip = true
                        }

                        null -> Unit
                    }
                },
        ) {
            key(index) {
                page(nav(index, active = true, report = reportStart, reportAnchor = reportAnchor))
            }
        }
    }
}

/** A circle of [radius] around [centre], used as the clip the scale-up opens through. */
private class CircleClip(private val centre: Offset, private val radius: Float) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Generic(
        Path().apply {
            addOval(Rect(center = centre, radius = radius))
        },
    )
}

/**
 * How far the circle has to grow before no corner is left uncovered.
 *
 * Everything is in pixels: the button's centre was measured with `positionInRoot`, and the layer that
 * gets clipped is in the same space.
 */
private fun farthestCorner(centre: Offset, size: IntSize): Float {
    if (size.width == 0 || size.height == 0) return 0f
    val horizontal = maxOf(centre.x, size.width - centre.x)
    val vertical = maxOf(centre.y, size.height - centre.y)
    return hypot(horizontal, vertical)
}
