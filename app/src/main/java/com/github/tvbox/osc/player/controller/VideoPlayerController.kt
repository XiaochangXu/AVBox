package com.github.tvbox.osc.player.controller

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.platform.ComposeView
import androidx.media3.ui.SubtitleView
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.bean.SourceBean
import com.github.tvbox.osc.player.AppPlayerView
import com.github.tvbox.osc.player.MyVideoView
import com.github.tvbox.osc.player.state.LockVisibility
import com.github.tvbox.osc.player.state.PlayerUiState
import com.github.tvbox.osc.player.state.PlayState
import com.github.tvbox.osc.player.state.VideoSizeGate
import com.github.tvbox.osc.player.ui.PlayerSurfaceHost
import com.github.tvbox.osc.player.ui.VideoGestureHandler
import com.github.tvbox.osc.player.usecase.M3u8PurifyUseCase
import com.github.tvbox.osc.player.usecase.PlayerSwitchUseCase
import com.github.tvbox.osc.player.usecase.WebParseUseCase
import com.github.tvbox.osc.subtitle.widget.SimpleSubtitleView
import com.github.tvbox.osc.util.DanmuHelper
import com.github.tvbox.osc.util.LOG
import com.github.tvbox.osc.util.PlayerUtils
import com.github.tvbox.osc.util.SubtitleHelper
import org.json.JSONObject
import java.util.HashMap

/**
 * 播放器控制器：纯 Kotlin 实现，不再继承 View。
 *
 * 整个 UI 树由 [composeHost] 这一个 ComposeView 承载；字幕 / 歌词 / media3 字幕这三个
 * 必须保留的原生 View 改由 Compose 的 AndroidView 桥接，见 [PlayerSurfaceHost]。
 * 因此控制器不再参与 View 树的 addView，挂载交给 [AppPlayerView.setVideoController]。
 */
@Suppress("MemberVisibilityCanBePrivate")
class VideoPlayerController(
    val context: Context,
) : AppPlayerView.VideoControllerHost,
    PlayerControlApi {

    companion object {

        private const val LOCK_HIDE_DELAY_MS = 3000L
    }

    internal val state: PlayerUiState = PlayerUiState()

    val playerView: MyVideoView?
        get() = videoView

    val resources: Resources
        get() = context.resources

    /**
     * 由 Compose 宿主回填的尺寸。
     * 手势层用它把滑动位移换算成进度 / 亮度 / 音量，替代原先 View.width / View.height。
     */
    internal var width: Int = 0

    internal var height: Int = 0

    private var activityCache: Activity? = null

    val isLocked: Boolean
        get() = state.locked

    fun playerActivity(): Activity? =
        activityCache ?: PlayerUtils.scanForActivity(context)?.also { activityCache = it }
            ?: videoView?.hostActivity()

    internal fun gestureCanChangePosition(): Boolean = canChangePosition

    internal fun gestureEnableInNormal(): Boolean = enableInNormal

    internal fun gestureEnabled(): Boolean = gestureSwitch

    internal fun currentSpeed(): Float = playerView?.speed ?: 1f

    internal fun enterGestureSeek() {
        if (!state.dragging) {
            state.dragging = true
            if (!state.controlsVisible) actions.applyShowBottom()
            stopProgress()
        }
    }

    internal fun exitGestureSeek() {
        state.dragging = false
        startProgress()
        actions.keepControlsAlive()
    }

    internal fun saveProgressFromView() {
        videoView?.saveCurrentProgress()
    }

    internal fun showSlideHint(text: String, brightness: Boolean) {
        state.slideHintText = text
        state.slideHintBrightness = brightness
        state.slideHintVisible = true
    }

    private val tapConfirmRunnable = Runnable { confirmGestureTap() }

    internal fun onGestureTapPending() {
        uiHandler.removeCallbacks(tapConfirmRunnable)
        uiHandler.postDelayed(tapConfirmRunnable, gestureHandler.doubleTapTimeoutMs)
    }

    private fun confirmGestureTap() {
        gestureHandler.markSingleTapConfirmed()
    }

    fun togglePlayFromGesture() {
        videoView?.togglePlay()
    }

    fun seekToFromGesture(positionMs: Long) {
        videoView?.seekTo(positionMs)
    }

    fun setSpeedFromGesture(speed: Float) {
        videoView?.setSpeed(speed)
    }

    internal var videoView: MyVideoView? = null

    override fun setKernelProvider(view: MyVideoView?) {
        videoView = view
        view?.setVideoController(this)
    }

    internal lateinit var gestureActions: VideoGestureActionsImpl

    internal lateinit var gestureHandler: VideoGestureHandler

    internal lateinit var actions: PlayerActionsDelegate

    internal lateinit var config: PlayerConfigDelegate

    private var canChangePosition = true
    private var enableInNormal = false
    private var gestureSwitch = true

    internal lateinit var subtitleView: SimpleSubtitleView
        private set
    internal lateinit var lyricView: SimpleSubtitleView
        private set
    internal lateinit var exoSubtitleView: SubtitleView
        private set

    /**
     * 唯一的原生 View 宿主：内部 setContent 出整棵 Compose UI 树。
     * 由 [controllerView] 交给 AppPlayerView 挂到播放器容器上。
     */
    internal lateinit var composeHost: ComposeView
        private set

    internal val videoSizeGate = VideoSizeGate()

    internal var previewMode = false
    internal var speedOld = 1.0f
    private var skipEnd = true
    internal var isClickBackBtn = false
    private var showParseFlag = false
    internal var playerConfig: JSONObject? = null
    internal var listener: VodControlListener? = null

    internal val uiHandler by lazy { Handler(Looper.getMainLooper()) }

    private var progressTicking = false
    private val progressRunnable by lazy { Runnable { onProgressTick() } }

    private var contentUrl: String? = null

    private var progressPhase = ProgressPhase.IDLE

    override fun onContentUrlSet(url: String?) {
        if (url.isNullOrEmpty()) return
        contentUrl = url
        progressPhase = ProgressPhase.ACTIVE
    }
    internal val idleHideRunnable by lazy {
        Runnable {
            if (state.overlayPanelOpen) actions.keepControlsAlive() else actions.hideBottom()
        }
    }
    internal val lockHideRunnable by lazy { Runnable { state.lockState = LockVisibility.HIDDEN } }

    private val m3u8PurifyUseCase by lazy {
        M3u8PurifyUseCase(context, object : M3u8PurifyUseCase.Callback {
            override fun startPlayUrl(url: String?, headers: HashMap<String, String>?) {
                listener?.startPlayUrl(url ?: return, headers)
            }

            override fun onM3u8ProxyUrl(proxyUrl: String?, sourceUrl: String?) {
                listener?.onM3u8ProxyUrl(proxyUrl ?: return, sourceUrl ?: return)
            }
        })
    }
    private val webParseUseCase by lazy { WebParseUseCase() }

    init {
        gestureActions = VideoGestureActionsImpl(this)
        gestureHandler = VideoGestureHandler(gestureActions)
        actions = PlayerActionsDelegate(this)
        config = PlayerConfigDelegate(this)

        initNativeSubtitleViews()
        initComposeHost()

        state.sysTimeVisible = false
        state.isPortrait =
            resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        updateDanmuBtnState()
        updateDanmuSearchBtnState()
        initSubtitleInfo()
    }

    /**
     * 字幕 / 歌词 / media3 字幕只创建实例，不再 addView。
     * 它们由 [PlayerSurfaceHost] 里的 AndroidView 负责挂载与摆放。
     *
     * 注意：layoutParams 必须在这里声明。AndroidView 的宿主是按子 View 自身的
     * layoutParams 摆放的，若不声明则退化为 WRAP_CONTENT，单行字幕会贴左而不是居中、
     * media3 字幕的底距定位也会失准。
     */
    private fun initNativeSubtitleViews() {
        val vs5 = resources.getDimensionPixelSize(R.dimen.vs_5)
        val vs15 = resources.getDimensionPixelSize(R.dimen.vs_15)
        val vs20 = resources.getDimensionPixelSize(R.dimen.vs_20)

        subtitleView = SimpleSubtitleView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(typeface, Typeface.BOLD)
            setPadding(vs20, vs15, vs20, vs15)
            visibility = View.VISIBLE
        }

        exoSubtitleView = SubtitleView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            visibility = View.GONE
        }

        lyricView = SimpleSubtitleView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            gravity = Gravity.CENTER
            setTextColor(0xFF00FF00.toInt())
            setTypeface(typeface, Typeface.BOLD)
            textScaleX = 1.1f
            setPadding(vs5, vs20, vs5, vs20)
            visibility = View.GONE
        }
    }

    private fun initComposeHost() {
        composeHost = ComposeView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            // 替代原先 View.onDetachedFromWindow 的清理时机：宿主摘除即停掉所有 Handler 任务。
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit

                override fun onViewDetachedFromWindow(v: View) = onHostDetached()
            })
            setContent {
                PlayerSurfaceHost(this@VideoPlayerController)
            }
        }
    }

    override fun controllerView(): View = composeHost

    /** Compose 宿主尺寸变化时回填，并同步横竖屏状态。 */
    internal fun onHostSizeChanged(w: Int, h: Int) {
        width = w
        height = h
        initOrientationState()
    }

    /** 对应原 View.onDetachedFromWindow：宿主被摘除时清理挂起的回调。 */
    internal fun onHostDetached() {
        uiHandler.removeCallbacks(progressRunnable)
        progressTicking = false
        uiHandler.removeCallbacks(idleHideRunnable)
        uiHandler.removeCallbacks(lockHideRunnable)
        actions.cancelKeySeekCommit()
        config.cancelSpeedRetry()
        uiHandler.removeCallbacks(tapConfirmRunnable)
    }

    private fun initSubtitleInfo() {
        subtitleView.setTextSize(SubtitleHelper.getTextSize(playerActivity()).toFloat())
    }

    override fun setPlayState(playState: PlayState) {
        state.playState = playState
        if (playState != PlayState.IDLE && playState != PlayState.ERROR &&
            playState != PlayState.PREPARING
        ) {
            updateLiveButtonsState()
        }
        applyPlayState(playState)
    }

    private fun applyPlayState(playState: PlayState) = when (playState) {
        PlayState.IDLE -> {
            state.locked = false
            progressPhase = ProgressPhase.IDLE
        }
        PlayState.PLAYING -> {
            state.exitPaused = false
            initOrientationState()
            startProgress()
        }
        PlayState.ERROR -> listener?.errReplay()
        PlayState.PREPARED -> listener?.prepared()
        PlayState.COMPLETED -> {
            state.locked = false
            listener?.playNext(true)
        }
        PlayState.PREPARING, PlayState.BUFFERING, PlayState.BUFFERED,
        PlayState.START_ABORT, PlayState.PAUSED,
        -> Unit
    }

    override fun setPlayerState(playerState: Int) {
        state.playerState = playerState
    }

    override fun onVideoSizeChanged(width: Int, height: Int) {
        state.videoSize = videoSizeGate.textFor(width, height)
        onVideoSizeReady?.invoke(width, height)
    }

    internal var onVideoSizeReady: ((Int, Int) -> Unit)? = null

    override fun onVideoSizeCleared() {
        videoSizeGate.onKernelContentReplaced()
    }

    internal fun progressSnapshot(): ProgressSnapshot? {
        val view = videoView ?: return null
        if (!ProgressUiGate.accept(progressPhase, contentUrl, view.currentUrl)) return null
        return ProgressSnapshot(
            durationMs = PlayerUtils.safeTimeMs(view.duration),
            positionMs = PlayerUtils.safeTimeMs(view.currentPosition),
            bufferedPercent = runCatching { view.bufferedPercentage }.getOrDefault(0),
        )
    }

    private fun onProgressTick() {
        progressTicking = false
        val view = videoView
        val snapshot = if (view != null && !state.dragging) progressSnapshot() else null
        LOG.i(
            "echo-progress-tick: accepted=${snapshot != null} phase=$progressPhase"
                + " uiPosition=${state.position} uiDuration=${state.duration}"
                + " rawDuration=${snapshot?.durationMs} rawPosition=${snapshot?.positionMs}"
                + " playing=${view?.isPlaying} state=${view?.playState}",
        )
        if (snapshot != null) {
            if (snapshot.durationMs > 0) state.duration = snapshot.durationMs
            if (snapshot.positionMs > 0 || snapshot.durationMs > 0) state.position = snapshot.positionMs
            state.bufferedPercent = snapshot.bufferedPercent
            if (skipEnd && snapshot.positionMs != 0 && snapshot.durationMs != 0) {
                val et = playerConfig?.optInt("et", 0) ?: 0
                if (et > 0 && snapshot.positionMs + et * 1000 >= snapshot.durationMs) {
                    skipEnd = false
                    listener?.playNext(true)
                }
            }
        }
        if (state.dragging) return
        if (view?.isPlaying != true) return
        progressTicking = true
        val speed = view.speed.takeIf { it > 0f } ?: 1f
        val delayMs = ((1000 - state.position % 1000) / speed).toLong().coerceAtLeast(1L)
        uiHandler.postDelayed(progressRunnable, delayMs)
    }

    override fun startProgress() {
        if (progressTicking) return
        progressTicking = true
        uiHandler.post(progressRunnable)
    }

    internal fun stopProgress() {
        if (!progressTicking) return
        uiHandler.removeCallbacks(progressRunnable)
        progressTicking = false
    }

    internal fun updateSeekUiHint(curr: Int, seekTo: Int) {
        state.seekHintForward = seekTo > curr
        state.seekHintText = PlayerUtils.stringForTime(seekTo)
        state.seekHintVisible = true
    }

    internal fun isInPlaybackState(): Boolean {
        if (videoView == null) return false
        return when (state.playState) {
            PlayState.PLAYING, PlayState.PAUSED, PlayState.BUFFERING, PlayState.BUFFERED -> true
            else -> false
        }
    }

    override fun toggleControlBar() {
        actions.toggleControls()
    }

    internal fun showLockView() {
        if (previewMode) {
            state.locked = false
            uiHandler.removeCallbacks(lockHideRunnable)
            state.lockState = LockVisibility.GONE
            return
        }
        state.lockState = LockVisibility.SHOWN
        uiHandler.removeCallbacks(lockHideRunnable)
        if (state.locked) {
            uiHandler.postDelayed(lockHideRunnable, LOCK_HIDE_DELAY_MS)
        }
    }

    private fun initOrientationState() {
        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        state.isPortrait = isPortrait
        if (isPortrait) {
            state.backVisible = false
        }
    }

    private fun updateDanmuBtnState() {
        state.danmuOpen = DanmuHelper.isOpen()
    }

    internal fun updateDanmuSearchBtnState() {
        state.danmuSearchAvailable = ApiConfig.get().hasDanmuSearchUi()
    }

    private fun updateLiveButtonsState() {
        state.liveButtonsVisible = runCatching { videoView?.duration ?: 0L != 0L }.getOrDefault(true)
    }

    override fun getUiState(): PlayerUiState = state

    override fun getSubtitleView(): SimpleSubtitleView = subtitleView

    override fun getLyricView(): SimpleSubtitleView = lyricView

    override fun getExoSubtitleView(): SubtitleView = exoSubtitleView

    override fun setListener(l: VodControlListener?) {
        listener = l
    }

    override fun setPlayerConfig(playerCfg: JSONObject) {
        playerConfig = playerCfg
        config.updatePlayerCfgState()
    }

    override fun showParse(userJxList: Boolean) {
        showParseFlag = userJxList
        state.showParseRow = userJxList
    }

    override fun setPreviewMode(previewMode: Boolean) {
        this.previewMode = previewMode
        state.previewMode = previewMode
        if (previewMode && state.controlsVisible) actions.hideBottom()
        uiHandler.removeCallbacks(lockHideRunnable)
        state.lockState = LockVisibility.GONE
    }

    override fun setTitle(playTitleInfo: String) {
        state.title = playTitleInfo
    }

    override fun setUrlTitle(playTitleInfo: String) = Unit

    override fun setHasDanmu(hasDanmu: Boolean) {
        updateDanmuBtnState()
    }

    override fun setCanChangePosition(canChangePosition: Boolean) {
        this.canChangePosition = canChangePosition
    }

    override fun setEnableInNormal(enableInNormal: Boolean) {
        this.enableInNormal = enableInNormal
    }

    override fun setGestureEnabled(gestureEnabled: Boolean) {
        this.gestureSwitch = gestureEnabled
    }

    override fun hidePauseRoot() = Unit

    override fun onNewPlayStarted(sameContent: Boolean) {
        state.exitPaused = false
        if (sameContent) {
            LOG.i(
                "echo-progress-reset: same content restart, keep uiPosition=${state.position}"
                    + " uiDuration=${state.duration}",
            )
            return
        }
        progressPhase = ProgressPhase.LOADING
        val size = runCatching { videoView?.videoSize }.getOrNull() ?: intArrayOf(0, 0)
        LOG.i(
            "echo-progress-reset: onNewPlayStarted cleared uiPosition=${state.position}"
                + " uiDuration=${state.duration}",
        )
        state.videoSize = videoSizeGate.onNewSession(size[0], size[1])
        state.position = 0
        state.duration = 0
    }
    override fun setLifecyclePaused(paused: Boolean) {
        state.lifecyclePaused = paused
        if (paused) uiHandler.removeCallbacks(idleHideRunnable) else actions.keepControlsAlive()
    }

    override fun setExitPaused(paused: Boolean) {
        state.exitPaused = paused
    }

    override fun resetSpeed() {
        skipEnd = true
        config.applySpeedWhenReady()
    }

    override fun onBackPressed(): Boolean {
        if (isClickBackBtn) {
            isClickBackBtn = false
            if (state.controlsVisible) actions.hideBottom()
            return false
        }
        if (state.controlsVisible) {
            actions.hideBottom()
            return true
        }
        return false
    }

    override fun switchPlayer(): Boolean = PlayerSwitchUseCase.switchPlayer()

    override fun stopOther() {
        PlayerSwitchUseCase.stopOther()
    }

    override fun playM3u8(url: String?, headers: HashMap<String, String>?) {
        m3u8PurifyUseCase.playM3u8(url ?: return, headers)
    }

    override fun encodeUrl(url: String?): String = PlayerSwitchUseCase.encodeUrl(url)

    override fun firstUrlByArray(url: String?): String = PlayerSwitchUseCase.firstUrlByArray(url)

    override fun evaluateScript(sourceBean: SourceBean?, url: String?, view: WebView?) {
        webParseUseCase.evaluateScript(sourceBean, url, view)
    }

    override fun getWebPlayUrlIfNeeded(webPlayUrl: String?): String {
        return webParseUseCase.getWebPlayUrlIfNeeded(webPlayUrl) ?: ""
    }
}
