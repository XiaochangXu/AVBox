package com.github.tvbox.osc.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchHelperTest {

    private fun keys(vararg keys: String): Set<String> = keys.toSet()

    private fun checked(vararg keys: String): HashMap<String, String> {
        val map = HashMap<String, String>()
        for (key in keys) map[key] = "1"
        return map
    }

    @Test
    fun nullOrEmptySelection_isNeverStale() {
        assertFalse(SearchHelper.isSelectionStale(null, keys("a", "b")))
        assertFalse(SearchHelper.isSelectionStale(HashMap(), keys("a", "b")))
        assertFalse(SearchHelper.isSelectionStale(null, keys()))
    }

    @Test
    fun selectionMatchesCurrentSources_isNotStale() {
        assertFalse(SearchHelper.isSelectionStale(checked("a", "b"), keys("a", "b", "c")))
    }

    @Test
    fun selectionFromAnotherSourceSet_isStale() {
        assertTrue(SearchHelper.isSelectionStale(checked("old1", "old2"), keys("new1", "new2")))
    }

    @Test
    fun partiallyMatchingSelection_isStale() {
        assertTrue(SearchHelper.isSelectionStale(checked("shared", "oldOnly"), keys("shared", "newOnly")))
    }

    @Test
    fun selectionWithAllKeysUnknown_isStale() {
        assertTrue(SearchHelper.isSelectionStale(checked("x"), keys()))
    }
}
