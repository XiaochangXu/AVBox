package com.github.tvbox.osc.ui.page

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.tvbox.osc.R
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.ui.components.AVBoxAlertDialog
import com.github.tvbox.osc.ui.components.AppTopBarScaffold
import com.github.tvbox.osc.ui.components.LocalSheetDismiss
import com.github.tvbox.osc.ui.components.LocalSheetDismissThen
import com.github.tvbox.osc.ui.components.SettingsCard
import com.github.tvbox.osc.ui.components.SettingsCardPosition
import com.github.tvbox.osc.ui.components.SettingsGroup
import com.github.tvbox.osc.ui.components.SettingsOptionMenuRow
import com.github.tvbox.osc.ui.components.SettingsRow
import com.github.tvbox.osc.ui.components.SettingsSliderRow
import com.github.tvbox.osc.ui.components.SettingsSwitchRow
import com.github.tvbox.osc.ui.components.TopBarActionBox
import com.github.tvbox.osc.util.AppLanguage
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.HistoryMerge
import com.github.tvbox.osc.util.LanguageManager
import com.github.tvbox.osc.util.restartApp
import kotlin.math.roundToInt
import org.greenrobot.eventbus.EventBus

@Composable
fun PreferenceSettingsScreen(onNavigateBack: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val state by vm.state
    var sliderSpeed by remember(state.longPressSpeed) { mutableStateOf(state.longPressSpeed) }
    var sliderBuffer by remember(state.bufferTimes) { mutableStateOf(state.bufferTimes) }
    var sliderThreads by remember(state.searchThreads) { mutableStateOf(state.searchThreads) }
    var danmuApiDialog by remember { mutableStateOf(false) }

    val listState = rememberScrollState()
    AppTopBarScaffold(
        titleContent = {
            Text(
                text = stringResource(R.string.settings_preference_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        navigationIcon = {
            TopBarActionBox(R.drawable.ic_arrow_left, stringResource(R.string.common_back), onClick = onNavigateBack)
        },
    ) { topPad, _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(listState)
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
        ) {
            Spacer(Modifier.height(topPad + 8.dp))

            SettingsGroup(title = stringResource(R.string.settings_group_language_layout)) {
                SettingsCard(SettingsCardPosition.FIRST) {
                    LanguageRow()
                }
                SettingsCard(SettingsCardPosition.LAST) {
                    CollectColumnsRow(
                        columns = state.collectColumns,
                        onSelect = { columns ->
                            vm.put(HawkConfig.COLLECT_COLUMNS, columns)
                            EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_COLLECT_LAYOUT_CHANGE))
                        },
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            SettingsGroup(title = stringResource(R.string.settings_group_privacy)) {
                SettingsCard(SettingsCardPosition.FIRST) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_history_merge),
                        leadingIconRes = R.drawable.ic_pref_history_merge,
                        checked = state.historyMerge,
                        onCheckedChange = {
                            HistoryMerge.setEnabled(it)
                            vm.refresh()
                            EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_HISTORY_REFRESH))
                        },
                    )
                }
                SettingsCard(SettingsCardPosition.MIDDLE) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_incognito),
                        leadingIconRes = R.drawable.ic_pref_incognito,
                        checked = state.incognito,
                        onCheckedChange = {
                            vm.put(HawkConfig.INCOGNITO, it)
                            EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_HISTORY_REFRESH))
                        },
                    )
                }
                SettingsCard(SettingsCardPosition.MIDDLE) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_hide_status_bar),
                        leadingIconRes = R.drawable.ic_pref_hide_status_bar,
                        subtitle = stringResource(R.string.settings_hide_status_bar_subtitle),
                        checked = state.hideStatusBar,
                        onCheckedChange = { vm.put(HawkConfig.HIDE_STATUS_BAR, it) },
                    )
                }
                SettingsCard(SettingsCardPosition.MIDDLE) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_gesture_disable),
                        leadingIconRes = R.drawable.ic_pref_gesture,
                        subtitle = stringResource(R.string.settings_gesture_disable_subtitle),
                        checked = state.gestureControlDisabled,
                        onCheckedChange = { vm.put(HawkConfig.GESTURE_CONTROL_DISABLED, it) },
                    )
                }
                SettingsCard(SettingsCardPosition.MIDDLE) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_nav_animation_disable),
                        leadingIconRes = R.drawable.ic_pref_nav_animation,
                        subtitle = stringResource(R.string.settings_nav_animation_disable_subtitle),
                        checked = state.navAnimationDisabled,
                        onCheckedChange = { vm.put(HawkConfig.NAV_ANIMATION_DISABLED, it) },
                    )
                }
                SettingsCard(SettingsCardPosition.LAST) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_nav_live_hidden),
                        leadingIconRes = R.drawable.ic_pref_nav_live_hidden,
                        subtitle = stringResource(R.string.settings_nav_live_hidden_subtitle),
                        checked = state.navLiveHidden,
                        onCheckedChange = { vm.put(HawkConfig.NAV_LIVE_HIDDEN, it) },
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            SettingsGroup(title = stringResource(R.string.settings_group_play_search)) {
                SettingsCard(SettingsCardPosition.FIRST) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_auto_switch_line),
                        leadingIconRes = R.drawable.ic_pref_auto_switch_line,
                        checked = state.autoSwitchLine,
                        onCheckedChange = { vm.put(HawkConfig.AUTO_SWITCH_LINE, it) },
                    )
                }
                SettingsCard(SettingsCardPosition.MIDDLE) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_m3u8_purify),
                        leadingIconRes = R.drawable.ic_pref_m3u8_purify,
                        checked = state.m3u8Purify,
                        onCheckedChange = { vm.put(HawkConfig.M3U8_PURIFY, it) },
                    )
                }
                SettingsCard(SettingsCardPosition.MIDDLE) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_danmu_switch),
                        leadingIconRes = R.drawable.ic_pref_danmu,
                        checked = state.danmuOpen,
                        onCheckedChange = { vm.put(HawkConfig.DANMU_OPEN, it) },
                    )
                }
                SettingsCard(SettingsCardPosition.MIDDLE) {
                    SettingsRow(
                        title = stringResource(R.string.settings_danmu_api),
                        leadingIconRes = R.drawable.ic_pref_danmu_api,
                        valueText = stringResource(if (state.danmuApi.isEmpty()) R.string.common_not_set else R.string.common_set),
                        onClick = { danmuApiDialog = true },
                    )
                }
                SettingsCard(SettingsCardPosition.MIDDLE) {
                    SettingsSliderRow(
                        title = stringResource(R.string.settings_long_press_speed),
                        leadingIconRes = R.drawable.ic_pref_long_press_speed,
                        value = sliderSpeed.toFloat(),
                        valueText = "${sliderSpeed}x",
                        valueRange = 2f..10f,
                        steps = 7,
                        onValueChange = { sliderSpeed = (it - 2).roundToInt() + 2 },
                        onValueChangeFinished = {
                            if (sliderSpeed != state.longPressSpeed) {
                                vm.put(HawkConfig.LONG_PRESS_SPEED, sliderSpeed)
                            }
                        },
                    )
                }
                SettingsCard(SettingsCardPosition.MIDDLE) {
                    SettingsSliderRow(
                        title = stringResource(R.string.settings_buffer_time),
                        leadingIconRes = R.drawable.ic_pref_buffer_time,
                        value = sliderBuffer.toFloat(),
                        valueText = "${sliderBuffer}x",
                        valueRange = 1f..10f,
                        steps = 8,
                        onValueChange = { sliderBuffer = (it - 1).roundToInt() + 1 },
                        onValueChangeFinished = {
                            if (sliderBuffer != state.bufferTimes) {
                                vm.put(HawkConfig.BUFFER_TIMES, sliderBuffer)
                            }
                        },
                    )
                }
                SettingsCard(SettingsCardPosition.LAST) {
                    SettingsSliderRow(
                        title = stringResource(R.string.settings_search_threads),
                        leadingIconRes = R.drawable.ic_pref_search_threads,
                        value = sliderThreads.toFloat(),
                        valueText = "$sliderThreads",
                        valueRange = 16f..64f,
                        steps = 2,
                        onValueChange = { sliderThreads = ((it - 16) / 16).roundToInt() * 16 + 16 },
                        onValueChangeFinished = {
                            if (sliderThreads != state.searchThreads) {
                                vm.put(HawkConfig.SEARCH_THREADS, sliderThreads)
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(64.dp))
        }
    }

    if (danmuApiDialog) {
        TextEditDialog(
            title = stringResource(R.string.settings_danmu_api),
            initialText = state.danmuApi,
            onDismiss = { danmuApiDialog = false },
            onConfirm = { text ->
                vm.put(HawkConfig.DANMU_API, text)
                danmuApiDialog = false
            },
        )
    }
}

@Composable
private fun CollectColumnsRow(columns: Int, onSelect: (Int) -> Unit) {
    val options = listOf(
        stringResource(R.string.settings_collect_columns_three),
        stringResource(R.string.settings_collect_columns_two),
    )
    val selectedIndex = if (columns == 3) 0 else 1
    SettingsOptionMenuRow(
        title = stringResource(R.string.settings_collect_columns),
        leadingIconRes = R.drawable.ic_pref_collect_columns,
        valueText = options[selectedIndex],
        options = options,
        selectedIndex = selectedIndex,
        onSelect = { idx -> onSelect(if (idx == 0) 3 else 2) },
    )
}

@Composable
private fun LanguageRow() {
    val available = LanguageManager.available()
    val current = LanguageManager.current()
    val context = LocalContext.current
    var pending by remember { mutableStateOf<AppLanguage?>(null) }
    var rollback by remember { mutableStateOf(AppLanguage.System) }
    var restarting by remember { mutableStateOf(false) }
    SettingsOptionMenuRow(
        title = stringResource(R.string.settings_language),
        leadingIconRes = R.drawable.ic_pref_language,
        subtitle = stringResource(R.string.settings_language_subtitle),
        valueText = stringResource(languageLabelRes(current)),
        options = available.map { stringResource(languageLabelRes(it)) },
        selectedIndex = available.indexOf(current),
        onSelect = { idx ->
            val target = available.getOrNull(idx)
            if (target != null && target != current) {
                rollback = current
                LanguageManager.set(target)
                pending = target
            }
        },
    )
    val cancel = {
        LanguageManager.set(rollback)
        pending = null
    }
    pending?.let {
        AVBoxAlertDialog(
            onDismissRequest = cancel,
            text = { Text(stringResource(R.string.settings_language_restart_message)) },
            dismissButton = {
                val dismissAnimated = LocalSheetDismiss.current
                TextButton(onClick = { dismissAnimated() }) { Text(stringResource(R.string.common_cancel)) }
            },
            confirmButton = {
                val dismissThen = LocalSheetDismissThen.current
                TextButton(onClick = {
                    dismissThen {
                        pending = null
                        restarting = true
                    }
                }) { Text(stringResource(R.string.common_confirm)) }
            },
        )
    }
    if (restarting) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            withFrameNanos { }
            restartApp(context.applicationContext)
        }
    }
}

private fun languageLabelRes(lang: AppLanguage): Int = when (lang) {
    AppLanguage.System -> R.string.settings_language_system
    AppLanguage.SimplifiedChinese -> R.string.settings_language_zh_hans
    AppLanguage.English -> R.string.settings_language_en
    AppLanguage.TraditionalTW -> R.string.settings_language_zh_hant_tw
    AppLanguage.TraditionalHK -> R.string.settings_language_zh_hant_hk
}
