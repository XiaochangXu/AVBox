package com.github.tvbox.osc.player.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPressScaleTest {

    @Test
    fun startsUnpressedAtFullScale() {
        assertFalse(PlayerPressState().pressed)
    }

    @Test
    fun pressAndReleaseTogglesPressed() {
        val state = PlayerPressState()
        state.start()
        assertTrue(state.pressed)
        state.stop()
        assertFalse(state.pressed)
    }

    @Test
    fun repeatedStartStaysPressed() {
        val state = PlayerPressState()
        state.start()
        state.start()
        assertTrue(state.pressed)
    }

    @Test
    fun repeatedStopIsIdempotent() {
        val state = PlayerPressState()
        state.start()
        state.stop()
        state.stop()
        assertFalse(state.pressed)
    }

    @Test
    fun separateButtonsKeepIndependentState() {
        val first = PlayerPressState()
        val second = PlayerPressState()
        first.start()
        assertTrue(first.pressed)
        assertFalse(second.pressed)
    }

    @Test
    fun pressScaleIsInwardButNotCollapsing() {
        assertTrue(PLAYER_PRESS_SCALE < 1f)
        assertTrue(PLAYER_PRESS_SCALE > 0f)
        assertTrue(PLAYER_PRESS_SCALE in 0.85f..0.95f)
    }
}
