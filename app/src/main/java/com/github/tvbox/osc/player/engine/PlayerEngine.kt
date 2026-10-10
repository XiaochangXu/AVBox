package com.github.tvbox.osc.player.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Effect
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoFrameProcessor
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.Clock
import androidx.media3.common.util.Size
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.DefaultAnalyticsCollector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import com.github.tvbox.osc.player.PlayerCodecStats
import com.github.tvbox.osc.player.TrackInfo
import com.github.tvbox.osc.player.TrackInfoBean
import com.github.tvbox.osc.player.effect.PictureEffects
import com.github.tvbox.osc.player.effect.RedrawPolicy
import com.github.tvbox.osc.player.effect.ReplayableCacheVideoRenderer
import com.github.tvbox.osc.player.state.KernelPlayback
import com.github.tvbox.osc.util.LOG
import okhttp3.OkHttpClient
import java.util.ArrayList
import java.util.concurrent.atomic.AtomicLong

data class PlayerEngineConfig(
    val bufferTimes: Int = DEFAULT_BUFFER_TIMES,
    val tunnelingRequested: Boolean = false,
    val surfaceRender: Boolean = true,
    val preferAac: Boolean = false,
    val dynamicScheduling: Boolean = true,
    val enableLog: Boolean = false,
    val playbackLooper: Looper? = null,
    val okHttpClient: OkHttpClient? = null,
    val preloadTargetChecker: (url: String, headers: Map<String, String>?) -> Boolean = { _, _ -> false },
    val playCacheEnabled: () -> Boolean = { false },
) {
    companion object {
        const val DEFAULT_BUFFER_TIMES = 3
    }
}

class PlayerEngine(
    context: Context,
    private val config: PlayerEngineConfig = PlayerEngineConfig(),
) {

    private val appContext: Context = context.applicationContext

    val mediaSources = MediaSources(appContext, config.okHttpClient)

    private val videoRenderers = ArrayList<Renderer>()
    private val videoRendererIndices = ArrayList<Int>()

    private var audioRenderersFactory: EngineRenderersFactory? = null

    private val trackSelector = DefaultTrackSelector(appContext)

    private val trackSelection = EngineTrackSelection(object : EngineTrackSelection.Host {
        override fun trackSelector(): DefaultTrackSelector = this@PlayerEngine.trackSelector

        override fun internalPlayer(): ExoPlayer? = this@PlayerEngine.internalPlayer
    })

    private var internalPlayer: ExoPlayer? = null

    val player: ExoPlayer?
        get() = internalPlayer

    private var speedPlaybackParameters: PlaybackParameters? = null
    private var mediaSource: MediaSource? = null
    private var currentPlayPath: String? = null
    private var currentHeaders: Map<String, String>? = null
    private var retriedAsHls = false

    private var useDiskCache = false

    @Volatile
    private var startPositionMs = 0L

    @Volatile
    private var startPositionApplied = false

    @Volatile
    private var videoEffectsOpen = false

    private var tunnelingEnabled = false

    @Volatile
    private var pictureHdrSource = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastOutputWidth = 0
    private var lastOutputHeight = 0
    private var pendingOutputWidth = 0
    private var pendingOutputHeight = 0
    private var outputSurfacePresent = false
    private var redrawScheduled = false
    private var audioOnlyRequested = false
    private var videoOutputInvalid = false
    private var videoRenderersDisabled = false

    var videoSizeListener: VideoSizeListener? = null

    var playbackStateListener: ((Int) -> Unit)? = null

    var kernelSignalListener: (() -> Unit)? = null

    var retryAsHlsListener: (() -> Unit)? = null

    fun interface ErrorListener {
        fun onPlayerError(error: PlaybackException, kind: Int)
    }

    private val errorListeners = ArrayList<ErrorListener>()

    @Volatile
    private var lastErrorKindValue = ERROR_KIND_UNKNOWN

    @Volatile
    private var droppedFramesTotal = 0L

    @Volatile
    private var rebufferCountTotal = 0

    @Volatile
    private var playbackStarted = false
    private val renderedFrameCount = AtomicLong()

    @Volatile
    private var frameRateWindowStartMs = 0L

    @Volatile
    private var measuredFrameRateValue = 0f

    @Volatile
    private var frameRateTracking = false

    private val videoFrameListener = VideoFrameMetadataListener { _, _, _, _ ->
        renderedFrameCount.incrementAndGet()
    }

    private val engineListener = object : Player.Listener {

        override fun onTracksChanged(tracks: Tracks) {
            trackSelection.loadDefaultSubtitleTrackBeforeReady()
            reportVideoSizeFromTracks(tracks)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                trackSelection.markSubtitleSelectionClosed()
            }
            playbackStateListener?.invoke(playbackState)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            kernelSignalListener?.invoke()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            kernelSignalListener?.invoke()
        }

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            kernelSignalListener?.invoke()
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoEffectsOpen && videoSize.width > 0 && videoSize.height > 0) {
                videoEffectsOpen = false
                LOG.i("echo-picture-effects inactive: kernel reported video size")
            }
            videoSizeListener?.onVideoSizeChanged(videoSize.width, videoSize.height, videoSize.unappliedRotationDegrees)
        }

        override fun onCues(cueGroup: CueGroup) {
            trackSelection.dispatchCues(cueGroup.cues)
        }

        override fun onPlayerError(error: PlaybackException) {
            handlePlayerError(error)
        }
    }

    private val analyticsListener = object : AnalyticsListener {
        override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
            droppedFramesTotal += droppedFrames
        }

        override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                playbackStarted = true
            } else if (playbackState == Player.STATE_BUFFERING && playbackStarted) {
                rebufferCountTotal++
            }
        }
    }

    init {
        val exo = createPlayer()
        internalPlayer = exo
        applyPlaybackParameters()
        disableFrameRateMatching()
        exo.addListener(engineListener)
        exo.addAnalyticsListener(analyticsListener)
        applyFrameRateTracking()
        LOG.i("echo-exo-cues-listener-ready")
    }

    private fun createPlayer(): ExoPlayer {
        val factory = EngineRenderersFactory(
            appContext,
            { trackSelection.subtitleDelayUs },
            videoRenderers,
            videoRendererIndices,
            config.dynamicScheduling,
        )
        factory.setEnableDecoderFallback(true)
        factory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        val renderersFactory = factory
        audioRenderersFactory = renderersFactory
        renderersFactory.forceDisableMediaCodecAsynchronousQueueing()
        LOG.i("echo-exo-disable-async-codec-queue")
        LOG.i("echo-exo-video-dynamic-scheduling: ${config.dynamicScheduling}")

        val bufferTimes = config.bufferTimes.coerceIn(1, 10)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS * bufferTimes,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS * bufferTimes,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            )
            .build()
        LOG.i("echo-exo-low-memory-load-control")

        val builder = ExoPlayer.Builder(
            appContext,
            renderersFactory,
            DefaultMediaSourceFactory(appContext),
            trackSelector,
            loadControl,
            DefaultBandwidthMeter.getSingletonInstance(appContext),
            DefaultAnalyticsCollector(Clock.DEFAULT),
        )
        config.playbackLooper?.let { builder.setPlaybackLooper(it) }
        val exo = builder.build()
        exo.playWhenReady = true
        if (config.enableLog) {
            exo.addAnalyticsListener(EventLogger(trackSelector, "ExoPlayer"))
        }
        return exo
    }

    fun setDataSource(path: String, headers: Map<String, String>?) {
        setDataSource(path, headers, false)
    }

    fun setDataSource(path: String, headers: Map<String, String>?, isLive: Boolean) {
        LOG.i("echo-setDataSource:$path")
        var playPath = path
        if (SourcePolicy.isRtmp(playPath) && isLive) {
            val flagged = SourcePolicy.applyRtmpLiveFlag(playPath, true)
            if (flagged != playPath) {
                playPath = flagged
                LOG.i("echo-rtmp-live-flag: $playPath")
            }
        }
        currentPlayPath = playPath
        currentHeaders = copyHeaders(headers)
        resetSessionFlags()
        trackSelection.resetForNewContent()
        resetPlaybackStats()
        mediaSource = mediaSources.getMediaSource(playPath, copyHeaders(currentHeaders))

        val preloadTarget = config.preloadTargetChecker(playPath, headers)
        val playCacheWanted = useDiskCache && config.playCacheEnabled()
        val mode = SourcePolicy.resolveCacheMode(
            isLocalProxyUrl = SourcePolicy.isLocalProxyUrl(playPath),
            isRtmp = SourcePolicy.isRtmp(playPath),
            preloadTarget = preloadTarget,
            playCacheWanted = playCacheWanted,
        )
        if (mode == SourcePolicy.CacheMode.NONE) {
            if (preloadTarget || playCacheWanted) {
                LOG.i(
                    (if (SourcePolicy.isRtmp(playPath)) "echo-play-cache-skip-rtmp: " else "echo-play-cache-skip-local-proxy: ") + playPath,
                )
            }
            return
        }
        val cached = if (mode == SourcePolicy.CacheMode.PRELOAD_TARGET) {
            mediaSources.getPreloadTargetMediaSource(playPath, headers)
        } else {
            mediaSources.getMediaSource(playPath, headers, true)
        }
        mediaSource = cached
        LOG.i((if (mode == SourcePolicy.CacheMode.PRELOAD_TARGET) "echo-preload-disk-source: " else "echo-play-cache-source: ") + playPath)
    }

    private fun resetSessionFlags() {
        retriedAsHls = false
        videoEffectsOpen = false
        pictureHdrSource = false
        lastErrorKindValue = ERROR_KIND_UNKNOWN
    }

    private fun resetPlaybackStats() {
        droppedFramesTotal = 0
        rebufferCountTotal = 0
        playbackStarted = false
        PlayerCodecStats.videoDecoderName = ""
        renderedFrameCount.set(0)
        frameRateWindowStartMs = 0
        measuredFrameRateValue = 0f
    }

    fun setStartPosition(positionMs: Long) {
        startPositionMs = maxOf(0L, positionMs)
        startPositionApplied = false
    }

    val isStartPositionApplied: Boolean
        get() = startPositionApplied

    fun prepare(): Boolean {
        val exo = internalPlayer ?: return false
        val source = mediaSource ?: return false
        speedPlaybackParameters?.let { exo.setPlaybackParameters(it) }
        exo.setMediaSource(source, startPositionMs)
        startPositionApplied = true
        exo.prepare()
        return true
    }

    fun start() {
        internalPlayer?.playWhenReady = true
    }

    fun setOptions() {
        internalPlayer?.playWhenReady = true
    }

    fun pause() {
        internalPlayer?.playWhenReady = false
    }

    fun stop() {
        internalPlayer?.stop()
    }

    fun seekTo(positionMs: Long) {
        internalPlayer?.seekTo(positionMs)
    }

    fun reset() {
        internalPlayer?.let {
            it.stop()
            it.clearMediaItems()
        }
        resetSessionFlags()
    }

    fun release() {
        internalPlayer?.let {
            it.removeListener(engineListener)
            it.removeAnalyticsListener(analyticsListener)
            it.release()
        }
        internalPlayer = null
        audioOnlyRequested = false
        videoOutputInvalid = false
        videoRenderersDisabled = false
        pendingOutputWidth = 0
        pendingOutputHeight = 0
        outputSurfacePresent = false
        speedPlaybackParameters = null
    }

    val isPlaying: Boolean
        get() {
            val exo = internalPlayer ?: return false
            return when (exo.playbackState) {
                Player.STATE_BUFFERING, Player.STATE_READY -> exo.playWhenReady
                else -> false
            }
        }

    val kernelPlayback: KernelPlayback
        get() = when (internalPlayer?.playbackState) {
            Player.STATE_BUFFERING -> KernelPlayback.BUFFERING
            Player.STATE_READY -> KernelPlayback.READY
            Player.STATE_ENDED -> KernelPlayback.ENDED
            else -> KernelPlayback.IDLE
        }

    val kernelPlayWhenReady: Boolean
        get() = internalPlayer?.playWhenReady == true

    val kernelIsPlaying: Boolean
        get() = internalPlayer?.isPlaying == true

    val kernelSuppressed: Boolean
        get() {
            val exo = internalPlayer ?: return false
            return exo.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE
        }

    val currentPosition: Long
        get() = internalPlayer?.currentPosition ?: 0L

    val duration: Long
        get() = internalPlayer?.duration ?: 0L

    val bufferedPercentage: Int
        get() = internalPlayer?.bufferedPercentage ?: 0

    val playbackState: Int
        get() = internalPlayer?.playbackState ?: Player.STATE_IDLE

    fun setVideoSurface(surface: Surface?) {
        val invalid = surface == null || !surface.isValid
        LOG.i(
            "echo-surface-bind: valid=" + (surface != null && surface.isValid)
                + " invalid=" + invalid + " disabled=" + videoRenderersDisabled,
        )
        internalPlayer?.setVideoSurface(surface)
        outputSurfacePresent = !invalid
        setVideoOutputInvalid(invalid)
        resendPendingOutputResolution()
    }

    private fun resendPendingOutputResolution() {
        if (!outputSurfacePresent) return
        val width = if (pendingOutputWidth > 0) pendingOutputWidth else lastOutputWidth
        val height = if (pendingOutputHeight > 0) pendingOutputHeight else lastOutputHeight
        pendingOutputWidth = 0
        pendingOutputHeight = 0
        if (width > 0 && height > 0) sendOutputResolution(width, height)
    }

    fun clearVideoOutput() {
        internalPlayer?.clearVideoSurface()
        outputSurfacePresent = false
        setVideoOutputInvalid(true)
    }

    fun detachVideoSurface() {
        internalPlayer?.clearVideoSurface()
        outputSurfacePresent = false
        LOG.i("echo-exo-detach-surface: renderers kept")
    }

    private fun setVideoOutputInvalid(invalid: Boolean) {
        if (invalid == videoOutputInvalid) return
        videoOutputInvalid = invalid
        applyRendererEnablement()
    }

    fun setDisplay(holder: SurfaceHolder?) {
        if (holder == null) {
            setVideoSurface(null)
            return
        }
        val surface = holder.surface
        if (surface == null || !surface.isValid) {
            // 翻页/宿主窗口切换时 surface 可能已销毁：此时必须清空输出，否则解码器会绑定已释放的 surface。
            setVideoSurface(null)
            return
        }
        setVideoSurface(surface)
        val frame = holder.surfaceFrame
        if (frame != null) {
            notifyVideoOutputResolution(frame.width(), frame.height())
        }
    }

    fun setVolume(leftVolume: Float, rightVolume: Float) {
        internalPlayer?.setVolume((leftVolume + rightVolume) / 2f)
    }

    fun setLooping(isLooping: Boolean) {
        internalPlayer?.repeatMode = if (isLooping) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
    }

    fun setSpeed(speed: Float) {
        val params = PlaybackParameters(speed)
        speedPlaybackParameters = params
        internalPlayer?.setPlaybackParameters(params)
    }

    val speed: Float
        get() = speedPlaybackParameters?.speed ?: 1f

    val tcpSpeed: Long
        get() = NetworkSpeed.getNetSpeed(appContext)

    fun addErrorListener(listener: ErrorListener) {
        if (!errorListeners.contains(listener)) {
            errorListeners.add(listener)
        }
    }

    fun removeErrorListener(listener: ErrorListener) {
        errorListeners.remove(listener)
    }

    private fun applyPlaybackParameters() {
        if (internalPlayer == null) return
        tunnelingEnabled = config.tunnelingRequested && config.surfaceRender
        val builder = trackSelector.buildUponParameters()
        builder.setTunnelingEnabled(tunnelingEnabled)
        if (config.preferAac) {
            builder.setPreferredAudioMimeTypes(MimeTypes.AUDIO_AAC)
        }
        trackSelector.setParameters(builder.build())
        LOG.i(
            "echo-exo-tunnel-prefs: tunnel=${config.tunnelingRequested}, surfaceRender=${config.surfaceRender}, " +
                "preferAac=${config.preferAac}",
        )
    }

    val isTunnelingEnabled: Boolean
        get() = tunnelingEnabled

    val isAudioOnlyMode: Boolean
        get() = audioOnlyRequested

    fun setAudioOnlyMode(audioOnly: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { setAudioOnlyMode(audioOnly) }
            return
        }
        if (audioOnly == audioOnlyRequested) return
        audioOnlyRequested = audioOnly
        applyRendererEnablement()
    }

    private fun applyRendererEnablement() {
        val exo = internalPlayer ?: return
        if (videoRendererIndices.isEmpty()) return
        val disable = audioOnlyRequested || videoOutputInvalid
        if (disable == videoRenderersDisabled) return
        val rendererCount = exo.rendererCount
        val builder = trackSelector.buildUponParameters()
        var applied = 0
        for (index in videoRendererIndices) {
            if (index < 0 || index >= rendererCount) continue
            builder.setRendererDisabled(index, disable)
            applied++
        }
        if (applied == 0) return
        videoRenderersDisabled = disable
        trackSelector.setParameters(builder.build())
        LOG.i(
            "echo-music audio-only mode=$disable videoRenderers=$applied" +
                " requested=$audioOnlyRequested outputInvalid=$videoOutputInvalid",
        )
    }

    val videoDecoderName: String
        get() = PlayerCodecStats.videoDecoderName

    val audioCodecChoice: AudioCodecChoice?
        get() = AudioCodecProbe.liveInfo(audioRenderersFactory?.audioRenderer)

    val audioRendererName: String?
        get() = AudioCodecProbe.rendererClassName(audioRenderersFactory?.audioRenderer)

    val lastErrorKind: Int
        get() = lastErrorKindValue

    val droppedFrames: Long
        get() = droppedFramesTotal

    val rebufferCount: Int
        get() = rebufferCountTotal

    fun applyVideoEffects(effects: List<Effect>) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { applyVideoEffects(effects) }
            return
        }
        val exo = internalPlayer ?: return
        try {
            exo.setVideoEffects(effects)
            videoEffectsOpen = true
        } catch (th: Throwable) {
            videoEffectsOpen = false
            LOG.e("PlayerEngine", "echo-picture-effects apply failed", th)
        }
    }

    val isPictureEffectsActive: Boolean
        get() = videoEffectsOpen

    val isPictureHdrSource: Boolean
        get() = pictureHdrSource

    fun notifyVideoOutputResolution(width: Int, height: Int) {
        val exo = internalPlayer ?: return
        if (width <= 0 || height <= 0) return
        PictureEffects.setOutputCanvas(width, height)
        val sizeChanged = width != lastOutputWidth || height != lastOutputHeight
        lastOutputWidth = width
        lastOutputHeight = height
        LOG.i(
            "echo-output-size: ${width}x$height changed=$sizeChanged" +
                " renderers=" + videoRenderers.size + " effects=" + videoEffectsOpen +
                " outputInvalid=" + videoOutputInvalid + " surfacePresent=" + outputSurfacePresent,
        )
        if (!outputSurfacePresent) {
            pendingOutputWidth = width
            pendingOutputHeight = height
            return
        }
        pendingOutputWidth = 0
        pendingOutputHeight = 0
        sendOutputResolution(width, height)
        if (RedrawPolicy.shouldRedrawOnGeometry(sizeChanged, isPlaying, redrawReady())) {
            redrawVideoFrame()
        }
    }

    private fun sendOutputResolution(width: Int, height: Int) {
        val exo = internalPlayer ?: return
        for (renderer in videoRenderers) {
            try {
                exo.createMessage(renderer)
                    .setType(Renderer.MSG_SET_VIDEO_OUTPUT_RESOLUTION)
                    .setPayload(Size(width, height))
                    .send()
            } catch (th: Throwable) {
                LOG.e("PlayerEngine", "echo-picture-output-resolution failed", th)
            }
        }
    }

    private fun redrawReady(): Boolean {
        if (!videoEffectsOpen) return false
        for (renderer in videoRenderers) {
            if (renderer is ReplayableCacheVideoRenderer) return true
        }
        return false
    }

    fun redrawVideoFrame() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { redrawVideoFrame() }
            return
        }
        if (redrawScheduled || !redrawReady()) return
        redrawScheduled = true
        mainHandler.post {
            redrawScheduled = false
            val exo = internalPlayer ?: return@post
            if (!videoEffectsOpen) return@post
            try {
                exo.setVideoEffects(VideoFrameProcessor.REDRAW)
            } catch (th: Throwable) {
                LOG.e("PlayerEngine", "echo-picture-redraw failed", th)
            }
        }
    }

    private fun disableFrameRateMatching() {
        val exo = internalPlayer ?: return
        for (renderer in videoRenderers) {
            try {
                exo.createMessage(renderer)
                    .setType(Renderer.MSG_SET_CHANGE_FRAME_RATE_STRATEGY)
                    .setPayload(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF)
                    .send()
                LOG.i("echo-frameRate matching OFF -> ${renderer.javaClass.simpleName}")
            } catch (th: Throwable) {
                LOG.i("echo-frameRate matching OFF failed: $th")
            }
        }
    }

    val measuredFrameRate: Float
        get() = measuredFrameRateValue

    fun sampleFrameRate() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (frameRateWindowStartMs == 0L) {
            frameRateWindowStartMs = now
            renderedFrameCount.set(0)
            return
        }
        val elapsed = now - frameRateWindowStartMs
        if (elapsed < 1000) return
        measuredFrameRateValue = renderedFrameCount.getAndSet(0) * 1000f / elapsed
        frameRateWindowStartMs = now
    }

    fun setFrameRateTracking(enabled: Boolean) {
        frameRateTracking = enabled
        frameRateWindowStartMs = 0
        renderedFrameCount.set(0)
        if (enabled) measuredFrameRateValue = 0f
        applyFrameRateTracking()
    }

    private fun applyFrameRateTracking() {
        val exo = internalPlayer ?: return
        if (frameRateTracking) {
            exo.setVideoFrameMetadataListener(videoFrameListener)
        } else {
            exo.clearVideoFrameMetadataListener(videoFrameListener)
        }
    }

    fun setOnCuesListener(listener: ((List<Cue>) -> Unit)?) {
        trackSelection.setOnCuesListener(listener)
    }

    fun setInternalSubtitleDelay(milliseconds: Int) {
        trackSelection.setInternalSubtitleDelay(milliseconds)
    }

    fun getSelectedVideoFormat(): Format? {
        return trackSelection.getSelectedVideoFormat()
    }

    fun getSelectedAudioFormat(): Format? {
        return trackSelection.getSelectedAudioFormat()
    }

    private fun reportVideoSizeFromTracks(tracks: Tracks) {
        if (!videoEffectsOpen) return
        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_VIDEO || !group.isSelected) continue
            for (i in 0 until group.length) {
                if (!group.isTrackSelected(i)) continue
                val format = group.getTrackFormat(i)
                if (format.width <= 0 || format.height <= 0) return
                var width = format.width
                var height = format.height
                if (format.rotationDegrees == 90 || format.rotationDegrees == 270) {
                    val rotated = width
                    width = height
                    height = rotated
                }
                pictureHdrSource = ColorInfo.isTransferHdr(format.colorInfo)
                LOG.i(
                    "echo-picture-size: ${width}x$height rotation=${format.rotationDegrees} hdr=$pictureHdrSource",
                )
                videoSizeListener?.onVideoSizeChanged(width, height, 0)
                return
            }
        }
    }

    private fun handlePlayerError(error: PlaybackException) {
        val codeName = error.errorCodeName
        lastErrorKindValue = classifyError(codeName)
        LOG.e("Tvbox-runtime", "echo-Exo player error: $currentPlayPath", error)
        val sb = StringBuilder("echo-exo-player-error: code=").append(codeName).append(", msg=").append(error.message)
        var cause = error.cause
        var i = 0
        while (cause != null && i < 5) {
            sb.append(" | cause[").append(i).append("]=")
                .append(cause.javaClass.simpleName).append(": ").append(cause.message)
            cause = cause.cause
            i++
        }
        LOG.i(sb.toString())
        if (retryAsHls(error)) {
            return
        }
        for (listener in ArrayList(errorListeners)) {
            listener.onPlayerError(error, lastErrorKindValue)
        }
    }

    private fun retryAsHls(error: PlaybackException): Boolean {
        val exo = internalPlayer ?: return false
        val path = currentPlayPath ?: return false
        if (retriedAsHls || !isParsingError(error)) {
            return false
        }
        retriedAsHls = true
        LOG.i("echo-Exo retry as HLS: $path")
        val hlsSource = mediaSources.getHlsMediaSource(path, copyHeaders(currentHeaders)) ?: return false
        mediaSource = hlsSource
        retryAsHlsListener?.invoke()
        exo.setMediaSource(hlsSource, startPositionMs)
        startPositionApplied = true
        exo.prepare()
        exo.playWhenReady = true
        return true
    }

    private fun copyHeaders(headers: Map<String, String>?): Map<String, String>? =
        headers?.let { HashMap(it) }

    companion object {

        const val ERROR_KIND_UNKNOWN = 0
        const val ERROR_KIND_NETWORK = 1
        const val ERROR_KIND_DECODE = 2

        @JvmStatic
        fun classifyError(codeName: String?): Int {
            if (codeName == null) return ERROR_KIND_UNKNOWN
            if (codeName.startsWith("ERROR_CODE_IO") || codeName.startsWith("ERROR_CODE_PARSING")) return ERROR_KIND_NETWORK
            if (codeName.startsWith("ERROR_CODE_DECOD")) return ERROR_KIND_DECODE
            return ERROR_KIND_UNKNOWN
        }

        @JvmStatic
        fun isParsingError(error: PlaybackException?): Boolean {
            val errorCode = error?.errorCode ?: return false
            return errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
                errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ||
                errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED
        }
    }

    fun interface VideoSizeListener {
        fun onVideoSizeChanged(width: Int, height: Int, unappliedRotationDegrees: Int)
    }

    fun getTrackInfo(): TrackInfo {
        return trackSelection.getTrackInfo()
    }

    fun setTrack(track: TrackInfoBean?) {
        trackSelection.setTrack(track)
    }

    fun selectTrack(track: TrackInfoBean?) {
        trackSelection.selectTrack(track)
    }

    fun restoreTracks() {
        trackSelection.restoreTracks()
    }

    fun loadDefaultSubtitleTrack() {
        trackSelection.loadDefaultSubtitleTrack()
    }

    fun ensureSubtitleTrackSelected() {
        trackSelection.ensureSubtitleTrackSelected()
    }

    fun resetTrackSelection() {
        trackSelection.resetTrackSelection()
    }

    fun setContentKey(key: String?) {
        trackSelection.setContentKey(key)
    }

    fun setUseDiskCache(enabled: Boolean) {
        useDiskCache = enabled
    }

}
