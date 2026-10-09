package com.shilapi.xcertplay.media

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import com.shilapi.xcertplay.airplay.AudioStreamCodec

/**
 * Reports which audio formats this ROM can actually use. Android 4.4 ships no Opus codec, and its
 * audio HAL often accepts fewer PCM rates than CarPlay will negotiate; both failures are silent at
 * run time, so the phone must never be offered a format this head unit cannot render.
 */
object AudioCodecAvailability {
    private const val TAG = "AudioCodecAvailability"

    @Volatile private var decoderResult: Boolean? = null
    @Volatile private var encoderResult: Boolean? = null

    val opusMediaCodec: Boolean
        get() = decoderResult ?: canCreate(decoder = true).also { decoderResult = it }

    /** Opus may only be advertised when some decoder exists: MediaCodec, or the software fallback. */
    val opusDecoder: Boolean
        get() = opusMediaCodec || SoftwareOpusDecoder.available

    /** Which software back end this ROM actually runs, or null when there is none. */
    val opusSoftwareBackend: String?
        get() = SoftwareOpusDecoder.backend

    val opusEncoder: Boolean
        get() = encoderResult ?: canCreate(decoder = false).also { encoderResult = it }

    /** PCM rates this ROM will play back at the given channel count. */
    fun playableRates(channels: Int): Set<Int> = AudioStreamCodec.PCM_RATES.filter { rate ->
        measure {
            AudioTrack.getMinBufferSize(
                rate,
                if (channels >= 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        }
    }.toSet()

    /** PCM rates this ROM will record at the given channel count (the microphone uplink). */
    fun recordableRates(channels: Int): Set<Int> = AudioStreamCodec.PCM_RATES.filter { rate ->
        measure {
            AudioRecord.getMinBufferSize(
                rate,
                if (channels >= 2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        }
    }.toSet()

    private fun measure(bufferSize: () -> Int): Boolean = try {
        bufferSize() > 0
    } catch (error: Exception) {
        Log.w(TAG, "PCM buffer size query failed", error)
        false
    }

    private fun canCreate(decoder: Boolean): Boolean = try {
        val codec = if (decoder) {
            MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
        } else {
            MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
        }
        codec.release()
        true
    } catch (error: Exception) {
        Log.i(TAG, "Opus ${if (decoder) "decoder" else "encoder"} unavailable on this ROM", error)
        false
    }
}
