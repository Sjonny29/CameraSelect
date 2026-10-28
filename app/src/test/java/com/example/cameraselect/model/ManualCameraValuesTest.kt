package com.example.cameraselect.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualCameraValuesTest {

    private val supported = ManualCapabilities(
        supportsManualSensor = true,
        isoRange = 100..3200,
        exposureRangeNs = 125_000L..1_000_000_000L,
        minimumFocusDistance = 10f
    )

    @Test
    fun isoStopsStayInsideTheSensorRange() {
        val stops = ManualCameraValues.isoStops(supported)

        assertTrue(stops.isNotEmpty())
        assertEquals(100, stops.first().value)
        assertEquals(3200, stops.last().value)
        assertTrue(stops.all { it.value in 100..3200 })
        assertEquals(stops.map { it.value }, stops.map { it.value }.sorted())
    }

    @Test
    fun isoStopsAreEmptyWithoutManualSensorSupport() {
        val stops = ManualCameraValues.isoStops(supported.copy(supportsManualSensor = false))
        assertTrue(stops.isEmpty())
    }

    @Test
    fun shutterStopsStayInsideTheExposureRange() {
        val stops = ManualCameraValues.shutterStops(supported)

        assertTrue(stops.isNotEmpty())
        assertTrue(stops.all { it.value in supported.exposureRangeNs!! })
        assertEquals(125_000L, stops.first().value)
        assertEquals(1_000_000_000L, stops.last().value)
        assertEquals("1/8000", stops.first().label)
        assertEquals("1s", stops.last().label)
    }

    @Test
    fun shutterStopsFallBackToHardwareEndpointsForExoticRanges() {
        val exotic = supported.copy(exposureRangeNs = 1L..100L)
        val stops = ManualCameraValues.shutterStops(exotic)

        assertTrue(stops.isNotEmpty())
        assertTrue(stops.all { it.value in 1L..100L })
        assertEquals(1L, stops.first().value)
        assertEquals(100L, stops.last().value)
    }

    @Test
    fun stopListsReachTheFullHardwareRangeEvenForNonStandardEndpoints() {
        val pixel = supported.copy(
            isoRange = 50..203936,
            exposureRangeNs = 15_625L..1_000_000_000L
        )

        val isoStops = ManualCameraValues.isoStops(pixel)
        assertEquals(50, isoStops.first().value)
        assertEquals(203936, isoStops.last().value)
        assertEquals("203936", isoStops.last().label)
        assertEquals(isoStops.map { it.value }.distinct().sorted(), isoStops.map { it.value })

        val shutterStops = ManualCameraValues.shutterStops(pixel)
        assertEquals(15_625L, shutterStops.first().value)
        assertEquals("1/64000", shutterStops.first().label)
        assertEquals(1_000_000_000L, shutterStops.last().value)
    }

    @Test
    fun fastShutterStopsAreAvailableWhenTheSensorSupportsThem() {
        val fast = supported.copy(exposureRangeNs = 15_625L..1_000_000_000L)
        val labels = ManualCameraValues.shutterStops(fast).map { it.label }

        assertTrue(labels.containsAll(listOf("1/8000", "1/16000", "1/32000", "1/64000")))
    }

    @Test
    fun focusStopsRunFromInfinityToTheClosestFocus() {
        val stops = ManualCameraValues.focusStops(supported)

        assertEquals(0f, stops.first().value)
        assertEquals("∞", stops.first().label)
        assertEquals(10f, stops.last().value)
        assertEquals("0.10m", stops.last().label)
        assertTrue(stops.zipWithNext().all { (a, b) -> a.value < b.value })
    }

    @Test
    fun focusStopsAreEmptyForFixedFocusLenses() {
        assertTrue(ManualCameraValues.focusStops(supported.copy(minimumFocusDistance = 0f)).isEmpty())
        assertTrue(ManualCameraValues.focusStops(supported.copy(minimumFocusDistance = null)).isEmpty())
    }

    @Test
    fun shutterFormattingUsesPhotographicLabels() {
        assertEquals("1/64000", ManualCameraValues.formatShutter(15_625L))
        assertEquals("1/32000", ManualCameraValues.formatShutter(31_250L))
        assertEquals("1/8000", ManualCameraValues.formatShutter(125_000L))
        assertEquals("1/100", ManualCameraValues.formatShutter(10_000_000L))
        assertEquals("1/3", ManualCameraValues.formatShutter(333_333_333L))
        assertEquals("1s", ManualCameraValues.formatShutter(1_000_000_000L))
        assertEquals("3s", ManualCameraValues.formatShutter(3_000_000_000L))
        assertEquals("—", ManualCameraValues.formatShutter(null))
    }

    @Test
    fun shutterFormattingWithUnitAddsSeconds() {
        assertEquals("1/250s", ManualCameraValues.formatShutterWithUnit(4_000_000L))
        assertEquals("1s", ManualCameraValues.formatShutterWithUnit(1_000_000_000L))
        assertEquals("—", ManualCameraValues.formatShutterWithUnit(null))
    }

    @Test
    fun focusFormattingUsesMetresAndInfinity() {
        assertEquals("—", ManualCameraValues.formatFocus(null))
        assertEquals("∞", ManualCameraValues.formatFocus(0f))
        assertEquals("0.50m", ManualCameraValues.formatFocus(2f))
        assertEquals("0.10m", ManualCameraValues.formatFocus(10f))
        assertEquals("2.0m", ManualCameraValues.formatFocus(0.5f))
    }

    @Test
    fun nearestIndexClampsToTheAvailableStops() {
        val stops = ManualCameraValues.isoStops(supported)

        assertEquals(0, ManualCameraValues.nearestIsoIndex(stops, 1))
        assertEquals(stops.lastIndex, ManualCameraValues.nearestIsoIndex(stops, 1_000_000))
        assertEquals(
            stops.indexOfFirst { it.value == 1600 },
            ManualCameraValues.nearestIsoIndex(stops, 1560)
        )
    }

    @Test
    fun lockingExposureUsesLiveValuesAndClampsToCapabilities() {
        val locked = ManualCameraValues.lockExposure(
            ManualCameraSettings(),
            supported,
            liveIso = 640,
            liveShutterNs = 16_666_666L
        )

        assertTrue(locked.manualExposure)
        assertEquals(640, locked.iso)
        assertEquals(16_666_666L, locked.shutterNs)
    }

    @Test
    fun lockingExposureClampsOutOfRangeValues() {
        val locked = ManualCameraValues.lockExposure(
            ManualCameraSettings(iso = 512_000, shutterNs = 90_000_000_000L),
            supported,
            liveIso = null,
            liveShutterNs = null
        )

        assertEquals(3200, locked.iso)
        assertEquals(1_000_000_000L, locked.shutterNs)
    }

    @Test
    fun normalizeKeepsSettingsInsideTheCurrentLensRange() {
        val normalized = ManualCameraValues.normalize(
            ManualCameraSettings(
                manualExposure = true,
                iso = 50,
                shutterNs = 60_000_000_000L,
                manualFocus = true,
                focusDioptres = 25f
            ),
            supported
        )

        assertEquals(100, normalized.iso)
        assertEquals(1_000_000_000L, normalized.shutterNs)
        assertEquals(10f, normalized.focusDioptres)
        assertTrue(normalized.manualExposure)
        assertTrue(normalized.manualFocus)
    }

    @Test
    fun lockingFocusFallsBackToInfinity() {
        val locked = ManualCameraValues.lockFocus(
            ManualCameraSettings(),
            supported,
            liveFocusDioptres = null,
            fallbackDioptres = 0f
        )

        assertTrue(locked.manualFocus)
        assertEquals(0f, locked.focusDioptres)
    }

    @Test
    fun lockingExposurePrefersLiveMeteringOverStaleManualValues() {
        val locked = ManualCameraValues.lockExposure(
            ManualCameraSettings(manualExposure = false, iso = 6400, shutterNs = 5_000_000L),
            supported,
            liveIso = 200,
            liveShutterNs = 20_000_000L
        )

        assertEquals(200, locked.iso)
        assertEquals(20_000_000L, locked.shutterNs)
    }

    @Test
    fun lockingFocusPrefersLiveFocusOverTheLastManualDistance() {
        val locked = ManualCameraValues.lockFocus(
            ManualCameraSettings(manualFocus = false, focusDioptres = 5f),
            supported,
            liveFocusDioptres = 1.5f,
            fallbackDioptres = 0f
        )

        assertTrue(locked.manualFocus)
        assertEquals(1.5f, locked.focusDioptres ?: -1f, 0.0001f)
    }
}
