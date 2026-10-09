package com.shilapi.xcertplay.media

import android.media.MediaCodecInfo
import android.os.Build
import java.util.Locale

/**
 * Separates vendor (hardware) codecs from AOSP software ones.
 *
 * A head unit's VPU can decode a CarPlay stream in hardware while its CPU cannot do so in real
 * time, so picking the wrong one is the difference between smooth video and none. Below API 29
 * there is no [MediaCodecInfo.isSoftwareOnly] flag, leaving the vendor naming convention as the
 * only signal.
 */
internal object VideoCodecNames {
    private val SOFTWARE_PREFIXES = listOf("omx.google.", "c2.android.", "omx.ffmpeg.", "omx.pv.")

    fun isSoftware(name: String): Boolean {
        val lower = name.lowercase(Locale.US)
        return SOFTWARE_PREFIXES.any { lower.startsWith(it) } || ".sw." in lower
    }

    fun isSoftware(info: MediaCodecInfo): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) info.isSoftwareOnly else isSoftware(info.name)
}
