package com.github.tvbox.osc.sourcedata

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleFilePickerTest {

    companion object {
        private const val EP1 = "Show.S01E01.1080p.ass"
        private const val EP2 = "Show.S01E02.1080p.ass"
        private const val EP3 = "Show.S01E03.1080p.ass"
    }

    @Test
    fun picksSameFileWhenUserPickedItBefore() {
        assertEquals(1, SubtitleFilePicker.pick(listOf(EP1, EP2, EP3), "第2集", EP2))
    }

    @Test
    fun picksByEpisodeNumberAcrossEpisodes() {
        assertEquals(2, SubtitleFilePicker.pick(listOf(EP1, EP2, EP3), "第3集", EP1))
        assertEquals(0, SubtitleFilePicker.pick(listOf(EP1, EP2, EP3), "第1集", EP3))
    }

    @Test
    fun episodeNumberFormatsAreTolerated() {
        val files = listOf("Show.S01E01.ass", "Show.S01E02.ass")
        assertEquals(1, SubtitleFilePicker.pick(files, "02", "Show.S01E01.ass"))
        assertEquals(1, SubtitleFilePicker.pick(files, "第2集", ""))
        assertEquals(1, SubtitleFilePicker.pick(files, "EP02", ""))
    }

    @Test
    fun sameEpisodeMultipleVariantsPicksMatchingVariant() {
        val files = listOf("Show.S01E02.chs.ass", "Show.S01E02.cht.ass")
        assertEquals(0, SubtitleFilePicker.pick(files, "第2集", "Show.S01E01.chs.ass"))
        assertEquals(1, SubtitleFilePicker.pick(files, "第2集", "Show.S01E01.cht.ass"))
    }

    @Test
    fun sameEpisodeSameVariantReturnsMiss() {
        val files = listOf("Show.S01E02.1080p.ass", "Show.S01E02.720p.ass")
        assertEquals(-1, SubtitleFilePicker.pick(files, "第2集", "Show.S01E01.1080p.ass"))
    }

    @Test
    fun sameEpisodeDuplicatedNameReturnsMiss() {
        val files = listOf("Show.E02.ass", "Show.E02.ass")
        assertEquals(-1, SubtitleFilePicker.pick(files, "第2集", ""))
    }

    @Test
    fun singleFileReleaseIsWholeSeason() {
        val files = listOf("Show.S01.全集.ass")
        assertEquals(0, SubtitleFilePicker.pick(files, "正片", ""))
    }

    @Test
    fun singleFileWithOtherEpisodeNumberReturnsMiss() {
        assertEquals(-1, SubtitleFilePicker.pick(listOf(EP1), "第3集", ""))
    }

    @Test
    fun noEpisodeNumberInSeriesNameReturnsMiss() {
        assertEquals(-1, SubtitleFilePicker.pick(listOf(EP1, EP2), "正片", ""))
    }

    @Test
    fun emptyInputsReturnMiss() {
        assertEquals(-1, SubtitleFilePicker.pick(emptyList<String>(), "第1集", EP1))
        assertEquals(-1, SubtitleFilePicker.pick(null, "第1集", EP1))
    }

    @Test
    fun extensionDifferenceIsTolerated() {
        assertEquals(0, SubtitleFilePicker.pick(listOf("Show.S01E01.ass"), "正片", "Show.S01E01.srt"))
        assertEquals(-1, SubtitleFilePicker.pick(listOf("Show.S01E01.ass", "Show.S01E02.ass"), "正片", "Other.S01E01.srt"))
    }
}
