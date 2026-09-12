/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0
 */

package love.miao.yun.ui.predictiveback

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.nav.transition.NavGesture
import top.yukonga.miuix.kmp.nav.transition.NavSettlePhase
import top.yukonga.miuix.kmp.nav.transition.NavSwipeEdge

/**
 * Everything the predictive-back gesture needs, shared by both engines' second-level pages.
 *
 * [dismissProgress] is the master value: `0` while the page is fully open, `1` once it is gone.
 * The gesture writes to it directly, and the release animation continues from wherever the finger
 * left it — which is the whole point of a predictive transition.
 */
class PredictiveBackState internal constructor(
    private val scope: kotlinx.coroutines.CoroutineScope,
) {
    internal val progress = Animatable(0f)

    /** Non-null only while a finger is on screen. */
    var gesture by mutableStateOf<NavGesture?>(null)
        private set

    /** Non-null only while the release animation runs. */
    var settle by mutableStateOf<SettlingBack?>(null)
        private set

    /** Nanoseconds-since-start of the current settle, for transitions that run on a wall clock. */
    private var settleStartNanos by mutableLongStateOf(0L)

    /** Velocity at release, in progress units per second. */
    private var releaseVelocity by mutableFloatStateOf(0f)

    var swipeEdge by mutableStateOf(NavSwipeEdge.Left)
    private var initialTouchY by mutableFloatStateOf(Float.NaN)

    /** The size of the layer being animated; transitions convert depth into pixels with it. */
    var layerSize by mutableStateOf(IntSize.Zero)

    val value: Float get() = progress.value

    /** Drops all gesture state and returns to the fully-open rest position. */
    fun forceReset() {
        gesture = null
        settle = null
        initialTouchY = Float.NaN
        onDismissed = null
        scope.launch { progress.snapTo(0f) }
    }

    fun onGestureStart() {
        gesture = null
        settle = null
    }

    /** Called for every progress event while the gesture is live. */
    fun onGestureProgress(edge: NavSwipeEdge, touchY: Float, fraction: Float) {
        if (initialTouchY.isNaN()) initialTouchY = touchY
        swipeEdge = edge
        gesture = NavGesture(
            progress = fraction,
            swipeEdge = edge,
            touchY = touchY,
            initialTouchY = initialTouchY,
        )
        scope.launch { progress.snapTo(fraction) }
    }

    /**
     * Runs the release animation with the spec the selected style declares.
     *
     * @param commit `true` to finish the dismissal, `false` to spring back to the open page.
     * @param spec the animation to settle with, taken from the chosen transition.
     * @param onFinished invoked once the page is fully dismissed, and only then.
     */
    fun settleTo(commit: Boolean, spec: androidx.compose.animation.core.AnimationSpec<Float>) {
        val target = if (commit) 1f else 0f
        // Cancel springs read the clock too, so the settle context is published either way.
        releaseVelocity = 0f
        settleStartNanos = System.nanoTime()
        settle = SettlingBack(
            settlePhase = if (commit) NavSettlePhase.Commit else NavSettlePhase.Cancel,
            velocity = releaseVelocity,
            elapsed = { (System.nanoTime() - settleStartNanos) / 1_000_000f },
        )
        scope.launch {
            try {
                progress.animateTo(target, spec)
                gesture = null
                initialTouchY = Float.NaN
                if (commit) {
                    onDismissed?.invoke()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } finally {
                settle = null
            }
        }
    }

    /** Set by the caller so the state can actually remove the page once it is gone. */
    internal var onDismissed: (() -> Unit)? = null
}

@Composable
fun rememberPredictiveBackState(): PredictiveBackState {
    val scope = rememberCoroutineScope()
    return remember(scope) { PredictiveBackState(scope) }
}
