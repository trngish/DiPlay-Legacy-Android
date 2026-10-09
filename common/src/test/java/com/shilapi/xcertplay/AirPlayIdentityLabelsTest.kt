package com.shilapi.xcertplay

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class AirPlayIdentityLabelsTest {
    private val context get() = RuntimeEnvironment.getApplication()

    /** A head unit that never mentions its maker should not be labelled as somebody else's. */
    @Test fun unsetOemLabelFollowsTheCarName() {
        assertEquals(AirPlayPersistence.DEFAULT_CAR_NAME, AirPlayPersistence.loadCarName(context))
        assertEquals(AirPlayPersistence.DEFAULT_CAR_NAME, AirPlayPersistence.loadOemLabel(context))
        AirPlayPersistence.saveCarName(context, "CS11")
        assertEquals("CS11", AirPlayPersistence.loadOemLabel(context))
    }

    @Test fun explicitOemLabelWinsAndBlankFallsBack() {
        AirPlayPersistence.saveCarName(context, "CS11")
        AirPlayPersistence.saveOemLabel(context, "Lynk & Co")
        assertEquals("Lynk & Co", AirPlayPersistence.loadOemLabel(context))
        AirPlayPersistence.saveOemLabel(context, "   ")
        assertEquals("CS11", AirPlayPersistence.loadOemLabel(context))
    }

    @Test fun blankCarNameFallsBackToTheAppName() {
        AirPlayPersistence.saveCarName(context, "  ")
        assertEquals(AirPlayPersistence.DEFAULT_CAR_NAME, AirPlayPersistence.loadCarName(context))
    }
}
