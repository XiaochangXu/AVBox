package com.github.tvbox.osc.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.avbox.osc.ComposeTestActivity
import com.github.tvbox.osc.player.state.PlayerActions
import com.github.tvbox.osc.player.state.PlayerUiState
import com.github.tvbox.osc.testing.TestApplication
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private class NoopTopBarActions : PlayerActions {
    override fun toggleControls() = Unit
    override fun keepControlsAlive() = Unit
    override fun onNextClicked() = Unit
    override fun onPreClicked() = Unit
    override fun onPlayPauseClicked() = Unit
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

private fun stateWithTopBar(): PlayerUiState = PlayerUiState().apply {
    topLeftVisible = true
    topRightVisible = true
    title = "兰香如故 1"
    videoSize = "1920 X 1080"
    videoQuality = "1080P"
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestApplication::class, qualifiers = "w2800dp-h1260dp-xxhdpi")
class PlayerTopBarDeviceLayoutTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun topBarContentAnchorsToTheTopOnDeviceGeometry() {
        val state = stateWithTopBar()
        rule.setContent {
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag("root"),
            ) {
                PlayerTopBar(state, NoopTopBarActions())
            }
        }
        rule.mainClock.advanceTimeBy(2000)
        rule.waitForIdle()
        val screen = rule.onNodeWithTag("root").fetchSemanticsNode().size
        val back = rule.onNodeWithTag(TOP_BAR_BACK_TAG).fetchSemanticsNode().boundsInRoot
        val backCenter = back.top + back.height / 2f
        assertTrue(
            "返回键必须贴顶, 实际中心=$backCenter 屏高=${screen.height}",
            backCenter <= screen.height * 0.15f,
        )
    }
}
