package com.github.tvbox.osc.player.controller

import android.content.Context
import android.media.AudioManager
import com.github.tvbox.osc.R
import com.github.tvbox.osc.player.AppPlayerView
import com.github.tvbox.osc.player.state.PlayState
import com.github.tvbox.osc.player.ui.VideoGestureActions
import com.github.tvbox.osc.player.ui.VideoGestureSession
import com.github.tvbox.osc.util.GestureHelper
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.KV
import com.github.tvbox.osc.player.ui.VERBOSE_GESTURE_LOG
import com.github.tvbox.osc.util.LOG

internal class VideoGestureActionsImpl(private val host: VideoPlayerController) : VideoGestureActions {

    private enum class HintKind { NONE, SEEK, SLIDE }

    private fun showSeekHintOnly() {
        if (host.state.slideHintVisible) host.actions.hideSlideHint()
        shownHint = HintKind.SEEK
    }

    private fun showSlideHintOnly() {
        if (host.state.seekHintVisible) host.actions.hideSeekHint()
        shownHint = HintKind.SLIDE
    }

    private val slideFullWidthMs = 120000f

    private val verticalRangePerScreen = 2.5f

    private val topBandFraction = 0.15f

    private var seekTargetMs = -1

    private var brightnessBase: Float? = null

    private var volumeBase: Int? = null

    private var lastAppliedBrightness = Float.NaN
    private var lastAppliedVolume = Int.MIN_VALUE

    private var cachedStreamMax = 0

    private var shownHint: HintKind = HintKind.NONE

    private val audioManager: AudioManager? by lazy {
        host.context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    override fun inPlayback(): Boolean = host.isInPlaybackState()

    fun beginSession(width: Int, height: Int, screenWidth: Int, downY: Float): VideoGestureSession {
        seekTargetMs = -1
        brightnessBase = host.playerActivity()?.window?.attributes?.screenBrightness?.let {
            if (it < 0f) 0.5f else it
        }
        volumeBase = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)
        cachedStreamMax = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0
        lastAppliedBrightness = Float.NaN
        lastAppliedVolume = Int.MIN_VALUE
        val view = host.playerView
        val paused = host.state.playState == PlayState.PAUSED
        return VideoGestureSession(
            inPlayback = host.isInPlaybackState(),
            canChangePosition = host.gestureCanChangePosition(),
            enableInNormal = host.gestureEnableInNormal(),
            fullScreen = host.state.playerState == AppPlayerView.PLAYER_FULL_SCREEN,
            locked = host.isLocked,
            previewMode = host.previewMode,
            paused = paused,
            gestureEnabled = host.gestureEnabled(),
            verticalSlidingDisabled = GestureHelper.isControlDisabled(),
            width = width,
            height = height,
            screenWidth = screenWidth,
            edge = false,
            fromTopBand = downY < height * topBandFraction,
        )
    }

    override fun onSingleTap() {
        if (host.isLocked) {
            host.showLockView()
            return
        }
        host.actions.toggleControls()
    }

    override fun onDoubleTapTogglePlay() {
        if (host.isLocked) return
        if (!host.isInPlaybackState()) return
        host.togglePlayFromGesture()
    }

    override fun onLongPressStart() {
        if (host.isLocked) return
        host.speedOld = host.currentSpeed()
        val boost = KV.get(HawkConfig.LONG_PRESS_SPEED, HawkConfig.LONG_PRESS_SPEED_DEFAULT).toFloat()
        host.setSpeedFromGesture(boost)
        host.state.speedBoostValue = boost
        host.state.speedBoostVisible = true
    }

    override fun onLongPressEnd() {
        host.setSpeedFromGesture(host.speedOld)
        host.state.speedBoostVisible = false
    }

    override fun onSeekPreview(totalDeltaX: Float) {
        if (host.isLocked) return
        val width = host.width
        if (width <= 0) return
        val snapshot = host.progressSnapshot()
        if (snapshot == null) {
            seekTargetMs = -1
            return
        }
        val duration = snapshot.durationMs
        val current = snapshot.positionMs
        var target = (totalDeltaX / width * slideFullWidthMs + current).toInt()
        if (target > duration) target = duration
        if (target < 0) target = 0
        showSeekHintOnly()
        host.updateSeekUiHint(current, target)
        seekTargetMs = target
    }

    override fun onSeekCommit() {
        val target = seekTargetMs
        seekTargetMs = -1
        if (target < 0) return
        if (host.progressSnapshot() == null) return
        host.seekToFromGesture(target.toLong())
        host.saveProgressFromView()
    }

    override fun onSeekCancel() {
        seekTargetMs = -1
    }

    override fun onBrightnessSlide(totalDeltaY: Float) {
        if (host.isLocked) return
        val activity = host.playerActivity() ?: return
        val window = activity.window
        val attrs = window.attributes
        val height = host.height
        if (height <= 0) return
        val base = brightnessBase ?: return
        val delta = -totalDeltaY / (height * verticalRangePerScreen)
        var target = base + delta
        if (target < 0f) target = 0f
        if (target > 1f) target = 1f
        if (target != lastAppliedBrightness) {
            attrs.screenBrightness = target
            window.attributes = attrs
            lastAppliedBrightness = target
        }
        if (VERBOSE_GESTURE_LOG) {
            LOG.i(
                "echo-slide: kind=brightness dy=" + totalDeltaY + " h=" + height +
                    " base=" + base + " target=" + target,
            )
        }
        showSlideHintOnly()
        host.showSlideHint(host.context.getString(R.string.player_gesture_percent, (target * 100).toInt()), brightness = true)
    }

    override fun onVolumeSlide(totalDeltaY: Float) {
        if (host.isLocked) return
        val am = audioManager ?: return
        val height = host.height
        if (height <= 0) return
        val streamMax = cachedStreamMax
        if (streamMax <= 0) return
        val base = volumeBase ?: return
        val delta = -totalDeltaY / (height * verticalRangePerScreen) * streamMax
        var index = base + delta
        if (index > streamMax) index = streamMax.toFloat()
        if (index < 0f) index = 0f
        val applied = index.toInt()
        if (applied != lastAppliedVolume) {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, applied, 0)
            lastAppliedVolume = applied
        }
        if (VERBOSE_GESTURE_LOG) {
            LOG.i(
                "echo-slide: kind=volume dy=" + totalDeltaY + " h=" + height +
                    " base=" + base + " max=" + streamMax + " target=" + index,
            )
        }
        showSlideHintOnly()
        host.showSlideHint(host.context.getString(R.string.player_gesture_percent, (index / streamMax * 100).toInt()), brightness = false)
    }
}
