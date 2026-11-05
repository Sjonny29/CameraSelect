package com.example.cameraselect.capture

import com.example.cameraselect.model.VideoStandard
import kotlin.math.abs

/**
 * The frame rate a recording will use, plus the AE target range the sensor
 * should be driven with. [aeFpsRange] is null when the camera did not report
 * any usable range, in which case the HAL default is kept.
 */
data class VideoRatePlan(
    val framesPerSecond: Int,
    val aeFpsRange: IntRange?
)

/**
 * Chooses the recording frame rate for a [VideoStandard].
 *
 * The preferred rate (50 PAL / 60 NTSC) is only used when both the reported AE
 * ranges and the chosen recording size can sustain it. Otherwise the standard
 * fallback (25 PAL / 30 NTSC) is used. Deliberately Android-free so it can be
 * unit tested on the JVM.
 */
object VideoRatePlanner {

    /**
     * HALs commonly report 16,666,667 ns for 60 fps instead of the exact
     * 16,666,666 ns, so allow a one percent slack when checking a size.
     */
    private const val DURATION_TOLERANCE_PERCENT = 101L

    fun plan(
        standard: VideoStandard,
        availableFpsRanges: List<IntRange>,
        minFrameDurationNs: Long?
    ): VideoRatePlan {
        val fps = if (canDeliver(standard.preferredFps, availableFpsRanges, minFrameDurationNs)) {
            standard.preferredFps
        } else {
            standard.fallbackFps
        }
        return VideoRatePlan(
            framesPerSecond = fps,
            aeFpsRange = bestRangeFor(fps, availableFpsRanges)
        )
    }

    private fun canDeliver(
        fps: Int,
        ranges: List<IntRange>,
        minFrameDurationNs: Long?
    ): Boolean {
        if (fps <= 0) return false
        // An empty capability list means the camera did not tell us it can do
        // this rate, so the safe choice is the standard fallback.
        val rangeOk = ranges.any { fps in it }
        val durationOk = minFrameDurationNs == null ||
            minFrameDurationNs <= requiredFrameDurationNs(fps) * DURATION_TOLERANCE_PERCENT / 100L
        return rangeOk && durationOk
    }

    private fun requiredFrameDurationNs(fps: Int): Long = 1_000_000_000L / fps

    /**
     * Prefers the narrowest range containing [fps] (a fixed 50/60 range is
     * steadier than a variable 15..60 range), then the closest upper bound.
     */
    private fun bestRangeFor(fps: Int, ranges: List<IntRange>): IntRange? {
        if (ranges.isEmpty()) return null
        val containing = ranges.filter { fps in it }
        if (containing.isNotEmpty()) {
            return containing.minWithOrNull(
                compareBy<IntRange> { it.last - it.first }.thenBy { it.first }
            )
        }
        return ranges.minByOrNull { abs(it.last - fps) }
    }
}
