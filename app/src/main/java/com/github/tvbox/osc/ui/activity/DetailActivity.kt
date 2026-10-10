package com.github.tvbox.osc.ui.activity

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.setContent
import com.github.tvbox.osc.ui.theme.enableTransparentEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.ui.components.SheetHostScaffold
import com.github.tvbox.osc.player.PageHost
import com.github.tvbox.osc.player.PlaybackController
import com.github.tvbox.osc.player.PlaybackService
import com.github.tvbox.osc.player.state.PlayState
import com.github.tvbox.osc.ui.player.PlayContainer
import com.github.tvbox.osc.ui.theme.AVBoxTheme
import com.github.tvbox.osc.ui.theme.AppThemeState
import com.github.tvbox.osc.util.LOG
import com.github.tvbox.osc.util.MusicSettings
import com.github.tvbox.osc.util.PermissionHelper
import kotlinx.coroutines.launch

private const val SYSBAR_APPEARANCE_REASSERT_DELAY_MS = 400L
private const val VIDEO_SIZE_WATCH_TIMEOUT_MS = 10000L
private const val CAST_URL_POLL_MS = 250L
private const val CAST_URL_WAIT_ATTEMPTS = 20

class DetailActivity : BaseActivity(), PageHost {

    private val vm: DetailViewModel by lazy {
        ViewModelProvider(this)[DetailViewModel::class.java]
    }

    var playContainer: PlayContainer? by mutableStateOf<PlayContainer?>(null)
        private set
    private var fullScreen = false
    private var pendingEpisodeSync = false
    private var videoSizeWatchArmed = false
    private var castWaitAttempts = 0

    private val videoSizeTimeoutRunnable = Runnable {
        if (!videoSizeWatchArmed) return@Runnable
        LOG.i("echo-player detail size timeout, keep portrait placeholder")
        videoSizeWatchArmed = false
        playContainer?.setVideoSizeReadyListener(null)
    }

    private val castWaitRunnable = object : Runnable {
        override fun run() {
            val container = playContainer ?: return
            if (container.hasCastUrl()) {
                container.endCastPrepare()
                container.showCast()
                return
            }
            if (castWaitAttempts >= CAST_URL_WAIT_ATTEMPTS) {
                container.endCastPrepare()
                container.showCast()
                return
            }
            castWaitAttempts += 1
            window.decorView.postDelayed(this, CAST_URL_POLL_MS)
        }
    }

    private val localSubtitlePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) playContainer?.onLocalSubtitlePicked(uri)
    }

    override fun launchLocalSubtitlePicker() {
        try {
            localSubtitlePicker.launch(arrayOf("*/*"))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_file_picker_unavailable), Toast.LENGTH_SHORT).show()
        }
    }

    override fun shouldRefreshAutoSize(): Boolean = true

    override fun hideSysBar() {
        if (fullScreen) super.hideSysBar()
    }

    override fun keepStatusBarHidden(): Boolean = fullScreen

    private fun applyStatusBarAppearance() {
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = !AppThemeState.isDark(systemDark)
        }
    }

    override fun init() {
        enableTransparentEdgeToEdge()
        applyStatusBarAppearance()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val container = playContainer
                if (vm.enteringFullscreen.value) {
                    vm.cancelEntrySlide()
                    container?.stopForExitFullscreen()
                    return
                }
                if (vm.exitingFullscreen.value) return
                if (fullScreen) {
                    if (container != null && container.onBackPressed()) return
                    if (DetailPlaybackPolicy.plan(DetailPlaybackEntry.ExitFullscreen).stopPlayback) {
                        container?.stopForExitFullscreen()
                    }
                    vm.onFullScreenToggleRequested(false, playbackFacts())
                } else {
                    if (vm.backToPreviousTarget()) {
                        pendingEpisodeSync = false
                        return
                    }
                    container?.setPlayTitle(false)
                    container?.setExitingPreview(true)
                    finish()
                }
            }
        })
        vm.initFromIntent(intent)
        findViewById<androidx.compose.ui.platform.ComposeView>(R.id.compose_view).setContent {
            AVBoxTheme(manageStatusBarIcons = false) {
                SheetHostScaffold {
                    DetailScreen(activity = this, vm = vm)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingEpisodeSync = false
        vm.onFullScreenToggleRequested(false, playbackFacts())
        vm.pushTargetFromIntent(intent)
    }

    fun ensurePlayContainer(): PlayContainer {
        if (playContainer == null) {
            playContainer = PlayContainer(this).also {
                it.setPageHost(this)
                it.setPreviewMode(true)
                it.setOnQualitySelectedListener(vm::onQualitySelectionAccepted)
            }
        }
        return playContainer!!
    }

    fun playbackFacts(): DetailPlaybackFacts = DetailPlaybackFacts(
        landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
    )

    private fun releasePlayContainer() {
        playContainer?.hostDestroy()
        playContainer = null
    }

    override fun context(): Context = this

    override fun isPageAlive(): Boolean = !isFinishing && !isDestroyed

    override fun runOnUi(action: Runnable) {
        if (isPageAlive()) runOnUiThread(action)
    }

    override fun toast(text: CharSequence) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    override fun requestNotificationPermission() {
        PermissionHelper.requestNotificationIfNeeded(this)
    }

    override fun onPlaybackLinesExhausted(): Boolean = startDetailFallbackAfterLinesExhausted()

    override fun showEpisodeSheet() {
        if (vm.vodInfo != null) vm.showEpisodeSheet()
    }

    fun playCurrent() {
        val container = ensurePlayContainer()
        cancelCastWait()
        container.endCastPrepare()
        container.clearCastAbort()
        val session = vm.preparePlaySession()
        if (session == null) {
            container.clearSourceSwitchTip()
            return
        }
        container.setData(session)
    }

    fun ensurePlaying(): PlayContainer {
        val container = ensurePlayContainer()
        if (!engineOwnsDetailContent()) {
            playCurrent()
        } else {
            container.ensurePlaybackActive()
        }
        return container
    }

    /**
     * 引擎里已经装着内容、但装的是别的影片时（典型场景：在影片 A 的音乐页返回首页，
     * 再打开全新影片 B），不能直接复用，否则音乐页拿到的是 A 的海报与内容。
     */
    private fun engineOwnsDetailContent(): Boolean {
        val playing = PlaybackService.peek()?.controller()?.vod() ?: return false
        val info = vm.vodInfo ?: return false
        return contentMatches(playing, info)
    }

    private fun contentMatches(playing: VodInfo, info: VodInfo): Boolean {
        if (playing.id != info.id) return false
        // 首页复用同一播放器实例时 controller 的 sourceKey 可能取自上次会话，
        // 因此只在本页确实用过该源时才要求源一致，避免把同一影片误判成新内容。
        val expectedSource = vm.firstsourceKey
        return expectedSource.isEmpty()
            || playing.sourceKey == expectedSource
            || vm.sourceKey == expectedSource
    }

    fun openCast() {
        val container = ensurePlayContainer()
        cancelCastWait()
        container.beginCastPrepare()
        val session = vm.preparePlaySession()
        if (session == null) {
            container.endCastPrepare()
            Toast.makeText(this, getString(R.string.toast_no_cast_url), Toast.LENGTH_SHORT).show()
            return
        }
        container.setData(session)
        window.decorView.postDelayed(castWaitRunnable, CAST_URL_POLL_MS)
    }

    private fun cancelCastWait() {
        castWaitAttempts = 0
        window.decorView.removeCallbacks(castWaitRunnable)
    }

    fun musicPlaybackDetected(): Boolean {
        if (!MusicSettings.autoOpenPage()) return false
        val container = playContainer ?: return false
        val engine = PlaybackService.peek() ?: return false
        if (engine.isReleased() || engine.attachedPage() !== container) return false
        val state = engine.player().playState
        val accepted = state == PlayState.PREPARING ||
            state == PlayState.PREPARED ||
            state == PlayState.BUFFERING ||
            state == PlayState.BUFFERED ||
            state == PlayState.PLAYING
        val matched = accepted && isAudioContent()
        LOG.i("echo-music auto-watch: state=$state accepted=$accepted matched=$matched")
        return matched
    }

    fun isAudioContent(): Boolean {
        val controller = PlaybackService.peek()?.controller() ?: return false
        val url = controller.webPlayUrl() ?: return false
        val byUrl = PlaybackController.looksLikeAudioUrl(url)
        val byTrack = controller.isAudioOnlyContent()
        LOG.i(
            "echo-music audio-content: byUrl=$byUrl audioOnlyContent=$byTrack"
                + " url=" + url.substringBefore('?'),
        )
        return byUrl || byTrack
    }

    fun openMusicPlayer() {
        if (vm.vodInfo == null) {
            Toast.makeText(this, getString(R.string.detail_content_not_ready), Toast.LENGTH_SHORT).show()
            return
        }
        // 先按纯音频启动，避免详情页这一帧就把视频解码器建起来（交接后无法回收）。
        prepareAudioOnlyForMusicPage()
        ensurePlaying()
        if (!handOffToMusicPlayer()) {
            Toast.makeText(this, getString(R.string.detail_no_playable_content), Toast.LENGTH_SHORT).show()
        }
    }

    private fun prepareAudioOnlyForMusicPage() {
        PlaybackService.peek()?.controller()?.setMusicAudioOnly(true)
    }

    private fun restoreVideoAfterMusicPage() {
        PlaybackService.peek()?.controller()?.setMusicAudioOnly(false)
    }

    fun handOffToMusicPlayer(): Boolean {
        val container = playContainer ?: return false
        val playing = PlaybackService.peek()?.controller()?.vod()
        if (playing == null) {
            restoreVideoAfterMusicPage()
            return false
        }
        // 引擎里若还装着别的影片，先切到本页内容，避免音乐页显示上一部影片。
        val info = vm.vodInfo
        if (info != null && !contentMatches(playing, info)) {
            LOG.i("echo-music handoff switch content: " + playing.id + " -> " + info.id)
            restoreVideoAfterMusicPage()
            playCurrent()
        }
        prepareAudioOnlyForMusicPage()
        val keepDetailPage = !isAudioContent()
        container.setExitingPreview(true)
        container.handOverToNextPage()
        MusicPlayerActivity.start(this, vm.firstsourceKey)
        if (keepDetailPage) pendingEpisodeSync = true else finish()
        return true
    }

    private fun syncEpisodeAfterMusicPage() {
        if (!pendingEpisodeSync) return
        pendingEpisodeSync = false
        val playing = PlaybackService.peek()?.controller()?.vod() ?: return
        val info = vm.vodInfo ?: return
        if (playing.id != info.id) return
        if (playing.playFlag == info.playFlag && playing.playIndex == info.playIndex) return
        info.playFlag = playing.playFlag
        info.playIndex = playing.playIndex
        vm.bumpRevision()
    }

    fun applyFullscreen(full: Boolean) {
        playContainer?.setAutoSwitchLineEnabled(!full)
        if (fullScreen == full) return
        fullScreen = full
        requestedOrientation = orientationPolicyValue()
        if (full) {
            armVideoSizeWatch()
            hideSysBar()
        } else {
            videoSizeWatchArmed = false
            playContainer?.setVideoSizeReadyListener(null)
            window.decorView.removeCallbacks(videoSizeTimeoutRunnable)
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.show(WindowInsetsCompat.Type.navigationBars())
            applyHideStatusBarPref()
            applyStatusBarAppearance()
            window.decorView.postDelayed({
                if (!isFinishing && !isDestroyed) applyStatusBarAppearance()
            }, SYSBAR_APPEARANCE_REASSERT_DELAY_MS)
        }
        syncFullBoxSideEffects()
    }

    private fun armVideoSizeWatch() {
        val container = playContainer ?: return
        if (container.hasVideoSize()) {
            applyVideoOrientation(container.isPortraitVideo())
            return
        }
        if (videoSizeWatchArmed) return
        videoSizeWatchArmed = true
        container.setVideoSizeReadyListener { portraitVideo -> onVideoSizeReady(portraitVideo) }
        window.decorView.postDelayed(videoSizeTimeoutRunnable, VIDEO_SIZE_WATCH_TIMEOUT_MS)
    }

    private fun onVideoSizeReady(portraitVideo: Boolean) {
        if (!videoSizeWatchArmed) return
        videoSizeWatchArmed = false
        window.decorView.removeCallbacks(videoSizeTimeoutRunnable)
        playContainer?.setVideoSizeReadyListener(null)
        applyVideoOrientation(portraitVideo)
    }

    private fun applyVideoOrientation(portraitVideo: Boolean) {
        if (!fullScreen || portraitVideo) return
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        settleRotationAfterExit()
    }

    fun settleRotationAfterExit() {
        if (!vm.rotating.value) return
        vm.rotating.value = false
        syncFullBoxSideEffects()
    }

    fun setPlayerTouchBlocked(blocked: Boolean) {
        playContainer?.setTouchBlocked(blocked)
    }

    fun isFullBox(): Boolean {
        val landNow = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return DetailFullScreenFrame.playerFullScreen(vm.rotating.value, fullScreen, landNow)
    }

    private fun syncFullBoxSideEffects() {
        playContainer?.setPreviewMode(!isFullBox())
    }

    fun startDetailFallbackAfterLinesExhausted(): Boolean = vm.startFallbackAfterLinesExhausted()

    override fun onResume() {
        super.onResume()
        applyStatusBarAppearance()
        playContainer?.hostResume()
        syncEpisodeAfterMusicPage()
        val container = playContainer ?: return
        vm.applyPlaybackEntry(
            DetailPlaybackEntry.MusicReturn,
            resumable = container.hasClaimedPlayback() && !container.ownsEngineContent(),
        )
    }

    override fun onPause() {
        playContainer?.hostPause()
        super.onPause()
    }

    override fun onDestroy() {
        window.decorView.removeCallbacks(videoSizeTimeoutRunnable)
        window.decorView.removeCallbacks(castWaitRunnable)
        releasePlayContainer()
        vm.destroyEngine()
        super.onDestroy()
    }
}
