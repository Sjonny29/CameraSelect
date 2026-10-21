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
     */
    fun update(
        nowMs: Long,
        iso: Int?,
        exposureTimeNs: Long?,
        flashRequired: Boolean,
    ): Boolean {
        if (previewStartedAtMs == Long.MIN_VALUE) return isDark
        if (nowMs - previewStartedAtMs < previewWarmUpMs) return isDark

        val sensorScore = sensorScore(iso, exposureTimeNs)
        val sensorThreshold = if (isDark) sensorDarkExit else sensorDarkEnter
        val sensorDark = sensorScore != null && sensorScore > sensorThreshold

        val luma = lastLuma?.takeIf { nowMs - lastLumaAtMs <= lumaMaxAgeMs }
        val lumaThreshold = if (isDark) lumaDarkExit else lumaDarkEnter
        val lumaDark = luma != null && luma < lumaThreshold

        // Require the sensor exposure to be dark and at least one independent
        // signal to agree. FLASH_REQUIRED alone never enables the flash.
        val darkCandidate = sensorDark && (lumaDark || flashRequired)

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
