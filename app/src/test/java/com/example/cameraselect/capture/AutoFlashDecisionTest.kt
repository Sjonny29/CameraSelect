package com.example.cameraselect.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoFlashDecisionTest {

    private val darkIso = 3200
    private val darkExposureNs = 33_333_333L // 1/30 s -> score ~106
    private val brightIso = 100
    private val brightExposureNs = 5_000_000L // 1/200 s -> score 0.5

    private fun AutoFlashDecision.bright(nowMs: Long, luma: Float = 120f): Boolean {
        onPreviewLuma(nowMs, luma)
        return update(nowMs, brightIso, brightExposureNs, flashRequired = false)
    }

    private fun AutoFlashDecision.dark(
        nowMs: Long,
        flashRequired: Boolean = true,
        luma: Float? = 15f,
    ): Boolean {
        luma?.let { onPreviewLuma(nowMs, it) }
        return update(nowMs, darkIso, darkExposureNs, flashRequired)
    }

    @Test
    fun brightSceneWithFlashRequired_staysBright() {
        val decision = AutoFlashDecision()
        decision.onPreviewStarted(0)

        var result = false
        for (t in 900L..3000L step 50L) {
            decision.onPreviewLuma(t, 120f)
            result = decision.update(t, brightIso, brightExposureNs, flashRequired = true)
        }

        assertFalse(result)
    }

    @Test
    fun darkScene_turnsDarkAfterDwell() {
        val decision = AutoFlashDecision()
        decision.onPreviewStarted(0)

        assertFalse(decision.dark(900))
        assertFalse(decision.dark(1200))
        assertTrue(decision.dark(1260))
    }

    @Test
    fun singleNoisyDarkFrame_doesNotTrigger() {
        val decision = AutoFlashDecision()
        decision.onPreviewStarted(0)

        assertFalse(decision.dark(900))
        var result = false
        for (t in 950L..3000L step 50L) {
            result = decision.bright(t)
        }

        assertFalse(result)
    }

    @Test
    fun hysteresis_keepsDarkForBorderlineScene() {
        val decision = AutoFlashDecision()
        decision.onPreviewStarted(0)

        assertFalse(decision.dark(900))
        assertTrue(decision.dark(1400))

        // Score ~16.7 (between the exit and enter thresholds) and luma 58
        // (between the exit and enter thresholds) must not drop out of dark.
        val borderlineIso = 1000
        val borderlineExposureNs = 16_666_666L
        var result = true
        for (t in 1450L..2500L step 50L) {
            decision.onPreviewLuma(t, 58f)
            result = decision.update(t, borderlineIso, borderlineExposureNs, flashRequired = true)
        }
        assertTrue(result)

        // A clearly bright scene has to persist before dark is left.
        assertTrue(decision.bright(3000))
        assertTrue(decision.bright(4000))
        assertFalse(decision.bright(4200))
    }

    @Test
    fun warmUpSamplesAreIgnored() {
        val decision = AutoFlashDecision()
        decision.onPreviewStarted(1000)

        assertFalse(decision.dark(1400))
        assertFalse(decision.dark(1700))

        // Preview warm-up ends at 1800; the decision starts from there.
        assertFalse(decision.dark(1900))
        assertTrue(decision.dark(2300))
    }

    @Test
    fun darkSensorWithoutAgreement_staysBright() {
        val decision = AutoFlashDecision()
        decision.onPreviewStarted(0)

        var result = decision.update(900, darkIso, darkExposureNs, flashRequired = false)
        for (t in 950L..3000L step 50L) {
            result = decision.update(t, darkIso, darkExposureNs, flashRequired = false)
        }
        assertFalse(result)

        assertFalse(decision.update(3050, darkIso, darkExposureNs, flashRequired = true))
        assertTrue(decision.update(3450, darkIso, darkExposureNs, flashRequired = true))
    }

    @Test
    fun staleLumaDoesNotKeepSceneDark() {
        val decision = AutoFlashDecision()
        decision.onPreviewStarted(0)

        assertFalse(decision.dark(900))
        assertTrue(decision.dark(1400))

        // No new luminance samples: the stale reading must expire so a brighter
        // scene can leave dark state again.
        val borderlineIso = 1200
        val borderlineExposureNs = 16_666_666L // score 20
        assertTrue(decision.update(5000, borderlineIso, borderlineExposureNs, flashRequired = false))
        assertFalse(decision.update(6200, borderlineIso, borderlineExposureNs, flashRequired = false))
    }

    @Test
    fun lowLumaWithBrightSensor_staysBright() {
        val decision = AutoFlashDecision()
        decision.onPreviewStarted(0)

        var result = false
        for (t in 900L..3000L step 50L) {
            decision.onPreviewLuma(t, 10f)
            result = decision.update(t, brightIso, brightExposureNs, flashRequired = true)
        }

        assertFalse(result)
    }
}
