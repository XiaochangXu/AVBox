package com.github.tvbox.osc.ui.player

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Toast
import com.github.tvbox.osc.player.KernelDecision
import com.github.tvbox.osc.player.KernelPlayer
import com.github.tvbox.osc.player.KernelReusePolicy
import com.github.tvbox.osc.player.PreloadCoordinator
import com.github.tvbox.osc.player.PlaybackHostApi
import com.github.tvbox.osc.player.PlaybackViewBridge
import com.github.tvbox.osc.player.PlayerHelper
import com.github.tvbox.osc.player.host.EngineTextureRenderViewFactory
import com.github.tvbox.osc.player.state.PlayState
import com.github.tvbox.osc.util.LOG
import com.github.tvbox.osc.util.PermissionHelper
import org.json.JSONObject
import java.util.HashMap

class PlayContainerViewBridge(private val container: PlayContainer) : PlaybackViewBridge {

    override fun isPageAlive(): Boolean = container.isAttached()

    override fun runOnUi(action: Runnable) {
        val activity = container.mActivity
        if (container.isAttached() && activity != null) activity.runOnUiThread(action)
    }

    override fun toast(text: CharSequence) {
        Toast.makeText(container.context, text, Toast.LENGTH_SHORT).show()
    }

    override fun showTip(msg: String, loading: Boolean, error: Boolean) {
        container.setTip(msg, loading, error)
    }

    override fun hideTipOnUiThread() {
        container.hideTipOnUiThread()
    }

    override fun playState(): PlayState = container.mVideoView?.playState ?: PlayState.IDLE

    override fun currentPosition(): Long = container.mVideoView?.currentPosition ?: 0L

    override fun isPlaying(): Boolean = container.mVideoView?.isPlaying == true

    override fun duration(): Long = container.mVideoView?.duration ?: 0L

    override fun mediaPlayer(): KernelPlayer? = container.mVideoView?.mediaPlayer

    override fun isKernelErrored(): Boolean = container.mVideoView?.isKernelErrored() == true

    override fun currentUrl(): String? = container.mVideoView?.currentUrl

    override fun onContentUrlSet(url: String?) {
        container.mController?.onContentUrlSet(url)
    }

    override fun context(): Context = container.context

    override fun playbackHost(): PlaybackHostApi = container

    override fun requestNotificationPermission() {
        val host = container.mPageHost
        val activity = container.mActivity
        if (host != null) {
            host.requestNotificationPermission()
        } else if (activity != null) {
            PermissionHelper.requestNotificationIfNeeded(activity)
        }
    }

    override fun switchRenderToTexture() {
        val view = container.mVideoView
        if (view != null && view.renderIsSurface) view.switchRenderToTexture()
    }

    override fun ensureRenderViewMatchesConfig() {
        container.mVideoView?.ensureRenderViewMatchesConfig()
    }

    override fun setAudioOnlyMode(audioOnly: Boolean) {
        container.setAudioOnlyMode(audioOnly)
    }

    override fun isAudioOnlyMode(): Boolean = container.isAudioOnlyMode()

    override fun releasePlayer() {
        container.releasePlayerKernel()
    }

    override fun setTitle(title: String) {
        container.mController?.setTitle(title)
    }

    override fun stopOtherPlayers() {
        container.mController?.stopOther()
    }

    override fun resetDanmu() {
        container.resetDanmuState()
    }

    override fun startDanmuIfReady() {
        container.startDanmuIfReady()
    }

    override fun clearLyric() {
        container.clearLyricView()
    }

    override fun clearArtwork() {
        container.mVideoView?.clearArtwork()
    }

    override fun clearVideoFrame() {
        container.mVideoView?.clearVideoFrame()
    }

    override fun setSubtitleViewVisible(visible: Boolean) {
        val controller = container.mController ?: return
        controller.getSubtitleView().visibility = if (visible) View.VISIBLE else View.GONE
    }

    override fun onNewPlayStarted(sameContent: Boolean) {
        container.mExitingPreview = false
        container.mController?.onNewPlayStarted(sameContent)
    }

    override fun applyPlayerConfigToView(forceKernel: Int) {
        val view = container.mVideoView ?: return
        PlayerHelper.updateCfg(view, container.scheduler.playerCfg()!!)
    }

    override fun useTextureRenderForAudio() {
        container.mVideoView?.setRenderViewFactory(EngineTextureRenderViewFactory.create())
    }

    override fun playExternalPlayer(
        playerType: Int,
        url: String,
        title: String,
        subtitle: String?,
        headers: HashMap<String, String>?,
        progress: Long,
    ): Boolean {
        val activity = container.mActivity ?: return false
        return PlayerHelper.runExternalPlayer(
            playerType,
            activity,
            url,
            title,
            subtitle ?: "",
            headers,
            progress,
        )
    }

    override fun playM3u8(url: String, headers: HashMap<String, String>) {
        container.mController?.playM3u8(url, headers)
    }

    override fun playM3u8(url: String, headers: HashMap<String, String>?, gen: Int) {
        if (!container.scheduler.isParseResultCurrent(gen)) {
            LOG.i("echo-ignore stale m3u8 result")
            return
        }
        playM3u8(url, headers!!)
    }

    override fun startVideoPlayback(
        url: String,
        headers: HashMap<String, String>?,
        forceExoPlayer: Boolean,
    ) {
        val view = container.mVideoView ?: return
        container.mController?.hidePauseRoot()
        if (view.mediaPlayer != null && view.needsRenderRebuild(view.factoryRenderType())) {
            view.requireKernelRebuild()
            LOG.i("echo-render-changed: rebuild kernel on next start")
        }
        if (view.isKernelErrored()) {
            view.requireKernelRebuild()
            LOG.i("echo-kernel-error: rebuild errored kernel on start")
        }
        val rebuildKernel = view.consumeKernelRebuildRequired()
        val kernelPresent = view.mediaPlayer != null
        val reusePlayer =
            KernelReusePolicy.decide(kernelPresent, rebuildKernel, forceExoPlayer, true) == KernelDecision.REUSE
        if (!reusePlayer) container.hideTip()
        val sameContent = reusePlayer && container.scheduler.isSameStartedContent()
        if (!reusePlayer && kernelPresent) {
            container.releasePlayerKernel()
        } else if (sameContent) {
            view.saveCurrentProgress()
        }
        view.setProgressKey(container.scheduler.progressKey())
        view.setTrackMemoryKey(container.trackMemoryKey())
        container.scheduler.markContentStarted()
        if (headers != null) {
            view.setUrl(url, headers)
        } else {
            view.setUrl(url)
        }
        // 记录本轮内容地址：播放器切换完成前仍会返回上一部影片的进度，进度回调靠它判陈旧。
        container.mController?.onContentUrlSet(url)
        container.scheduler.startSwitchLinePlayTimeout()
        if (reusePlayer) {
            val base = container.scheduler.playTimeoutBasePosition()
            view.skipPositionWhenPlay((if (sameContent) view.resumePositionForReplay(base) else base).toInt())
            view.replay(false)
        } else {
            view.start()
        }
        container.mController?.resetSpeed()
    }

    override fun buildPreloadSnapshot(): PreloadCoordinator.Snapshot? = container.buildPreloadSnapshot()

    override fun showPreloadReadyTip() {
        container.showPreloadReady()
    }

    override fun hidePreloadReadyTip() {
        container.hidePreloadReady()
    }

    override fun firstUrlByArray(url: String): String =
        container.mController?.firstUrlByArray(url) ?: url

    override fun setArtwork(url: String) {
        container.mVideoView?.setArtwork(url)
    }

    override fun showParse(show: Boolean) {
        container.mController?.showParse(show)
    }

    override fun checkDanmu(danmaku: String, onFailed: Runnable?) {
        container.checkDanmu(danmaku) { onFailed?.run() }
    }

    override fun encodeUrl(url: String): String =
        container.mController?.encodeUrl(url) ?: url

    override fun evaluateScript(url: String, webView: WebView?) {
        container.mController?.evaluateScript(container.scheduler.sourceBean(), url, webView)
    }

    override fun newSniffWebView(): WebView = container.MyWebView(container.context)

    override fun attachSniffWebView(webView: WebView) {
        val activity = container.mActivity
        if (container.isAttached() && activity != null) {
            activity.addContentView(webView, ViewGroup.LayoutParams(1, 1))
        }
    }

    override fun showErrorWithRetry(err: String, finish: Boolean) {
        container.errorWithRetry(err, finish)
    }

    override fun switchPlayerKernel(): Boolean =
        container.mController != null && container.mController.switchPlayer()

    override fun applyPlayerConfig(cfg: JSONObject) {
        container.mController?.setPlayerConfig(cfg)
    }

    override fun onLinesExhausted(): Boolean {
        val host = container.mPageHost ?: return false
        return host.onPlaybackLinesExhausted()
    }
}
