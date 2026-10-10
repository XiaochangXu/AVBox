package com.github.tvbox.osc.quickjs

import com.whl.quickjs.wrapper.UriUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UriUtilTest {

    @Test
    fun resolve_absoluteReferenceReplacesBase() {
        assertEquals(
            "http://other/b.js",
            UriUtil.resolve("http://host/app/x.js", "http://other/b.js"),
        )
    }

    @Test
    fun resolve_sameDirectoryRelativeReference() {
        assertEquals("http://host/app/z.js", UriUtil.resolve("http://host/app/x.js", "z.js"))
        assertEquals("http://host/app/z.js", UriUtil.resolve("http://host/app/x.js", "./z.js"))
    }

    @Test
    fun resolve_parentDirectoryReference() {
        assertEquals("http://host/z.js", UriUtil.resolve("http://host/app/x.js", "../z.js"))
        assertEquals("http://host/z.js", UriUtil.resolve("http://host/app/sub/x.js", "../../z.js"))
    }

    @Test
    fun resolve_rootedPathReference() {
        assertEquals("http://host/z.js", UriUtil.resolve("http://host/app/deep/x.js", "/z.js"))
    }

    @Test
    fun resolve_keepsQueryAndAppendsFragment() {
        assertEquals("http://host/app/x.js?v=1", UriUtil.resolve("http://host/app/x.js?v=1", ""))
        assertEquals("http://host/app/x.js#f", UriUtil.resolve("http://host/app/x.js", "#f"))
        assertEquals("http://host/app/x.js?q=1", UriUtil.resolve("http://host/app/x.js?old=1", "?q=1"))
    }

    @Test
    fun resolve_networkPathReferenceKeepsBaseScheme() {
        assertEquals("http://other/b.js", UriUtil.resolve("http://host/app/x.js", "//other/b.js"))
    }

    @Test
    fun resolve_relativeBaseWithoutScheme() {
        assertEquals("app/z.js", UriUtil.resolve("app/x.js", "z.js"))
        assertEquals("z.js", UriUtil.resolve("app/x.js", "../z.js"))
    }

    @Test
    fun resolve_dotSegments_insideAbsoluteReference() {
        assertEquals("http://host/a/c.js", UriUtil.resolve("http://host/x.js", "http://host/a/b/../c.js"))
        assertEquals("http://host/a/c.js", UriUtil.resolve("http://host/x.js", "http://host/a/./c.js"))
    }

    @Test
    fun resolve_multiLevelDotSegments_doNotEscapeRoot() {
        assertEquals("http://host/c.js", UriUtil.resolve("http://host/a/b/x.js", "../../../c.js"))
    }

    @Test
    fun resolve_nullArgumentsAreTreatedAsEmpty() {
        assertEquals("b.js", UriUtil.resolve(null, "b.js"))
        assertEquals("http://host/app/x.js", UriUtil.resolve("http://host/app/x.js", null))
        assertEquals("", UriUtil.resolve(null, null))
    }

    @Test
    fun resolve_authorityOnlyBase_keepsAuthoritySlash() {
        assertEquals("http://host/z.js", UriUtil.resolve("http://host", "z.js"))
    }

    @Test
    fun resolve_fragmentIsDroppedFromBase() {
        assertEquals("http://host/app/x.js#new", UriUtil.resolve("http://host/app/x.js#old", "#new"))
    }

    @Test
    fun isAbsolute_matchesSchemePresence() {
        assertTrue(UriUtil.isAbsolute("http://host/x.js"))
        assertTrue(UriUtil.isAbsolute("file:///sdcard/x.js"))
        assertFalse(UriUtil.isAbsolute("app/x.js"))
        assertFalse(UriUtil.isAbsolute("#frag"))
        assertFalse(UriUtil.isAbsolute(null))
    }
}
