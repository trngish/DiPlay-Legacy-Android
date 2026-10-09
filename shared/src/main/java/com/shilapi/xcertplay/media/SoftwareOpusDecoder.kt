package com.shilapi.xcertplay.media

import android.util.Log
import io.github.jaredmdobson.concentus.OpusApplication
import io.github.jaredmdobson.concentus.OpusDecoder
import io.github.jaredmdobson.concentus.OpusEncoder
import java.io.Closeable

/**
 * Decodes Opus outside MediaCodec, for ROMs that have no Opus codec at all: Android 4.4 ships none,
 * so CarPlay telephony and alert audio would otherwise be dropped without an error.
 *
 * Two back ends, native first. The prebuilt libopus is faster, but whether its C++ unwinding
 * symbols resolve on 4.4 is not provable from a build machine, so Concentus follows as a fallback
 * that needs no native code at all.
 */
class SoftwareOpusDecoder private constructor(
    val channels: Int,
    val backend: String,
    private val handle: Long,
    private val javaDecoder: OpusDecoder?,
) : Closeable {
    private var released = false

    /** Returns decoded samples per channel, or a negative value when this packet produced none. */
    fun decode(packet: ByteArray, offset: Int, length: Int, output: ShortArray): Int {
        if (handle != 0L) return SoftwareOpusNative.decode(handle, packet, offset, length, output)
        val decoder = javaDecoder ?: return NO_DECODER
        // The buffer is sized for the largest frame libopus will emit, so it doubles as the limit.
        val capacity = output.size / channels
        if (capacity < 1 || capacity > MAX_SAMPLES_PER_CHANNEL) return BUFFER_UNUSABLE
        return try {
            decoder.decode(packet, offset, length, output, 0, capacity, false)
        } catch (error: Exception) {
            DECODE_FAILED
        }
    }

    override fun close() {
        if (released) return
        released = true
        if (handle != 0L) SoftwareOpusNative.destroy(handle)
    }

    companion object {
        private const val TAG = "SoftwareOpusDecoder"

        /** 120 ms at 48 kHz, the largest frame Opus defines. */
        private const val MAX_SAMPLES_PER_CHANNEL = 5760
        private const val NO_DECODER = -1
        private const val BUFFER_UNUSABLE = -2
        private const val DECODE_FAILED = -3

        /** 10 ms of narrowband speech: the cheapest frame that still proves a decoder works. */
        private const val PROBE_RATE = 16000
        private const val PROBE_SAMPLES = 160
        private const val MAX_PACKET_BYTES = 1275

        private val nativeAvailable: Boolean by lazy {
            runCatching { System.loadLibrary("opus") }.isSuccess &&
                runCatching { SoftwareOpusNative.initialize() }.getOrDefault(false) &&
                nativeDecodesProbePacket()
        }

        private val javaAvailable: Boolean by lazy { javaDecodesProbePacket() }

        /** Whether this device can decode Opus without MediaCodec. Probed once per process. */
        val available: Boolean
            get() = nativeAvailable || javaAvailable

        /** Which back end [create] would return, so the capability report can name it. */
        val backend: String?
            get() = when {
                nativeAvailable -> "native"
                javaAvailable -> "concentus"
                else -> null
            }

        fun create(sampleRateHz: Int, channels: Int): SoftwareOpusDecoder? {
            val channelCount = maxOf(1, channels)
            if (nativeAvailable) {
                val handle = SoftwareOpusNative.create(sampleRateHz, channelCount)
                if (handle != 0L) return SoftwareOpusDecoder(channelCount, "native", handle, null)
            }
            if (!javaAvailable) return null
            val decoder = runCatching { OpusDecoder(sampleRateHz, channelCount) }.getOrNull() ?: return null
            return SoftwareOpusDecoder(channelCount, "concentus", 0L, decoder)
        }

        /**
         * One encoded frame, shared by both self-tests so they always judge the same input. Encoding
         * is only used here: the uplink never encodes through this class.
         */
        private fun probePacket(): Pair<ByteArray, Int>? = try {
            val pcm = ShortArray(PROBE_SAMPLES) { (((it % 64) - 32) * 300).toShort() }
            val packet = ByteArray(MAX_PACKET_BYTES)
            val written = OpusEncoder(PROBE_RATE, 1, OpusApplication.OPUS_APPLICATION_VOIP)
                .encode(pcm, 0, pcm.size, packet, 0, packet.size)
            if (written > 0) packet to written else null
        } catch (error: Throwable) {
            Log.i(TAG, "Opus probe packet could not be encoded", error)
            null
        }

        /**
         * Resolved symbols are not a working decoder: this back end reaches libopus through dlopen,
         * so an ABI mismatch with the ROM only shows up on the first real call.
         */
        private fun nativeDecodesProbePacket(): Boolean {
            val probe = probePacket() ?: return false
            return try {
                val handle = SoftwareOpusNative.create(PROBE_RATE, 1)
                if (handle == 0L) return false
                val pcm = ShortArray(PROBE_SAMPLES)
                val samples = SoftwareOpusNative.decode(handle, probe.first, 0, probe.second, pcm)
                SoftwareOpusNative.destroy(handle)
                samples == PROBE_SAMPLES && pcm.any { it != 0.toShort() }
            } catch (error: Throwable) {
                Log.i(TAG, "Native Opus decode self-test failed on this runtime", error)
                false
            }
        }

        /** Concentus is pure Java, so the failure worth catching is a runtime that cannot run it. */
        private fun javaDecodesProbePacket(): Boolean {
            val probe = probePacket() ?: return false
            return try {
                val pcm = ShortArray(PROBE_SAMPLES)
                val samples = OpusDecoder(PROBE_RATE, 1)
                    .decode(probe.first, 0, probe.second, pcm, 0, pcm.size, false)
                samples == PROBE_SAMPLES && pcm.any { it != 0.toShort() }
            } catch (error: Throwable) {
                Log.i(TAG, "Concentus decode self-test failed on this runtime", error)
                false
            }
        }
    }
}

private object SoftwareOpusNative {
    init {
        System.loadLibrary("xcertplay_opus")
    }

    external fun initialize(): Boolean
    external fun create(sampleRate: Int, channels: Int): Long
    external fun decode(handle: Long, data: ByteArray, offset: Int, length: Int, output: ShortArray): Int
    external fun destroy(handle: Long)
}
