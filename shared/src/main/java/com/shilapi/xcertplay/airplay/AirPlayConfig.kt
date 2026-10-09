package com.shilapi.xcertplay.airplay

/** Display insets in pixels, used for CarPlay viewArea and safeArea declarations. */
data class AirPlayInsets(
    val top: Int = 0,
    val bottom: Int = 0,
    val left: Int = 0,
    val right: Int = 0,
)

/** One display advertised to the phone in /info. */
data class AirPlayDisplayConfig(
    val widthPixels: Int,
    val heightPixels: Int,
    val widthPhysicalMm: Int? = null,
    val heightPhysicalMm: Int? = null,
    val fps: Int = 60,
    val primaryInputDevice: Int = 1,
    val viewArea: AirPlayInsets? = null,
    val safeArea: AirPlayInsets? = null,
    val safeAreaDrawOutside: Boolean? = null,
    val initialUrl: String? = null,
    /** Display feature bits; null keeps the main-screen default (high-fidelity touch and knobs). */
    val features: Int? = null,
)

/** One OEM homescreen icon. */
data class AirPlayIcon(
    val widthPixels: Int,
    val heightPixels: Int,
    val data: ByteArray,
)

/** Immutable accessory configuration consumed by the AirPlay session server. */
data class AirPlayConfig(
    val deviceName: String,
    val deviceId: String,
    val btMac: String,
    val sourceVersion: String,
    val main: AirPlayDisplayConfig,
    val cluster: AirPlayDisplayConfig? = null,
    val rightHandDrive: Boolean = false,
    val port: Int = 7000,
    val entertainmentSampleRate: Int = 48000,
    val hevc: Boolean = false,
    val disableAudioOutput: Boolean = false,
    val microphone: Boolean = false,
    /**
     * Opus bits reach the phone only when the ROM instantiates the codecs (see AudioCodecAvailability);
     * Android 4.4 has neither, and declaring them there silences notifications, Siri and calls.
     */
    val opusOutputAvailable: Boolean = true,
    val opusInputAvailable: Boolean = true,
    /**
     * PCM rates the device can actually render or record, per channel count. A rate the audio HAL
     * refuses becomes silence later with no protocol error, so it must not appear in the mask.
     * Defaults keep every rate declared for callers that do not probe, such as tests.
     */
    val pcmOutputMonoRates: Set<Int> = AudioStreamCodec.PCM_RATES.toSet(),
    val pcmOutputStereoRates: Set<Int> = AudioStreamCodec.PCM_RATES.toSet(),
    val pcmInputMonoRates: Set<Int> = AudioStreamCodec.PCM_RATES.toSet(),
    val manufacturer: String = "xcertplay",
    val model: String = "xcertplay",
    val oemLabel: String = "xcertplay",
    val icons: List<AirPlayIcon> = emptyList(),
)
