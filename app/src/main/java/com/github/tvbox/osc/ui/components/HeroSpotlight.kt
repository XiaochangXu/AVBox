package com.github.tvbox.osc.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.tvbox.osc.R
import com.github.tvbox.osc.bean.Movie
import com.github.tvbox.osc.util.LOG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val HERO_SPOTLIGHT_PAGES_PER_SET = 100_000

private const val HERO_SPOTLIGHT_HEIGHT_RATIO = 0.58f

internal val HeroSpotlightFadeStops = arrayOf(
    0.45f to 0f,
    0.55f to 0.22f,
    0.65f to 0.47f,
    0.75f to 0.70f,
    0.85f to 0.90f,
    0.93f to 0.975f,
    1f to 1f,
)

private val HeroSpotlightMinHeight = 360.dp

private val HeroSpotlightMaxHeight = 560.dp

internal fun heroSpotlightHeight(screenHeightDp: Int): Dp =
    (screenHeightDp * HERO_SPOTLIGHT_HEIGHT_RATIO).dp
        .coerceIn(HeroSpotlightMinHeight, HeroSpotlightMaxHeight)

@Composable
fun HeroSpotlight(
    videos: List<Movie.Video>,
    backdropColor: Color,
    onBackdropSeed: (Int?) -> Unit,
    onPosterPic: (String) -> Unit,
    onCardClick: (Movie.Video) -> Unit,
    modifier: Modifier = Modifier,
    fadeToBackdrop: Boolean = false,
) {
    if (videos.isEmpty()) return
    val count = videos.size
    val pagerState = rememberPagerState(
        initialPage = count * (HERO_SPOTLIGHT_PAGES_PER_SET / 2),
        pageCount = { count * HERO_SPOTLIGHT_PAGES_PER_SET },
    )
    val heroHeight = heroSpotlightHeight(LocalConfiguration.current.screenHeightDp)
    val current = videos[pagerState.currentPage % count]
    val scope = rememberCoroutineScope()
    var pendingPic by remember { mutableStateOf<String?>(null) }
    var settledPage by remember { mutableStateOf(pagerState.settledPage) }

    LaunchedEffect(pagerState, videos) {
        snapshotFlow { pagerState.settledPage }.collect { settledPage = it }
    }

    val settledVideo = videos[settledPage % count]
    val settledPic = settledVideo.pic
    val settledUrl = tmdbPosterUrl(
        settledVideo.name,
        settledVideo.year,
        settledVideo.sourceKey,
        preferLarge = true,
        cacheOnly = true,
    ) ?: settledPic

    LaunchedEffect(settledPage, settledUrl) {
        val hit = HomeBackdrop.has(settledPic)
        LOG.i("echo-home-backdrop settle page=$settledPage hit=$hit pic=${settledPic.orEmpty().takeLast(24)}")
        onPosterPic(settledUrl.orEmpty())
        if (hit) {
            onBackdropSeed(HomeBackdrop.seedOf(settledPic))
        } else {
            pendingPic = settledPic
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
        ) { page ->
            val video = videos[page % count]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (fadeToBackdrop) {
                            Modifier
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            *HeroSpotlightFadeStops
                                                .map { (position, alpha) ->
                                                    position to Color.White.copy(alpha = 1f - alpha)
                                                }
                                                .toTypedArray(),
                                        ),
                                        blendMode = BlendMode.DstIn,
                                    )
                                }
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onCardClick(video) },
            ) {
                VodPoster(
                    name = video.name,
                    pic = video.pic,
                    modifier = Modifier.fillMaxSize(),
                    preferLarge = true,
                    year = video.year,
                    sourceKey = video.sourceKey,
                    tmdbCacheOnly = true,
                    onImage = { image ->
                        val pic = video.pic
                        if (!HomeBackdrop.has(pic)) {
                            scope.launch {
                                val seed = withContext(Dispatchers.Default) { ImagePalette.seedOf(image) }
                                HomeBackdrop.put(pic, seed)
                                LOG.i("echo-home-backdrop seed=${seed ?: -1} pic=${pic.orEmpty().takeLast(24)}")
                                if (pic == pendingPic) onBackdropSeed(seed)
                            }
                        }
                    },
                )
                if (!fadeToBackdrop) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    *HeroSpotlightFadeStops
                                        .map { (position, alpha) ->
                                            position to backdropColor.copy(alpha = alpha)
                                        }
                                        .toTypedArray(),
                                ),
                            ),
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HeroSpotlightCaption(video = current)
            if (count > 1) {
                HeroSpotlightDots(
                    count = count,
                    selected = pagerState.currentPage % count,
                )
            }
        }
    }
}

@Composable
private fun HeroSpotlightCaption(video: Movie.Video) {
    Text(
        text = video.name ?: "",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight(800),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp),
    )
    val rating = ratingBadgeText(video.note)
    if (!rating.isNullOrBlank()) {
        Text(
            text = if (rating != video.note?.trim()) stringResource(R.string.detail_rating, rating) else rating,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, start = 24.dp, end = 24.dp),
        )
    }
}

@Composable
private fun HeroSpotlightDots(count: Int, selected: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val active = index == selected
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(width = if (active) 18.dp else 6.dp, height = 6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (active) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f)
                        },
                    ),
            )
        }
    }
}
