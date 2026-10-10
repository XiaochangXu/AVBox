@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.github.tvbox.osc.ui.page

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.data.AppGraph
import com.github.tvbox.osc.data.CollectRepository
import com.github.tvbox.osc.data.VodCollect
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.ui.WindowSize
import com.github.tvbox.osc.ui.components.LoadState
import com.github.tvbox.osc.ui.components.LoadStateBox
import com.github.tvbox.osc.ui.components.VodPoster
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.KV
import com.github.tvbox.osc.util.SubscribeList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class CollectViewModel(
    private val collect: CollectRepository = AppGraph.collectRepository,
) : ViewModel() {
    val loading = MutableStateFlow(true)
    val items = MutableStateFlow<List<VodCollect>>(emptyList())

    val columns = MutableStateFlow(KV.get(HawkConfig.COLLECT_COLUMNS, 3))

    val unavailableKeys = MutableStateFlow<Set<String>>(emptySet())

    init {
        EventBus.getDefault().register(this)
        refresh()
        viewModelScope.launch {
            AppBootstrap.state.collect { boot ->
                if (boot is AppBootstrap.Boot.Ready) {
                    viewModelScope.launch(Dispatchers.IO) { recomputeUnavailableNow() }
                }
            }
        }
    }

    override fun onCleared() {
        EventBus.getDefault().unregister(this)
    }

    val scrollSignal = MutableStateFlow(0)

    val placementAnim = MutableStateFlow(false)

    fun refresh(scrollToTop: Boolean = false) {
        if (scrollToTop) placementAnim.value = false
        viewModelScope.launch(Dispatchers.IO) {
            items.value = collect.getAllVodCollect()
            recomputeUnavailableNow()
            loading.value = false
            if (scrollToTop) scrollSignal.value++
        }
    }

    private fun recomputeUnavailableNow() {
        if (AppBootstrap.state.value !is AppBootstrap.Boot.Ready) return
        unavailableKeys.value = computeUnavailable(items.value)
    }

    private fun computeUnavailable(list: List<VodCollect>): Set<String> {
        val current = collect.currentCid()
        val known = SubscribeList.vodUrls()
        return list.filter { item ->
            val cid = item.cid.orEmpty()
            if (cid.isNotEmpty() && !known.contains(cid)) {
                true
            } else {
                (cid.isEmpty() || cid == current) && ApiConfig.get().getSource(item.sourceKey) == null
            }
        }.mapNotNull { it.sourceKey }.toSet()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onRefreshEvent(event: RefreshEvent) {
        if (event.type == RefreshEvent.TYPE_COLLECT_REFRESH) {
            refresh(scrollToTop = true)
        } else if (event.type == RefreshEvent.TYPE_API_URL_CHANGE) {
            viewModelScope.launch(Dispatchers.IO) { recomputeUnavailableNow() }
        } else if (event.type == RefreshEvent.TYPE_COLLECT_LAYOUT_CHANGE) {
            columns.value = KV.get(HawkConfig.COLLECT_COLUMNS, 3)
        }
    }

    fun deleteSelected(list: List<VodCollect>) {
        if (list.isEmpty()) return
        placementAnim.value = true
        viewModelScope.launch(Dispatchers.IO) {
            list.forEach { item -> collect.deleteVodCollect(item.id) }
            refresh()
        }
    }

    fun deleteAll() {
        viewModelScope.launch(Dispatchers.IO) {
            collect.deleteVodCollectAll()
            refresh()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CollectTab(
    vm: CollectViewModel,
    listState: LazyGridState,
    editMode: Boolean,
    selected: Set<Int>,
    onToggleSelected: (Int) -> Unit,
    onRequestDelete: (VodCollect) -> Unit,
    navStart: Dp,
    navBottom: Dp,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by vm.items.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val placementAnim by vm.placementAnim.collectAsStateWithLifecycle()
    val unavailableKeys by vm.unavailableKeys.collectAsStateWithLifecycle()
    val columns by vm.columns.collectAsStateWithLifecycle()

    when {
        loading -> Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = navBottom),
            contentAlignment = Alignment.Center,
        ) {
            ContainedLoadingIndicator(Modifier.size(64.dp))
        }

        else -> AnimatedContent(
            targetState = items,
            contentKey = { it.isEmpty() },
            transitionSpec = {
                fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                    fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            },
            label = "collectContent",
        ) { list ->
            if (list.isEmpty()) {
                LoadStateBox(
                    state = LoadState.Empty,
                    emptyText = stringResource(R.string.collect_empty),
                    errorText = "",
                    retryText = "",
                    emptyIconRes = R.drawable.ic_empty_record,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = navBottom),
                )
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val gridColumns = WindowSize.gridColumns(
                        availableWidthDp = (maxWidth - 32.dp - navStart).value.toInt(),
                        minColumns = columns,
                    )
                    LazyVerticalGrid(
                        state = listState,
                        columns = GridCells.Fixed(gridColumns),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp + navStart,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = 8.dp + navBottom,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(list, key = { it.id }) { item ->
                            CollectCard(
                                item = item,
                                unavailable = item.sourceKey?.let { unavailableKeys.contains(it) } == true,
                                editMode = editMode,
                                selected = item.id in selected,
                                modifier = Modifier.animateItem(
                                    fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    placementSpec = if (placementAnim) {
                                        spring(stiffness = Spring.StiffnessMediumLow)
                                    } else {
                                        null
                                    },
                                    fadeOutSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                ),
                                onClick = {
                                    if (editMode) {
                                        onToggleSelected(item.id)
                                    } else {
                                        val cid = item.cid.orEmpty()
                                        when {
                                            cid.isEmpty() || cid == AppGraph.collectRepository.currentCid() ->
                                                context.jumpToDetail(
                                                    item.vodId, item.sourceKey, item.name, item.pic, collect = true,
                                                )

                                            SubscribeList.vodUrls().contains(cid) ->
                                                scope.launch {
                                                    reopenViaSubscription(
                                                        context = context,
                                                        cid = cid,
                                                        sourceKey = item.sourceKey,
                                                        vodId = item.vodId,
                                                        name = item.name,
                                                        pic = item.pic,
                                                        collect = true,
                                                    )
                                                }

                                            else -> context.jumpToSearch(item.name.orEmpty())
                                        }
                                    }
                                },
                                onLongClick = {
                                    if (editMode) onToggleSelected(item.id) else onRequestDelete(item)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollectCard(
    item: VodCollect,
    unavailable: Boolean,
    editMode: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        ) {
            VodPoster(name = item.name, pic = item.pic, sourceKey = item.sourceKey, tmdbCacheOnly = true, modifier = Modifier.fillMaxSize())
            if (editMode) {
                SelectCircle(
                    selected = selected,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                )
            }
            if (unavailable) {
                Text(
                    text = stringResource(R.string.source_unavailable),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.85f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            text = item.name ?: "",
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
}


