package com.github.tvbox.osc.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

internal const val PLAYER_IN_MS = 120
internal const val PLAYER_OUT_MS = 120
internal const val PLAYER_HINT_IN_MS = 120
internal const val PLAYER_HINT_OUT_MS = 120
internal const val PLAYER_SIDE_IN_MS = 120
internal const val PLAYER_SIDE_OUT_MS = 120
internal const val PLAYER_OSD_IN_MS = 120
internal const val PLAYER_OSD_OUT_MS = 120

internal const val PLAYER_SLIDE_DP = 12
internal const val PLAYER_HINT_SLIDE_DP = 8
internal const val PLAYER_SIDE_SCALE = 0.88f

internal val PLAYER_ENTER_EASING = LinearOutSlowInEasing
internal val PLAYER_EXIT_EASING = FastOutLinearInEasing

@Composable
internal fun playerSlidePx(): Int = with(LocalDensity.current) { PLAYER_SLIDE_DP.dp.toPx().toInt() }

@Composable
internal fun playerHintSlidePx(): Int =
    with(LocalDensity.current) { PLAYER_HINT_SLIDE_DP.dp.toPx().toInt() }

internal fun playerEnterTransition(): EnterTransition =
    fadeIn(animationSpec = tween(PLAYER_IN_MS, easing = PLAYER_ENTER_EASING))

internal fun playerExitTransition(): ExitTransition =
    fadeOut(animationSpec = tween(PLAYER_OUT_MS, easing = PLAYER_EXIT_EASING))

internal fun playerEnterFromTop(slidePx: Int): EnterTransition =
    slideInVertically(
        animationSpec = tween(PLAYER_IN_MS, easing = PLAYER_ENTER_EASING),
        initialOffsetY = { -slidePx },
    ) + playerEnterTransition()

internal fun playerExitToTop(slidePx: Int): ExitTransition =
    slideOutVertically(
        animationSpec = tween(PLAYER_OUT_MS, easing = PLAYER_EXIT_EASING),
        targetOffsetY = { -slidePx },
    ) + playerExitTransition()

internal fun playerEnterFromBottom(slidePx: Int): EnterTransition =
    slideInVertically(
        animationSpec = tween(PLAYER_IN_MS, easing = PLAYER_ENTER_EASING),
        initialOffsetY = { slidePx },
    ) + playerEnterTransition()

internal fun playerExitToBottom(slidePx: Int): ExitTransition =
    slideOutVertically(
        animationSpec = tween(PLAYER_OUT_MS, easing = PLAYER_EXIT_EASING),
        targetOffsetY = { slidePx },
    ) + playerExitTransition()

internal fun playerHintEnter(slidePx: Int): EnterTransition =
    slideInVertically(
        animationSpec = tween(PLAYER_HINT_IN_MS, easing = PLAYER_ENTER_EASING),
        initialOffsetY = { -slidePx },
    ) + fadeIn(animationSpec = tween(PLAYER_HINT_IN_MS, easing = PLAYER_ENTER_EASING))

internal fun playerHintExit(): ExitTransition =
    fadeOut(animationSpec = tween(PLAYER_HINT_OUT_MS, easing = PLAYER_EXIT_EASING))

@Composable
internal fun playerSideAlpha(visible: Boolean): Float = animateFloatAsState(
    targetValue = if (visible) 1f else 0f,
    animationSpec = tween(
        durationMillis = if (visible) PLAYER_SIDE_IN_MS else PLAYER_SIDE_OUT_MS,
        easing = if (visible) PLAYER_ENTER_EASING else PLAYER_EXIT_EASING,
    ),
    label = "playerSideAlpha",
).value

@Composable
internal fun playerSideScale(visible: Boolean): Float = animateFloatAsState(
    targetValue = if (visible) 1f else PLAYER_SIDE_SCALE,
    animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
    label = "playerSideScale",
).value

internal fun Modifier.playerSideEffect(alpha: Float, scale: Float): Modifier = graphicsLayer {
    this.alpha = alpha
    scaleX = scale
    scaleY = scale
}

@Composable
internal fun PlayerHintVisibility(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = playerHintEnter(playerHintSlidePx()),
        exit = playerHintExit(),
    ) {
        content()
    }
}

@Composable
internal fun PlayerFadeVisibility(
    visible: Boolean,
    enterMs: Int,
    exitMs: Int,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(enterMs, easing = PLAYER_ENTER_EASING)),
        exit = fadeOut(animationSpec = tween(exitMs, easing = PLAYER_EXIT_EASING)),
    ) {
        content()
    }
}
