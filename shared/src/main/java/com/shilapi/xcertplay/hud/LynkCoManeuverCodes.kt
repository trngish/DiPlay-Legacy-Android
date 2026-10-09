package com.shilapi.xcertplay.hud

/**
 * Maps Apple iAP2 RouteGuidanceManeuverType to LynkCo / ECARX HUD navigation icon IDs.
 */
internal object LynkCoManeuverCodes {
    const val ICON_UNKNOWN = 0
    const val ICON_STRAIGHT = 1
    const val ICON_LEFT = 2
    const val ICON_RIGHT = 3
    const val ICON_SLIGHT_LEFT = 4
    const val ICON_SLIGHT_RIGHT = 5
    const val ICON_SHARP_LEFT = 6
    const val ICON_SHARP_RIGHT = 7
    const val ICON_U_TURN_LEFT = 8
    const val ICON_U_TURN_RIGHT = 9
    const val ICON_DESTINATION = 10
    const val ICON_ROUNDABOUT = 11

    /**
     * Converts Apple maneuver type to ECARX / LynkCo HUD maneuver icon code.
     */
    fun icon(appleType: Int, drivingSide: Int): Int {
        val leftHand = drivingSide == 1
        if (appleType in 28..46) {
            return ICON_ROUNDABOUT
        }
        return when (appleType) {
            1, 20 -> ICON_LEFT
            2, 21 -> ICON_RIGHT
            47 -> ICON_SHARP_LEFT
            48 -> ICON_SHARP_RIGHT
            13, 22, 49, 52 -> ICON_SLIGHT_LEFT
            14, 23, 50, 53 -> ICON_SLIGHT_RIGHT
            4, 18, 19, 26 -> if (leftHand) ICON_U_TURN_RIGHT else ICON_U_TURN_LEFT
            6, 7 -> ICON_ROUNDABOUT
            10, 12, 24, 25, 27 -> ICON_DESTINATION
            3, 5, 8, 9, 11, 51 -> ICON_STRAIGHT
            else -> ICON_UNKNOWN
        }
    }
}
