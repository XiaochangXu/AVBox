package com.github.tvbox.osc.data

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackProgressTest {

    @Test
    fun stepAdvance_countsSmoothPlayback() {
        assertEquals(1_000, PlaybackProgress.stepAdvanceMs(1_000, 0))
        assertEquals(2_000, PlaybackProgress.stepAdvanceMs(3_000, 1_000))
    }

    @Test
    fun stepAdvance_ignoresStalledPosition() {
        assertEquals(0, PlaybackProgress.stepAdvanceMs(90_000, 90_000))
    }

    @Test
    fun stepAdvance_ignoresJumpAndRewind() {
        assertEquals(0, PlaybackProgress.stepAdvanceMs(90_000, 0))
        assertEquals(0, PlaybackProgress.stepAdvanceMs(0, 90_000))
    }

    @Test
    fun shouldMarkWatched_needsAccumulatedAdvance() {
        assertFalse(PlaybackProgress.shouldMarkWatched(999, "src|1#线路A#0", ""))
        assertTrue(PlaybackProgress.shouldMarkWatched(1_000, "src|1#线路A#0", ""))
    }

    @Test
    fun shouldMarkWatched_sendsOncePerEpisode() {
        assertFalse(PlaybackProgress.shouldMarkWatched(5_000, "src|1#线路A#0", "src|1#线路A#0"))
        assertTrue(PlaybackProgress.shouldMarkWatched(5_000, "src|1#线路A#1", "src|1#线路A#0"))
    }

    @Test
    fun decodePercent_readsLegacyPlainInt() {
        assertEquals(42, PlaybackProgress.decodePercent("42"))
        assertEquals(0, PlaybackProgress.decodePercent("0"))
    }

    @Test
    fun decodePercent_readsEntryPayload() {
        val raw = PlaybackProgress.encodeEntry(42, 1_200_000, 1_700_000_000_000L)
        assertEquals(42, PlaybackProgress.decodePercent(raw))
    }

    @Test
    fun encodeEntry_keepsDurationOnlyWhenKnown() {
        val withDuration = JsonParser.parseString(PlaybackProgress.encodeEntry(30, 90_000, 7L)).asJsonObject
        assertEquals(30, withDuration.get("p").asInt)
        assertEquals(90_000, withDuration.get("d").asInt)
        assertEquals(7L, withDuration.get("t").asLong)
        val withoutDuration = JsonParser.parseString(PlaybackProgress.encodeEntry(30, 0, 7L)).asJsonObject
        assertFalse(withoutDuration.has("d"))
    }

    @Test
    fun decodePercent_ignoresGarbage() {
        assertNull(PlaybackProgress.decodePercent(null))
        assertNull(PlaybackProgress.decodePercent(""))
        assertNull(PlaybackProgress.decodePercent("{}"))
        assertNull(PlaybackProgress.decodePercent("not-json"))
    }

    @Test
    fun decodeSavedAt_readsTimestampAndFallsBackForLegacy() {
        val raw = PlaybackProgress.encodeEntry(42, 90_000, 1_700_000_000_000L)
        assertEquals(1_700_000_000_000L, PlaybackProgress.decodeSavedAt(raw))
        assertEquals(0L, PlaybackProgress.decodeSavedAt("42"))
        assertEquals(0L, PlaybackProgress.decodeSavedAt("not-json"))
        assertEquals(0L, PlaybackProgress.decodeSavedAt(null))
    }

    @Test
    fun pickEvictions_dropsOldestFirstAndLegacyFirst() {
        val entries = mapOf(
            "old" to PlaybackProgress.encodeEntry(10, 1_000, 100L),
            "new" to PlaybackProgress.encodeEntry(10, 1_000, 300L),
            "mid" to PlaybackProgress.encodeEntry(10, 1_000, 200L),
            "legacy" to "55",
        )
        assertEquals(emptyList<String>(), PlaybackProgress.pickEvictions(entries, 4))
        assertEquals(listOf("legacy", "old"), PlaybackProgress.pickEvictions(entries, 2))
    }
}
