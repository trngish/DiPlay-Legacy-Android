package com.shilapi.xcertplay.hud

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.Log
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import java.lang.reflect.Method
import java.util.concurrent.Executors

/**
 * Publishes CarPlay route arrows, distance and next road name to LynkCo CS11 / ECARX HUD.
 * Uses ECARX Adapt API DimInteraction if present, with fallback to ECARX system intents.
 */
@SuppressLint("StaticFieldLeak")
internal object LynkCoHudBridge {
    private const val TAG = "DiPlay-LynkCo-HUD"
    private const val ACTION_ECARX_NAVI_INFO = "com.ecarx.action.NAVI_INFO"
    private const val ACTION_GEELY_NAVI_INFO = "com.geely.navi.info"

    private val lock = Any()
    private val route = BydHudRouteState()
    private var context: Context? = null
    private var dimInteractionInstance: Any? = null
    private var updateNaviInfoMethod: Method? = null
    private var isEcarxClassAvailable = false
    private var showing = false

    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "diplay-lynkco-hud").apply { isDaemon = true }
    }

    /** Checks whether LynkCo / ECARX vendor classes or packages exist on this head unit. */
    fun available(appContext: Context): Boolean {
        val hasDimClass = runCatching {
            Class.forName("com.ecarx.xui.adaptapi.diminteraction.DimInteraction")
        }.isSuccess
        if (hasDimClass) return true

        val pm = appContext.packageManager
        return runCatching { pm.getPackageInfo("com.ecarx.dim_interaction", 0) }.isSuccess ||
            runCatching { pm.getPackageInfo("com.ecarx.hud", 0) }.isSuccess
    }

    fun initialize(appContext: Context) = synchronized(lock) {
        if (context == null) {
            context = appContext.applicationContext
            initDimInteraction()
        }
    }

    private fun initDimInteraction() {
        val ctx = context ?: return
        runCatching {
            val dimClass = Class.forName("com.ecarx.xui.adaptapi.diminteraction.DimInteraction")
            val createMethod = dimClass.getMethod("create", Context::class.java)
            dimInteractionInstance = createMethod.invoke(null, ctx)
            if (dimInteractionInstance != null) {
                updateNaviInfoMethod = dimInteractionInstance?.javaClass?.methods?.firstOrNull {
                    it.name == "updateNaviDirective" || it.name == "updateNaviInfo" || it.name == "setNaviStatus"
                }
                isEcarxClassAvailable = true
                Log.i(TAG, "ECARX DimInteraction initialized successfully")
            }
        }.onFailure {
            Log.i(TAG, "ECARX DimInteraction class not loaded, falling back to system intent broadcasts: ${it.message}")
        }
    }

    fun onFrame(frame: Iap2Frame) = synchronized(lock) {
        val change = route.accept(frame.messageId, frame.payload)
        when (change) {
            BydHudRouteChange.GUIDANCE -> sendCurrentLocked()
            BydHudRouteChange.CLEAR -> clearHudLocked()
            BydHudRouteChange.NONE -> Unit
        }
    }

    fun clear() = synchronized(lock) {
        route.clear()
        clearHudLocked()
    }

    private fun sendCurrentLocked() {
        val guidance = route.currentApple() ?: run {
            clearHudLocked()
            return
        }
        val iconCode = LynkCoManeuverCodes.icon(guidance.type, guidance.drivingSide)
        val road = guidance.road
        val distance = guidance.distanceMeters

        executor.execute {
            publishNaviData(iconCode, distance, road)
        }
        showing = true
    }

    private fun clearHudLocked() {
        if (!showing) return
        executor.execute {
            publishNaviData(LynkCoManeuverCodes.ICON_UNKNOWN, 0, "")
        }
        showing = false
    }

    private fun publishNaviData(iconCode: Int, distanceMeters: Int, nextRoad: String) {
        val ctx = context ?: return

        // 1. Try ECARX Adapt API if instance is available
        if (isEcarxClassAvailable && dimInteractionInstance != null) {
            runCatching {
                updateNaviInfoMethod?.invoke(dimInteractionInstance, iconCode, distanceMeters, nextRoad)
            }.onFailure {
                Log.w(TAG, "DimInteraction method invocation failed", it)
            }
        }

        // 2. Broadcast to ECARX / LynkCo HUD & Cluster receivers
        val intent = Intent(ACTION_ECARX_NAVI_INFO).apply {
            putExtra("icon", iconCode)
            putExtra("icon_code", iconCode)
            putExtra("turn_icon", iconCode)
            putExtra("distance", distanceMeters)
            putExtra("distance_meters", distanceMeters)
            putExtra("road", nextRoad)
            putExtra("next_road", nextRoad)
            putExtra("is_navigating", iconCode != LynkCoManeuverCodes.ICON_UNKNOWN)
        }
        ctx.sendBroadcast(intent)

        // 3. Fallback broadcast for Geely/LynkCo variant HUD services
        val geelyIntent = Intent(ACTION_GEELY_NAVI_INFO).apply {
            putExtra("turnIcon", iconCode)
            putExtra("distance", distanceMeters)
            putExtra("nextRoad", nextRoad)
            putExtra("status", if (iconCode != LynkCoManeuverCodes.ICON_UNKNOWN) 1 else 0)
        }
        ctx.sendBroadcast(geelyIntent)
    }
}
