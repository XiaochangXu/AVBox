package com.github.tvbox.osc.data

import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.util.HistoryHelper
import com.github.tvbox.osc.util.KV

object EpisodeTotals {

    private const val KEY = "episode_totals"

    private const val LIMIT = WatchProgressIndex.MAX_TITLES

    private val INDEX_PATTERNS = listOf(
        Regex("^第?\\s*(\\d{1,4})\\s*[集期话話]?\\s*(?:\\.[a-z0-9]{1,5})?$"), // i18n: keep(R13:集数正则)
        Regex("^(?:ep|e|episode)\\.?\\s*(\\d{1,4})\\s*(?:\\.[a-z0-9]{1,5})?$", RegexOption.IGNORE_CASE),
    )

    fun key(sourceKey: String?, vodId: String?): String =
        sourceKey.orEmpty() + "|" + vodId.orEmpty()

    fun snapshot(): Map<String, Int> {
        if (HistoryHelper.isIncognito()) return emptyMap()
        return read()
            .mapNotNull { (key, value) -> value.toIntOrNull()?.takeIf { it > 1 }?.let { key to it } }
            .toMap()
    }

    fun episodeCount(episodes: List<String?>): Int? {
        val indexes = episodes.mapNotNull { episodeIndexOf(it) }.toSet()
        return if (indexes.size >= 2) indexes.size else null
    }

    fun isNumberedEpisode(name: String?): Boolean = episodeIndexOf(name) != null

    private fun episodeIndexOf(name: String?): Int? {
        val text = name?.trim().orEmpty()
        if (text.isEmpty()) return null
        INDEX_PATTERNS.forEach { pattern ->
            val match = pattern.find(text) ?: return@forEach
            val value = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }?.toIntOrNull()
            if (value != null) return value
        }
        return null
    }

    fun put(sourceKey: String?, vodId: String?, total: Int?) {
        if (sourceKey.isNullOrEmpty() || vodId.isNullOrEmpty()) return
        if (HistoryHelper.isIncognito()) return
        putInternal(sourceKey, vodId, total)
    }

    @Synchronized
    fun remove(owner: String) {
        val map = read()
        if (map.remove(owner) == null) return
        KV.put(KEY, map)
    }

    @Synchronized
    fun retain(owners: Set<String>) {
        val map = read()
        val kept = HashMap<String, String>(map.size)
        for ((id, value) in map) {
            if (owners.contains(id)) kept[id] = value
        }
        if (kept.size == map.size) return
        KV.put(KEY, kept)
    }

    @Synchronized
    fun removeAll() {
        KV.delete(KEY)
    }

    fun putFromVod(vod: VodInfo) {
        val list = vod.playFlag?.let { vod.seriesMap?.get(it) }
        put(vod.sourceKey, vod.id, list?.let { episodeCount(it.map { series -> series.name }) })
    }

    @Synchronized
    private fun putInternal(sourceKey: String, vodId: String, total: Int?) {
        val id = key(sourceKey, vodId)
        val map = read()
        if (total == null || total <= 1) {
            if (map.remove(id) == null) return
        } else {
            val value = total.toString()
            if (map[id] == value) return
            map[id] = value
        }
        if (map.size > LIMIT) map.keys.take(map.size - LIMIT).forEach { map.remove(it) }
        KV.put(KEY, map)
    }

    private fun read(): HashMap<String, String> = KV.get(KEY, HashMap<String, String>())
}
