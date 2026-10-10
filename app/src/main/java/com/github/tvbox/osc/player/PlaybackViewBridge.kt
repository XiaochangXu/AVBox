package com.github.tvbox.osc.player

import android.content.Context
import android.webkit.WebView
import com.github.tvbox.osc.player.state.PlayState
import org.json.JSONObject
import java.util.HashMap

interface PlaybackViewBridge {

    fun isPageAlive(): Boolean

    fun runOnUi(action: Runnable)

    fun context(): Context

    fun playbackHost(): PlaybackHostApi

    fun toast(text: CharSequence)

    fun showTip(msg: String, loading: Boolean, error: Boolean)

    fun hideTipOnUiThread()

    fun showErrorWithRetry(err: String, finish: Boolean)

    fun requestNotificationPermission()

    fun playState(): PlayState

    fun currentPosition(): Long

    fun duration(): Long

    fun isPlaying(): Boolean

    fun mediaPlayer(): KernelPlayer?

    fun isKernelErrored(): Boolean

    /** 已下发给播放器的地址；与 webPlayUrl 不一致说明内容还在切换途中。 */
    fun currentUrl(): String?

    /** 通知控制器本轮内容地址，用于识别切换途中的陈旧进度。 */
    fun onContentUrlSet(url: String?)

    fun releasePlayer()

    fun setTitle(title: String)

    fun stopOtherPlayers()

    fun resetDanmu()

    fun startDanmuIfReady()

    fun clearLyric()

    fun clearArtwork()

    fun clearVideoFrame()

    fun setSubtitleViewVisible(visible: Boolean)

    fun onNewPlayStarted(sameContent: Boolean)

    fun applyPlayerConfigToView(forceKernel: Int)

    fun useTextureRenderForAudio()

    fun switchRenderToTexture()

    fun ensureRenderViewMatchesConfig()

    fun setAudioOnlyMode(audioOnly: Boolean)

    fun isAudioOnlyMode(): Boolean

    fun playExternalPlayer(
        playerType: Int,
        url: String,
        title: String,
        subtitle: String?,
        headers: HashMap<String, String>?,
        progress: Long,
    ): Boolean

    fun playM3u8(url: String, headers: HashMap<String, String>)

    fun playM3u8(url: String, headers: HashMap<String, String>?, gen: Int)

    fun startVideoPlayback(url: String, headers: HashMap<String, String>?, forceExoPlayer: Boolean)

    fun switchPlayerKernel(): Boolean

    fun applyPlayerConfig(cfg: JSONObject)

    fun firstUrlByArray(url: String): String

    fun setArtwork(url: String)

    fun showParse(show: Boolean)

    fun checkDanmu(danmaku: String, onFailed: Runnable?)

    fun encodeUrl(url: String): String

    fun evaluateScript(url: String, webView: WebView?)

    fun newSniffWebView(): WebView?

    fun attachSniffWebView(webView: WebView)

    fun buildPreloadSnapshot(): PreloadCoordinator.Snapshot?

    fun showPreloadReadyTip()

    fun hidePreloadReadyTip()

    fun onLinesExhausted(): Boolean
}
