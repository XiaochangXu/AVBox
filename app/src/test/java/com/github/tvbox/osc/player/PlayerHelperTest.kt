package com.github.tvbox.osc.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerHelperTest {

    @Test
    fun displaySpeed_usesByteUnitsWithBinaryBase() {
        assertEquals("512B/s", PlayerHelper.getDisplaySpeed(512L, true))
        assertEquals("900KB/s", PlayerHelper.getDisplaySpeed(900L * 1024, true))
        assertTrue(PlayerHelper.getDisplaySpeed(3L * 1024 * 1024, true).endsWith("MB/s"))
    }

    @Test
    fun displaySpeed_zeroTextFollowsShowFlag() {
        assertEquals("0B/s", PlayerHelper.getDisplaySpeed(0L, true))
        assertEquals("", PlayerHelper.getDisplaySpeed(0L, false))
    }

    @Test
    fun exoDecodeApplied_onlySoftValueMatchesAppliedFlag() {
        assertTrue(PlayerHelper.isExoDecodeApplied("软解码", true))
        assertTrue(PlayerHelper.isExoDecodeApplied("硬解码", false))
        assertTrue(PlayerHelper.isExoDecodeApplied("", false))
        assertTrue(PlayerHelper.isExoDecodeApplied(null, false))
        assertFalse(PlayerHelper.isExoDecodeApplied("软解码", false))
        assertFalse(PlayerHelper.isExoDecodeApplied("硬解码", true))
        assertFalse(PlayerHelper.isExoDecodeApplied(null, true))
    }

    @Test
    fun decodeKindOf_missingNameIsUnknown() {
        assertEquals(PlayerDecodeKind.UNKNOWN, PlayerHelper.decodeKindOf(null, true, false))
        assertEquals(PlayerDecodeKind.UNKNOWN, PlayerHelper.decodeKindOf("", true, false))
    }

    @Test
    fun decodeKindOf_softwareNameWinsOverHardwareFlag() {
        assertEquals(PlayerDecodeKind.SOFTWARE, PlayerHelper.decodeKindOf("c2.android.aac.decoder", true, false))
        assertEquals(PlayerDecodeKind.SOFTWARE, PlayerHelper.decodeKindOf("OMX.google.aac.decoder", false, false))
        assertEquals(PlayerDecodeKind.SOFTWARE, PlayerHelper.decodeKindOf("OMX.ffmpeg.aac.decoder", false, false))
        assertEquals(PlayerDecodeKind.SOFTWARE, PlayerHelper.decodeKindOf("c2.qti.aac.decoder.sw", true, false))
    }

    @Test
    fun decodeKindOf_softwareOnlyFlagWinsOverHardwareFlag() {
        assertEquals(PlayerDecodeKind.SOFTWARE, PlayerHelper.decodeKindOf("c2.qti.aac.decoder", true, true))
    }

    @Test
    fun decodeKindOf_vendorHardwareDecoderIsHardware() {
        assertEquals(PlayerDecodeKind.HARDWARE, PlayerHelper.decodeKindOf("c2.qti.aac.decoder", true, false))
        assertEquals(PlayerDecodeKind.HARDWARE, PlayerHelper.decodeKindOf("OMX.qcom.audio.decoder.aac", true, false))
    }

    @Test
    fun decodeKindOf_vendorNameWithoutHardwareFlagIsUnknown() {
        assertEquals(PlayerDecodeKind.UNKNOWN, PlayerHelper.decodeKindOf("OMX.qcom.audio.decoder.aac", false, false))
    }
}
