package com.shilapi.xcertplay.transport

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothLinkTelemetryTest {
    private val phone = "AA:BB:CC:DD:9F:3A"

    private fun describe(
        action: String?,
        state: Int = -1,
        previous: Int = -1,
        address: String? = phone,
        target: String? = phone,
    ) = BluetoothLinkTelemetry.describe(action, state, previous, address, target)

    @Test fun profileTransitionNamesLinkStateAndWhosePhoneItIs() {
        assertEquals(
            "bluetooth event link=a2dp state=connected previous=disconnected phone=carplay tail=9F3A",
            describe(
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothProfile.STATE_CONNECTED,
                BluetoothProfile.STATE_DISCONNECTED,
            ),
        )
    }

    /** Headset audio uses its own 10/11/12 states; reading them as profile states would lie. */
    @Test fun headsetAudioStatesAreDecodedSeparately() {
        val line = describe(
            BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED,
            BluetoothHeadset.STATE_AUDIO_CONNECTED,
            BluetoothHeadset.STATE_AUDIO_DISCONNECTED,
        )
        assertTrue(line, line.contains("link=headset-audio state=connected previous=disconnected"))
        assertFalse(line, line.contains("code12"))
    }

    /** The link dying is the event that kills a wireless session, so it must be unmistakable. */
    @Test fun aclDropIsReportedEvenWithoutStateExtras() {
        val line = describe(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        assertEquals("bluetooth event link=acl-disconnected phone=carplay tail=9F3A", line)
    }

    @Test fun anotherPairedDeviceIsNotMistakenForTheCarPlayPhone() {
        assertTrue(describe(BluetoothDevice.ACTION_ACL_CONNECTED, address = "11:22:33:44:55:66")
            .contains("phone=other"))
        assertTrue(describe(BluetoothDevice.ACTION_ACL_CONNECTED, target = null).contains("phone=unknown"))
        assertTrue(describe(BluetoothDevice.ACTION_ACL_CONNECTED, address = null).contains("tail=none"))
    }

    /** The exported report redacts addresses, so the full one must never reach a log line. */
    @Test fun fullAddressNeverAppearsAndWordingSurvivesTheRedactor() {
        for (action in listOf(
            BluetoothDevice.ACTION_ACL_CONNECTED,
            BluetoothDevice.ACTION_ACL_DISCONNECTED,
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED,
            "something.unexpected",
        )) {
            val line = describe(action, BluetoothProfile.STATE_CONNECTED, BluetoothProfile.STATE_CONNECTING)
            assertFalse(line, line.contains(phone))
            assertFalse(line, line.contains("name="))
            assertTrue(line, line.length <= 700)
        }
    }
}
