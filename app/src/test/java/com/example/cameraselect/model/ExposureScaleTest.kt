package com.example.cameraselect.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class ExposureScaleTest {

    private val isoRange = 100..6400
    private val shutterRange = 250_000L..30_000_000_000L

    @Test
    fun isoScaleUsesTheRangeEndpoints() {
        val scale = ExponentialScale(isoRange.first.toDouble(), isoRange.last.toDouble())

        assertEquals(0f, scale.positionOf(100.0), 0.0001f)
        assertEquals(100f, scale.positionOf(6400.0), 0.0001f)
        assertEquals(100.0, scale.valueAt(0f), 0.0001)
        assertEquals(6400.0, scale.valueAt(100f), 0.0001)
    }

    @Test
    fun isoScaleMidpointIsTheGeometricMean() {
        val scale = ExponentialScale(isoRange.first.toDouble(), isoRange.last.toDouble())

        assertEquals(800.0, scale.valueAt(50f), 0.001)
    }

    @Test
    fun isoRoundTripHitsEveryStandardStop() {
        for (iso in ManualCameraValues.standardIsoStops(isoRange)) {
            val position = ExposureScale.isoPosition(iso, isoRange)
            assertEquals(iso, ExposureScale.isoAt(position, isoRange))
        }
    }

    @Test
    fun shutterScaleUsesTheRangeEndpoints() {
        val scale = ExponentialScale(
            shutterRange.first.toDouble(),
            shutterRange.last.toDouble()
        )

        assertEquals(0f, scale.positionOf(shutterRange.first.toDouble()), 0.0001f)
        assertEquals(100f, scale.positionOf(shutterRange.last.toDouble()), 0.0001f)
        assertEquals(shutterRange.first.toDouble(), scale.valueAt(0f), 0.001)
        assertEquals(shutterRange.last.toDouble(), scale.valueAt(100f), 0.001)
    }

    @Test
    fun shutterScaleMidpointIsTheGeometricMean() {
        val scale = ExponentialScale(
            shutterRange.first.toDouble(),
            shutterRange.last.toDouble()
        )
        val expected = sqrt(shutterRange.first.toDouble() * shutterRange.last.toDouble())

        assertEquals(expected, scale.valueAt(50f), 1.0)
    }

    @Test
    fun shutterRoundTripHitsEveryStandardStop() {
        for (ns in ManualCameraValues.standardShutterStops(shutterRange)) {
            val position = ExposureScale.shutterPosition(ns, shutterRange)
            val roundTripped = ExposureScale.shutterAt(position, shutterRange)
            val tolerance = maxOf(1L, ns / 1000L)

            assertTrue(
                "round trip for $ns was $roundTripped",
                abs(roundTripped - ns) <= tolerance
            )
        }
    }

    @Test
    fun fullHardwareRangeIsUsedWhenTheLensIsWider() {
        val pixelIso = 50..203936
        val pixelIsoStops = ManualCameraValues.standardIsoStops(pixelIso)

        assertEquals(50, pixelIsoStops.first())
        assertEquals(203936, pixelIsoStops.last())
        assertEquals(0f, ExposureScale.isoPosition(50, pixelIso), 0.0001f)
        assertEquals(100f, ExposureScale.isoPosition(203936, pixelIso), 0.0001f)

        val pixelShutter = 15_625L..30_000_000_000L
        val pixelShutterStops = ManualCameraValues.standardShutterStops(pixelShutter)

        assertEquals(15_625L, pixelShutterStops.first())
        assertEquals(30_000_000_000L, pixelShutterStops.last())
        assertEquals(0f, ExposureScale.shutterPosition(15_625L, pixelShutter), 0.0001f)
        assertEquals(
            100f,
            ExposureScale.shutterPosition(30_000_000_000L, pixelShutter),
            0.0001f
        )
    }

    @Test
    fun standardStopsAreOrderedAndSpanTheScale() {
        val isoPositions = ManualCameraValues.standardIsoStops(isoRange)
            .map { ExposureScale.isoPosition(it, isoRange) }
        val shutterPositions = ManualCameraValues.standardShutterStops(shutterRange)
            .map { ExposureScale.shutterPosition(it, shutterRange) }

        assertTrue(isoPositions.all { it in 0f..100f })
        assertTrue(shutterPositions.all { it in 0f..100f })
        assertEquals(isoPositions.sorted(), isoPositions)
        assertEquals(shutterPositions.sorted(), shutterPositions)
        assertEquals(0f, isoPositions.first(), 0.0001f)
        assertEquals(100f, isoPositions.last(), 0.0001f)
        assertEquals(0f, shutterPositions.first(), 0.0001f)
        assertEquals(100f, shutterPositions.last(), 0.0001f)
    }

    @Test
    fun arbitraryIsoValuesSurviveTheRoundTrip() {
        for (iso in listOf(1234, 3777, 4321)) {
            val position = ExposureScale.isoPosition(iso, isoRange)
            assertEquals(iso, ExposureScale.isoAt(position, isoRange))
        }
    }

    @Test
    fun arbitraryShutterValuesAreNotForcedOntoAStandardStop() {
        // 1/200 s sits between the 1/250 and 1/125 full stops.
        for (ns in listOf(5_000_000L, 6_250_000L, 12_345_678L)) {
            val position = ExposureScale.shutterPosition(ns, shutterRange)
            val roundTripped = ExposureScale.shutterAt(position, shutterRange)

            assertTrue(
                "round trip for $ns was $roundTripped",
                abs(roundTripped - ns) <= 1_000L
            )
        }
    }

    @Test
    fun finePositionsBetweenStopsDoNotDriftTowardAStop() {
        val position = ExposureScale.isoPosition(1234, isoRange)
        val iso = ExposureScale.isoAt(position, isoRange)
        val recomputed = ExposureScale.isoPosition(iso, isoRange)

        assertEquals(position, recomputed, 0.0001f)
        assertEquals(iso, ExposureScale.isoAt(recomputed, isoRange))
    }

    @Test
    fun minorTicksSubdivideEveryGap() {
        val stops = listOf(0f, 50f, 100f)
        val minors = ExposureScale.minorTickPositions(stops)

        assertEquals(4, minors.size)
        assertEquals(16.666f, minors[0], 0.01f)
        assertEquals(33.333f, minors[1], 0.01f)
        assertEquals(66.666f, minors[2], 0.01f)
        assertEquals(83.333f, minors[3], 0.01f)
    }

    @Test
    fun minorTicksHandleUnevenGaps() {
        val minors = ExposureScale.minorTickPositions(listOf(0f, 6f, 12f, 15f))

        assertEquals(listOf(2f, 4f, 8f, 10f, 13f, 14f), minors)
    }

    @Test
    fun medianStopGapIgnoresTheOddEndpointGap() {
        assertEquals(6f, ExposureScale.medianStopGap(listOf(0f, 6f, 12f, 100f)), 0.0001f)
        assertEquals(8f, ExposureScale.medianStopGap(listOf(0f, 2f, 8f, 20f, 30f)), 0.0001f)
    }

    @Test
    fun clampingAtBothEndsIsSafe() {
        assertEquals(100, ExposureScale.isoAt(-10f, isoRange))
        assertEquals(6400, ExposureScale.isoAt(110f, isoRange))
        assertEquals(shutterRange.first, ExposureScale.shutterAt(-10f, shutterRange))
        assertEquals(shutterRange.last, ExposureScale.shutterAt(110f, shutterRange))
    }

    @Test
    fun equalMinimumAndMaximumDoesNotDivideByZero() {
        val scale = ExponentialScale(100.0, 100.0)

        assertEquals(0f, scale.positionOf(100.0), 0.0001f)
        assertEquals(100.0, scale.valueAt(50f), 0.0001)
    }
}
