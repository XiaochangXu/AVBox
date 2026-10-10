package com.github.tvbox.osc.player.engine

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import com.github.tvbox.osc.player.effect.ReplayableCacheVideoRenderer
import com.github.tvbox.osc.util.LOG
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class EngineRenderersFactory(
    context: Context,
    private val subtitleDelayUsProvider: () -> Long,
    private val videoRendererSink: MutableList<Renderer>,
    private val videoRendererIndices: MutableList<Int>,
    private val dynamicScheduling: Boolean,
) : DefaultRenderersFactory(context) {

    var audioRenderer: Renderer? = null
        private set

    companion object {

        private val VIDEO_CODEC_SELECTOR = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val preferSoft = CodecPreferences.isPreferSoftwareDecode()
            val infos: List<MediaCodecInfo> =
                (if (preferSoft) MediaCodecSelector.PREFER_SOFTWARE else MediaCodecSelector.DEFAULT)
                    .getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
            LOG.i(
                "echo-exo-selector: mime=$mimeType preferSoft=$preferSoft count=${infos.size} " +
                    "first=${if (infos.isEmpty()) "none" else infos[0].name}",
            )
            infos
        }
    }

    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>,
    ) {
        val firstRendererIndex = out.size
        super.buildVideoRenderers(
            context,
            extensionRendererMode,
            VIDEO_CODEC_SELECTOR,
            enableDecoderFallback,
            eventHandler,
            eventListener,
            allowedVideoJoiningTimeMs,
            out,
        )
        replaceWithReplayableRenderer(
            context,
            VIDEO_CODEC_SELECTOR,
            enableDecoderFallback,
            eventHandler,
            eventListener,
            allowedVideoJoiningTimeMs,
            firstRendererIndex,
            out,
        )
        for (i in firstRendererIndex until out.size) {
            videoRendererIndices.add(i)
            videoRendererSink.add(out[i])
        }
    }

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>,
    ) {
        val firstRendererIndex = out.size
        super.buildAudioRenderers(
            context,
            extensionRendererMode,
            mediaCodecSelector,
            enableDecoderFallback,
            audioSink,
            eventHandler,
            eventListener,
            out,
        )
        for (i in firstRendererIndex until out.size) {
            val renderer = out[i]
            if (renderer is MediaCodecAudioRenderer) {
                audioRenderer = renderer
                LOG.i("echo-player-audio-renderer: bound ${renderer.javaClass.simpleName}")
                return
            }
        }
        if (firstRendererIndex < out.size) {
            audioRenderer = out[firstRendererIndex]
            LOG.i(
                "echo-player-audio-renderer: not media codec, using" +
                    " ${out[firstRendererIndex].javaClass.simpleName}",
            )
        }
    }

    override fun buildTextRenderers(        context: Context,
        output: TextOutput,
        outputLooper: Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>,
    ) {
        val firstRendererIndex = out.size
        super.buildTextRenderers(context, output, outputLooper, extensionRendererMode, out)
        for (i in firstRendererIndex until out.size) {
            out[i] = Proxy.newProxyInstance(
                Renderer::class.java.classLoader,
                arrayOf<Class<*>>(Renderer::class.java),
                SubtitleOffsetRendererHandler(out[i], subtitleDelayUsProvider),
            ) as Renderer
        }
    }

    private fun replaceWithReplayableRenderer(
        context: Context,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        firstRendererIndex: Int,
        out: ArrayList<Renderer>,
    ) {
        for (i in firstRendererIndex until out.size) {
            val renderer = out[i]
            if (renderer !is MediaCodecVideoRenderer || renderer is ReplayableCacheVideoRenderer) {
                continue
            }
            val lateThresholdToDropDecoderInputUs =
                MediaCodecVideoRenderer.DEFAULT_LATE_THRESHOLD_TO_DROP_DECODER_INPUT_US
            var builder = MediaCodecVideoRenderer.Builder(context)
                .setCodecAdapterFactory(codecAdapterFactory)
                .setMediaCodecSelector(mediaCodecSelector)
                .setAllowedJoiningTimeMs(allowedVideoJoiningTimeMs)
                .setEnableDecoderFallback(enableDecoderFallback)
                .setEventHandler(eventHandler)
                .setEventListener(eventListener)
                .setMaxDroppedFramesToNotify(DefaultRenderersFactory.MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY)
                .experimentalSetParseAv1SampleDependencies(true)
                .experimentalSetLateThresholdToDropDecoderInputUs(lateThresholdToDropDecoderInputUs)
                .setEarlySchedulingThresholdUs(MediaCodecVideoRenderer.DEFAULT_EARLY_SCHEDULING_THRESHOLD_US)
                .setEnableDurationToProgressUs(dynamicScheduling)
            if (Build.VERSION.SDK_INT >= 34) {
                builder = builder.experimentalSetEnableMediaCodecBufferDecodeOnlyFlag(false)
            }
            out[i] = ReplayableCacheVideoRenderer(builder, lateThresholdToDropDecoderInputUs)
            LOG.i(
                "echo-exo-video-renderer: replayable cache on, usesExoSelector=true " +
                    "preferSoft=${CodecPreferences.isPreferSoftwareDecode()}",
            )
            return
        }
        LOG.i("echo-exo-video-renderer: media codec renderer not found, redraw disabled")
    }

    private class SubtitleOffsetRendererHandler(
        private val renderer: Renderer,
        private val delayUsProvider: () -> Long,
    ) : InvocationHandler {

        @Suppress("UNCHECKED_CAST")
        override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any? {
            var invokeArgs: Array<out Any>? = args
            if (method.name == "render" && args != null && args.isNotEmpty() && args[0] is Long) {
                val cloned = (args as Array<Any>).clone()
                cloned[0] = maxOf(0L, (args[0] as Long) - delayUsProvider())
                invokeArgs = cloned
            }
            try {
                return if (invokeArgs == null) method.invoke(renderer) else method.invoke(renderer, *invokeArgs)
            } catch (e: InvocationTargetException) {
                throw e.cause!!
            }
        }
    }
}
