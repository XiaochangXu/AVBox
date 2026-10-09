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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.tvbox.osc.R
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.data.EpisodeTotals
import com.github.tvbox.osc.data.FollowDays
import com.github.tvbox.osc.data.VodFollow
import com.github.tvbox.osc.ui.components.AVBoxOptionMenuAction
import com.github.tvbox.osc.ui.components.VodPoster
import com.github.tvbox.osc.ui.components.WeekdayRes
import com.github.tvbox.osc.ui.components.formatReminderTime
import com.github.tvbox.osc.ui.components.joinedDaysText
import com.github.tvbox.osc.ui.theme.cardContainer
import kotlin.math.roundToInt

private val ScheduleIconSize = 16.dp

private val ScheduleTextIndent = 22.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FollowRow(
    follow: VodFollow,
    history: VodInfo?,
    totalEpisodes: Int?,
    playedPercent: Int?,
    watched: Boolean,
    editMode: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onWatchedChange: (Boolean) -> Unit,
) {
    var progressEntered by rememberSaveable { mutableStateOf(false) }
    val days = FollowDays.decode(follow.updateDays)
    val daysText = if (days.isEmpty()) {
        ""
    } else {
        joinedDaysText(
            WeekdayRes.map { stringResource(it) },
            days,
            stringResource(R.string.follow_days_separator),
        )
    }
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
                    name = follow.name,
                    pic = follow.pic,
                    sourceKey = follow.sourceKey,
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
                if (watched) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = stringResource(R.string.following_watched),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = follow.name ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    AVBoxOptionMenuAction(
                        options = listOf(
                            stringResource(R.string.following_not_watched),
                            stringResource(R.string.following_watched),
                        ),
                        selectedIndex = if (watched) 1 else 0,
                        onSelect = { index -> onWatchedChange(index == 1) },
                        contentDescription = stringResource(R.string.player_menu_more),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_follow_update_time),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(ScheduleIconSize),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (days.isEmpty()) {
                            stringResource(R.string.following_no_schedule)
                        } else {
                            stringResource(R.string.following_update_days, daysText)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (days.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(modifier = Modifier.width(ScheduleTextIndent))
                        Text(
                            text = stringResource(
                                R.string.following_update_time,
                                formatReminderTime(follow.updateHour),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                val eps = followEpisodeTotal(history, totalEpisodes)
                val episodeFraction = eps?.let { total ->
                    ((history?.playIndex ?: 0) + 1).coerceIn(1, total).toFloat() / total
                }
                val barProgress = playedPercent?.let { it / 100f } ?: episodeFraction
                if (history != null && barProgress != null) {
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
                                    (history.playIndex + 1).coerceIn(1, eps),
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

private fun followEpisodeTotal(history: VodInfo?, totalEpisodes: Int?): Int? {
    if (history == null) return null
    val numberedEpisode = history.playNote.isNullOrEmpty() || EpisodeTotals.isNumberedEpisode(history.playNote)
    if (!numberedEpisode) return null
    return totalEpisodes ?: parseEpisodeTotal(history.note) ?: parseEpisodeTotal(history.state)
}
