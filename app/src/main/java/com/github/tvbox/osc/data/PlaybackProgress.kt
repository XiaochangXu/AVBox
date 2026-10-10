package com.github.tvbox.osc.data

import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.util.HistoryHelper
import com.github.tvbox.osc.util.KV
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.greenrobot.eventbus.EventBus
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object PlaybackProgress {

    private const val KEY = "playback_progress"

    private const val LIMIT = WatchProgressIndex.MAX_TITLES

    private const val MIN_INTERVAL_MS = 5_000L

    private const val MIN_ADVANCE_MS = 1_000

    private const val MAX_STEP_MS = 10_000

    private var lastKey = ""

    private var lastSavedAt = 0L

    private var lastSavedPercent = -1

    private var sampleToken = ""

    private var lastPosition = -1

    private var advancedMs = 0

    private var watchedToken = ""

    private val writer: ExecutorService by lazy {
        Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "playback-progress") }
    }

    fun key(sourceKey: String?, vodId: String?): String =
        sourceKey.orEmpty() + "|" + vodId.orEmpty()

    fun snapshot(): Map<String, Int> {
        if (HistoryHelper.isIncognito()) return emptyMap()
        return read()
            .mapNotNull { (key, value) ->
                decodePercent(value)?.takeIf { it in 0..100 }?.let { key to it }
            }
            .toMap()
    }

    fun onEpisodeStart(vod: VodInfo?, scrollToTop: Boolean) {
        if (HistoryHelper.isIncognito()) return
        val id = currentKey(vod) ?: return
        val cleared = forget(id)
        val event = if (scrollToTop) {
            RefreshEvent(RefreshEvent.TYPE_HISTORY_REFRESH)
        } else {
            RefreshEvent(RefreshEvent.TYPE_HISTORY_REFRESH, java.lang.Boolean.FALSE)
        }
        if (cleared || !scrollToTop) EventBus.getDefault().post(event)
    }

    @JvmStatic
    fun onEpisodeStartNoScroll(vod: VodInfo?) = onEpisodeStart(vod, false)

    fun onProgress(vod: VodInfo?, positionMs: Int, durationMs: Int) {
        val id = currentKey(vod) ?: return
        WatchProgressStore.noteDuration(id, durationMs.toLong())
        markWatched(vod, positionMs, durationMs)
        if (WatchProgressRules.decide(positionMs.toLong(), durationMs.toLong()) == WatchDecision.SKIP) return
        val percent = calcPercent(positionMs, durationMs) ?: return
        val now = System.currentTimeMillis()
        if (id == lastKey && (percent == lastSavedPercent || now - lastSavedAt < MIN_INTERVAL_MS)) return
        lastKey = id
        lastSavedAt = now
        lastSavedPercent = percent
        writer.execute { write(id, percent, durationMs, now) }
    }

    fun flush(vod: VodInfo?, positionMs: Int, durationMs: Int): Boolean {
        val id = currentKey(vod) ?: return false
        markWatched(vod, positionMs, durationMs)
        watchedToken = ""
        sampleToken = ""
        advancedMs = 0
        if (WatchProgressRules.decide(positionMs.toLong(), durationMs.toLong()) == WatchDecision.SKIP) return false
        val percent = calcPercent(positionMs, durationMs) ?: return false
        if (id == lastKey && percent == lastSavedPercent) return false
        val now = System.currentTimeMillis()
        if (!write(id, percent, durationMs, now)) return false
        lastKey = id
        lastSavedAt = now
        lastSavedPercent = percent
        return true
    }

    fun markFinished(vod: VodInfo?) {
        val id = currentKey(vod) ?: return
        if (id == lastKey && lastSavedPercent == 100) return
        val now = System.currentTimeMillis()
        if (!write(id, 100, 0, now)) return
        lastKey = id
        lastSavedAt = now
        lastSavedPercent = 100
        EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_HISTORY_REFRESH))
    }

    fun stepAdvanceMs(positionMs: Int, lastPositionMs: Int): Int =
        (positionMs - lastPositionMs).takeIf { it in 1..MAX_STEP_MS } ?: 0

    fun shouldMarkWatched(advancedMs: Int, token: String, lastToken: String): Boolean =
        token != lastToken && advancedMs >= MIN_ADVANCE_MS

    private fun markWatched(vod: VodInfo?, positionMs: Int, durationMs: Int) {
        if (vod == null) return
        if (PlaybackPorts.isLiveMode?.invoke() == true) return
        val token = key(vod.sourceKey, vod.id) + "#" + vod.playFlag + "#" + vod.playIndex
        if (token != sampleToken) {
            sampleToken = token
            lastPosition = positionMs
            advancedMs = 0
            return
        }
        advancedMs += stepAdvanceMs(positionMs, lastPosition)
        lastPosition = positionMs
        if (!shouldMarkWatched(advancedMs, token, watchedToken)) return
        if (!WatchProgressRules.shouldRemember(positionMs.toLong(), durationMs.toLong())) return
        watchedToken = token
        EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_PLAYBACK_STARTED))
    }

    private fun calcPercent(positionMs: Int, durationMs: Int): Int? {
        if (durationMs <= 0) return null
        return (positionMs.toLong() * 100 / durationMs).toInt().coerceIn(0, 100)
    }

    private fun currentKey(vod: VodInfo?): String? {
        if (vod == null) return null
        if (vod.sourceKey.isNullOrEmpty() || vod.id.isNullOrEmpty()) return null
        return key(vod.sourceKey, vod.id)
    }

    internal fun encodeEntry(percent: Int, durationMs: Int, at: Long): String {
        val obj = JsonObject()
        obj.addProperty("p", percent)
        if (durationMs > 0) obj.addProperty("d", durationMs)
        obj.addProperty("t", at)
        return obj.toString()
    }

    internal fun decodePercent(raw: String?): Int? {
        if (raw.isNullOrEmpty()) return null
        raw.toIntOrNull()?.let { return it }
        return try {
            JsonParser.parseString(raw).asJsonObject.get("p")?.asInt
        } catch (e: Exception) {
            null
        }
    }

    internal fun decodeSavedAt(raw: String?): Long {
        if (raw.isNullOrEmpty()) return 0L
        if (raw.toIntOrNull() != null) return 0L
        return try {
            JsonParser.parseString(raw).asJsonObject.get("t")?.asLong ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    internal fun pickEvictions(entries: Map<String, String>, limit: Int): List<String> {
        if (entries.size <= limit) return emptyList()
        return entries.entries.sortedBy { decodeSavedAt(it.value) }.take(entries.size - limit).map { it.key }
    }

    @Synchronized
    private fun write(id: String, percent: Int, durationMs: Int, at: Long): Boolean {
        if (HistoryHelper.isIncognito()) return false
        val map = read()
        map[id] = encodeEntry(percent, durationMs, at)
        pickEvictions(map, LIMIT).forEach { map.remove(it) }
        return KV.put(KEY, map)
    }

    @Synchronized
    private fun remove(id: String): Boolean {
        val map = read()
        if (map.remove(id) == null) return false
        return KV.put(KEY, map)
    }

    fun retain(owners: Set<String>) {
        writer.execute { retainNow(owners) }
    }

    @Synchronized
    private fun retainNow(owners: Set<String>) {
        val map = read()
        val kept = HashMap<String, String>(map.size)
        for ((id, value) in map) {
            if (owners.contains(id)) kept[id] = value
        }
        if (kept.size == map.size) return
        KV.put(KEY, kept)
    }

    @Synchronized
    fun forget(id: String): Boolean = remove(id)

    @Synchronized
    fun forgetAll() {
        lastKey = ""
        lastSavedPercent = -1
        KV.delete(KEY)
    }

    private fun read(): HashMap<String, String> = KV.get(KEY, HashMap<String, String>())
}
