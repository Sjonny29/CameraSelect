package com.example.cameraselect.repository

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import androidx.core.content.edit
import com.example.cameraselect.capture.CameraCaptureManager
import com.example.cameraselect.model.CameraLensInfo
import com.example.cameraselect.model.FlashMode
import com.example.cameraselect.model.LensFacing
import com.example.cameraselect.model.PhotoFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class CameraRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("camera_select_prefs", Context.MODE_PRIVATE)

    companion object {
        fun log(message: String) {
            Log.d("CameraDebugger", message)
        }
    }

    private class RawCameraData(
        val id: String,
        val lensFacing: LensFacing,
        val isHidden: Boolean,
        val focalLengthActual: Float,
        val eq35mm: Int,
        val supportsRaw: Boolean,
    )

    @SuppressLint("MissingPermission")
    suspend fun discoverAllCameras(): List<CameraLensInfo> = withContext(Dispatchers.IO) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val standardCameraIds = cameraManager.cameraIdList.toSet()
            log("=== [CAMERA REPOSITORY] Starting Camera Discovery ===")
            log("Standard Camera IDs from CameraManager: $standardCameraIds")

            val rawList = mutableListOf<RawCameraData>()
            val processedIds = mutableSetOf<String>()

            // 1. Process standard camera IDs
            for (id in standardCameraIds) {
                processedIds.add(id)
                inspectRawCamera(cameraManager, id, isHidden = false, rawList, processedIds, standardCameraIds)
            }

            // 2. Proactive scan for hidden camera IDs 0..15 & physical backing IDs
            for (i in 0..15) {
                val probeId = i.toString()
                if (!processedIds.contains(probeId)) {
                    try {
                        cameraManager.getCameraCharacteristics(probeId)
                        log("Probed hidden/additional Camera ID $probeId successfully!")
                        processedIds.add(probeId)
                        inspectRawCamera(cameraManager, probeId, isHidden = true, rawList, processedIds, standardCameraIds)
                    } catch (_: Exception) {
                        // ID does not exist, ignore
                    }
                }
            }

            // Group raw cameras by facing side and compute zoom ratios & display strings
            val result = processAndFormatLenses(rawList)
            log("=== [CAMERA REPOSITORY] Discovery Complete. Total discovered lenses: ${result.size} ===")
            result
        } catch (e: Exception) {
            log("Failed to enumerate cameras: ${e.message}")
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    private fun inspectRawCamera(
        cameraManager: CameraManager,
        id: String,
        isHidden: Boolean,
        rawList: MutableList<RawCameraData>,
        processedIds: MutableSet<String>,
        standardCameraIds: Set<String>
    ) {
        try {
            log("Inspecting Camera ID $id (isHidden=$isHidden)")
            val characteristics = cameraManager.getCameraCharacteristics(id)

            val lensFacing = when (characteristics.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_FRONT -> LensFacing.FRONT
                CameraCharacteristics.LENS_FACING_BACK -> LensFacing.BACK
                else -> LensFacing.EXTERNAL
            }

            val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            val actualFocal = focalLengths?.firstOrNull() ?: 0f

            val sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            val sensorWidth = sensorSize?.width ?: 0f
            val eq35mm = if (sensorWidth > 0f && actualFocal > 0f) {
                (actualFocal * 36.0f / sensorWidth).roundToInt()
            } else {
                actualFocal.roundToInt()
            }

            val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)

            rawList.add(
                RawCameraData(
                    id = id,
                    lensFacing = lensFacing,
                    isHidden = isHidden,
                    focalLengthActual = actualFocal,
                    eq35mm = eq35mm,
                    supportsRaw = CameraCaptureManager.supportsRawCapture(characteristics)
                )
            )

            // Check physical camera IDs backing logical multi-cameras
            if (capabilities?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA) == true) {
                try {
                    val physicalIds = characteristics.physicalCameraIds
                    log("Logical camera $id physical IDs: $physicalIds")
                    for (physId in physicalIds) {
                        if (processedIds.add(physId)) {
                            val physHidden = !standardCameraIds.contains(physId)
                            inspectRawCamera(cameraManager, physId, isHidden = physHidden, rawList, processedIds, standardCameraIds)
                        }
                    }
                } catch (e: Exception) {
                    log("Error getting physical camera IDs for $id: ${e.message}")
                }
            }
        } catch (e: Exception) {
            log("Error inspecting raw camera $id: ${e.message}")
        }
    }

    private fun processAndFormatLenses(rawList: List<RawCameraData>): List<CameraLensInfo> {
        val result = mutableListOf<CameraLensInfo>()

        val grouped = rawList.groupBy { it.lensFacing }
        for ((facing, list) in grouped) {
            // Find true 1x Main camera for this facing side (closest to standard 24mm 35mm-equivalent)
            val baseCamera = findBaseCamera(list)

            for (raw in list) {
                val ratio = calculateZoomRatio(raw, baseCamera)

                val zoomLabel = formatZoomLabel(ratio)
                val classification = classifyLens(facing, ratio, raw.eq35mm)
                val focalFormatted = if (raw.eq35mm > 0) "${raw.eq35mm}mm" else "${raw.focalLengthActual.roundToInt()}mm"
                val displayName = "$zoomLabel ID${raw.id} $classification Focal length $focalFormatted"

                result.add(
                    CameraLensInfo(
                        id = raw.id,
                        lensFacing = raw.lensFacing,
                        isHidden = raw.isHidden,
                        zoomLabel = zoomLabel,
                        classification = classification,
                        focalLengthMm = if (raw.eq35mm > 0) raw.eq35mm else raw.focalLengthActual.roundToInt(),
                        focalLengthActual = raw.focalLengthActual,
                        displayName = displayName,
                        supportsRaw = raw.supportsRaw
                    )
                )
            }
        }

        result.sortWith(
            compareBy<CameraLensInfo> { it.focalLengthActual }
                .thenBy { it.focalLengthMm }
                .thenBy { it.id.toIntOrNull() ?: Int.MAX_VALUE }
                .thenBy { it.id }
        )

        return result
    }

    private fun findBaseCamera(list: List<RawCameraData>): RawCameraData? {
        if (list.isEmpty()) return null

        // Prefer standard non-hidden cameras first
        val candidates = list.filter { !it.isHidden }.ifEmpty { list }

        // 1. Find camera whose 35mm equivalent focal length is closest to standard wide angle (24mm)
        val withEq = candidates.filter { it.eq35mm in 12..120 }
        if (withEq.isNotEmpty()) {
            return withEq.minByOrNull { abs(it.eq35mm - 24) }
        }

        // 2. Fallback to physical focal length closest to 5.0mm (standard wide lens)
        val withActual = candidates.filter { it.focalLengthActual > 0f }
        if (withActual.isNotEmpty()) {
            return withActual.minByOrNull { abs(it.focalLengthActual - 5.0f) }
        }

        return candidates.firstOrNull()
    }

    private fun calculateZoomRatio(raw: RawCameraData, base: RawCameraData?): Float {
        if (base == null) return 1.0f

        // Calculate ratio using 35mm equivalent focal length if available
        if (raw.eq35mm > 0 && base.eq35mm > 0) {
            return raw.eq35mm.toFloat() / base.eq35mm.toFloat()
        }

        // Otherwise use raw physical focal length
        if (raw.focalLengthActual > 0f && base.focalLengthActual > 0f) {
            return raw.focalLengthActual / base.focalLengthActual
        }

        return 1.0f
    }

    private fun formatZoomLabel(ratio: Float): String {
        if (abs(ratio - 1.0f) < 0.12f) return "1x"
        if (ratio in 0.58f..0.68f) return "0.6x"
        val rounded = (ratio * 10).roundToInt() / 10.0f
        return if (rounded % 1.0f == 0.0f) {
            "${rounded.toInt()}x"
        } else {
            String.format(Locale.US, "%.1fx", rounded)
        }
    }

    private fun classifyLens(facing: LensFacing, ratio: Float, eq35mm: Int): String {
        return when (facing) {
            LensFacing.BACK -> {
                when {
                    ratio < 0.85f || eq35mm in 1..23 -> "Ultra wide"
                    ratio > 1.8f || eq35mm > 55 -> "Telephoto"
                    else -> "Main"
                }
            }
            LensFacing.FRONT -> {
                when {
                    ratio < 0.85f -> "Ultra wide"
                    else -> "Main"
                }
            }
            LensFacing.EXTERNAL -> "External"
        }
    }

    // SharedPreferences Persistence Helpers
    fun getShowHidden(facing: LensFacing): Boolean {
        val key = facingKey("show_hidden_", facing)
        return prefs.getBoolean(key, false)
    }

    fun setShowHidden(facing: LensFacing, show: Boolean) {
        val key = facingKey("show_hidden_", facing)
        prefs.edit { putBoolean(key, show) }
    }

    fun getEnabledCameraIds(): Set<String>? {
        return prefs.getStringSet("enabled_camera_ids", null)
    }

    fun saveEnabledCameraIds(ids: Set<String>) {
        prefs.edit { putStringSet("enabled_camera_ids", ids) }
    }

    fun getIsMirrored(facing: LensFacing): Boolean {
        val migratedKey = "mirror_default_migrated_v2"
        if (!prefs.getBoolean(migratedKey, false)) {
            prefs.edit {
                putBoolean(facingKey("is_mirrored_", LensFacing.FRONT), false)
                putBoolean(migratedKey, true)
            }
        }
        val key = facingKey("is_mirrored_", facing)
        return prefs.getBoolean(key, false)
    }

    fun setIsMirrored(facing: LensFacing, mirrored: Boolean) {
        val key = facingKey("is_mirrored_", facing)
        prefs.edit { putBoolean(key, mirrored) }
    }

    fun getNoCrop(facing: LensFacing): Boolean {
        val key = facingKey("no_crop_", facing)
        return prefs.getBoolean(key, false)
    }

    fun setNoCrop(facing: LensFacing, noCrop: Boolean) {
        val key = facingKey("no_crop_", facing)
        prefs.edit { putBoolean(key, noCrop) }
    }

    fun getFlashMode(): FlashMode {
        val modeStr = prefs.getString("flash_mode", null)
        if (modeStr != null) {
            return try {
                FlashMode.valueOf(modeStr)
            } catch (_: Exception) {
                FlashMode.OFF
            }
        }
        return if (prefs.contains("is_flash_on") && prefs.getBoolean("is_flash_on", false)) {
            FlashMode.ON
        } else {
            FlashMode.OFF
        }
    }

    fun setFlashMode(mode: FlashMode) {
        prefs.edit { putString("flash_mode", mode.name) }
    }

    fun getPhotoFormat(): PhotoFormat {
        val stored = prefs.getString("photo_format", null) ?: return PhotoFormat.JPEG
        return try {
            PhotoFormat.valueOf(stored)
        } catch (_: Exception) {
            PhotoFormat.JPEG
        }
    }

    fun setPhotoFormat(format: PhotoFormat) {
        prefs.edit { putString("photo_format", format.name) }
    }

    private fun facingKey(prefix: String, facing: LensFacing): String =
        prefix + if (facing == LensFacing.FRONT) "front" else "rear"
}
