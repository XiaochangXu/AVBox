package com.github.tvbox.osc.bean

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VodInfoReverseTest {

    @Test
    fun reverse_withoutSeriesIsNoop() {
        val info = VodInfo()
        info.reverse()
        assertNull(info.seriesMap)
    }

    @Test
    fun reverse_reversesEveryLineList() {
        val info = VodInfo()
        info.seriesMap = linkedMapOf(
            "线路1" to mutableListOf(
                VodInfo.VodSeries("1", "http://a/1"),
                VodInfo.VodSeries("2", "http://a/2"),
            ),
        )

        info.reverse()

        val list = info.seriesMap!!["线路1"]!!
        assertEquals("2", list[0].name)
        assertEquals("1", list[1].name)
    }
}
