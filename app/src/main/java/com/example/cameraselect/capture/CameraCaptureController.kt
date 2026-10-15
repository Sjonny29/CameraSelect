package com.example.cameraselect.capture

import android.net.Uri
import androidx.compose.runtime.mutableStateOf

class CameraCaptureController {
    var takePhotoAction: ((rotationDegrees: Int, onCaptured: (Uri?) -> Unit) -> Unit)? = null
    var startRecordingAction: ((rotationDegrees: Int, onStarted: () -> Unit, onError: (String) -> Unit) -> Unit)? = null
    var stopRecordingAction: ((onFinished: (Uri?) -> Unit) -> Unit)? = null
    var isDarkSceneAction: (() -> Boolean)? = null

    val isDarkSceneState = mutableStateOf(false)

    fun takePhoto(rotationDegrees: Int, onCaptured: (Uri?) -> Unit) {
        takePhotoAction?.invoke(rotationDegrees, onCaptured)
    }

    fun startRecording(rotationDegrees: Int, onStarted: () -> Unit, onError: (String) -> Unit) {
        startRecordingAction?.invoke(rotationDegrees, onStarted, onError)
    }

    fun stopRecording(onFinished: (Uri?) -> Unit) {
        stopRecordingAction?.invoke(onFinished)
    }

    fun isDarkScene(): Boolean =
        isDarkSceneAction?.invoke() ?: isDarkSceneState.value
}
