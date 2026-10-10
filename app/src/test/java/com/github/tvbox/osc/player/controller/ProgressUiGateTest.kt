package com.github.tvbox.osc.player.controller

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressUiGateTest {

    @Test
    fun loadingPhase_rejectsEvenWhenUrlMatches() {
        assertFalse(ProgressUiGate.accept(ProgressPhase.LOADING, "http://a", "http://a"))
        assertFalse(ProgressUiGate.accept(ProgressPhase.LOADING, "http://a", "http://b"))
        assertFalse(ProgressUiGate.accept(ProgressPhase.LOADING, null, null))
    }

    @Test
    fun idlePhase_rejects() {
        assertFalse(ProgressUiGate.accept(ProgressPhase.IDLE, "http://a", "http://a"))
    }

    @Test
    fun activePhase_acceptsMatchingUrl() {
        assertTrue(ProgressUiGate.accept(ProgressPhase.ACTIVE, "http://a", "http://a"))
    }

    @Test
    fun activePhase_rejectsMismatchedUrl() {
        assertFalse(ProgressUiGate.accept(ProgressPhase.ACTIVE, "http://a", "http://b"))
        assertFalse(ProgressUiGate.accept(ProgressPhase.ACTIVE, "http://a", null))
    }

    @Test
    fun activePhase_rejectsMissingContentUrl() {
        assertFalse(ProgressUiGate.accept(ProgressPhase.ACTIVE, null, "http://a"))
        assertFalse(ProgressUiGate.accept(ProgressPhase.ACTIVE, "", "http://a"))
    }
}
