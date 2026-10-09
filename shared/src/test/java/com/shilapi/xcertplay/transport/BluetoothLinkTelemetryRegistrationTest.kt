package com.shilapi.xcertplay.transport

import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Looper
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class BluetoothLinkTelemetryRegistrationTest {
    @Test fun registersLogsDeliveredEventsAndStopsOnClose() {
        val context = RuntimeEnvironment.getApplication()
        val lines = mutableListOf<String>()
        val telemetry = BluetoothLinkTelemetry(context, null) { lines += it }

        telemetry.start()
        assertTrue(lines.toString(), lines.contains("bluetooth telemetry listening"))

        context.sendBroadcast(Intent(BluetoothDevice.ACTION_ACL_DISCONNECTED))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(lines.toString(), lines.any { it.contains("link=acl-disconnected") })

        telemetry.close()
        lines.clear()
        context.sendBroadcast(Intent(BluetoothDevice.ACTION_ACL_CONNECTED))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(lines.toString(), lines.isEmpty())
    }
}
