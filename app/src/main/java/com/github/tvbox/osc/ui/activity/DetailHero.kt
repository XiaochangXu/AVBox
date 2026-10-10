package com.github.tvbox.osc.ui.activity

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.Image
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.github.tvbox.osc.R
import com.github.tvbox.osc.player.ui.playerPressEffect
import com.github.tvbox.osc.ui.components.ImagePalette
import com.github.tvbox.osc.ui.components.LocalTopBarGlassBackdrop
import com.github.tvbox.osc.ui.components.TopBarActionBox
import com.github.tvbox.osc.ui.components.VodPoster
import com.github.tvbox.osc.ui.components.tmdbPosterPath
import com.github.tvbox.osc.ui.page.TmdbPosterStyle
import com.github.tvbox.osc.ui.theme.AppThemeState
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.KV
import com.github.tvbox.osc.util.TmdbPoster
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DetailTopScrimAlpha = 0.32f

private const val HeroCircleAlpha = 0.6f

private const val HERO_HEIGHT_RATIO = 0.75f

private const val HERO_ROLL_INTERVAL_MS = 5000L

private const val HERO_ROLL_ANIM_MS = 300

private val DetailHeroFadeStops = arrayOf(
    0.42f to 0.05f,
    0.52f to 0.40f,
    0.62f to 0.72f,
    0.72f to 0.92f,
    0.82f to 1f,
)

private const val HERO_TMDB_LOADING_TIMEOUT_MS = 1200L

private val DetailTopScrimExtra = 24.dp

private val HeroMinHeight = 480.dp

private val HeroMaxHeight = 660.dp

private val HeroCaptionHorizontalPadding = 24.dp

private val HeroCaptionBottomPadding = 16.dp

private val HeroCaptionSpacing = 6.dp

private val HeroPlayCapsuleHeight = 52.dp

private val HeroPlayCapsuleMinWidth = 180.dp

private val HeroPlayCapsuleHorizontalPadding = 32.dp

private val HeroCircleButtonSize = 52.dp

private val HeroCircleButtonSpacing = 24.dp

private val HeroActionSpacing = 20.dp

private val HeroButtonRowSpacing = 16.dp

private val MetaSeparator = " · "

private val TypeSeparators = Regex("[,，、/|;；]+")

@Composable
internal fun DetailTopScrim(modifier: Modifier = Modifier) {
    val statusTop = with(LocalDensity.current) { WindowInsets.statusBarsIgnoringVisibility.getTop(this).toDp() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(statusTop + DetailTopScrimExtra)
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = DetailTopScrimAlpha),
                    1f to Color.Transparent,
                ),
            ),
    )
}

@Composable
internal fun DetailHero(
    title: String,
    picture: String?,
    year: Int,
    area: String?,
    type: String?,
    collected: Boolean,
    followed: Boolean,
    onPosterPic: (String) -> Unit,
    onSeed: (Int?) -> Unit,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onMusic: () -> Unit,
    onCast: () -> Unit,
    onCollect: () -> Unit,
    onFollow: () -> Unit,
    rollPaused: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val glassBackdrop = rememberLayerBackdrop(onDraw = { drawContent() })
    val circleColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = HeroCircleAlpha)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(
                (LocalConfiguration.current.screenHeightDp * HERO_HEIGHT_RATIO)
                    .dp
                    .coerceIn(HeroMinHeight, HeroMaxHeight),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            *DetailHeroFadeStops
                                .map { (position, alpha) -> position to Color.White.copy(alpha = 1f - alpha) }
                                .toTypedArray(),
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            Box(modifier = Modifier.fillMaxSize().layerBackdrop(glassBackdrop)) {
                DetailHeroPoster(
                    title = title,
                    picture = picture,
                    year = year,
                    onImage = { image ->
                        scope.launch {
                            val seed = withContext(Dispatchers.Default) { ImagePalette.seedOf(image) }
                            onSeed(seed)
                        }
                    },
                    onBackdropPic = onPosterPic,
                    paused = rollPaused,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        CompositionLocalProvider(LocalTopBarGlassBackdrop provides glassBackdrop) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopBarActionBox(
                    iconRes = R.drawable.ic_arrow_left,
                    contentDescription = stringResource(R.string.common_back),
                    onClick = onBack,
                    fallbackColor = circleColor,
                )
                Spacer(Modifier.weight(1f))
                TopBarActionBox(
                    iconRes = if (collected) R.drawable.ic_tab_collect_filled else R.drawable.ic_tab_collect,
                    contentDescription = stringResource(
                        if (collected) R.string.detail_uncollect else R.string.detail_collect,
                    ),
                    onClick = onCollect,
                    fallbackColor = circleColor,
                    tint = if (collected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                .padding(
                    start = HeroCaptionHorizontalPadding,
                    end = HeroCaptionHorizontalPadding,
                    bottom = HeroCaptionBottomPadding,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight(800),
                color = heroCaptionColor(),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            val yearArea = listOfNotNull(
                year.takeIf { it > 0 }?.toString(),
                area?.takeIf { it.isNotBlank() },
            ).joinToString(MetaSeparator)
            if (yearArea.isNotEmpty()) {
                DetailHeroMetaLine(yearArea)
            }
            val types = type.orEmpty()
                .split(TypeSeparators)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(MetaSeparator)
            if (types.isNotEmpty()) {
                DetailHeroMetaLine(types)
            }
            Box(
                modifier = Modifier
                    .padding(top = HeroActionSpacing)
                    .widthIn(min = HeroPlayCapsuleMinWidth)
                    .height(HeroPlayCapsuleHeight)
                    .playerPressEffect(onTap = onPlay)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = HeroPlayCapsuleHorizontalPadding),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.player_ic_play),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary),
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = stringResource(R.string.common_play),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            DetailActionRow(
                followed = followed,
                onMusic = onMusic,
                onCast = onCast,
                onFollow = onFollow,
                modifier = Modifier.padding(top = HeroButtonRowSpacing),
            )
        }
    }
}

@Composable
internal fun DetailActionRow(
    followed: Boolean,
    onMusic: () -> Unit,
    onCast: () -> Unit,
    onFollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(HeroCircleButtonSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeroCircleButton(
            iconRes = R.drawable.ic_detail_music_player,
            contentDescription = stringResource(R.string.detail_music_player),
            selected = false,
            onClick = onMusic,
        )
        HeroCircleButton(
            iconRes = R.drawable.ic_detail_cast,
            contentDescription = stringResource(R.string.common_cast),
            selected = false,
            onClick = onCast,
        )
        HeroCircleButton(
            iconRes = R.drawable.ic_tab_following,
            contentDescription = stringResource(R.string.tab_following),
            selected = followed,
            onClick = onFollow,
        )
    }
}

@Composable
private fun HeroCircleButton(
    iconRes: Int,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(HeroCircleButtonSize)
            .playerPressEffect(onTap = onClick)
            .detailGlass(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun heroCaptionColor(): Color =
    if (AppThemeState.isDark(isSystemInDarkTheme())) {
        Color.White
    } else {
        Color.Black
    }

@Composable
private fun DetailHeroMetaLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        color = heroCaptionColor(),
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = HeroCaptionSpacing),
    )
}

@Composable
private fun DetailHeroPoster(
    title: String,
    picture: String?,
    year: Int,
    onImage: (Image) -> Unit,
    onBackdropPic: (String) -> Unit,
    paused: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val epoch by TmdbPoster.configEpoch.collectAsState()
    val style = remember(title, epoch) { TmdbPosterStyle.of(KV.get(HawkConfig.TMDB_POSTER_STYLE, 0)) }
    val multiEnabled = TmdbPoster.isActive() &&
        (style == TmdbPosterStyle.Random || style == TmdbPosterStyle.Roll)
    var images by remember(title, year, epoch, multiEnabled) {
        mutableStateOf(if (multiEnabled) TmdbPoster.cachedImages(title, year) else null)
    }
    LaunchedEffect(title, year, epoch, multiEnabled) {
        if (multiEnabled && images == null) images = TmdbPoster.resolveImages(title, year)
    }
    val urls = remember(images) { images.orEmpty().map { TmdbPoster.imageUrl(it, true) } }
    if (urls.isEmpty()) {
        val fixedPath = tmdbPosterPath(title, year, null)
        val resolving = (multiEnabled && images == null) || fixedPath == null
        var waitExpired by remember(title, year, epoch) { mutableStateOf(false) }
        LaunchedEffect(title, year, epoch, resolving) {
            waitExpired = false
            if (resolving) {
                delay(HERO_TMDB_LOADING_TIMEOUT_MS)
                waitExpired = true
            }
        }
        val fixedUrl = fixedPath?.takeIf { it.isNotEmpty() }?.let { TmdbPoster.imageUrl(it, true) }
        LaunchedEffect(fixedUrl, picture) {
            val url = fixedUrl ?: picture
            if (!url.isNullOrEmpty()) onBackdropPic(url)
        }
        if (resolving && !waitExpired) {
            DetailHeroPosterLoading(modifier)
            return
        }
        VodPoster(
            name = title,
            pic = picture,
            preferLarge = true,
            year = year,
            modifier = modifier,
            onImage = onImage,
        )
        return
    }
    val seedIndex = remember(title, urls) {
        if (style == TmdbPosterStyle.Random) Random.nextInt(urls.size) else 0
    }
    var index by remember(title, urls) { mutableIntStateOf(seedIndex) }
    if (style == TmdbPosterStyle.Roll && urls.size > 1 && !paused) {
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(lifecycleOwner, urls) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(HERO_ROLL_INTERVAL_MS)
                    index = (index + 1) % urls.size
                }
            }
        }
    }
    val context = LocalPlatformContext.current
    LaunchedEffect(index, urls) {
        if (urls.size > 1) {
            val request = ImageRequest.Builder(context).data(urls[(index + 1) % urls.size]).build()
            SingletonImageLoader.get(context).enqueue(request)
        }
    }
    LaunchedEffect(urls) { onBackdropPic(urls[seedIndex]) }
    var seedSent by remember(title, urls) { mutableStateOf(false) }
    AnimatedContent(
        targetState = urls[index],
        transitionSpec = {
            (slideInHorizontally(tween(HERO_ROLL_ANIM_MS)) { it } + fadeIn(tween(HERO_ROLL_ANIM_MS)))
                .togetherWith(
                    slideOutHorizontally(tween(HERO_ROLL_ANIM_MS)) { -it } + fadeOut(tween(HERO_ROLL_ANIM_MS)),
                )
        },
        label = "heroPosterRoll",
        modifier = modifier,
    ) { targetUrl ->
        VodPoster(
            name = title,
            pic = targetUrl,
            preferLarge = true,
            resolveTmdb = false,
            modifier = Modifier.fillMaxSize(),
            onImage = { image ->
                if (!seedSent) {
                    seedSent = true
                    onImage(image)
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DetailHeroPosterLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        ContainedLoadingIndicator(Modifier.size(64.dp))
    }
}
