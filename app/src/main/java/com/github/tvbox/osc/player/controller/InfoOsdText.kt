package com.github.tvbox.osc.player.controller

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.webkit.WebView
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.github.tvbox.osc.R
import com.github.tvbox.osc.player.AppPlayerView
import com.github.tvbox.osc.player.ExoPlayer
import com.github.tvbox.osc.player.PlayerDecodeKind
import com.github.tvbox.osc.player.PlayerHelper
import com.github.tvbox.osc.player.engine.AudioDecoderLookup
import com.github.tvbox.osc.player.state.PlayState
import com.github.tvbox.osc.player.state.PlayerUiState
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.KV
import com.github.tvbox.osc.util.LOG
import java.util.Locale

internal object InfoOsdText {

    fun refreshInfoOsd(
        context: Context,
        state: PlayerUiState,
        videoView: AppPlayerView?,
        activity: Activity?,
        speed: Long,
    ) {
        val view = videoView
        val exo = view?.mediaPlayer as? ExoPlayer
        val video = exo?.selectedVideoFormat
        val left = ArrayList<String>()
        val right = ArrayList<String>()

        left.add(context.getString(R.string.osd_video) + " " + videoText(video, exo))
        left.add(context.getString(R.string.osd_decoder) + " " + (exo?.videoDecoderName()?.takeIf { it.isNotEmpty() } ?: "-"))
        left.add(context.getString(R.string.osd_audio) + " " + audioText(context, exo))

        exo?.sampleFrameRate()
        val throughput = runCatching {
            DefaultBandwidthMeter.getSingletonInstance(context).bitrateEstimate
        }.getOrDefault(0L)
        left.add(
            context.getString(R.string.osd_network) + " " + PlayerHelper.getDisplaySpeed(speed, true)
                + " · " + bitrateText(throughput) + marginText(throughput, video)
        )
        left.add(context.getString(R.string.osd_playback) + " " + playbackText(context, state, exo))
        val footer = context.getString(R.string.osd_config) + " " + configText(context, view, exo)
        left.add(
            context.getString(R.string.osd_conclusion) + " "
                + context.getString(if (state.playState == PlayState.ERROR) R.string.osd_abnormal else R.string.osd_normal)
        )

        right.add(
            context.getString(R.string.osd_device) + " " + Build.MODEL + " / " + Build.DEVICE + " / "
                + (Build.SUPPORTED_ABIS.firstOrNull() ?: "-")
        )
        right.add(context.getString(R.string.osd_system) + " Android " + Build.VERSION.RELEASE + " / SDK " + Build.VERSION.SDK_INT)
        right.add(context.getString(R.string.osd_chip) + " " + chipText())
        right.add(context.getString(R.string.osd_screen) + " " + screenText(context, activity))
        right.add("WebView " + webViewText())
        right.add(context.getString(R.string.osd_network_env) + " " + networkEnvText(context))

        state.infoOsdLeft = left
        state.infoOsdRight = right
        state.infoOsdFooter = footer
    }

    private fun videoText(format: Format?, exo: ExoPlayer?): String {
        if (format == null) return "-"
        val parts = ArrayList<String>()
        parts.add(videoCodecName(format))
        if (format.width > 0 && format.height > 0) parts.add(format.width.toString() + "x" + format.height)
        val fps = frameRateText(format, exo)
        if (fps.isNotEmpty()) parts.add(fps)
        val bitrate = bitrateText(format.bitrate.toLong())
        if (bitrate.isNotEmpty()) parts.add(bitrate)
        val codecs = format.codecs
        if (!codecs.isNullOrEmpty()) parts.add(codecs)
        return parts.joinToString(" · ")
    }

    private fun frameRateText(format: Format, exo: ExoPlayer?): String {
        if (format.frameRate > 0f) return format.frameRate.toInt().toString() + "fps"
        val measured = exo?.measuredFrameRate() ?: 0f
        if (measured <= 0f) return ""
        return String.format(Locale.US, "%.1ffps", measured)
    }

    private fun videoCodecName(format: Format): String = when (format.sampleMimeType) {
        MimeTypes.VIDEO_H264 -> "H.264"
        MimeTypes.VIDEO_H265 -> "H.265"
        MimeTypes.VIDEO_AV1 -> "AV1"
        MimeTypes.VIDEO_VP9 -> "VP9"
        MimeTypes.VIDEO_MP4V -> "MPEG-4"
        else -> format.sampleMimeType?.substringAfter('/')?.uppercase(Locale.US) ?: "-"
    }

    private fun audioText(context: Context, exo: ExoPlayer?): String {
        val format = exo?.selectedAudioFormat
        if (format == null) return "-"
        val parts = ArrayList<String>()
        val codecs = format.codecs
        parts.add(if (!codecs.isNullOrEmpty()) codecs else format.sampleMimeType?.substringAfter('/')?.uppercase(Locale.US) ?: "-")
        val live = exo.audioCodecChoice()
        if (live != null) {
            parts.add(decodeKindText(context, PlayerHelper.decodeKindOf(live.name, live.hardwareAccelerated, live.softwareOnly)))
            parts.add(live.name)
        } else {
            val predicted = AudioDecoderLookup.choiceFor(format)
            if (predicted != null) {
                parts.add(decodeKindText(context, PlayerHelper.decodeKindOf(predicted.name, predicted.hardwareAccelerated, predicted.softwareOnly)))
                parts.add(predicted.name)
            }
        }
        if (format.channelCount > 0) parts.add(format.channelCount.toString() + ".0")
        if (format.sampleRate > 0) {
            val khz = format.sampleRate / 1000f
            parts.add((if (khz % 1f == 0f) khz.toInt().toString() else String.format(Locale.US, "%.1f", khz)) + "kHz")
        }
        logAudioCodec(format, exo)
        return parts.joinToString(" · ")
    }

    private fun decodeKindText(context: Context, kind: PlayerDecodeKind): String = when (kind) {
        PlayerDecodeKind.HARDWARE -> context.getString(R.string.player_decode_hard)
        PlayerDecodeKind.SOFTWARE -> context.getString(R.string.player_decode_soft)
        PlayerDecodeKind.UNKNOWN -> "-"
    }

    private var lastLoggedAudioKey: String? = null

    private fun logAudioCodec(format: Format, exo: ExoPlayer?) {
        val live = exo?.audioCodecChoice()
        val predicted = if (live == null) AudioDecoderLookup.choiceFor(format) else null
        val choice = live ?: predicted ?: return
        val source = if (live != null) "live" else "predicted"
        val key = source + "|" + choice.name + "|" + choice.hardwareAccelerated + "|" + choice.softwareOnly
        if (key == lastLoggedAudioKey) return
        lastLoggedAudioKey = key
        LOG.i(
            "echo-player-audio-codec: source=$source mime=${format.sampleMimeType} name=${choice.name}" +
                " hw=${choice.hardwareAccelerated} swOnly=${choice.softwareOnly}" +
                " kind=${PlayerHelper.decodeKindOf(choice.name, choice.hardwareAccelerated, choice.softwareOnly)}" +
                " renderer=${exo?.audioRendererName() ?: "-"}",
        )
    }

    private fun bitrateText(bps: Long): String {
        if (bps <= 0) return ""
        return String.format(Locale.US, "%.1fMbps", bps / 1000000f)
    }

    private fun marginText(throughput: Long, format: Format?): String {
        val bitrate = format?.bitrate ?: 0
        if (throughput <= 0 || bitrate <= 0) return ""
        return " · x" + String.format(Locale.US, "%.2f", throughput.toFloat() / bitrate)
    }

    private fun playbackText(context: Context, state: PlayerUiState, exo: ExoPlayer?): String {
        val parts = ArrayList<String>()
        parts.add(context.getString(playStateRes(state)))
        parts.add(timeText(state.position) + " / " + timeText(state.duration))
        parts.add(context.getString(R.string.osd_dropped_frames, exo?.droppedFrames() ?: 0L))
        parts.add(context.getString(R.string.osd_rebuffer, exo?.rebufferCount() ?: 0))
        return parts.joinToString(" · ")
    }

    private fun playStateRes(state: PlayerUiState): Int = when (state.playState) {
        PlayState.BUFFERING -> R.string.osd_state_buffering
        PlayState.PLAYING -> R.string.osd_state_playing
        PlayState.PAUSED -> R.string.osd_state_paused
        PlayState.COMPLETED -> R.string.osd_state_ended
        PlayState.ERROR -> R.string.osd_abnormal
        else -> R.string.osd_state_ready
    }

    private fun timeText(millis: Int): String {
        if (millis <= 0) return "00:00"
        val seconds = millis / 1000
        return String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
    }

    private fun decodeText(context: Context, exo: ExoPlayer?): String {
        val name = exo?.videoDecoderName()?.takeIf { it.isNotEmpty() } ?: return "-"
        val software = name.startsWith("c2.android.") || name.startsWith("OMX.google.") ||
            name.startsWith("OMX.ffmpeg.") || name.contains(".sw.")
        return context.getString(if (software) R.string.player_decode_soft else R.string.player_decode_hard)
    }

    private fun configText(context: Context, videoView: AppPlayerView?, exo: ExoPlayer?): String {
        val parts = ArrayList<String>()
        parts.add(context.getString(R.string.player_exo))
        parts.add(decodeText(context, exo))
        parts.add(if (videoView?.renderIsSurface == true) "Surface" else "Texture")
        parts.add(context.getString(R.string.osd_tunnel) + " " + onOffText(context, exo?.isTunnelingEnabled == true))
        parts.add(context.getString(R.string.osd_frame_rate_match) + " " + onOffText(context, false))
        parts.add(context.getString(R.string.osd_preload) + " " + onOffText(context, KV.get(HawkConfig.PRELOAD_NEXT_EPISODE, false) == true))
        parts.add(context.getString(R.string.osd_cache) + " " + onOffText(context, KV.get(HawkConfig.PLAY_CACHE, false) == true))
        return parts.joinToString(" · ")
    }

    private fun onOffText(context: Context, on: Boolean): String = context.getString(if (on) R.string.common_on else R.string.common_off)

    private fun chipText(): String {
        val parts = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            Build.SOC_MODEL?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        }
        Build.HARDWARE?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        Build.BOARD?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        return if (parts.isEmpty()) "-" else parts.joinToString(" / ")
    }

    @Suppress("DEPRECATION")
    private fun screenText(context: Context, activity: Activity?): String {
        val display = activity?.windowManager?.defaultDisplay ?: return "-"
        val parts = ArrayList<String>()
        val mode = display?.mode
        if (mode != null) {
            parts.add(mode.physicalWidth.toString() + "x" + mode.physicalHeight)
        } else {
            parts.add(context.resources.displayMetrics.widthPixels.toString() + "x" + context.resources.displayMetrics.heightPixels)
        }
        parts.add(String.format(Locale.US, "%.0fHz", display.refreshRate))
        return parts.joinToString(" · ")
    }

    private fun webViewText(): String {
        if (Build.VERSION.SDK_INT < 26) return "-"
        return WebView.getCurrentWebViewPackage()?.versionName ?: "-"
    }

    private fun networkEnvText(context: Context): String {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return "-"
        val network = manager.activeNetwork ?: return "offline"
        val capabilities = manager.getNetworkCapabilities(network) ?: return "offline"
        val type = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Other"
        }
        val validated = if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) "validated" else "unvalidated"
        return type + " / " + validated + (if (manager.isActiveNetworkMetered) " metered" else " unmetered")
    }

    fun readBattery(context: Context, state: PlayerUiState) {
        runCatching {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                ?: return
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            state.batteryPercent = if (level in 0..100) level else -1
            val status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
            state.batteryCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
        }
    }
}
