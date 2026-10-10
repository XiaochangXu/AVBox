package com.github.tvbox.osc.util.kvcodec

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Type
import java.util.ArrayList
import java.util.HashMap
import java.util.Map

class KVDecoderTest {

    private lateinit var decoder: KVDecoder

    private class MapRegistry : KVDecoder.TypeRegistry {
        override fun typeOf(key: String): Type? {
            val direct = TYPES[key]
            return when {
                direct != null -> direct
                key.startsWith("live_group_index") -> TYPES["live_group_index"]
                key.startsWith("jsRuntime_") -> TYPES["api_url"]
                else -> null
            }
        }
    }

    @Before
    fun setUp() {
        decoder = KVDecoder()
        decoder.setRegistry(MapRegistry())
    }

    @Test
    fun stringRoundTrip_keepsLiteralText() {
        val encoded = decoder.encode("hello")
        assertEquals("hello", encoded)
        assertEquals("hello", decoder.decode("api_url", encoded, ""))
    }

    @Test
    fun stringThatLooksLikeJson_isNotMistakenForComplexValue() {
        val tricky = "{\"a\":1}"
        val encoded = decoder.encode(tricky)
        assertEquals(tricky, decoder.decode("api_url", encoded, ""))
    }

    @Test
    fun primitiveRoundTrip() {
        assertEquals(Integer.valueOf(2), decoder.decode("play_type", 2, 0))
        assertEquals(java.lang.Boolean.TRUE, decoder.decode("play_type", java.lang.Boolean.TRUE, java.lang.Boolean.FALSE))
        assertEquals(java.lang.Float.valueOf(1.5f), decoder.decode("play_type", 1.5f, 0f))
        assertEquals(java.lang.Long.valueOf(9L), decoder.decode("play_type", 9L, 0L))
    }

    @Test
    fun arrayList_restoresStringElementType() {
        val value = ArrayList<String>()
        value.add("源A\thttp://a")
        value.add("源B\thttp://b")

        val encoded = decoder.encode(value)
        val restored = decoder.decode<ArrayList<String>>("subscribe_list", encoded, ArrayList())

        assertNotNull(restored)
        assertEquals(value, restored)
        assertTrue(restored!![0] is String)
    }

    @Test
    fun jsonArray_restoresNodeTreeNotList() {
        val value = JsonParser.parseString("[{\"group\":\"央视\"},{\"group\":\"卫视\"}]").asJsonArray

        val encoded = decoder.encode(value)
        val restored = decoder.decode<JsonArray>("live_group_list", encoded, JsonArray())

        assertNotNull(restored)
        assertEquals(2, restored!!.size())
        assertEquals("央视", restored[0].asJsonObject["group"].asString)
    }

    @Test
    fun flatMap_restoresStringValues() {
        val value = HashMap<String, String>()
        value["sourceA"] = "detail"
        val encoded = decoder.encode(value)

        val restored = decoder.decode<HashMap<String, String>>("source_card_policy", encoded, HashMap())

        assertNotNull(restored)
        assertEquals("detail", restored!!["sourceA"])
        assertTrue(restored["sourceA"] is String)
    }

    @Test
    fun nestedMap_registryTypeRestoresStringValues() {
        val value = HashMap<String, HashMap<String, String>>()
        val inner = HashMap<String, String>()
        inner["sourceA"] = "1"
        value["http://api"] = inner
        val encoded = decoder.encode(value)

        val forced = KVDecoder()
        forced.setRegistry(object : KVDecoder.TypeRegistry {
            override fun typeOf(key: String): Type? = TYPES["checked_sources_for_search"]
        })
        val restored = forced.decode<Any?>("checked_sources_for_search", encoded, null)

        assertTrue(restored is Map<*, *>)
        val asMap = restored as Map<*, *>
        val nested = asMap["http://api"] as Map<*, *>
        assertEquals("1", nested["sourceA"])
        assertTrue(nested["sourceA"] is String)
    }

    @Test
    fun nestedMap_withoutRegistry_collapsesInnerElementsToNodeTree() {
        val value = HashMap<String, HashMap<String, String>>()
        val inner = HashMap<String, String>()
        inner["sourceA"] = "1"
        value["http://api"] = inner
        val encoded = decoder.encode(value)

        val bare = KVDecoder()
        val restored = bare.decode<Any?>("checked_sources_for_search", encoded, HashMap<String, String>())

        assertTrue(restored is Map<*, *>)
        val innerValue = (restored as Map<*, *>)["http://api"]
        assertTrue(innerValue is Map<*, *>)
        assertEquals("1", (innerValue as Map<*, *>)["sourceA"])
    }

    @Test
    fun callerConcreteTypeWins_forDynamicKeysOutsideRegistry() {
        val encoded = decoder.encode("payload")
        assertEquals("payload", decoder.decode("jsRuntime_abc_def", encoded, ""))
    }

    @Test
    fun searchThreadsCrashRegression_zeroUnwrapsToIntNotDefault() {
        assertEquals(Integer.valueOf(0), decoder.decode("search_threads", 0, 32))
        assertNull(decoder.decode("search_threads", null, null))
    }

    @Test
    fun unregisteredKeyWithoutDefault_fallsBackToJsonNodeTree() {
        val value = JsonParser.parseString("[{\"group\":\"央视\"}]").asJsonArray

        val restored = decoder.decode<Any?>("unknown_key", decoder.encode(value), null)
        assertTrue(restored is JsonArray)
        assertEquals("央视", (restored as JsonArray)[0].asJsonObject["group"].asString)
    }

    @Test
    fun objectDefault_isTreatedAsNoTypeHint_notAsPassthrough() {
        val value = JsonParser.parseString("[1,2]").asJsonArray
        val restored = decoder.decode<Any?>("unknown_key", decoder.encode(value), Any())
        assertTrue("复杂值应按节点树还原,而不是返回原始字符串", restored is JsonArray)
    }

    @Test
    fun unregisteredPlainTextKeyWithoutDefault_isReportedNotSilentlyDefaulted() {
        assertNull(decoder.decode("unknown_key", "plain", null))
    }

    @Test
    fun quietCopy_reportsNothing_butKeepsSameResult() {
        val quiet = decoder.quiet()
        assertNull(quiet.decode("unknown_key", "plain", null))
        assertEquals("payload", quiet.decode("jsRuntime_abc_def", decoder.encode("payload"), ""))
    }

    @Test
    fun decodeFailure_returnsNullInsteadOfThrowing() {
        val restored = decoder.decode<Any?>("checked_sources_for_search", "\u0001json:{not-json", HashMap<String, String>())
        assertNull(restored)
    }

    @Test
    fun legacyPlainTextValue_isConvertedByTargetType() {
        assertEquals("硬解码", decoder.decode("api_url", "硬解码", ""))
    }

    @Test
    fun jsonNumberParsedAsDouble_isCoercedToIntTarget() {
        assertEquals(Integer.valueOf(0), decoder.decode("play_type", 0, 32))
        assertEquals(Integer.valueOf(3), decoder.decode("play_type", 3, 32))
    }

    @Test
    fun jsonNumberCoercion_coversAllNumericTargets() {
        assertEquals(Integer.valueOf(7), decoder.decode("k", 7, 0))
        assertEquals(java.lang.Long.valueOf(7L), decoder.decode("k", 7, 0L))
        assertEquals(java.lang.Float.valueOf(2.5f), decoder.decode("k", 2.5, 0f))
        assertEquals(java.lang.Double.valueOf(2.5), decoder.decode("k", 2.5, 0.0))
    }

    @Test
    fun intTarget_outOfRangeFallsBackToDefaultInsteadOfTruncating() {
        assertNull(decoder.coerceNumber(Int::class.java, Integer.MAX_VALUE.toLong() + 1))
        assertNull(decoder.coerceNumber(Integer::class.java, Double.MAX_VALUE))
    }

    @Test
    fun stringValueOnNumericTarget_isNotCoerced() {
        assertEquals("32", decoder.coerceNumber(Int::class.java, "32"))
    }

    companion object {
        private val TYPES: MutableMap<String, Type> = HashMap()

        init {
            TYPES["search_history"] = object : TypeToken<ArrayList<String>>() {}.type
            TYPES["subscribe_list"] = object : TypeToken<ArrayList<String>>() {}.type
            TYPES["live_group_list"] = TypeToken.get(JsonArray::class.java).type
            TYPES["source_card_policy"] = object : TypeToken<HashMap<String, String>>() {}.type
            TYPES["checked_sources_for_search"] =
                object : TypeToken<HashMap<String, HashMap<String, String>>>() {}.type
            TYPES["api_url"] = TypeToken.get(String::class.java).type
            TYPES["play_type"] = TypeToken.get(Int::class.java).type
            TYPES["live_group_index"] = TypeToken.get(Int::class.java).type
        }
    }
}
