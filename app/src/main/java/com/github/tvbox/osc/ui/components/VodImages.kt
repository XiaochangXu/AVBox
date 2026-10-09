package com.github.tvbox.osc.ui.components

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.crossfade
import com.github.tvbox.osc.net.OkGoHelper
import com.github.tvbox.osc.util.LOG
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient

object VodImages {

    private const val PIC_HTTP_CACHE_MB = 250L
    private const val DOUBAN_SMALL_POSTER = "/s_ratio_poster/"
    private const val DOUBAN_LARGE_POSTER = "/l_ratio_poster/"
    private var picCacheDir: File? = null

    fun init(context: Context) {
        picCacheDir = File(context.cacheDir, "pic_http_cache")
        SingletonImageLoader.setSafe { appContext ->
            ImageLoader.Builder(appContext)
                .components {
                    add(com.github.tvbox.osc.util.CoilBridge.okhttpFetcher { picClient() })
                }
                .crossfade(true)
                .build()
        }
    }

    private fun picClient(): OkHttpClient = OkHttpClient.Builder()
        .apply { OkGoHelper.getDefaultClient()?.dns?.let { dns(it) } }
        .cache(picCacheDir?.let { Cache(it, PIC_HTTP_CACHE_MB * 1024L * 1024L) })
        .addInterceptor(picHeaderInterceptor)
        .addNetworkInterceptor(picForceCacheInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val picForceCacheInterceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        val cc = response.header("Cache-Control")
        if (cc != null && (cc.contains("no-store") || cc.contains("no-cache") || cc.contains("max-age=0"))) {
            response.newBuilder()
                .removeHeader("Cache-Control")
                .removeHeader("Pragma")
                .header("Cache-Control", "public, max-age=604800")
                .build()
        } else {
            response
        }
    }

    private val picHeaderInterceptor = Interceptor { chain ->
        val request = chain.request()
        val parsed = parseVodPicUrl(request.url.toString())
        if (parsed == null) {
            chain.proceed(request)
        } else {
            val (url, headers) = parsed
            val newRequest = request.newBuilder().url(url).apply {
                headers.forEach { (k, v) -> header(k, v) }
            }.build()
            chain.proceed(newRequest)
        }
    }

    private fun parseVodPicUrl(raw: String): Pair<String, Map<String, String>>? {
        if (raw.startsWith("data:") || !raw.contains('@')) return null

        fun grab(key: String): String? =
            if (raw.contains("$key=")) raw.split("$key=")[1].split("@")[0] else null

        val url = raw.split("@")[0]
        if (url.isEmpty()) return null

        val headers = LinkedHashMap<String, String>()
        fun put(k: String, v: String?) {
            if (!v.isNullOrEmpty()) headers[k] = v
        }
        grab("@Headers")?.let { json ->
            try {
                val decoded = URLDecoder.decode(json, "UTF-8")
                val obj = Gson().fromJson(decoded, JsonObject::class.java)
                for (key in obj.keySet()) put(key, obj.get(key).asString)
            } catch (ignored: Throwable) {
                LOG.d("VodImages", "pic url @Headers decode failed, skip extra headers")
            }
        }
        put("Cookie", grab("@Cookie"))
        put("User-Agent", grab("@User-Agent"))
        put("Referer", grab("@Referer"))
        return url to headers
    }

    fun largePosterUrl(raw: String?): String? {
        if (raw.isNullOrEmpty() || raw.startsWith("data:")) return raw
        val url = raw.split("@")[0]
        if (!url.contains(DOUBAN_SMALL_POSTER)) return raw
        return url.replace(DOUBAN_SMALL_POSTER, DOUBAN_LARGE_POSTER) + raw.substring(url.length)
    }
}
