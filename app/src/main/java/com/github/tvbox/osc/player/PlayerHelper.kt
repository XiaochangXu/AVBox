package com.github.tvbox.osc.player

import android.app.Activity
import android.content.Context

import com.github.tvbox.osc.R
import com.github.tvbox.osc.player.engine.SourcePolicy
import com.github.tvbox.osc.player.host.EngineSurfaceRenderViewFactory
import com.github.tvbox.osc.player.host.EngineTextureRenderViewFactory
import com.github.tvbox.osc.player.host.PlayerRenderViewFactory
import com.github.tvbox.osc.player.thirdparty.RemoteTVBox
import com.github.tvbox.osc.util.AppContextHolder
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.KV
import com.github.tvbox.osc.util.LOG
import com.github.tvbox.osc.util.LanguageManager

import org.json.JSONException
import org.json.JSONObject

import java.text.DecimalFormat

object PlayerHelper {
    @JvmStatic
    fun updateCfg(videoView: MyVideoView?, playerCfg: JSONObject) {
        updateCfg(videoView, playerCfg, -1)
    }

    @JvmStatic
    fun updateCfg(videoView: MyVideoView?, playerCfg: JSONObject, forcePlayerType: Int) {
        var renderType = KV.get(HawkConfig.PLAY_RENDER, 1)
        var exoDecode = KV.get(HawkConfig.EXO_DECODE, "硬解码") // i18n: keep
        var scale = KV.get(HawkConfig.PLAY_SCALE, 0)
        try {
            renderType = playerCfg.getInt("pr")
            scale = playerCfg.getInt("sc")
        } catch (e: JSONException) {
            LOG.e("PlayerHelper", e)
        }
        exoDecode = playerCfg.optString("exo", exoDecode)
        val exoDecodeChanged = applyExoDecode(exoDecode)
        val renderViewFactory: PlayerRenderViewFactory = when (renderType) {
            1 -> EngineSurfaceRenderViewFactory.create()
            else -> EngineTextureRenderViewFactory.create()
        }
        if (videoView != null) {
            if (exoDecodeChanged && videoView.mediaPlayer is ExoPlayer) {
                videoView.requireKernelRebuild()
                LOG.i("echo-exo-decode-changed: rebuild kernel on next start")
            }
            videoView.setRenderViewFactory(renderViewFactory)
            videoView.setScreenScaleType(scale)
        }
    }

    private fun applyExoDecode(exoDecode: String): Boolean {
        val prefer = "软解码" == exoDecode // i18n: keep
        if (ExoPlayer.isPreferSoftwareDecode() == prefer) return false
        ExoPlayer.setPreferSoftwareDecode(prefer)
        return true
    }

    @JvmStatic
    fun isExoDecodeApplied(playerCfg: JSONObject?): Boolean {
        val exoDecode = if (playerCfg == null) null else playerCfg.optString("exo", "硬解码") // i18n: keep
        return isExoDecodeApplied(exoDecode, ExoPlayer.isPreferSoftwareDecode())
    }

    @JvmStatic
    fun isExoDecodeApplied(exoDecode: String?, preferSoftwareDecode: Boolean): Boolean {
        return ("软解码" == exoDecode) == preferSoftwareDecode // i18n: keep
    }

    @JvmStatic
    fun isLocalProxyUrl(url: String?): Boolean = SourcePolicy.isLocalProxyUrl(url)

    @JvmStatic
    fun decodeKindOf(
        codecName: String?,
        hardwareAccelerated: Boolean,
        softwareOnly: Boolean,
    ): PlayerDecodeKind {
        val name = codecName.orEmpty()
        if (name.isEmpty()) return PlayerDecodeKind.UNKNOWN
        if (softwareOnly) return PlayerDecodeKind.SOFTWARE
        if (isSoftwareCodecName(name)) return PlayerDecodeKind.SOFTWARE
        if (hardwareAccelerated) return PlayerDecodeKind.HARDWARE
        return PlayerDecodeKind.UNKNOWN
    }

    private fun isSoftwareCodecName(name: String): Boolean =
        name.startsWith("c2.android.") ||
            name.startsWith("OMX.google.") ||
            name.startsWith("OMX.ffmpeg.") ||
            name.contains(".sw.") ||
            name.endsWith(".sw")

    @JvmStatic
    fun getPlayerName(playType: Int): String {
        return when (playType) {
            13 -> str(R.string.player_nearby_tvbox)
            else -> str(R.string.player_exo)
        }
    }

    private var mPlayersExistInfo: HashMap<Int, Boolean>? = null

    @JvmStatic
    fun invalidatePlayersExistInfo() {
        mPlayersExistInfo = null
    }

    @JvmStatic
    fun getPlayersExistInfo(): HashMap<Int, Boolean> {
        if (mPlayersExistInfo == null) {
            val playersExist = HashMap<Int, Boolean>()
            playersExist[2] = true
            playersExist[13] = RemoteTVBox.getAvalible() != null
            mPlayersExistInfo = playersExist
        }
        return mPlayersExistInfo!!
    }

    @JvmStatic
    fun getPlayerExist(playType: Int): Boolean {
        val playersExistInfo = getPlayersExistInfo()
        if (playersExistInfo.containsKey(playType)) {
            return playersExistInfo[playType]!!
        } else {
            return false
        }
    }

    @JvmStatic
    fun getExistPlayerTypes(): ArrayList<Int> {
        val playersExistInfo = getPlayersExistInfo()
        val existPlayers = ArrayList<Int>()
        for (playerType in playersExistInfo.keys) {
            if (playersExistInfo[playerType]!!) {
                existPlayers.add(playerType)
            }
        }
        return existPlayers
    }

    @JvmStatic
    fun runExternalPlayer(playerType: Int, activity: Activity, url: String, title: String, subtitle: String, headers: HashMap<String, String>?): Boolean {
        return runExternalPlayer(playerType, activity, url, title, subtitle, headers, 0L)
    }

    @JvmStatic
    fun runExternalPlayer(playerType: Int, activity: Activity, url: String, title: String, subtitle: String, headers: HashMap<String, String>?, progress: Long): Boolean {
        var callResult = false
        when (playerType) {
            13 -> {
                callResult = RemoteTVBox.run(activity, url, title, subtitle, headers)
            }
        }
        return callResult
    }

    @JvmStatic
    fun getRenderName(renderType: Int): String {
        return if (renderType == 1) {
            "SurfaceView"
        } else {
            "TextureView"
        }
    }

    @JvmStatic
    fun getScaleName(screenScaleType: Int): String {
        return when (screenScaleType) {
            AppPlayerView.SCREEN_SCALE_16_9 -> "16:9"
            AppPlayerView.SCREEN_SCALE_4_3 -> "4:3"
            AppPlayerView.SCREEN_SCALE_MATCH_PARENT -> str(R.string.player_scale_fill)
            AppPlayerView.SCREEN_SCALE_ORIGINAL -> str(R.string.player_scale_origin)
            AppPlayerView.SCREEN_SCALE_CENTER_CROP -> str(R.string.player_scale_crop)
            else -> str(R.string.common_default)
        }
    }

    private fun str(resId: Int): String {
        val app: Context? = AppContextHolder.context()
        return if (app == null) "" else LanguageManager.localized(app).getString(resId)
    }

    @JvmStatic
    fun getDisplaySpeed(speed: Long, show: Boolean): String {
        return if (speed > 1048576)
            DecimalFormat("#.00").format(speed / 1048576.0) + "MB/s"
        else if (speed > 1024)
            (speed / 1024).toString() + "KB/s"
        else
            if (speed > 0) speed.toString() + "B/s" else (if (show) "0B/s" else "")
    }
}
