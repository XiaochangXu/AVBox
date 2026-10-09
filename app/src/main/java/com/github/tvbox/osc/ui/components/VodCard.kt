package com.github.tvbox.osc.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.tvbox.osc.bean.Movie

enum class VodCardStyle { Overlay, Stacked }

@Composable
fun VodCard(
    video: Movie.Video,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: VodCardStyle = VodCardStyle.Overlay,
) {
    if (style == VodCardStyle.Stacked) {
        Column(modifier = modifier) {
            PressableCard(
                onClick = onClick,
                onLongClick = onLongClick,
                shape = RoundedCornerShape(16.dp),
            ) {
                Box(modifier = Modifier.aspectRatio(2f / 3f)) {
                    VodPoster(video.name, video.pic, year = video.year, sourceKey = video.sourceKey, tmdbCacheOnly = true)
                    RatingBadge(video, Modifier.align(Alignment.TopEnd))
                }
            }
            Text(
                text = video.name ?: "",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
        }
        return
    }

    PressableCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
    ) {
        Box(modifier = Modifier.aspectRatio(2f / 3f)) {
            VodPoster(video.name, video.pic, year = video.year, sourceKey = video.sourceKey, tmdbCacheOnly = true)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.8f),
                        )
                    ),
            )
            RatingBadge(video, Modifier.align(Alignment.TopEnd))
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                Text(
                    text = video.name ?: "",
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = buildString {
                    if (video.year > 0) append(video.year)
                    if (!video.area.isNullOrBlank()) {
                        if (isNotEmpty()) append(" / ")
                        append(video.area)
                    }
                }
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun RatingBadge(video: Movie.Video, modifier: Modifier = Modifier) {
    val badge = ratingBadgeText(video.note) ?: return
    Text(
        text = badge,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        modifier = modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

private val RATING_SCORE_REGEX = Regex("评分[:：]?\\s*(\\d+(?:\\.\\d+)?)") // i18n: keep(源备注评分提取)
private val RATING_SCORE_SUFFIX_REGEX = Regex("^(\\d+(?:\\.\\d+)?)\\s*分$") // i18n: keep(源备注评分提取)

internal fun ratingBadgeText(note: String?): String? {
    val n = note?.trim().orEmpty()
    if (n.isEmpty()) return null
    val m = RATING_SCORE_REGEX.find(n)
        ?: RATING_SCORE_SUFFIX_REGEX.find(n)
    if (m != null) {
        val num = m.groupValues[1]
        return if (num.toFloatOrNull() == 0f) null else num
    }
    return n.takeIf { it.toFloatOrNull() != 0f }
}
