package com.example.cameraselect.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.example.cameraselect.model.CameraLensInfo
import com.example.cameraselect.model.CaptureMode
import com.example.cameraselect.model.FlashMode
import com.example.cameraselect.model.LensFacing
import com.example.cameraselect.model.PhotoFormat
import com.example.cameraselect.repository.CameraRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CameraViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CameraRepository(application)

    private val _allCameras = MutableStateFlow<List<CameraLensInfo>>(emptyList())
    val allCameras: StateFlow<List<CameraLensInfo>> = _allCameras.asStateFlow()

    private val _activeFacing = MutableStateFlow(LensFacing.FRONT)
    val activeFacing: StateFlow<LensFacing> = _activeFacing.asStateFlow()

    private val _showHiddenFront = MutableStateFlow(repository.getShowHidden(LensFacing.FRONT))
    val showHiddenFront: StateFlow<Boolean> = _showHiddenFront.asStateFlow()

    private val _showHiddenRear = MutableStateFlow(repository.getShowHidden(LensFacing.BACK))
    val showHiddenRear: StateFlow<Boolean> = _showHiddenRear.asStateFlow()

    private val _isMirroredFront = MutableStateFlow(repository.getIsMirrored(LensFacing.FRONT))
    val isMirroredFront: StateFlow<Boolean> = _isMirroredFront.asStateFlow()

    private val _isMirroredRear = MutableStateFlow(repository.getIsMirrored(LensFacing.BACK))
    val isMirroredRear: StateFlow<Boolean> = _isMirroredRear.asStateFlow()

    private val _noCropFront = MutableStateFlow(repository.getNoCrop(LensFacing.FRONT))
    val noCropFront: StateFlow<Boolean> = _noCropFront.asStateFlow()

    private val _noCropRear = MutableStateFlow(repository.getNoCrop(LensFacing.BACK))
    val noCropRear: StateFlow<Boolean> = _noCropRear.asStateFlow()

    private val _flashMode = MutableStateFlow(repository.getFlashMode())
    val flashMode: StateFlow<FlashMode> = _flashMode.asStateFlow()

    private val _photoFormat = MutableStateFlow(repository.getPhotoFormat())
    val photoFormat: StateFlow<PhotoFormat> = _photoFormat.asStateFlow()

    private val _enabledCameraIds = MutableStateFlow<Set<String>>(emptySet())
    val enabledCameraIds: StateFlow<Set<String>> = _enabledCameraIds.asStateFlow()

    private val _selectedCameraId = MutableStateFlow<String?>(null)
    val selectedCameraId: StateFlow<String?> = _selectedCameraId.asStateFlow()

    private val _isSettingsOpen = MutableStateFlow(false)
    val isSettingsOpen: StateFlow<Boolean> = _isSettingsOpen.asStateFlow()

    private val _captureMode = MutableStateFlow(CaptureMode.PHOTO)
    val captureMode: StateFlow<CaptureMode> = _captureMode.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationSeconds = MutableStateFlow(0)
    val recordingDurationSeconds: StateFlow<Int> = _recordingDurationSeconds.asStateFlow()

    private val _lastCapturedMediaUri = MutableStateFlow<Uri?>(null)
    val lastCapturedMediaUri: StateFlow<Uri?> = _lastCapturedMediaUri.asStateFlow()

    private val _isLastCapturedVideo = MutableStateFlow(false)
    val isLastCapturedVideo: StateFlow<Boolean> = _isLastCapturedVideo.asStateFlow()

    init {
        refreshCameras()
    }

    fun refreshCameras() {
        viewModelScope.launch {
            val cameras = repository.discoverAllCameras()
            _allCameras.value = cameras

            // Load or initialize enabled cameras
            val savedEnabled = repository.getEnabledCameraIds()
            if (savedEnabled != null) {
                _enabledCameraIds.value = savedEnabled
            } else {
                // Default: enable all standard (non-hidden) cameras
                val defaultEnabled = cameras.filter { !it.isHidden }.map { it.id }.toSet()
                _enabledCameraIds.value = defaultEnabled
                repository.saveEnabledCameraIds(defaultEnabled)
            }

            // Determine active camera ID if not selected
            updateSelectedCameraForFacing(_activeFacing.value)
        }
    }

    fun toggleActiveFacing() {
        val newFacing = if (_activeFacing.value == LensFacing.FRONT) LensFacing.BACK else LensFacing.FRONT
        _activeFacing.value = newFacing
        updateSelectedCameraForFacing(newFacing)
    }

    fun toggleShowHidden(facing: LensFacing) =
        toggleFacingFlag(facing, _showHiddenFront, _showHiddenRear, repository::setShowHidden)

    fun toggleIsMirrored(facing: LensFacing) =
        toggleFacingFlag(facing, _isMirroredFront, _isMirroredRear, repository::setIsMirrored)

    fun toggleNoCrop(facing: LensFacing) =
        toggleFacingFlag(facing, _noCropFront, _noCropRear, repository::setNoCrop)

    private fun toggleFacingFlag(
        facing: LensFacing,
        frontFlow: MutableStateFlow<Boolean>,
        rearFlow: MutableStateFlow<Boolean>,
        persist: (LensFacing, Boolean) -> Unit
    ) {
        val targetFlow = if (facing == LensFacing.FRONT) frontFlow else rearFlow
        val newValue = !targetFlow.value
        targetFlow.value = newValue
        persist(facing, newValue)
    }

    fun cycleFlashMode() {
        val nextMode = when (_flashMode.value) {
            FlashMode.AUTO -> FlashMode.OFF
            FlashMode.OFF -> FlashMode.ON
            FlashMode.ON -> FlashMode.TORCH
            FlashMode.TORCH -> FlashMode.AUTO
        }
        _flashMode.value = nextMode
        repository.setFlashMode(nextMode)
    }

    fun setPhotoFormat(format: PhotoFormat) {
        _photoFormat.value = format
        repository.setPhotoFormat(format)
    }

    fun toggleCameraEnabled(cameraId: String) {
        val current = _enabledCameraIds.value.toMutableSet()
        if (current.contains(cameraId)) {
            current.remove(cameraId)
        } else {
            current.add(cameraId)
        }
        _enabledCameraIds.value = current
        repository.saveEnabledCameraIds(current)

        // If selected camera was disabled, switch to a valid enabled camera
        val activeCamera = _allCameras.value.firstOrNull { it.id == _selectedCameraId.value }
        if (activeCamera != null && activeCamera.id == cameraId && !current.contains(cameraId)) {
            updateSelectedCameraForFacing(_activeFacing.value)
        }
    }

    fun selectCamera(cameraId: String) {
        _selectedCameraId.value = cameraId
        // Ensure active facing matches selected camera's facing
        _allCameras.value.firstOrNull { it.id == cameraId }?.let { info ->
            _activeFacing.value = info.lensFacing
        }
    }

    fun setSettingsOpen(open: Boolean) {
        _isSettingsOpen.value = open
    }

    fun setCaptureMode(mode: CaptureMode) {
        if (!_isRecording.value) {
            _captureMode.value = mode
        }
    }

    fun setIsRecording(isRecording: Boolean) {
        _isRecording.value = isRecording
        if (!isRecording) {
            _recordingDurationSeconds.value = 0
        }
    }

    fun setRecordingDurationSeconds(seconds: Int) {
        _recordingDurationSeconds.value = seconds
    }

    fun setLastCapturedMedia(uri: Uri?, isVideo: Boolean) {
        _lastCapturedMediaUri.value = uri
        _isLastCapturedVideo.value = isVideo
    }

    private fun updateSelectedCameraForFacing(facing: LensFacing) {
        val showHidden = if (facing == LensFacing.FRONT) _showHiddenFront.value else _showHiddenRear.value
        val facingCameras = _allCameras.value.filter {
            it.lensFacing == facing && (!it.isHidden || showHidden)
        }
        val enabledFacingCameras = facingCameras.filter { _enabledCameraIds.value.contains(it.id) }

        val candidate = enabledFacingCameras.firstOrNull { it.zoomLabel == "1x" || it.classification.equals("Main", ignoreCase = true) }
            ?: enabledFacingCameras.firstOrNull()
            ?: facingCameras.firstOrNull { it.zoomLabel == "1x" || it.classification.equals("Main", ignoreCase = true) }
            ?: facingCameras.firstOrNull()
            ?: _allCameras.value.firstOrNull { it.lensFacing == facing }

        candidate?.let {
            _selectedCameraId.value = it.id
        }
    }
}
