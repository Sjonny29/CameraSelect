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
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object CameraCaptureManager {

    private const val TAG = "CameraCaptureManager"

    fun chooseOptimalCaptureSize(characteristics: CameraCharacteristics, previewSize: Size): Size {
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val jpegSizes = map?.getOutputSizes(ImageFormat.JPEG) ?: emptyArray()
        if (jpegSizes.isEmpty()) {
            return previewSize
        }

        val targetRatio = if (previewSize.height > 0) {
            maxOf(previewSize.width, previewSize.height).toFloat() / minOf(previewSize.width, previewSize.height).toFloat()
        } else {
            16f / 9f
        }

        val matchingRatioSizes = jpegSizes.filter { size ->
            val ratio = maxOf(size.width, size.height).toFloat() / minOf(size.width, size.height).toFloat()
            abs(ratio - targetRatio) < 0.08f
        }

        return if (matchingRatioSizes.isNotEmpty()) {
            matchingRatioSizes.maxByOrNull { it.width.toLong() * it.height.toLong() } ?: matchingRatioSizes[0]
        } else {
            jpegSizes.maxByOrNull { it.width.toLong() * it.height.toLong() } ?: jpegSizes[0]
        }
    }

    fun chooseOptimalVideoSize(characteristics: CameraCharacteristics, previewSize: Size): Size {
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
            return Size(maxOf(p1080.width, p1080.height), minOf(p1080.width, p1080.height))
        }

        // 2. Prefer 720p (1280x720)
        val p720 = videoSizes.firstOrNull {
            (it.width == 1280 && it.height == 720) || (it.width == 720 && it.height == 1280)
        }
        if (p720 != null) {
            return Size(maxOf(p720.width, p720.height), minOf(p720.width, p720.height))
        }

        // 3. Fallback: select largest size <= 1920x1080 or closest to 16:9
        val maxAllowedArea = 1920L * 1080L * 2
        val candidates = videoSizes.filter { it.width.toLong() * it.height.toLong() <= maxAllowedArea }
            .ifEmpty { videoSizes.toList() }

        val best = candidates.maxByOrNull { it.width.toLong() * it.height.toLong() } ?: videoSizes[0]
        return Size(maxOf(best.width, best.height), minOf(best.width, best.height))
    }

    fun computeJpegOrientation(characteristics: CameraCharacteristics, deviceRotationDegrees: Int): Int {
        val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
        val isFront = facing == CameraCharacteristics.LENS_FACING_FRONT

        return if (isFront) {
            (sensorOrientation + deviceRotationDegrees) % 360
        } else {
            (sensorOrientation - deviceRotationDegrees + 360) % 360
        }
    }

    fun computeOrientationHint(characteristics: CameraCharacteristics, deviceRotationDegrees: Int): Int {
        val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
        val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
        val isFront = facing == CameraCharacteristics.LENS_FACING_FRONT

        return if (isFront) {
            (sensorOrientation + deviceRotationDegrees) % 360
        } else {
            (sensorOrientation - deviceRotationDegrees + 360) % 360
        }
    }

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
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

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
