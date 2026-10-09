package com.shilapi.xcertplay

import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Point
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Process
import android.view.WindowManager
import com.shilapi.xcertplay.media.AudioCodecAvailability
import com.shilapi.xcertplay.network.CarHotspotStatus
import java.io.File
import java.net.BindException
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.Collections

/**
 * Readings the head unit can give without a CarPlay session, so one exported report answers the
 * questions that would otherwise each need a separate drive: which audio codecs exist, what the
 * hotspot signals actually say, who owns the mDNS discovery port, and which vendor classes are
 * really on this firmware.
 *
 * Every line goes through [DiagnosticRedactor], which drops lines containing addresses or the word
 * "certificate" and replaces anything address-shaped, so sections report counts and flags only.
 */
internal object DeviceCapabilitySnapshot {
    private const val MDNS_PORT = 5353
    private const val AUDIO_VIDEO_MAJOR_CLASS = 0x0400
    private const val MDNS_PORT_HEX = "14E9"
    private const val PROC_TABLES = "/proc/net/udp /proc/net/udp6"
    private const val ARP_TABLE = "/proc/net/arp"
    private const val UNRESOLVED_NEIGHBOUR = "00:00:00:00:00:00"
    private const val MAX_TABLE_BYTES = 256 * 1024
    private const val MAX_TABLE_ROWS = 4096

    fun report(context: Context): String = listOf(
        guarded("platform") { platform(context) },
        guarded("display") { display(context) },
        guarded("audio") { audio(context) },
        guarded("hotspot") { hotspot(context) },
        guarded("interfaces") { interfaces() },
        guarded("discovery port") { discoveryPort(context) },
        guarded("vendor") { vendor(context) },
    ).flatten().mapNotNull(DiagnosticRedactor::redact).joinToString("\n")
        .ifEmpty { "No device readings available." }

    private fun guarded(section: String, body: () -> List<String>): List<String> =
        runCatching(body).getOrElse { listOf("$section unavailable: ${it.javaClass.simpleName}") }

    /**
     * Panel geometry, because head-unit screens are not documented: vendors quote a diagonal and
     * nothing else, and the reported dpi is often wrong, so both the pixel counts and the dpi the
     * framework believes are recorded and the millimetres are marked as derived.
     */
    @Suppress("DEPRECATION") // API 19 has neither WindowMetrics nor a non-deprecated Display route.
    private fun display(context: Context): List<String> {
        val resources = context.resources
        val metrics = resources.displayMetrics
        val display = (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
        val real = Point()
        val usable = Point()
        @Suppress("DEPRECATION")
        display?.getSize(usable)
        display?.getRealSize(real)
        val widthMm = if (metrics.xdpi > 1f) Math.round(real.x / metrics.xdpi * 25.4f) else 0
        val heightMm = if (metrics.ydpi > 1f) Math.round(real.y / metrics.ydpi * 25.4f) else 0
        val configuration = resources.configuration
        return listOf(
            "display real=${real.x}x${real.y} usable=${usable.x}x${usable.y} rotation=${display?.rotation}",
            "display densityDpi=${metrics.densityDpi} density=${metrics.density} " +
                "xdpi=${metrics.xdpi} ydpi=${metrics.ydpi} derivedPanelMm=${widthMm}x${heightMm}",
            "display smallestWidthDp=${configuration.smallestScreenWidthDp} " +
                "widthDp=${configuration.screenWidthDp} heightDp=${configuration.screenHeightDp}",
        )
    }

    private fun platform(context: Context): List<String> {
        val abis = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Build.SUPPORTED_ABIS.toList()
        } else {
            @Suppress("DEPRECATION")
            listOfNotNull(Build.CPU_ABI, Build.CPU_ABI2)
        }
        val packages = context.packageManager
        fun feature(name: String) = packages.hasSystemFeature(name)
        val recordAudio = context.checkPermission(
            android.Manifest.permission.RECORD_AUDIO,
            Process.myPid(),
            Process.myUid(),
        ) == PackageManager.PERMISSION_GRANTED
        return listOf(
            "abis=${abis.joinToString(",")}",
            "feature usb_host=${feature(PackageManager.FEATURE_USB_HOST)} " +
                "usb_accessory=${feature(PackageManager.FEATURE_USB_ACCESSORY)} " +
                "microphone=${feature(PackageManager.FEATURE_MICROPHONE)} " +
                "bluetooth=${feature(PackageManager.FEATURE_BLUETOOTH)} " +
                "wifi=${feature(PackageManager.FEATURE_WIFI)} " +
                "wifi_direct=${feature(PackageManager.FEATURE_WIFI_DIRECT)}",
            "permission record_audio=$recordAudio",
            memory(context),
        )
    }

    /**
     * The heap ceiling decides whether GC pauses can stall the video path, and head units vary
     * widely in what they grant an app.
     */
    private fun memory(context: Context): String {
        val activity = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return "memory heapClassMb=${activity?.memoryClass} largeHeapClassMb=${activity?.largeMemoryClass} " +
            "runtimeMaxMb=${Runtime.getRuntime().maxMemory() / (1024 * 1024)} " +
            "processors=${Runtime.getRuntime().availableProcessors()}"
    }

    private fun audio(context: Context): List<String> {
        val mediaCodec = AudioCodecAvailability.opusMediaCodec
        val software = AudioCodecAvailability.opusDecoder
        val lines = mutableListOf(
            "audio opus mediacodec=$mediaCodec software=$software " +
                "backend=${AudioCodecAvailability.opusSoftwareBackend} encode=${AudioCodecAvailability.opusEncoder}",
            "audio pcm play mono=${AudioCodecAvailability.playableRates(1).sorted()} " +
                "stereo=${AudioCodecAvailability.playableRates(2).sorted()}",
            "audio pcm record mono=${AudioCodecAvailability.recordableRates(1).sorted()}",
        )
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audio != null) {
            lines += "audio hal rate=${audio.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)} " +
                "framesPerBuffer=${audio.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)}"
        }
        return lines
    }

    private fun hotspot(context: Context): List<String> = listOf(
        "hotspot verdict=${CarHotspotStatus.isEnabled(context)} " +
            "apState=${CarHotspotStatus.apState(context)} " +
            "frameworkSaysOn=${CarHotspotStatus.apEnabledByFramework(context)} " +
            "stickyApState=${CarHotspotStatus.stickyApState(context)}",
        "hotspot wifiEnabled=${wifiEnabled(context)}",
        neighboursLine(),
    )

    /**
     * Whether anything is attached to the hotspot. Without this, an iPhone that joined but could not
     * reach the AirPlay port looks identical to one that never joined. The kernel neighbour table is
     * the only route available here: the framework getters are system-only on this API range.
     */
    private fun neighboursLine(): String {
        val table = runCatching { File(ARP_TABLE).readText() }.getOrNull()
            ?: return "hotspot neighbours readable=false"
        val counts = arpNeighbourCounts(table.lines())
        val perInterface = counts.entries
            .joinToString(separator = "", prefix = "[", postfix = "]") { "${it.key}=${it.value}" }
        return "hotspot neighbours readable=true total=${counts.values.sum()} iface$perInterface"
    }

    /** Complete neighbour rows of `/proc/net/arp`, counted per interface. Addresses are not kept. */
    internal fun arpNeighbourCounts(lines: List<String>): Map<String, Int> {
        val counts = linkedMapOf<String, Int>()
        for (line in lines.drop(1)) {
            val fields = line.trim().split(Regex("\\s+"))
            if (fields.size < 6) continue
            if (fields[3] == UNRESOLVED_NEIGHBOUR) continue
            if (fields[5] == "lo") continue
            counts[fields[5]] = (counts[fields[5]] ?: 0) + 1
        }
        return counts
    }

    private fun wifiEnabled(context: Context): Boolean? = runCatching {
        (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.isWifiEnabled
    }.getOrNull()

    /**
     * The class of device a Bluetooth adapter announces. An app cannot change it, and a phone standing
     * in for a head unit announces itself as a phone, so this is the one field that can explain an
     * iPhone refusing to hand over audio for reasons outside the CarPlay protocol itself. Reflective
     * because the compile SDK no longer exposes the getter while API 18 devices still have it.
     */
    private fun bluetoothClass(adapter: BluetoothAdapter?): String = runCatching {
        val announced = adapter?.javaClass?.getMethod("getBluetoothClass")?.invoke(adapter)
            ?: return@runCatching "unknown"
        val device = announced.javaClass.getMethod("getDeviceClass").invoke(announced) as? Int
            ?: return@runCatching "unknown"
        val audioVideo = runCatching {
            announced.javaClass
                .getMethod("doesPatternContain", Int::class.javaPrimitiveType)
                .invoke(announced, AUDIO_VIDEO_MAJOR_CLASS) as? Boolean
        }.getOrNull()
        "0x%06x major=0x%04x audioVideo=${audioVideo ?: "unknown"}".format(device, device and 0x1F00)
    }.getOrElse { "unreadable:${it.javaClass.simpleName}" }

    private fun interfaces(): List<String> {
        val all = NetworkInterface.getNetworkInterfaces()?.let { Collections.list(it) }.orEmpty()
        return all.map { iface ->
            runCatching {
                val addresses = Collections.list(iface.inetAddresses)
                val v4 = addresses.count { it.address?.size == 4 }
                val v6 = addresses.size - v4
                "iface=${iface.displayName} up=${iface.isUp} loopback=${iface.isLoopback} " +
                    "multicast=${iface.supportsMulticast()} v4=$v4 v6=$v6"
            }.getOrElse { error -> "iface unreadable: ${error.javaClass.simpleName}" }
        }
    }

    /**
     * A busy discovery port is invisible to the user: mDNS just never answers. Both the bind probe
     * and the kernel table are reported, because the probe cannot say who holds the port and the
     * table cannot always be read.
     */
    private fun discoveryPort(context: Context): List<String> {
        val owners = PROC_TABLES.split(' ').flatMap { table -> tableOwners(table) }
        val described = owners.map { uid ->
            val packages = context.packageManager.getPackagesForUid(uid)?.joinToString("|") ?: "?"
            if (uid == Process.myUid()) "uid=$uid(self)" else "uid=$uid pkg=$packages"
        }
        return listOf(
            "mdns bind v4=${bindProbe("0.0.0.0")} v6=${bindProbe("::")}",
            "mdns port=$MDNS_PORT owners=[${described.joinToString(", ")}] " +
                "tablesReadable=${PROC_TABLES.split(' ').count { File(it).canRead() }}/2",
        )
    }

    private fun bindProbe(address: String): String = runCatching {
        DatagramSocket(null).use { socket ->
            socket.reuseAddress = false
            socket.bind(InetSocketAddress(InetAddress.getByName(address), MDNS_PORT))
            "free"
        }
    }.getOrElse { error -> if (error is BindException) "busy" else "error:${error.javaClass.simpleName}" }

    /** UIDs holding a local datagram port of 5353, from the kernel's UDP tables. */
    private fun tableOwners(path: String): List<Int> {
        val table = File(path)
        if (!table.canRead()) return emptyList()
        val uids = mutableListOf<Int>()
        runCatching {
            table.inputStream().bufferedReader().use { reader ->
                reader.readLine()
                var rows = 0
                var bytes = 0
                while (rows < MAX_TABLE_ROWS) {
                    val line = reader.readLine() ?: return@runCatching
                    bytes += line.length
                    if (bytes > MAX_TABLE_BYTES) return@runCatching
                    val fields = line.trim().split(Regex("\\s+"))
                    if (fields.size < 10) continue
                    val local = fields[1]
                    val colon = local.lastIndexOf(':')
                    if (colon < 0) continue
                    if (!local.substring(colon + 1).equals(MDNS_PORT_HEX, ignoreCase = true)) continue
                    fields[7].toIntOrNull()?.let { uids += it }
                    rows++
                }
            }
        }
        return uids
    }

    @Suppress("DEPRECATION") // BluetoothManager.getAdapter() needs API 18+ guards for no gain here.
    private fun vendor(context: Context): List<String> {
        val framework = File("/system/framework")
        val ecarxJars = runCatching {
            framework.listFiles()?.map { it.name }?.filter { it.startsWith("ecarx") }?.sorted()
        }.getOrNull()
        fun present(className: String) = runCatching { Class.forName(className) }.isSuccess
        fun servicePresent(name: String) = runCatching {
            Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, name) != null
        }.getOrDefault(false)
        fun installed(packageName: String) = runCatching {
            context.packageManager.getPackageInfo(packageName, 0)
        }.isSuccess
        val bluetooth = runCatching { BluetoothAdapter.getDefaultAdapter() }.getOrNull()
        val bluetoothEnabled = runCatching { bluetooth?.isEnabled }.getOrNull()
        return listOf(
            "vendor ecarxJars=${ecarxJars?.joinToString(",") ?: "unreadable"}",
            "vendor class DimInteraction=${present("com.ecarx.xui.adaptapi.diminteraction.DimInteraction")} " +
                "EcarxMusicClient=${present("com.ecarx.sdk.mediacenter.MusicClient")} " +
                "NforetekUiCommand=${present("com.nforetek.bt.aidl.UiCommand")}",
            "vendor service ecarx_btuiservice=${servicePresent("ecarx_btuiservice")}",
            "vendor package ecarxDimInteraction=${installed("com.ecarx.dim_interaction")} " +
                "bydAmapService=${installed("com.byd.amapservice")} " +
                "esFileExplorer=${installed("com.estrongs.android.pop")} " +
                "easyPlayMod=${installed("com.shihab.diplay.legacy")}",
            "bluetooth adapter=${bluetooth != null} enabled=$bluetoothEnabled " +
                "class=${bluetoothClass(bluetooth)}",
        )
    }
}
