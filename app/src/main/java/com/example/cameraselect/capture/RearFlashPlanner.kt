package com.example.cameraselect.capture

import com.example.cameraselect.model.FlashMode

/** Camera2 AE mode, kept Android-free so the planner can be unit tested. */
enum class FlashAeMode {
    ON,
    AUTO_FLASH,
    ALWAYS_FLASH
}

/** Camera2 flash (lamp) mode, kept Android-free so the planner can be unit tested. */
enum class FlashLampMode {
    OFF,
    TORCH
}

data class FlashRequestPlan(
    val aeMode: FlashAeMode,
    val lampMode: FlashLampMode,
    val needsPrecapture: Boolean
)

/**
 * Maps the user's flash mode and the auto-flash verdict onto one request plan.
 *
 * AUTO keeps the preview in AUTO_FLASH so the HAL keeps reporting
 * FLASH_REQUIRED, but the still request either forces the LED (dark) or uses
 * plain AE (bright). The still request never uses FLASH_MODE_SINGLE: the AE
 * mode is responsible for the flash, which is how Camera2 expects auto/always
 * flash to be requested.
 */
object RearFlashPlanner {

    fun plan(
        flashMode: FlashMode,
        hasFlash: Boolean,
        autoFlashDark: Boolean,
        isStillCapture: Boolean,
    ): FlashRequestPlan {
        if (!hasFlash) {
            return FlashRequestPlan(
                aeMode = FlashAeMode.ON,
                lampMode = FlashLampMode.OFF,
                needsPrecapture = false
            )
        }

        return when (flashMode) {
            FlashMode.OFF -> FlashRequestPlan(
                aeMode = FlashAeMode.ON,
                lampMode = FlashLampMode.OFF,
                needsPrecapture = false
            )

            FlashMode.ON -> FlashRequestPlan(
                aeMode = FlashAeMode.ALWAYS_FLASH,
                lampMode = FlashLampMode.OFF,
                needsPrecapture = isStillCapture
            )

            FlashMode.TORCH -> FlashRequestPlan(
                aeMode = FlashAeMode.ON,
                lampMode = FlashLampMode.TORCH,
                needsPrecapture = false
            )

            FlashMode.AUTO -> when {
                !isStillCapture -> FlashRequestPlan(
                    // Repeating preview requests never fire the flash, but
                    // this mode keeps the HAL reporting FLASH_REQUIRED.
                    aeMode = FlashAeMode.AUTO_FLASH,
                    lampMode = FlashLampMode.OFF,
                    needsPrecapture = false
                )

                autoFlashDark -> FlashRequestPlan(
                    // Force the LED and let AE meter for it, so the still is
                    // exposed for the flash instead of the ambient light.
                    aeMode = FlashAeMode.ALWAYS_FLASH,
                    lampMode = FlashLampMode.OFF,
                    needsPrecapture = true
                )

                else -> FlashRequestPlan(
                    // Plain AE without AUTO_FLASH so the HAL cannot fire the
                    // flash on its own in a bright scene.
                    aeMode = FlashAeMode.ON,
                    lampMode = FlashLampMode.OFF,
                    needsPrecapture = false
                )
            }
        }
    }
}
