package com.github.tvbox.osc.util.net

import com.github.tvbox.osc.net.OkGoHelper

import java.io.IOException
import java.net.SocketTimeoutException
import java.util.LinkedHashMap

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.OkHttp
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

object Http {

    suspend fun get(url: String, init: HttpRequest.() -> Unit = {}): String {
        return executeWithRetry(HttpRequest(url).apply(init).build(), client())
    }

    suspend fun getRaw(url: String, init: HttpRequest.() -> Unit = {}): HttpRawResponse {
        return executeRawWithRetry(HttpRequest(url).apply(init).build(), client())
    }

    fun getSync(url: String, init: HttpRequest.() -> Unit = {}): Response {
        return getSync(HttpRequest(url).apply(init).build(), client())
    }

    internal fun getSync(request: Request, client: OkHttpClient): Response {
        return client.newCall(request).execute()
    }

    internal suspend fun executeWithRetry(request: Request, client: OkHttpClient): String {
        return executeChecked(request, client) { it.body.string() }
    }

    internal suspend fun executeRawWithRetry(request: Request, client: OkHttpClient): HttpRawResponse {
        return executeChecked(request, client) { HttpRawResponse(it.body.bytes(), it.headers, it.code) }
    }

    private suspend fun <T> executeChecked(request: Request, client: OkHttpClient, read: (Response) -> T): T {
        return withContext(Dispatchers.IO) {
            awaitResponse(request, client, 0).use { response ->
                if (HttpPolicy.shouldFail(response.code)) throw HttpException(response.code)
                read(response)
            }
        }
    }

    private suspend fun awaitResponse(request: Request, client: OkHttpClient, retryCount: Int): Response {
        return try {
            execute(request, client)
        } catch (e: SocketTimeoutException) {
            if (!HttpPolicy.shouldRetry(retryCount, e)) throw e
            awaitResponse(request, client, retryCount + 1)
        }
    }

    private suspend fun execute(request: Request, client: OkHttpClient): Response {
        val call = client.newCall(request)
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    cont.resume(response)
                }
            })
        }
    }

    private fun client(): OkHttpClient {
        return OkGoHelper.getDefaultClient() ?: throw IllegalStateException("default OkHttpClient not initialized")
    }
}

class HttpRequest internal constructor(private val url: String) {

    private val headers = LinkedHashMap<String, String>()
    private val params = LinkedHashMap<String, String>()

    fun headers(key: String, value: String) {
        headers[key] = value
    }

    fun headers(map: Map<String, String>) {
        headers.putAll(map)
    }

    fun params(key: String, value: String?) {
        if (value != null) params[key] = value
    }

    fun params(map: Map<String, String>?) {
        if (map == null) return
        for ((key, value) in map) {
            params(key, value)
        }
    }

    internal fun build(): Request {
        val builder = Request.Builder().url(HttpPolicy.buildUrl(url, params))
        val acceptLanguage = HttpPolicy.acceptLanguage()
        if (acceptLanguage.isNotEmpty()) builder.header(HEADER_ACCEPT_LANGUAGE, acceptLanguage)
        builder.header(HEADER_USER_AGENT, USER_AGENT)
        for ((key, value) in headers) {
            builder.header(key, value)
        }
        return builder.build()
    }

    private companion object {
        const val HEADER_ACCEPT_LANGUAGE = "Accept-Language"
        const val HEADER_USER_AGENT = "User-Agent"
        val USER_AGENT = "okhttp/" + OkHttp.VERSION
    }
}

class HttpException(val code: Int) : Exception("HTTP $code")

class HttpRawResponse internal constructor(val body: ByteArray, val headers: Headers, val code: Int)
