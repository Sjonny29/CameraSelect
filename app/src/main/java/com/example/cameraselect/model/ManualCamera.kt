package com.example.cameraselect.model

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Which manual parameter the Pro panel is editing. */
enum class ManualControlTarget {
    ISO,
    SHUTTER,
    FOCUS
}

/**
 * Manual capabilities of one Camera2 lens.
 *
 * Ranges come straight from [android.hardware.camera2.CameraCharacteristics].
 * The manual controls are unavailable when the lens is not MANUAL_SENSOR
 * capable, which is common for front and some ultra-wide cameras.
 */
data class ManualCapabilities(
    val supportsManualSensor: Boolean = false,
    val isoRange: IntRange? = null,
    val exposureRangeNs: LongRange? = null,
    /** Dioptres. 0 means the lens is fixed focus and has no manual focus. */
    val minimumFocusDistance: Float? = null
) {
    val supportsIso: Boolean
        get() = supportsManualSensor && isoRange != null

    val supportsShutter: Boolean
        get() = supportsManualSensor && exposureRangeNs != null

    val supportsManualFocus: Boolean
        get() = minimumFocusDistance != null && minimumFocusDistance > 0f

    companion object {
        val UNSUPPORTED = ManualCapabilities()
    }
}

/**
 * The manual values currently selected in Pro mode.
 *
 * When [manualExposure] is false the HAL keeps its AE loop running and [iso] /
 * [shutterNs] are just the last values we locked. Camera2 has a single AE loop,
 * so ISO and shutter are always manual or automatic together.
 */
data class ManualCameraSettings(
    val manualExposure: Boolean = false,
    val iso: Int? = null,
    val shutterNs: Long? = null,
    val manualFocus: Boolean = false,
    val focusDioptres: Float? = null
) {
    fun withIso(value: Int): ManualCameraSettings = copy(iso = value, manualExposure = true)

    fun withShutter(value: Long): ManualCameraSettings = copy(shutterNs = value, manualExposure = true)

    fun withFocus(value: Float): ManualCameraSettings = copy(focusDioptres = value, manualFocus = true)
}

/** One entry of a Pro wheel. [label] is what the wheel and badge show. */
data class ManualStop<T>(val value: T, val label: String)

/**
 * Pure helpers for the Pro photo controls: stop lists, value formatting and
 * clamping. Deliberately Android-free so it can be unit tested on the JVM.
 */
object ManualCameraValues {

    private val THIRD_STOP = 2.0.pow(1.0 / 3.0)

    private val ISO_TABLE = listOf(
        25, 32, 40, 50, 64, 80, 100, 125, 160, 200, 250, 320, 400, 500, 640,
        800, 1000, 1250, 1600, 2000, 2500, 3200, 4000, 5000, 6400, 8000,
        10000, 12800, 16000, 20000, 25600, 32000, 40000, 51200, 64000,
        80000, 102400, 128000, 160000, 204800, 256000, 320000, 409600,
        512000, 640000, 819200, 1024000
    )

    /**
     * Standard photographic shutter values, in nanoseconds. The labels are what
     * a photographer expects to see, not raw nanosecond numbers.
     */
    private val SHUTTER_TABLE: List<ManualStop<Long>> = listOf(
        "1/64000" to 1.0 / 64000,
        "1/50000" to 1.0 / 50000,
        "1/40000" to 1.0 / 40000,
        "1/32000" to 1.0 / 32000,
        "1/25000" to 1.0 / 25000,
        "1/20000" to 1.0 / 20000,
        "1/16000" to 1.0 / 16000,
        "1/12500" to 1.0 / 12500,
        "1/10000" to 1.0 / 10000,
        "1/8000" to 1.0 / 8000,
        "1/6400" to 1.0 / 6400,
        "1/5000" to 1.0 / 5000,
        "1/4000" to 1.0 / 4000,
        "1/3200" to 1.0 / 3200,
        "1/2500" to 1.0 / 2500,
        "1/2000" to 1.0 / 2000,
        "1/1600" to 1.0 / 1600,
        "1/1250" to 1.0 / 1250,
        "1/1000" to 1.0 / 1000,
        "1/800" to 1.0 / 800,
        "1/640" to 1.0 / 640,
        "1/500" to 1.0 / 500,
        "1/400" to 1.0 / 400,
        "1/320" to 1.0 / 320,
        "1/250" to 1.0 / 250,
        "1/200" to 1.0 / 200,
        "1/160" to 1.0 / 160,
        "1/125" to 1.0 / 125,
        "1/100" to 1.0 / 100,
        "1/80" to 1.0 / 80,
        "1/60" to 1.0 / 60,
        "1/50" to 1.0 / 50,
        "1/40" to 1.0 / 40,
        "1/30" to 1.0 / 30,
        "1/25" to 1.0 / 25,
        "1/20" to 1.0 / 20,
        "1/15" to 1.0 / 15,
        "1/13" to 1.0 / 13,
        "1/10" to 1.0 / 10,
        "1/8" to 1.0 / 8,
        "1/6" to 1.0 / 6,
        "1/5" to 1.0 / 5,
        "1/4" to 1.0 / 4,
        "1/3" to 1.0 / 3,
        "0.4s" to 0.4,
        "0.5s" to 0.5,
        "0.6s" to 0.6,
        "0.8s" to 0.8,
        "1s" to 1.0,
        "1.3s" to 1.3,
        "1.6s" to 1.6,
        "2s" to 2.0,
        "2.5s" to 2.5,
        "3s" to 3.0,
        "4s" to 4.0,
        "5s" to 5.0,
        "6s" to 6.0,
        "8s" to 8.0,
        "10s" to 10.0,
        "13s" to 13.0,
        "15s" to 15.0,
        "20s" to 20.0,
        "25s" to 25.0,
        "30s" to 30.0
    ).map { (label, seconds) ->
        ManualStop((seconds * 1_000_000_000.0).roundToLong(), label)
    }

    private const val FOCUS_STEP_DIOPTRES = 0.1f

    fun isoStops(capabilities: ManualCapabilities): List<ManualStop<Int>> {
        if (!capabilities.supportsManualSensor) return emptyList()
        val range = capabilities.isoRange ?: return emptyList()
        val standard = ISO_TABLE.filter { it in range }
        val values: List<Int> = if (standard.isEmpty()) {
            geometricStops(range.first.toDouble(), range.last.toDouble())
                .map { it.toInt() }
        } else {
            standard
        }
        return withHardwareEndpoints(values, range.first, range.last)
            .map { ManualStop(it, it.toString()) }
    }

    fun shutterStops(capabilities: ManualCapabilities): List<ManualStop<Long>> {
        if (!capabilities.supportsManualSensor) return emptyList()
        val range = capabilities.exposureRangeNs ?: return emptyList()
        val standard = SHUTTER_TABLE.filter { it.value in range }
        val values: List<Long> = if (standard.isEmpty()) {
            geometricStops(range.first.toDouble(), range.last.toDouble())
        } else {
            standard.map { it.value }
        }
        return withHardwareEndpoints(values, range.first, range.last)
            .map { ManualStop(it, formatShutter(it)) }
    }

    fun focusStops(capabilities: ManualCapabilities): List<ManualStop<Float>> {
        val maxDioptres = capabilities.minimumFocusDistance ?: return emptyList()
        if (maxDioptres <= 0f) return emptyList()

        val steps = ceil(maxDioptres / FOCUS_STEP_DIOPTRES).toInt().coerceAtLeast(1)
        val values = (0..steps)
            .map { (it * FOCUS_STEP_DIOPTRES).coerceAtMost(maxDioptres) }
            .distinct()

        return values.map { ManualStop(it, formatFocus(it)) }
    }

    fun nearestIsoIndex(stops: List<ManualStop<Int>>, value: Int?): Int {
        if (stops.isEmpty()) return 0
        if (value == null) return stops.size / 2
        return stops.indices.minByOrNull { abs(stops[it].value - value) } ?: 0
    }

    fun nearestShutterIndex(stops: List<ManualStop<Long>>, value: Long?): Int {
        if (stops.isEmpty()) return 0
        if (value == null) return stops.size / 2
        return stops.indices.minByOrNull { abs(stops[it].value - value) } ?: 0
    }

    fun nearestFocusIndex(stops: List<ManualStop<Float>>, value: Float?): Int {
        if (stops.isEmpty()) return 0
        if (value == null) return 0
        return stops.indices.minByOrNull { abs(stops[it].value - value) } ?: 0
    }

    fun formatIso(iso: Int?): String = iso?.toString() ?: "—"

    fun formatShutter(exposureNs: Long?): String {
        if (exposureNs == null || exposureNs <= 0L) return "—"

        val nearest = SHUTTER_TABLE.minByOrNull { abs(it.value - exposureNs) }
        if (nearest != null && abs(nearest.value - exposureNs) <= maxOf(1L, nearest.value / 50L)) {
            return nearest.label
        }

        val seconds = exposureNs / 1_000_000_000.0
        return if (seconds >= 0.3) {
            formatSeconds(seconds)
        } else {
            "1/${(1.0 / seconds).roundToInt()}"
        }
    }

    fun formatFocus(dioptres: Float?): String {
        if (dioptres == null) return "—"
        if (dioptres <= 0.001f) return "∞"

        val metres = 1f / dioptres
        return when {
            metres >= 10f -> "${metres.roundToInt()}m"
            metres >= 1f -> String.format(Locale.US, "%.1fm", metres)
            else -> String.format(Locale.US, "%.2fm", metres)
        }
    }

    /**
     * Switches exposure to manual at the current metered values, so grabbing a
     * wheel in AUTO never jumps the picture to a stale manual setting.
     */
    fun lockExposure(
        settings: ManualCameraSettings,
        capabilities: ManualCapabilities,
        liveIso: Int?,
        liveShutterNs: Long?
    ): ManualCameraSettings {
        if (settings.manualExposure) return settings

        val iso = liveIso
            ?: settings.iso
            ?: capabilities.isoRange?.let { 100.coerceIn(it) }
            ?: 100
        val shutter = liveShutterNs
            ?: settings.shutterNs
            ?: capabilities.exposureRangeNs?.let { 16_666_666L.coerceIn(it) }
            ?: 16_666_666L

        return settings.copy(
            manualExposure = true,
            iso = capabilities.isoRange?.let { iso.coerceIn(it) } ?: iso,
            shutterNs = capabilities.exposureRangeNs?.let { shutter.coerceIn(it) } ?: shutter
        )
    }

    fun lockFocus(
        settings: ManualCameraSettings,
        capabilities: ManualCapabilities,
        liveFocusDioptres: Float?,
        fallbackDioptres: Float?
    ): ManualCameraSettings {
        if (settings.manualFocus) return settings
        val max = capabilities.minimumFocusDistance ?: 0f
        val focus = liveFocusDioptres
            ?: settings.focusDioptres
            ?: fallbackDioptres
            ?: 0f
        return settings.copy(
            manualFocus = true,
            focusDioptres = focus.coerceIn(0f, max)
        )
    }

    fun normalize(
        settings: ManualCameraSettings,
        capabilities: ManualCapabilities
    ): ManualCameraSettings {
        var normalized = settings
        settings.iso?.let { iso ->
            capabilities.isoRange?.let { range ->
                normalized = normalized.copy(iso = iso.coerceIn(range))
            }
        }
        settings.shutterNs?.let { shutter ->
            capabilities.exposureRangeNs?.let { range ->
                normalized = normalized.copy(shutterNs = shutter.coerceIn(range))
            }
        }
        settings.focusDioptres?.let { focus ->
            capabilities.minimumFocusDistance?.let { max ->
                normalized = normalized.copy(focusDioptres = focus.coerceIn(0f, max))
            }
        }
        return normalized
    }

    /**
     * The hardware range is authoritative: some sensors report non-standard
     * endpoints (for example ISO 203936 or 1/64000 s) that never appear in the
     * photographic tables above. Always surface those exact endpoints so the
     * wheel can reach the full capability of the lens.
     */
    private fun <T : Comparable<T>> withHardwareEndpoints(
        values: List<T>,
        first: T,
        last: T
    ): List<T> = (values + first + last).distinct().sorted()

    private fun geometricStops(min: Double, max: Double): List<Long> {
        if (min <= 0.0 || max < min) return emptyList()

        val values = mutableListOf<Long>()
        var value = min
        var guard = 0
        while (value < max && guard < 512) {
            values.add(value.roundToLong())
            value *= THIRD_STOP
            guard++
        }
        values.add(max.roundToLong())
        return values.distinct().sorted()
    }

    private fun formatSeconds(seconds: Double): String {
        val rounded = (seconds * 10.0).roundToInt() / 10.0
        return if (abs(rounded - rounded.roundToInt()) < 0.01) {
            "${rounded.roundToInt()}s"
        } else {
            String.format(Locale.US, "%.1fs", rounded)
        }
    }
}
