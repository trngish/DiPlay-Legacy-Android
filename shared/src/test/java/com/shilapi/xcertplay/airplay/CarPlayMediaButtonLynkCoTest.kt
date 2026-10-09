package com.shilapi.xcertplay.airplay

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarPlayMediaButtonLynkCoTest {
    @Test
    fun identifiesLynkCoVoiceKeysToOpenSiri() {
        assertTrue(CarPlayMediaButton.opensSiri(CarPlayMediaButton.KEYCODE_ECARX_VOICE_SEARCH))
        assertTrue(CarPlayMediaButton.opensSiri(CarPlayMediaButton.KEYCODE_ECARX_VOICE_PROMPT))
        assertTrue(CarPlayMediaButton.opensSiri(CarPlayMediaButton.KEYCODE_ECARX_VOICE_CUSTOM))
        assertTrue(CarPlayMediaButton.opensSiri(KeyEvent.KEYCODE_SEARCH))
        assertTrue(CarPlayMediaButton.opensSiri(KeyEvent.KEYCODE_VOICE_ASSIST))
    }

    @Test
    fun mapsLynkCoMediaKeysToCarPlayButtons() {
        assertEquals(CarPlayMediaButton.NEXT, CarPlayMediaButton.forKeyCode(CarPlayMediaButton.KEYCODE_ECARX_MEDIA_FF))
        assertEquals(CarPlayMediaButton.PREVIOUS, CarPlayMediaButton.forKeyCode(CarPlayMediaButton.KEYCODE_ECARX_MEDIA_REW))
        assertEquals(CarPlayMediaButton.PLAY_PAUSE, CarPlayMediaButton.forKeyCode(CarPlayMediaButton.KEYCODE_ECARX_MEDIA_TOGGLE))
    }
}
