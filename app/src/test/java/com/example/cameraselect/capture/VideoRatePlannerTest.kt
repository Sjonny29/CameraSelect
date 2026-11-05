package com.example.cameraselect.capture

import com.example.cameraselect.model.VideoStandard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoRatePlannerTest {

    private val ns60 = 16_666_666L
    private val ns30 = 33_333_333L

    @Test
    fun ntscUses60WhenRangesAndSizeAllowIt() {
        val plan = VideoRatePlanner.plan(
            standard = VideoStandard.NTSC,
            availableFpsRanges = listOf(15..30, 30..30, 60..60),
            minFrameDurationNs = ns60
        )

        assertEquals(60, plan.framesPerSecond)
        assertEquals(60..60, plan.aeFpsRange)
    }

    @Test
    fun palUses50WhenTheRangeSupportsIt() {
        val plan = VideoRatePlanner.plan(
            standard = VideoStandard.PAL,
            availableFpsRanges = listOf(15..30, 25..50),
            minFrameDurationNs = 20_000_000L
        )

        assertEquals(50, plan.framesPerSecond)
        assertEquals(25..50, plan.aeFpsRange)
    }

    @Test
    fun palFallsBackTo25When50IsUnavailable() {
        val plan = VideoRatePlanner.plan(
            standard = VideoStandard.PAL,
            availableFpsRanges = listOf(15..30, 30..30),
            minFrameDurationNs = ns30
        )

        assertEquals(25, plan.framesPerSecond)
        assertEquals(15..30, plan.aeFpsRange)
    }

    @Test
    fun ntscFallsBackTo30When60IsUnavailable() {
        val plan = VideoRatePlanner.plan(
            standard = VideoStandard.NTSC,
            availableFpsRanges = listOf(15..30, 30..30),
            minFrameDurationNs = ns30
        )

        assertEquals(30, plan.framesPerSecond)
        assertEquals(30..30, plan.aeFpsRange)
    }

    @Test
    fun sizeLimitationForcesTheFallbackEvenWhenAeRangeSupports60() {
        val plan = VideoRatePlanner.plan(
            standard = VideoStandard.NTSC,
            availableFpsRanges = listOf(15..30, 60..60),
            minFrameDurationNs = ns30
        )

        assertEquals(30, plan.framesPerSecond)
    }

    @Test
    fun emptyCapabilitiesStillFallBackToTheStandardRate() {
        val ntsc = VideoRatePlanner.plan(VideoStandard.NTSC, emptyList(), null)
        val pal = VideoRatePlanner.plan(VideoStandard.PAL, emptyList(), null)

        assertEquals(30, ntsc.framesPerSecond)
        assertNull(ntsc.aeFpsRange)
        assertEquals(25, pal.framesPerSecond)
        assertNull(pal.aeFpsRange)
    }

    @Test
    fun narrowestMatchingRangeIsPreferred() {
        val plan = VideoRatePlanner.plan(
            standard = VideoStandard.NTSC,
            availableFpsRanges = listOf(15..60, 60..60),
            minFrameDurationNs = ns60
        )

        assertEquals(60..60, plan.aeFpsRange)
    }

    @Test
    fun sixtyFpsToleratesTheRoundedOnePercentDuration() {
        val plan = VideoRatePlanner.plan(
            standard = VideoStandard.NTSC,
            availableFpsRanges = listOf(60..60),
            minFrameDurationNs = 16_830_000L
        )

        assertEquals(60, plan.framesPerSecond)
    }
}
