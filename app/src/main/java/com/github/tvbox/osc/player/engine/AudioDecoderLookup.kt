package com.github.tvbox.osc.player.engine

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import com.github.tvbox.osc.player.PlayerDecodeKind
import com.github.tvbox.osc.player.PlayerHelper

internal object AudioDecoderLookup {

    private val codecList: MediaCodecList? by lazy {
        runCatching { MediaCodecList(MediaCodecList.REGULAR_CODECS) }.getOrNull()
    }

    fun choiceFor(format: Format?): AudioCodecChoice? {
        val mime = format?.sampleMimeType?.takeIf { it.isNotEmpty() } ?: return null
        if (!MimeTypes.isAudio(mime)) return null
        val list = codecList ?: return null
        val mediaFormat = MediaFormat.createAudioFormat(mime, format.sampleRate, format.channelCount)
        val candidates = list.codecInfos.filter { it.isAudioDecoderFor(mediaFormat) }
        if (candidates.isEmpty()) return null
        val info = candidates.firstOrNull { it.isHardwareAccelerated && !it.isSoftwareOnly } ?: candidates.first()
        return AudioCodecChoice(
            name = info.name,
            hardwareAccelerated = info.isHardwareAccelerated,
            softwareOnly = info.isSoftwareOnly,
        )
    }

    fun decodeKind(format: Format?): Pair<String, PlayerDecodeKind>? {
        val choice = choiceFor(format) ?: return null
        return choice.name to PlayerHelper.decodeKindOf(
            choice.name,
            choice.hardwareAccelerated,
            choice.softwareOnly,
        )
    }

    private fun MediaCodecInfo.isAudioDecoderFor(format: MediaFormat): Boolean {
        if (isEncoder) return false
        val caps = runCatching { getCapabilitiesForType(format.getString(MediaFormat.KEY_MIME)) }.getOrNull()
            ?: return false
        if (caps.colorFormats.isNotEmpty()) return false
        return runCatching { caps.isFormatSupported(format) }.getOrDefault(false)
    }
}
