package com.shilapi.xcertplay.media

import io.github.jaredmdobson.concentus.OpusApplication
import io.github.jaredmdobson.concentus.OpusEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftwareOpusDecoderTest {
    private fun voicePacket(): Pair<ByteArray, Int> {
        val pcm = ShortArray(160) { (((it % 64) - 32) * 300).toShort() }
        val packet = ByteArray(1275)
        val written = OpusEncoder(16000, 1, OpusApplication.OPUS_APPLICATION_VOIP)
            .encode(pcm, 0, pcm.size, packet, 0, packet.size)
        return packet to written
    }

    @Test fun backEndIsNamedForTheCapabilityReport() {
        assertNotNull(SoftwareOpusDecoder.backend)
        val decoder = SoftwareOpusDecoder.create(16000, 1)
        assertNotNull(decoder)
        assertEquals(SoftwareOpusDecoder.backend, decoder!!.backend)
    }

    @Test fun javaBackEndDecodesAnOpusPacketIntoPcm() {
        val decoder = SoftwareOpusDecoder.create(16000, 1) ?: return
        if (decoder.backend != "concentus") return
        val (packet, written) = voicePacket()
        val pcm = ShortArray(160)
        assertEquals(160, decoder.decode(packet, 0, written, pcm))
        assertTrue(pcm.any { it != 0.toShort() })
    }

    @Test fun bufferTooSmallForOneChannelIsRejected() {
        val decoder = SoftwareOpusDecoder.create(16000, 1) ?: return
        val (packet, written) = voicePacket()
        assertTrue(decoder.decode(packet, 0, written, ShortArray(0)) < 0)
    }
}
