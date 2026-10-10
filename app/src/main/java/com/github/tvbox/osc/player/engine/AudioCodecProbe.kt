package com.github.tvbox.osc.player.engine

import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer
import java.lang.reflect.Method

class AudioCodecChoice(
    val name: String,
    val hardwareAccelerated: Boolean,
    val softwareOnly: Boolean,
)

internal object AudioCodecProbe {

    private val getCodecInfoMethod: Method? by lazy {
        runCatching { MediaCodecRenderer::class.java.getDeclaredMethod("getCodecInfo") }
            .onSuccess { it.isAccessible = true }
            .getOrNull()
    }

    fun liveInfo(renderer: Any?): AudioCodecChoice? {
        val audio = renderer as? MediaCodecAudioRenderer ?: return null
        val method = getCodecInfoMethod ?: return null
        val info = runCatching { method.invoke(audio) as? MediaCodecInfo }.getOrNull() ?: return null
        val name = info.name
        if (name.isEmpty()) return null
        return AudioCodecChoice(
            name = name,
            hardwareAccelerated = info.hardwareAccelerated,
            softwareOnly = info.softwareOnly,
        )
    }

    fun rendererClassName(renderer: Any?): String? =
        renderer?.javaClass?.simpleName?.takeIf { it.isNotEmpty() }
}
