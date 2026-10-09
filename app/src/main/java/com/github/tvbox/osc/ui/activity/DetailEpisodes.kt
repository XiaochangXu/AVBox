package com.github.tvbox.osc.ui.activity

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.github.tvbox.osc.R
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.ui.components.AVBoxBottomSheet
import com.github.tvbox.osc.ui.components.LocalSheetDismiss
import com.github.tvbox.osc.ui.theme.filterChipColors
import com.github.tvbox.osc.util.TmdbApi
import com.github.tvbox.osc.util.TmdbPoster
import kotlinx.coroutines.launch

private val EpisodeThumbWidth = 180.dp

@Composable
internal fun EpisodeRow(
    vm: DetailViewModel,
    info: VodInfo,
    episodes: List<VodInfo.VodSeries>,
    playIndex: Int,
    currentFlag: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitleIcon(painterResource(R.drawable.ic_detail_episodes))
            Text(
                text = stringResource(R.string.detail_episodes),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
            PillAction(
                iconRes = if (info.reverseSort) {
                    R.drawable.ic_episode_order_asc
                } else {
                    R.drawable.ic_episode_reverse
                },
                text = stringResource(if (info.reverseSort) R.string.detail_order_asc else R.string.detail_order_desc),
                onClick = { vm.toggleReverse() },
            )
            Spacer(Modifier.width(2.dp))
            PillAction(
                iconRes = R.drawable.ic_episode_grid_all,
                text = stringResource(R.string.common_all),
                onClick = { vm.showEpisodeSheet() },
            )
        }
        val listState = rememberLazyListState()
        var prevReverseSort by remember { mutableStateOf(info.reverseSort) }
        LaunchedEffect(playIndex, currentFlag, episodes.size, info.reverseSort) {
            if (episodes.isEmpty()) return@LaunchedEffect
            val reverseChanged = info.reverseSort != prevReverseSort
            prevReverseSort = info.reverseSort
            if (reverseChanged) {
                listState.scrollToItem(0)
            } else if (playIndex >= 0) {
                listState.scrollToItem(minOf(playIndex, episodes.size - 1))
            }
        }
        val seasonHint = remember(info.name, currentFlag) {
            TmdbApi.parseSeasonHint(info.name) ?: TmdbApi.parseSeasonHint(currentFlag)
        }
        val episodeMeta = rememberEpisodeMeta(info.name, info.year, episodes.size, seasonHint)
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(episodes) { index, ep ->
                EpisodeCard(
                    name = ep.name ?: (index + 1).toString(),
                    episodeNumber = index + 1,
                    thumbEnabled = episodeMeta != null,
                    episode = episodeMeta?.getOrNull(index),
                    selected = index == playIndex,
                    onClick = { vm.onEpisodeClick(index) },
                )
            }
        }
    }
}

@Composable
private fun rememberEpisodeMeta(
    name: String?,
    year: Int,
    episodeCount: Int,
    seasonHint: Int?,
): List<TmdbApi.TmdbEpisode>? {
    if (name.isNullOrBlank() || episodeCount <= 0) return null
    val epoch by TmdbPoster.configEpoch.collectAsState()
    var meta by remember(name, episodeCount, seasonHint, epoch) {
        mutableStateOf(TmdbPoster.cachedEpisodeMeta(name, episodeCount, seasonHint))
    }
    LaunchedEffect(name, episodeCount, seasonHint, epoch) {
        if (meta == null) meta = TmdbPoster.resolveEpisodeMeta(name, year, episodeCount, seasonHint)
    }
    return meta
}

@Composable
private fun EpisodeCard(
    name: String,
    episodeNumber: Int,
    thumbEnabled: Boolean,
    episode: TmdbApi.TmdbEpisode?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (!thumbEnabled) {
        DetailItemCard(
            selected = selected,
            onClick = onClick,
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }
    val stillPath = episode?.stillPath.orEmpty()
    val title = episode?.name?.takeIf { it.isNotBlank() } ?: name
    Column(
        modifier = Modifier
            .width(EpisodeThumbWidth)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(detailCardColor())
                .then(
                    if (selected) {
                        Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                    } else {
                        Modifier
                    },
                ),
        ) {
            if (stillPath.isNotEmpty()) {
                AsyncImage(
                    model = TmdbPoster.stillUrl(stillPath),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                text = episodeNumber.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 7.dp, vertical = 1.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        )
    }
}

@Composable
private fun PillAction(iconRes: Int, text: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun EpisodeSheet(vm: DetailViewModel, revision: Int, slideFromEnd: Boolean) {
    @Suppress("UNUSED_EXPRESSION") revision
    val show by vm.episodeSheet.collectAsState()
    if (!show) return
    val info = vm.vodInfo ?: return
    val flags = info.seriesFlags.orEmpty()
    val currentFlag = info.playFlag
    val episodes = info.seriesMap?.get(currentFlag).orEmpty()
    val playIndex = info.playIndex

    val groupCount = when {
        episodes.size > 400 -> 120
        episodes.size > 100 -> 60
        else -> 20
    }
    val groups = if (episodes.size > groupCount) {
        val result = ArrayList<String>()
        var i = 0
        while (i < episodes.size) {
            val end = minOf(i + groupCount, episodes.size)
            result.add("${i + 1} - $end")
            i += groupCount
        }
        result
    } else {
        emptyList()
    }
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val gridScope = rememberCoroutineScope()
    var selectedGroup by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(show, currentFlag, playIndex) {
        if (show && playIndex >= 0) {
            selectedGroup = playIndex / groupCount
            if (playIndex in episodes.indices) gridState.scrollToItem(playIndex)
        }
    }

    AVBoxBottomSheet(
        onDismissRequest = { vm.dismissEpisodeSheet() },
        title = if (info.name.isNullOrEmpty()) {
                stringResource(R.string.detail_episodes)
            } else {
                stringResource(R.string.detail_episodes_of, info.name.orEmpty())
            },
        isScrollable = false,
        slideFromEnd = slideFromEnd,
    ) {
        val dismissAnimated = LocalSheetDismiss.current
        Column(modifier = Modifier.fillMaxWidth()) {
            if (flags.size > 1 && !slideFromEnd) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(flags, key = { i, f -> "${i}_${f.name}" }) { _, flag ->
                        FilterChip(
                            selected = flag.name == currentFlag,
                            onClick = { vm.onFlagClick(flag.name ?: "") },
                            label = { Text(flag.name ?: "") },
                            shape = RoundedCornerShape(20.dp),
                            colors = MaterialTheme.colorScheme.filterChipColors(),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (groups.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(groups) { index, label ->
                        FilterChip(
                            selected = index == selectedGroup,
                            onClick = {
                                selectedGroup = index
                                gridScope.launch { gridState.scrollToItem(index * groupCount) }
                            },
                            label = { Text(label) },
                            shape = RoundedCornerShape(20.dp),
                            colors = MaterialTheme.colorScheme.filterChipColors(),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            val maxNameLength = episodes.maxOfOrNull { it.name?.length ?: 0 } ?: 0
            val gridColumnCount = when {
                maxNameLength <= 4 -> if (slideFromEnd) 2 else 4
                maxNameLength <= 12 -> 2
                else -> 1
            }
            val gridModifier = if (slideFromEnd) {
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            } else {
                val rowCount = if (episodes.isEmpty()) 0 else (episodes.size + gridColumnCount - 1) / gridColumnCount
                val gridContentHeight = (rowCount * 40).dp + (((rowCount - 1).coerceAtLeast(0)) * 8).dp
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = minOf(560.dp, gridContentHeight))
            }
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                state = gridState,
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(gridColumnCount),
                modifier = gridModifier,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding() + 16.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                gridItemsIndexed(episodes) { index, ep ->
                    FilterChip(
                        selected = index == playIndex,
                        onClick = {
                            vm.onEpisodeClick(index)
                            dismissAnimated()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        label = {
                            Text(
                                text = ep.name ?: (index + 1).toString(),
                                maxLines = 1,
                                softWrap = false,
                                textAlign = TextAlign.Center,
                                fontSize = 13.sp,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = MaterialTheme.colorScheme.filterChipColors(),
                    )
                }
            }
        }
    }
}
