@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.github.tvbox.osc.ui.page

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.tvbox.osc.R
import com.github.tvbox.osc.io.FileUtils
import com.github.tvbox.osc.net.OkGoHelper
import com.github.tvbox.osc.player.effect.anime4k.Anime4kTier
import com.github.tvbox.osc.ui.activity.ConfigManageActivity
import com.github.tvbox.osc.ui.activity.PlaySettingsActivity
import com.github.tvbox.osc.ui.activity.PreferenceSettingsActivity
import com.github.tvbox.osc.ui.activity.ThemeSettingsActivity
import com.github.tvbox.osc.ui.components.AVBoxAlertDialog
import com.github.tvbox.osc.ui.components.AVBoxBottomSheet
import com.github.tvbox.osc.ui.components.AppTopBarScaffold
import com.github.tvbox.osc.ui.components.LocalSheetDismissThen
import com.github.tvbox.osc.ui.components.SettingsCard
import com.github.tvbox.osc.ui.components.SettingsCardPosition
import com.github.tvbox.osc.ui.components.SettingsGroup
import com.github.tvbox.osc.ui.components.SettingsOptionMenuRow
import com.github.tvbox.osc.ui.components.SettingsRow
import com.github.tvbox.osc.util.DefaultConfig
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.HistoryHelper
import com.github.tvbox.osc.util.HistoryMerge
import com.github.tvbox.osc.util.KV
import com.github.tvbox.osc.util.MusicSettings
import com.github.tvbox.osc.util.TmdbPoster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CACHE_SIZE_REFRESH_MIN_INTERVAL_MS = 60_000L

data class SettingsState(
    val playType: Int,
    val playRender: Int,
    val playScale: Int,
    val exoDecode: String,
    val anime4kTier: Anime4kTier,
    val kernelPrewarm: Boolean,
    val playTunnel: Boolean,
    val preferAac: Boolean,
    val musicPlayerPage: Boolean,
    val autoSwitchLine: Boolean,
    val m3u8Purify: Boolean,
    val incognito: Boolean,
    val hideStatusBar: Boolean,
    val gestureControlDisabled: Boolean,
    val navAnimationDisabled: Boolean,
    val navLiveHidden: Boolean,
    val collectColumns: Int,
    val danmuOpen: Boolean,
    val danmuApi: String,
    val defaultLoadLive: Boolean,
    val historyNumIndex: Int,
    val historyMerge: Boolean,
    val searchThreads: Int,
    val longPressSpeed: Int,
    val bufferTimes: Int,
    val preloadNextEpisode: Boolean,
    val preloadDuration: Int,
    val playCache: Boolean,
    val exoCacheSizeMb: Int,
    val dohIndex: Int,
    val cacheSizeText: String = "",
)

class SettingsViewModel : ViewModel() {
    private var cacheSizeText: String = ""
    private var cacheSizeRefreshedAt = 0L

    private val _state = mutableStateOf(loadState())

    init {
        refresh()
    }

    val state: androidx.compose.runtime.State<SettingsState> = _state

    fun refresh() {
        _state.value = loadState()
        refreshCacheSize()
    }

    fun refreshState() {
        _state.value = loadState()
    }

    fun refreshCacheSize() {
        cacheSizeRefreshedAt = SystemClock.elapsedRealtime()
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { FileUtils.formatCacheSize(FileUtils.getCacheSize()) }
            applyCacheSizeText(text)
        }
    }

    fun refreshCacheSizeIfStale() {
        if (SystemClock.elapsedRealtime() - cacheSizeRefreshedAt < CACHE_SIZE_REFRESH_MIN_INTERVAL_MS) return
        refreshCacheSize()
    }

    fun clearCache(onCleared: () -> Unit = {}) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                FileUtils.clearCache()
                TmdbPoster.clearCachedPosters()
                FileUtils.formatCacheSize(FileUtils.getCacheSize())
            }
            applyCacheSizeText(text)
            onCleared()
        }
    }

    private fun applyCacheSizeText(text: String) {
        if (text != cacheSizeText) {
            cacheSizeText = text
            _state.value = _state.value.copy(cacheSizeText = text)
        }
    }

    private fun loadState(): SettingsState = SettingsState(
        playType = KV.get(HawkConfig.PLAY_TYPE, 2),
        playRender = KV.get(HawkConfig.PLAY_RENDER, 1),
        playScale = KV.get(HawkConfig.PLAY_SCALE, 0),
        exoDecode = KV.get(HawkConfig.EXO_DECODE, "硬解码"), // i18n: keep
        anime4kTier = Anime4kTier.current(),
        kernelPrewarm = KV.get(HawkConfig.KERNEL_PREWARM, false),
        playTunnel = KV.get(HawkConfig.PLAY_TUNNEL, false),
        preferAac = KV.get(HawkConfig.PLAY_PREFER_AAC, false),
        musicPlayerPage = MusicSettings.autoOpenPage(),
        autoSwitchLine = KV.get(HawkConfig.AUTO_SWITCH_LINE, false),
        m3u8Purify = KV.get(HawkConfig.M3U8_PURIFY, false),
        incognito = KV.get(HawkConfig.INCOGNITO, false),
        hideStatusBar = KV.get(HawkConfig.HIDE_STATUS_BAR, false),
        gestureControlDisabled = KV.get(HawkConfig.GESTURE_CONTROL_DISABLED, false),
        navAnimationDisabled = KV.get(HawkConfig.NAV_ANIMATION_DISABLED, false),
        navLiveHidden = KV.get(HawkConfig.NAV_LIVE_HIDDEN, false),
        collectColumns = KV.get(HawkConfig.COLLECT_COLUMNS, 3),
        danmuOpen = KV.get(HawkConfig.DANMU_OPEN, true),
        danmuApi = KV.get(HawkConfig.DANMU_API, ""),
        defaultLoadLive = KV.get(HawkConfig.DEFAULT_LOAD_LIVE, false),
        historyNumIndex = KV.get(HawkConfig.HISTORY_NUM, 0),
        historyMerge = HistoryMerge.isEnabled(),
        searchThreads = KV.get(HawkConfig.SEARCH_THREADS, HawkConfig.SEARCH_THREADS_DEFAULT),
        longPressSpeed = KV.get(HawkConfig.LONG_PRESS_SPEED, HawkConfig.LONG_PRESS_SPEED_DEFAULT),
        bufferTimes = KV.get(HawkConfig.BUFFER_TIMES, HawkConfig.BUFFER_TIMES_DEFAULT),
        preloadNextEpisode = KV.get(HawkConfig.PRELOAD_NEXT_EPISODE, false),
        preloadDuration = KV.get(HawkConfig.PRELOAD_DURATION, HawkConfig.PRELOAD_DURATION_DEFAULT),
        playCache = KV.get(HawkConfig.PLAY_CACHE, false),
        exoCacheSizeMb = KV.get(HawkConfig.EXO_CACHE_SIZE_MB, HawkConfig.EXO_CACHE_SIZE_MB_DEFAULT),
        dohIndex = KV.get(HawkConfig.DOH_URL, 0),
        cacheSizeText = cacheSizeText,
    )

    fun <T> put(key: String, value: T) {
        KV.put(key, value)
        refresh()
    }
}

@Composable
fun SettingsPage(
    vm: SettingsViewModel = viewModel(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val navStart = contentPadding.calculateStartPadding(LocalLayoutDirection.current)
    val navBottom = contentPadding.calculateBottomPadding()
    val state by vm.state
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.refreshState()
        vm.refreshCacheSizeIfStale()
    }
    val context = LocalContext.current
    val versionName = remember { DefaultConfig.getAppVersionName(context) ?: "" }
    var aboutSheet by remember { mutableStateOf(false) }

    val listState = rememberScrollState()

    AppTopBarScaffold(
        topBarStartInset = navStart,
        titleContent = {
            Text(
                text = stringResource(R.string.tab_mine),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
    ) { topPad, _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(listState)
                .padding(horizontal = 16.dp)
                .padding(start = navStart, bottom = 8.dp + navBottom),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Spacer(Modifier.height(topPad - 20.dp))

            AppInfoHeaderCard(versionName = versionName)

            SettingsGroup(title = null) {
                SettingsCard(SettingsCardPosition.FIRST) {
                    SettingsRow(
                        title = stringResource(R.string.settings_config_manage),
                        subtitle = stringResource(R.string.settings_config_manage_subtitle),
                        iconRes = R.drawable.ic_settings_api,
                        onClick = { ConfigManageActivity.start(context) },
                    )
                }
                SettingsCard(SettingsCardPosition.MIDDLE) {
                    SettingsRow(
                        title = stringResource(R.string.settings_theme),
                        subtitle = stringResource(R.string.settings_theme_subtitle),
                        iconRes = R.drawable.ic_settings_theme,
                        onClick = { ThemeSettingsActivity.start(context) },
                    )
                }
                SettingsCard(SettingsCardPosition.MIDDLE) {
                    SettingsRow(
                        title = stringResource(R.string.settings_play),
                        subtitle = stringResource(R.string.settings_play_subtitle),
                        iconRes = R.drawable.ic_settings_play,
                        onClick = { PlaySettingsActivity.start(context) },
                    )
                }
                SettingsCard(SettingsCardPosition.LAST) {
                    SettingsRow(
                        title = stringResource(R.string.settings_preference_title),
                        subtitle = stringResource(R.string.settings_preference_subtitle),
                        iconRes = R.drawable.ic_settings_preference,
                        onClick = { PreferenceSettingsActivity.start(context) },
                    )
                }
            }

            SettingsGroup(title = null) {
                SettingsCard(SettingsCardPosition.FIRST) {
                    SettingsOptionMenuRow(
                        title = stringResource(R.string.settings_default_page),
                        subtitle = stringResource(R.string.settings_default_page_subtitle),
                        iconRes = R.drawable.ic_settings_start,
                        valueText = stringResource(if (state.defaultLoadLive) R.string.common_live else R.string.common_vod),
                        options = listOf(
                            stringResource(R.string.common_vod),
                            stringResource(R.string.common_live),
                        ),
                        selectedIndex = if (state.defaultLoadLive) 1 else 0,
                        onSelect = { idx -> vm.put(HawkConfig.DEFAULT_LOAD_LIVE, idx == 1) },
                    )
                }
                SettingsCard(SettingsCardPosition.MIDDLE) {
                    SettingsOptionMenuRow(
                        title = stringResource(R.string.settings_history_limit),
                        subtitle = stringResource(R.string.settings_history_limit_subtitle),
                        iconRes = R.drawable.ic_settings_history,
                        valueText = stringResource(
                            R.string.settings_history_limit_value,
                            HistoryHelper.getHisNum(state.historyNumIndex),
                        ),
                        options = listOf(0, 1, 2).map {
                            stringResource(R.string.settings_history_limit_value, HistoryHelper.getHisNum(it))
                        },
                        selectedIndex = state.historyNumIndex,
                        onSelect = { idx -> vm.put(HawkConfig.HISTORY_NUM, idx) },
                    )
                }
                SettingsCard(SettingsCardPosition.MIDDLE) {
                    var showClearCacheDialog by rememberSaveable { mutableStateOf(false) }
                    SettingsRow(
                        title = stringResource(R.string.settings_clear_cache),
                        subtitle = stringResource(R.string.settings_clear_cache_subtitle),
                        iconRes = R.drawable.ic_delete,
                        valueText = state.cacheSizeText,
                        onClick = { showClearCacheDialog = true },
                    )
                    if (showClearCacheDialog) {
                        val dismissThen = LocalSheetDismissThen.current
                        AVBoxAlertDialog(
                            onDismissRequest = { showClearCacheDialog = false },
                            title = { Text(stringResource(R.string.settings_clear_cache)) },
                            text = { Text(stringResource(R.string.settings_clear_cache_confirm_text)) },
                            dismissButton = {
                                TextButton(onClick = { showClearCacheDialog = false }) {
                                    Text(stringResource(R.string.common_cancel))
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    showClearCacheDialog = false
                                    dismissThen {
                                        vm.clearCache {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.toast_cache_cleared),
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        }
                                    }
                                }) {
                                    Text(stringResource(R.string.common_confirm))
                                }
                            },
                        )
                    }
                }
                SettingsCard(SettingsCardPosition.LAST) {
                    SettingsOptionMenuRow(
                        title = "DOH",
                        subtitle = stringResource(R.string.settings_doh_subtitle),
                        iconRes = R.drawable.ic_settings_doh,
                        valueText = if (state.dohIndex == 0) stringResource(R.string.common_off)
                        else OkGoHelper.dnsHttpsList.getOrNull(state.dohIndex)
                            ?: stringResource(R.string.common_off),
                        options = OkGoHelper.dnsHttpsList.mapIndexed { index, name ->
                            if (index == 0) context.getString(R.string.common_off) else name
                        },
                        selectedIndex = state.dohIndex,
                        onSelect = { idx -> vm.put(HawkConfig.DOH_URL, idx) },
                    )
                }
            }

            SettingsGroup(title = null) {
                SettingsCard(SettingsCardPosition.FIRST) {
                    SettingsRow(
                        title = stringResource(R.string.settings_about),
                        subtitle = stringResource(R.string.settings_about_subtitle),
                        iconRes = R.drawable.ic_settings_about,
                        onClick = { aboutSheet = true },
                    )
                }

                SettingsCard(SettingsCardPosition.LAST) {
                    SettingsRow(
                        title = stringResource(R.string.settings_github),
                        subtitle = stringResource(R.string.settings_github_subtitle),
                        iconRes = R.drawable.ic_settings_github,
                        onClick = { openExternalUrl(context, GITHUB_REPO_URL) },
                    )
                }
            }
        }
    }

    if (aboutSheet) {
        AboutSheet(versionName = versionName, onDismiss = { aboutSheet = false })
    }

}

@Composable
private fun AboutSheet(versionName: String, onDismiss: () -> Unit) {
    AVBoxBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.settings_about)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            if (versionName.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.settings_about_version, versionName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.settings_about_disclaimer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private const val GITHUB_REPO_URL = "https://github.com/XiaochangXu/AVBox"

private fun openExternalUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.toast_no_app_for_link), Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun TextEditDialog(
    title: String,
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initialText) }
    AVBoxAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.dialog_api_url_hint)) },
            )
        },
        confirmButton = {
            val dismissThen = LocalSheetDismissThen.current
            TextButton(onClick = { dismissThen { onConfirm(text.trim()) } }) {
                Text(stringResource(R.string.common_confirm))
            }
        },
    )
}
