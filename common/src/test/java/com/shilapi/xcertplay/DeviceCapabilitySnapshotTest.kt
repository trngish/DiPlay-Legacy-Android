package com.shilapi.xcertplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class DeviceCapabilitySnapshotTest {
    private val lines get() = DeviceCapabilitySnapshot.report(RuntimeEnvironment.getApplication()).lines()

    /** A line the redactor drops is a reading that silently never reaches the exported report. */
    @Test fun everyLineSurvivesRedaction() {
        val report = lines
        assertTrue(report.isNotEmpty())
        report.forEach { line ->
            assertTrue("redactor would drop: $line", DiagnosticRedactor.redact(line) != null)
        }
    }

    @Test fun coversTheSectionsTheCarTripDependsOn() {
        val report = lines
        assertTrue(report.any { it.startsWith("abis=") })
        assertTrue(report.any { it.startsWith("display real=") })
        assertTrue(report.any { it.startsWith("display densityDpi=") })
        assertTrue(report.any { it.startsWith("audio opus ") })
        assertTrue(report.any { it.startsWith("hotspot verdict=") })
        assertTrue(report.any { it.startsWith("hotspot neighbours ") })
        assertTrue(report.any { it.startsWith("mdns bind ") })
        assertTrue(report.any { it.startsWith("mdns port=5353 ") })
        assertTrue(report.any { it.startsWith("vendor ecarxJars=") })
    }

    @Test fun linesStayShortEnoughToNotBeTruncated() {
        lines.forEach { line -> assertTrue("too long: $line", line.length <= 700) }
        lines.forEach { line -> assertFalse("redactor drops this word: $line", line.contains("certificate")) }
    }

    /** Only attached neighbours count, and the table's addresses must never leave the parser. */
    @Test fun neighbourRowsAreCountedPerInterface() {
        val counts = DeviceCapabilitySnapshot.arpNeighbourCounts(
            listOf(
                "IP address       HW type     Flags       HW address            Mask     Device",
                "192.168.43.10    0x1         0x2         aa:bb:cc:dd:ee:ff     *        wlan0",
                "192.168.43.11    0x1         0x0         00:00:00:00:00:00     *        wlan0",
                "10.17.30.5       0x1         0x6         11:22:33:44:55:66     *        rmnet0",
                "127.0.0.1        0x1         0x2         de:ad:be:ef:00:01     *        lo",
                "truncated",
            ),
        )
        assertEquals(mapOf("wlan0" to 1, "rmnet0" to 1), counts)
    }
}
