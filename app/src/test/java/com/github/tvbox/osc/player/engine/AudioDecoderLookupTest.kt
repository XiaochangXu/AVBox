package com.github.tvbox.osc.player.engine

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.tvbox.osc.testing.TestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestApplication::class)
class AudioDecoderLookupTest {

    private fun audioFormat(mime: String, sampleRate: Int = 44100, channels: Int = 2): Format =
        Format.Builder()
            .setSampleMimeType(mime)
            .setSampleRate(sampleRate)
            .setChannelCount(channels)
            .build()

    @Test
    fun nullFormatHasNoChoice() {
        assertNull(AudioDecoderLookup.choiceFor(null))
        assertNull(AudioDecoderLookup.decodeKind(null))
    }

    @Test
    fun formatWithoutMimeHasNoChoice() {
        assertNull(AudioDecoderLookup.choiceFor(Format.Builder().setSampleRate(44100).build()))
    }

    @Test
    fun videoMimeHasNoAudioChoice() {
        assertNull(AudioDecoderLookup.choiceFor(audioFormat(MimeTypes.VIDEO_H264)))
    }

    @Test
    fun nonAudioMimeHasNoChoice() {
        assertNull(AudioDecoderLookup.choiceFor(audioFormat(MimeTypes.APPLICATION_SUBRIP)))
    }

    @Test
    fun decodeKindMirrorsChoiceName() {
        val format = audioFormat(MimeTypes.AUDIO_AAC)
        assertEquals(AudioDecoderLookup.choiceFor(format)?.name, AudioDecoderLookup.decodeKind(format)?.first)
    }
}
