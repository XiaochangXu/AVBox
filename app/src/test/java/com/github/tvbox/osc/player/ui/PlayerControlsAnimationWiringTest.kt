package com.github.tvbox.osc.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.avbox.osc.ComposeTestActivity
import com.github.tvbox.osc.player.state.PlayerActions
import com.github.tvbox.osc.player.state.PlayerUiState
import com.github.tvbox.osc.testing.TestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestApplication::class)
class PlayerControlsAnimationWiringTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComposeTestActivity>()

    private class NoopActions : PlayerActions {
        var playPause = 0
        override fun toggleControls() = Unit
        override fun keepControlsAlive() = Unit
        override fun onNextClicked() = Unit
        override fun onPreClicked() = Unit
        override fun onPlayPauseClicked() { playPause++ }
        override fun onScaleClicked() = Unit
        override fun onScaleLongClicked() = Unit
        override fun onSpeedClicked() = Unit
        override fun onSpeedLongClicked() = Unit
        override fun onPlayerClicked() = Unit
        override fun onPlayerLongClicked() = Unit
        override fun onTimeStartClicked() = Unit
        override fun onTimeStartLongClicked() = Unit
        override fun onTimeEndClicked() = Unit
        override fun onTimeEndLongClicked() = Unit
        override fun onTimeResetClicked() = Unit
        override fun onEpisodeClicked() = Unit
        override fun onCastClicked() = Unit
        override fun onSubtitleClicked() = Unit
        override fun onSubtitleLongClicked() = Unit
        override fun onAudioTrackClicked() = Unit
        override fun onVideoTrackClicked() = Unit
        override fun onDanmuSettingClicked() = Unit
        override fun onDanmuSettingLongClicked() = Unit
        override fun onDanmuSearchClicked() = Unit
        override fun onDanmuSearchLongClicked() = Unit
        override fun onRotateClicked() = Unit
        override fun onParamsClicked() = Unit
        override fun onInfoOsdClicked() = Unit
        override fun onBackClicked() = Unit
        override fun onLockClicked() = Unit
        override fun onParseSelected(position: Int) = Unit
        override fun onSeekStarted() = Unit
        override fun onSeekPreview(progress: Int) = Unit
        override fun onSeekFinished(progress: Int) = Unit
        override fun onSeekCancelled() = Unit
        override fun onSeekStep(dir: Int) = Unit
        override fun onSeekRelative(deltaMs: Long) = Unit
        override fun refreshSystemInfo() = Unit
        override fun hideSeekHint() = Unit
        override fun hideSlideHint() = Unit
    }

    private class Recorder : VideoGestureActions {
        val calls = mutableListOf<String>()
        override fun inPlayback(): Boolean = true
        override fun onSingleTap() { calls += "singleTap" }
        override fun onDoubleTapTogglePlay() { calls += "doubleTap" }
        override fun onLongPressStart() { calls += "longPressStart" }
        override fun onLongPressEnd() { calls += "longPressEnd" }
        override fun onSeekPreview(totalDeltaX: Float) { calls += "seekPreview" }
        override fun onSeekCommit() { calls += "seekCommit" }
        override fun onSeekCancel() { calls += "seekCancel" }
        override fun onBrightnessSlide(totalDeltaY: Float) { calls += "brightness" }
        override fun onVolumeSlide(totalDeltaY: Float) { calls += "volume" }
    }

    private fun alwaysClaim(): (IntSize, Float) -> VideoGestureSession = { _, _ ->
        VideoGestureSession(
            inPlayback = true,
            canChangePosition = true,
            enableInNormal = true,
            fullScreen = true,
            locked = false,
            previewMode = false,
            paused = false,
            gestureEnabled = true,
            verticalSlidingDisabled = false,
            width = 1000,
            height = 600,
            screenWidth = 1000,
            edge = false,
        )
    }

    private fun composeBottomOverlay(
        state: PlayerUiState,
        actions: PlayerActions,
        recorder: Recorder,
    ) {
        val handler = VideoGestureHandler(recorder)
        rule.setContent {
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag("overlay")
                    .videoGestureLayer(
                        handler = handler,
                        sessionProvider = alwaysClaim(),
                        onTapPending = { handler.markSingleTapConfirmed() },
                    ),
            ) {
                AnimatedVisibility(
                    visible = state.controlsVisible,
                    enter = playerEnterFromBottom(0),
                    exit = playerExitToBottom(0),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    Box(Modifier.padding(bottom = 8.dp)) {
                        PlayerBottomBar(state, actions)
                    }
                }
            }
        }
    }

    private fun settle() {
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
    }

    private fun tapBottomBand() {
        rule.onNodeWithTag("overlay").performTouchInput {
            down(Offset(centerX, bottom - 40f))
            up()
        }
        settle()
    }

    @Test
    fun hiddenControlsKeepTheBottomBandClickable() {
        val state = PlayerUiState()
        val recorder = Recorder()
        state.controlsVisible = false
        composeBottomOverlay(state, NoopActions(), recorder)
        tapBottomBand()
        assertTrue(
            "控件收起时底部区域必须仍能唤出控件, 实际派发: ${recorder.calls}",
            recorder.calls.contains("singleTap"),
        )
    }

    @Test
    fun shownControlsDoNotLeakTheBottomBandToTheGestureLayer() {
        val state = PlayerUiState()
        val recorder = Recorder()
        state.controlsVisible = true
        composeBottomOverlay(state, NoopActions(), recorder)
        settle()
        recorder.calls.clear()
        tapBottomBand()
        assertTrue(
            "控件在屏时不该再派发单击唤出, 实际派发: ${recorder.calls}",
            recorder.calls.none { it == "singleTap" },
        )
    }

    @Test
    fun collapsingControlsRemovesTheBarFromTheTree() {
        val state = PlayerUiState()
        state.controlsVisible = true
        composeBottomOverlay(state, NoopActions(), Recorder())
        settle()
        rule.onNodeWithTag(TRANSPORT_PLAY_PAUSE_TAG).assertExists()
        state.controlsVisible = false
        settle()
        rule.onNodeWithTag(TRANSPORT_PLAY_PAUSE_TAG).assertDoesNotExist()
    }

    @Test
    fun transportButtonStillFiresItsAction() {
        val state = PlayerUiState()
        val actions = NoopActions()
        state.controlsVisible = true
        composeBottomOverlay(state, actions, Recorder())
        settle()
        rule.onNodeWithTag(TRANSPORT_PLAY_PAUSE_TAG).performClick()
        rule.waitForIdle()
        assertEquals(1, actions.playPause)
    }

    @Test
    fun topBarContentSitsAtTheTopEdgeNotBelowTheScrim() {
        val state = PlayerUiState()
        state.topLeftVisible = true
        state.topRightVisible = true
        state.title = "兰香如故 1"
        state.videoSize = "1920 X 1080"
        state.videoQuality = "1080P"
        rule.setContent {
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag("overlay"),
            ) {
                PlayerTopBar(state, NoopActions())
            }
        }
        settle()
        val screenHeight = rule.onNodeWithTag("overlay").fetchSemanticsNode().size.height
        val backBounds = rule.onNodeWithTag(TOP_BAR_BACK_TAG).fetchSemanticsNode().boundsInRoot
        val backCenter = backBounds.top + backBounds.height / 2f
        assertTrue(
            "返回键必须贴顶, 实际中心=$backCenter / 屏高=$screenHeight",
            backCenter <= screenHeight * 0.15f,
        )
    }

    @Test
    fun topBarStaysAnchoredToTheTopEdge() {
        val state = PlayerUiState()
        state.topLeftVisible = true
        state.topRightVisible = true
        rule.setContent {
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag("overlay"),
            ) {
                PlayerTopBar(state, NoopActions())
            }
        }
        settle()
        val screenHeight = rule.onNodeWithTag("overlay").fetchSemanticsNode().size.height
        val barBounds = rule.onNodeWithTag(TOP_BAR_ROOT_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("顶栏必须锚在顶边, 实际 top=${barBounds.top}", barBounds.top <= screenHeight * 0.1f)
        assertTrue(
            "顶栏不该长到占满屏幕, 实际 bottom=${barBounds.bottom} / screen=$screenHeight",
            barBounds.bottom <= screenHeight * 0.6f,
        )
    }
}
