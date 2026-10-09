package com.shilapi.xcertplay.hud

import org.junit.Assert.assertEquals
import org.junit.Test

class LynkCoManeuverCodesTest {
    @Test
    fun mapsAppleTypesToLynkCoIconCodes() {
        assertEquals(LynkCoManeuverCodes.ICON_LEFT, LynkCoManeuverCodes.icon(1, 0))
        assertEquals(LynkCoManeuverCodes.ICON_RIGHT, LynkCoManeuverCodes.icon(2, 0))
        assertEquals(LynkCoManeuverCodes.ICON_SLIGHT_LEFT, LynkCoManeuverCodes.icon(13, 0))
        assertEquals(LynkCoManeuverCodes.ICON_SLIGHT_RIGHT, LynkCoManeuverCodes.icon(14, 0))
        assertEquals(LynkCoManeuverCodes.ICON_SHARP_LEFT, LynkCoManeuverCodes.icon(47, 0))
        assertEquals(LynkCoManeuverCodes.ICON_SHARP_RIGHT, LynkCoManeuverCodes.icon(48, 0))
        assertEquals(LynkCoManeuverCodes.ICON_U_TURN_LEFT, LynkCoManeuverCodes.icon(4, 0))
        assertEquals(LynkCoManeuverCodes.ICON_U_TURN_RIGHT, LynkCoManeuverCodes.icon(4, 1))
        assertEquals(LynkCoManeuverCodes.ICON_ROUNDABOUT, LynkCoManeuverCodes.icon(6, 0))
        assertEquals(LynkCoManeuverCodes.ICON_ROUNDABOUT, LynkCoManeuverCodes.icon(30, 0))
        assertEquals(LynkCoManeuverCodes.ICON_DESTINATION, LynkCoManeuverCodes.icon(10, 0))
        assertEquals(LynkCoManeuverCodes.ICON_STRAIGHT, LynkCoManeuverCodes.icon(3, 0))
    }
}
