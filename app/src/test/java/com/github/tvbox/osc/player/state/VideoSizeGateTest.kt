package com.github.tvbox.osc.player.state

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoSizeGateTest {

    @Test
    fun noSession_kernelValueIsShown() {
        val gate = VideoSizeGate()
        assertEquals("1920 X 1080", gate.textFor(1920, 1080))
    }

    @Test
    fun newSession_showsPlaceholderWhileKernelStillHoldsPreviousSize() {
        val gate = VideoSizeGate()
        assertEquals(VideoSizeGate.UNKNOWN, gate.onNewSession(1920, 1080))
        assertEquals(VideoSizeGate.UNKNOWN, gate.textFor(1920, 1080))
        assertEquals(VideoSizeGate.UNKNOWN, gate.textFor(1920, 1080))
    }

    @Test
    fun newSession_kernelResetShowsPlaceholder() {
        val gate = VideoSizeGate()
        gate.onNewSession(1920, 1080)
        gate.onKernelContentReplaced()
        assertEquals(VideoSizeGate.UNKNOWN, gate.textFor(0, 0))
        assertEquals("1280 X 544", gate.textFor(1280, 544))
        assertEquals("1280 X 544", gate.textFor(1280, 544))
    }

    @Test
    fun newSession_reportBeforeNextPollIsShownAtOnce() {
        val gate = VideoSizeGate()
        gate.onNewSession(1920, 1080)
        gate.onKernelContentReplaced()
        assertEquals("1280 X 544", gate.textFor(1280, 544))
    }

    @Test
    fun newSession_sameSizeAsPreviousNeedsContentReplaced() {
        val gate = VideoSizeGate()
        gate.onNewSession(1920, 1080)
        assertEquals(VideoSizeGate.UNKNOWN, gate.textFor(1920, 1080))
    }

    @Test
    fun contentReplaced_sameSizeAsPreviousIsShown() {
        val gate = VideoSizeGate()
        gate.onNewSession(1920, 1080)
        gate.onKernelContentReplaced()
        assertEquals("1920 X 1080", gate.textFor(1920, 1080))
    }

    @Test
    fun newSession_unknownKernelSnapshotTrustsNextReport() {
        val gate = VideoSizeGate()
        assertEquals(VideoSizeGate.UNKNOWN, gate.onNewSession(0, 0))
        assertEquals(VideoSizeGate.UNKNOWN, gate.textFor(0, 0))
        assertEquals("1920 X 1080", gate.textFor(1920, 1080))
    }

    @Test
    fun sessionEnded_laterSizesAreShownDirectly() {
        val gate = VideoSizeGate()
        gate.onNewSession(1920, 1080)
        gate.onKernelContentReplaced()
        gate.textFor(1280, 544)
        assertEquals("1920 X 1080", gate.textFor(1920, 1080))
        assertEquals(VideoSizeGate.UNKNOWN, gate.textFor(0, 0))
    }

    @Test
    fun qualityTag_mapsShortSideToLabel() {
        assertEquals("2160P", VideoSizeGate.qualityTag(3840, 2160))
        assertEquals("1440P", VideoSizeGate.qualityTag(2560, 1440))
        assertEquals("1080P", VideoSizeGate.qualityTag(1920, 1080))
        assertEquals("720P", VideoSizeGate.qualityTag(1280, 720))
        assertEquals("480P", VideoSizeGate.qualityTag(1080, 606))
        assertEquals("360P", VideoSizeGate.qualityTag(640, 360))
        assertEquals("240P", VideoSizeGate.qualityTag(426, 240))
        assertEquals("144P", VideoSizeGate.qualityTag(256, 144))
    }

    @Test
    fun qualityTag_portraitUsesShortSideToo() {
        assertEquals("1080P", VideoSizeGate.qualityTag(1080, 1920))
    }

    @Test
    fun qualityFor_followsSameVisibilityRulesAsTextFor() {
        val gate = VideoSizeGate()
        assertEquals("1080P", gate.qualityFor(1920, 1080))
        assertEquals("", gate.qualityFor(0, 0))
        gate.onNewSession(1920, 1080)
        assertEquals("", gate.qualityFor(1920, 1080))
        gate.onKernelContentReplaced()
        assertEquals("1080P", gate.qualityFor(1920, 1080))
    }
}
