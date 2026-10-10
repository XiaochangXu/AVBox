package com.github.tvbox.osc.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

class FileUtilsNativeLibRepairTest {

    @get:Rule
    val folder = TemporaryFolder()

    companion object {
        private val ELF = byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte(), 0x02, 0x01, 0x01, 0x00)

        private const val XML_ERROR =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Error>\n  <Code>NoSuchKey</Code>\n</Error>"
    }

    private fun write(dir: File, name: String, content: ByteArray): File {
        val file = File(dir, name)
        FileOutputStream(file).use { it.write(content) }
        return file
    }

    private fun write(dir: File, name: String, content: String): File =
        write(dir, name, content.toByteArray(StandardCharsets.UTF_8))

    @Test
    fun removesXmlErrorSavedAsSo() {
        val root = folder.newFolder("files")
        val tv = File(root, "TV")
        assertTrue(tv.mkdirs())
        val bogus = write(tv, "libwexproxy.so", XML_ERROR)
        val bogusTemp = write(tv, ".libwexproxyMZVs13PWOs", XML_ERROR)

        assertEquals(2, FileUtils.repairBogusNativeLibs(root))
        assertFalse(bogus.exists())
        assertFalse(bogusTemp.exists())
    }

    @Test
    fun removesTruncatedAndEmptySo() {
        val root = folder.newFolder("files2")
        val shortFile = write(root, "a.so", byteArrayOf(0x7F, 'E'.code.toByte()))
        val empty = write(root, "b.so", ByteArray(0))

        assertEquals(2, FileUtils.repairBogusNativeLibs(root))
        assertFalse(shortFile.exists())
        assertFalse(empty.exists())
    }

    @Test
    fun removesHtmlSavedAsLib() {
        val root = folder.newFolder("files3")
        val html = write(root, ".libproxy", "<!DOCTYPE html><html>404</html>")

        assertEquals(1, FileUtils.repairBogusNativeLibs(root))
        assertFalse(html.exists())
    }

    @Test
    fun keepsValidElfLibraries() {
        val root = folder.newFolder("files4")
        val tv = File(root, "TV")
        assertTrue(tv.mkdirs())
        val so = write(root, "libLoadNiMa.so", ELF)
        val temp = write(tv, ".libLoadNiMaz3tbtfmq2v", ELF)

        assertEquals(0, FileUtils.repairBogusNativeLibs(root))
        assertTrue(so.exists())
        assertTrue(temp.exists())
    }

    @Test
    fun keepsNonLibraryFiles() {
        val root = folder.newFolder("files5")
        val cfg = write(root, ".wexcofig.json", "{}")
        val string = write(root, ".wexstring", XML_ERROR)
        val flag = write(root, "go_proxy_video", "{}")
        val db = write(root, "spider.db", XML_ERROR)
        val other = write(root, "libdata.txt", XML_ERROR)

        assertEquals(0, FileUtils.repairBogusNativeLibs(root))
        assertTrue(cfg.exists())
        assertTrue(string.exists())
        assertTrue(flag.exists())
        assertTrue(db.exists())
        assertTrue(other.exists())
    }

    @Test
    fun handlesMissingDirAndNestedLevels() {
        assertEquals(0, FileUtils.repairBogusNativeLibs(File(folder.root, "not-there")))

        val root = folder.newFolder("files6")
        val deep = File(root, "a/b")
        assertTrue(deep.mkdirs())
        val bogus = write(deep, "x.so", XML_ERROR)
        assertEquals(1, FileUtils.repairBogusNativeLibs(root))
        assertFalse(bogus.exists())
    }

    @Test
    fun stopsAtDepthLimit() {
        val root = folder.newFolder("files7")
        val tooDeep = File(root, "a/b/c/d/e")
        assertTrue(tooDeep.mkdirs())
        val bogus = write(tooDeep, "x.so", XML_ERROR)

        assertEquals(0, FileUtils.repairBogusNativeLibs(root))
        assertTrue("超出深度上限的文件不应被删除", bogus.exists())
    }
}
