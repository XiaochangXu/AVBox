package com.github.tvbox.osc.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import androidx.appcompat.view.ContextThemeWrapper
import com.github.tvbox.osc.R
import com.github.tvbox.osc.player.host.EngineSurfaceRenderViewFactory
import com.github.tvbox.osc.player.host.EngineTextureRenderViewFactory
import com.github.tvbox.osc.player.state.PlayState
import com.github.tvbox.osc.player.usecase.PlayerSwitchUseCase
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.KV
import com.github.tvbox.osc.util.LOG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.HashMap

class PlaybackEngine(context: Context) : PlaybackHostApi {

    private val appContext: Context = context.applicationContext

    private val videoView: MyVideoView

    private val controller: PlaybackController = PlaybackController()

    private val headlessView: HeadlessView = HeadlessView()

    private val main: Handler = Handler(Looper.getMainLooper())

    private val stateScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val progressSampler: PlaybackProgressSampler = PlaybackProgressSampler(object : PlaybackProgressSampler.Host {
        override fun playerView(): MyVideoView = videoView

        override fun playbackController(): PlaybackController = controller

        override fun isLive(): Boolean = liveMode
    })

    private val progressSink: AppPlayerView.ProgressSink = object : AppPlayerView.ProgressSink {
        override fun saveProgress(url: String?, progress: Long) {
            progressSampler.onSinkSave(url, progress)
            if (controller.webPlayUrl() != null && progress > 0) {
                controller.markPlaybackStarted()
                activeView().hideTipOnUiThread()
            }
        }

        override fun getSavedProgress(url: String?): Long {
            return controller.getSavedProgress(url)
        }
    }

    private var pageRef: WeakReference<PlaybackPage>? = null
    private var session: PlaybackSession? = null
    private var released: Boolean = false

    private var liveMode: Boolean = false
    private var serviceLostKept: Boolean = false
    private var serviceLostWasPlaying: Boolean = false

    private val idleRelease: Runnable = Runnable {
        if (released || liveMode || attachedPage() != null) return@Runnable
        LOG.i(TAG + " idle release: no host for " + (IDLE_RELEASE_DELAY_MS / 1000) + "s")
        release()
        PlaybackService.onEngineReleased(this@PlaybackEngine)
    }

    init {
        LOG.i(TAG + " engine create")
        videoView = createPlayerView()
        controller.setViewBridge(headlessView)
        controller.initFetch()
        controller.initPreload()
    }

    fun player(): MyVideoView = videoView

    fun controller(): PlaybackController = controller

    fun attachedPage(): PlaybackPage? = pageRef?.get()

    fun headlessBridge(): PlaybackViewBridge = headlessView

    private fun createPlayerView(): MyVideoView {
        val ctx = ContextThemeWrapper(appContext, R.style.AppTheme_NoActionBar)
        val view = MyVideoView(ctx)
        view.setRenderViewFactory(
            if (KV.get(HawkConfig.PLAY_RENDER, 1) == 1) {
                EngineSurfaceRenderViewFactory.create()
            } else {
                EngineTextureRenderViewFactory.create()
            }
        )
        view.setExoDiskCacheEnabled(true)
        view.setProgressSink(progressSink)
        stateScope.launch {
            view.playStateFlow.collect { playState -> onPlayStateChanged(playState) }
        }
        return view
    }

    private fun onPlayStateChanged(playState: PlayState) {
        if (released) return
        progressSampler.onPlayStateChanged(playState)
        if (playState == PlayState.ERROR) {
            LOG.i(
                "echo-player error: kernel="
                    + (if (videoView.mediaPlayer == null) "null" else videoView.mediaPlayer!!.javaClass.simpleName)
                    + " pos=" + videoView.currentPosition
                    + " started=" + controller.isPlaybackStarted()
                    + " url=" + controller.webPlayUrl()
            )
        }
        if (playState == PlayState.PLAYING) {
            if (controller.isConfirmedAudioOnly()) {
                videoView.hideVideoFrameCover()
            } else {
                videoView.showVideoFrame()
            }
        }
        if (liveMode) return
        if (playState == PlayState.PLAYING) {
            controller.ensureAudioOnlyRender()
            controller.onPlayerStateForPreload(playState)
        }
        if (playState == PlayState.BUFFERING || playState == PlayState.BUFFERED) {
            controller.onPlayerStateForPreload(playState)
        }
        if (controller.webPlayUrl() != null && controller.isStartedPlayState(playState)) {
            controller.markPlaybackStarted()
            if (!released && !videoView.isVideoFrameCleared()) {
                activeView().hideTipOnUiThread()
            }
        }
        if (controller.handlePlayStateForMusicSession(playState)) {
            return
        }
        activeView().startDanmuIfReady()
    }

    fun enterLiveState(): Boolean {
        if (released || liveMode) return false
        val page = attachedPage()
        if (page != null) detach(page)
        controller.stopMusicSessionForFailedPlayback()
        PlaybackService.forceStopSession(appContext)
        liveMode = true
        serviceLostKept = false
        serviceLostWasPlaying = false
        setLiveFlag(true)
        cancelIdleRelease()
        LOG.i(TAG + " re-enter live state (after vod takeover)")
        session = null
        controller.clearStartedContent()
        videoView.setProgressSink(null)
        videoView.setExoDiskCacheEnabled(false)
        videoView.release()
        return true
    }

    fun enterLive() {
        if (released) return
        session = null
        controller.clearStartedContent()
        LOG.i(TAG + " enter live mode")
        val page = attachedPage()
        if (page != null) detach(page)
        liveMode = true
        serviceLostKept = false
        serviceLostWasPlaying = false
        setLiveFlag(true)
        cancelIdleRelease()
        releasePlayer()
        videoView.setProgressSink(null)
        videoView.setExoDiskCacheEnabled(false)
        videoView.clearArtwork()
        videoView.showVideoFrame()
        controller.stopMusicSessionForFailedPlayback()
        PlaybackService.forceStopSession(appContext)
    }

    fun exitLive() {
        if (released) return
        if (!liveMode) {
            LOG.i(TAG + " exit live skipped: player taken over by vod page")
            return
        }
        videoView.release()
        exitLiveState()
        videoView.releaseController()
        LOG.i(TAG + " live stream released")
        scheduleIdleRelease()
    }

    private fun scheduleIdleRelease() {
        main.removeCallbacks(idleRelease)
        if (released) return
        if (PrewarmPolicy.idleReleaseDelayMs(prewarmEnabled(), IDLE_RELEASE_DELAY_MS) == PrewarmPolicy.NO_IDLE_RELEASE) {
            LOG.i(TAG + " idle release suppressed: kernel prewarm on")
            return
        }
        main.postDelayed(idleRelease, IDLE_RELEASE_DELAY_MS)
    }

    private fun cancelIdleRelease() {
        main.removeCallbacks(idleRelease)
    }

    private fun setLiveFlag(live: Boolean) {
        KV.put(HawkConfig.PLAYER_IS_LIVE, live)
    }

    private fun exitLiveState() {
        if (released || !liveMode) return
        liveMode = false
        setLiveFlag(false)
        LOG.i(TAG + " exit live mode")
        videoView.setProgressSink(progressSink)
        videoView.setExoDiskCacheEnabled(true)
    }

    fun isLiveMode(): Boolean = liveMode

    fun isReleased(): Boolean = released

    /** 用户配置的渲染类型：1=SurfaceView，其余=TextureView（与 PlayerHelper.updateCfg 一致）。 */
    private fun configuredRenderType(): Int = KV.get(HawkConfig.PLAY_RENDER, 1)

    fun attach(page: PlaybackPage) {
        if (released) return
        if (liveMode) exitLiveState()
        pageRef = WeakReference(page)
        if (!videoView.isPlaying) videoView.coverVideoFrame()
        controller.setViewBridge(page.viewBridge())
        if (!page.isAudioOnlyPage()) {
            videoView.attachContainerTo(page.renderSlot())
            videoView.alignRenderViewToConfig(configuredRenderType())
        }
        cancelIdleRelease()
        consumeServiceLostKeep(false)
        LOG.i(TAG + " attach page=" + page.hashCode() + " key=" + (session?.playbackKey() ?: "-"))
    }

    fun detach(page: PlaybackPage) {
        detach(page, false)
    }

    fun detachForHandover(page: PlaybackPage) {
        detach(page, true)
    }

    private fun detach(page: PlaybackPage, keepPlayback: Boolean) {
        if (released) return
        if (liveMode) return
        videoView.saveCurrentProgress()
        val cur = attachedPage()
        if (cur != null && cur !== page) return
        pageRef = null
        if (!keepPlayback) {
            videoView.pause()
            videoView.stopPlaybackKeepPlayer()
            controller.stopPlaybackForPageExit()
            PlaybackService.forceStopSession(appContext)
        }
        videoView.releaseController()
        videoView.setDanmuView(null)
        videoView.detachContainerFromHost()
        controller.setViewBridge(headlessView)
        scheduleIdleRelease()
        LOG.i(TAG + (if (keepPlayback) " detach for handover page=" else " detach page=") + page.hashCode())
    }

    fun releasePlayer() {
        if (released) return
        LOG.i(TAG + " release player kernel (engine kept)")
        videoView.release()
        controller.clearStartedContent()
    }

    fun prewarmKernel() {
        if (released) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { prewarmKernel() }
            return
        }
        if (videoView.mediaPlayer != null) return
        try {
            PlayerHelper.updateCfg(videoView, JSONObject())
            videoView.prewarmKernel()
            LOG.i(TAG + " prewarm kernel")
        } catch (th: Throwable) {
            LOG.e(TAG + " prewarm failed: " + th.message)
        }
    }

    fun onPrewarmPreferenceChanged(enabled: Boolean) {
        if (released) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { onPrewarmPreferenceChanged(enabled) }
            return
        }
        if (enabled) {
            cancelIdleRelease()
            prewarmKernel()
            return
        }
        if (PrewarmPolicy.shouldScheduleOnDisable(attachedPage() != null || liveMode)) {
            scheduleIdleRelease()
        }
    }

    fun discardStartedContentOf(owners: List<String>) {
        if (released) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { discardStartedContentOf(owners) }
            return
        }
        val current = session
        if (current == null || owners.isEmpty()) return
        val owner = current.sourceKey() + "|" + current.vod().id
        if (!owners.contains(owner)) return
        LOG.i("echo-progress discard-session owner=" + owner)
        controller.clearStartedContent()
    }

    fun release() {
        if (released) return
        released = true
        serviceLostKept = false
        serviceLostWasPlaying = false
        setLiveFlag(false)
        LOG.i(TAG + " engine release")
        progressSampler.stop()
        val page = attachedPage()
        pageRef = null
        page?.onServiceStopped()
        controller.setViewBridge(headlessView)
        controller.onHostDestroy()
        PlaybackService.forceStopSession(appContext)
        videoView.releaseController()
        videoView.setDanmuView(null)
        videoView.detachContainerFromHost()
        videoView.release()
        controller.releaseFetch()
        controller.stopParse()
        controller.stopLoadWebView(true)
        stateScope.cancel()
        main.removeCallbacksAndMessages(null)
    }

    fun keepKernelAfterServiceDestroy() {
        if (released) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { keepKernelAfterServiceDestroy() }
            return
        }
        if (liveMode || !prewarmEnabled()) {
            LOG.i(TAG + " engine released (service destroyed, keep off)")
            release()
            PlaybackService.onEngineReleased(this@PlaybackEngine)
            return
        }
        serviceLostKept = true
        serviceLostWasPlaying = videoView.isPlaying
        if (serviceLostWasPlaying) videoView.pause()
        controller.stopParse()
        controller.stopLoadWebView(true)
        LOG.i(
            TAG + " engine kept (service destroyed, page=" + (attachedPage() != null)
                + ", playing=" + serviceLostWasPlaying + ")",
        )
    }

    fun consumeServiceLostKeep(resumePlayback: Boolean) {
        if (!serviceLostKept) return
        serviceLostKept = false
        val wasPlaying = serviceLostWasPlaying
        serviceLostWasPlaying = false
        if (resumePlayback && wasPlaying) videoView.resume()
        controller.updateMusicSession()
    }

    fun isServiceLostKept(): Boolean = serviceLostKept

    private fun activeView(): PlaybackViewBridge {
        val page = attachedPage()
        return page?.viewBridge() ?: headlessView
    }

    override fun setData(session: PlaybackSession) {
        this.session = session
        controller.startSession(session)
    }

    override fun play(reset: Boolean) {
        controller.play(reset)
    }

    override fun playNext(rmProgress: Boolean) {
    }

    override fun playPrevious() {
    }

    override fun selectQuality(position: Int): Boolean = false

    override fun setAutoSwitchLineEnabled(enabled: Boolean) {
    }

    override fun setPreviewMode(previewMode: Boolean) {
    }

    override fun toggleControllerControls() {
    }

    override fun onBackPressed(): Boolean = false

    override fun setExitingPreview(exitingPreview: Boolean) {
    }

    override fun setPlayTitle(show: Boolean) {
    }

    override fun stopForSourceSwitch(tip: String) {
        controller.markStoppedForSourceSwitch()
        controller.stopMusicSessionForFailedPlayback()
    }

    override fun clearSourceSwitchTip() {
    }

    override fun showCast() {
    }

    override fun onLocalSubtitlePicked(uri: Uri) {
    }

    override fun hostResume() {
        videoView.resume()
    }

    override fun hostPause() {
        if (!controller.isConfirmedAudioOnly()) videoView.pause()
    }

    override fun hostDestroy() {
    }

    override fun resumeFromMediaSession() {
        videoView.start()
        controller.updateMusicSession()
    }

    override fun pauseFromMediaSession() {
        videoView.pause()
        controller.updateMusicSession()
    }

    override fun stopFromMediaSession() {
        videoView.pause()
        controller.stopMusicSession()
    }

    override fun seekFromMediaSession(position: Long) {
        videoView.seekTo(position)
        controller.updateMusicSession()
    }

    private inner class HeadlessView : PlaybackViewBridge {

        override fun isPageAlive(): Boolean = !released

        override fun runOnUi(action: Runnable) {
            main.post(action)
        }

        override fun context(): Context = appContext

        override fun playbackHost(): PlaybackHostApi = this@PlaybackEngine

        override fun toast(text: CharSequence) {
        }

        override fun showTip(msg: String, loading: Boolean, error: Boolean) {
        }

        override fun hideTipOnUiThread() {
        }

        override fun showErrorWithRetry(err: String, finish: Boolean) {
        }

        override fun requestNotificationPermission() {
        }

        override fun playState(): PlayState = if (released) PlayState.IDLE else videoView.playState

        override fun currentPosition(): Long = if (released) 0 else videoView.currentPosition

        override fun duration(): Long = if (released) 0 else videoView.duration

        override fun isPlaying(): Boolean = !released && videoView.isPlaying

        override fun mediaPlayer(): KernelPlayer? = if (released) null else videoView.mediaPlayer

        override fun isKernelErrored(): Boolean = !released && videoView.isKernelErrored()

        override fun currentUrl(): String? = if (released) null else videoView.currentUrl

        override fun onContentUrlSet(url: String?) {
            // 无头桥接没有进度条；详情页路径由 PlayContainerViewBridge 直接转给页面控制器。
        }

        override fun releasePlayer() {
            this@PlaybackEngine.releasePlayer()
        }

        override fun setTitle(title: String) {
        }

        override fun stopOtherPlayers() {
        }

        override fun resetDanmu() {
        }

        override fun clearLyric() {
        }

        override fun clearArtwork() {
            if (!released) videoView.clearArtwork()
        }

        override fun clearVideoFrame() {
            if (!released) videoView.clearVideoFrame()
        }

        override fun setSubtitleViewVisible(visible: Boolean) {
        }

        override fun onNewPlayStarted(sameContent: Boolean) {
        }

        override fun applyPlayerConfigToView(forceKernel: Int) {
        }

        override fun useTextureRenderForAudio() {
        }

        override fun switchRenderToTexture() {
            if (!released && videoView.renderIsSurface) videoView.switchRenderToTexture()
        }

        override fun ensureRenderViewMatchesConfig() {
            if (!released) videoView.ensureRenderViewMatchesConfig()
        }

        override fun setAudioOnlyMode(audioOnly: Boolean) {
            if (released) return
            if (!audioOnly) {
                videoView.alignRenderViewToConfig(configuredRenderType())
            }
            (videoView.mediaPlayer as? ExoPlayer)?.setAudioOnlyMode(audioOnly)
        }

        override fun isAudioOnlyMode(): Boolean {
            if (released) return false
            return (videoView.mediaPlayer as? ExoPlayer)?.isAudioOnlyMode() == true
        }

        override fun playExternalPlayer(
            playerType: Int,
            url: String,
            title: String,
            subtitle: String?,
            headers: HashMap<String, String>?,
            progress: Long,
        ): Boolean = false

        override fun playM3u8(url: String, headers: HashMap<String, String>) {
            startVideoPlayback(url, headers, false)
        }

        override fun playM3u8(url: String, headers: HashMap<String, String>?, gen: Int) {
            if (!controller.isParseResultCurrent(gen)) return
            startVideoPlayback(url, headers, false)
        }

        override fun startVideoPlayback(url: String, headers: HashMap<String, String>?, forceExoPlayer: Boolean) {
            if (released) return
            if (videoView.isKernelErrored()) {
                videoView.requireKernelRebuild()
                LOG.i(TAG + " rebuild errored kernel on start (headless)")
            }
            val kernelPresent = videoView.mediaPlayer != null
            val rebuildKernel = videoView.consumeKernelRebuildRequired()
            val reusePlayer =
                KernelReusePolicy.decide(kernelPresent, rebuildKernel, forceExoPlayer, true) == KernelDecision.REUSE
            val sameContent = reusePlayer && controller.isSameStartedContent()
            if (!reusePlayer && kernelPresent) releasePlayer()
            if (sameContent) videoView.saveCurrentProgress()
            videoView.setProgressKey(controller.progressKey())
            videoView.setTrackMemoryKey("")
            controller.markContentStarted()
            videoView.setUrl(url, headers)
            if (reusePlayer) {
                val base = controller.playTimeoutBasePosition()
                videoView.skipPositionWhenPlay((if (sameContent) videoView.resumePositionForReplay(base) else base).toInt())
                videoView.replay(false)
            } else {
                videoView.start()
            }
        }

        override fun switchPlayerKernel(): Boolean {
            return true
        }

        override fun applyPlayerConfig(cfg: JSONObject) {
        }

        override fun firstUrlByArray(url: String): String {
            return PlayerSwitchUseCase.firstUrlByArray(url)
        }

        override fun setArtwork(url: String) {
            if (!released) videoView.setArtwork(url)
        }

        override fun showParse(show: Boolean) {
        }

        override fun checkDanmu(danmaku: String, onFailed: Runnable?) {
        }

        override fun encodeUrl(url: String): String {
            return PlayerSwitchUseCase.encodeUrl(url)
        }

        override fun evaluateScript(url: String, webView: WebView?) {
        }

        override fun newSniffWebView(): WebView? {
            return null
        }

        override fun attachSniffWebView(webView: WebView) {
        }

        override fun startDanmuIfReady() {
        }

        override fun buildPreloadSnapshot(): PreloadCoordinator.Snapshot? {
            return null
        }

        override fun showPreloadReadyTip() {
        }

        override fun hidePreloadReadyTip() {
        }

        override fun onLinesExhausted(): Boolean {
            return false
        }
    }

    companion object {

        private const val TAG = "echo-p2"

        private const val IDLE_RELEASE_DELAY_MS = 60_000L

        private fun prewarmEnabled(): Boolean {
            return KV.get(HawkConfig.KERNEL_PREWARM, false)
        }
    }
}
