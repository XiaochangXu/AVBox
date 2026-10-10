package com.github.tvbox.osc.player

import android.app.Activity
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Parcelable
import android.text.TextUtils
import android.view.Gravity
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.github.tvbox.osc.player.host.EngineSurfaceRenderViewFactory
import com.github.tvbox.osc.player.host.EngineTextureRenderViewFactory
import com.github.tvbox.osc.player.host.PlayerRenderView
import com.github.tvbox.osc.player.host.PlayerRenderViewFactory
import com.github.tvbox.osc.player.host.PlayerAudioFocus
import com.github.tvbox.osc.player.host.AudioFocusTarget
import com.github.tvbox.osc.player.state.PlayState
import com.github.tvbox.osc.util.LOG
import com.github.tvbox.osc.util.PlayerUtils
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

open class AppPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    interface ProgressSink {
        fun saveProgress(url: String?, progress: Long)

        fun getSavedProgress(url: String?): Long
    }

    protected var mMediaPlayer: KernelPlayer? = null

    protected var mProgressSink: ProgressSink? = null

    protected val mPlayerContainer: FrameLayout = FrameLayout(context)

    protected var mRenderView: PlayerRenderView? = null

    protected var mRenderViewFactory: PlayerRenderViewFactory =
        if (com.github.tvbox.osc.util.KV.get(com.github.tvbox.osc.util.HawkConfig.PLAY_RENDER, 1) == 1) {
            EngineSurfaceRenderViewFactory.create()
        } else {
            EngineTextureRenderViewFactory.create()
        }

    protected var mCurrentScreenScaleType = SCREEN_SCALE_DEFAULT

    protected var mVideoSize = intArrayOf(0, 0)

    protected var mUrl: String? = null

    protected var mProgressKey: String? = null

    protected var mHeaders: Map<String, String>? = null

    protected var mCurrentPosition = 0L

    private var mLastReportedPlayState = PlayState.IDLE

    private val _playStateFlow = MutableSharedFlow<PlayState>(extraBufferCapacity = PLAY_STATE_FLOW_BUFFER)

    val playStateFlow: SharedFlow<PlayState> = _playStateFlow

    protected var mCurrentPlayerState = PLAYER_NORMAL

    protected var mEnableAudioFocus = true

    private var mAudioFocusHelper: PlayerAudioFocus? = null

    private val audioFocusTarget = object : AudioFocusTarget {
        override fun isPlaybackPlaying(): Boolean = isPlaying

        override fun isPlaybackMuted(): Boolean = false

        override fun startPlayback() {
            start()
        }

        override fun pausePlayback() {
            pause()
        }

        override fun setPlaybackVolume(volume: Float) {
            mMediaPlayer?.setVolume(volume, volume)
        }
    }

    init {
        mPlayerContainer.setBackgroundColor(Color.BLACK)
        addView(
            mPlayerContainer,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        mEnableAudioFocus = true
    }

    open fun start() {
        if (isInIdleState() || isInStartAbortState()) {
            startPlay()
            return
        }
        startInPlaybackState()
    }

    protected open fun startPlay(): Boolean {
        if (showNetWarning()) {
            mMediaPlayer?.abortStart()
            dispatchPlayState(PlayState.START_ABORT)
            return false
        }
        if (mEnableAudioFocus) {
            ensureAudioFocusHelper()
            mAudioFocusHelper?.onNewPlayback()
        }
        mProgressSink?.let { sink ->
            mCurrentPosition = sink.getSavedProgress(progressKey())
        }
        mMediaPlayer?.release()
        mMediaPlayer = null
        initPlayer()
        addDisplay()
        startPrepare(false)
        return true
    }

    protected open fun showNetWarning(): Boolean = false

    protected open fun initPlayer() {
        val player = createPlayer()
        player.setPlayerEventListener(kernelEventListener)
        mMediaPlayer = player
        setInitOptions()
        player.initPlayer()
        setOptions()
    }

    protected open fun createPlayer(): KernelPlayer = ExoPlayer(context)

    protected open fun setInitOptions() = Unit

    protected open fun setOptions() {
        mMediaPlayer?.setLooping(false)
        mMediaPlayer?.setVolume(1.0f, 1.0f)
    }

    open fun prewarmKernel() {
        if (mMediaPlayer != null) return
        ensureAudioFocusHelper()
        initPlayer()
        addDisplay()
    }

    private fun ensureAudioFocusHelper() {
        if (mEnableAudioFocus && mAudioFocusHelper == null) {
            mAudioFocusHelper = PlayerAudioFocus(context, audioFocusTarget)
        }
    }

    protected open fun addDisplay() {
        mRenderView?.let { render ->
            mPlayerContainer.removeView(render.getView())
            mMediaPlayer?.detachVideoSurface()
            render.release()
        }
        val render = mRenderViewFactory.createRenderView(context)
        mMediaPlayer?.let { render.attachToPlayer(it) }
        mRenderView = render
        mPlayerContainer.addView(
            render.getView(),
            0,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER),
        )
    }

    protected fun startPrepare(reset: Boolean) {
        startPrepare(reset, false)
    }

    protected fun startPrepare(reset: Boolean, rebindRenderView: Boolean) {
        if (reset) {
            mMediaPlayer?.reset()
            setOptions()
            if (rebindRenderView) {
                mMediaPlayer?.let { player -> mRenderView?.attachToPlayer(player) }
            }
        }
        if (prepareDataSource()) {
            mMediaPlayer?.let { player ->
                player.setStartPosition(mCurrentPosition)
                player.prepareAsync()
            }
            reportPlayState()
            setPlayerState(PLAYER_NORMAL)
        }
    }

    protected open fun prepareDataSource(): Boolean {
        val url = mUrl
        if (!TextUtils.isEmpty(url)) {
            mMediaPlayer?.setDataSource(url!!, mHeaders)
            return true
        }
        return false
    }

    protected fun startInPlaybackState() {
        if (mMediaPlayer?.start() != true) return
        reportPlayState()
        if (!isMute()) {
            mAudioFocusHelper?.requestFocus()
        }
        mPlayerContainer.keepScreenOn = true
    }

    open fun pause() {
        if (mMediaPlayer?.pause() != true) return
        reportPlayState()
        if (!isMute()) {
            mAudioFocusHelper?.abandonFocus()
        }
        mPlayerContainer.keepScreenOn = false
    }

    open fun resume() {
        if (isInPlaybackState() && mMediaPlayer?.isPlaying == false) {
            resumePlay()
        }
    }

    private fun resumePlay() {
        if (mMediaPlayer?.start() != true) return
        reportPlayState()
        if (!isMute()) {
            mAudioFocusHelper?.requestFocus()
        }
        mPlayerContainer.keepScreenOn = true
    }

    open fun stopPlaybackKeepPlayer() {
        if (mMediaPlayer?.stop() != true) return
        reportPlayState()
    }

    open fun saveCurrentProgress() {
        saveProgress()
    }

    open fun release() {
        val hadActiveState = mLastReportedPlayState != PlayState.IDLE
        if (hadActiveState) captureLivePosition()
        mAudioFocusHelper?.abandonFocus()
        mAudioFocusHelper = null
        mMediaPlayer?.release()
        mMediaPlayer = null
        if (hadActiveState) {
            mRenderView?.let { render ->
                mPlayerContainer.removeView(render.getView())
                render.release()
            }
            mRenderView = null
            mPlayerContainer.keepScreenOn = false
            saveProgress()
            mCurrentPosition = 0
            reportPlayState()
        }
        mVideoSize[0] = 0
        mVideoSize[1] = 0
    }

    protected fun saveProgress() {
        val sink = mProgressSink ?: return
        captureLivePosition()
        if (mCurrentPosition > 0) {
            LOG.d("AppPlayerView", "saveProgress: " + mCurrentPosition)
            sink.saveProgress(progressKey(), mCurrentPosition)
        }
    }

    private fun captureLivePosition() {
        if (!isInPlaybackState()) return
        val live = mMediaPlayer?.currentPosition ?: 0L
        if (live > 0) mCurrentPosition = live
    }

    open fun resumePositionForReplay(fallback: Long): Long {
        if (isInPlaybackState()) {
            val live = mMediaPlayer?.currentPosition ?: 0L
            if (live > 0) return live
        }
        if (mCurrentPosition > 0) return mCurrentPosition
        return fallback
    }

    protected fun progressKey(): String? = mProgressKey ?: mUrl

    protected fun isInPlaybackState(): Boolean =
        mMediaPlayer?.playState?.isInPlaybackState == true

    protected fun isInIdleState(): Boolean =
        (mMediaPlayer?.playState ?: PlayState.IDLE) == PlayState.IDLE

    private fun isInStartAbortState(): Boolean = mMediaPlayer?.playState == PlayState.START_ABORT

    private val kernelEventListener = object : KernelPlayer.Listener {

        override fun onPrepared() {
            val player = mMediaPlayer ?: return
            if (mCurrentPosition > 0 && !player.isStartPositionApplied()) {
                player.seekTo(mCurrentPosition)
            }
            dispatchPlayState(PlayState.PREPARED)
            if (!isMute()) {
                mAudioFocusHelper?.requestFocus()
            }
        }

        override fun onKernelPlayStateChanged() {
            reportPlayState()
        }

        override fun onInfo(what: Int, extra: Int) {
            when (what) {
                KernelPlayer.MEDIA_INFO_BUFFERING_START -> reportPlayState()

                KernelPlayer.MEDIA_INFO_BUFFERING_END -> reportPlayState()

                KernelPlayer.MEDIA_INFO_RENDERING_START -> {
                    reportPlayState()
                    if (mMediaPlayer?.playState == PlayState.PLAYING) {
                        mPlayerContainer.keepScreenOn = true
                    }
                }

                KernelPlayer.MEDIA_INFO_VIDEO_ROTATION_CHANGED ->
                    mRenderView?.setVideoRotation(extra)
            }
        }

        override fun onError() {
            mPlayerContainer.keepScreenOn = false
            reportPlayState()
        }

        override fun onCompletion() {
            mPlayerContainer.keepScreenOn = false
            mCurrentPosition = 0
            mProgressSink?.saveProgress(progressKey(), 0L)
            reportPlayState()
        }

        override fun onVideoSizeChanged(width: Int, height: Int) {
            mVideoSize[0] = width
            mVideoSize[1] = height
            onVideoSizeReported(width, height)
            mVideoController?.onVideoSizeChanged(width, height)
            mRenderView?.let { render ->
                render.setScaleType(mCurrentScreenScaleType)
                render.setVideoSize(width, height)
            }
        }
    }

    protected open fun onVideoSizeReported(width: Int, height: Int) = Unit

    @get:JvmName("getMediaPlayer")
    val mediaPlayer: KernelPlayer?
        get() = mMediaPlayer

    open val duration: Long
        get() = if (isInPlaybackState()) mMediaPlayer?.duration ?: 0L else 0L

    open val currentPosition: Long
        get() {
            if (isInPlaybackState()) {
                mCurrentPosition = mMediaPlayer?.currentPosition ?: 0L
                return mCurrentPosition
            }
            return 0
        }

    open fun seekTo(pos: Long) {
        if (isInPlaybackState()) {
            mMediaPlayer?.seekTo(pos)
        }
    }

    open fun selectTrack(track: TrackInfoBean) {
        mMediaPlayer?.setTrack(track)
        reportPlayState()
    }

    open val isPlaying: Boolean
        get() = isInPlaybackState() && mMediaPlayer?.isPlaying == true

    val bufferedPercentage: Int
        get() = mMediaPlayer?.bufferedPercentage ?: 0

    open val tcpSpeed: Long
        get() = mMediaPlayer?.tcpSpeed ?: 0L

    open fun setSpeed(speed: Float) {
        if (isInPlaybackState()) mMediaPlayer?.setSpeed(speed)
    }

    open val speed: Float
        get() = if (isInPlaybackState()) mMediaPlayer?.speed ?: 1f else 1f

    open fun isMute(): Boolean = false

    open fun setUrl(url: String) {
        setUrl(url, null)
    }

    open fun setUrl(url: String, headers: Map<String, String>?) {
        mUrl = url
        mHeaders = headers
        mVideoSize[0] = 0
        mVideoSize[1] = 0
        mVideoController?.onVideoSizeCleared()
    }

    /** 当前已下发给播放器的地址，用于判断播放器是否已完成内容切换。 */
    val currentUrl: String?
        get() = mUrl

    open fun forgetVideoSize() {
        mVideoSize[0] = 0
        mVideoSize[1] = 0
    }

    open fun setProgressKey(key: String?) {
        mProgressKey = key
    }

    open fun setProgressSink(sink: ProgressSink?) {
        mProgressSink = sink
    }

    open fun skipPositionWhenPlay(position: Int) {
        mCurrentPosition = position.toLong()
    }

    open fun replay(resetPosition: Boolean) {
        if (resetPosition) {
            mCurrentPosition = 0
        }
        val player = mMediaPlayer
        if (player == null) {
            LOG.i("replay() called without kernel, fallback to start()")
            start()
            return
        }
        if (mEnableAudioFocus) {
            mAudioFocusHelper?.onNewPlayback()
        }
        player.resetTrackSelection()
        if (player.keepRenderViewOnReset()) {
            player.reset()
            setOptions()
            player.setOptions()
            startPrepare(false)
        } else {
            startPrepare(true, true)
        }
    }

    open fun factoryRenderType(): Int =
        if (mRenderViewFactory is EngineTextureRenderViewFactory) 0 else 1

    open fun setRenderViewFactory(factory: PlayerRenderViewFactory) {
        mRenderViewFactory = factory
    }

    open fun needsRenderRebuild(targetRenderType: Int): Boolean {
        val render = mRenderView ?: return false
        return (targetRenderType == 1) != (render.getView() is SurfaceView)
    }

    open fun setScreenScaleType(screenScaleType: Int) {
        mCurrentScreenScaleType = screenScaleType
        mRenderView?.setScaleType(screenScaleType)
    }

    open val videoSize: IntArray
        get() = mVideoSize

    fun getCurrentPlayerState(): Int = mCurrentPlayerState

    @Suppress("UNUSED_PARAMETER")
    open fun setMute(isMute: Boolean) = Unit

    private fun dispatchPlayState(playState: PlayState) {
        mLastReportedPlayState = playState
        mVideoController?.setPlayState(playState)
        if (!_playStateFlow.tryEmit(playState)) {
            LOG.e("echo-player playState-flow-drop: " + playState)
        }
    }

    private fun reportPlayState() {
        val state = mMediaPlayer?.playState ?: PlayState.IDLE
        if (state == mLastReportedPlayState) return
        dispatchPlayState(state)
    }

    protected fun setPlayerState(playerState: Int) {
        mCurrentPlayerState = playerState
        mVideoController?.setPlayerState(playerState)
    }

    interface VideoControllerHost {
        fun setPlayState(playState: PlayState)

        fun setPlayerState(playerState: Int)

        fun onVideoSizeChanged(width: Int, height: Int)

        fun onVideoSizeCleared()

        fun startProgress()

        /**
         * 控制器挂载到播放器容器上的 View。
         *
         * 默认返回控制器自身（直播等仍以 View 实现的控制器无需覆写）；
         * 纯 Compose 控制器本身不是 View，应覆写此方法返回其 ComposeView 宿主。
         */
        fun controllerView(): View? = this as? View
    }

    protected var mVideoController: VideoControllerHost? = null

    open val videoController: VideoControllerHost?
        get() = mVideoController

    open fun setVideoController(controller: VideoControllerHost?) {
        val old = mVideoController
        old?.controllerView()?.let { mPlayerContainer.removeView(it) }
        mVideoController = controller
        if (controller != null) {
            controller.controllerView()?.let { attach ->
                mPlayerContainer.addView(
                    attach,
                    LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
                )
            }
            val state = mMediaPlayer?.playState ?: PlayState.IDLE
            controller.setPlayState(state)
            controller.setPlayerState(mCurrentPlayerState)
            if (state != PlayState.IDLE && state != PlayState.ERROR) {
                controller.startProgress()
            }
        }
    }

    open fun releaseController(): VideoControllerHost? {
        val old = mVideoController
        setVideoController(null)
        return old
    }

    open fun isFullScreen(): Boolean = false

    open fun togglePlay() {
        if (isPlaying) pause() else start()
    }

    open fun doScreenShot(): Bitmap? = mRenderView?.doScreenShot()

    open fun onBackPressed(): Boolean = false

    open fun attachContainerTo(host: ViewGroup?) {
        if (host == null) return
        val parent = mPlayerContainer.parent as? ViewGroup
        if (parent === host) return
        mMediaPlayer?.detachVideoSurface()
        parent?.removeView(mPlayerContainer)
        val lp = mPlayerContainer.layoutParams ?: LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT,
        )
        host.addView(mPlayerContainer, 0, lp)
    }

    open fun detachContainerFromHost() {
        val parent = mPlayerContainer.parent as? ViewGroup
        if (parent == null || parent === this) return
        mMediaPlayer?.detachVideoSurface()
        parent.removeView(mPlayerContainer)
        addView(
            mPlayerContainer,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
    }

    open fun isContainerAttachedTo(host: ViewGroup?): Boolean =
        host != null && mPlayerContainer.parent === host

    open fun playerContainer(): FrameLayout = mPlayerContainer

    open fun bringControllerToFront() {
        mVideoController?.controllerView()?.bringToFront()
    }

    fun hostActivity(): Activity? = PlayerUtils.scanForActivity(context)

    open val renderIsSurface: Boolean
        get() = mRenderView?.getView() is SurfaceView

    protected fun renderView(): PlayerRenderView? = mRenderView

    protected fun renderViewFactory(): PlayerRenderViewFactory = mRenderViewFactory

    override fun onSaveInstanceState(): Parcelable? {
        saveProgress()
        return super.onSaveInstanceState()
    }

    companion object {

        const val SCREEN_SCALE_DEFAULT = 0
        const val SCREEN_SCALE_16_9 = 1
        const val SCREEN_SCALE_4_3 = 2
        const val SCREEN_SCALE_MATCH_PARENT = 3
        const val SCREEN_SCALE_ORIGINAL = 4
        const val SCREEN_SCALE_CENTER_CROP = 5

        const val PLAYER_NORMAL = 10
        const val PLAYER_FULL_SCREEN = 11
        const val PLAYER_TINY_SCREEN = 12

        private const val PLAY_STATE_FLOW_BUFFER = 8
    }
}
