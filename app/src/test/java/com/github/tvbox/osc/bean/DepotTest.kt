package com.github.tvbox.osc.bean

import com.google.gson.Gson
import com.google.gson.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DepotTest {

    private fun arr(text: String): JsonArray = Gson().fromJson(text, JsonArray::class.java)

    @Test
    fun arrayFrom_readsUrlNameOptional() {
        val items = Depot.arrayFrom(
            arr("[{\"name\":\"仓A\",\"url\":\"http://a/1\"},{\"url\":\"http://b/2\"}]"),
        )

        assertEquals(2, items.size)
        assertEquals("仓A", items[0].getName())
        assertEquals("http://a/1", items[0].getUrl())
        assertEquals("http://b/2", items[1].getName())
    }

    @Test
    fun arrayFrom_supportsApiFieldAndBareString() {
        val items = Depot.arrayFrom(
            arr("[{\"name\":\"x\",\"api\":\"http://d/4\"},\"http://c/3\"]"),
        )

        assertEquals(2, items.size)
        assertEquals("x", items[0].getName())
        assertEquals("http://d/4", items[0].getUrl())
        assertEquals("http://c/3", items[1].getUrl())
    }

    @Test
    fun arrayFrom_skipsEntriesWithoutUrl() {
        val items = Depot.arrayFrom(
            arr("[{\"name\":\"empty\",\"url\":\"\"},{\"name\":\"noUrl\"},{\"url\":\"http://ok/1\"}]"),
        )

        assertEquals(1, items.size)
        assertEquals("http://ok/1", items[0].getUrl())
    }

    @Test
    fun arrayFrom_toleratesGarbageEntries() {
        val items = Depot.arrayFrom(
            arr("[null,123,[\"http://nested\"],{\"url\":123},{\"url\":\"http://ok/1\"}]"),
        )

        assertEquals(1, items.size)
        assertEquals("http://ok/1", items[0].getUrl())
    }

    @Test
    fun arrayFrom_nonStringNameFallsBackToUrl() {
        val items = Depot.arrayFrom(arr("[{\"url\":\"http://a/1\",\"name\":123}]"))

        assertEquals(1, items.size)
        assertEquals("http://a/1", items[0].getName())
    }

    @Test
    fun arrayFrom_nullArrayIsEmptyNotCrash() {
        assertTrue(Depot.arrayFrom(null).isEmpty())
        assertTrue(Depot.arrayFrom(arr("[]")).isEmpty())
    }

    @Test
    fun getUrl_trimsWhitespace() {
        val items = Depot.arrayFrom(arr("[{\"name\":\" x \",\"url\":\"  http://a/1  \"}]"))

        assertEquals(1, items.size)
        assertEquals("http://a/1", items[0].getUrl())
        assertEquals("x", items[0].getName())
    }
}
