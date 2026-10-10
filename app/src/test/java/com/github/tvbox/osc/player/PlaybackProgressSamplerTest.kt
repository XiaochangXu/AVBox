package com.github.tvbox.osc.player

import com.github.tvbox.osc.player.state.PlayState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackProgressSamplerTest {

    private fun keepsTicking(state: PlayState, live: Boolean = false): Boolean =
        ProgressSampling.keepsTicking(state, live)

    private fun shouldWrite(
        state: PlayState = PlayState.PLAYING,
        playing: Boolean = true,
        live: Boolean = false,
        sameContent: Boolean = true,
    ): Boolean = ProgressSampling.shouldWrite(state, playing, live, sameContent)

    @Test
    fun keepsTicking_onlyWhilePlaybackRuns() {
        assertTrue(keepsTicking(PlayState.PLAYING))
        assertTrue(keepsTicking(PlayState.BUFFERING))
        assertTrue(keepsTicking(PlayState.BUFFERED))
        assertFalse(keepsTicking(PlayState.PAUSED))
        assertFalse(keepsTicking(PlayState.IDLE))
        assertFalse(keepsTicking(PlayState.COMPLETED))
        assertFalse(keepsTicking(PlayState.PREPARING))
        assertFalse(keepsTicking(PlayState.PREPARED))
        assertFalse(keepsTicking(PlayState.ERROR))
        assertFalse(keepsTicking(PlayState.START_ABORT))
    }

    @Test
    fun keepsTicking_neverInLiveMode() {
        assertFalse(keepsTicking(PlayState.PLAYING, live = true))
        assertFalse(keepsTicking(PlayState.BUFFERING, live = true))
        assertFalse(keepsTicking(PlayState.BUFFERED, live = true))
    }

    @Test
    fun shouldWrite_requiresPlayingContent() {
        assertTrue(shouldWrite())
        assertFalse(shouldWrite(playing = false))
        assertFalse(shouldWrite(state = PlayState.PAUSED))
        assertFalse(shouldWrite(state = PlayState.BUFFERED))
        assertFalse(shouldWrite(state = PlayState.PREPARING))
        assertFalse(shouldWrite(state = PlayState.IDLE))
    }

    @Test
    fun shouldWrite_blockedForStaleOrLiveContent() {
        assertFalse(shouldWrite(sameContent = false))
        assertFalse(shouldWrite(live = true))
    }

    @Test
    fun switchInFlight_onlyWhenBothKeysKnownAndDifferent() {
        assertFalse(ProgressSampling.switchInFlight(null, "src|1|线路1|0"))
        assertFalse(ProgressSampling.switchInFlight("src|1|线路1|0", "src|1|线路1|0"))
        assertFalse(ProgressSampling.switchInFlight("src|1|线路1|0", null))
        assertTrue(ProgressSampling.switchInFlight("src|1|线路1|0", "src|1|线路1|1"))
        assertTrue(ProgressSampling.switchInFlight("src|1|线路1|0", "src|2|线路1|0"))
    }

    @Test
    fun sameContentRestart_onlyWhenBothKeysKnownAndEqual() {
        assertFalse(ProgressSampling.sameContentRestart(null, "src|1|线路1|0"))
        assertFalse(ProgressSampling.sameContentRestart("src|1|线路1|0", null))
        assertFalse(ProgressSampling.sameContentRestart("", "src|1|线路1|0"))
        assertFalse(ProgressSampling.sameContentRestart("src|1|线路1|0", ""))
        assertFalse(ProgressSampling.sameContentRestart("src|1|线路1|0", "src|1|线路1|1"))
        assertFalse(ProgressSampling.sameContentRestart("src|1|线路1|0", "src|1|线路2|0"))
        assertTrue(ProgressSampling.sameContentRestart("src|1|线路1|0", "src|1|线路1|0"))
    }
}
