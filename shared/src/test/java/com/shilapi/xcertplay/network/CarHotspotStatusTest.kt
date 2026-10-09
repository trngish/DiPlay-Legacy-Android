package com.shilapi.xcertplay.network

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The AP state mapping, tested on its own because a false "off" stops a car-hotspot connection that
 * works today, while a false "on" only costs the interface scan's timeout.
 */
class CarHotspotStatusTest {
    @Test
    fun statesThatDeployedUnitsReportWhileTetheringAreOn() {
        // 13 is what both this fork and upstream treat as enabled on real head units; 12 is the AOSP
        // enabled value. Neither may fall through to null, and neither may ever read as off.
        assertEquals(true, CarHotspotStatus.stateEnabled(13))
        assertEquals(true, CarHotspotStatus.stateEnabled(12))
    }

    @Test
    fun onlyExplicitlyDisabledOrFailedStatesBlock() {
        assertEquals(false, CarHotspotStatus.stateEnabled(11))
        assertEquals(false, CarHotspotStatus.stateEnabled(14))
    }

    @Test
    fun transitionalAndUnknownStatesStayUnknown() {
        // 10 is "disabling": the user may have just turned the hotspot on, so blocking would race them.
        assertEquals(null, CarHotspotStatus.stateEnabled(10))
        assertEquals(null, CarHotspotStatus.stateEnabled(99))
        assertEquals(null, CarHotspotStatus.stateEnabled(null))
    }
}
