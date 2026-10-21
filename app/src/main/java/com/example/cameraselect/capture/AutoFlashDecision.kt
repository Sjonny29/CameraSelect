package com.example.cameraselect.capture

/**
 * The part of the HAL's AE state that matters for flash decisions.
 */
enum class AeFlashState {
    /** The HAL says the scene needs a flash. */
    REQUIRED,

    /** The HAL has converged without asking for a flash. */
    CONVERGED,

    /** Searching, locked, precapturing, or the result did not contain a state. */
    UNKNOWN
}

/** Why the detector reached its current verdict. Used for logging. */
enum class FlashDecisionReason {
    NONE,
    HAL_REQUIRED,
    SENSOR_DARK,
    VERY_DARK,
    HAL_CONVERGED_VETO,
    LUMA_VETO
}

/**
 * Decides whether the scene is dark enough for AUTO flash/torch.
 *
 * The HAL is the primary source of truth for a rear still photo: a steady
 * FLASH_REQUIRED confirms a dim scene, and a steady CONVERGED vetoes the flash.
 * The sensor exposure (or, when it is missing, the preview luminance) still has
 * to agree, so a single noisy AE frame or a pushy FLASH_REQUIRED report cannot
 * fire the flash in a bright scene. A very dark sensor reading can override
 * both signals, which covers devices whose HAL never reports FLASH_REQUIRED.
 *
 * Cameras without an LED (typically the front camera) ignore the HAL state
 * entirely: they only use the sensor exposure plus preview luminance, so a
 * stray FLASH_REQUIRED cannot turn the simulated screen flash on in a lit room.
 *
 * The same detector also drives the video auto-torch. Video runs with plain AE
 * (no flash), so CONVERGED there says nothing about whether a still would need
 * a flash. [update] therefore takes a [useHalVeto] flag: the video path keeps
 * the original sensor/luma behavior, while still capture gets the hybrid HAL
 * confirm/veto logic.
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
    private val lumaBrightVeto: Float = 90f,
    private val enterDwellMs: Long = 350L,
    private val exitDwellMs: Long = 1_200L,
    private val previewWarmUpMs: Long = 800L,
    private val lumaMaxAgeMs: Long = 1_500L,
    private val halDebounceMs: Long = 150L,
) {
    var isDark: Boolean = false
        private set

    /** Reason for the current (or, while bright, the latest) verdict. */
    var reason: FlashDecisionReason = FlashDecisionReason.NONE
        private set

    private var previewStartedAtMs = Long.MIN_VALUE
    private var darkCandidateSinceMs = Long.MIN_VALUE
    private var brightSinceMs = Long.MIN_VALUE
    private var lastLuma: Float? = null
    private var lastLumaAtMs = Long.MIN_VALUE
    private var halCandidate = AeFlashState.UNKNOWN
    private var halCandidateSinceMs = Long.MIN_VALUE
    private var stableHalState = AeFlashState.UNKNOWN
    private var suspendedUntilMs = Long.MIN_VALUE

    /** Restarts the decision, e.g. when a new preview session is configured. */
    fun onPreviewStarted(nowMs: Long) {
        isDark = false
        reason = FlashDecisionReason.NONE
        previewStartedAtMs = nowMs
        darkCandidateSinceMs = Long.MIN_VALUE
        brightSinceMs = nowMs
        lastLuma = null
        lastLumaAtMs = Long.MIN_VALUE
        halCandidate = AeFlashState.UNKNOWN
        halCandidateSinceMs = nowMs
        stableHalState = AeFlashState.UNKNOWN
        suspendedUntilMs = Long.MIN_VALUE
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
     * Ignores AE/luminance frames until [nowMs]. Used while the precapture and
     * flash sequence is running, because the preflash makes the scene look
     * brighter than it really is.
     */
    fun suspendUntil(nowMs: Long) {
        if (nowMs > suspendedUntilMs) {
            suspendedUntilMs = nowMs
        }
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
        aeFlashState: AeFlashState,
        hasHardwareFlash: Boolean = true,
        useHalVeto: Boolean = true,
    ): Boolean {
        if (previewStartedAtMs == Long.MIN_VALUE) return isDark
        if (nowMs - previewStartedAtMs < previewWarmUpMs) return isDark
        if (nowMs < suspendedUntilMs) return isDark

        updateStableHalState(nowMs, aeFlashState)

        val sensorScore = sensorScore(iso, exposureTimeNs)
        val hasSensorData = sensorScore != null
        val sensorThreshold = if (isDark) sensorDarkExit else sensorDarkEnter
        val sensorDark = sensorScore != null && sensorScore > sensorThreshold

        val luma = lastLuma?.takeIf { nowMs - lastLumaAtMs <= lumaMaxAgeMs }
        val lumaThreshold = if (isDark) lumaDarkExit else lumaDarkEnter
        val lumaDark = luma != null && luma < lumaThreshold
        val lumaBright = luma != null && luma > lumaBrightVeto

        // The extreme-exposure override only makes sense for a camera that can
        // actually fire an LED.
        val veryDark = hasHardwareFlash && sensorScore != null && sensorScore > sensorVeryDark

        val darkCandidate: Boolean
        val candidateReason: FlashDecisionReason
        if (!hasHardwareFlash) {
            // No LED: ignore the HAL entirely. Both the sensor and the preview
            // have to agree that the scene is dark.
            darkCandidate = sensorDark && lumaDark
            candidateReason = if (darkCandidate) {
                FlashDecisionReason.SENSOR_DARK
            } else {
                FlashDecisionReason.NONE
            }
        } else if (!useHalVeto) {
            // Video auto-torch path. The video request uses plain AE, so a
            // CONVERGED state must not veto the torch. Keep the original
            // sensor/luma behavior; a FLASH_REQUIRED report can still confirm
            // the scene, and a very dark sensor can force it.
            darkCandidate = sensorDark &&
                (lumaDark || stableHalState == AeFlashState.REQUIRED || veryDark)
            candidateReason = when {
                !darkCandidate -> FlashDecisionReason.NONE
                veryDark -> FlashDecisionReason.VERY_DARK
                stableHalState == AeFlashState.REQUIRED -> FlashDecisionReason.HAL_REQUIRED
                else -> FlashDecisionReason.SENSOR_DARK
            }
        } else {
            when {
                // A very dark sensor is trusted even when the preview pipeline
                // brightens the displayed image or the HAL stays quiet.
                veryDark -> {
                    darkCandidate = true
                    candidateReason = FlashDecisionReason.VERY_DARK
                }

                // The HAL vetoes the flash in a scene it has already exposed.
                stableHalState == AeFlashState.CONVERGED -> {
                    darkCandidate = false
                    candidateReason = FlashDecisionReason.HAL_CONVERGED_VETO
                }

                // The HAL confirms the flash, but the sensor still has to
                // agree (or the preview must be dark when the sensor has no
                // exposure data). This keeps a faulty FLASH_REQUIRED report
                // from firing the LED in a bright scene.
                stableHalState == AeFlashState.REQUIRED -> {
                    val sensorAgrees = if (hasSensorData) sensorDark else lumaDark
                    darkCandidate = sensorAgrees
                    candidateReason = if (sensorAgrees) {
                        FlashDecisionReason.HAL_REQUIRED
                    } else {
                        FlashDecisionReason.NONE
                    }
                }

                // A bright preview is a final safety net when the HAL is
                // silent but the sensor looks dim.
                lumaBright -> {
                    darkCandidate = false
                    candidateReason = FlashDecisionReason.LUMA_VETO
                }

                sensorDark && lumaDark -> {
                    darkCandidate = true
                    candidateReason = FlashDecisionReason.SENSOR_DARK
                }

                else -> {
                    darkCandidate = false
                    candidateReason = FlashDecisionReason.NONE
                }
            }
        }

        if (darkCandidate) {
            brightSinceMs = Long.MIN_VALUE
            if (darkCandidateSinceMs == Long.MIN_VALUE) darkCandidateSinceMs = nowMs
            if (!isDark && nowMs - darkCandidateSinceMs >= enterDwellMs) {
                isDark = true
                reason = candidateReason
            } else if (isDark) {
                // Keep the reason fresh (e.g. HAL_REQUIRED -> VERY_DARK).
                reason = candidateReason
            }
        } else {
            darkCandidateSinceMs = Long.MIN_VALUE
            if (brightSinceMs == Long.MIN_VALUE) brightSinceMs = nowMs
            if (isDark && nowMs - brightSinceMs >= exitDwellMs) {
                isDark = false
                reason = candidateReason
            } else if (!isDark) {
                reason = candidateReason
            }
        }
        return isDark
    }

    private fun updateStableHalState(nowMs: Long, aeFlashState: AeFlashState) {
        if (aeFlashState == halCandidate) {
            if (halCandidateSinceMs == Long.MIN_VALUE) halCandidateSinceMs = nowMs
            if (nowMs - halCandidateSinceMs >= halDebounceMs) {
                stableHalState = aeFlashState
            }
        } else {
            halCandidate = aeFlashState
            halCandidateSinceMs = nowMs
            // Drop the previous veto/confirm while the HAL is changing. If the
            // new state persists for halDebounceMs it becomes stable above;
            // meanwhile the sensor/luma fallback keeps working. This avoids a
            // stale CONVERGED veto sticking forever on a HAL that alternates
            // between CONVERGED and FLASH_REQUIRED.
            stableHalState = AeFlashState.UNKNOWN
        }
    }

    private fun sensorScore(iso: Int?, exposureTimeNs: Long?): Double? {
        if (iso == null || iso <= 0) return null
        if (exposureTimeNs == null || exposureTimeNs <= 0) return null
        return iso * (exposureTimeNs / 1_000_000_000.0)
    }
}
