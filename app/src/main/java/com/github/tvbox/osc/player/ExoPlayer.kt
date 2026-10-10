package com.github.tvbox.osc.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import androidx.media3.common.Effect
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import com.github.tvbox.osc.player.engine.AudioCodecChoice
import com.github.tvbox.osc.player.engine.CodecPreferences
import com.github.tvbox.osc.player.engine.PlayerEngine
import com.github.tvbox.osc.player.engine.PlayerEngineConfig
import com.github.tvbox.osc.player.effect.PictureEffects
import com.github.tvbox.osc.player.state.PlayState
import com.github.tvbox.osc.player.state.PlaybackStateMachine
import com.github.tvbox.osc.player.state.deriveKernelPlayState
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.KV
import com.github.tvbox.osc.util.LOG
import kotlinx.coroutines.flow.StateFlow

class ExoPlayer(context: Context) : KernelPlayer() {

    private val appContext: Context = context.applicationContext

    private var engine: PlayerEngine? = null

    val stateMachine = PlaybackStateMachine()

    private val mainHandler = Handler(Looper.getMainLooper())

    override val playState: PlayState
        get() = stateMachine.currentState

    override val stateFlow: StateFlow<PlayState>
        get() = stateMachine.state

    private var onCuesListener: OnCuesListener? = null

    private var useDiskCacheFlag = false

    private var contentKeyValue = ""

    private var awaitingPrepared = false

    private var pendingSpeed: Float? = null

    override fun initPlayer() {
        val config = PlayerEngineConfig(
            bufferTimes = bufferTimes(),
            tunnelingRequested = KV.get(HawkConfig.PLAY_TUNNEL, false),
            surfaceRender = KV.get(HawkConfig.PLAY_RENDER, 1) == 1,
            preferAac = KV.get(HawkConfig.PLAY_PREFER_AAC, false),
            dynamicScheduling = KV.get(
                HawkConfig.EXO_VIDEO_DYNAMIC_SCHEDULING,
                HawkConfig.EXO_VIDEO_DYNAMIC_SCHEDULING_DEFAULT,
            ),
            playbackLooper = if (PreloadManagerHolder.enabled()) PreloadManagerHolder.preloadLooper() else null,
            preloadTargetChecker = { url, headers -> PreloadManagerHolder.isPreloadTargetUrl(url, headers) },
            playCacheEnabled = { KV.get(HawkConfig.PLAY_CACHE, false) },
        )
        val newEngine = PlayerEngine(appContext, config)
        engine = newEngine
        newEngine.setUseDiskCache(useDiskCacheFlag)
        newEngine.setContentKey(contentKeyValue)
        pendingSpeed?.let { newEngine.setSpeed(it) }
        newEngine.videoSizeListener = PlayerEngine.VideoSizeListener { width, height, rotation ->
            mPlayerEventListener?.onVideoSizeChanged(width, height)
            if (rotation > 0) {
                mPlayerEventListener?.onInfo(MEDIA_INFO_VIDEO_ROTATION_CHANGED, rotation)
            }
        }
        newEngine.playbackStateListener = { state -> dispatchPlaybackState(state) }
        newEngine.kernelSignalListener = { dispatchKernelSignal() }
        newEngine.retryAsHlsListener = { awaitingPrepared = true }
        newEngine.addErrorListener { _, _ ->
            stateMachine.onError()
            mPlayerEventListener?.onError()
        }
        newEngine.setOnCuesListener { cues -> onCuesListener?.onCues(cues) }
        LOG.i("echo-m7b-engine-bridge-ready")
    }

    override fun setDataSource(path: String, headers: Map<String, String>?) {
        stateMachine.onContentReplaced()
        engine?.setDataSource(path, headers, KV.get(HawkConfig.PLAYER_IS_LIVE, false))
    }

    override fun prepareAsync() {
        val current = engine ?: return
        PictureEffects.onPrepare(this, current.isTunnelingEnabled)
        current.setStartPosition(startPosition)
        if (!current.prepare()) return
        awaitingPrepared = true
        stateMachine.onPrepareRequested()
        markStartPositionApplied()
    }

    override fun start(): Boolean {
        val current = engine ?: return false
        if (!stateMachine.currentState.isInPlaybackState) return false
        current.start()
        stateMachine.onPlayRequested()
        return true
    }

    override fun pause(): Boolean {
        val current = engine ?: return false
        if (!stateMachine.currentState.isInPlaybackState) return false
        if (!current.isPlaying) return false
        current.pause()
        stateMachine.onPauseRequested()
        return true
    }

    override fun stop(): Boolean {
        val current = engine ?: return false
        if (stateMachine.currentState == PlayState.PAUSED) return false
        current.stop()
        stateMachine.onStopRequested()
        return true
    }

    override fun abortStart() {
        stateMachine.onStartAborted()
    }

    fun stopForFrameClear() {
        engine?.stop()
    }

    override fun reset() {
        awaitingPrepared = false
        engine?.reset()
        stateMachine.onReset()
    }

    override fun release() {
        PictureEffects.onPlayerReleased(this)
        engine?.release()
        engine = null
        awaitingPrepared = false
        stateMachine.onReset()
    }

    override fun keepRenderViewOnReset(): Boolean = true

    override fun resetTrackSelection() {
        engine?.resetTrackSelection()
    }

    override fun seekTo(time: Long) {
        if (stateMachine.currentState == PlayState.PAUSED) {
            stateMachine.onSeekWhilePaused()
        }
        engine?.seekTo(time)
    }

    override val isPlaying: Boolean get() = engine?.isPlaying ?: false

    override val currentPosition: Long get() = engine?.currentPosition ?: 0L

    override val duration: Long get() = engine?.duration ?: 0L

    override val bufferedPercentage: Int get() = engine?.bufferedPercentage ?: 0

    override val tcpSpeed: Long get() = engine?.tcpSpeed ?: 0L

    override val speed: Float get() = engine?.speed ?: pendingSpeed ?: 1f

    override fun setSpeed(speed: Float) {
        pendingSpeed = speed
        engine?.setSpeed(speed)
    }

    override fun setSurface(surface: Surface?) {
        engine?.setVideoSurface(surface)
    }

    override fun setDisplay(holder: SurfaceHolder?) {
        engine?.setDisplay(holder)
    }

    override fun clearDisplay() {
        engine?.clearVideoOutput()
    }

    override fun detachVideoSurface() {
        engine?.detachVideoSurface()
    }

    fun setAudioOnlyMode(audioOnly: Boolean) {
        engine?.setAudioOnlyMode(audioOnly)
    }

    fun isAudioOnlyMode(): Boolean = engine?.isAudioOnlyMode == true

    override fun setVolume(leftVolume: Float, rightVolume: Float) {
        engine?.setVolume(leftVolume, rightVolume)
    }

    override fun setLooping(isLooping: Boolean) {
        engine?.setLooping(isLooping)
    }

    override fun setOptions() {
        engine?.setOptions()
    }

    private fun dispatchPlaybackState(state: Int) {
        if (awaitingPrepared) {
            if (state == Player.STATE_READY) {
                awaitingPrepared = false
                stateMachine.onPrepared()
                stateMachine.onRenderingStart()
                val listener = mPlayerEventListener ?: return
                listener.onPrepared()
                listener.onInfo(MEDIA_INFO_RENDERING_START, 0)
            }
            return
        }
        when (state) {
            Player.STATE_BUFFERING -> {
                stateMachine.onBufferingStart()
                mPlayerEventListener?.onInfo(MEDIA_INFO_BUFFERING_START, engine?.bufferedPercentage ?: 0)
            }

            Player.STATE_READY -> {
                stateMachine.onBufferingEnd(engine?.kernelIsPlaying == true)
                mPlayerEventListener?.onInfo(MEDIA_INFO_BUFFERING_END, engine?.bufferedPercentage ?: 0)
            }

            Player.STATE_ENDED -> {
                stateMachine.onCompletion()
                mPlayerEventListener?.onCompletion()
            }
        }
    }

    private fun dispatchKernelSignal() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            alignKernelPlayState()
        } else {
            mainHandler.post { alignKernelPlayState() }
        }
    }

    private fun alignKernelPlayState() {
        if (awaitingPrepared) return
        when (stateMachine.currentState) {
            PlayState.IDLE, PlayState.START_ABORT, PlayState.ERROR -> return
            else -> Unit
        }
        val current = engine ?: return
        val next = deriveKernelPlayState(
            current.kernelPlayback,
            current.kernelPlayWhenReady,
            current.kernelIsPlaying,
            current.kernelSuppressed,
        ) ?: return
        if (stateMachine.alignWithKernel(next)) {
            mPlayerEventListener?.onKernelPlayStateChanged()
        }
    }

    fun setContentKey(key: String?) {
        contentKeyValue = key ?: ""
        engine?.setContentKey(contentKeyValue)
    }

    fun setUseDiskCache(enabled: Boolean) {
        useDiskCacheFlag = enabled
        engine?.setUseDiskCache(enabled)
    }

    fun notifyVideoOutputResolution(width: Int, height: Int) {
        engine?.notifyVideoOutputResolution(width, height)
    }

    fun redrawVideoFrame() {
        engine?.redrawVideoFrame()
    }

    fun applyVideoEffects(effects: List<Effect>) {
        engine?.applyVideoEffects(effects)
    }

    fun isPictureEffectsActive(): Boolean = engine?.isPictureEffectsActive ?: false

    fun isPictureHdrSource(): Boolean = engine?.isPictureHdrSource ?: false

    val isTunnelingEnabled: Boolean
        get() = engine?.isTunnelingEnabled ?: false

    override fun getTrackInfo(): TrackInfo = engine?.getTrackInfo() ?: TrackInfo()

    override fun setTrack(track: TrackInfoBean?) {
        engine?.setTrack(track)
    }

    fun selectTrack(track: TrackInfoBean?) {
        engine?.selectTrack(track)
    }

    fun restoreTracks() {
        engine?.restoreTracks()
    }

    fun loadDefaultSubtitleTrack() {
        engine?.loadDefaultSubtitleTrack()
    }

    fun ensureSubtitleTrackSelected() {
        engine?.ensureSubtitleTrackSelected()
    }

    fun setOnCuesListener(listener: OnCuesListener?) {
        onCuesListener = listener
        engine?.setOnCuesListener(listener?.let { target -> { cues -> target.onCues(cues) } })
    }

    fun setInternalSubtitleDelay(milliseconds: Int) {
        engine?.setInternalSubtitleDelay(milliseconds)
    }

    fun droppedFrames(): Long = engine?.droppedFrames ?: 0L

    fun rebufferCount(): Int = engine?.rebufferCount ?: 0

    fun videoDecoderName(): String = engine?.videoDecoderName ?: ""

    fun audioCodecChoice(): AudioCodecChoice? = engine?.audioCodecChoice

    fun audioRendererName(): String? = engine?.audioRendererName

    fun measuredFrameRate(): Float = engine?.measuredFrameRate ?: 0f

    fun sampleFrameRate() {
        engine?.sampleFrameRate()
    }

    fun setFrameRateTracking(enabled: Boolean) {
        engine?.setFrameRateTracking(enabled)
    }

    val selectedVideoFormat: Format?
        get() = engine?.getSelectedVideoFormat()

    val selectedAudioFormat: Format?
        get() = engine?.getSelectedAudioFormat()

    fun lastErrorKind(): Int = engine?.lastErrorKind ?: ERROR_KIND_UNKNOWN

    private fun bufferTimes(): Int {
        val value = KV.get(HawkConfig.BUFFER_TIMES, HawkConfig.BUFFER_TIMES_DEFAULT)
        return value.coerceIn(1, 10)
    }

    @JvmSuppressWildcards
    fun interface OnCuesListener {
        fun onCues(cues: List<Cue>)
    }

    companion object {

        const val ERROR_KIND_UNKNOWN = 0
        const val ERROR_KIND_NETWORK = 1
        const val ERROR_KIND_DECODE = 2

        @JvmStatic
        fun setPreferSoftwareDecode(prefer: Boolean) {
            CodecPreferences.setPreferSoftwareDecode(prefer)
        }

        @JvmStatic
        fun isPreferSoftwareDecode(): Boolean = CodecPreferences.isPreferSoftwareDecode()
    }
}
