package com.github.tvbox.osc.player.controller

import android.webkit.WebView
import androidx.media3.ui.SubtitleView

import com.github.tvbox.osc.bean.SourceBean
import com.github.tvbox.osc.player.MyVideoView
import com.github.tvbox.osc.player.state.PlayerUiState
import com.github.tvbox.osc.subtitle.widget.SimpleSubtitleView
import org.json.JSONObject
import java.util.HashMap

interface PlayerControlApi {

    fun setKernelProvider(view: MyVideoView?) {
    }

    fun getSubtitleView(): SimpleSubtitleView

    fun getLyricView(): SimpleSubtitleView

    fun getExoSubtitleView(): SubtitleView

    fun getUiState(): PlayerUiState

    fun setListener(listener: VodControlListener?) {
    }

    fun setPlayerConfig(playerCfg: JSONObject)

    fun showParse(userJxList: Boolean)

    fun setPreviewMode(previewMode: Boolean)

    fun setTitle(playTitleInfo: String) {
    }

    fun setUrlTitle(playTitleInfo: String) {
    }

    fun setHasDanmu(hasDanmu: Boolean)

    fun setCanChangePosition(canChangePosition: Boolean)

    fun setEnableInNormal(enableInNormal: Boolean)

    fun setGestureEnabled(gestureEnabled: Boolean)

    fun toggleControlBar()

    fun hidePauseRoot()

    fun onNewPlayStarted(sameContent: Boolean)

    /** 内容地址下发到播放器时告知控制器，用于识别切换途中的陈旧进度。 */
    fun onContentUrlSet(url: String?)

    fun setLifecyclePaused(paused: Boolean)

    fun setExitPaused(paused: Boolean)

    fun resetSpeed()

    fun onBackPressed(): Boolean

    fun switchPlayer(): Boolean

    fun stopOther()

    fun playM3u8(url: String?, headers: HashMap<String, String>?)

    fun encodeUrl(url: String?): String

    fun firstUrlByArray(url: String?): String

    fun evaluateScript(sourceBean: SourceBean?, url: String?, view: WebView?)

    fun getWebPlayUrlIfNeeded(webPlayUrl: String?): String
}
