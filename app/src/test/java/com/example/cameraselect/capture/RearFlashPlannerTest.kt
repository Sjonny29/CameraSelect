package com.example.cameraselect.capture

import com.example.cameraselect.model.FlashMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RearFlashPlannerTest {

    @Test
    fun off_usesPlainAeAndNoFlash() {
        val plan = RearFlashPlanner.plan(
            flashMode = FlashMode.OFF,
            hasFlash = true,
            autoFlashDark = true,
            isStillCapture = true
        )

        assertEquals(FlashAeMode.ON, plan.aeMode)
        assertEquals(FlashLampMode.OFF, plan.lampMode)
        assertFalse(plan.needsPrecapture)
    }

    @Test
    fun on_forcesFlashAndRunsPrecaptureOnStill() {
        val plan = RearFlashPlanner.plan(
            flashMode = FlashMode.ON,
            hasFlash = true,
            autoFlashDark = false,
            isStillCapture = true
        )

        assertEquals(FlashAeMode.ALWAYS_FLASH, plan.aeMode)
        assertEquals(FlashLampMode.OFF, plan.lampMode)
        assertTrue(plan.needsPrecapture)
    }

    @Test
    fun torch_usesTorchWithoutPrecapture() {
        val plan = RearFlashPlanner.plan(
            flashMode = FlashMode.TORCH,
            hasFlash = true,
            autoFlashDark = true,
            isStillCapture = true
        )

        assertEquals(FlashAeMode.ON, plan.aeMode)
        assertEquals(FlashLampMode.TORCH, plan.lampMode)
        assertFalse(plan.needsPrecapture)
    }

    @Test
    fun autoPreview_staysInAutoFlashModeToKeepHalReporting() {
        val plan = RearFlashPlanner.plan(
            flashMode = FlashMode.AUTO,
            hasFlash = true,
            autoFlashDark = true,
            isStillCapture = false
        )

        assertEquals(FlashAeMode.AUTO_FLASH, plan.aeMode)
        assertEquals(FlashLampMode.OFF, plan.lampMode)
        assertFalse(plan.needsPrecapture)
    }

    @Test
    fun autoDarkStill_forcesFlashAndRunsPrecapture() {
        val plan = RearFlashPlanner.plan(
            flashMode = FlashMode.AUTO,
            hasFlash = true,
            autoFlashDark = true,
            isStillCapture = true
        )

        assertEquals(FlashAeMode.ALWAYS_FLASH, plan.aeMode)
        assertEquals(FlashLampMode.OFF, plan.lampMode)
        assertTrue(plan.needsPrecapture)
    }

    @Test
    fun autoBrightStill_usesPlainAeSoHalCannotFire() {
        val plan = RearFlashPlanner.plan(
            flashMode = FlashMode.AUTO,
            hasFlash = true,
            autoFlashDark = false,
            isStillCapture = true
        )

        assertEquals(FlashAeMode.ON, plan.aeMode)
        assertEquals(FlashLampMode.OFF, plan.lampMode)
        assertFalse(plan.needsPrecapture)
    }

    @Test
    fun noHardwareFlash_neverFiresOrPrecaptures() {
        for (mode in FlashMode.values()) {
            val plan = RearFlashPlanner.plan(
                flashMode = mode,
                hasFlash = false,
                autoFlashDark = true,
                isStillCapture = true
            )

            assertEquals(FlashAeMode.ON, plan.aeMode)
            assertEquals(FlashLampMode.OFF, plan.lampMode)
            assertFalse(plan.needsPrecapture)
        }
    }
}
