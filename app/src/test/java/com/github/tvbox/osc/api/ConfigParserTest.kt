package com.github.tvbox.osc.api

import com.github.tvbox.osc.bean.LiveSettingItem
import com.github.tvbox.osc.bean.SourceBean
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.function.Supplier

class ConfigParserTest {

    private fun json(text: String): JsonObject = GSON.fromJson(text, JsonObject::class.java)

    private fun jsonArray(text: String): JsonArray = GSON.fromJson(text, JsonArray::class.java)

    @Test
    fun trimJsonObject_keepsOnlyObjectBody() {
        assertEquals("{\"a\":1}", ConfigParser.trimJsonObject("// 版权说明\n{\"a\":1}\n// 尾巴"))
        assertEquals("{\"a\":1}", ConfigParser.trimJsonObject("  {\"a\":1}  "))
        assertEquals("没有花括号", ConfigParser.trimJsonObject("没有花括号"))
        assertEquals("", ConfigParser.trimJsonObject(null))
    }

    @Test
    fun trimJsonObject_keepsRawTextWhenBracesReversed() {
        assertEquals("} {", ConfigParser.trimJsonObject("} {"))
    }

    @Test
    fun isLiveJsonContent_detectsBomAndText() {
        assertTrue(ConfigParser.isLiveJsonContent("{\"lives\":[]}"))
        assertTrue(ConfigParser.isLiveJsonContent("\ufeff {\"lives\":[]}"))
        assertFalse(ConfigParser.isLiveJsonContent("#EXTM3U\nCCTV1,http://a/1"))
        assertFalse(ConfigParser.isLiveJsonContent("   "))
        assertFalse(ConfigParser.isLiveJsonContent(null))
    }

    @Test
    fun extractQuotedAttr_readsValueAndToleratesMalformed() {
        val line = "#EXTM3U url-tvg=\" http://a/e.xml \" tvg-url=\"http://b/e.xml\""
        assertEquals("http://a/e.xml", ConfigParser.extractQuotedAttr(line, "url-tvg"))
        assertEquals("http://b/e.xml", ConfigParser.extractQuotedAttr(line, "tvg-url"))
        assertEquals("", ConfigParser.extractQuotedAttr(line, "x-tvg-url"))
        assertEquals("", ConfigParser.extractQuotedAttr("#EXTM3U x-tvg-url=\"http://a/e.xml", "x-tvg-url"))
    }

    @Test
    fun extractQuotedAttr_tvgUrlAlsoMatchesInsideXTvgUrl() {
        val line = "#EXTM3U x-tvg-url=\"http://a/e.xml\" tvg-url=\"http://b/e.xml\""
        assertEquals("http://a/e.xml", ConfigParser.extractQuotedAttr(line, "tvg-url"))
    }

    @Test
    fun extractLiveTextEpg_triesThreeAttributeNamesInOrder() {
        assertEquals(
            "http://a/e.xml",
            ConfigParser.extractLiveTextEpg("#EXTM3U x-tvg-url=\"http://a/e.xml\"\nCCTV1,http://a/1"),
        )
        assertEquals("http://b/e.xml", ConfigParser.extractLiveTextEpg("#EXTM3U tvg-url=\"http://b/e.xml\""))
        assertEquals("http://c/e.xml", ConfigParser.extractLiveTextEpg("#EXTM3U url-tvg=\"http://c/e.xml\""))
        assertEquals(
            "http://a/e.xml",
            ConfigParser.extractLiveTextEpg("#EXTM3U x-tvg-url=\"http://a/e.xml\" tvg-url=\"http://b/e.xml\""),
        )
    }

    @Test
    fun extractLiveTextEpg_handlesBomCrlfAndMissing() {
        assertEquals("http://a/e.xml", ConfigParser.extractLiveTextEpg("\ufeff#EXTM3U x-tvg-url=\"http://a/e.xml\""))
        assertEquals(
            "http://a/e.xml",
            ConfigParser.extractLiveTextEpg("CCTV1,http://a/1\r\n#EXTM3U x-tvg-url=\"http://a/e.xml\"\r\n"),
        )
        assertEquals("", ConfigParser.extractLiveTextEpg("#EXTM3U\nCCTV1,http://a/1"))
        assertEquals("", ConfigParser.extractLiveTextEpg(null))
    }

    @Test
    fun parseSites_buildsBeansAndSkipsIncomplete() {
        val sites = ConfigParser.parseSites(
            json(
                "{\"sites\":[" +
                    "{\"key\":\"csp_A\",\"name\":\"站点A\",\"type\":3,\"api\":\"http://a/api\",\"ext\":\"/path/ext\"," +
                    "\"icon\":\"http://a/i.png\",\"categories\":[\"电影\",\"剧集\"],\"searchable\":0}," +
                    "{\"key\":\"no_api\",\"type\":1}," +
                    "{\"type\":1,\"api\":\"http://c/api\"}," +
                    "{\"key\":\"py_B\",\"type\":1,\"api\":\"http://b/api\"}]}",
            ),
        )

        assertEquals(2, sites.size)
        val a = sites[0]
        assertEquals("csp_A", a.key)
        assertEquals("站点A", a.name)
        assertEquals(3, a.type)
        assertEquals("http://a/api", a.api)
        assertEquals("/path/ext", a.ext)
        assertEquals("http://a/i.png", a.icon)
        assertEquals(2, a.categories!!.size)
        assertFalse(a.isSearchable())
    }

    @Test
    fun parseSites_appliesDefaultsAndPyFilterable() {
        val sites = ConfigParser.parseSites(
            json("{\"sites\":[{\"key\":\"py_B\",\"type\":1,\"api\":\"http://b/api\"}]}"),
        )

        assertEquals(1, sites.size)
        val b = sites[0]
        assertEquals("py_B", b.name)
        assertTrue(b.isSearchable())
        assertTrue(b.isQuickSearch())
        assertTrue(b.isChangeable())
        assertEquals(1, b.filterable)
        assertEquals(-1, b.playerType)
        assertEquals(0, b.timeout)
        assertEquals("", b.jar)
        assertEquals("", b.style)
        assertEquals("", b.clickSelector)
    }

    @Test
    fun parseSites_keepsConfigOrder() {
        val sites = ConfigParser.parseSites(
            json(
                "{\"sites\":[" +
                    "{\"key\":\"b\",\"type\":1,\"api\":\"http://b\"}," +
                    "{\"key\":\"a\",\"type\":1,\"api\":\"http://a\"}]}",
            ),
        )
        assertEquals("b", sites[0].key)
        assertEquals("a", sites[1].key)
    }

    @Test
    fun parseSites_readsHideIndexsDanmakuAndHeader() {
        val sites = ConfigParser.parseSites(
            json(
                "{\"sites\":[" +
                    "{\"key\":\"a\",\"type\":1,\"api\":\"http://a\",\"hide\":1,\"indexs\":1,\"danmaku\":0," +
                    "\"header\":{\"User-Agent\":\"ua\",\"Referer\":\"http://a/\",\"num\":5}}," +
                    "{\"key\":\"b\",\"type\":1,\"api\":\"http://b\",\"header\":\"not-an-object\"}]}",
            ),
        )

        val a = sites[0]
        assertTrue(a.isHidden())
        assertTrue(a.isIndexSource)
        assertFalse(a.isDanmakuEnabled())
        val aHeader = a.header!!
        assertEquals(3, aHeader.size)
        assertEquals("ua", aHeader["User-Agent"])
        assertEquals("5", aHeader["num"])

        val b = sites[1]
        assertFalse(b.isHidden())
        assertFalse(b.isIndexSource)
        assertTrue(b.isDanmakuEnabled())
        assertTrue(b.header!!.isEmpty())
    }

    @Test
    fun parseSites_dropsIllegalHeaders() {
        val sites = ConfigParser.parseSites(
            json(
                "{\"sites\":[{\"key\":\"a\",\"type\":1,\"api\":\"http://a\"," +
                    "\"header\":{\"Referer\":\"http://a/\",\"中文名\":\"x\",\"X-Cn\":\"中文值\",\"\":\"v\"}}]}",
            ),
        )

        val header = sites[0].header!!
        assertEquals(1, header.size)
        assertEquals("http://a/", header["Referer"])
    }

    @Test
    fun parseApiCollection_acceptsObjectsAndPrimitives() {
        val lines = ConfigParser.parseApiCollection(
            "{\"urls\":[{\"name\":\"线路1\",\"url\":\"http://a/1\"},{\"url\":\"http://b/2\"},\"http://c/3\"]}",
        )

        assertEquals(3, lines.size)
        assertEquals("线路1\thttp://a/1", lines[0])
        assertEquals("http://b/2\thttp://b/2", lines[1])
        assertEquals("http://c/3\thttp://c/3", lines[2])
    }

    @Test
    fun parseApiCollection_usesApiFieldWhenUrlMissing() {
        val lines = ConfigParser.parseApiCollection(
            "{\"urls\":[{\"name\":\"x\",\"api\":\"http://d/4\"},{\"name\":\"empty\",\"url\":\"\"}]}",
        )
        assertEquals(1, lines.size)
        assertEquals("x\thttp://d/4", lines[0])
    }

    @Test
    fun parseApiCollection_toleratesSurroundingText() {
        val lines = ConfigParser.parseApiCollection("说明文字\n{\"urls\":[\"http://c/3\"]}\n尾巴")
        assertEquals(1, lines.size)
        assertEquals("http://c/3\thttp://c/3", lines[0])
    }

    @Test
    fun parseApiCollection_rejectsNormalConfigAndGarbage() {
        assertTrue(ConfigParser.parseApiCollection("{\"sites\":[],\"urls\":[\"http://a/1\"]}").isEmpty())
        assertTrue(ConfigParser.parseApiCollection("{\"urls\":\"http://a/1\"}").isEmpty())
        assertTrue(ConfigParser.parseApiCollection("这不是 JSON").isEmpty())
        assertTrue(ConfigParser.parseApiCollection(null).isEmpty())
    }

    @Test
    fun isDepotJson_matchesOnlyUrlsWithoutSites() {
        assertTrue(ConfigParser.isDepotJson(json("{\"urls\":[{\"name\":\"仓A\",\"url\":\"http://a/1\"}]}")))
        assertFalse(ConfigParser.isDepotJson(json("{\"urls\":[]}")))
        assertFalse(ConfigParser.isDepotJson(json("{\"urls\":\"http://a/1\"}")))
        assertFalse(ConfigParser.isDepotJson(json("{\"urls\":{\"url\":\"http://a/1\"}}")))
        assertFalse(ConfigParser.isDepotJson(json("{\"sites\":[],\"urls\":[\"http://a/1\"]}")))
        assertFalse(ConfigParser.isDepotJson(json("{\"lives\":[]}")))
        assertFalse(ConfigParser.isDepotJson(null))
    }

    @Test
    fun parseApiCollection_toleratesBadNameAndEmptyEntries() {
        val lines = ConfigParser.parseApiCollection(
            "{\"urls\":[{\"url\":\"http://a/1\",\"name\":123},{\"name\":\"空地址\"},\"http://c/3\"]}",
        )
        assertEquals(2, lines.size)
        assertEquals("http://a/1\thttp://a/1", lines[0])
        assertEquals("http://c/3\thttp://c/3", lines[1])
    }

    @Test
    fun parseLiveSettingItems_namesAndIndexes() {
        val items: ArrayList<LiveSettingItem> = ConfigParser.parseLiveSettingItems(
            jsonArray("[{\"name\":\"线路A\"},{},{\"name\":\"\"}]"),
        )

        assertEquals(3, items.size)
        assertEquals(0, items[0].itemIndex)
        assertEquals("线路A", items[0].itemName)
        assertEquals(1, items[1].itemIndex)
        assertEquals("线路2", items[1].itemName)
        assertEquals("", items[2].itemName)
    }

    @Test
    fun parseHosts_splitsOnFirstEqualsOnly() {
        val hosts = ConfigParser.parseHosts(jsonArray("[\"a.com=1.2.3.4\",\"b.com=2.3.4.5=x\",\"bad\"]"))

        assertEquals(2, hosts.size)
        assertEquals("1.2.3.4", hosts["a.com"])
        assertEquals("2.3.4.5=x", hosts["b.com"])
    }

    @Test
    fun parseLiveChannelName_prefersNameThenFirstUrl() {
        val urls = arrayListOf("http://a/1", "http://a/2")

        assertEquals("CCTV1", ConfigParser.parseLiveChannelName(json("{\"name\":\" CCTV1 \"}"), urls))
        assertEquals("http://a/1", ConfigParser.parseLiveChannelName(json("{\"urls\":[\"http://a/1\"]}"), urls))
        assertEquals("http://a/1", ConfigParser.parseLiveChannelName(json("{\"name\":null}"), urls))
        assertEquals("http://a/1", ConfigParser.parseLiveChannelName(json("{\"name\":[\"CCTV1\"]}"), urls))
        assertEquals("http://a/1", ConfigParser.parseLiveChannelName(json("{\"name\":{\"id\":1}}"), urls))
        assertEquals("3", ConfigParser.parseLiveChannelName(json("{\"name\":3}"), urls))
        assertEquals(
            "http://a/2",
            ConfigParser.parseLiveChannelName(json("{}"), arrayListOf("", "http://a/2")),
        )
        assertEquals("", ConfigParser.parseLiveChannelName(json("{}"), arrayListOf()))
        assertEquals("", ConfigParser.parseLiveChannelName(null, arrayListOf()))
    }

    @Test
    fun parseLiveCatchup_shapesAndBadValues() {
        val asObject = ConfigParser.parseLiveCatchup(
            json("{\"catchup\":{\"type\":\"default\",\"source\":\"http://a/{date}\"}}"),
        )
        assertEquals("default", asObject!!["type"].asString)
        assertEquals("http://a/{date}", asObject["source"].asString)

        val asScalar = ConfigParser.parseLiveCatchup(
            json("{\"catchup\":\"default\",\"catchup-source\":\"http://a/{date}\",\"catchup-replace\":\"a,b\"}"),
        )
        assertEquals("default", asScalar!!["type"].asString)
        assertEquals("http://a/{date}", asScalar["source"].asString)
        assertEquals("a,b", asScalar["replace"].asString)

        assertNull(ConfigParser.parseLiveCatchup(json("{\"catchup\":null}")))
        assertNull(ConfigParser.parseLiveCatchup(json("{\"name\":\"CCTV1\"}")))
        assertNull(ConfigParser.parseLiveCatchup(json("{\"catchup\":[\"default\"]}")))
    }

    @Test
    fun clanToAddress_localhostUsesLocalBase() {
        assertEquals(
            "http://192.168.1.9:9978/file/abc.json",
            ConfigParser.clanToAddress("clan://localhost/abc.json", LOCAL),
        )
    }

    @Test
    fun clanToAddress_remoteHostDoesNotTouchLocalBase() {
        assertEquals(
            "http://tvbox.example.com/file/abc.json",
            ConfigParser.clanToAddress("clan://tvbox.example.com/abc.json", NEVER),
        )
    }

    @Test
    fun clanContentFix_rewritesBothPrefixes() {
        val fixed = ConfigParser.clanContentFix(
            "http://192.168.1.9:9978/file/abc.json",
            "{\"a\":\"clan://localhost/x.jpg\",\"b\":\"file:///sdcard/y.jpg\"}",
        )
        assertEquals(
            "{\"a\":\"http://192.168.1.9:9978/file/x.jpg\",\"b\":\"http://192.168.1.9:9978/file//sdcard/y.jpg\"}",
            fixed,
        )
    }

    @Test
    fun fixContentPath_untouchedWithoutRelativePath() {
        val content = "{\"a\":\"http://a/x.jpg\"}"
        assertEquals(content, ConfigParser.fixContentPath("http://h/dir/config.json", content, NEVER))
    }

    @Test
    fun fixContentPath_resolvesRelativePathsAgainstConfigUrl() {
        assertEquals(
            "{\"a\":\"http://h/dir/pic.jpg\"}",
            ConfigParser.fixContentPath("http://h/dir/config.json", "{\"a\":\"./pic.jpg\"}", NEVER),
        )
        assertEquals(
            "{\"a\":\"http://h/pic.jpg\"}",
            ConfigParser.fixContentPath("h/dir/config.json", "{\"a\":\"../pic.jpg\"}", NEVER),
        )
    }

    @Test
    fun fixContentPath_clanUrlGoesThroughLocalBase() {
        assertEquals(
            "{\"a\":\"http://192.168.1.9:9978/file/pic.jpg\"}",
            ConfigParser.fixContentPath("clan://localhost/config.json", "{\"a\":\"./pic.jpg\"}", LOCAL),
        )
    }

    @Test
    fun configUrl_keepsHttpAndAddsScheme() {
        val http = ConfigParser.configUrl("http://a/config.json", NEVER)
        assertEquals("http://a/config.json", http.url)
        assertNull(http.key)

        assertEquals("http://a/config.json", ConfigParser.configUrl("a/config.json", NEVER).url)
    }

    @Test
    fun configUrl_splitsPkKey() {
        val withKey = ConfigParser.configUrl("http://a/config.json;pk;1234", NEVER)
        assertEquals("http://a/config.json", withKey.url)
        assertEquals("1234", withKey.key)

        val noScheme = ConfigParser.configUrl("a/config.json;pk;k", NEVER)
        assertEquals("http://a/config.json", noScheme.url)
        assertEquals("k", noScheme.key)
    }

    @Test
    fun configUrl_clanAndFileLinks() {
        assertEquals(
            "http://192.168.1.9:9978/file/config.json",
            ConfigParser.configUrl("clan://localhost/config.json", LOCAL).url,
        )
        assertEquals(
            "http://tvbox.example.com/file/c.json",
            ConfigParser.configUrl("clan://tvbox.example.com/c.json;pk;k", NEVER).url,
        )
        assertEquals(
            "http://192.168.1.9:9978/file//sdcard/c.json",
            ConfigParser.configUrl("file:///sdcard/c.json", LOCAL).url,
        )
    }

    companion object {
        private val GSON = Gson()

        private val LOCAL = Supplier { "http://192.168.1.9:9978/" }

        private val NEVER = Supplier<String> {
            throw AssertionError("非 clan://localhost/ 地址不应该去取本机服务基址")
        }
    }
}
