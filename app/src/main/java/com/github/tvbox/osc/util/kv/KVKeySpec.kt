package com.github.tvbox.osc.util.kv

import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.kvcodec.KVDecoder
import com.google.gson.JsonArray
import com.google.gson.reflect.TypeToken

import java.lang.reflect.Type

class KVKeySpec : KVDecoder.TypeRegistry {

    override fun typeOf(key: String): Type? {
        val type = TYPES[key]
        return type ?: typeOfDynamic(key)
    }

    companion object {
        @JvmField
        val JS_RUNTIME_PREFIX = "jsRuntime_"
        @JvmField
        val CACHE_PREFIX = "cache_"

        private val TYPES: MutableMap<String, Type> = HashMap()

        @JvmStatic
        fun typeOfDynamic(key: String): Type? {
            if (key.startsWith(HawkConfig.LIVE_GROUP_INDEX)) return TYPES[HawkConfig.LIVE_GROUP_INDEX]
            if (key.startsWith(JS_RUNTIME_PREFIX) || key.startsWith(CACHE_PREFIX)) return TYPES[HawkConfig.API_URL]
            return null
        }

        @JvmStatic
        fun registeredCount(): Int {
            return TYPES.size
        }

        private fun register(key: String, sample: Any) {
            TYPES[key] = TypeToken.get(sample.javaClass).type
        }

        private fun register(key: String, type: TypeToken<*>) {
            TYPES[key] = type.type
        }

        init {
            register(HawkConfig.API_URL, "")
            register(HawkConfig.EPG_URL, "")
            register(HawkConfig.API_LINE_SOURCE, "")
            register(HawkConfig.HOME_API, "")
            register(HawkConfig.DEFAULT_PARSE, "")
            register(HawkConfig.EXO_DECODE, "")
            register(HawkConfig.LIVE_CHANNEL, "")
            register(HawkConfig.DOH_JSON, "")
            register(HawkConfig.LIVE_API_URL, "")
            register(HawkConfig.REMOTE_TVBOX, "")
            register(HawkConfig.DANMU_API, "")
            register(HawkConfig.THEME_PALETTE_STYLE, "")
            register(HawkConfig.PICTURE_PRESET, "")
            register(HawkConfig.ANIME4K_TIER, "")
            register(HawkConfig.ANIME4K_SHARPEN, 1.0f)
            register(HawkConfig.ANIME4K_DEBLUR, false)
            register(HawkConfig.HOME_HOT, "")
            register(HawkConfig.HOME_HOT_DAY, "")
            register(HawkConfig.THUNDER_IMEI, "")
            register(HawkConfig.THUNDER_MAC, "")
            register(HawkConfig.TMDB_API_KEY, "")
            register(HawkConfig.TMDB_API_BASE, "")
            register(HawkConfig.TMDB_IMAGE_BASE, "")

            register(HawkConfig.PLAY_TYPE, 0)
            register(HawkConfig.PLAY_RENDER, 0)
            register(HawkConfig.PLAY_SCALE, 0)
            register(HawkConfig.LIVE_PLAY_SCALE, 0)
            register(HawkConfig.DOH_URL, 0)
            register(HawkConfig.HISTORY_NUM, 0)
            register(HawkConfig.LIVE_CONNECT_TIMEOUT, 0)
            register(HawkConfig.SUBTITLE_TEXT_SIZE, 0)
            register(HawkConfig.SUBTITLE_TIME_DELAY, 0)
            register(HawkConfig.SUBTITLE_EXO_SCALE, 0)
            register(HawkConfig.SUBTITLE_TEXT_STYLE, 0)
            register(HawkConfig.LIVE_GROUP_INDEX, 0)
            register(HawkConfig.SEARCH_THREADS, 0)
            register(HawkConfig.LONG_PRESS_SPEED, 0)
            register(HawkConfig.TMDB_POSTER_STYLE, 0)
            register(HawkConfig.BUFFER_TIMES, 0)
            register(HawkConfig.PRELOAD_DURATION, 0)
            register(HawkConfig.EXO_CACHE_SIZE_MB, 0)
            register(HawkConfig.DANMU_MAX_LINE, 0)
            register(HawkConfig.THEME_SOURCE, 0)
            register(HawkConfig.THEME_MODE, 0)
            register(HawkConfig.THEME_SEED, 0)
            register(HawkConfig.LIQUID_GLASS_BLUR, 0)
            register(HawkConfig.LIQUID_GLASS_DISTORTION, 0)
            register(HawkConfig.COLLECT_COLUMNS, 0)

            register(HawkConfig.PLAYER_IS_LIVE, false)
            register(HawkConfig.LIVE_CHANNEL_REVERSE, false)
            register(HawkConfig.LIVE_CROSS_GROUP, false)
            register(HawkConfig.LIVE_SHOW_NET_SPEED, false)
            register(HawkConfig.LIVE_SHOW_TIME, false)
            register(HawkConfig.M3U8_PURIFY, false)
            register(HawkConfig.AUTO_SWITCH_LINE, false)
            register(HawkConfig.DEFAULT_LOAD_LIVE, false)
            register(HawkConfig.INCOGNITO, false)
            register(HawkConfig.HIDE_STATUS_BAR, false)
            register(HawkConfig.GESTURE_CONTROL_DISABLED, false)
            register(HawkConfig.NAV_ANIMATION_DISABLED, false)
            register(HawkConfig.NAV_LIVE_HIDDEN, false)
            register(HawkConfig.PLAY_TUNNEL, false)
            register(HawkConfig.PLAY_PREFER_AAC, false)
            register(HawkConfig.ANIME4K_ENABLED, false)
            register(HawkConfig.KERNEL_PREWARM, false)
            register(HawkConfig.EXO_VIDEO_DYNAMIC_SCHEDULING, false)
            register(HawkConfig.PRELOAD_NEXT_EPISODE, false)
            register(HawkConfig.PLAY_CACHE, false)
            register(HawkConfig.DANMU_OPEN, false)
            register(HawkConfig.DANMU_RANDOM_COLOR, false)
            register(HawkConfig.DANMU_API_USE_DEFAULT, false)
            register(HawkConfig.LIQUID_GLASS_NAVBAR, false)
            register(HawkConfig.LIQUID_GLASS_CONTROLS, false)
            register(HawkConfig.LIQUID_GLASS_DISPERSION, false)
            register(HawkConfig.THEME_PURE_BLACK, false)
            register(HawkConfig.TMDB_ENABLE, false)

            register(HawkConfig.LIQUID_GLASS_BLUR, 0f)
            register(HawkConfig.LIQUID_GLASS_DISTORTION, 0f)
            register(HawkConfig.LIQUID_GLASS_TRANSLUCENCY, 0f)
            register(HawkConfig.DANMU_SPEED, 0f)
            register(HawkConfig.DANMU_ALPHA, 0f)
            register(HawkConfig.DANMU_SIZE_SCALE, 0f)
            register(HawkConfig.SUBTITLE_EXO_POSITION, 0f)
            register(HawkConfig.PICTURE_SATURATION, 0f)
            register(HawkConfig.PICTURE_CONTRAST, 0f)
            register(HawkConfig.PICTURE_BRIGHTNESS, 0f)
            register(HawkConfig.PICTURE_GAMMA, 0f)
            register(HawkConfig.PICTURE_HUE, 0f)
            register(HawkConfig.PICTURE_TEMPERATURE, 0f)
            register(HawkConfig.PICTURE_SHARPNESS, 0f)
            register(HawkConfig.PICTURE_SHADOW_LIFT, 0f)

            register(HawkConfig.SEARCH_HISTORY, object : TypeToken<ArrayList<String>>() {
            })
            register(HawkConfig.API_HISTORY, object : TypeToken<ArrayList<String>>() {
            })
            register(HawkConfig.LIVE_API_HISTORY, object : TypeToken<ArrayList<String>>() {
            })
            register(HawkConfig.API_LINE_LIST, object : TypeToken<ArrayList<String>>() {
            })
            register(HawkConfig.LIVE_API_LINE_LIST, object : TypeToken<ArrayList<String>>() {
            })
            register(HawkConfig.BOOT_LOADING_JAR, "")
            register(HawkConfig.BOOT_SAFE_DISABLED, "")
            register(HawkConfig.BOOT_DISABLED_SOURCES, object : TypeToken<ArrayList<String>>() {
            })
            register(HawkConfig.BOOT_VOD_SOURCE, "")
            register(HawkConfig.BOOT_LIVE_SOURCE, "")
            register(HawkConfig.BOOT_LOADING_COUNT, 0L)
            register(HawkConfig.BOOT_LAST_ATTEMPT_AT, 0L)
            register(HawkConfig.BOOT_LOAD_START_ELAPSED, 0L)
            register(HawkConfig.SUBSCRIBE_LIST, object : TypeToken<ArrayList<String>>() {
            })
            register(HawkConfig.LIVE_SUBSCRIBE_LIST, object : TypeToken<ArrayList<String>>() {
            })
            register(HawkConfig.LOCAL_SOURCE_TREES, object : TypeToken<ArrayList<String>>() {
            })
            register(HawkConfig.LIVE_GROUP_LIST, TypeToken.get(JsonArray::class.java))

            register(HawkConfig.LIVE_WEB_HEADER, object : TypeToken<HashMap<String, String>>() {
            })
            register(HawkConfig.SOURCES_FOR_SEARCH, object : TypeToken<HashMap<String, HashMap<String, String>>>() {
            })
            register(HawkConfig.SOURCE_NAME_CACHE, object : TypeToken<HashMap<String, String>>() {
            })
        }
    }
}
