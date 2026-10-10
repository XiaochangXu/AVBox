package com.github.tvbox.osc.player.controller

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.bean.ParseBean
import com.github.tvbox.osc.player.ExoPlayer
import com.github.tvbox.osc.player.PlayerHelper
import com.github.tvbox.osc.player.state.LockVisibility
import com.github.tvbox.osc.player.state.PlayerActions
import com.github.tvbox.osc.player.state.SelectDialogState
import com.github.tvbox.osc.util.LOG
import com.github.tvbox.osc.util.PlayerUtils
import org.json.JSONException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SEEK_MAX = 1000

internal class PlayerActionsDelegate(private val host: VideoPlayerController) : PlayerActions {

    private val idleHideMillis = 10000L

    private var keySeekProgress = 0

    private val keySeekCommitRunnable by lazy { Runnable { commitKeySeek() } }

    fun cancelKeySeekCommit() {
        host.uiHandler.removeCallbacks(keySeekCommitRunnable)
    }

    override fun toggleControls() {
        if (!host.state.controlsVisible) showBottom() else hideBottom()
    }

    private fun showBottom() {
        applyShowBottom()
    }

    fun applyShowBottom() {
        host.updateDanmuSearchBtnState()
        host.state.controlsVisible = true
        host.state.topLeftVisible = true
        host.state.topRightVisible = true
        host.state.netSpeedTopRightVisible = true
        host.state.sysTimeVisible = true
        host.state.backVisible = !host.state.isPortrait
        host.showLockView()
        keepControlsAlive()
    }

    fun hideBottom() {
        host.uiHandler.removeCallbacks(host.idleHideRunnable)
        if (host.state.dragging) onSeekCancelled()
        host.state.controlsVisible = false
        host.state.topLeftVisible = false
        host.state.topRightVisible = false
        host.state.netSpeedTopRightVisible = false
        host.state.sysTimeVisible = false
        host.state.backVisible = false
        host.uiHandler.removeCallbacks(host.lockHideRunnable)
        if (host.state.lockState != LockVisibility.GONE) {
            host.state.lockState = LockVisibility.HIDDEN
        }
    }

    override fun keepControlsAlive() {
        if (host.state.controlsVisible) {
            host.uiHandler.removeCallbacks(host.idleHideRunnable)
            host.uiHandler.postDelayed(host.idleHideRunnable, idleHideMillis)
        }
    }

    override fun onNextClicked() {
        host.listener?.playNext(false)
        hideBottom()
    }

    override fun onPreClicked() {
        host.listener?.playPre()
        hideBottom()
    }

    override fun onPlayPauseClicked() {
        if (host.state.tipVisible && !host.isInPlaybackState()) return
        host.videoView?.togglePlay()
        keepControlsAlive()
    }

    override fun onScaleClicked() {
        keepControlsAlive()
        host.config.showScaleDialog()
    }

    override fun onScaleLongClicked() {
        keepControlsAlive()
        host.config.applyScale(0)
    }

    override fun onSpeedClicked() {
        keepControlsAlive()
        host.config.showSpeedDialog()
    }

    override fun onSpeedLongClicked() {
        keepControlsAlive()
        host.config.applySpeed(1.0f)
    }

    override fun onPlayerClicked() {
        keepControlsAlive()
        val cfg = host.playerConfig ?: return
        val existPlayerTypes = PlayerHelper.getExistPlayerTypes()
        if (existPlayerTypes.isEmpty()) return
        val current = cfg.optInt("pl", 2)
        var nextIdx = 0
        for (i in existPlayerTypes.indices) {
            if (current == existPlayerTypes[i]) {
                nextIdx = if (i == existPlayerTypes.size - 1) 0 else i + 1
            }
        }
        host.config.applyPlayer(existPlayerTypes[nextIdx])
        hideBottom()
    }

    override fun onPlayerLongClicked() {
        keepControlsAlive()
        try {
            val cfg = host.playerConfig ?: return
            val playerType = cfg.getInt("pl")
            var defaultPos = 0
            val players = PlayerHelper.getExistPlayerTypes()
            val names = ArrayList<String>()
            for (p in players.indices) {
                names.add(PlayerHelper.getPlayerName(players[p]))
                if (players[p] == playerType) {
                    defaultPos = p
                }
            }
            host.state.selectDialog = SelectDialogState(
                tip = host.context.getString(R.string.player_select_player),
                items = names,
                defaultIndex = defaultPos,
                onSelected = { pos ->
                    if (players[pos] != playerType) {
                        host.config.applyPlayer(players[pos])
                        hideBottom()
                    }
                },
            )
        } catch (e: JSONException) {
            LOG.e("VideoPlayerController", e)
        }
    }

    override fun onTimeStartClicked() {
        keepControlsAlive()
        host.config.markTimeStart()
    }

    override fun onTimeStartLongClicked() {
        host.config.setTimeMark("st", 0)
    }

    override fun onTimeEndClicked() {
        keepControlsAlive()
        host.config.markTimeEnd()
    }

    override fun onTimeEndLongClicked() {
        host.config.setTimeMark("et", 0)
    }

    override fun onTimeResetClicked() {
        keepControlsAlive()
        try {
            val cfg = host.playerConfig ?: return
            cfg.put("st", 0)
            cfg.put("et", 0)
            host.config.updatePlayerCfgState()
            host.listener?.updatePlayerCfg()
        } catch (e: JSONException) {
            LOG.e("VideoPlayerController", e)
        }
    }

    override fun onEpisodeClicked() {
        host.listener?.showEpisodes()
        keepControlsAlive()
    }

    override fun onCastClicked() {
        host.listener?.clickCast()
    }

    override fun onSubtitleClicked() {
        host.listener?.selectSubtitle()
        keepControlsAlive()
    }

    override fun onSubtitleLongClicked() {
        host.listener?.closeSubtitles()
        hideBottom()
        Toast.makeText(host.context, host.context.getString(R.string.player_subtitle_closed), Toast.LENGTH_SHORT).show()
    }

    override fun onAudioTrackClicked() {
        host.listener?.selectAudioTrack()
        keepControlsAlive()
    }

    override fun onVideoTrackClicked() {
        host.listener?.selectVideoTrack()
        keepControlsAlive()
    }

    override fun onDanmuSettingClicked() {
        host.listener?.showDanmuSetting()
    }

    override fun onDanmuSettingLongClicked() {
        val opened = host.listener?.toggleDanmu() ?: false
        hideBottom()
        Toast.makeText(host.context, host.context.getString(if (opened) R.string.player_danmu_opened else R.string.player_danmu_temp_closed), Toast.LENGTH_SHORT).show()
    }

    override fun onDanmuSearchClicked() {
        host.listener?.searchDanmuUi(false)
        hideBottom()
    }

    override fun onDanmuSearchLongClicked() {
        host.listener?.searchDanmuUi(true)
        hideBottom()
    }

    override fun onRotateClicked() {
        if (host.state.locked) return
        val toPortrait =
            host.resources.configuration.orientation != Configuration.ORIENTATION_PORTRAIT
        host.playerActivity()?.requestedOrientation =
            if (toPortrait) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        hideBottom()
    }

    override fun onParamsClicked() {
        keepControlsAlive()
        host.state.paramsSheet = host.config.buildParamsSheet()
    }

    override fun onInfoOsdClicked() {
        host.state.infoOsdVisible = !host.state.infoOsdVisible
        val exo = host.videoView?.mediaPlayer as? ExoPlayer
        if (host.state.infoOsdVisible) {
            exo?.setFrameRateTracking(true)
            InfoOsdText.refreshInfoOsd(
                host.context, host.state, host.videoView, host.playerActivity(),
                runCatching { host.videoView?.tcpSpeed ?: 0L }.getOrDefault(0L),
            )
        } else {
            exo?.setFrameRateTracking(false)
        }
        if (host.state.overlayPanelOpen) keepControlsAlive() else hideBottom()
    }

    override fun onBackClicked() {
        host.isClickBackBtn = host.state.controlsVisible && !host.previewMode
        (host.playerActivity() as? ComponentActivity)?.onBackPressedDispatcher?.onBackPressed()
    }

    override fun onLockClicked() {
        val newLocked = !host.state.locked
        host.state.locked = newLocked
        if (newLocked) hideBottom()
        host.showLockView()
    }

    override fun onParseSelected(position: Int) {
        val parseBeanList = ApiConfig.get().parseBeanList
        if (position < 0 || position >= parseBeanList.size) return
        val parseBean: ParseBean = parseBeanList[position]
        ApiConfig.get().setDefaultParse(parseBean)
        host.state.parseListVersion++
        host.listener?.changeParse(parseBean)
        hideBottom()
    }

    override fun onSeekStarted() {
        if (!host.state.controlsVisible) applyShowBottom()
        if (host.state.dragging) return
        host.state.dragging = true
        host.stopProgress()
        host.uiHandler.removeCallbacks(host.idleHideRunnable)
        keepControlsAlive()
    }

    override fun onSeekPreview(progress: Int) {
        val view = host.videoView ?: return
        val duration = PlayerUtils.safeTimeMs(view.duration)
        host.state.seekPreviewPositionMs = seekBarToPosition(progress, duration)
    }

    override fun onSeekFinished(progress: Int) {
        keepControlsAlive()
        val view = host.videoView
        var seekTarget = -1
        if (view != null) {
            val duration = PlayerUtils.safeTimeMs(view.duration)
            seekTarget = seekBarToPosition(progress, duration).toInt()
            view.seekTo(seekTarget.toLong())
        }
        if (seekTarget >= 0) host.state.position = seekTarget
        host.state.dragging = false
        keySeekProgress = 0
        host.startProgress()
        keepControlsAlive()
        if (seekTarget >= 0) host.savePlaybackProgress(notifyHistory = true, seekTargetMs = seekTarget)
    }

    override fun onSeekCancelled() {
        host.state.dragging = false
        keySeekProgress = 0
        host.startProgress()
        keepControlsAlive()
    }

    override fun onSeekStep(dir: Int) {
        val view = host.videoView ?: return
        val duration = PlayerUtils.safeTimeMs(view.duration)
        if (duration <= 0) return
        if (!host.state.controlsVisible) applyShowBottom()
        if (!host.state.dragging) {
            host.state.dragging = true
            host.stopProgress()
            host.uiHandler.removeCallbacks(host.idleHideRunnable)
        }
        keySeekProgress = (keySeekProgress + keySeekIncrement(duration) * dir).coerceIn(0, SEEK_MAX)
        host.state.seekPreviewPositionMs = seekBarToPosition(keySeekProgress, duration)
        host.updateSeekUiHint(
            PlayerUtils.safeTimeMs(view.currentPosition),
            host.state.seekPreviewPositionMs.toInt(),
        )
        host.uiHandler.removeCallbacks(keySeekCommitRunnable)
        host.uiHandler.postDelayed(keySeekCommitRunnable, 400)
    }

    override fun onSeekRelative(deltaMs: Long) {
        val view = host.videoView ?: return
        val duration = PlayerUtils.safeTimeMs(view.duration)
        if (duration <= 0) return
        if (!host.state.controlsVisible) applyShowBottom()
        val current = PlayerUtils.safeTimeMs(view.currentPosition)
        val target = (current + deltaMs).coerceIn(0L, duration.toLong())
        host.state.dragging = false
        keySeekProgress = 0
        view.seekTo(target)
        host.state.position = target.toInt()
        host.state.seekPreviewPositionMs = target
        host.updateSeekUiHint(current, target.toInt())
        host.savePlaybackProgress(notifyHistory = true, seekTargetMs = target.toInt())
        keepControlsAlive()
    }

    private fun commitKeySeek() {
        if (!host.state.dragging) return
        onSeekFinished(keySeekProgress)
    }

    private fun seekBarToPosition(progress: Int, duration: Int): Long {
        if (duration <= 0) return 0L
        return duration.toLong() * progress / SEEK_MAX
    }

    private fun keySeekIncrement(duration: Int): Int {
        val increment: Long = when {
            duration > 3 * 60 * 60 * 1000 -> 5 * 60 * 1000L
            duration > 30 * 60 * 1000 -> 60 * 1000L
            duration > 15 * 60 * 1000 -> 30 * 1000L
            duration > 10 * 60 * 1000 -> 15 * 1000L
            else -> 10 * 1000L
        }
        return maxOf(1, (increment * SEEK_MAX / duration).toInt())
    }

    override fun refreshSystemInfo() {
        val view = host.videoView ?: return
        host.state.sysTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        InfoOsdText.readBattery(host.context, host.state)
        val speed = runCatching { view.tcpSpeed }.getOrDefault(0L)
        host.state.netSpeedTopRight = PlayerHelper.getDisplaySpeed(speed, true)
        host.state.netSpeedCenter = PlayerHelper.getDisplaySpeed(speed, false)
        val size = runCatching { view.videoSize }.getOrDefault(intArrayOf(0, 0))
        host.state.videoSize = host.videoSizeGate.textFor(size[0], size[1])
        host.state.videoQuality = host.videoSizeGate.qualityFor(size[0], size[1])
        if (host.state.infoOsdVisible) InfoOsdText.refreshInfoOsd(host.context, host.state, host.videoView, host.playerActivity(), speed)
    }

    override fun hideSeekHint() {
        host.state.seekHintVisible = false
    }

    override fun hideSlideHint() {
        host.state.slideHintVisible = false
    }
}
