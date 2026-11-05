package com.example.cameraselect.model

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
 * Shared 0..100 scale helpers for the ISO and shutter sliders. Deliberately
 * Android-free so the mapping can be unit tested.
 */
object ExposureScale {

    const val MIN_POSITION = 0f
    const val MAX_POSITION = 100f

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

    /**
     * Short guide ticks between the standard stops. [subdivisions] equal parts
     * are inserted into every gap, so the default of three gives two visual
     * third-stop ticks between each pair. They are guides only: the ruler never
     * forces the pointer onto them.
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
}
