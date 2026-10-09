package com.github.tvbox.osc.ui.page

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.tvbox.osc.R
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.data.EpisodeTotals
import com.github.tvbox.osc.ui.components.VodPoster
import com.github.tvbox.osc.ui.theme.cardContainer
import kotlin.math.roundToInt

internal const val PROGRESS_ENTER_DURATION_MS = 600

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HistoryRow(
    item: VodInfo,
    totalEpisodes: Int?,
    playedPercent: Int?,
    editMode: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    var progressEntered by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.cardContainer,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .aspectRatio(2f / 3f),
            ) {
                VodPoster(
                    name = item.name,
                    pic = item.pic,
                    year = item.year,
                    sourceKey = item.sourceKey,
                    tmdbCacheOnly = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp)),
                )
                if (editMode) {
                    SelectCircle(
                        selected = selected,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (!item.sourceName.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        val unavailable = item.sourceUnavailable
                        Text(
                            text = if (unavailable) {
                                "${item.sourceName} · ${stringResource(R.string.source_unavailable)}"
                            } else {
                                item.sourceName
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (unavailable) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .widthIn(max = 160.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
                Text(
                    text = if (item.playNote.isNullOrEmpty()) {
                        item.note ?: ""
                    } else {
                        stringResource(R.string.history_last_watched, item.playNote)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val numberedEpisode = item.playNote.isNullOrEmpty() || EpisodeTotals.isNumberedEpisode(item.playNote)
                val eps = if (numberedEpisode) {
                    totalEpisodes ?: parseEpisodeTotal(item.note) ?: parseEpisodeTotal(item.state)
                } else {
                    null
                }
                val episodeFraction = eps?.let { total ->
                    (item.playIndex + 1).coerceIn(1, total).toFloat() / total
                }
                val barProgress = playedPercent?.let { it / 100f } ?: episodeFraction
                if (barProgress != null) {
                    val barColor = MaterialTheme.colorScheme.primary
                    val progressAnim = remember {
                        Animatable(if (progressEntered) barProgress else 0f)
                    }
                    LaunchedEffect(barProgress) {
                        val spec = if (progressEntered) {
                            ProgressIndicatorDefaults.ProgressAnimationSpec
                        } else {
                            tween(PROGRESS_ENTER_DURATION_MS)
                        }
                        progressEntered = true
                        progressAnim.animateTo(barProgress, spec)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { progressAnim.value },
                            modifier = Modifier
                                .weight(1f)
                                .height(5.dp),
                            color = barColor,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            drawStopIndicator = {},
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (eps != null) {
                                stringResource(
                                        R.string.history_episode_progress,
                                        (item.playIndex + 1).coerceIn(1, eps),
                                        eps,
                                    )
                            } else {
                                stringResource(R.string.history_watched_percent, (barProgress * 100).roundToInt())
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = barColor,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(5.dp))
                }
            }
        }
    }
}

// i18n: keep —— 匹配源数据(片名/备注)里的"第N集/期",不能翻
private val EpisodeTotalRegex = Regex("(\\d+)\\s*[集期]")

internal fun parseEpisodeTotal(note: String?): Int? {
    val total = note?.let { EpisodeTotalRegex.find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: return null
    return total.takeIf { it in 2..1000 }
}
