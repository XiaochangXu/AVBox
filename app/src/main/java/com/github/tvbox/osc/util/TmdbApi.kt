package com.github.tvbox.osc.util

import com.github.tvbox.osc.util.net.Http
import com.github.tvbox.osc.util.net.HttpException
import com.github.tvbox.osc.util.net.HttpRequest
import com.google.gson.JsonObject
import com.google.gson.JsonParser

import java.net.SocketTimeoutException
import java.util.Locale

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object TmdbApi {

    const val SOURCE_KEY = "tmdb"

    const val HTTP_TOO_MANY_REQUESTS = 429

    private const val API_PATH = "3"
    private const val CONFIGURATION_PATH = "configuration"
    private const val SEARCH_PATH = "search/multi"
    private const val IMAGES_PATH = "images"
    const val MEDIA_TV = "tv"

    private const val MEDIA_MOVIE = "movie"
    private const val TEST_POSTER_PATH = "/1E5baAaEse26fej7uHcjOgEE2t2.jpg"
    private const val TEST_POSTER_SIZE = "w92"
    private const val POSTER_SIZE_SMALL = "w342"
    private const val POSTER_SIZE_LARGE = "w780"
    private const val IMAGE_CONTENT_TYPE_PREFIX = "image/"
    private const val BEARER_PREFIX = "Bearer "
    private const val BEARER_KEY_PREFIX = "eyJ"
    private const val IMAGES_FIELD = "\"images\""
    private const val RESULTS_KEY = "results"
    private const val POSTERS_KEY = "posters"
    private const val BACKDROPS_KEY = "backdrops"
    private const val CREDITS_KEY = "credits"
    private const val SEASONS_KEY = "seasons"
    private const val EPISODES_KEY = "episodes"
    private const val SEASON_NUMBER_FIELD = "season_number"
    private const val EPISODE_COUNT_FIELD = "episode_count"
    private const val STILL_SIZE = "w300"
    private const val ALTERNATIVE_TITLES_PATH = "alternative_titles"
    private const val ALT_TITLES_KEY_MOVIE = "titles"
    private const val ALT_TITLES_KEY_TV = "results"
    private const val ALT_TITLE_FIELD = "title"
    private const val CAST_KEY = "cast"
    private const val GENRES_KEY = "genres"
    private const val PROFILE_SIZE = "w185"
    private const val MAX_CAST_MEMBERS = 12
    private const val HTTP_SUCCESS_MIN = 200
    private const val HTTP_SUCCESS_MAX = 299
    private const val MAX_HERO_POSTERS = 10
    private const val MAX_MOVIE_STILLS = 10
    private const val LANGUAGE_EN = "en-US"
    private const val LANGUAGE_ZH_SIMPLIFIED = "zh-CN"
    private const val LANGUAGE_ZH_TRADITIONAL = "zh-TW"
    private const val CHINESE_LANGUAGE = "zh"

    private const val FULL_WIDTH_SPACE = 0x3000
    private const val FULL_WIDTH_START = 0xFF01
    private const val FULL_WIDTH_END = 0xFF5E
    private const val FULL_WIDTH_OFFSET = 0xFEE0

    private val TRADITIONAL_REGIONS = setOf("TW", "HK", "MO")

    private val MATCH_PUNCTUATION = Regex("""[\s！？!?，,。、；;：:·～~…—－“”‘’"'（）()\[\]【】《》〈〉]+""") // i18n: keep(片名匹配词表)

    private val TITLE_INVISIBLE = Regex("[\u200B-\u200F\u2060\uFEFF\u00AD]") // i18n: keep(片名清洗词表)

    private val TITLE_TRAILING_LABEL = Regex(
        """\s*(?:更新至\d+集|连载至\d+集|杜比视界|杜比全景声|全景声|中英双字|国语版|粤语版|台版|港版|日版|美版|英版|韩版|泰版|未删减|无删减|删减版|修复版|加长版|导演剪辑版|完结|国语|粤语|英语|中字|双语|原声|高清|超清|蓝光|真彩|高码|无水印|HDR|2160P|1080I|1080P|720P|4K|8K|HD|H\.265|H265|HEVC|X265|X264|60帧|120帧|60FPS|120FPS|杜比)$""", // i18n: keep(片名清洗词表)
        RegexOption.IGNORE_CASE,
    )

    private val TITLE_BRACKETS = Regex("""[\[【][^\]】]*[\]】]""")

    private val TITLE_LABEL_PARENS = Regex(
        """[（(]\s*(?:\d{4}|国语版|粤语版|台版|港版|日版|美版|韩版|未删减|无删减|删减版|修复版|导演剪辑版|加长版|杜比视界|杜比|全景声|真彩|高码|无水印|国语|粤语|英语|中字|双语|高清|蓝光|全集|完结|抢先版|HDR|4K|8K|HD|BD|TS|TC|DVD|WEB-?DL|BluRay|REMUX|H\.?265|X265|X264|HEVC)\s*[)）]""", // i18n: keep(片名清洗词表)
        RegexOption.IGNORE_CASE,
    )

    private val TITLE_SEASON = Regex("""第[一二三四五六七八九十百零两0-9]+[季部]""") // i18n: keep(片名清洗词表)

    private val TITLE_RECAP_SECTION = Regex(
        """(?:\s|：|:)\s*(?:深度解读|精彩解说|剧情揭秘|剧情解析|影视解说|深度解析|全集解说|速看|盘点|杂谈|解说|解读|揭秘).*$""",
    ) // i18n: keep(片名清洗词表)

    private val TITLE_RECAP_TAIL = Regex(
        """(?:之)?(?:精彩|深度|剧情|全集)?(?:解说|解读|杂谈|揭秘|盘点|速看)$""",
    ) // i18n: keep(片名清洗词表)

    private val SEASON_HINT_ARABIC = Regex("""第\s*([0-9]+)\s*季""") // i18n: keep(季号解析词表)

    private val SEASON_HINT_EN = Regex("""season\s*([0-9]+)""", RegexOption.IGNORE_CASE)

    private val SEASON_HINT_SHORT = Regex("""\bS([0-9]{1,2})\b""", RegexOption.IGNORE_CASE)

    private val SEASON_HINT_CN = Regex("""第\s*([一二三四五六七八九十]+)\s*季""") // i18n: keep(季号解析词表)

    private val CN_DIGIT_VALUES = mapOf(
        '一' to 1,
        '二' to 2,
        '三' to 3,
        '四' to 4,
        '五' to 5,
        '六' to 6,
        '七' to 7,
        '八' to 8,
        '九' to 9,
    )

    interface TmdbClient {
        suspend fun testApi(apiKey: String, apiBaseUrl: String): Boolean
        suspend fun testImage(imageBaseUrl: String): Boolean
        suspend fun search(apiKey: String, apiBaseUrl: String, query: String): List<TmdbSearchHit>
        suspend fun images(apiKey: String, apiBaseUrl: String, mediaType: String, id: Int): List<String>
        suspend fun stills(apiKey: String, apiBaseUrl: String, mediaType: String, id: Int): List<String>
        suspend fun detail(apiKey: String, apiBaseUrl: String, mediaType: String, id: Int): TmdbDetail?
        suspend fun person(apiKey: String, apiBaseUrl: String, id: Int): TmdbPerson?
        suspend fun alternativeTitles(apiKey: String, apiBaseUrl: String, mediaType: String, id: Int): List<String>
        suspend fun season(apiKey: String, apiBaseUrl: String, tvId: Int, seasonNumber: Int): List<TmdbEpisode>
    }

    data class TmdbSearchHit(
        val id: Int,
        val mediaType: String,
        val title: String,
        val originalTitle: String,
        val year: Int,
        val posterPath: String,
        val originalLanguage: String? = null,
    )

    data class TmdbCastMember(
        val id: Int,
        val name: String,
        val character: String,
        val profilePath: String,
    )

    data class TmdbDetail(
        val overview: String,
        val rating: Double,
        val genres: List<String>,
        val cast: List<TmdbCastMember>,
        val seasons: List<TmdbSeason>,
    )

    data class TmdbSeason(
        val seasonNumber: Int,
        val episodeCount: Int,
    )

    data class TmdbEpisode(
        val name: String,
        val stillPath: String,
    )

    data class TmdbEpisodeRef(
        val seasonNumber: Int,
        val episodeNumber: Int,
    )

    data class TmdbPerson(
        val name: String,
        val biography: String,
        val birthday: String,
        val placeOfBirth: String,
        val profilePath: String,
    )

    private data class PosterEntry(val path: String, val vote: Double)

    fun normalizeBaseUrl(raw: String): String = raw.trim().trimEnd('/')

    fun isValidBaseUrl(value: String): Boolean {
        if (!value.startsWith("http://") && !value.startsWith("https://")) return false
        return value.toHttpUrlOrNull() != null
    }

    fun apiBaseUrl(configured: String): String =
        normalizeBaseUrl(configured).ifEmpty { HawkConfig.TMDB_API_BASE_DEFAULT }

    fun imageBaseUrl(configured: String): String =
        normalizeBaseUrl(configured).ifEmpty { HawkConfig.TMDB_IMAGE_BASE_DEFAULT }

    fun isBearerKey(apiKey: String): Boolean = apiKey.startsWith(BEARER_KEY_PREFIX)

    fun imageUrl(configuredImageBase: String, posterPath: String, large: Boolean): String =
        imageBaseUrl(configuredImageBase) + "/" + (if (large) POSTER_SIZE_LARGE else POSTER_SIZE_SMALL) + posterPath

    fun profileUrl(configuredImageBase: String, profilePath: String): String =
        imageBaseUrl(configuredImageBase) + "/" + PROFILE_SIZE + profilePath

    fun stillUrl(configuredImageBase: String, stillPath: String): String =
        imageBaseUrl(configuredImageBase) + "/" + STILL_SIZE + stillPath

    fun cleanTitle(raw: String?): String {
        var text = raw?.trim().orEmpty()
        if (text.isEmpty()) return text
        text = text.substringBefore('/').trim()
        text = TITLE_INVISIBLE.replace(text, "")
        text = TITLE_BRACKETS.replace(text, "")
        text = TITLE_LABEL_PARENS.replace(text, "")
        text = TITLE_SEASON.replace(text, "")
        text = TITLE_RECAP_SECTION.replace(text, "")
        while (true) {
            val stripped = TITLE_TRAILING_LABEL.replace(text, "")
            if (stripped == text || stripped.isEmpty()) break
            text = stripped
        }
        text = TITLE_RECAP_TAIL.replace(text, "")
        return text.trim()
    }

    fun normalizeForMatch(raw: String?): String {
        val cleaned = cleanTitle(toHalfWidth(raw?.trim().orEmpty()))
        if (cleaned.isEmpty()) return ""
        // 站点“仙逆剧场弑仙之战” vs TMDB“仙逆剧场版：弑仙之战”：折叠“剧场版→剧场”对齐两侧
        val folded = cleaned.replace("剧场版", "剧场")
        return MATCH_PUNCTUATION.replace(folded.lowercase(Locale.ROOT), "")
    }

    internal fun toHalfWidth(text: String): String {
        if (text.isEmpty()) return text
        val builder = StringBuilder(text.length)
        for (char in text) {
            val code = char.code
            when {
                code == FULL_WIDTH_SPACE -> builder.append(' ')
                code in FULL_WIDTH_START..FULL_WIDTH_END -> builder.append((code - FULL_WIDTH_OFFSET).toChar())
                else -> builder.append(char)
            }
        }
        return builder.toString()
    }

    fun searchQuery(raw: String?): String {
        val cleaned = cleanTitle(raw)
        return cleaned.ifEmpty { raw?.trim().orEmpty() }
    }

    fun pickBest(hits: List<TmdbSearchHit>, name: String?, year: Int): TmdbSearchHit? {
        val target = normalizeForMatch(name)
        if (target.isEmpty()) return null
        val matched = hits.filter { hit ->
            hit.posterPath.isNotEmpty() &&
                (normalizeForMatch(hit.title) == target || normalizeForMatch(hit.originalTitle) == target)
        }
        if (matched.isEmpty()) return null
        // 完全相等候选内决胜：年份距离 → 站点无年份取最早原版 → 中文原声优先 → id 稳定
        return matched.minWithOrNull(
            compareBy(
                { if (year > 0) kotlin.math.abs(it.year - year) else 0 },
                { if (year > 0) 0 else if (it.year > 0) it.year else Int.MAX_VALUE },
                { if (it.originalLanguage == "zh") 0 else 1 },
                { it.id },
            ),
        )
    }

    fun parseSearchHits(json: String): List<TmdbSearchHit> {
        val root = parseJsonObject(json) ?: return emptyList()
        val results = root.arrayOrNull(RESULTS_KEY) ?: return emptyList()
        return results.mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val item = element.asJsonObject
            val mediaType = item.stringOrNull("media_type").orEmpty()
            if (mediaType != MEDIA_MOVIE && mediaType != MEDIA_TV) return@mapNotNull null
            val id = item.intOrNull("id") ?: return@mapNotNull null
            val isTv = mediaType == MEDIA_TV
            TmdbSearchHit(
                id = id,
                mediaType = mediaType,
                title = item.stringOrNull(if (isTv) "name" else "title").orEmpty(),
                originalTitle = item.stringOrNull(if (isTv) "original_name" else "original_title").orEmpty(),
                year = item.stringOrNull(if (isTv) "first_air_date" else "release_date")
                    .orEmpty()
                    .take(4)
                    .toIntOrNull() ?: 0,
                posterPath = item.stringOrNull("poster_path").orEmpty(),
                originalLanguage = item.stringOrNull("original_language"),
            )
        }
    }

    fun parseDetail(json: String): TmdbDetail? {
        val root = parseJsonObject(json) ?: return null
        return TmdbDetail(
            overview = root.stringOrNull("overview").orEmpty(),
            rating = root.doubleOrNull("vote_average") ?: 0.0,
            genres = root.arrayOrNull(GENRES_KEY)?.mapNotNull { element ->
                if (!element.isJsonObject) null else element.asJsonObject.stringOrNull("name")
            }.orEmpty(),
            cast = parseCast(root),
            seasons = root.arrayOrNull(SEASONS_KEY)?.mapNotNull { element ->
                if (!element.isJsonObject) return@mapNotNull null
                val item = element.asJsonObject
                val number = item.intOrNull(SEASON_NUMBER_FIELD) ?: return@mapNotNull null
                TmdbSeason(number, item.intOrNull(EPISODE_COUNT_FIELD) ?: 0)
            }.orEmpty(),
        )
    }

    fun parseSeasonEpisodes(json: String): List<TmdbEpisode> {
        val root = parseJsonObject(json) ?: return emptyList()
        val entries = root.arrayOrNull(EPISODES_KEY) ?: return emptyList()
        return entries.mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val item = element.asJsonObject
            val number = item.intOrNull("episode_number") ?: return@mapNotNull null
            Pair(
                TmdbEpisode(
                    name = item.stringOrNull("name").orEmpty(),
                    stillPath = item.stringOrNull("still_path").orEmpty(),
                ),
                number,
            )
        }.sortedBy { it.second }.map { it.first }
    }

    fun mapEpisodes(seasons: List<TmdbSeason>, episodeCount: Int, seasonHint: Int?): List<TmdbEpisodeRef>? {
        if (episodeCount <= 0) return null
        val ordered = seasons.filter { it.seasonNumber > 0 && it.episodeCount > 0 }.sortedBy { it.seasonNumber }
        if (ordered.isEmpty()) return null
        if (seasonHint != null) {
            val hinted = ordered.firstOrNull { it.seasonNumber == seasonHint && it.episodeCount == episodeCount }
            if (hinted != null) return singleSeasonRefs(hinted, episodeCount)
        }
        val exact = ordered.filter { it.episodeCount == episodeCount }
        if (exact.size == 1) return singleSeasonRefs(exact[0], episodeCount)
        // 单季且站点集数少于该季（更新中/缺集）：按序取前 N 集；带季号提示时不放宽（防错位）
        if (seasonHint == null && ordered.size == 1 && episodeCount < ordered[0].episodeCount) {
            return singleSeasonRefs(ordered[0], episodeCount)
        }
        if (ordered.sumOf { it.episodeCount } == episodeCount) {
            val refs = ArrayList<TmdbEpisodeRef>(episodeCount)
            for (season in ordered) {
                for (episode in 1..season.episodeCount) refs.add(TmdbEpisodeRef(season.seasonNumber, episode))
            }
            return refs
        }
        // 站点集数 ≤ 正片+特别篇(season 0)总和：把特别篇按 TMDB 顺序（在前）并入，缺集时按序取前 N 集
        if (seasonHint == null) {
            val specials = seasons.filter { it.seasonNumber == 0 && it.episodeCount > 0 }
            val specialsTotal = specials.sumOf { it.episodeCount }
            if (specialsTotal > 0 && episodeCount <= specialsTotal + ordered.sumOf { it.episodeCount }) {
                val refs = ArrayList<TmdbEpisodeRef>(episodeCount)
                var filled = 0
                for (season in specials + ordered) {
                    for (episode in 1..season.episodeCount) {
                        if (filled >= episodeCount) return refs
                        refs.add(TmdbEpisodeRef(season.seasonNumber, episode))
                        filled++
                    }
                }
                return refs
            }
        }
        return null
    }

    fun parseSeasonHint(raw: String?): Int? {
        val text = toHalfWidth(raw?.trim().orEmpty())
        if (text.isEmpty()) return null
        SEASON_HINT_ARABIC.find(text)?.let { return it.groupValues[1].toIntOrNull() }
        SEASON_HINT_EN.find(text)?.let { return it.groupValues[1].toIntOrNull() }
        SEASON_HINT_SHORT.find(text)?.let { return it.groupValues[1].toIntOrNull() }
        SEASON_HINT_CN.find(text)?.let { return cnNumberToInt(it.groupValues[1]) }
        return null
    }

    internal fun cnNumberToInt(text: String): Int? {
        if (text.isEmpty()) return null
        if (text == "十") return 10
        val tenIndex = text.indexOf('十')
        if (tenIndex >= 0) {
            val tens = if (tenIndex == 0) 1 else CN_DIGIT_VALUES[text[0]] ?: return null
            val ones = if (tenIndex == text.length - 1) 0 else CN_DIGIT_VALUES[text[tenIndex + 1]] ?: return null
            return tens * 10 + ones
        }
        return CN_DIGIT_VALUES[text[0]]
    }

    private fun singleSeasonRefs(season: TmdbSeason, episodeCount: Int): List<TmdbEpisodeRef> =
        (1..episodeCount).map { TmdbEpisodeRef(season.seasonNumber, it) }

    private fun parseCast(root: JsonObject): List<TmdbCastMember> {
        val entries = root.get(CREDITS_KEY)
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.arrayOrNull(CAST_KEY)
            ?: return emptyList()
        return entries.mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val item = element.asJsonObject
            val profile = item.stringOrNull("profile_path") ?: return@mapNotNull null
            Pair(
                TmdbCastMember(
                    id = item.intOrNull("id") ?: 0,
                    name = item.stringOrNull("name").orEmpty(),
                    character = item.stringOrNull("character").orEmpty(),
                    profilePath = profile,
                ),
                item.intOrNull("order") ?: Int.MAX_VALUE,
            )
        }.sortedBy { it.second }.map { it.first }.take(MAX_CAST_MEMBERS)
    }

    fun parseAlternativeTitles(json: String): List<String> {
        val root = parseJsonObject(json) ?: return emptyList()
        val entries = root.arrayOrNull(ALT_TITLES_KEY_MOVIE)
            ?: root.arrayOrNull(ALT_TITLES_KEY_TV)
            ?: return emptyList()
        return entries.mapNotNull { element ->
            if (!element.isJsonObject) null else element.asJsonObject.stringOrNull(ALT_TITLE_FIELD)
        }
    }

    fun parsePerson(json: String): TmdbPerson? {
        val root = parseJsonObject(json) ?: return null
        return TmdbPerson(
            name = root.stringOrNull("name").orEmpty(),
            biography = root.stringOrNull("biography").orEmpty(),
            birthday = root.stringOrNull("birthday").orEmpty(),
            placeOfBirth = root.stringOrNull("place_of_birth").orEmpty(),
            profilePath = root.stringOrNull("profile_path").orEmpty(),
        )
    }

    fun parseImagePaths(json: String): List<String> {
        val root = parseJsonObject(json) ?: return emptyList()
        val posters = root.arrayOrNull(POSTERS_KEY) ?: return emptyList()
        return posters.mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val item = element.asJsonObject
            val path = item.stringOrNull("file_path") ?: return@mapNotNull null
            PosterEntry(path, item.doubleOrNull("vote_average") ?: 0.0)
        }.sortedByDescending { it.vote }
            .map { it.path }
            .take(MAX_HERO_POSTERS)
    }

    fun parseBackdropPaths(json: String): List<String> {
        val root = parseJsonObject(json) ?: return emptyList()
        val backdrops = root.arrayOrNull(BACKDROPS_KEY) ?: return emptyList()
        val textless = ArrayList<PosterEntry>()
        val labeled = ArrayList<PosterEntry>()
        for (element in backdrops) {
            if (!element.isJsonObject) continue
            val item = element.asJsonObject
            val path = item.stringOrNull("file_path") ?: continue
            val entry = PosterEntry(path, item.doubleOrNull("vote_average") ?: 0.0)
            if (item.stringOrNull("iso_639_1") == null) textless.add(entry) else labeled.add(entry)
        }
        return (textless.sortedByDescending { it.vote } + labeled.sortedByDescending { it.vote })
            .map { it.path }
            .take(MAX_MOVIE_STILLS)
    }

    private fun parseJsonObject(json: String): JsonObject? {
        return try {
            val parsed = JsonParser.parseString(json)
            if (parsed.isJsonObject) parsed.asJsonObject else null
        } catch (e: Exception) {
            LOG.e("TmdbApi", e)
            null
        }
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotEmpty() && it != "null" }

    private fun JsonObject.intOrNull(key: String): Int? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt

    private fun JsonObject.doubleOrNull(key: String): Double? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble

    private fun JsonObject.arrayOrNull(key: String) =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun authInit(apiKey: String, extra: HttpRequest.() -> Unit = {}): HttpRequest.() -> Unit = {
        if (isBearerKey(apiKey)) {
            headers("Authorization", BEARER_PREFIX + apiKey)
        } else {
            params("api_key", apiKey)
        }
        extra()
    }

    private fun searchLanguage(): String {
        val locale = LanguageManager.resolve()
        if (!locale.language.equals(CHINESE_LANGUAGE, ignoreCase = true)) return LANGUAGE_EN
        return if (locale.country.uppercase(Locale.ROOT) in TRADITIONAL_REGIONS) {
            LANGUAGE_ZH_TRADITIONAL
        } else {
            LANGUAGE_ZH_SIMPLIFIED
        }
    }

    class DefaultTmdbClient : TmdbClient {

        override suspend fun testApi(apiKey: String, apiBaseUrl: String): Boolean {
            val url = apiBaseUrl(apiBaseUrl) + "/" + API_PATH + "/" + CONFIGURATION_PATH
            return try {
                Http.get(url, authInit(apiKey)).contains(IMAGES_FIELD)
            } catch (e: SocketTimeoutException) {
                LOG.e("TmdbApi", e)
                false
            } catch (e: HttpException) {
                LOG.e("TmdbApi", e)
                false
            } catch (e: Exception) {
                LOG.e("TmdbApi", e)
                false
            }
        }

        override suspend fun testImage(imageBaseUrl: String): Boolean {
            val url = imageBaseUrl(imageBaseUrl) + "/" + TEST_POSTER_SIZE + TEST_POSTER_PATH
            return try {
                val response = Http.getRaw(url)
                response.headers["Content-Type"]?.startsWith(IMAGE_CONTENT_TYPE_PREFIX) == true
            } catch (e: SocketTimeoutException) {
                LOG.e("TmdbApi", e)
                false
            } catch (e: HttpException) {
                LOG.e("TmdbApi", e)
                false
            } catch (e: Exception) {
                LOG.e("TmdbApi", e)
                false
            }
        }

        override suspend fun search(apiKey: String, apiBaseUrl: String, query: String): List<TmdbSearchHit> {
            val url = apiBaseUrl(apiBaseUrl) + "/" + API_PATH + "/" + SEARCH_PATH
            val response = Http.getRaw(url, authInit(apiKey) {
                params("query", query)
                params("language", searchLanguage())
                params("include_adult", "false")
            })
            val text = String(response.body, Charsets.UTF_8)
            if (response.code !in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX) {
                LOG.i("echo-tmdb http path=search code=${response.code} body=${text.take(180)}")
                throw HttpException(response.code)
            }
            val hits = parseSearchHits(text)
            if (hits.isEmpty()) LOG.i("echo-tmdb empty path=search q=$query body=${text.take(180)}")
            return hits
        }

        override suspend fun images(apiKey: String, apiBaseUrl: String, mediaType: String, id: Int): List<String> {
            val url = apiBaseUrl(apiBaseUrl) + "/" + API_PATH + "/" + mediaType + "/" + id + "/" + IMAGES_PATH
            val response = Http.getRaw(url, authInit(apiKey))
            val text = String(response.body, Charsets.UTF_8)
            if (response.code !in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX) {
                LOG.i("echo-tmdb http path=images code=${response.code} body=${text.take(180)}")
                throw HttpException(response.code)
            }
            val paths = parseImagePaths(text)
            if (paths.isEmpty()) LOG.i("echo-tmdb empty path=images media=$mediaType id=$id")
            return paths
        }

        override suspend fun stills(apiKey: String, apiBaseUrl: String, mediaType: String, id: Int): List<String> {
            val url = apiBaseUrl(apiBaseUrl) + "/" + API_PATH + "/" + mediaType + "/" + id + "/" + IMAGES_PATH
            val response = Http.getRaw(url, authInit(apiKey))
            val text = String(response.body, Charsets.UTF_8)
            if (response.code !in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX) {
                LOG.i("echo-tmdb http path=stills code=${response.code} body=${text.take(180)}")
                throw HttpException(response.code)
            }
            val paths = parseBackdropPaths(text)
            if (paths.isEmpty()) LOG.i("echo-tmdb empty path=stills media=$mediaType id=$id")
            return paths
        }

        override suspend fun detail(apiKey: String, apiBaseUrl: String, mediaType: String, id: Int): TmdbDetail? {
            val url = apiBaseUrl(apiBaseUrl) + "/" + API_PATH + "/" + mediaType + "/" + id
            val response = Http.getRaw(url, authInit(apiKey) {
                params("language", searchLanguage())
                params("append_to_response", CREDITS_KEY)
            })
            val text = String(response.body, Charsets.UTF_8)
            if (response.code !in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX) {
                LOG.i("echo-tmdb http path=detail code=${response.code} body=${text.take(180)}")
                throw HttpException(response.code)
            }
            return parseDetail(text)
        }

        override suspend fun person(apiKey: String, apiBaseUrl: String, id: Int): TmdbPerson? {
            val url = apiBaseUrl(apiBaseUrl) + "/" + API_PATH + "/person/" + id
            val response = Http.getRaw(url, authInit(apiKey) {
                params("language", searchLanguage())
            })
            val text = String(response.body, Charsets.UTF_8)
            if (response.code !in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX) {
                LOG.i("echo-tmdb http path=person code=${response.code} body=${text.take(180)}")
                throw HttpException(response.code)
            }
            return parsePerson(text)
        }

        override suspend fun alternativeTitles(
            apiKey: String,
            apiBaseUrl: String,
            mediaType: String,
            id: Int,
        ): List<String> {
            val url = apiBaseUrl(apiBaseUrl) + "/" + API_PATH + "/" + mediaType + "/" + id + "/" + ALTERNATIVE_TITLES_PATH
            val response = Http.getRaw(url, authInit(apiKey))
            val text = String(response.body, Charsets.UTF_8)
            if (response.code !in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX) {
                LOG.i("echo-tmdb http path=alts code=${response.code} body=${text.take(180)}")
                throw HttpException(response.code)
            }
            return parseAlternativeTitles(text)
        }

        override suspend fun season(apiKey: String, apiBaseUrl: String, tvId: Int, seasonNumber: Int): List<TmdbEpisode> {
            val url = apiBaseUrl(apiBaseUrl) + "/" + API_PATH + "/" + MEDIA_TV + "/" + tvId + "/season/" + seasonNumber
            val response = Http.getRaw(url, authInit(apiKey) {
                params("language", searchLanguage())
            })
            val text = String(response.body, Charsets.UTF_8)
            if (response.code !in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX) {
                LOG.i("echo-tmdb http path=season code=${response.code} body=${text.take(180)}")
                throw HttpException(response.code)
            }
            return parseSeasonEpisodes(text)
        }
    }
}
