package com.github.tvbox.osc.player

import android.os.Handler
import android.os.Looper
import com.github.tvbox.osc.data.PlaybackProgress
import com.github.tvbox.osc.data.WatchProgressStore
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.player.state.PlayState
import com.github.tvbox.osc.util.LOG
import org.greenrobot.eventbus.EventBus

class PlaybackProgressSampler(private val host: Host) {

    interface Host {
        fun playerView(): MyVideoView?
        fun playbackController(): PlaybackController
        fun isLive(): Boolean
    }

    private val main = Handler(Looper.getMainLooper())

    private var ticking = false

    private val tickRunnable = object : Runnable {
        override fun run() {
            ticking = false
            val view = host.playerView() ?: return
            if (!ProgressSampling.keepsTicking(view.playState, host.isLive())) return
            sample()
            scheduleTick()
        }
    }

    fun onPlayStateChanged(state: PlayState) {
        if (host.isLive()) {
            stop()
            return
        }
        when (state) {
            PlayState.PLAYING -> {
                sample()
                scheduleTick()
            }

            PlayState.BUFFERING, PlayState.BUFFERED -> scheduleTick()

            PlayState.PAUSED, PlayState.IDLE -> {
                stop()
                flush()
            }

            PlayState.COMPLETED -> {
                stop()
                markFinished()
            }

            PlayState.PREPARING, PlayState.PREPARED, PlayState.ERROR, PlayState.START_ABORT -> stop()
        }
    }

    fun onSinkSave(progressKey: String?, positionMs: Long) {
        val view = host.playerView() ?: return
        val controller = host.playbackController()
        if (ProgressSampling.switchInFlight(controller.startedProgressKey(), controller.progressKey())) {
            LOG.i("echo-progress sink skipped: content switch in flight key=" + progressKey)
            return
        }
        WatchProgressStore.save(controller.progressOwner(), progressKey, positionMs, view.duration)
        if (positionMs <= 0) return
        val duration = view.duration
        if (duration <= 0) return
        if (!PlaybackProgress.flush(controller.vod(), positionMs.toInt(), duration.toInt())) return
        EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_HISTORY_REFRESH))
    }

    fun stop() {
        main.removeCallbacks(tickRunnable)
        ticking = false
    }

    private fun scheduleTick() {
        if (ticking) return
        ticking = true
        main.postDelayed(tickRunnable, SAMPLE_INTERVAL_MS)
    }

    private fun sample() {
        val view = host.playerView() ?: return
        val controller = host.playbackController()
        val ready = ProgressSampling.shouldWrite(
            view.playState,
            view.isPlaying,
            host.isLive(),
            controller.isSameStartedContent(),
        )
        if (!ready) return
        val duration = view.duration
        val position = view.currentPosition
        if (duration <= 0 || position <= 0) return
        WatchProgressStore.save(controller.progressOwner(), controller.progressKey(), position, duration)
        PlaybackProgress.onProgress(controller.vod(), position.toInt(), duration.toInt())
        LOG.i("echo-progress sample: pos=" + position + " dur=" + duration + " key=" + controller.progressKey())
    }

    private fun flush() {
        val view = host.playerView() ?: return
        val controller = host.playbackController()
        if (host.isLive() || !controller.isSameStartedContent()) return
        val duration = view.duration
        val position = view.currentPosition
        if (duration <= 0 || position <= 0) return
        WatchProgressStore.save(controller.progressOwner(), controller.progressKey(), position, duration)
        val written = PlaybackProgress.flush(controller.vod(), position.toInt(), duration.toInt())
        LOG.i(
            "echo-progress flush: pos=" + position + " dur=" + duration
                + " written=" + written + " key=" + controller.progressKey()
        )
        EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_HISTORY_REFRESH))
    }

    private fun markFinished() {
        val controller = host.playbackController()
        if (ProgressSampling.switchInFlight(controller.startedProgressKey(), controller.progressKey())) {
            LOG.i("echo-progress finished skipped: content switch in flight")
            return
        }
        PlaybackProgress.markFinished(controller.vod())
        LOG.i("echo-progress finished: key=" + controller.progressKey())
    }

    companion object {

        private const val SAMPLE_INTERVAL_MS = 5_000L
    }
}

object ProgressSampling {

    fun keepsTicking(state: PlayState, live: Boolean): Boolean {
        if (live) return false
        return state == PlayState.PLAYING || state == PlayState.BUFFERING || state == PlayState.BUFFERED
    }

    fun switchInFlight(startedKey: String?, progressKey: String?): Boolean {
        if (startedKey == null || progressKey == null) return false
        return startedKey != progressKey
    }

    fun sameContentRestart(startedPlaybackKey: String?, currentPlaybackKey: String?): Boolean {
        if (startedPlaybackKey.isNullOrEmpty() || currentPlaybackKey.isNullOrEmpty()) return false
        return startedPlaybackKey == currentPlaybackKey
    }

    fun shouldWrite(state: PlayState, playing: Boolean, live: Boolean, sameContent: Boolean): Boolean {
        if (live || !playing || !sameContent) return false
        return state == PlayState.PLAYING
    }
}
