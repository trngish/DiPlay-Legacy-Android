package com.shilapi.xcertplay.network

import android.content.Context
import android.content.IntentFilter
import android.net.wifi.WifiManager

/**
 * Reads whether the head unit's own Wi-Fi hotspot is on, for the "Car hotspot" link.
 *
 * DiPlay does not turn the hotspot on itself: that needs a permission Android only grants over
 * ADB. The user turns it on in the car settings, or automates it with a tool such as BYDMate.
 */
object CarHotspotStatus {
    private const val ACTION_WIFI_AP_STATE_CHANGED = "android.net.wifi.WIFI_AP_STATE_CHANGED"
    private const val EXTRA_WIFI_AP_STATE = "wifi_state"

    /**
     * Deployed head units report 13 while the AP is up, and the AOSP numbering calls 12 enabled.
     * A false "off" stops the connection outright, so neither value may be treated as off.
     */
    internal val activeStates = setOf(12, 13)
    internal val offStates = setOf(11, 14)

    /**
     * True/false from the Wi-Fi AP state, or null for a state we cannot trust, including anything
     * outside the documented range: callers must not block on unknown. Interface flags are not used:
     * BYD keeps wlan1 up with an address while tethering is off.
     */
    fun isEnabled(context: Context): Boolean? {
        if (apEnabledByFramework(context) == true) return true
        return stateEnabled(apState(context) ?: stickyApState(context))
    }

    /**
     * Raw `getWifiApState` reading, or null when this ROM does not expose it at all. Public only so
     * the diagnostic report can explain a verdict: [isEnabled] alone cannot say which signal failed.
     */
    fun apState(context: Context): Int? = invoke(wifi(context), "getWifiApState") as? Int

    /**
     * The framework's own verdict. Deployed head units sit in state 13, where this answers false,
     * so it can confirm "on" but must never be read as "off".
     */
    fun apEnabledByFramework(context: Context): Boolean? =
        invoke(wifi(context), "isWifiApEnabled") as? Boolean

    /** The last sticky AP broadcast, for firmware whose WifiManager reflection is unavailable. */
    fun stickyApState(context: Context): Int? = runCatching {
        context.applicationContext.registerReceiver(null, IntentFilter(ACTION_WIFI_AP_STATE_CHANGED))
            ?.getIntExtra(EXTRA_WIFI_AP_STATE, -1)
            ?.takeIf { it in 10..14 }
    }.getOrNull()

    internal fun stateEnabled(state: Int?): Boolean? = when {
        state == null -> null
        state in activeStates -> true
        state in offStates -> false
        else -> null
    }

    private fun wifi(context: Context): WifiManager? =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private fun invoke(wifi: WifiManager?, method: String): Any? = wifi?.let {
        runCatching { WifiManager::class.java.getMethod(method).invoke(it) }.getOrNull()
    }
}
