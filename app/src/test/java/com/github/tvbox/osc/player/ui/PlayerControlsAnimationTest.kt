package com.github.tvbox.osc.player.ui

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerControlsAnimationTest {

    @Test
    fun everyChromeAnimationRunsForOneHundredTwentyMillis() {
        assertEquals(120, PLAYER_IN_MS)
        assertEquals(120, PLAYER_OUT_MS)
        assertEquals(120, PLAYER_HINT_IN_MS)
        assertEquals(120, PLAYER_HINT_OUT_MS)
        assertEquals(120, PLAYER_SIDE_IN_MS)
        assertEquals(120, PLAYER_SIDE_OUT_MS)
        assertEquals(120, PLAYER_OSD_IN_MS)
        assertEquals(120, PLAYER_OSD_OUT_MS)
    }

    @Test
    fun hintAnimationIsFarShorterThanItsOneSecondLifetime() {
        assertTrue(PLAYER_HINT_IN_MS + PLAYER_HINT_OUT_MS <= 1000 / 3)
    }

    @Test
    fun everyTimingConstantIsUnified() {
        assertEquals(PLAYER_IN_MS, PLAYER_HINT_IN_MS)
        assertEquals(PLAYER_OUT_MS, PLAYER_HINT_OUT_MS)
    }

    @Test
    fun sideButtonsMatchTheBarTiming() {
        assertEquals(PLAYER_IN_MS, PLAYER_SIDE_IN_MS)
        assertEquals(PLAYER_OUT_MS, PLAYER_SIDE_OUT_MS)
    }

    @Test
    fun osdMatchesTheBarTiming() {
        assertEquals(PLAYER_IN_MS, PLAYER_OSD_IN_MS)
        assertEquals(PLAYER_OUT_MS, PLAYER_OSD_OUT_MS)
    }

    @Test
    fun enterUsesDecelerateAndExitUsesAccelerate() {
        assertSame(LinearOutSlowInEasing, PLAYER_ENTER_EASING)
        assertSame(FastOutLinearInEasing, PLAYER_EXIT_EASING)
    }

    @Test
    fun slideStaysInsideTheLocalMotionBudget() {
        assertTrue(PLAYER_SLIDE_DP in 1..16)
        assertTrue(PLAYER_HINT_SLIDE_DP in 1..PLAYER_SLIDE_DP)
    }

    @Test
    fun pressAndSideScalesStayInsideTheProjectRange() {
        assertTrue(PLAYER_SIDE_SCALE in 0.85f..0.95f)
        assertTrue(PLAYER_PRESS_SCALE in 0.85f..0.95f)
        assertEquals(PLAYER_PRESS_SCALE, PLAYER_SIDE_SCALE, 0.05f)
    }

    @Test
    fun reentryBudgetStaysUnderHalfASecond() {
        assertTrue(PLAYER_IN_MS + PLAYER_OUT_MS <= 500)
    }
}
