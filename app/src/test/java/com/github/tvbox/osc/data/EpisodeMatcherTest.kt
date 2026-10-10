package com.github.tvbox.osc.data

import com.github.tvbox.osc.bean.VodInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeMatcherTest {

    private fun series(name: String): VodInfo.VodSeries = VodInfo.VodSeries(name, "http://example/$name")

    private fun seriesList(vararg names: String): List<VodInfo.VodSeries> =
        names.map { series(it) }

    private fun vod(flagNames: List<String>, vararg mapFlags: String): VodInfo = VodInfo().apply {
        seriesFlags = ArrayList(flagNames.map { VodInfo.VodSeriesFlag(it) })
        seriesMap = LinkedHashMap<String?, MutableList<VodInfo.VodSeries>>().apply {
            for (flag in mapFlags) put(flag, seriesList("第1集").toMutableList())
        }
    }

    @Test
    fun episodeNumber_fromCommonNameForms() {
        assertEquals(12, EpisodeMatcher.extractEpisodeNumber("第12集"))
        assertEquals(12, EpisodeMatcher.extractEpisodeNumber("第 12 集"))
        assertEquals(12, EpisodeMatcher.extractEpisodeNumber("EP12"))
        assertEquals(12, EpisodeMatcher.extractEpisodeNumber("E12"))
        assertEquals(12, EpisodeMatcher.extractEpisodeNumber("12"))
    }

    @Test
    fun episodeNumber_ignoresYearBracketsAndQuality() {
        assertEquals(5, EpisodeMatcher.extractEpisodeNumber("[1080P] 第5集"))
        assertEquals(8, EpisodeMatcher.extractEpisodeNumber("影片名 (2024) 第08集"))
        assertEquals(1000, EpisodeMatcher.extractEpisodeNumber("名侦探柯南 2024 第1000集"))
        assertEquals(2, EpisodeMatcher.extractEpisodeNumber("S01E02"))
    }

    @Test
    fun episodeNumber_withoutNumber_isMinusOne() {
        assertEquals(-1, EpisodeMatcher.extractEpisodeNumber(null))
        assertEquals(-1, EpisodeMatcher.extractEpisodeNumber(""))
        assertEquals(-1, EpisodeMatcher.extractEpisodeNumber("抢先版"))
        assertEquals(-1, EpisodeMatcher.extractEpisodeNumber("【测试】1080P"))
    }

    @Test
    fun matchScore_exactNameWins() {
        assertEquals(100, EpisodeMatcher.episodeMatchScore("第3集", 3, "第3集"))
        assertEquals(100, EpisodeMatcher.episodeMatchScore("Final", -1, "final"))
    }

    @Test
    fun matchScore_sameEpisodeNumber() {
        assertEquals(80, EpisodeMatcher.episodeMatchScore("第3集", 3, "第3话"))
        assertEquals(0, EpisodeMatcher.episodeMatchScore("第3集", 3, "第5集"))
    }

    @Test
    fun matchScore_containsNameOnlyWhenNoEpisodeNumber() {
        assertEquals(70, EpisodeMatcher.episodeMatchScore("正片", -1, "正片 上"))
        assertEquals(60, EpisodeMatcher.episodeMatchScore("正片 上", -1, "正片"))
        assertEquals(0, EpisodeMatcher.episodeMatchScore("正", -1, "正片"))
        assertEquals(0, EpisodeMatcher.episodeMatchScore("正片", -1, "正"))
    }

    @Test
    fun matchScore_emptyInputs() {
        assertEquals(0, EpisodeMatcher.episodeMatchScore(null, -1, "x"))
        assertEquals(0, EpisodeMatcher.episodeMatchScore("x", -1, null))
        assertEquals(0, EpisodeMatcher.episodeMatchScore("", 3, ""))
    }

    @Test
    fun sameEpisodeIndex_matchesByEpisodeNumber() {
        val targets = seriesList("第1集", "第3集", "第5集")
        assertEquals(1, EpisodeMatcher.sameEpisodeIndex(series("第3集"), targets, 0))
    }

    @Test
    fun sameEpisodeIndex_tieKeepsFirstCandidate() {
        val targets = seriesList("第3话", "EP3")
        assertEquals(0, EpisodeMatcher.sameEpisodeIndex(series("第3集"), targets, 0))
    }

    @Test
    fun sameEpisodeIndex_exactNameBeatsEarlierEpisodeNumber() {
        assertEquals(1, EpisodeMatcher.sameEpisodeIndex(series("第3集"), seriesList("EP3", "第3集"), 0))
    }

    @Test
    fun sameEpisodeIndex_containsMatchWhenNoEpisodeNumber() {
        assertEquals(0, EpisodeMatcher.sameEpisodeIndex(series("正片"), seriesList("正片 上", "其他"), 1))
    }

    @Test
    fun sameEpisodeIndex_noMatch_fallsBackAndClamps() {
        val targets = seriesList("第1集", "第2集")
        assertEquals(1, EpisodeMatcher.sameEpisodeIndex(series("第99集"), targets, 5))
        assertEquals(0, EpisodeMatcher.sameEpisodeIndex(series("第99集"), targets, -3))
    }

    @Test
    fun sameEpisodeIndex_degenerateInputs() {
        val targets = seriesList("第1集", "第2集", "第3集")
        assertEquals(0, EpisodeMatcher.sameEpisodeIndex(series("第3集"), null, 0))
        assertEquals(0, EpisodeMatcher.sameEpisodeIndex(series("第3集"), seriesList(), 0))
        assertEquals(0, EpisodeMatcher.sameEpisodeIndex(series("第3集"), seriesList("第9集"), 0))
        assertEquals(1, EpisodeMatcher.sameEpisodeIndex(null, targets, 1))
        assertEquals(2, EpisodeMatcher.sameEpisodeIndex(VodInfo.VodSeries(), targets, 9))
    }

    @Test
    fun lineFlags_seriesFlagsFirstThenRemainingMapKeys() {
        val info = vod(
            listOf("线路A", "", "线路B", "线路A"),
            "线路B", "线路A", "线路C", "",
        )
        assertEquals(listOf("线路A", "线路B", "线路C"), EpisodeMatcher.lineFlagsInDisplayOrder(info))
    }

    @Test
    fun lineFlags_nullVodOrMap_isEmpty() {
        assertEquals(0, EpisodeMatcher.lineFlagsInDisplayOrder(null).size)
        assertEquals(0, EpisodeMatcher.lineFlagsInDisplayOrder(VodInfo()).size)
    }

    @Test
    fun lineFlagIndex_foundAndMissing() {
        val flags = listOf("线路A", "线路B")
        assertEquals(1, EpisodeMatcher.lineFlagIndex(flags, "线路B"))
        assertEquals(-1, EpisodeMatcher.lineFlagIndex(flags, "线路C"))
        assertEquals(-1, EpisodeMatcher.lineFlagIndex(null, "线路A"))
        assertEquals(-1, EpisodeMatcher.lineFlagIndex(flags, null))
        assertEquals(-1, EpisodeMatcher.lineFlagIndex(flags, ""))
    }
}
