package com.shilapi.xcertplay

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
        assertTrue(report.any { it.startsWith("mdns bind ") })
        assertTrue(report.any { it.startsWith("mdns port=5353 ") })
        assertTrue(report.any { it.startsWith("vendor ecarxJars=") })
    }

    @Test fun linesStayShortEnoughToNotBeTruncated() {
        lines.forEach { line -> assertTrue("too long: $line", line.length <= 700) }
        lines.forEach { line -> assertFalse("redactor drops this word: $line", line.contains("certificate")) }
    }
}
