package com.github.tvbox.osc.player.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.tvbox.osc.bean.Subtitle
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.dlna.CastVideo
import com.github.tvbox.osc.player.effect.PictureEffectUnavailableReason
import com.github.tvbox.osc.player.effect.PicturePreset
import com.github.tvbox.osc.player.effect.PictureProfile
import com.github.tvbox.osc.player.AppPlayerView

class PlayerUiState {

    var playState: PlayState by mutableStateOf(PlayState.IDLE)
    var playerState: Int by mutableStateOf(AppPlayerView.PLAYER_NORMAL)
    var duration: Int by mutableStateOf(0)
    var position: Int by mutableStateOf(0)
    var bufferedPercent: Int by mutableStateOf(0)
    var locked: Boolean by mutableStateOf(false)
    var showing: Boolean by mutableStateOf(false)

    var controlsVisible: Boolean by mutableStateOf(false)
    var dragging: Boolean by mutableStateOf(false)
    var seekPreviewPositionMs: Long by mutableStateOf(0L)

    var seekHintVisible: Boolean by mutableStateOf(false)
    var seekHintForward: Boolean by mutableStateOf(true)
    var seekHintText: String by mutableStateOf("")

    var slideHintVisible: Boolean by mutableStateOf(false)
    var slideHintText: String by mutableStateOf("")
    var slideHintBrightness: Boolean by mutableStateOf(true)

    var speedBoostVisible: Boolean by mutableStateOf(false)
    var speedBoostValue: Float by mutableStateOf(3.0f)

    var title: String by mutableStateOf("")
    var videoSize: String by mutableStateOf(VideoSizeGate.UNKNOWN)
    var videoQuality: String by mutableStateOf("")
    var sysTime: String by mutableStateOf("")
    var batteryPercent: Int by mutableStateOf(-1)
    var batteryCharging: Boolean by mutableStateOf(false)
    var netSpeedTopRight: String by mutableStateOf("")
    var netSpeedCenter: String by mutableStateOf("")

    var topLeftVisible: Boolean by mutableStateOf(false)
    var topRightVisible: Boolean by mutableStateOf(false)
    var sysTimeVisible: Boolean by mutableStateOf(false)
    var netSpeedTopRightVisible: Boolean by mutableStateOf(false)

    var backVisible: Boolean by mutableStateOf(false)
    var lockState: LockVisibility by mutableStateOf(LockVisibility.GONE)

    var infoOsdVisible: Boolean by mutableStateOf(false)
    var infoOsdLeft: List<String> by mutableStateOf(emptyList())
    var infoOsdRight: List<String> by mutableStateOf(emptyList())
    var infoOsdFooter: String by mutableStateOf("")
    var showParseRow: Boolean by mutableStateOf(false)
    var isPortrait: Boolean by mutableStateOf(true)
    var playerType: Int by mutableStateOf(2)
    var liveButtonsVisible: Boolean by mutableStateOf(true)
    var danmuOpen: Boolean by mutableStateOf(false)
    var danmuSearchAvailable: Boolean by mutableStateOf(false)
    var sessionVod: VodInfo? by mutableStateOf(null)
    var previewMode: Boolean by mutableStateOf(false)
    var timeStartText: String by mutableStateOf("")
    var timeEndText: String by mutableStateOf("")
    var parseListVersion: Int by mutableStateOf(0)

    var selectDialog: SelectDialogState? by mutableStateOf(null)

    var paramsSheet: ParamsSheetState? by mutableStateOf(null)

    var paramsTab: ParamsTab by mutableStateOf(ParamsTab.Playback)

    var tipMsg: String by mutableStateOf("")
    var tipLoading: Boolean by mutableStateOf(false)
    var tipErr: Boolean by mutableStateOf(false)

    val tipVisible: Boolean get() = tipLoading || tipErr

    fun applyTip(msg: String, loading: Boolean, err: Boolean) {
        tipMsg = msg
        tipLoading = loading
        tipErr = err
    }

    var danmuSettingSheet: DanmuSettingSheetState? by mutableStateOf(null)
    var danmuSearchSheet: DanmuSearchSheetState? by mutableStateOf(null)
    var subtitleSheet: SubtitleSheetState? by mutableStateOf(null)
    var subtitleSearchSheet: SubtitleSearchSheetState? by mutableStateOf(null)
    var castSheet: CastSheetState? by mutableStateOf(null)
    var episodeSheetOpen: Boolean by mutableStateOf(false)

    val overlayPanelOpen: Boolean
        get() = selectDialog != null || paramsSheet != null || danmuSettingSheet != null ||
                danmuSearchSheet != null || subtitleSheet != null || subtitleSearchSheet != null ||
                castSheet != null || episodeSheetOpen

    val danmuBtnVisible: Boolean get() = danmuOpen

    val episodeBtnVisible: Boolean
        get() {
            val vod = sessionVod ?: return false
            val flag = vod.playFlag ?: return false
            return (vod.seriesMap?.get(flag)?.size ?: 0) > 1
        }

    var lifecyclePaused: Boolean by mutableStateOf(false)

    var exitPaused: Boolean by mutableStateOf(false)

    val playbackActive: Boolean
        get() = lifecyclePaused ||
                playState == PlayState.PLAYING ||
                playState == PlayState.BUFFERING ||
                playState == PlayState.BUFFERED

    val pauseOverlayVisible: Boolean
        get() = playState == PlayState.PAUSED && !controlsVisible && !lifecyclePaused && !exitPaused

    val seekPreviewOrPosition: Int
        get() = if (dragging && duration > 0) seekPreviewPositionMs.toInt().coerceIn(0, duration) else position

    val loadingVisible: Boolean
        get() = playState == PlayState.PREPARING || playState == PlayState.BUFFERING

    val centerControlsVisible: Boolean
        get() = controlsVisible && !loadingVisible && !tipVisible && !locked

    val netSpeedCenterVisible: Boolean
        get() = playState == PlayState.IDLE
}

enum class LockVisibility { GONE, HIDDEN, SHOWN }

enum class ParamsTab { Playback, Picture }

class SelectDialogState(
    val tip: String,
    val items: List<String>,
    val defaultIndex: Int,
    val onSelected: (Int) -> Unit,
)

class ParamsChoice(
    val options: List<String>,
    val selected: Int,
    val onSelect: (Int) -> Unit,
)

class ParamsSheetState(
    val speed: ParamsChoice,
    val decode: ParamsChoice,
    val player: ParamsChoice,
    val scale: ParamsChoice,
    val picture: PictureParamsState,
    val timeStartText: String,
    val timeEndText: String,
    val onSetTimeStart: () -> Unit,
    val onSetTimeEnd: () -> Unit,
    val onResetTime: () -> Unit,
    val onSearchDanmu: (() -> Unit)?,
)

class PictureParamsState(
    val preset: PicturePreset,
    val tuning: PictureProfile,
    val unavailableReason: PictureEffectUnavailableReason,
    val anime4kTierText: String,
    val anime4kEnabled: Boolean,
    val anime4kUnavailable: Boolean,
    val anime4kSharpen: Float,
    val anime4kDeblur: Boolean,
    val onAnime4kToggled: (Boolean) -> Unit,
    val onAnime4kSharpenChanged: (Float) -> Unit,
    val onAnime4kDeblurToggled: (Boolean) -> Unit,
    val onPresetSelected: (PicturePreset) -> Unit,
    val onTuningChanged: (PictureProfile) -> Unit,
    val onReset: () -> Unit,
    val onCompareChanged: (Boolean) -> Unit,
)

class DanmuSettingSheetState(
    val onOpenSearch: () -> Unit,
    val onReset: () -> Unit = {},
)

class DanmuSearchSheetState(
    val episode: String,
    val searchWord: String,
    val onLoad: (String) -> Unit,
)

class SubtitleSheetState(
    val exoInternal: Boolean,
    val hasInternal: Boolean,
    val onSelectInternal: () -> Unit,
    val onSelectLocal: () -> Unit,
    val onSelectRemote: () -> Unit,
    val onSelectStyle: (Int) -> Unit = {},
    val onTextSizeChange: () -> Unit = {},
    val onReset: () -> Unit = {},
)

class SubtitleSearchSheetState(
    val searchWord: String,
    val onLoadSubtitle: (Subtitle, String) -> Unit,
)

class CastSheetState(
    val video: CastVideo,
    val onCastSuccess: () -> Unit,
)

interface PlayerActions {
    fun toggleControls()
    fun keepControlsAlive()

    fun onNextClicked()
    fun onPreClicked()
    fun onPlayPauseClicked()
    fun onScaleClicked()
    fun onScaleLongClicked()
    fun onSpeedClicked()
    fun onSpeedLongClicked()
    fun onPlayerClicked()
    fun onPlayerLongClicked()
    fun onTimeStartClicked()
    fun onTimeStartLongClicked()
    fun onTimeEndClicked()
    fun onTimeEndLongClicked()
    fun onTimeResetClicked()
    fun onEpisodeClicked()
    fun onCastClicked()
    fun onSubtitleClicked()
    fun onSubtitleLongClicked()
    fun onAudioTrackClicked()
    fun onVideoTrackClicked()
    fun onDanmuSettingClicked()
    fun onDanmuSettingLongClicked()
    fun onDanmuSearchClicked()
    fun onDanmuSearchLongClicked()
    fun onRotateClicked()
    fun onParamsClicked()
    fun onInfoOsdClicked()
    fun onBackClicked()
    fun onLockClicked()

    fun onParseSelected(position: Int)

    fun onSeekStarted()
    fun onSeekPreview(progress: Int)
    fun onSeekFinished(progress: Int)
    fun onSeekCancelled()
    fun onSeekStep(dir: Int)

    fun onSeekRelative(deltaMs: Long)

    fun refreshSystemInfo()

    fun hideSeekHint()
    fun hideSlideHint()
}
