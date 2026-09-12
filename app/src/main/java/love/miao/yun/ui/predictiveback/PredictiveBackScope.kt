/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0
 */

package love.miao.yun.ui.predictiveback

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import top.yukonga.miuix.kmp.nav.runtime.NavChange
import top.yukonga.miuix.kmp.nav.transition.NavGesture
import top.yukonga.miuix.kmp.nav.transition.NavMotion
import top.yukonga.miuix.kmp.nav.transition.NavRole
import top.yukonga.miuix.kmp.nav.transition.NavSettle
import top.yukonga.miuix.kmp.nav.transition.NavSettlePhase
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitionScope

/**
 * A [NavTransitionScope] this app fills in by hand.
 *
 * miuix normally builds this inside its own navigation runtime, which this project does not use —
 * the shell here is a pager plus a single second-level page. Everything a transition actually
 * reads, though, is plain state: how deep the layer sits, which role it plays, and how far the
 * back gesture has travelled. Supplying that state ourselves is what lets the reference project's
 * transition code run unchanged, and is why both the AOSP and the miuix style can be offered.
 *
 * Every value is taken through a lambda rather than captured directly: `NavTransition` reads its
 * scope inside a `graphicsLayer { }` block, so these must stay deferred to keep the animation off
 * the recomposition path.
 *
 * @param dismissProgress how far the dismiss gesture has travelled: `0` fully shown, `1` gone.
 * @param covered `true` for the page being revealed *behind* the one being dismissed.
 */
class PredictiveBackScope(
    private val dismissProgress: () -> Float,
    private val covered: Boolean,
    private val gestureState: () -> NavGesture?,
    private val settleState: () -> NavSettle?,
    private val layerSize: () -> IntSize,
    private val layoutDirectionValue: LayoutDirection,
    private val densityValue: Density,
) : NavTransitionScope {

    /**
     * The dismissed page sits just below the top, so its depth runs from `-1` (fully shown) to `0`
     * (gone) — exactly what the transitions' `topProgress` expects. The page revealed behind it
     * starts fully covering and uncovers as the gesture advances.
     */
    override val relativeDepth: Float
        get() = if (covered) (1f - dismissProgress()) else -dismissProgress()

    override val role: NavRole
        get() = if (covered) NavRole.Covered else NavRole.Outgoing

    /** Second-level pages are only ever popped, never pushed. */
    override val change: NavChange
        get() = NavChange.Pop

    override val gesture: NavGesture?
        get() = gestureState()

    override val settle: NavSettle?
        get() = settleState()

    override val layoutSize: IntSize
        get() = layerSize()

    override val layoutDirection: LayoutDirection
        get() = layoutDirectionValue

    override val density: Density
        get() = densityValue
}

/** Applies [transition] to this layer using the state above. */
fun Modifier.navTransition(transition: NavTransition, scope: NavTransitionScope): Modifier =
    with(transition) { transformEntry(scope) }

// ------------------------------------------------------------------ settle timing

/**
 * The spec to settle with once the finger lifts, read off the transition itself so the timing
 * tracks whichever style is selected.
 *
 * [NavSettleSpec] is a small sealed type rather than an `AnimationSpec`, and miuix's own converter
 * for it is `internal`, so the two cases are mapped here.
 */
fun NavTransition.commitSpec(): AnimationSpec<Float> = motion.commit.toSpec()

fun NavTransition.cancelSpec(): AnimationSpec<Float> = motion.cancel.toSpec()

private fun NavSettleSpec.toSpec(): AnimationSpec<Float> = when (this) {
    is NavSettleSpec.Tween -> tween(durationMillis = durationMillis, easing = easing)
    is NavSettleSpec.Spring -> spring(dampingRatio = dampingRatio, stiffness = stiffness)
}

/** A [NavSettle] reporting how long the release animation has been running. */
class SettlingBack(
    private val settlePhase: NavSettlePhase,
    private val velocity: Float,
    private val elapsed: () -> Float,
) : NavSettle {
    override val phase: NavSettlePhase get() = settlePhase
    override val releaseVelocity: Float get() = velocity
    override val elapsedMillis: Float get() = elapsed()
}

/** The motion a style declares, exposed so callers can reason about it if needed. */
val NavTransition.navMotion: NavMotion get() = motion

/** Reads the layout direction and density a transition needs out of the composition. */
@Composable
fun rememberPredictiveBackScope(
    dismissProgress: () -> Float,
    covered: Boolean,
    layerSize: () -> IntSize,
    gesture: () -> NavGesture?,
    settle: () -> NavSettle?,
): PredictiveBackScope {
    val direction = LocalLayoutDirection.current
    val density = LocalDensity.current
    // Deliberately not remembered: the transition only reads this from inside a graphicsLayer
    // block, and rebuilding a tiny holder is cheaper than risking a captured stale lambda.
    return PredictiveBackScope(
        dismissProgress = dismissProgress,
        covered = covered,
        gestureState = gesture,
        settleState = settle,
        layerSize = layerSize,
        layoutDirectionValue = direction,
        densityValue = density,
    )
}
