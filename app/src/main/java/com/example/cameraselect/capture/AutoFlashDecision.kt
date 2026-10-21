package com.example.cameraselect.capture

/**
 * Decides whether the scene is dark enough for AUTO flash/torch.
 *
 * A scene only counts as dark when the sensor exposure and the preview image
 * agree with each other. The verdict also has to be stable for a short dwell
 * time before it is applied, so a single noisy AE frame or a pushy
 * FLASH_REQUIRED report can no longer fire the flash in a bright scene.
 *
 * The class is deliberately free of Android types so the thresholds can be
 * unit tested.
 */
class AutoFlashDecision(
    private val sensorDarkEnter: Double = 25.0,
    private val sensorDarkExit: Double = 15.0,
    private val sensorVeryDark: Double = 60.0,
    private val lumaDarkEnter: Float = 50f,
    private val lumaDarkExit: Float = 65f,
    private val enterDwellMs: Long = 350L,
    private val exitDwellMs: Long = 1_200L,
    private val previewWarmUpMs: Long = 800L,
    private val lumaMaxAgeMs: Long = 1_500L,
) {
    var isDark: Boolean = false
        private set

    private var previewStartedAtMs = Long.MIN_VALUE
    private var darkCandidateSinceMs = Long.MIN_VALUE
    private var brightSinceMs = Long.MIN_VALUE
    private var lastLuma: Float? = null
    private var lastLumaAtMs = Long.MIN_VALUE

    /** Restarts the decision, e.g. when a new preview session is configured. */
    fun onPreviewStarted(nowMs: Long) {
        isDark = false
        previewStartedAtMs = nowMs
        darkCandidateSinceMs = Long.MIN_VALUE
        brightSinceMs = nowMs
        lastLuma = null
        lastLumaAtMs = Long.MIN_VALUE
    }

    /**
     * Feeds a preview luminance sample (0..255). Samples taken before the
     * preview has warmed up are ignored because a freshly (re)configured
     * texture can briefly read as black.
     */
    fun onPreviewLuma(nowMs: Long, averageLuma: Float) {
        if (previewStartedAtMs == Long.MIN_VALUE) return
        if (nowMs - previewStartedAtMs < previewWarmUpMs) return
        lastLuma = averageLuma
        lastLumaAtMs = nowMs
    }

    /**
     * Feeds one AE result. Returns the stabilised dark-scene verdict.
     *
     * [hasHardwareFlash] must be false for cameras without an LED (typically
     * the front camera). Such cameras run with plain AE, but some HALs still
     * report FLASH_REQUIRED; trusting it there would fire the simulated screen
     * flash in a lit room. The same applies to the extreme-exposure override,
     * which is meant to help a real flash fire in a dim room. Without an LED,
     * only the sensor exposure and the preview luminance may agree.
     */
    fun update(
        nowMs: Long,
        iso: Int?,
        exposureTimeNs: Long?,
        flashRequired: Boolean,
        hasHardwareFlash: Boolean = true,
    ): Boolean {
        if (previewStartedAtMs == Long.MIN_VALUE) return isDark
        if (nowMs - previewStartedAtMs < previewWarmUpMs) return isDark

        val sensorScore = sensorScore(iso, exposureTimeNs)
        val sensorThreshold = if (isDark) sensorDarkExit else sensorDarkEnter

        val luma = lastLuma?.takeIf { nowMs - lastLumaAtMs <= lumaMaxAgeMs }
        val lumaThreshold = if (isDark) lumaDarkExit else lumaDarkEnter
        val lumaDark = luma != null && luma < lumaThreshold

        // FLASH_REQUIRED and the extreme-exposure override only make sense for
        // cameras that can actually fire a flash.
        val flashAgrees = hasHardwareFlash && flashRequired

        // Most devices report SENSOR_SENSITIVITY/SENSOR_EXPOSURE_TIME. When a
        // lens does not, fall back to the HAL's flash request plus a dark
        // preview instead of never enabling the flash.
        val sensorDark = if (sensorScore != null) {
            sensorScore > sensorThreshold
        } else {
            flashAgrees && lumaDark
        }

        // Very long exposure / very high ISO means the scene is dark even if
        // the preview pipeline brightens the displayed image or the HAL does
        // not report FLASH_REQUIRED.
        val veryDarkSensor = hasHardwareFlash &&
            sensorScore != null &&
            sensorScore > sensorVeryDark

        // Require the sensor exposure to be dark and at least one independent
        // signal to agree. FLASH_REQUIRED alone never enables the flash.
        val darkCandidate = sensorDark && (lumaDark || flashAgrees || veryDarkSensor)

        if (darkCandidate) {
            brightSinceMs = Long.MIN_VALUE
            if (darkCandidateSinceMs == Long.MIN_VALUE) darkCandidateSinceMs = nowMs
            if (!isDark && nowMs - darkCandidateSinceMs >= enterDwellMs) {
                isDark = true
            }
        } else {
            darkCandidateSinceMs = Long.MIN_VALUE
            if (brightSinceMs == Long.MIN_VALUE) brightSinceMs = nowMs
            if (isDark && nowMs - brightSinceMs >= exitDwellMs) {
                isDark = false
            }
        }
        return isDark
    }

    private fun sensorScore(iso: Int?, exposureTimeNs: Long?): Double? {
        if (iso == null || iso <= 0) return null
        if (exposureTimeNs == null || exposureTimeNs <= 0) return null
        return iso * (exposureTimeNs / 1_000_000_000.0)
    }
}
