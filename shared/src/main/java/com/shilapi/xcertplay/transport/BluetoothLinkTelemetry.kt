package com.shilapi.xcertplay.transport

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import java.io.Closeable

/**
 * Records what the car's Bluetooth stack does to the phone link while CarPlay runs.
 *
 * Wireless CarPlay carries its iAP2 control channel over RFCOMM on this same link, so a profile
 * transition here is not cosmetic: when the ACL link goes down, the session dies with it. This only
 * reads. It deliberately never releases a profile, because on vendor stacks asking for an A2DP or
 * HFP disconnect can take the whole link down and drop the session it was meant to protect.
 */
internal class BluetoothLinkTelemetry(
    private val context: Context,
    private val carPlayPhoneAddress: String?,
    private val log: (String) -> Unit,
) : Closeable {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            log(
                describe(
                    action = intent.action,
                    state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, STATE_ABSENT),
                    previousState = intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, STATE_ABSENT),
                    address = deviceAddress(intent),
                    carPlayPhoneAddress = carPlayPhoneAddress,
                ),
            )
        }
    }

    private var registered = false

    /** Head units without Bluetooth simply fail to register; that is logged, not thrown. */
    fun start() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)
        }
        registered = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            true
        }.getOrDefault(false)
        log(if (registered) "bluetooth telemetry listening" else "bluetooth telemetry registration failed")
    }

    override fun close() {
        if (!registered) return
        registered = false
        runCatching { context.unregisterReceiver(receiver) }
    }

    private fun deviceAddress(intent: Intent): String? {
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        }
        return runCatching { device?.address }.getOrNull()
    }

    internal companion object {
        private const val STATE_ABSENT = -1
        private const val HEADSET_AUDIO = "headset-audio"

        /**
         * Wording is a pure function so it can be tested without a Bluetooth stack. The address is
         * reduced to "is this the CarPlay phone" plus a two-byte tail: the exported report redacts
         * full addresses, which would otherwise make the only question that matters unanswerable.
         */
        fun describe(
            action: String?,
            state: Int,
            previousState: Int,
            address: String?,
            carPlayPhoneAddress: String?,
        ): String {
            val link = when (action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> "acl-connected"
                BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED -> "acl-disconnect-requested"
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> "acl-disconnected"
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> "a2dp"
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> "headset"
                BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED -> HEADSET_AUDIO
                else -> "unknown"
            }
            val states = when {
                state == STATE_ABSENT -> ""
                link == HEADSET_AUDIO -> " state=${audioState(state)} previous=${audioState(previousState)}"
                else -> " state=${profileState(state)} previous=${profileState(previousState)}"
            }
            val phone = when {
                address == null || carPlayPhoneAddress == null -> "unknown"
                address.equals(carPlayPhoneAddress, ignoreCase = true) -> "carplay"
                else -> "other"
            }
            return "bluetooth event link=$link$states phone=$phone tail=${tail(address)}"
        }

        private fun profileState(state: Int): String = when (state) {
            BluetoothProfile.STATE_DISCONNECTED -> "disconnected"
            BluetoothProfile.STATE_CONNECTING -> "connecting"
            BluetoothProfile.STATE_CONNECTED -> "connected"
            BluetoothProfile.STATE_DISCONNECTING -> "disconnecting"
            STATE_ABSENT -> "absent"
            else -> "code$state"
        }

        private fun audioState(state: Int): String = when (state) {
            BluetoothHeadset.STATE_AUDIO_DISCONNECTED -> "disconnected"
            BluetoothHeadset.STATE_AUDIO_CONNECTING -> "connecting"
            BluetoothHeadset.STATE_AUDIO_CONNECTED -> "connected"
            STATE_ABSENT -> "absent"
            else -> "code$state"
        }

        private fun tail(address: String?): String {
            val hex = address?.replace(":", "")?.takeIf { it.isNotEmpty() } ?: return "none"
            return hex.takeLast(4).uppercase()
        }
    }
}
