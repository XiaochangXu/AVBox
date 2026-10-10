package com.github.tvbox.osc.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.avbox.osc.ComposeTestActivity
import com.github.tvbox.osc.testing.TestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestApplication::class)
class PlayerPressHoldTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComposeTestActivity>()

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

    @Test
    fun tapOnPressEffectButtonFiresExactlyOnce() {
        var taps = 0
        rule.setContent {
            Box(
                Modifier
                    .size(80.dp)
                    .testTag("pressButton")
                    .playerPressEffect(onTap = { taps++ }),
            )
        }
        rule.onNodeWithTag("pressButton").performTouchInput {
            down(center)
            up()
        }
        rule.waitForIdle()
        assertEquals("单击必须只派发一次", 1, taps)
    }

    @Test
    fun longPressOnPressEffectButtonFiresLongPressOnly() {
        var taps = 0
        var longs = 0
        rule.setContent {
            Box(
                Modifier
                    .size(80.dp)
                    .testTag("pressButton")
                    .playerPressEffect(onTap = { taps++ }, onLongClick = { longs++ }),
            )
        }
        rule.onNodeWithTag("pressButton").performTouchInput {
            down(center)
        }
        rule.mainClock.advanceTimeBy(700)
        rule.waitForIdle()
        rule.onNodeWithTag("pressButton").performTouchInput {
            up()
        }
        rule.waitForIdle()
        assertEquals("长按只派发一次", 1, longs)
        assertEquals("长按不该再派发单击", 0, taps)
    }

    @Test
    fun holdOnPressEffectButtonDoesNotLeakToGestureLayer() {
        val recorder = Recorder()
        val handler = VideoGestureHandler(recorder)
        val parentSeen = mutableListOf<Boolean>()
        var longs = 0
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
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Final)
                                    parentSeen += event.changes.any { it.isConsumed }
                                }
                            }
                        },
                )
                Box(
                    Modifier
                        .size(80.dp)
                        .testTag("pressButton")
                        .playerPressEffect(onTap = {}, onLongClick = { longs++ }),
                )
            }
        }
        rule.onNodeWithTag("pressButton").performTouchInput {
            down(center)
        }
        rule.mainClock.advanceTimeBy(700)
        rule.waitForIdle()
        rule.onNodeWithTag("pressButton").performTouchInput {
            up()
        }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(800)
        rule.waitForIdle()
        assertEquals("长按抬起才算长按, 实际 $longs", 1, longs)
        assertTrue(
            "父层若收到事件则应看到消费标记, 实际: $parentSeen",
            parentSeen.isEmpty() || parentSeen.all { it },
        )
        assertTrue(
            "按住控件 700ms 不该把触摸漏给手势层, 实际派发: ${recorder.calls}",
            recorder.calls.none {
                it == "singleTap" || it == "doubleTap" || it == "longPressStart"
            },
        )
    }

    @Test
    fun holdOnPlainAreaStillReachesGestureLayer() {
        val recorder = Recorder()
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
            )
        }
        rule.onNodeWithTag("overlay").performTouchInput {
            down(center)
        }
        rule.mainClock.advanceTimeBy(700)
        rule.waitForIdle()
        rule.onNodeWithTag("overlay").performTouchInput {
            up()
        }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(800)
        rule.waitForIdle()
        assertTrue(
            "空白区按住应触发长按, 实际派发: ${recorder.calls}",
            recorder.calls.contains("longPressStart"),
        )
    }
}
