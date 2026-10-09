package com.github.tvbox.osc.ui.page

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.base.App
import com.github.tvbox.osc.io.removeLocalCopy
import com.github.tvbox.osc.util.ApiLineSignal
import com.github.tvbox.osc.util.BootGuard
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.HistoryHelper
import com.github.tvbox.osc.util.KV
import com.github.tvbox.osc.util.LOG
import com.github.tvbox.osc.util.LanguageManager
import com.github.tvbox.osc.util.TmdbApi
import com.github.tvbox.osc.util.TmdbPoster
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SubscribeSource(val name: String, val url: String)

data class PendingSwitch(val item: SubscribeSource, val vod: Boolean)

enum class ConfigMode { Vod, Tmdb, Live }

enum class SubscribeMode { Vod, Live }

enum class TmdbPosterStyle {
    Fixed, Random, Roll;

    @StringRes
    fun labelRes(): Int = when (this) {
        Fixed -> R.string.tmdb_poster_style_fixed
        Random -> R.string.tmdb_poster_style_random
        Roll -> R.string.tmdb_poster_style_roll
    }

    companion object {
        fun of(value: Int): TmdbPosterStyle = entries.getOrElse(value) { Fixed }
    }
}

@Immutable
data class ConfigTmdbState(
    val enabled: Boolean,
    val posterStyle: TmdbPosterStyle,
    val apiKey: String,
    val apiBase: String,
    val imageBase: String,
    val apiBaseError: Boolean = false,
    val imageBaseError: Boolean = false,
)

@Immutable
data class ConfigTmdbActions(
    val setEnabled: (Boolean) -> Unit,
    val setPosterStyle: (TmdbPosterStyle) -> Unit,
    val setApiKey: (String) -> Unit,
    val setApiBase: (String) -> Unit,
    val setImageBase: (String) -> Unit,
    val commitApiBase: () -> Unit,
    val commitImageBase: () -> Unit,
    val testApi: () -> Unit,
    val testImage: () -> Unit,
)

enum class TmdbTest { None, Api, Image }

internal fun parseSubscribe(value: String): SubscribeSource {
    val index = value.indexOf(SUBSCRIBE_SPLIT)
    return if (index < 0) {
        SubscribeSource(value.trim(), value.trim())
    } else {
        SubscribeSource(
            value.substring(0, index).trim(),
            value.substring(index + SUBSCRIBE_SPLIT.length),
        )
    }
}

internal const val SUBSCRIBE_SPLIT = "\t"

internal fun vodSubscribes(): List<SubscribeSource> =
    KV.get(HawkConfig.SUBSCRIBE_LIST, ArrayList<String>()).map { parseSubscribe(it) }

class ConfigManageViewModel(
    private val tmdbClientFactory: () -> TmdbApi.TmdbClient,
) : ViewModel() {

    val vodItems = MutableStateFlow(loadSubscribes(SubscribeMode.Vod))
    val liveItems = MutableStateFlow(loadSubscribes(SubscribeMode.Live))
    val activeUrl = MutableStateFlow(KV.get(HawkConfig.API_URL, ""))
    val liveActiveUrl = MutableStateFlow(KV.get(HawkConfig.LIVE_API_URL, ""))
    val liveFollow = MutableStateFlow(ApiConfig.isLiveFollowVod())
    val disabledUrls = MutableStateFlow(BootGuard.disabledSources().toSet())
    val selected = MutableStateFlow(emptySet<String>())
    val manageMode = MutableStateFlow(false)
    val editTarget = MutableStateFlow<SubscribeSource?>(null)
    val pendingSwitch = MutableStateFlow<PendingSwitch?>(null)
    val toastEvent = MutableStateFlow<String?>(null)
    private val tmdbState = MutableStateFlow(
        ConfigTmdbState(
            enabled = KV.get(HawkConfig.TMDB_ENABLE, false),
            posterStyle = TmdbPosterStyle.of(KV.get(HawkConfig.TMDB_POSTER_STYLE, 0)),
            apiKey = KV.get(HawkConfig.TMDB_API_KEY, ""),
            apiBase = KV.get(HawkConfig.TMDB_API_BASE, ""),
            imageBase = KV.get(HawkConfig.TMDB_IMAGE_BASE, ""),
        ),
    )
    private val tmdbTesting = MutableStateFlow(TmdbTest.None)
    private val copyCleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        viewModelScope.launch {
            ApiLineSignal.version.collect { refreshActiveSnapshot() }
        }
        viewModelScope.launch {
            AppBootstrap.state.collect { boot ->
                if (boot is AppBootstrap.Boot.Ready) refreshActiveSnapshot()
            }
        }
    }

    fun tmdbState(): StateFlow<ConfigTmdbState> = tmdbState.asStateFlow()

    fun tmdbTesting(): StateFlow<TmdbTest> = tmdbTesting.asStateFlow()

    fun tmdbActions(): ConfigTmdbActions = ConfigTmdbActions(
        setEnabled = { value -> updateTmdbEnabled(value) },
        setPosterStyle = { style -> updateTmdbPosterStyle(style) },
        setApiKey = { value -> updateTmdbApiKey(value) },
        setApiBase = { value -> updateTmdbApiBase(value) },
        setImageBase = { value -> updateTmdbImageBase(value) },
        commitApiBase = { commitTmdbApiBase() },
        commitImageBase = { commitTmdbImageBase() },
        testApi = { testTmdbApi() },
        testImage = { testTmdbImage() },
    )

    private fun updateTmdbEnabled(enabled: Boolean) {
        KV.put(HawkConfig.TMDB_ENABLE, enabled)
        tmdbState.value = tmdbState.value.copy(enabled = enabled)
        TmdbPoster.notifyConfigChanged()
    }

    private fun updateTmdbPosterStyle(style: TmdbPosterStyle) {
        KV.put(HawkConfig.TMDB_POSTER_STYLE, style.ordinal)
        tmdbState.value = tmdbState.value.copy(posterStyle = style)
        TmdbPoster.notifyConfigChanged()
    }

    private fun updateTmdbApiKey(value: String) {
        KV.put(HawkConfig.TMDB_API_KEY, value)
        tmdbState.value = tmdbState.value.copy(apiKey = value)
        TmdbPoster.notifyConfigChanged()
    }

    private fun updateTmdbApiBase(value: String) {
        tmdbState.value = tmdbState.value.copy(apiBase = value, apiBaseError = false)
    }

    private fun updateTmdbImageBase(value: String) {
        tmdbState.value = tmdbState.value.copy(imageBase = value, imageBaseError = false)
    }

    private fun commitTmdbApiBase() {
        val normalized = TmdbApi.normalizeBaseUrl(tmdbState.value.apiBase)
        KV.put(HawkConfig.TMDB_API_BASE, normalized)
        tmdbState.value = tmdbState.value.copy(
            apiBase = normalized,
            apiBaseError = normalized.isNotEmpty() && !TmdbApi.isValidBaseUrl(normalized),
        )
        TmdbPoster.notifyConfigChanged()
    }

    private fun commitTmdbImageBase() {
        val normalized = TmdbApi.normalizeBaseUrl(tmdbState.value.imageBase)
        KV.put(HawkConfig.TMDB_IMAGE_BASE, normalized)
        tmdbState.value = tmdbState.value.copy(
            imageBase = normalized,
            imageBaseError = normalized.isNotEmpty() && !TmdbApi.isValidBaseUrl(normalized),
        )
        TmdbPoster.notifyConfigChanged()
    }

    private fun testTmdbApi() {
        if (!beginTmdbTest(TmdbTest.Api)) return
        tmdbState.value = tmdbState.value.copy(apiBaseError = false)
        val apiKey = tmdbState.value.apiKey.trim()
        if (apiKey.isEmpty()) {
            toastEvent.value = str(R.string.tmdb_key_required)
            endTmdbTest(TmdbTest.Api)
            return
        }
        viewModelScope.launch {
            val result = try {
                tmdbClientFactory().testApi(apiKey, tmdbState.value.apiBase)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LOG.e("TmdbApi", e)
                false
            }
            toastEvent.value = str(
                if (result) R.string.tmdb_test_api_ok else R.string.tmdb_test_api_fail,
            )
            endTmdbTest(TmdbTest.Api)
        }
    }

    private fun testTmdbImage() {
        if (!beginTmdbTest(TmdbTest.Image)) return
        tmdbState.value = tmdbState.value.copy(imageBaseError = false)
        val apiKey = tmdbState.value.apiKey.trim()
        if (apiKey.isEmpty()) {
            toastEvent.value = str(R.string.tmdb_key_required)
            endTmdbTest(TmdbTest.Image)
            return
        }
        viewModelScope.launch {
            val result = try {
                tmdbClientFactory().testImage(tmdbState.value.imageBase)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LOG.e("TmdbApi", e)
                false
            }
            toastEvent.value = str(
                if (result) R.string.tmdb_test_image_ok else R.string.tmdb_test_image_fail,
            )
            endTmdbTest(TmdbTest.Image)
        }
    }

    private fun beginTmdbTest(test: TmdbTest): Boolean {
        if (tmdbTesting.value != TmdbTest.None) return false
        tmdbTesting.value = test
        return true
    }

    private fun endTmdbTest(test: TmdbTest) {
        if (tmdbTesting.value == test) tmdbTesting.value = TmdbTest.None
    }

    private fun str(resId: Int, vararg args: Any): String {
        val app = App.getInstance() ?: return ""
        return LanguageManager.localized(app).getString(resId, *args)
    }

    fun clearToast() {
        toastEvent.value = null
    }

    fun cancelPendingSwitch() {
        pendingSwitch.value = null
    }

    private fun refreshActiveSnapshot() {
        activeUrl.value = KV.get(HawkConfig.API_URL, "")
        liveActiveUrl.value = KV.get(HawkConfig.LIVE_API_URL, "")
        liveFollow.value = ApiConfig.isLiveFollowVod()
    }

    fun onModeChanged() {
        manageMode.value = false
        selected.value = emptySet()
        editTarget.value = null
    }

    fun exitManageMode() {
        onModeChanged()
    }

    fun toggleSelected(value: String) {
        val cur = selected.value
        setSelected(if (value in cur) cur - value else cur + value)
    }

    fun longPressSelect(value: String) {
        selected.value = setOf(value)
        manageMode.value = true
    }

    private fun setSelected(next: Set<String>) {
        selected.value = next
        if (next.isEmpty()) manageMode.value = false
    }

    private fun isInUse(url: String, vod: Boolean): Boolean =
        if (vod) {
            url == activeUrl.value || HistoryHelper.isApiLineSourceOf(url, activeUrl.value)
        } else {
            (!liveFollow.value && url == liveActiveUrl.value) ||
                (!liveFollow.value && HistoryHelper.isLiveApiLineSourceOf(url, liveActiveUrl.value))
        }

    private fun activeInEitherMode(url: String): Boolean {
        val vodApi = KV.get(HawkConfig.API_URL, "")
        val liveApi = KV.get(HawkConfig.LIVE_API_URL, "")
        return url == vodApi || url == liveApi ||
            HistoryHelper.isApiLineSourceOf(url, vodApi) ||
            HistoryHelper.isLiveApiLineSourceOf(url, liveApi)
    }

    private fun referencedBySubscribes(url: String): Boolean =
        loadSubscribes(SubscribeMode.Vod).any { parseSubscribe(it).url == url } ||
            loadSubscribes(SubscribeMode.Live).any { parseSubscribe(it).url == url }

    private fun referencedByRepo(url: String): Boolean =
        (HistoryHelper.getApiLines().orEmpty() + HistoryHelper.getLiveApiLines().orEmpty())
            .any { HistoryHelper.getApiLineUrl(it) == url }

    private fun switchToVod(item: SubscribeSource) {
        if (activeUrl.value == item.url) return
        val followLive = applyVodSource(item)
        activeUrl.value = item.url
        if (followLive) {
            liveActiveUrl.value = ""
            liveFollow.value = true
        }
    }

    private fun switchToLive(item: SubscribeSource) {
        if (!liveFollow.value && liveActiveUrl.value == item.url) return
        applyLiveSource(item)
        liveActiveUrl.value = item.url
        liveFollow.value = false
    }

    fun requestSwitch(item: SubscribeSource, vod: Boolean) {
        if (item.url in disabledUrls.value) {
            pendingSwitch.value = PendingSwitch(item, vod)
        } else if (vod) {
            switchToVod(item)
        } else {
            switchToLive(item)
        }
    }

    fun enableAndSwitch() {
        val pending = pendingSwitch.value ?: return
        pendingSwitch.value = null
        BootGuard.enableSource(pending.item.url)
        disabledUrls.value = disabledUrls.value - pending.item.url
        if (pending.vod) switchToVod(pending.item) else switchToLive(pending.item)
    }

    fun followLiveNow() {
        applyLiveFollowVod()
        liveActiveUrl.value = ""
        liveFollow.value = true
        toastEvent.value = str(R.string.toast_live_follow_vod)
    }

    fun deleteSelected(mode: SubscribeMode) {
        val vod = mode == SubscribeMode.Vod
        val items = if (vod) vodItems.value else liveItems.value
        val target = selected.value.filterNot { isInUse(parseSubscribe(it).url, vod) }
        if (target.size != selected.value.size) {
            toastEvent.value = str(R.string.toast_source_in_use)
        }
        val remaining = items.filterNot { it in target }
        KV.put(subscribeKeyOf(mode), ArrayList(remaining))
        val removedUrls = target.map { parseSubscribe(it).url }
        BootGuard.forgetSources(removedUrls)
        disabledUrls.value = disabledUrls.value - removedUrls
        val copyUrls = removedUrls.filterNot {
            activeInEitherMode(it) || referencedBySubscribes(it) || referencedByRepo(it)
        }
        if (copyUrls.isNotEmpty()) {
            copyCleanupScope.launch { copyUrls.forEach { removeLocalCopy(it) } }
        }
        if (vod) {
            vodItems.value = remaining
            if (remaining.isEmpty()) {
                ApiConfig.get().clearVodConfig()
                activeUrl.value = ""
                AppBootstrap.retry()
            }
        } else {
            liveItems.value = remaining
            if (remaining.isEmpty()) {
                applyLiveFollowVod()
                liveActiveUrl.value = ""
                liveFollow.value = true
            }
        }
        setSelected(emptySet())
    }

    fun commitAdd(vod: Boolean, name: String, url: String) {
        val mode = if (vod) SubscribeMode.Vod else SubscribeMode.Live
        val newItems = saveSubscribe(mode, name, url)
        if (vod) vodItems.value = newItems else liveItems.value = newItems
        if (newItems.size == 1) {
            val item = parseSubscribe(newItems.first())
            if (vod) switchToVod(item) else switchToLive(item)
        }
    }

    fun commitEdit(vod: Boolean, target: SubscribeSource, name: String, url: String) {
        if (url.isEmpty()) return
        val mode = if (vod) SubscribeMode.Vod else SubscribeMode.Live
        val newValue = (name.ifEmpty { url }) + SUBSCRIBE_SPLIT + url
        val oldValue = selected.value.firstOrNull { parseSubscribe(it).url == target.url }
        val updated = updateSubscribe(mode, target, name, url)
        if (vod) vodItems.value = updated else liveItems.value = updated
        if (oldValue != null) selected.value = selected.value - oldValue + newValue
        editTarget.value = null
        val item = parseSubscribe(newValue)
        if (vod) {
            if (target.url == activeUrl.value && url != activeUrl.value) switchToVod(item)
        } else if (!liveFollow.value && target.url == liveActiveUrl.value && url != liveActiveUrl.value) {
            switchToLive(item)
        }
    }

    private fun applyVodSource(item: SubscribeSource): Boolean =
        AppBootstrap.switchVodSubscription(item.url)

    private fun applyLiveSource(item: SubscribeSource) {
        HistoryHelper.setLiveApiHistory(item.url)
        KV.put(HawkConfig.LIVE_API_URL, item.url)
        if (!HistoryHelper.isLiveApiLineHistory(item.url)) HistoryHelper.clearLiveApiLineList()
        ApiConfig.get().clearLiveHosts()
        ApiConfig.get().invalidateLiveConfig()
    }

    private fun applyLiveFollowVod() {
        KV.put(HawkConfig.LIVE_API_URL, "")
        HistoryHelper.clearLiveApiLineList()
        ApiConfig.get().clearLiveHosts()
        ApiConfig.get().invalidateLiveConfig()
    }

    private fun subscribeKeyOf(mode: SubscribeMode): String = when (mode) {
        SubscribeMode.Vod -> HawkConfig.SUBSCRIBE_LIST
        SubscribeMode.Live -> HawkConfig.LIVE_SUBSCRIBE_LIST
    }

    private fun loadSubscribes(mode: SubscribeMode): List<String> =
        KV.get(subscribeKeyOf(mode), ArrayList<String>()).toList()

    private fun saveSubscribe(mode: SubscribeMode, name: String, url: String): List<String> {
        val value = (name.ifEmpty { url }) + SUBSCRIBE_SPLIT + url
        val list = ArrayList(loadSubscribes(mode))
        val existIndex = list.indexOfFirst { parseSubscribe(it).url == url }
        if (existIndex >= 0) list[existIndex] = value else list.add(value)
        KV.put(subscribeKeyOf(mode), list)
        return list
    }

    private fun updateSubscribe(mode: SubscribeMode, original: SubscribeSource, name: String, url: String): List<String> {
        val value = (name.ifEmpty { url }) + SUBSCRIBE_SPLIT + url
        val list = ArrayList(loadSubscribes(mode))
        val index = list.indexOfFirst { parseSubscribe(it).url == original.url }
        if (index < 0) return list
        list[index] = value
        val dupIndex = list.indexOfFirst { it != value && parseSubscribe(it).url == url }
        if (dupIndex >= 0) list.removeAt(dupIndex)
        KV.put(subscribeKeyOf(mode), list)
        return list
    }
}
