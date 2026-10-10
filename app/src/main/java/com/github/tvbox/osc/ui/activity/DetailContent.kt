package com.github.tvbox.osc.ui.activity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.bean.Movie
import com.github.tvbox.osc.data.FollowDays
import com.github.tvbox.osc.ui.components.FollowReminderSheet

@Composable
internal fun DetailContent(
    activity: DetailActivity,
    vm: DetailViewModel,
    revision: Int,
    onPosterPic: (String) -> Unit,
    onSeed: (Int?) -> Unit,
    onCardLongClick: (Movie.Video) -> Unit,
) {
    val info = vm.vodInfo ?: return
    @Suppress("UNUSED_EXPRESSION") revision

    val flags = info.seriesFlags.orEmpty()
    val currentFlag = info.playFlag
    val episodes = info.seriesMap?.get(currentFlag).orEmpty()
    val playIndex = info.playIndex
    val qualityOptions by vm.qualityOptions.collectAsState()
    val qualitySelected by vm.qualitySelected.collectAsState()
    val collected by vm.collected.collectAsState()
    val followRecord by vm.follow.collectAsState()
    var descExpanded by rememberSaveable { mutableStateOf(false) }
    var followScheduleOpen by rememberSaveable { mutableStateOf(false) }

    val currentSource = ApiConfig.get().getSource(vm.firstsourceKey)
    val displaySourceName = currentSource?.name ?: vm.firstsourceKey
    val heroRollPaused by vm.fullScreen.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item(key = "hero") {
            DetailHero(
                title = info.name ?: "TVBox",
                picture = info.pic,
                year = info.year,
                area = info.area,
                type = info.type,
                collected = collected,
                followed = followRecord != null,
                onPosterPic = onPosterPic,
                onSeed = onSeed,
                onBack = { activity.onBackPressedDispatcher.onBackPressed() },
                onPlay = { vm.onPlayRequested(DetailPlaybackEntry.PlayCapsule) },
                onMusic = { activity.openMusicPlayer() },
                onCast = { activity.openCast() },
                onCollect = { vm.toggleCollect() },
                onFollow = { followScheduleOpen = true },
                rollPaused = heroRollPaused,
            )
        }

        item(key = "desc") {
            val desc = remember(info.des) { removeHtmlTag(info.des) }
            if (desc.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp),
                ) {
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (descExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { descExpanded = !descExpanded },
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { descExpanded = !descExpanded },
                    ) {
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = stringResource(if (descExpanded) R.string.detail_collapse else R.string.detail_expand),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(if (descExpanded) 180f else 0f),
                        )
                    }
                }
            }
        }

        item(key = "tmdb_info") {
            TmdbInfoSection(info.name, info.year)
        }

        if (episodes.isNotEmpty()) {
            item(key = "episodes") {
                EpisodeRow(vm, info, episodes, playIndex, currentFlag)
            }
        }

        if (qualityOptions.size > 1) {
            item(key = "quality") {
                ChipRow(
                    title = stringResource(R.string.detail_quality),
                    leading = { SectionTitleIcon(painterResource(R.drawable.ic_detail_quality)) },
                ) {
                    itemsIndexed(qualityOptions) { index, option ->
                        FilterChip(
                            selected = index == qualitySelected,
                            onClick = { vm.onQualityClick(index, activity.playbackFacts()) },
                            label = { Text(option) },
                            shape = RoundedCornerShape(20.dp),
                            border = detailChipBorder(selected = index == qualitySelected),
                            colors = detailChipColors(),
                        )
                    }
                }
            }
        }

        if (flags.isNotEmpty()) {
            item(key = "flags") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                    ) {
                        SectionTitleIcon(painterResource(R.drawable.ic_detail_line))
                        Text(
                            text = stringResource(R.string.detail_line),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = stringResource(R.string.detail_source, displaySourceName),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .widthIn(max = 180.dp),
                        )
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(flags, key = { i, f -> "${i}_${f.name}" }) { _, flag ->
                            LineCard(
                                name = flag.name ?: "",
                                count = info.seriesMap?.get(flag.name)?.size ?: 0,
                                selected = flag.name == currentFlag,
                                onClick = { vm.onFlagClick(flag.name ?: "") },
                            )
                        }
                    }
                }
            }
        }

        item(key = "sources") {
            SourceSection(vm, currentSourceName = displaySourceName, revision = revision)
        }

        item(key = "related") {
            RelatedSection(activity, vm, onCardLongClick)
        }
    }

    if (followScheduleOpen) {
        FollowReminderSheet(
            initialDays = followRecord?.let { FollowDays.decode(it.updateDays) }?.takeIf { it.isNotEmpty() }
                ?: setOf(0),
            initialHour = followRecord?.updateHour ?: 21,
            onDismissRequest = { followScheduleOpen = false },
            onSave = { days, hour -> vm.saveFollow(days, hour) },
        )
    }
}

@Composable
private fun LineCard(
    name: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DetailItemCard(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.width(DetailItemWidth),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.detail_line_video_count, count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun ChipRow(
    title: String,
    leading: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        ) {
            leading?.invoke()
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = if (leading != null) 8.dp else 0.dp),
            )
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}
