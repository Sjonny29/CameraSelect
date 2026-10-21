package com.example.cameraselect.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoFlashDecisionTest {

    private val darkIso = 3200
    private val darkExposureNs = 33_333_333L // 1/30 s -> score ~106
    private val brightIso = 100
    private val brightExposureNs = 5_000_000L // 1/200 s -> score 0.5

    private fun AutoFlashDecision.bright(
        nowMs: Long,
        luma: Float = 120f,
        hal: AeFlashState = AeFlashState.CONVERGED,
    ): Boolean {
        onPreviewLuma(nowMs, luma)
        return update(nowMs, brightIso, brightExposureNs, hal)
    }

    private fun AutoFlashDecision.dark(
        nowMs: Long,
        hal: AeFlashState = AeFlashState.REQUIRED,
        luma: Float? = 15f,
    ): Boolean {
        luma?.let { onPreviewLuma(nowMs, it) }
        return update(nowMs, darkIso, darkExposureNs, hal)
    }

    @Test
    fun brightSceneWithFlashRequired_staysBright() {
        val decision = AutoFlashDecision()
        decision.onPreviewStarted(0)

        var result = false
        for (t in 900L..3000L step 50L) {
            decision.onPreviewLuma(t, 120f)
            result = decision.update(t, brightIso, brightExposureNs, AeFlashState.REQUIRED)
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

        // Score ~66.6 (between the exit 50 and enter 80 thresholds) and luma 22
        // (between the enter 18 and exit 28 thresholds) must not drop out of dark.
        val borderlineIso = 2000
        val borderlineExposureNs = 33_333_333L
        var result = true
        for (t in 1450L..2500L step 50L) {
            decision.onPreviewLuma(t, 22f)
            result = decision.update(t, borderlineIso, borderlineExposureNs, AeFlashState.REQUIRED)
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

        // Score ~90: dark enough for the enter threshold (80) but not so dark
        // that the sensor alone is trusted (120). Without luma or a flash
        // request the scene must stay bright.
        val borderlineIso = 2700
        val borderlineExposureNs = 33_333_333L
        var result = decision.update(900, borderlineIso, borderlineExposureNs, AeFlashState.CONVERGED)
        for (t in 950L..3000L step 50L) {
            result = decision.update(t, borderlineIso, borderlineExposureNs, AeFlashState.CONVERGED)
        }
        assertFalse(result)

        // The HAL can confirm the same dim scene.
        assertFalse(decision.update(3050, borderlineIso, borderlineExposureNs, AeFlashState.REQUIRED))
        var confirmed = false
        for (t in 3100L..3650L step 50L) {
            confirmed = decision.update(t, borderlineIso, borderlineExposureNs, AeFlashState.REQUIRED)
        }
        assertTrue(confirmed)
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
        assertTrue(
            decision.update(5000, borderlineIso, borderlineExposureNs, AeFlashState.CONVERGED)
        )
        // Give the HAL debounce time to accept CONVERGED; the exit dwell then
        // starts from that moment.
        assertTrue(
            decision.update(5200, borderlineIso, borderlineExposureNs, AeFlashState.CONVERGED)
        )
        assertFalse(
            decision.update(6500, borderlineIso, borderlineExposureNs, AeFlashState.CONVERGED)
        )
    }

    @Test
    fun lowLumaWithBrightSensor_staysBright() {
        val decision = AutoFlashDecision()
        decision.onPreviewStarted(0)

        var result = false
        for (t in 900L..3000L step 50L) {
            decision.onPreviewLuma(t, 10f)
            result = decision.update(t, brightIso, brightExposureNs, AeFlashState.REQUIRED)
        }

        assertFalse(result)
    }

    @Test
    fun missingSensorData_fallsBackToFlashRequiredAndLuma() {
        val decision = AutoFlashDecision()
        decision.onPreviewStarted(0)

        // FLASH_REQUIRED without luminance data is not enough.
        assertFalse(decision.update(900, null, null, AeFlashState.REQUIRED))

        decision.onPreviewLuma(900, 15f)
        assertFalse(decision.update(1200, null, null, AeFlashState.REQUIRED))
        assertTrue(decision.update(1550, null, null, AeFlashState.REQUIRED))
    }

    @Test
    fun missingSensorDataWithBrightPreview_staysBright() {
        val decision = AutoFlashDecision()
        decision.onPreviewStarted(0)

        var result = false
        for (t in 900L..3000L step 50L) {
            decision.onPreviewLuma(t, 120f)
            result = decision.update(t, null, null, AeFlashState.REQUIRED)
        }

        assertFalse(result)
    }

    @Test
    fun veryDarkSensor_turnsDarkEvenWithBrightPreview() {
        val decision = AutoFlashDecision()
        decision.onPreviewStarted(0)

        var result = false
        for (t in 900L..1400L step 50L) {
            decision.onPreviewLuma(t, 120f)
            result = decision.update(t, 6400, darkExposureNs, AeFlashState.CONVERGED)
        }

        assertTrue(result)
    }

    @Test
    fun noHardwareFlash_ignoresFlashRequiredInLitRoom() {
        val decision = AutoFlashDecision()
        decision.onPreviewStarted(0)

        // Score ~90: passes the 80 sensor threshold, but the preview is bright.
        // A front camera without an LED must not trust FLASH_REQUIRED here.
        val dimIso = 2700
        val dimExposureNs = 33_333_333L
        var result = false
        for (t in 900L..3000L step 50L) {
            decision.onPreviewLuma(t, 90f)
            result = decision.update(
                t,
                dimIso,
                dimExposureNs,
                AeFlashState.REQUIRED,
                hasHardwareFlash = false
            )
        }

        assertFalse(result)
    }

    @Test
    fun noHardwareFlash_ignoresVeryDarkSensorOverride() {
        val decision = AutoFlashDecision()
        decision.onPreviewStarted(0)

        // The extreme-exposure override exists to help a real LED fire. On a
        // camera without one it must not drive the simulated screen flash.
        var result = false
        for (t in 900L..1500L step 50L) {
            decision.onPreviewLuma(t, 120f)
            result = decision.update(
                t,
                6400,
                darkExposureNs,
                AeFlashState.CONVERGED,
                hasHardwareFlash = false
            )
        }

        assertFalse(result)
    }

    @Test
    fun noHardwareFlash_turnsDarkFromDarkPreview() {
        val decision = AutoFlashDecision()
        decision.onPreviewStarted(0)

        decision.onPreviewLuma(900, 15f)
        assertFalse(
            decision.update(900, darkIso, darkExposureNs, AeFlashState.UNKNOWN, hasHardwareFlash = false)
        )
        assertFalse(
            decision.update(1200, darkIso, darkExposureNs, AeFlashState.UNKNOWN, hasHardwareFlash = false)
        )
        assertTrue(
            decision.update(1260, darkIso, darkExposureNs, AeFlashState.UNKNOWN, hasHardwareFlash = false)
        )
    }

    @Test
    fun halConvergedVetoesBorderlineDarkScene() {
        val decision = AutoFlashDecision()
        decision.onPreviewStarted(0)

        // Sensor score ~33 and a dark preview, but the HAL has already
        // converged: hybrid logic must not force the flash.
        val dimIso = 1000
        val dimExposureNs = 33_333_333L
        var result = false
        for (t in 900L..3000L step 50L) {
            decision.onPreviewLuma(t, 15f)
            result = decision.update(t, dimIso, dimExposureNs, AeFlashState.CONVERGED)
        }

        assertFalse(result)
        assertEquals(FlashDecisionReason.HAL_CONVERGED_VETO, decision.reason)
    }

    @Test
    fun halRequiredConfirmsDimSceneDespiteBrightPreview() {
        val decision = AutoFlashDecision()
        decision.onPreviewStarted(0)

        // The sensor is dim (score ~90, above the 80 enter threshold but below
        // the 120 override) and the HAL asks for a flash. A bright preview
        // (e.g. a night-mode preview) must not veto that combination.
        val dimIso = 2700
        val dimExposureNs = 33_333_333L
        decision.onPreviewLuma(900, 120f)
        assertFalse(decision.update(900, dimIso, dimExposureNs, AeFlashState.REQUIRED))
        assertFalse(decision.update(1200, dimIso, dimExposureNs, AeFlashState.REQUIRED))
        assertTrue(decision.update(1580, dimIso, dimExposureNs, AeFlashState.REQUIRED))
        assertEquals(FlashDecisionReason.HAL_REQUIRED, decision.reason)
    }

    @Test
    fun veryDarkSensorOverridesHalConverged() {
        val decision = AutoFlashDecision()
        decision.onPreviewStarted(0)

        var result = false
        for (t in 900L..1400L step 50L) {
            decision.onPreviewLuma(t, 120f)
            result = decision.update(t, 6400, darkExposureNs, AeFlashState.CONVERGED)
        }

        assertTrue(result)
        assertEquals(FlashDecisionReason.VERY_DARK, decision.reason)
    }

    @Test
    fun halDebounceIgnoresSingleFlashReport() {
        val decision = AutoFlashDecision()
        decision.onPreviewStarted(0)

        // Dim sensor, bright preview, and one lone FLASH_REQUIRED frame.
        // Without a stable HAL state the detector must stay bright.
        val dimIso = 1000
        val dimExposureNs = 33_333_333L
        decision.onPreviewLuma(900, 120f)
        assertFalse(decision.update(900, dimIso, dimExposureNs, AeFlashState.REQUIRED))

        var result = false
        for (t in 950L..3000L step 50L) {
            decision.onPreviewLuma(t, 120f)
            result = decision.update(t, dimIso, dimExposureNs, AeFlashState.CONVERGED)
        }

        assertFalse(result)
    }

    @Test
    fun videoTorchMode_ignoresHalConverged() {
        val decision = AutoFlashDecision()
        decision.onPreviewStarted(0)

        // Video uses plain AE, so CONVERGED must not veto the recording torch
        // in a dark scene.
        var result = false
        for (t in 900L..1400L step 50L) {
            decision.onPreviewLuma(t, 15f)
            result = decision.update(
                t,
                darkIso,
                darkExposureNs,
                AeFlashState.CONVERGED,
                useHalVeto = false
            )
        }

        assertTrue(result)
    }

    @Test
    fun videoTorchMode_staysOffInBrightSceneWithStrayFlashRequired() {
        val decision = AutoFlashDecision()
        decision.onPreviewStarted(0)

        var result = false
        for (t in 900L..3000L step 50L) {
            decision.onPreviewLuma(t, 120f)
            result = decision.update(
                t,
                brightIso,
                brightExposureNs,
                AeFlashState.REQUIRED,
                useHalVeto = false
            )
        }

        assertFalse(result)
    }

    @Test
    fun videoTorchMode_veryDarkSensorForcesTorch() {
        val decision = AutoFlashDecision()
        decision.onPreviewStarted(0)

        var result = false
        for (t in 900L..1400L step 50L) {
            decision.onPreviewLuma(t, 120f)
            result = decision.update(
                t,
                6400,
                darkExposureNs,
                AeFlashState.CONVERGED,
                useHalVeto = false
            )
        }

        assertTrue(result)
    }

    @Test
    fun suspensionIgnoresFramesDuringFlash() {
        val decision = AutoFlashDecision()
        decision.onPreviewStarted(0)

        assertFalse(decision.dark(900))
        assertTrue(decision.dark(1400))

        decision.suspendUntil(3000)
        var result = true
        for (t in 1450L..3000L step 50L) {
            decision.onPreviewLuma(t, 120f)
            result = decision.update(t, brightIso, brightExposureNs, AeFlashState.CONVERGED)
        }
        // The preflash must not flip the verdict mid-capture.
        assertTrue(result)

        // After the suspension the normal exit dwell applies again.
        assertTrue(decision.bright(3050))
        assertFalse(decision.bright(4400))
    }
}
