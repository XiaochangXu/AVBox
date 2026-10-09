@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.github.tvbox.osc.ui.activity

import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.tvbox.osc.R
import com.github.tvbox.osc.bean.Movie
import com.github.tvbox.osc.net.SearchSettings
import com.github.tvbox.osc.ui.components.PressableCard
import com.github.tvbox.osc.ui.components.VodPoster
import com.github.tvbox.osc.ui.theme.cardContainer

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HistoryChip(
    word: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Text(
            text = word,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

internal fun hideIme(activity: android.app.Activity?) {
    if (activity == null) return
    val view = activity.window?.currentFocus ?: return
    val imm = activity.getSystemService(InputMethodManager::class.java)
    imm?.hideSoftInputFromWindow(view.windowToken, 0)
}

@Composable
internal fun LayoutSwitchAction(
    selected: SearchSettings.SearchLayout,
    onSelect: (SearchSettings.SearchLayout) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Icon(
            painter = painterResource(R.drawable.ic_more_vert),
            contentDescription = stringResource(R.string.search_result_layout),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable { expanded = true }
                .padding(4.dp)
                .size(22.dp),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier
                    .width(156.dp)
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LayoutSwitchCard(
                    iconRes = R.drawable.ic_layout_horizontal,
                    label = stringResource(R.string.search_result_layout_horizontal),
                    selected = selected == SearchSettings.SearchLayout.Horizontal,
                    onClick = {
                        expanded = false
                        onSelect(SearchSettings.SearchLayout.Horizontal)
                    },
                )
                LayoutSwitchCard(
                    iconRes = R.drawable.ic_layout_vertical,
                    label = stringResource(R.string.search_result_layout_vertical),
                    selected = selected == SearchSettings.SearchLayout.Vertical,
                    onClick = {
                        expanded = false
                        onSelect(SearchSettings.SearchLayout.Vertical)
                    },
                )
            }
        }
    }
}

@Composable
internal fun LayoutSwitchCard(
    iconRes: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceBright,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

internal val SearchRailWidth = 140.dp

@Composable
internal fun RailResults(
    hits: List<SearchViewModel.SourceResult>,
    running: Boolean,
    selectedSource: String?,
    onSelectSource: (String?) -> Unit,
    topPad: Dp,
    railState: LazyListState,
    listState: LazyListState,
    onCardClick: (Movie.Video) -> Unit,
    onCardLongClick: (Movie.Video) -> Unit,
) {
    val rows = remember(hits, selectedSource) {
        hits
            .filter { selectedSource == null || it.sourceKey == selectedSource }
            .flatMap { result -> result.videos.map { result.sourceName to it } }
    }

    LaunchedEffect(selectedSource) {
        if (rows.isNotEmpty()) listState.scrollToItem(0)
    }

    Row(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = railState,
            modifier = Modifier.width(SearchRailWidth),
            contentPadding = PaddingValues(start = 12.dp, end = 10.dp, top = topPad + 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "rail_all") {
                SearchRailItem(
                    name = stringResource(R.string.common_all),
                    selected = selectedSource == null,
                    onClick = { onSelectSource(null) },
                )
            }
            items(hits, key = { "rail_${it.sourceKey}" }) { result ->
                SearchRailItem(
                    name = result.sourceName,
                    selected = selectedSource == result.sourceKey,
                    onClick = { onSelectSource(result.sourceKey) },
                )
            }
        }
        VerticalDivider(
            modifier = Modifier.padding(top = topPad + 8.dp, bottom = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 10.dp, end = 12.dp, top = topPad + 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (running) {
                item(key = "rail_progress") {
                    LinearWavyProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    )
                }
            }
            itemsIndexed(rows, key = { index, (_, video) -> "rail_row_${index}_${video.sourceKey}_${video.id}" }) { _, (siteName, video) ->
                SearchResultRow(
                    video = video,
                    siteName = siteName.takeIf { selectedSource == null },
                    onClick = { onCardClick(video) },
                    onLongClick = { onCardLongClick(video) },
                )
            }
            if (rows.isEmpty() && !running) {
                item(key = "rail_empty") {
                    Text(
                        text = stringResource(R.string.search_site_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun SearchRailItem(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceBright,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 10.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        )
    }
}

@Composable
internal fun SearchResultRow(
    video: Movie.Video,
    siteName: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    PressableCard(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .background(MaterialTheme.colorScheme.cardContainer),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VodPoster(
                name = video.name,
                pic = video.pic,
                year = video.year,
                sourceKey = video.sourceKey,
                tmdbCacheOnly = true,
                modifier = Modifier
                    .width(68.dp)
                    .fillMaxHeight(),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = video.name ?: "",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!video.note.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = video.note.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val meta = searchMetaText(video)
                    if (meta.isNotEmpty()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    if (siteName != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = siteName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .widthIn(max = 90.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

internal fun searchMetaText(video: Movie.Video): String = listOfNotNull(
    video.year.takeIf { it > 0 }?.toString(),
    video.area?.takeIf { it.isNotBlank() },
    video.type?.takeIf { it.isNotBlank() },
).joinToString(" · ")
