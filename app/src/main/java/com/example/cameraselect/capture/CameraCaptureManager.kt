package com.example.cameraselect.capture

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import android.media.ExifInterface
import android.media.Image
import android.media.MediaRecorder
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.core.content.ContextCompat
import com.example.cameraselect.model.ManualCapabilities
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object CameraCaptureManager {

    private const val TAG = "CameraCaptureManager"

    fun chooseOptimalCaptureSize(characteristics: CameraCharacteristics, fallbackSize: Size): Size {
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val jpegSizes = map?.getOutputSizes(ImageFormat.JPEG) ?: emptyArray()
        if (jpegSizes.isEmpty()) {
            return fallbackSize
        }

        // The preview crop setting must not affect still capture: saved JPEGs should
        // always use the full sensor field of view and its highest resolution.
        val activeArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        val pixelArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        val sensorWidth = activeArray?.width() ?: pixelArray?.width ?: 0
        val sensorHeight = activeArray?.height() ?: pixelArray?.height ?: 0
        val targetRatio = if (sensorWidth > 0 && sensorHeight > 0) {
            maxOf(sensorWidth, sensorHeight).toFloat() / minOf(sensorWidth, sensorHeight).toFloat()
        } else {
            16f / 9f
        }

        val matchingRatioSizes = jpegSizes.filter { size ->
            abs(aspectRatio(size) - targetRatio) < 0.08f
        }

        return if (matchingRatioSizes.isNotEmpty()) {
            matchingRatioSizes.maxByOrNull { it.width.toLong() * it.height.toLong() } ?: matchingRatioSizes[0]
        } else {
            jpegSizes.maxByOrNull { it.width.toLong() * it.height.toLong() } ?: jpegSizes[0]
        }
    }

    /**
     * Reads the manual-sensor and manual-focus capabilities of a lens.
     * A lens without MANUAL_SENSOR simply shows disabled Pro controls.
     */
    fun readManualCapabilities(characteristics: CameraCharacteristics): ManualCapabilities {
        val available = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
        val supportsManualSensor =
            available?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) == true

        val isoRange = characteristics
            .get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            ?.let { it.lower..it.upper }
        val exposureRange = characteristics
            .get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            ?.let { it.lower..it.upper }
        val minFocus = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)

        return ManualCapabilities(
            supportsManualSensor = supportsManualSensor,
            isoRange = isoRange,
            exposureRangeNs = exposureRange,
            minimumFocusDistance = minFocus
        )
    }

    fun supportsRawCapture(characteristics: CameraCharacteristics): Boolean {        val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
        if (capabilities?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW) != true) {
            return false
        }
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return false
        return map.getOutputSizes(ImageFormat.RAW_SENSOR)?.isNotEmpty() == true
    }

    fun chooseRawCaptureSize(characteristics: CameraCharacteristics): Size? {
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return null
        return map.getOutputSizes(ImageFormat.RAW_SENSOR)
            ?.maxByOrNull { it.width.toLong() * it.height.toLong() }
    }

    private fun landscapeSize(size: Size): Size =
        Size(maxOf(size.width, size.height), minOf(size.width, size.height))

    private fun aspectRatio(size: Size): Float =
        maxOf(size.width, size.height).toFloat() / minOf(size.width, size.height).toFloat()

    fun chooseOptimalVideoSize(characteristics: CameraCharacteristics): Size {
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val videoSizes = map?.getOutputSizes(MediaRecorder::class.java)
            ?: map?.getOutputSizes(android.graphics.SurfaceTexture::class.java)
            ?: emptyArray()

        if (videoSizes.isEmpty()) {
            return Size(1920, 1080)
        }

        // 1. Prefer 1080p (1920x1080)
        val p1080 = videoSizes.firstOrNull {
            (it.width == 1920 && it.height == 1080) || (it.width == 1080 && it.height == 1920)
        }
        if (p1080 != null) {
            return landscapeSize(p1080)
        }

        // 2. Prefer 720p (1280x720)
        val p720 = videoSizes.firstOrNull {
            (it.width == 1280 && it.height == 720) || (it.width == 720 && it.height == 1280)
        }
        if (p720 != null) {
            return landscapeSize(p720)
        }

        // 3. Fallback: select largest size <= 1920x1080 or closest to 16:9
        val maxAllowedArea = 1920L * 1080L * 2
        val candidates = videoSizes.filter { it.width.toLong() * it.height.toLong() <= maxAllowedArea }
            .ifEmpty { videoSizes.toList() }

        val best = candidates.maxByOrNull { it.width.toLong() * it.height.toLong() } ?: videoSizes[0]
        return landscapeSize(best)
    }

    private fun computeOrientation(characteristics: CameraCharacteristics, deviceRotationDegrees: Int, defaultSensorOrientation: Int): Int {
        val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: defaultSensorOrientation
        val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
        val isFront = facing == CameraCharacteristics.LENS_FACING_FRONT

        return if (isFront) {
            (sensorOrientation + deviceRotationDegrees) % 360
        } else {
            (sensorOrientation - deviceRotationDegrees + 360) % 360
        }
    }

    fun computeJpegOrientation(characteristics: CameraCharacteristics, deviceRotationDegrees: Int): Int =
        computeOrientation(characteristics, deviceRotationDegrees, 0)

    fun computeOrientationHint(characteristics: CameraCharacteristics, deviceRotationDegrees: Int): Int =
        computeOrientation(characteristics, deviceRotationDegrees, 90)

    @Suppress("ExifInterface")
    fun saveJpegToMediaStore(
        context: Context,
        bytes: ByteArray,
        isMirrored: Boolean,
        fallbackRotationDegrees: Int = 0
    ): Uri? {
        val finalBytes = if (isMirrored) {
            try {
                val originalBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (originalBitmap != null) {
                    val exif = try {
                        android.media.ExifInterface(java.io.ByteArrayInputStream(bytes))
                    } catch (_: Exception) {
                        null
                    }
                    val exifOrientation = exif?.getAttributeInt(
                        android.media.ExifInterface.TAG_ORIENTATION,
                        android.media.ExifInterface.ORIENTATION_UNDEFINED
                    ) ?: android.media.ExifInterface.ORIENTATION_UNDEFINED

                    val rotationDegrees = when (exifOrientation) {
                        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        android.media.ExifInterface.ORIENTATION_NORMAL -> 0
                        else -> fallbackRotationDegrees
                    }

                    val matrix = Matrix().apply {
                        if (rotationDegrees != 0) {
                            postRotate(rotationDegrees.toFloat())
                        }
                        postScale(-1f, 1f)
                    }

                    val flippedBitmap = Bitmap.createBitmap(
                        originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true
                    )
                    val bos = ByteArrayOutputStream()
                    flippedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, bos)
                    originalBitmap.recycle()
                    flippedBitmap.recycle()
                    bos.toByteArray()
                } else {
                    bytes
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error mirroring JPEG bytes, fallback to raw", e)
                bytes
            }
        } else {
            bytes
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "IMG_$timestamp.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/CameraSelect")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return null

        return try {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(finalBytes)
                stream.flush()
            }
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, contentValues, null, null)
            uri
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save photo to MediaStore", e)
            context.contentResolver.delete(uri, null, null)
            null
        }
    }

    fun saveDngToMediaStore(
        context: Context,
        characteristics: CameraCharacteristics,
        result: TotalCaptureResult,
        image: Image,
        rotationDegrees: Int
    ): Uri? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "IMG_$timestamp.dng"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/x-adobe-dng")
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/CameraSelect")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return null

        return try {
            DngCreator(characteristics, result).use { dngCreator ->
                dngCreator.setOrientation(exifOrientationForDegrees(rotationDegrees))
                dngCreator.setDescription("CameraSelect RAW")
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    dngCreator.writeImage(stream, image)
                    stream.flush()
                } ?: throw IllegalStateException("Could not open DNG output stream")
            }
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, contentValues, null, null)
            uri
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save DNG to MediaStore", e)
            context.contentResolver.delete(uri, null, null)
            null
        }
    }

    private fun exifOrientationForDegrees(rotationDegrees: Int): Int =
        when (((rotationDegrees % 360) + 360) % 360) {
            90 -> ExifInterface.ORIENTATION_ROTATE_90
            180 -> ExifInterface.ORIENTATION_ROTATE_180
            270 -> ExifInterface.ORIENTATION_ROTATE_270
            else -> ExifInterface.ORIENTATION_NORMAL
        }

    fun saveBitmapToMediaStore(
        context: Context,
        bitmap: Bitmap,
        isMirrored: Boolean,
        rotationDegrees: Int = 0
    ): Uri? {
        val matrix = Matrix().apply {
            if (rotationDegrees != 0) {
                postRotate(rotationDegrees.toFloat())
            }
            if (isMirrored) {
                postScale(-1f, 1f)
            }
        }
        val finalBitmap = if (!matrix.isIdentity) {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "IMG_$timestamp.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/CameraSelect")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return null

        return try {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                stream.flush()
            }
            if (isMirrored && finalBitmap != bitmap) {
                finalBitmap.recycle()
            }
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, contentValues, null, null)
            uri
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save bitmap photo to MediaStore", e)
            context.contentResolver.delete(uri, null, null)
            null
        }
    }

    fun createVideoUri(context: Context): Pair<Uri, ParcelFileDescriptor>? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "VID_$timestamp.mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, filename)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/CameraSelect")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return null

        val pfd = try {
            context.contentResolver.openFileDescriptor(uri, "rw")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open PFD for video", e)
            context.contentResolver.delete(uri, null, null)
            return null
        } ?: return null

        return Pair(uri, pfd)
    }

    fun finalizeVideo(context: Context, uri: Uri) {
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }
            context.contentResolver.update(uri, contentValues, null, null)
            Log.d(TAG, "Successfully finalized video: $uri")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to finalize video in MediaStore", e)
        }
    }

    fun discardVideo(context: Context, uri: Uri) {
        try {
            context.contentResolver.delete(uri, null, null)
            Log.d(TAG, "Discarded video: $uri")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to discard video", e)
        }
    }

    fun prepareMediaRecorder(
        context: Context,
        pfd: ParcelFileDescriptor,
        characteristics: CameraCharacteristics,
        videoSize: Size,
        deviceRotationDegrees: Int
    ): MediaRecorder? {
        return try {
            val recorder = MediaRecorder(context)

            val hasAudio = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasAudio) {
                try {
                    recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                } catch (e: Exception) {
                    Log.w(TAG, "Microphone unavailable, recording without audio: ${e.message}")
                }
            }

            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setOutputFile(pfd.fileDescriptor)
            recorder.setVideoEncodingBitRate(12_000_000)
            recorder.setVideoFrameRate(30)
            recorder.setVideoSize(videoSize.width, videoSize.height)
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)

            if (hasAudio) {
                try {
                    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    recorder.setAudioEncodingBitRate(192_000)
                    recorder.setAudioSamplingRate(48_000)
                } catch (e: Exception) {
                    Log.w(TAG, "Audio encoder setup failed, proceeding with video-only: ${e.message}")
                }
            }

            val hint = computeOrientationHint(characteristics, deviceRotationDegrees)
            recorder.setOrientationHint(hint)
            recorder.prepare()
            recorder
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare MediaRecorder", e)
            null
        }
    }
}
