package com.shilapi.xcertplay.airplay

import android.view.KeyEvent

/**
 * Hardware media keys → CarPlay media HID presses (indices into [AirPlayHid]'s media report).
 *
 * Hardware play and pause keys both map to the toggle: BYD picks PLAY or PAUSE from its own idea of
 * the play state, and a wrong guess would make the button do nothing. Explicit play and pause
 * commands from media controllers use [PLAY] and [PAUSE].
 */
object CarPlayMediaButton {
    const val PLAY = 1
    const val PAUSE = 2
    const val PLAY_PAUSE = 3
    const val NEXT = 4
    const val PREVIOUS = 5

    /** BYD's steering-wheel play/pause key; the firmware normally rewrites it to MEDIA_PLAY/PAUSE. */
    const val KEYCODE_BYD_AUTO_MEDIA_PLAY_PAUSE = 353

    /** BYD's steering-wheel voice key: a short press, and the code the wheel sends for a long press. */
    const val KEYCODE_BYD_AUTO_MEDIA_VOICE = 304
    const val KEYCODE_BYD_AUTO_MEDIA_VOICE_LONG = 312

    /** LynkCo / ECARX steering-wheel voice keycodes. */
    const val KEYCODE_ECARX_VOICE_SEARCH = 220
    const val KEYCODE_ECARX_VOICE_PROMPT = 221
    const val KEYCODE_ECARX_VOICE_CUSTOM = 222

    /** LynkCo / ECARX steering-wheel media keycodes. */
    const val KEYCODE_ECARX_MEDIA_FF = 226
    const val KEYCODE_ECARX_MEDIA_REW = 227
    const val KEYCODE_ECARX_MEDIA_TOGGLE = 280

    /**
     * Whether [keyCode] is a voice key that opens Siri. The BYD wheel sends each press as an
     * instant down/up pair, so a long press arrives as its own key rather than as a held one.
     */
    fun opensSiri(keyCode: Int): Boolean = keyCode == KeyEvent.KEYCODE_VOICE_ASSIST ||
        keyCode == KeyEvent.KEYCODE_SEARCH ||
        keyCode == KEYCODE_BYD_AUTO_MEDIA_VOICE || keyCode == KEYCODE_BYD_AUTO_MEDIA_VOICE_LONG ||
        keyCode == KEYCODE_ECARX_VOICE_SEARCH || keyCode == KEYCODE_ECARX_VOICE_PROMPT ||
        keyCode == KEYCODE_ECARX_VOICE_CUSTOM

    /** The CarPlay press for [keyCode], or null when the key is not a media key CarPlay handles. */
    fun forKeyCode(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_NEXT,
        KEYCODE_ECARX_MEDIA_FF -> NEXT
        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
        KEYCODE_ECARX_MEDIA_REW -> PREVIOUS
        KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_HEADSETHOOK,
        KEYCODE_BYD_AUTO_MEDIA_PLAY_PAUSE,
        KEYCODE_ECARX_MEDIA_TOGGLE -> PLAY_PAUSE
        else -> null
    }
}
