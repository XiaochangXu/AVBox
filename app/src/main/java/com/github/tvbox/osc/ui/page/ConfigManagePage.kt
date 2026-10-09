package com.github.tvbox.osc.ui.page

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.ui.activity.ConfigManageActivity
import com.github.tvbox.osc.ui.components.AVBoxAlertDialog
import com.github.tvbox.osc.ui.components.AVBoxBottomSheet
import com.github.tvbox.osc.ui.components.CapsuleSegmentedButton
import com.github.tvbox.osc.ui.components.LoadState
import com.github.tvbox.osc.ui.components.AppTopBarScaffold
import com.github.tvbox.osc.ui.components.LoadStateBox
import com.github.tvbox.osc.ui.components.LocalSheetDismiss
import com.github.tvbox.osc.ui.components.LocalSheetDismissThen
import com.github.tvbox.osc.ui.components.SegmentOption
import com.github.tvbox.osc.ui.components.SettingsCard
import com.github.tvbox.osc.ui.components.SettingsCardPosition
import com.github.tvbox.osc.ui.components.SettingsGroup
import com.github.tvbox.osc.ui.components.RowLeadingIcon
import com.github.tvbox.osc.ui.components.SettingsOptionRow
import com.github.tvbox.osc.ui.components.SettingsSwitch
import com.github.tvbox.osc.ui.components.SettingsSwitchRow
import com.github.tvbox.osc.ui.components.TopBarActionBox
import com.github.tvbox.osc.ui.components.glassSurface
import com.github.tvbox.osc.ui.theme.cardContainer
import com.github.tvbox.osc.util.HistoryHelper
import com.github.tvbox.osc.util.TmdbApi

private fun badgeText(name: String, url: String, emptyText: String): String = when {
    name.isNotEmpty() -> name
    url.isEmpty() -> emptyText
    else -> url.substringAfter("://").substringBefore('/').ifEmpty { url }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConfigManageScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val vm: ConfigManageViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ConfigManageViewModel { TmdbApi.DefaultTmdbClient() } }
        },
    )
    var mode by rememberSaveable { mutableStateOf(ConfigMode.Vod) }
    var addDialogOpen by remember { mutableStateOf(false) }
    var repoSheetOpen by remember { mutableStateOf(false) }
    val vodItems by vm.vodItems.collectAsState()
    val liveItems by vm.liveItems.collectAsState()
    val activeUrl by vm.activeUrl.collectAsState()
    val liveActiveUrl by vm.liveActiveUrl.collectAsState()
    val liveFollow by vm.liveFollow.collectAsState()
    val disabledUrls by vm.disabledUrls.collectAsState()
    val pendingSwitch by vm.pendingSwitch.collectAsState()
    val selected by vm.selected.collectAsState()
    val manageMode by vm.manageMode.collectAsState()
    val editTarget by vm.editTarget.collectAsState()
    val toastEvent by vm.toastEvent.collectAsState()
    val tmdbState by vm.tmdbState().collectAsState()
    val tmdbTesting by vm.tmdbTesting().collectAsState()

    val isVod = mode == ConfigMode.Vod
    val isTmdb = mode == ConfigMode.Tmdb
    val currentItems = if (isVod) vodItems else liveItems

    LaunchedEffect(toastEvent) {
        toastEvent?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.clearToast()
        }
    }

    LaunchedEffect(mode) {
        vm.onModeChanged()
        repoSheetOpen = false
    }

    BackHandler(enabled = manageMode) { vm.exitManageMode() }

    val canSwitchRepo = !isTmdb && if (isVod) {
        HistoryHelper.isApiLineUrl(activeUrl)
    } else {
        ApiConfig.get().isLiveApiLineMode() && HistoryHelper.isLiveApiLineUrl(liveActiveUrl)
    }

    val repoEntries = if (isVod) HistoryHelper.getApiLines() else HistoryHelper.getLiveApiLines()

    val repoActiveUrl = if (isVod) activeUrl else liveActiveUrl

    val noSourceText = stringResource(R.string.config_no_source)
    val vodBadge = remember(vodItems, activeUrl, noSourceText) {
        badgeText(
            vodItems.firstOrNull { parseSubscribe(it).url == activeUrl }?.let { parseSubscribe(it).name }.orEmpty(),
            activeUrl,
            noSourceText,
        )
    }
    AppTopBarScaffold(
        titleContent = {
            Text(
                text = stringResource(R.string.settings_config_manage),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        navigationIcon = {
            TopBarActionBox(
                R.drawable.ic_arrow_left,
                stringResource(R.string.common_back),
                onClick = { if (manageMode) vm.exitManageMode() else onNavigateBack() },
            )
        },
        actions = {
            AnimatedContent(
                targetState = manageMode && currentItems.isNotEmpty() && !isTmdb,
                transitionSpec = {
                    (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                        scaleIn(initialScale = 0.8f, animationSpec = spring(stiffness = Spring.StiffnessMedium)))
                        .togetherWith(fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)))
                },
                label = "configTopAction",
            ) { managing ->
                if (managing) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ManageActionIcon(
                            iconRes = R.drawable.ic_edit,
                            contentDescription = stringResource(R.string.common_edit),
                            enabled = selected.size == 1,
                            onClick = { vm.editTarget.value = selected.firstOrNull()?.let { parseSubscribe(it) } },
                        )
                        ManageActionIcon(
                            iconRes = R.drawable.ic_delete,
                            contentDescription = stringResource(R.string.common_delete),
                            enabled = selected.isNotEmpty(),
                            onClick = {
                                vm.deleteSelected(
                                    if (isVod) SubscribeMode.Vod else SubscribeMode.Live,
                                )
                            },
                        )
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!isTmdb) {
                            if (canSwitchRepo) {
                                TopBarActionBox(
                                    iconRes = R.drawable.ic_switch_repo,
                                    contentDescription = stringResource(R.string.config_switch_repo),
                                    onClick = { repoSheetOpen = true },
                                )
                            }
                            TopBarActionBox(
                                iconRes = R.drawable.ic_subscribe_add,
                                contentDescription = if (isVod) {
                                    stringResource(R.string.config_add_subscribe)
                                } else {
                                    stringResource(R.string.config_add_live_source)
                                },
                                onClick = { addDialogOpen = true },
                            )
                        }
                    }
                }
            }
        },
    ) { topPad, _ ->
        Column(modifier = Modifier.fillMaxSize()) {
            CapsuleSegmentedButton(
                options = listOf(
                    SegmentOption(
                        label = stringResource(R.string.common_vod),
                        value = ConfigMode.Vod,
                        iconPainter = painterResource(R.drawable.ic_config_vod),
                    ),
                    SegmentOption(
                        label = stringResource(R.string.common_tmdb),
                        value = ConfigMode.Tmdb,
                        iconPainter = painterResource(R.drawable.ic_config_tmdb),
                    ),
                    SegmentOption(
                        label = stringResource(R.string.common_live),
                        value = ConfigMode.Live,
                        iconPainter = painterResource(R.drawable.ic_config_live),
                    ),
                ),
                selectedValue = mode,
                onOptionSelected = { mode = it },
                containerColor = MaterialTheme.colorScheme.surfaceBright,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = topPad + 8.dp),
            )
            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    val toRight = targetState.ordinal > initialState.ordinal
                    (
                        slideInHorizontally(spring(stiffness = Spring.StiffnessMedium)) { full ->
                            if (toRight) full / 4 else -full / 4
                        } + fadeIn(spring(stiffness = Spring.StiffnessMedium))
                        ).togetherWith(
                        slideOutHorizontally(spring(stiffness = Spring.StiffnessMedium)) { full ->
                            if (toRight) -full / 4 else full / 4
                        } + fadeOut(spring(stiffness = Spring.StiffnessMedium))
                    )
                },
                label = "configSegment",
            ) { m ->
                val mIsVod = m == ConfigMode.Vod
                val mItems = if (mIsVod) vodItems else liveItems
                if (m == ConfigMode.Tmdb) {
                    ConfigTmdbScreen(
                        state = tmdbState,
                        actions = remember(vm) { vm.tmdbActions() },
                        testing = tmdbTesting,
                    )
                } else if (mItems.isEmpty()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (!mIsVod) {
                            FollowVodCard(
                                checked = liveFollow,
                                subtitle = if (activeUrl.isEmpty()) {
                                    stringResource(R.string.config_no_vod_source)
                                } else {
                                    stringResource(R.string.config_current_vod_source, vodBadge)
                                },
                                onFollow = { vm.followLiveNow() },
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp),
                            )
                        }
                        LoadStateBox(
                            state = LoadState.Empty,
                            emptyText = stringResource(
                                if (mIsVod) R.string.config_empty_subscribe
                                else R.string.config_empty_live_source
                            ),
                            errorText = "",
                            retryText = "",
                            emptyIconRes = R.drawable.ic_empty_record,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                    }
                } else {
                    val mOrdered = remember(mItems, activeUrl, liveActiveUrl, liveFollow, mIsVod) {
                        mItems.sortedByDescending {
                            val url = parseSubscribe(it).url
                            if (mIsVod) url == activeUrl else !liveFollow && url == liveActiveUrl
                        }
                    }
                    LazyColumn(
                        state = rememberLazyListState(),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 24.dp,
                            bottom = 8.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (!mIsVod) {
                            item(key = "Live#follow") {
                                FollowVodCard(
                                    checked = liveFollow,
                                    subtitle = if (activeUrl.isEmpty()) {
                                        stringResource(R.string.config_no_vod_source)
                                    } else {
                                        stringResource(R.string.config_current_vod_source, vodBadge)
                                    },
                                    onFollow = { vm.followLiveNow() },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                        items(mOrdered, key = { "${m.name}#$it" }) { value ->
                            val item = parseSubscribe(value)
                            val inUse = if (mIsVod) {
                                item.url == activeUrl || HistoryHelper.isApiLineSourceOf(item.url, activeUrl)
                            } else {
                                !liveFollow && (
                                    item.url == liveActiveUrl ||
                                        HistoryHelper.isLiveApiLineSourceOf(item.url, liveActiveUrl)
                                    )
                            }
                            SubscribeCard(
                                modifier = Modifier.animateItem(),
                                item = item,
                                active = inUse,
                                disabled = item.url in disabledUrls,
                                manageMode = manageMode,
                                selected = value in selected,
                                onClick = {
                                    if (manageMode) {
                                        vm.toggleSelected(value)
                                    } else {
                                        vm.requestSwitch(item, mIsVod)
                                    }
                                },
                                onLongClick = {
                                    vm.longPressSelect(value)
                                },
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        vm.requestSwitch(item, mIsVod)
                                    } else if (!mIsVod) {
                                        vm.followLiveNow()
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    val editing = editTarget
    if (addDialogOpen || editing != null) {
        AddSubscribeDialog(
            title = if (editing != null) {
                if (isVod) stringResource(R.string.config_edit_subscribe) else stringResource(R.string.config_edit_live_source)
            } else {
                if (isVod) stringResource(R.string.config_add_subscribe) else stringResource(R.string.config_add_live_source)
            },
            urlSupportingText = if (isVod) "" else stringResource(R.string.config_live_source_hint),
            initialName = editing?.name.orEmpty(),
            initialUrl = editing?.url.orEmpty(),
            onDismiss = {
                addDialogOpen = false
                vm.editTarget.value = null
            },
            onSave = { name, url ->
                if (editing != null) vm.commitEdit(isVod, editing, name, url) else vm.commitAdd(isVod, name, url)
                addDialogOpen = false
            },
            onPickFile = { onPicked ->
                (context as? ConfigManageActivity)?.launchLocalConfig { api -> onPicked(api) }
            },
        )
    }

    val pending = pendingSwitch
    if (pending != null) {
        AVBoxAlertDialog(
            onDismissRequest = { vm.cancelPendingSwitch() },
            title = { Text(stringResource(R.string.dialog_source_disabled_title)) },
            text = {
                Text(stringResource(R.string.dialog_source_disabled_message, pending.item.name))
            },
            confirmButton = {
                val dismissThen = LocalSheetDismissThen.current
                TextButton(onClick = { dismissThen { vm.enableAndSwitch() } }) {
                    Text(stringResource(R.string.dialog_source_disabled_confirm))
                }
            },
            dismissButton = {
                val dismissAnimated = LocalSheetDismiss.current
                TextButton(onClick = { dismissAnimated() }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    if (repoSheetOpen) {
        RepoSwitchSheet(
            entries = repoEntries,
            activeUrl = repoActiveUrl,
            disabledUrls = disabledUrls,
            onDismiss = { repoSheetOpen = false },
            onSelect = { url ->
                val name = HistoryHelper.getApiLineName(
                    repoEntries.firstOrNull { HistoryHelper.getApiLineUrl(it) == url }.orEmpty(),
                )
                vm.requestSwitch(SubscribeSource(name, url), isVod)
                if (vm.pendingSwitch.value != null) repoSheetOpen = false
            },
        )
    }
}

@Composable
private fun RepoSwitchSheet(
    entries: List<String>,
    activeUrl: String,
    disabledUrls: Set<String>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val dismissAnimated = LocalSheetDismiss.current
    var accepted by remember { mutableStateOf(false) }
    AVBoxBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.config_switch_repo),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        SettingsGroup(
            title = null,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            entries.forEachIndexed { index, entry ->
                val url = HistoryHelper.getApiLineUrl(entry)
                SettingsCard(
                    position = when {
                        entries.size <= 1 -> SettingsCardPosition.SINGLE
                        index == 0 -> SettingsCardPosition.FIRST
                        index == entries.size - 1 -> SettingsCardPosition.LAST
                        else -> SettingsCardPosition.MIDDLE
                    },
                    color = MaterialTheme.colorScheme.surfaceBright,
                ) {
                    SettingsOptionRow(
                        title = HistoryHelper.getApiLineName(entry),
                        selected = url == activeUrl,
                        onClick = onClick@{
                            if (accepted) return@onClick
                            accepted = true
                            if (url.isNotEmpty() && url != activeUrl) onSelect(url)
                            dismissAnimated()
                        },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (url in disabledUrls) {
                                    DisabledSourceTag()
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    text = url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 180.dp),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FollowVodCard(
    checked: Boolean,
    subtitle: String,
    onFollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsCard(position = SettingsCardPosition.SINGLE, modifier = modifier) {
        SettingsSwitchRow(
            title = stringResource(R.string.live_follow_vod_source),
            leadingIconRes = R.drawable.ic_subscribe_source,
            subtitle = subtitle,
            checked = checked,
            onCheckedChange = { next -> if (next) onFollow() },
        )
    }
}

@Composable
private fun SubscribeCard(
    item: SubscribeSource,
    active: Boolean,
    disabled: Boolean,
    manageMode: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.cardContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowLeadingIcon(R.drawable.ic_subscribe_source, enabled = true)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (disabled) {
                        Spacer(Modifier.width(8.dp))
                        DisabledSourceTag()
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.url,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            AnimatedContent(
                targetState = manageMode,
                transitionSpec = {
                    (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                        scaleIn(initialScale = 0.7f, animationSpec = spring(stiffness = Spring.StiffnessMedium)))
                        .togetherWith(fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)))
                },
                label = "configRowControl",
            ) { managing ->
                if (managing) {
                    Checkbox(checked = selected, onCheckedChange = { onClick() })
                } else {
                    SettingsSwitch(checked = active, onCheckedChange = onCheckedChange)
                }
            }
        }
    }
}

@Composable
private fun DisabledSourceTag() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            text = stringResource(R.string.config_source_disabled_tag),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun AddSubscribeDialog(
    title: String,
    urlSupportingText: String,
    initialName: String,
    initialUrl: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onPickFile: (onPicked: (String) -> Unit) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var url by remember { mutableStateOf(initialUrl) }
    val urlHint: (@Composable () -> Unit)? = if (urlSupportingText.isEmpty()) {
        null
    } else {
        {
            Text(
                text = urlSupportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    AVBoxAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .glassSurface(CircleShape, MaterialTheme.colorScheme.surfaceBright)
                        .clickable { onPickFile { picked -> url = picked } },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_file_choose),
                        contentDescription = stringResource(R.string.config_pick_local),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.config_field_name)) },
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.config_field_url)) },
                    supportingText = urlHint,
                )
            }
        },
        confirmButton = {
            val dismissThen = LocalSheetDismissThen.current
            TextButton(
                onClick = { dismissThen { onSave(name.trim(), url.trim()) } },
                enabled = url.isNotBlank(),
            ) { Text(stringResource(R.string.common_save)) }
        },
    )
}
