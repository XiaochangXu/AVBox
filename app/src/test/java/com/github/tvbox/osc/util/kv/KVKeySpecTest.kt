package com.github.tvbox.osc.util.kv

import com.google.gson.JsonArray
import com.google.gson.reflect.TypeToken
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.kvcodec.KVDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Type
import java.util.ArrayList
import java.util.HashMap
import java.util.Map

class KVKeySpecTest {

    private val spec = KVKeySpec()

    @Test
    fun registeredListKey_keepsElementTypeSignature() {
        val listType = spec.typeOf("subscribe_list")
        assertNotNull("订阅列表必须登记类型", listType)
        assertEquals(ArrayList::class.java, TypeToken.get(listType!!).rawType)
        assertTrue("泛型签名丢失:实际 $listType", listType.toString().contains("java.lang.String"))
    }

    @Test
    fun registeredNestedMapKey_keepsBothTypeArguments() {
        val nested = spec.typeOf("checked_sources_for_search")
        assertNotNull("嵌套泛型键必须登记类型", nested)
        assertEquals(HashMap::class.java, TypeToken.get(nested!!).rawType)
        val text = nested.toString()
        assertTrue(
            "嵌套泛型签名丢失: $text",
            text.contains("HashMap<java.lang.String, java.util.HashMap<java.lang.String, java.lang.String>>"),
        )
    }

    @Test
    fun jsonArrayKey_isRegisteredAsJsonArrayNotList() {
        val jsonArrayType = spec.typeOf("live_group_list")
        assertNotNull("直播分组必须登记类型", jsonArrayType)
        assertEquals(JsonArray::class.java, TypeToken.get(jsonArrayType!!).rawType)
    }

    @Test
    fun primitiveKeys_resolveToTheirBoxedTypes() {
        assertEquals(TypeToken.get(Integer::class.java).type, spec.typeOf("play_type"))
        assertEquals(TypeToken.get(java.lang.Boolean::class.java).type, spec.typeOf("incognito"))
        assertEquals(TypeToken.get(java.lang.Boolean::class.java).type, spec.typeOf("nav_live_hidden"))
        assertEquals(TypeToken.get(String::class.java).type, spec.typeOf("api_url"))
    }

    @Test
    fun dynamicKeyFamilies_resolveByPrefix() {
        assertEquals(TypeToken.get(Integer::class.java).type, spec.typeOf("live_group_index_http://example.com/tv"))
        assertEquals(TypeToken.get(String::class.java).type, spec.typeOf("jsRuntime_spiderA_cookie"))
        assertEquals(TypeToken.get(String::class.java).type, spec.typeOf("cache_rule_key"))
    }

    @Test
    fun newlyAddedSettingsKey_isRegistered() {
        val unusedGuard = HashMap<String, Type>()
        assertTrue(unusedGuard.isEmpty())
        assertEquals(
            TypeToken.get(java.lang.Boolean::class.java).type,
            spec.typeOf(HawkConfig.GESTURE_CONTROL_DISABLED),
        )
    }

    @Test
    fun exoVideoDynamicScheduling_isRegistered() {
        assertEquals(
            TypeToken.get(java.lang.Boolean::class.java).type,
            spec.typeOf(HawkConfig.EXO_VIDEO_DYNAMIC_SCHEDULING),
        )
    }

    @Test
    fun collectColumns_isRegistered() {
        assertEquals(
            TypeToken.get(Integer::class.java).type,
            spec.typeOf(HawkConfig.COLLECT_COLUMNS),
        )
    }

    @Test
    fun pictureParamKeys_areRegistered() {
        assertEquals(TypeToken.get(String::class.java).type, spec.typeOf(HawkConfig.PICTURE_PRESET))
        assertEquals(TypeToken.get(java.lang.Float::class.java).type, spec.typeOf(HawkConfig.PICTURE_SATURATION))
        assertEquals(TypeToken.get(java.lang.Float::class.java).type, spec.typeOf(HawkConfig.PICTURE_CONTRAST))
        assertEquals(TypeToken.get(java.lang.Float::class.java).type, spec.typeOf(HawkConfig.PICTURE_BRIGHTNESS))
        assertEquals(TypeToken.get(java.lang.Float::class.java).type, spec.typeOf(HawkConfig.PICTURE_GAMMA))
        assertEquals(TypeToken.get(java.lang.Float::class.java).type, spec.typeOf(HawkConfig.PICTURE_HUE))
        assertEquals(TypeToken.get(java.lang.Float::class.java).type, spec.typeOf(HawkConfig.PICTURE_TEMPERATURE))
        assertEquals(TypeToken.get(java.lang.Float::class.java).type, spec.typeOf(HawkConfig.PICTURE_SHARPNESS))
        assertEquals(TypeToken.get(java.lang.Float::class.java).type, spec.typeOf(HawkConfig.PICTURE_SHADOW_LIFT))
    }

    @Test
    fun anime4kKeys_areRegistered() {
        assertEquals(TypeToken.get(String::class.java).type, spec.typeOf(HawkConfig.ANIME4K_TIER))
        assertEquals(TypeToken.get(java.lang.Boolean::class.java).type, spec.typeOf(HawkConfig.ANIME4K_ENABLED))
        assertEquals(TypeToken.get(java.lang.Float::class.java).type, spec.typeOf(HawkConfig.ANIME4K_SHARPEN))
        assertEquals(TypeToken.get(java.lang.Boolean::class.java).type, spec.typeOf(HawkConfig.ANIME4K_DEBLUR))
    }

    @Test
    fun liveWebHeader_roundTripDecodesAsStringMap() {
        val decoder = KVDecoder()
        decoder.setRegistry(spec)
        val header = HashMap<String, String>()
        header["User-Agent"] = "Mozilla/5.0"
        header["Referer"] = "http://example.com/"
        val raw = decoder.encode(header)
        val decoded = decoder.decode<Any?>("live_web_header", raw, null)
        assertTrue(
            "应解出 Map,实际 " + (if (decoded == null) "null" else decoded.javaClass.toString()),
            decoded is Map<*, *>,
        )
        val asMap = decoded as Map<*, *>
        assertEquals("Mozilla/5.0", asMap["User-Agent"])
        assertEquals("http://example.com/", asMap["Referer"])
    }

    @Test
    fun unknownKey_returnsNull() {
        assertNull(spec.typeOf("definitely_not_a_registered_key"))
    }
}
