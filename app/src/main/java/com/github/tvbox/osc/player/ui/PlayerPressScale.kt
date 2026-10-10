package com.github.tvbox.osc.player.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

internal class PlayerPressState {

    var pressed by mutableStateOf(false)
        private set

    fun start() {
        pressed = true
    }

    fun stop() {
        pressed = false
    }
}

internal const val PLAYER_PRESS_SCALE = 0.9f

private const val PLAYER_PRESS_DAMPING = 0.75f

@Composable
internal fun Modifier.playerPressEffect(
    onTap: () -> Unit,
    onLongClick: (() -> Unit)? = null,
): Modifier {
    val state = remember { PlayerPressState() }
    val scale = animateFloatAsState(
        targetValue = if (state.pressed) PLAYER_PRESS_SCALE else 1f,
        animationSpec = spring(
            dampingRatio = PLAYER_PRESS_DAMPING,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "playerPressScale",
    ).value
    val tap by rememberUpdatedState(onTap)
    val longClick by rememberUpdatedState(onLongClick)
    val hasLongClick = onLongClick != null
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(hasLongClick) {
            detectTapGestures(
                onPress = {
                    state.start()
                    tryAwaitRelease()
                    state.stop()
                },
                onTap = { tap() },
                onLongPress = if (hasLongClick) {
                    { longClick?.invoke() }
                } else {
                    null
                },
            )
        }
}
