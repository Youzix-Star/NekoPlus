/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package love.miao.yun.ui.predictiveback

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.nav.gesture.PredictiveBackHandler
import top.yukonga.miuix.kmp.nav.transition.NavSwipeEdge

/**
 * The geometry one back motion is made of.
 *
 * Every value describes the same drag: how the two layers look while the second-level page is fully
 * open, and how they look once it is gone. The transforms interpolate between those two ends as a
 * pure function of the drag's progress, which is what keeps the finger tracking exact.
 *
 * @param pageFollowsFinger whether the dismissing page translates with the drag at all.
 * @param pageVerticalDrift whether it also tracks the finger's vertical movement.
 * @param pageScaleAtDismiss scale the dismissing page shrinks to as it leaves.
 * @param pageCornerRadiusAtDismiss corner radius it rounds to as it leaves, or `null` for square.
 * @param coveredParallaxFraction how far the revealed page slides toward the leading edge while it
 *   is covered, as a fraction of the screen width.
 * @param coveredDimAtRest how transparent the revealed page is while it is covered.
 * @param coveredScrimAtRest alpha of the dark scrim laid over the revealed page while covered.
 * @param commitSpec the animation that carries the drag to completion once the finger lifts.
 * @param cancelSpec the animation that springs the page back when the gesture is abandoned.
 */
internal data class BackMotionConfig(
    val pageFollowsFinger: Boolean,
    val pageVerticalDrift: Boolean,
    val pageScaleAtDismiss: Float,
    val pageCornerRadiusAtDismiss: Dp?,
    val coveredParallaxFraction: Float,
    val coveredDimAtRest: Float,
    val coveredScrimAtRest: Float,
    val commitSpec: AnimationSpec<Float>,
    val cancelSpec: AnimationSpec<Float>,
)

/**
 * The one motion the guide's predictive back plays.
 *
 * There used to be three of these (`Aosp` / `Miuix` / `无动画`) behind a setting in 外观. The user
 * settled it: miuix only, and miuix's own gesture — so there is nothing left to choose and nothing
 * left to keep for a case that can no longer happen. These are the numbers the `Miuix` style
 * carried; the geometry they feed still lives in one place ([Modifier.backLayer]), which is what
 * keeps the two UI engines animating identically.
 */
internal val MiuixBackMotion = BackMotionConfig(
    pageFollowsFinger = true,
    pageVerticalDrift = false,
    pageScaleAtDismiss = 1f,
    pageCornerRadiusAtDismiss = 24.dp,
    coveredParallaxFraction = 0.25f,
    coveredDimAtRest = 0.1f,
    coveredScrimAtRest = 0.18f,
    commitSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
    cancelSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
)

/** How far the drifting page is kept away from the screen edge, mirroring the platform gesture. */
private val VerticalDriftMargin = 8.dp

/** How long the second-level page takes to slide into place when it is opened. */
private const val ENTER_DURATION_MILLIS = 280

/** Which of the two stacked layers a transform is being computed for. */
internal enum class BackLayerRole { Outgoing, Covered }

/** What the second-level page is currently doing. */
internal enum class BackPhase {
    /** Settling into place after being opened, or sitting still. */
    Enter,

    /** A back gesture is on screen right now. */
    Gesture,

    /** The finger has lifted and the page is on its way out — or back. */
    Settle,
}

/**
 * Everything the transform needs for one layer, read inside the `graphicsLayer` block so the whole
 * animation stays off the recomposition path.
 */
internal class BackLayerInfo(
    val role: BackLayerRole,
    val progress: Float,
    val phase: BackPhase,
    val fromRight: Boolean,
    val touchY: Float,
    val touchYStart: Float,
    val rtl: Boolean,
)

/**
 * The single value the whole predictive-back animation is a function of.
 *
 * [progress] runs from `0` (the second-level page is fully open) to `1` (it is gone). The gesture
 * writes to it directly while a finger is down, and the release animation continues from wherever
 * the finger left it.
 *
 * The release deliberately runs on a scope that outlives the gesture handler: androidx cancels the
 * handler's coroutine the moment the gesture ends, so an animation started from there would be
 * cancelled along with it — which is exactly how a page ends up stuck halfway through a transition.
 */
internal class BackController(private val scope: CoroutineScope) {
    internal val progress = Animatable(0f)

    internal var phase by mutableStateOf(BackPhase.Enter)
    internal var fromRight by mutableStateOf(false)
    internal var touchY by mutableFloatStateOf(0f)
    internal var touchYStart by mutableFloatStateOf(Float.NaN)

    internal val value: Float get() = progress.value

    /** Drops back to the resting state: no gesture, no offset, page fully open. */
    internal suspend fun reset() {
        phase = BackPhase.Enter
        fromRight = false
        touchY = 0f
        touchYStart = Float.NaN
        progress.snapTo(0f)
    }

    internal fun beginGesture() {
        phase = BackPhase.Gesture
        fromRight = false
        touchYStart = Float.NaN
    }

    /** Called for every event of a live gesture. */
    internal suspend fun onProgress(fromRightEdge: Boolean, touchYValue: Float, fraction: Float) {
        // The first event of a gesture establishes the reference point the vertical drift is
        // measured from; the platform only ever reports the current touch position.
        if (touchYStart.isNaN()) touchYStart = touchYValue
        fromRight = fromRightEdge
        touchY = touchYValue
        progress.snapTo(fraction.coerceIn(0f, 1f))
    }

    /**
     * Carries the drag to its end, on a scope the gesture handler cannot cancel.
     *
     * @param commit `true` to finish the dismissal, `false` to spring the page back.
     * @param onFinished invoked only once a committed dismissal has fully played out.
     */
    internal fun settle(commit: Boolean, spec: AnimationSpec<Float>, onFinished: () -> Unit) {
        scope.launch {
            phase = BackPhase.Settle
            progress.animateTo(if (commit) 1f else 0f, spec)
            phase = BackPhase.Enter
            if (commit) onFinished()
        }
    }
}

@Composable
private fun rememberBackController(): BackController {
    val scope = rememberCoroutineScope()
    return remember(scope) { BackController(scope) }
}

/**
 * Hosts a second-level page over the tab content and animates the back gesture.
 *
 * Both UI engines mount their level-one content and their second-level page through this, so the
 * gesture behaves identically whichever engine is drawing, and there is only one implementation of
 * it to get right.
 *
 * @param subPageOpen whether a second-level page is showing.
 * @param onDismissed invoked once a committed dismissal has finished animating, and only then.
 * @param levelOne the tab content, revealed as the page above it leaves.
 * @param subPage the second-level page, given the action that closes it; only composed while
 *   [subPageOpen] is true.
 */
@Composable
fun PredictiveBackHost(
    subPageOpen: Boolean,
    onDismissed: () -> Unit,
    levelOne: @Composable () -> Unit,
    subPage: @Composable (close: () -> Unit) -> Unit,
) {
    val controller = rememberBackController()
    val dismiss by rememberUpdatedState(onDismissed)

    // miuix's own handler (`top.yukonga.miuix.kmp.nav.gesture`), not androidx's: it reports the
    // gesture as `NavBackEvent(progress, swipeEdge, touchY, frameTimeMillis)` and, unlike androidx's
    // callback flow, hands us a per-gesture stream plus separate commit/cancel callbacks — so the
    // release no longer has to be guessed from a CancellationException. It registers on the
    // navigation-event dispatcher, which androidx.activity's ComponentActivity installs on the decor
    // view, so this is live in our tree (verified against activity 1.13.0's sources).
    PredictiveBackHandler(
        enabled = subPageOpen,
        onProgress = { events ->
            controller.beginGesture()
            events.collect { event ->
                controller.onProgress(
                    fromRightEdge = event.swipeEdge == NavSwipeEdge.Right,
                    touchYValue = event.touchY,
                    fraction = event.progress,
                )
            }
        },
        // Both terminals run on the controller's own scope (see BackController.settle): the
        // handler's coroutine is gone by the time these fire, so an animation started from inside
        // it would be cancelled on the spot and leave the page stuck mid-gesture.
        onCommit = { controller.settle(commit = true, spec = MiuixBackMotion.commitSpec) { dismiss() } },
        onCancel = { controller.settle(commit = false, spec = MiuixBackMotion.cancelSpec) {} },
    )

    LaunchedEffect(subPageOpen) {
        if (subPageOpen) {
            controller.reset()
            // Opening is a plain slide in from the trailing edge, so the page starts fully off
            // screen and the style's own motion only takes over once a gesture begins.
            controller.progress.snapTo(1f)
            controller.progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = ENTER_DURATION_MILLIS,
                    easing = FastOutSlowInEasing,
                ),
            )
        } else {
            // Also the recovery path: whatever a cancelled or committed gesture left behind, the
            // page is gone now, so every layer must return to its resting transform.
            controller.reset()
        }
    }

    // Read here rather than inside the layer: a graphics layer knows its density but not which
    // way the layout runs, and the drag has to be mirrored in RTL.
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // A back button plays the same dismissal as the gesture: the page travels the rest of the way
    // out and only then is it taken down, so neither route can leave a half-moved page behind.
    val close: () -> Unit = {
        controller.settle(commit = true, spec = MiuixBackMotion.commitSpec) { dismiss() }
    }

    val coveredInfo = {
        BackLayerInfo(
            role = BackLayerRole.Covered,
            progress = controller.value,
            phase = controller.phase,
            fromRight = controller.fromRight,
            touchY = controller.touchY,
            touchYStart = controller.touchYStart,
            rtl = rtl,
        )
    }
    val outgoingInfo = {
        BackLayerInfo(
            role = BackLayerRole.Outgoing,
            progress = controller.value,
            phase = controller.phase,
            fromRight = controller.fromRight,
            touchY = controller.touchY,
            touchYStart = controller.touchYStart,
            rtl = rtl,
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // The level-one content only carries a transform while a page sits above it; at every other
        // moment the modifier is absent entirely, so the tab shell renders exactly as it does on
        // its own — no extra render layer, no re-clipped glass backdrop.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (subPageOpen) Modifier.backLayer(coveredInfo) else Modifier),
        ) {
            levelOne()
        }

        if (subPageOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .backScrim { controller.value },
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .backLayer(outgoingInfo),
            ) {
                subPage(close)
            }
        }
    }
}

/**
 * Applies the guide's back motion to one layer.
 *
 * Read the body as a pair of end states: `progress == 0` is the second-level page fully open,
 * `progress == 1` is it fully gone. Both layers interpolate between those two ends, which is what
 * makes the finger tracking exact — the transform is a pure function of the drag, nothing else.
 */
internal fun Modifier.backLayer(info: () -> BackLayerInfo): Modifier =
    this.graphicsLayer {
        val state = info()
        val config = MiuixBackMotion
        val progress = state.progress.coerceIn(0f, 1f)

        // In LTR a drag from the left edge carries the page to the right; a drag from the right
        // edge carries it the other way. RTL mirrors both.
        val layoutSign = if (state.rtl) -1f else 1f
        val sign = if (state.fromRight) -layoutSign else layoutSign

        when (state.role) {
            BackLayerRole.Outgoing -> {
                scaleX = 1f
                scaleY = 1f
                translationX = 0f
                translationY = 0f
                alpha = 1f
                clip = false
                shape = RectangleShape

                if (state.phase == BackPhase.Enter) {
                    translationX = sign * progress * size.width
                    alpha = 1f - 0.5f * progress
                } else if (config.pageFollowsFinger) {
                    val scale = 1f + (config.pageScaleAtDismiss - 1f) * progress
                    scaleX = scale
                    scaleY = scale
                    translationX = sign * progress * size.width

                    if (config.pageVerticalDrift) {
                        val start = state.touchYStart
                        val rawDelta = if (start.isNaN()) 0f else state.touchY - start
                        val half = size.height / 2f
                        if (half > 0f) {
                            // Quadratic falloff: a small drift barely moves the page, a long one
                            // settles against the edge instead of running off it.
                            val ratio = (abs(rawDelta) / half).coerceIn(0f, 1f)
                            val damped = 1f - (1f - ratio) * (1f - ratio)
                            val maxShift = (
                                (size.height - size.height * scale) / 2f -
                                    VerticalDriftMargin.toPx()
                                ).coerceAtLeast(0f)
                            translationY = maxShift * damped * (if (rawDelta < 0f) -1f else 1f)
                        }
                    }

                    val radius = config.pageCornerRadiusAtDismiss
                    if (radius != null) {
                        val px = radius.toPx() * progress
                        if (px > 0f) {
                            shape = RoundedCornerShape(px)
                            clip = true
                        }
                    }
                }
            }

            BackLayerRole.Covered -> {
                // How hidden this layer is: 1 while the page above it is fully open, 0 once that
                // page has been dismissed and this one has taken its place.
                val covered = 1f - progress
                scaleX = 1f
                scaleY = 1f
                translationX = -sign * config.coveredParallaxFraction * covered * size.width
                translationY = 0f
                alpha = 1f - config.coveredDimAtRest * covered
                clip = false
                shape = RectangleShape
            }
        }
    }

/** The scrim laid over the revealed page; it fades out as that page takes over. */
internal fun Modifier.backScrim(config: BackMotionConfig, progress: () -> Float): Modifier {
    if (config.coveredScrimAtRest <= 0f) return this
    return this
        .graphicsLayer {
            alpha = config.coveredScrimAtRest * (1f - progress().coerceIn(0f, 1f))
        }
        .background(Color.Black)
}
