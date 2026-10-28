package com.example.cameraselect.model

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Exponential (logarithmic) mapping between a 0..100 slider position and a
 * positive photographic value. Equal distances on the slider are equal stops,
 * so ISO and shutter speed share exactly the same feel.
 */
class ExponentialScale(val min: Double, val max: Double) {

    init {
        require(min > 0.0) { "min must be positive" }
        require(max >= min) { "max must be at least min" }
    }

    fun positionOf(value: Double): Float {
        if (max <= min) return 0f
        val clamped = value.coerceIn(min, max)
        return (100.0 * ln(clamped / min) / ln(max / min))
            .toFloat()
            .coerceIn(ExposureScale.MIN_POSITION, ExposureScale.MAX_POSITION)
    }

    fun valueAt(position: Float): Double {
        if (max <= min) return min
        val fraction = position.coerceIn(
            ExposureScale.MIN_POSITION,
            ExposureScale.MAX_POSITION
        ) / 100.0
        return (min * (max / min).pow(fraction)).coerceIn(min, max)
    }
}

/**
 * Shared 0..100 scale and magnetic-stop helpers for the ISO and shutter
 * sliders. Deliberately Android-free so the mapping can be unit tested.
 */
object ExposureScale {

    const val MIN_POSITION = 0f
    const val MAX_POSITION = 100f

    /**
     * Hard cap for the magnetic window, in slider units. Snapping is meant to
     * happen only when the pointer is already very close to a stop, so the rest
     * of the scale stays free for fine values.
     */
    const val SNAP_THRESHOLD = 1.8f

    /**
     * The magnetic window is a small fraction of the local stop spacing. For
     * the standard shutter scale that is roughly a fifth of a stop; for the
     * wider ISO scale the [SNAP_THRESHOLD] cap keeps it even tighter.
     */
    private const val SNAP_WINDOW_FRACTION = 0.18f
    private const val MAGNETIC_STRENGTH = 0.65f

    fun isoScale(capabilities: ManualCapabilities): ExponentialScale? {
        if (!capabilities.supportsManualSensor) return null
        val range = capabilities.isoRange ?: return null
        if (range.first <= 0 || range.last < range.first) return null
        return ExponentialScale(range.first.toDouble(), range.last.toDouble())
    }

    fun shutterScale(capabilities: ManualCapabilities): ExponentialScale? {
        if (!capabilities.supportsManualSensor) return null
        val range = capabilities.exposureRangeNs ?: return null
        if (range.first <= 0L || range.last < range.first) return null
        return ExponentialScale(range.first.toDouble(), range.last.toDouble())
    }

    fun isoPosition(iso: Int, range: IntRange): Float =
        ExponentialScale(range.first.toDouble(), range.last.toDouble())
            .positionOf(iso.toDouble())

    fun isoAt(position: Float, range: IntRange): Int =
        ExponentialScale(range.first.toDouble(), range.last.toDouble())
            .valueAt(position)
            .roundToInt()
            .coerceIn(range.first, range.last)

    fun shutterPosition(exposureNs: Long, range: LongRange): Float =
        ExponentialScale(range.first.toDouble(), range.last.toDouble())
            .positionOf(exposureNs.toDouble())

    fun shutterAt(position: Float, range: LongRange): Long =
        ExponentialScale(range.first.toDouble(), range.last.toDouble())
            .valueAt(position)
            .roundToLong()
            .coerceIn(range.first, range.last)

    fun nearestStop(position: Float, stopPositions: List<Float>): Float? =
        stopPositions.minByOrNull { abs(it - position) }

    /**
     * Short guide ticks between the standard stops. [subdivisions] equal parts
     * are inserted into every gap, so the default of three gives two visual
     * third-stop ticks between each pair. They are guides only and are never
     * used as snap targets.
     */
    fun minorTickPositions(
        stopPositions: List<Float>,
        subdivisions: Int = 3
    ): List<Float> {
        if (subdivisions < 2 || stopPositions.size < 2) return emptyList()

        val sorted = stopPositions.distinct().sorted()
        return buildList {
            for (index in 0 until sorted.lastIndex) {
                val start = sorted[index]
                val step = (sorted[index + 1] - start) / subdivisions
                if (step <= 0f) continue
                for (part in 1 until subdivisions) {
                    add(start + step * part)
                }
            }
        }.distinct().sorted()
    }

    /**
     * Median distance between neighbouring stops, in slider units. Used to keep
     * roughly the same number of stops visible on the ISO and shutter rulers
     * even though their hardware ranges cover different numbers of stops.
     */
    fun medianStopGap(stopPositions: List<Float>): Float {
        val sorted = stopPositions.distinct().sorted()
        if (sorted.size < 2) return MAX_POSITION / 6f

        val gaps = sorted.zipWithNext { first, second -> second - first }
            .filter { it > 0f }
            .sorted()
        if (gaps.isEmpty()) return MAX_POSITION / 6f

        val middle = gaps.size / 2
        return if (gaps.size % 2 == 0) {
            (gaps[middle - 1] + gaps[middle]) / 2f
        } else {
            gaps[middle]
        }
    }

    /**
     * Half-width of the magnetic window around [position]'s nearest stop. It is
     * a small fraction of the local stop spacing, capped so that only a very
     * deliberate approach settles onto a stop.
     */
    fun snapWindow(position: Float, stopPositions: List<Float>): Float {
        if (stopPositions.isEmpty()) return 0f

        val sorted = stopPositions.sorted()
        val index = sorted.indices.minByOrNull { abs(sorted[it] - position) } ?: return 0f
        val gap = when {
            sorted.size == 1 -> MAX_POSITION
            index == 0 -> sorted[1] - sorted[0]
            index == sorted.lastIndex -> sorted[index] - sorted[index - 1]
            else -> minOf(
                sorted[index] - sorted[index - 1],
                sorted[index + 1] - sorted[index]
            )
        }
        return minOf(SNAP_THRESHOLD, gap * SNAP_WINDOW_FRACTION)
    }

    /**
     * Smooth, bounded pull toward the nearest stop. The thumb still follows the
     * finger continuously; this only bends its path near a detent.
     */
    fun magneticPosition(position: Float, stopPositions: List<Float>): Float {
        val nearest = nearestStop(position, stopPositions) ?: return position
        val window = snapWindow(position, stopPositions)
        if (window <= 0f) return position

        val distance = abs(nearest - position)
        if (distance >= window) return position

        val t = 1f - distance / window
        val eased = t * t * (3f - 2f * t)
        return position + (nearest - position) * MAGNETIC_STRENGTH * eased
    }

    /**
     * Exact stop to use when the finger lifts, or [position] unchanged when the
     * user deliberately moved into the fine-tuning gap between stops.
     */
    fun settlePosition(position: Float, stopPositions: List<Float>): Float {
        val nearest = nearestStop(position, stopPositions) ?: return position
        return if (abs(nearest - position) <= snapWindow(position, stopPositions)) {
            nearest
        } else {
            position
        }
    }
}
