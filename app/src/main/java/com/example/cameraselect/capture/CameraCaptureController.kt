package com.example.cameraselect.capture

import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import com.example.cameraselect.model.ManualCameraSettings
import com.example.cameraselect.model.ManualCapabilities

class CameraCaptureController {
    var takePhotoAction: ((rotationDegrees: Int, saveRaw: Boolean, onCaptured: (Uri?, Uri?) -> Unit) -> Boolean)? = null
    var startRecordingAction: ((rotationDegrees: Int, onStarted: () -> Unit, onError: (String) -> Unit) -> Unit)? = null
    var stopRecordingAction: ((onFinished: (Uri?) -> Unit) -> Unit)? = null
    var isDarkSceneAction: (() -> Boolean)? = null
    var tapToFocusAction: ((x: Float, y: Float) -> Boolean)? = null

    val isDarkSceneState = mutableStateOf(false)
    val isFocusLockedState = mutableStateOf(false)

    /** Manual values selected in Pro mode. Lives here so they survive mode toggles. */
    val manualSettingsState = mutableStateOf(ManualCameraSettings())

    /** Manual capabilities of the lens that is currently open. */
    val manualCapabilitiesState = mutableStateOf(ManualCapabilities.UNSUPPORTED)

    /** Latest metered values, used by the AUTO readout and to lock a wheel. */
    val liveIsoState = mutableStateOf<Int?>(null)
    val liveShutterState = mutableStateOf<Long?>(null)
    val liveFocusState = mutableStateOf<Float?>(null)

    fun takePhoto(rotationDegrees: Int, saveRaw: Boolean, onCaptured: (Uri?, Uri?) -> Unit): Boolean =
        takePhotoAction?.invoke(rotationDegrees, saveRaw, onCaptured) ?: false

    fun startRecording(rotationDegrees: Int, onStarted: () -> Unit, onError: (String) -> Unit) {
        startRecordingAction?.invoke(rotationDegrees, onStarted, onError)
    }

    fun stopRecording(onFinished: (Uri?) -> Unit) {
        stopRecordingAction?.invoke(onFinished)
    }

    fun isDarkScene(): Boolean =
        isDarkSceneAction?.invoke() ?: isDarkSceneState.value

    fun tapToFocus(x: Float, y: Float): Boolean =
        tapToFocusAction?.invoke(x, y) ?: false
}
