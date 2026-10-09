package com.github.tvbox.osc.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.tvbox.osc.util.TmdbApi
import com.github.tvbox.osc.util.TmdbPoster
import coil3.Image
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transitionFactory
import coil3.transition.Transition

private const val UnnamedPosterKey = "！"

private const val POSTER_CHAR_WIDTH_RATIO = 0.46f

private val PosterPalette = intArrayOf(
    0xFFEF5350.toInt(),
    0xFFEC407A.toInt(),
    0xFFAB47BC.toInt(),
    0xFF7E57C2.toInt(),
    0xFF5C6BC0.toInt(),
    0xFF42A5F5.toInt(),
    0xFF29B6F6.toInt(),
    0xFF26C6DA.toInt(),
    0xFF26A69A.toInt(),
    0xFF66BB6A.toInt(),
    0xFF9CCC65.toInt(),
    0xFFD4E157.toInt(),
    0xFFFFEE58.toInt(),
    0xFFFFCA28.toInt(),
    0xFFFFA726.toInt(),
    0xFFFF7043.toInt(),
    0xFF8D6E63.toInt(),
    0xFFBDBDBD.toInt(),
    0xFF78909C.toInt(),
)

internal fun posterSeedColor(name: String?): Int {
    val key = posterFirstChar(name)
    return PosterPalette[(key.hashCode() and Int.MAX_VALUE) % PosterPalette.size]
}

internal fun posterFirstChar(name: String?): String {
    val text = name?.trim().orEmpty()
    if (text.isEmpty()) return UnnamedPosterKey
    return text.take(if (text[0].isHighSurrogate()) 2 else 1)
}

@Composable
internal fun VodPoster(
    name: String?,
    pic: String?,
    modifier: Modifier = Modifier,
    preferLarge: Boolean = false,
    year: Int = 0,
    sourceKey: String? = null,
    resolveTmdb: Boolean = true,
    tmdbCacheOnly: Boolean = false,
    onImage: ((Image) -> Unit)? = null,
) {
    val tmdbUrl = if (resolveTmdb) tmdbPosterUrl(name, year, sourceKey, preferLarge, tmdbCacheOnly) else null
    val siteUrl = if (preferLarge) VodImages.largePosterUrl(pic) else pic
    var showFallback by remember(pic) { mutableStateOf(false) }
    var posterUrl by remember(pic, siteUrl, tmdbUrl) {
        mutableStateOf(tmdbUrl ?: siteUrl)
    }
    val context = LocalPlatformContext.current
    val request = remember(context, posterUrl, preferLarge) {
        ImageRequest.Builder(context)
            .data(posterUrl)
            .apply {
                if (preferLarge) crossfade(true) else transitionFactory(Transition.Factory.NONE)
            }
            .build()
    }
    val baseRequest = remember(context, pic) {
        ImageRequest.Builder(context)
            .data(pic)
            .transitionFactory(Transition.Factory.NONE)
            .build()
    }
    val baseUnderlay = preferLarge && !pic.isNullOrEmpty() && posterUrl != pic && !showFallback

    Box(modifier = modifier) {
        if (showFallback) PosterFallback(name)
        if (baseUnderlay) {
            AsyncImage(
                model = baseRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        AsyncImage(
            model = request,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            onState = { state ->
                if (state is AsyncImagePainter.State.Error) {
                    val fallback = when {
                        tmdbUrl != null && posterUrl == tmdbUrl -> siteUrl
                        posterUrl != pic -> pic
                        else -> null
                    }
                    if (fallback != null && fallback != posterUrl) posterUrl = fallback else showFallback = true
                } else {
                    showFallback = false
                    if (state is AsyncImagePainter.State.Success) onImage?.invoke(state.result.image)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
internal fun tmdbPosterPath(name: String?, year: Int, sourceKey: String?, cacheOnly: Boolean = false): String? {
    if (name.isNullOrBlank() || sourceKey == TmdbApi.SOURCE_KEY || !TmdbPoster.isActive()) return ""
    val epoch by TmdbPoster.configEpoch.collectAsState()
    // 列表场景只读缓存不解析（进过详情页的片才有 TMDB 图），滚动期零网络请求
    if (cacheOnly) return TmdbPoster.cachedPoster(name, year)
    var posterPath by remember(name, year, epoch) {
        mutableStateOf(TmdbPoster.cachedPoster(name, year))
    }
    LaunchedEffect(name, year, epoch) {
        if (posterPath == null) posterPath = TmdbPoster.resolvePoster(name, year) ?: ""
    }
    return posterPath
}

@Composable
internal fun tmdbPosterUrl(
    name: String?,
    year: Int,
    sourceKey: String?,
    preferLarge: Boolean,
    cacheOnly: Boolean = false,
): String? =
    tmdbPosterPath(name, year, sourceKey, cacheOnly)
        ?.takeIf { it.isNotEmpty() }
        ?.let { TmdbPoster.imageUrl(it, preferLarge) }

@Composable
private fun PosterFallback(name: String?) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(posterSeedColor(name))),
        contentAlignment = Alignment.Center,
    ) {
        val charHeight = minOf(maxWidth, maxHeight) * POSTER_CHAR_WIDTH_RATIO
        val charSize = with(LocalDensity.current) { charHeight.toSp() }
        Text(
            text = posterFirstChar(name),
            style = TextStyle(
                color = Color.White,
                fontSize = charSize,
                lineHeight = charSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
            maxLines = 1,
            modifier = Modifier.padding(2.dp),
        )
    }
}
