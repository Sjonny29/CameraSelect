package com.example.cameraselect

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.media.MediaActionSound
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.cameraselect.model.FlashMode
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cameraselect.capture.CameraCaptureController
import com.example.cameraselect.capture.CameraCaptureManager
import com.example.cameraselect.model.CameraLensInfo
import com.example.cameraselect.model.CaptureMode
import com.example.cameraselect.model.LensFacing
import com.example.cameraselect.ui.CameraSettingsDialog
import com.example.cameraselect.ui.theme.CameraSelectTheme
import com.example.cameraselect.viewmodel.CameraViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.OptIn as KotlinOptIn

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CameraSelectTheme {
                CameraAppRoot()
            }
        }
    }
}

@KotlinOptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraAppRoot() {
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    )

    if (permissionsState.allPermissionsGranted) {
        CameraScreen()
    } else {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Camera and Audio permissions are required to use this app.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
                    Text("Grant Permissions")
                }
            }
        }
    }
}

@Composable
fun FlashOffCustomIcon(
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    val redSlashColor = if (isRecording) Color(0xFFFF3B30).copy(alpha = 0.35f) else Color(0xFFFF3B30)
    val whiteTint = if (isRecording) Color.White.copy(alpha = 0.35f) else Color.White

    Icon(
        imageVector = Icons.Default.FlashOn,
        contentDescription = "Flash Off",
        tint = whiteTint,
        modifier = modifier.drawWithContent {
            drawContent()
            val strokeWidth = 3.5.dp.toPx()
            drawLine(
                color = Color.Black.copy(alpha = if (isRecording) 0.35f else 0.85f),
                start = Offset(x = size.width * 0.28f, y = size.height * 0.18f),
                end = Offset(x = size.width * 0.72f, y = size.height * 0.82f),
                strokeWidth = strokeWidth + 3.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = redSlashColor,
                start = Offset(x = size.width * 0.28f, y = size.height * 0.18f),
                end = Offset(x = size.width * 0.72f, y = size.height * 0.82f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    )
}

@Composable
fun SimulatedScreenFlashOverlay(
    isScreenFlashActive: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    DisposableEffect(isScreenFlashActive) {
        val activity = context as? Activity
        if (activity != null && isScreenFlashActive) {
            val window = activity.window
            val params = window.attributes
            val previousBrightness = params.screenBrightness
            params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            window.attributes = params

            onDispose {
                params.screenBrightness = previousBrightness
                window.attributes = params
            }
        } else {
            onDispose {}
        }
    }

    if (isScreenFlashActive) {
        Canvas(modifier = modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Calculate border thickness for screen flash frame (~16% of screen dimension)
            val borderHoriz = minOf(w, h) * 0.16f
            val borderVert = minOf(w, h) * 0.16f

            // Frame Path: Outer screen rect minus inner rounded rect
            val framePath = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, w, h))
                addRoundRect(
                    RoundRect(
                        rect = Rect(borderHoriz, borderVert, w - borderHoriz, h - borderVert),
                        cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx())
                    )
                )
            }

            // Draw bright solid white outer illumination frame
            drawPath(path = framePath, color = Color.White)

            // Draw translucent inner preview window (12% white tint) so live camera feed remains clearly visible
            val innerTopLeft = Offset(borderHoriz, borderVert)
            val innerSize = androidx.compose.ui.geometry.Size(w - 2 * borderHoriz, h - 2 * borderVert)
            val cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx())

            drawRoundRect(
                color = Color.White.copy(alpha = 0.12f),
                topLeft = innerTopLeft,
                size = innerSize,
                cornerRadius = cornerRadius
            )

            // Inner subtle accent border
            drawRoundRect(
                color = Color.White.copy(alpha = 0.45f),
                topLeft = innerTopLeft,
                size = innerSize,
                cornerRadius = cornerRadius,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

@Composable
fun CameraScreen(viewModel: CameraViewModel = viewModel()) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val allCameras by viewModel.allCameras.collectAsStateWithLifecycle()
    val activeFacing by viewModel.activeFacing.collectAsStateWithLifecycle()
    val showHiddenFront by viewModel.showHiddenFront.collectAsStateWithLifecycle()
    val showHiddenRear by viewModel.showHiddenRear.collectAsStateWithLifecycle()
    val enabledCameraIds by viewModel.enabledCameraIds.collectAsStateWithLifecycle()
    val selectedCameraId by viewModel.selectedCameraId.collectAsStateWithLifecycle()
    val isSettingsOpen by viewModel.isSettingsOpen.collectAsStateWithLifecycle()

    val isMirroredFront by viewModel.isMirroredFront.collectAsStateWithLifecycle()
    val isMirroredRear by viewModel.isMirroredRear.collectAsStateWithLifecycle()

    val noCropFront by viewModel.noCropFront.collectAsStateWithLifecycle()
    val noCropRear by viewModel.noCropRear.collectAsStateWithLifecycle()

    val flashMode by viewModel.flashMode.collectAsStateWithLifecycle()

    val captureMode by viewModel.captureMode.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val recordingDurationSeconds by viewModel.recordingDurationSeconds.collectAsStateWithLifecycle()
    val lastCapturedMediaUri by viewModel.lastCapturedMediaUri.collectAsStateWithLifecycle()
    val isLastCapturedVideo by viewModel.isLastCapturedVideo.collectAsStateWithLifecycle()

    var hasPhysicalFlashForSelectedCamera by remember { mutableStateOf(false) }
    var isScreenFlashForCaptureActive by remember { mutableStateOf(false) }

    val buttonRotation = rememberDeviceRotation()
    val captureController = remember { CameraCaptureController() }

    val isTorchScreenFlashActive = (flashMode == FlashMode.TORCH) && !hasPhysicalFlashForSelectedCamera
    val isRecordingScreenFlashActive = isRecording && !hasPhysicalFlashForSelectedCamera && (
        flashMode == FlashMode.ON || flashMode == FlashMode.TORCH || (flashMode == FlashMode.AUTO && captureController.isDarkScene())
    )

    val isScreenFlashActive = isTorchScreenFlashActive ||
                              isRecordingScreenFlashActive ||
                              isScreenFlashForCaptureActive

    val currentFacingShowHidden = if (activeFacing == LensFacing.FRONT) showHiddenFront else showHiddenRear
    val currentFacingIsMirrored = if (activeFacing == LensFacing.FRONT) isMirroredFront else isMirroredRear
    val currentFacingNoCrop = if (activeFacing == LensFacing.FRONT) noCropFront else noCropRear

    // Shutter flash visual feedback
    var isShutterFlashVisible by remember { mutableStateOf(false) }
    val shutterFlashAlpha by animateFloatAsState(
        targetValue = if (isShutterFlashVisible) 0.85f else 0f,
        animationSpec = tween(durationMillis = 140),
        label = "shutterFlash"
    )

    // System camera shutter and recording sounds
    val mediaActionSound = remember {
        MediaActionSound().apply {
            load(MediaActionSound.SHUTTER_CLICK)
            load(MediaActionSound.START_VIDEO_RECORDING)
            load(MediaActionSound.STOP_VIDEO_RECORDING)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            mediaActionSound.release()
        }
    }

    // Recording duration timer coroutine
    LaunchedEffect(isRecording) {
        if (isRecording) {
            var seconds = 0
            viewModel.setRecordingDurationSeconds(0)
            while (isActive) {
                delay(1000)
                seconds++
                viewModel.setRecordingDurationSeconds(seconds)
            }
        } else {
            viewModel.setRecordingDurationSeconds(0)
        }
    }

    // Load gallery thumbnail bitmap for last captured photo or video
    var thumbnailBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(lastCapturedMediaUri) {
        val uri = lastCapturedMediaUri
        if (uri != null) {
            withContext(Dispatchers.IO) {
                try {
                    val bitmap = context.contentResolver.loadThumbnail(uri, Size(128, 128), null)
                    withContext(Dispatchers.Main) {
                        thumbnailBitmap = bitmap
                    }
                } catch (e: Exception) {
                    Log.e("CameraScreen", "Could not load thumbnail for $uri", e)
                }
            }
        }
    }

    // Filter cameras for zoom selector buttons on preview screen, sorted by focal length ascending
    val availableZoomCameras = remember(allCameras, activeFacing, enabledCameraIds, currentFacingShowHidden) {
        allCameras
            .filter {
                it.lensFacing == activeFacing &&
                enabledCameraIds.contains(it.id) &&
                (!it.isHidden || currentFacingShowHidden)
            }
            .sortedWith(
                compareBy<CameraLensInfo> { it.focalLengthActual }
                    .thenBy { it.focalLengthMm }
                    .thenBy { it.id.toIntOrNull() ?: Int.MAX_VALUE }
                    .thenBy { it.id }
            )
    }

    // Photo Capture action
    val onTakePhoto: () -> Unit = {
        val needsScreenFlash = !hasPhysicalFlashForSelectedCamera && when (flashMode) {
            FlashMode.OFF -> false
            FlashMode.ON -> true
            FlashMode.AUTO -> captureController.isDarkScene()
            FlashMode.TORCH -> true
        }

        coroutineScope.launch {
            if (needsScreenFlash) {
                isScreenFlashForCaptureActive = true
                delay(100) // Wait ~100ms for screen luminance to peak
            } else {
                isShutterFlashVisible = true
                coroutineScope.launch {
                    delay(100)
                    isShutterFlashVisible = false
                }
            }

            mediaActionSound.play(MediaActionSound.SHUTTER_CLICK)

            captureController.takePhoto(buttonRotation.toInt()) { uri ->
                if (needsScreenFlash) {
                    isScreenFlashForCaptureActive = false
                }
                if (uri != null) {
                    viewModel.setLastCapturedMedia(uri, isVideo = false)
                    Toast.makeText(context, "Photo saved", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to capture photo", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Video Recording Start action
    val onStartRecording: () -> Unit = {
        captureController.startRecording(
            rotationDegrees = buttonRotation.toInt(),
            onStarted = {
                mediaActionSound.play(MediaActionSound.START_VIDEO_RECORDING)
                viewModel.setIsRecording(true)
            },
            onError = { errorMsg ->
                Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Video Recording Stop action
    val onStopRecording: () -> Unit = {
        mediaActionSound.play(MediaActionSound.STOP_VIDEO_RECORDING)
        captureController.stopRecording { uri ->
            viewModel.setIsRecording(false)
            if (uri != null) {
                viewModel.setLastCapturedMedia(uri, isVideo = true)
                Toast.makeText(context, "Video saved", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Video could not be saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Gallery / Media Viewer viewer action
    val onOpenGallery: () -> Unit = {
        val uri = lastCapturedMediaUri
        if (uri != null) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, if (isLastCapturedVideo) "video/*" else "image/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.w("CameraScreen", "Could not view media, opening generic gallery", e)
                try {
                    val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                        data = uri
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallbackIntent)
                } catch (_: Exception) {
                    Toast.makeText(context, "Could not launch media player", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    type = "image/*"
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(context, "No gallery application found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Fullscreen Camera Preview
        selectedCameraId?.let { id ->
            Camera2PreviewView(
                cameraId = id,
                isMirrored = currentFacingIsMirrored,
                isNoCrop = currentFacingNoCrop,
                flashMode = flashMode,
                deviceRotationDegrees = buttonRotation.toInt(),
                captureController = captureController,
                onHasFlashChanged = { hasPhysicalFlash ->
                    hasPhysicalFlashForSelectedCamera = hasPhysicalFlash
                },
                modifier = Modifier.fillMaxSize()
            )
        } ?: Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No camera selected", color = Color.White)
        }

        // 2. Simulated Screen Flash Overlay (Snapchat style for cameras without physical flash)
        SimulatedScreenFlashOverlay(
            isScreenFlashActive = isScreenFlashActive,
            modifier = Modifier.fillMaxSize()
        )

        // 3. Shutter Snapshot White Flash Feedback Overlay
        if (shutterFlashAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = shutterFlashAlpha))
            )
        }

        // 4. Top-Left Flash Toggle Icon (Disabled while recording)
        IconButton(
            onClick = { if (!isRecording) viewModel.cycleFlashMode() },
            enabled = !isRecording,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(top = 16.dp, start = 16.dp)
                .shadow(8.dp, CircleShape)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                .size(56.dp)
                .rotate(buttonRotation)
        ) {
            when (flashMode) {
                FlashMode.AUTO -> {
                    Icon(
                        imageVector = Icons.Default.FlashAuto,
                        contentDescription = "Flash Auto",
                        tint = if (isRecording) Color.White.copy(alpha = 0.35f) else Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }
                FlashMode.OFF -> {
                    FlashOffCustomIcon(
                        isRecording = isRecording,
                        modifier = Modifier.size(44.dp)
                    )
                }
                FlashMode.ON -> {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = "Flash On",
                        tint = if (isRecording) Color.White.copy(alpha = 0.35f) else Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }
                FlashMode.TORCH -> {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = "Torch Light",
                        tint = if (isRecording) Color.White.copy(alpha = 0.35f) else Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
        }

        // 5. Top-Center Video Recording Timer Badge
        if (isRecording) {
            RecordingTimerBadge(
                durationSeconds = recordingDurationSeconds,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 18.dp)
                    .rotate(buttonRotation)
            )
        }

        // 6. Top-Right Settings (Cogwheel) Icon (Disabled while recording)
        IconButton(
            onClick = { if (!isRecording) viewModel.setSettingsOpen(true) },
            enabled = !isRecording,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 16.dp, end = 16.dp)
                .shadow(8.dp, CircleShape)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                .size(56.dp)
                .rotate(buttonRotation)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = if (isRecording) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(44.dp)
            )
        }

        // 7. Bottom Control Overlays (Zoom Buttons, Mode Switcher, Shutter Controls)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Zoom Lens Selection Buttons (Disabled during video recording)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (camera in availableZoomCameras) {
                    val isSelected = camera.id == selectedCameraId
                    LensZoomButton(
                        label = camera.zoomLabel,
                        isSelected = isSelected,
                        enabled = !isRecording,
                        onClick = { if (!isRecording) viewModel.selectCamera(camera.id) },
                        modifier = Modifier.rotate(buttonRotation)
                    )
                }
            }

            // Mode Selector Pill (PHOTO / VIDEO) - only displayed when not recording
            if (!isRecording) {
                CaptureModeSelector(
                    currentMode = captureMode,
                    onModeSelected = { mode -> viewModel.setCaptureMode(mode) },
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Main Shutter Controls Row (Gallery Thumbnail | Shutter Button | Camera Flip Switch)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Gallery / Last Captured Media Thumbnail Button
                GalleryThumbnailButton(
                    thumbnailBitmap = thumbnailBitmap,
                    isVideo = isLastCapturedVideo,
                    onClick = onOpenGallery,
                    modifier = Modifier.rotate(buttonRotation)
                )

                // Center: Capture Shutter Button
                CaptureShutterButton(
                    captureMode = captureMode,
                    isRecording = isRecording,
                    onPhotoClick = onTakePhoto,
                    onVideoRecordClick = onStartRecording,
                    onVideoStopClick = onStopRecording,
                    modifier = Modifier.rotate(buttonRotation)
                )

                // Right: Camera Switch / Flip Toggle
                IconButton(
                    onClick = { if (!isRecording) viewModel.toggleActiveFacing() },
                    enabled = !isRecording,
                    modifier = Modifier
                        .shadow(8.dp, CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                        .size(64.dp)
                        .rotate(buttonRotation)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch Camera",
                        tint = if (isRecording) Color.White.copy(alpha = 0.35f) else Color.White,
                        modifier = Modifier.size(52.dp)
                    )
                }
            }
        }

        // 8. Settings Overlay Dialog
        if (isSettingsOpen) {
            CameraSettingsDialog(
                facing = activeFacing,
                allCameras = allCameras,
                enabledCameraIds = enabledCameraIds,
                showHidden = currentFacingShowHidden,
                isMirrored = currentFacingIsMirrored,
                noCrop = currentFacingNoCrop,
                onToggleShowHidden = { viewModel.toggleShowHidden(activeFacing) },
                onToggleIsMirrored = { viewModel.toggleIsMirrored(activeFacing) },
                onToggleNoCrop = { viewModel.toggleNoCrop(activeFacing) },
                onToggleCameraEnabled = { id -> viewModel.toggleCameraEnabled(id) },
                onDismissRequest = { viewModel.setSettingsOpen(false) },
                rotationAngle = buttonRotation
            )
        }
    }
}

@Composable
fun CaptureModeSelector(
    currentMode: CaptureMode,
    onModeSelected: (CaptureMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.Black.copy(alpha = 0.45f),
        shadowElevation = 8.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CaptureModeItem(
                title = "PHOTO",
                isSelected = currentMode == CaptureMode.PHOTO,
                onClick = { onModeSelected(CaptureMode.PHOTO) }
            )
            CaptureModeItem(
                title = "VIDEO",
                isSelected = currentMode == CaptureMode.VIDEO,
                onClick = { onModeSelected(CaptureMode.VIDEO) }
            )
        }
    }
}

@Composable
private fun CaptureModeItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) Color(0xFFFFD700) else Color.Transparent,
        contentColor = if (isSelected) Color.Black else Color.White.copy(alpha = 0.85f),
        modifier = Modifier.padding(horizontal = 2.dp)
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun CaptureShutterButton(
    captureMode: CaptureMode,
    isRecording: Boolean,
    onPhotoClick: () -> Unit,
    onVideoRecordClick: () -> Unit,
    onVideoStopClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recordingPulse"
    )

    when (captureMode) {
        CaptureMode.PHOTO -> {
            Box(
                modifier = modifier
                    .shadow(8.dp, CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                    .size(80.dp)
                    .border(4.dp, Color.White, CircleShape)
                    .clickable(onClick = onPhotoClick),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(66.dp)
                        .background(Color.White, CircleShape)
                )
            }
        }
        CaptureMode.VIDEO -> {
            if (!isRecording) {
                Box(
                    modifier = modifier
                        .shadow(8.dp, CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                        .size(80.dp)
                        .border(4.dp, Color.White, CircleShape)
                        .clickable(onClick = onVideoRecordClick),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(66.dp)
                            .background(Color(0xFFE53935), CircleShape)
                    )
                }
            } else {
                Box(
                    modifier = modifier
                        .shadow(8.dp, CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                        .size((80 * pulseScale).dp)
                        .border(4.dp, Color(0xFFE53935), CircleShape)
                        .clickable(onClick = onVideoStopClick),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFFE53935), RoundedCornerShape(8.dp))
                    )
                }
            }
        }
    }
}

@Composable
fun GalleryThumbnailButton(
    thumbnailBitmap: Bitmap?,
    isVideo: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.5f),
        shadowElevation = 8.dp,
        modifier = modifier
            .size(56.dp)
            .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (thumbnailBitmap != null) {
                Image(
                    bitmap = thumbnailBitmap.asImageBitmap(),
                    contentDescription = "Last Captured Media",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (isVideo) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Video",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            } else {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = "Gallery",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun RecordingTimerBadge(
    durationSeconds: Int,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "timerDot")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.65f),
        shadowElevation = 8.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(Color.Red.copy(alpha = dotAlpha), CircleShape)
            )
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            Text(
                text = String.format(Locale.US, "%02d:%02d", minutes, seconds),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun LensZoomButton(
    label: String,
    isSelected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (isSelected) Color.White else Color.Black.copy(alpha = if (enabled) 0.45f else 0.2f),
        contentColor = if (isSelected) Color.Black else Color.White.copy(alpha = if (enabled) 1f else 0.4f),
        shadowElevation = 8.dp,
        modifier = modifier.size(if (isSelected) 60.dp else 52.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = if (isSelected) 18.sp else 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun rememberDeviceRotation(): Float {
    val context = LocalContext.current
    var targetAngle by remember { mutableFloatStateOf(0f) }

    DisposableEffect(context) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val targetOrientation = when (orientation) {
                    in 45..134 -> 270
                    in 135..224 -> 180
                    in 225..314 -> 90
                    else -> 0
                }
                targetAngle = getTargetRotationAngle(targetAngle, targetOrientation)
            }
        }
        if (listener.canDetectOrientation()) {
            listener.enable()
        }
        onDispose {
            listener.disable()
        }
    }

    val animatedAngle by animateFloatAsState(
        targetValue = targetAngle,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "buttonRotation"
    )

    return animatedAngle
}

private fun getTargetRotationAngle(currentAngle: Float, targetOrientation: Int): Float {
    val target = targetOrientation.toFloat()
    var diff = (target - (currentAngle % 360f)) % 360f
    if (diff > 180f) diff -= 360f
    if (diff < -180f) diff += 360f
    return currentAngle + diff
}

private fun applyFlashMode(
    builder: CaptureRequest.Builder,
    flashMode: FlashMode,
    hasFlash: Boolean,
    isStillCapture: Boolean = false,
    isDarkScene: Boolean = false
) {
    if (hasFlash) {
        when (flashMode) {
            FlashMode.OFF -> {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            }
            FlashMode.ON -> {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
                if (isStillCapture) {
                    builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE)
                } else {
                    builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                }
            }
            FlashMode.AUTO -> {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                if (isStillCapture) {
                    if (isDarkScene) {
                        builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE)
                    } else {
                        builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                    }
                } else {
                    builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                }
            }
            FlashMode.TORCH -> {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
            }
        }
    } else {
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
    }
}

private class CameraSessionHolder {
    var device: CameraDevice? = null
    var session: CameraCaptureSession? = null
    var previewRequestBuilder: CaptureRequest.Builder? = null
    var hasFlash: Boolean = false
    var flashMode: FlashMode = FlashMode.OFF
    var isDarkScene: Boolean = false
    var onDarkSceneChanged: ((Boolean) -> Unit)? = null
    var isTorchOnDuringRecording: Boolean = false
    private var lastLuminanceCheckTime: Long = 0L
    private var isLowLightLuminance: Boolean = false
    var sensorOrientation: Int = 90
    var previewSize: Size? = null
    var previewSurface: Surface? = null
    var textureView: TextureView? = null
    var imageReader: ImageReader? = null
    var backgroundThread: HandlerThread? = null
    var backgroundHandler: Handler? = null

    var mediaRecorder: MediaRecorder? = null
    var currentRecordingPfd: ParcelFileDescriptor? = null
    var currentRecordingUri: Uri? = null
    var isRecording: Boolean = false
    var recordingStartTime: Long = 0L

    var context: Context? = null
    var cameraId: String = ""
    var characteristics: CameraCharacteristics? = null

    var pendingPhotoCallback: ((ByteArray) -> Unit)? = null
    var isClosed: Boolean = false
    var isOpening: Boolean = false

    private fun checkFrameLuminance() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastLuminanceCheckTime > 300) {
            lastLuminanceCheckTime = now
            val view = textureView ?: return
            try {
                val bitmap = view.getBitmap(16, 16)
                if (bitmap != null) {
                    var totalLuminance = 0.0
                    val pixels = IntArray(256)
                    bitmap.getPixels(pixels, 0, 16, 0, 0, 16, 16)
                    bitmap.recycle()
                    for (p in pixels) {
                        val r = (p shr 16) and 0xFF
                        val g = (p shr 8) and 0xFF
                        val b = p and 0xFF
                        totalLuminance += (0.299 * r + 0.587 * g + 0.114 * b)
                    }
                    val avg = totalLuminance / 256.0
                    isLowLightLuminance = avg < 55.0
                }
            } catch (_: Exception) {}
        }
    }

    fun updateAEState(result: TotalCaptureResult) {
        val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
        val isFlashReq = (aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED)

        val exposureTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
        val iso = result.get(CaptureResult.SENSOR_SENSITIVITY)

        val isLowLightSensor = if (exposureTime != null && iso != null && exposureTime > 0) {
            val exposureMs = exposureTime / 1_000_000.0
            (exposureMs * iso) > 15000.0
        } else {
            false
        }

        checkFrameLuminance()

        val newDarkScene = isFlashReq || isLowLightSensor || isLowLightLuminance
        if (newDarkScene != isDarkScene) {
            isDarkScene = newDarkScene
            val ctx = context
            if (ctx != null) {
                ContextCompat.getMainExecutor(ctx).execute {
                    onDarkSceneChanged?.invoke(newDarkScene)
                }
            } else {
                onDarkSceneChanged?.invoke(newDarkScene)
            }
        }
    }

    fun onImageCaptured(bytes: ByteArray) {
        val cb = pendingPhotoCallback
        pendingPhotoCallback = null
        cb?.invoke(bytes)
    }

    fun updateFlash(mode: FlashMode) {
        flashMode = mode
        val builder = previewRequestBuilder ?: return
        val sess = session ?: return
        if (isClosed || isRecording) return
        try {
            applyFlashMode(builder, flashMode, hasFlash, isStillCapture = false, isDarkScene = isDarkScene)
            sess.setRepeatingRequest(
                builder.build(),
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        super.onCaptureCompleted(session, request, result)
                        updateAEState(result)
                    }
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            Log.e("Camera2Preview", "Error updating flash mode", e)
        }
    }

    fun takePhoto(
        isMirrored: Boolean,
        deviceRotationDegrees: Int,
        onCaptured: (Uri?) -> Unit
    ) {
        val dev = device
        val sess = session
        val reader = imageReader
        val chars = characteristics
        val ctx = context

        // For front-facing cameras, the preview is inherently mirrored by the
        // TextureView.  The isMirrored flag controls an *additional* flip in the
        // preview (via configureTransform).  The raw JPEG from the sensor, however,
        // is never mirrored, so we must invert the flag for front cameras so the
        // saved image matches what the user sees in the preview.
        val isFrontCamera = chars?.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        val captureMirrored = if (isFrontCamera) !isMirrored else isMirrored

        if (dev != null && sess != null && reader != null && chars != null && ctx != null) {
            val jpegOrientation = CameraCaptureManager.computeJpegOrientation(chars, deviceRotationDegrees)
            pendingPhotoCallback = { bytes ->
                val uri = CameraCaptureManager.saveJpegToMediaStore(ctx, bytes, captureMirrored, jpegOrientation)
                ContextCompat.getMainExecutor(ctx).execute {
                    onCaptured(uri)
                }
            }

            val doStillCapture = {
                try {
                    val captureBuilder = dev.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                    captureBuilder.addTarget(reader.surface)
                    captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    applyFlashMode(captureBuilder, flashMode, hasFlash, isStillCapture = true, isDarkScene = isDarkScene)
                    captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation)
                    captureBuilder.set(CaptureRequest.JPEG_QUALITY, 95.toByte())

                    sess.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            result: TotalCaptureResult
                        ) {
                            super.onCaptureCompleted(session, request, result)
                            if (hasFlash && (flashMode == FlashMode.ON || (flashMode == FlashMode.AUTO && isDarkScene))) {
                                try {
                                    val cancelBuilder = dev.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                        previewSurface?.let { addTarget(it) }
                                        applyFlashMode(this, flashMode, hasFlash, isStillCapture = false, isDarkScene = isDarkScene)
                                        set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL)
                                    }
                                    session.capture(cancelBuilder.build(), null, backgroundHandler)
                                } catch (e: Exception) {
                                    Log.w("Camera2Preview", "Failed to cancel AE precapture trigger", e)
                                }
                            }
                        }
                    }, backgroundHandler)
                } catch (e: Exception) {
                    Log.e("Camera2Preview", "Failed to capture still photo with ImageReader, trying fallback", e)
                    fallbackCaptureFromBitmap(isMirrored, deviceRotationDegrees, onCaptured)
                }
            }

            // Run precapture trigger when using hardware flash in ON or (AUTO and dark) mode
            val needsPrecapture = hasFlash && previewSurface != null && (
                flashMode == FlashMode.ON || (flashMode == FlashMode.AUTO && isDarkScene)
            )
            if (needsPrecapture) {
                try {
                    val precaptureBuilder = dev.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        previewSurface?.let { addTarget(it) }
                        applyFlashMode(this, flashMode, hasFlash, isStillCapture = false, isDarkScene = isDarkScene)
                        set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START)
                    }
                    var captured = false
                    sess.capture(precaptureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            result: TotalCaptureResult
                        ) {
                            super.onCaptureCompleted(session, request, result)
                            if (!captured) {
                                captured = true
                                doStillCapture()
                            }
                        }

                        override fun onCaptureFailed(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            failure: CaptureFailure
                        ) {
                            super.onCaptureFailed(session, request, failure)
                            if (!captured) {
                                captured = true
                                doStillCapture()
                            }
                        }
                    }, backgroundHandler)
                } catch (e: Exception) {
                    Log.e("Camera2Preview", "Error starting AE precapture trigger, proceeding with direct capture", e)
                    doStillCapture()
                }
            } else {
                doStillCapture()
            }
        } else {
            fallbackCaptureFromBitmap(isMirrored, deviceRotationDegrees, onCaptured)
        }
    }

    private fun fallbackCaptureFromBitmap(
        isMirrored: Boolean,
        deviceRotationDegrees: Int,
        onCaptured: (Uri?) -> Unit
    ) {
        val ctx = context ?: return onCaptured(null)
        val bitmap = textureView?.bitmap
        val chars = characteristics
        val rotationDegrees = if (chars != null) {
            CameraCaptureManager.computeJpegOrientation(chars, deviceRotationDegrees)
        } else 0

        // Same front-camera inversion as takePhoto – the bitmap from the
        // TextureView is already transformed by configureTransform (including
        // any mirror flip), so for front cameras we invert isMirrored.
        val isFrontCamera = chars?.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        val captureMirrored = if (isFrontCamera) !isMirrored else isMirrored

        if (bitmap != null) {
            backgroundHandler?.post {
                val uri = CameraCaptureManager.saveBitmapToMediaStore(ctx, bitmap, captureMirrored, rotationDegrees)
                ContextCompat.getMainExecutor(ctx).execute {
                    onCaptured(uri)
                }
            }
        } else {
            onCaptured(null)
        }
    }

    fun startRecording(
        deviceRotationDegrees: Int,
        onStarted: () -> Unit,
        onError: (String) -> Unit
    ) {
        val dev = device
        val chars = characteristics
        val ctx = context
        val prevSurface = previewSurface

        if (isRecording || dev == null || chars == null || ctx == null || prevSurface == null) {
            onError("Camera not ready for recording")
            return
        }

        val videoUriPair = CameraCaptureManager.createVideoUri(ctx)
        if (videoUriPair == null) {
            onError("Could not prepare video storage")
            return
        }
        val (videoUri, pfd) = videoUriPair

        val videoSize = CameraCaptureManager.chooseOptimalVideoSize(chars)
        val recorder = CameraCaptureManager.prepareMediaRecorder(ctx, pfd, chars, videoSize, deviceRotationDegrees)
        if (recorder == null) {
            try { pfd.close() } catch (_: Exception) {}
            CameraCaptureManager.discardVideo(ctx, videoUri)
            onError("Could not prepare video recorder")
            return
        }

        currentRecordingUri = videoUri
        currentRecordingPfd = pfd
        mediaRecorder = recorder

        try {
            session?.close()
            session = null

            val outputs = listOf(
                OutputConfiguration(prevSurface),
                OutputConfiguration(recorder.surface)
            )

            val sessionConfig = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                outputs,
                ContextCompat.getMainExecutor(ctx),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(newSession: CameraCaptureSession) {
                        if (isClosed) {
                            newSession.close()
                            return
                        }
                        session = newSession
                        try {
                            val recordBuilder = dev.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                            recordBuilder.addTarget(prevSurface)
                            recordBuilder.addTarget(recorder.surface)

                            val shouldTorch = hasFlash && (
                                flashMode == FlashMode.ON ||
                                flashMode == FlashMode.TORCH ||
                                (flashMode == FlashMode.AUTO && isDarkScene)
                            )

                            if (shouldTorch) {
                                recordBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                                recordBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                                isTorchOnDuringRecording = true
                            } else {
                                recordBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                                recordBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                                isTorchOnDuringRecording = false
                            }

                            val captureCallback = object : CameraCaptureSession.CaptureCallback() {
                                override fun onCaptureCompleted(
                                    session: CameraCaptureSession,
                                    request: CaptureRequest,
                                    result: TotalCaptureResult
                                ) {
                                    super.onCaptureCompleted(session, request, result)
                                    updateAEState(result)
                                    if (isRecording && hasFlash && flashMode == FlashMode.AUTO) {
                                        checkAndUpdateRecordingFlash(session, recordBuilder)
                                    }
                                }
                            }

                            newSession.setRepeatingRequest(recordBuilder.build(), captureCallback, backgroundHandler)
                            recorder.start()
                            isRecording = true
                            recordingStartTime = SystemClock.elapsedRealtime()
                            onStarted()
                        } catch (e: Exception) {
                            Log.e("Camera2Preview", "Failed to start media recorder", e)
                            onError("Failed to start recorder: ${e.message}")
                            cleanUpFailedRecording()
                            restartPreviewSession()
                        }
                    }

                    override fun onConfigureFailed(failedSession: CameraCaptureSession) {
                        onError("Video session configuration failed")
                        cleanUpFailedRecording()
                        restartPreviewSession()
                    }
                }
            )
            dev.createCaptureSession(sessionConfig)
        } catch (e: Exception) {
            Log.e("Camera2Preview", "Failed to create video capture session", e)
            onError("Failed to start video: ${e.message}")
            cleanUpFailedRecording()
            restartPreviewSession()
        }
    }

    private fun checkAndUpdateRecordingFlash(session: CameraCaptureSession, recordBuilder: CaptureRequest.Builder) {
        val shouldTorch = isDarkScene
        if (shouldTorch != isTorchOnDuringRecording) {
            isTorchOnDuringRecording = shouldTorch
            val flashValue = if (shouldTorch) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF
            recordBuilder.set(CaptureRequest.FLASH_MODE, flashValue)
            try {
                val captureCallback = object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        super.onCaptureCompleted(session, request, result)
                        updateAEState(result)
                        if (isRecording && hasFlash && flashMode == FlashMode.AUTO) {
                            checkAndUpdateRecordingFlash(session, recordBuilder)
                        }
                    }
                }
                session.setRepeatingRequest(recordBuilder.build(), captureCallback, backgroundHandler)
            } catch (e: Exception) {
                Log.e("Camera2Preview", "Failed to update torch during video recording", e)
            }
        }
    }

    private fun cleanUpFailedRecording() {
        isRecording = false
        try { mediaRecorder?.reset() } catch (_: Exception) {}
        try { mediaRecorder?.release() } catch (_: Exception) {}
        mediaRecorder = null
        try { currentRecordingPfd?.close() } catch (_: Exception) {}
        currentRecordingPfd = null
        currentRecordingUri?.let { uri ->
            context?.let { ctx -> CameraCaptureManager.discardVideo(ctx, uri) }
        }
        currentRecordingUri = null
    }

    fun stopRecording(onFinished: (Uri?) -> Unit) {
        if (!isRecording) {
            onFinished(null)
            return
        }

        val ctx = context
        val recorder = mediaRecorder
        val pfd = currentRecordingPfd
        val uri = currentRecordingUri

        isRecording = false

        backgroundHandler?.post {
            val elapsed = SystemClock.elapsedRealtime() - recordingStartTime
            if (elapsed < 1200) {
                try {
                    Thread.sleep(1200 - elapsed)
                } catch (_: InterruptedException) {}
            }

            var success = false
            try {
                recorder?.stop()
                success = true
            } catch (e: Exception) {
                Log.e("Camera2Preview", "MediaRecorder.stop() failed", e)
            } finally {
                try { recorder?.reset() } catch (_: Exception) {}
                try { recorder?.release() } catch (_: Exception) {}
                mediaRecorder = null
                try { pfd?.close() } catch (_: Exception) {}
                currentRecordingPfd = null
            }

            if (success && uri != null && ctx != null) {
                CameraCaptureManager.finalizeVideo(ctx, uri)
                ContextCompat.getMainExecutor(ctx).execute {
                    onFinished(uri)
                }
            } else {
                if (uri != null && ctx != null) {
                    CameraCaptureManager.discardVideo(ctx, uri)
                }
                if (ctx != null) {
                    ContextCompat.getMainExecutor(ctx).execute {
                        onFinished(null)
                    }
                }
            }
            currentRecordingUri = null

            restartPreviewSession()
        }
    }

    fun restartPreviewSession() {
        val dev = device ?: return
        val ctx = context ?: return
        val prevSurface = previewSurface ?: return
        if (isClosed) return

        try {
            session?.close()
            session = null

            val previewBuilder = dev.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewBuilder.addTarget(prevSurface)
            applyFlashMode(previewBuilder, flashMode, hasFlash, isStillCapture = false, isDarkScene = isDarkScene)
            previewRequestBuilder = previewBuilder

            val outputConfigs = mutableListOf(OutputConfiguration(prevSurface))
            imageReader?.let {
                outputConfigs.add(OutputConfiguration(it.surface))
            }

            val sessionConfig = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                outputConfigs,
                ContextCompat.getMainExecutor(ctx),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(newSession: CameraCaptureSession) {
                        if (isClosed) {
                            newSession.close()
                            return
                        }
                        try {
                            newSession.setRepeatingRequest(
                                previewBuilder.build(),
                                object : CameraCaptureSession.CaptureCallback() {
                                    override fun onCaptureCompleted(
                                        session: CameraCaptureSession,
                                        request: CaptureRequest,
                                        result: TotalCaptureResult
                                    ) {
                                        super.onCaptureCompleted(session, request, result)
                                        updateAEState(result)
                                    }
                                },
                                backgroundHandler
                            )
                            session = newSession
                        } catch (e: Exception) {
                            Log.e("Camera2Preview", "Failed to restart preview repeating request", e)
                        }
                    }

                    override fun onConfigureFailed(failedSession: CameraCaptureSession) {
                        Log.e("Camera2Preview", "Failed to restart preview session")
                    }
                }
            )
            dev.createCaptureSession(sessionConfig)
        } catch (e: Exception) {
            Log.e("Camera2Preview", "Failed to restart preview session", e)
        }
    }

    fun close() {
        isClosed = true
        isOpening = false
        if (isRecording) {
            try { mediaRecorder?.stop() } catch (_: Exception) {}
            try { mediaRecorder?.release() } catch (_: Exception) {}
            mediaRecorder = null
            try { currentRecordingPfd?.close() } catch (_: Exception) {}
            currentRecordingPfd = null
            currentRecordingUri?.let { uri ->
                context?.let { ctx -> CameraCaptureManager.finalizeVideo(ctx, uri) }
            }
            currentRecordingUri = null
            isRecording = false
        }

        try {
            session?.close()
        } catch (e: Exception) {
            Log.e("Camera2Preview", "Error closing capture session", e)
        } finally {
            session = null
            previewRequestBuilder = null
        }

        try {
            device?.close()
        } catch (e: Exception) {
            Log.e("Camera2Preview", "Error closing camera device", e)
        } finally {
            device = null
        }

        try {
            imageReader?.close()
        } catch (_: Exception) {}
        imageReader = null

        try {
            backgroundThread?.quitSafely()
        } catch (_: Exception) {}
        backgroundThread = null
        backgroundHandler = null

        textureView = null
        previewSurface = null
    }
}

@SuppressLint("MissingPermission")
@Composable
fun Camera2PreviewView(
    cameraId: String,
    isMirrored: Boolean,
    isNoCrop: Boolean,
    flashMode: FlashMode,
    deviceRotationDegrees: Int = 0,
    captureController: CameraCaptureController,
    onHasFlashChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cameraManager = remember { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }

    val hasFlash = remember(cameraId) {
        try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } catch (_: Exception) {
            false
        }
    }

    LaunchedEffect(hasFlash) {
        onHasFlashChanged(hasFlash)
    }

    key(cameraId, isNoCrop) {
        val holder = remember(cameraId, isNoCrop) { CameraSessionHolder() }

        DisposableEffect(holder, isMirrored) {
            holder.onDarkSceneChanged = { dark ->
                captureController.isDarkSceneState.value = dark
            }
            captureController.isDarkSceneState.value = holder.isDarkScene
            captureController.takePhotoAction = { rotationDegrees, onCaptured ->
                holder.takePhoto(isMirrored, rotationDegrees, onCaptured)
            }
            captureController.startRecordingAction = { rotationDegrees, onStarted, onError ->
                holder.startRecording(rotationDegrees, onStarted, onError)
            }
            captureController.stopRecordingAction = { onFinished ->
                holder.stopRecording(onFinished)
            }
            captureController.isDarkSceneAction = {
                holder.isDarkScene
            }
            onDispose {
                holder.onDarkSceneChanged = null
                captureController.takePhotoAction = null
                captureController.startRecordingAction = null
                captureController.stopRecordingAction = null
                captureController.isDarkSceneAction = null
            }
        }

        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                            openCameraSession(ctx, cameraManager, cameraId, isMirrored, isNoCrop, flashMode, this@apply, surface, width, height, holder, deviceRotationDegrees)
                        }

                        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                            holder.previewSize?.let { previewSize ->
                                configureTransform(isMirrored, isNoCrop, this@apply, width, height, previewSize, holder.sensorOrientation, deviceRotationDegrees)
                            }
                        }

                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                            holder.close()
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                    }

                    if (isAvailable && width > 0 && height > 0) {
                        val surface = surfaceTexture
                        if (surface != null) {
                            openCameraSession(ctx, cameraManager, cameraId, isMirrored, isNoCrop, flashMode, this, surface, width, height, holder, deviceRotationDegrees)
                        }
                    }
                }
            },
            update = { textureView ->
                if (textureView.width > 0 && textureView.height > 0) {
                    holder.previewSize?.let { previewSize ->
                        configureTransform(isMirrored, isNoCrop, textureView, textureView.width, textureView.height, previewSize, holder.sensorOrientation, deviceRotationDegrees)
                    }
                    holder.updateFlash(flashMode)
                }
            },
            modifier = modifier
        )

        DisposableEffect(cameraId, isNoCrop) {
            onDispose {
                holder.close()
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun openCameraSession(
    context: Context,
    cameraManager: CameraManager,
    cameraId: String,
    isMirrored: Boolean,
    isNoCrop: Boolean,
    flashMode: FlashMode,
    textureView: TextureView,
    surfaceTexture: SurfaceTexture,
    width: Int,
    height: Int,
    holder: CameraSessionHolder,
    deviceRotationDegrees: Int = 0
) {
    if (holder.isClosed || holder.isOpening || holder.device != null) return
    holder.isOpening = true
    holder.context = context
    holder.cameraId = cameraId
    holder.flashMode = flashMode
    holder.textureView = textureView

    try {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        holder.characteristics = characteristics

        val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        holder.hasFlash = hasFlash

        val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
        holder.sensorOrientation = sensorOrientation

        val previewSize = chooseOptimalPreviewSize(cameraManager, cameraId, width, height, isNoCrop)
        holder.previewSize = previewSize
        surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)

        configureTransform(isMirrored, isNoCrop, textureView, width, height, previewSize, sensorOrientation, deviceRotationDegrees)

        val previewSurface = Surface(surfaceTexture)
        holder.previewSurface = previewSurface

        val bgThread = HandlerThread("CameraBackground").apply { start() }
        val bgHandler = Handler(bgThread.looper)
        holder.backgroundThread = bgThread
        holder.backgroundHandler = bgHandler

        val captureSize = CameraCaptureManager.chooseOptimalCaptureSize(characteristics, previewSize)
        val reader = try {
            ImageReader.newInstance(captureSize.width, captureSize.height, ImageFormat.JPEG, 2).apply {
                setOnImageAvailableListener({ ir ->
                    val image = ir.acquireLatestImage() ?: return@setOnImageAvailableListener
                    try {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        holder.onImageCaptured(bytes)
                    } finally {
                        image.close()
                    }
                }, bgHandler)
            }
        } catch (e: Exception) {
            Log.w("Camera2Preview", "Could not create ImageReader for camera $cameraId", e)
            null
        }
        holder.imageReader = reader

        cameraManager.openCamera(
            cameraId,
            ContextCompat.getMainExecutor(context),
            object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    holder.isOpening = false
                    if (holder.isClosed) {
                        camera.close()
                        return
                    }
                    holder.device = camera

                    try {
                        val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                        requestBuilder.addTarget(previewSurface)

                        val currentFlashMode = holder.flashMode
                        applyFlashMode(requestBuilder, currentFlashMode, hasFlash, isStillCapture = false, isDarkScene = holder.isDarkScene)
                        holder.previewRequestBuilder = requestBuilder

                        val outputConfigs = mutableListOf(OutputConfiguration(previewSurface))
                        reader?.let {
                            outputConfigs.add(OutputConfiguration(it.surface))
                        }

                        val sessionConfig = SessionConfiguration(
                            SessionConfiguration.SESSION_REGULAR,
                            outputConfigs,
                            ContextCompat.getMainExecutor(context),
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    if (holder.isClosed) {
                                        session.close()
                                        return
                                    }
                                    try {
                                        session.setRepeatingRequest(
                                            requestBuilder.build(),
                                            object : CameraCaptureSession.CaptureCallback() {
                                                override fun onCaptureCompleted(
                                                    session: CameraCaptureSession,
                                                    request: CaptureRequest,
                                                    result: TotalCaptureResult
                                                ) {
                                                    super.onCaptureCompleted(session, request, result)
                                                    holder.updateAEState(result)
                                                }
                                            },
                                            bgHandler
                                        )
                                        holder.session = session
                                    } catch (e: Exception) {
                                        Log.e("Camera2Preview", "Failed to start repeating request for camera $cameraId", e)
                                    }
                                }

                                override fun onConfigureFailed(session: CameraCaptureSession) {
                                    Log.e("Camera2Preview", "Session configuration failed for camera $cameraId")
                                    Toast.makeText(context, "Camera session failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        camera.createCaptureSession(sessionConfig)
                    } catch (e: Exception) {
                        Log.e("Camera2Preview", "Failed to create capture session for camera $cameraId", e)
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    holder.isOpening = false
                    camera.close()
                    if (holder.device == camera) {
                        holder.device = null
                    }
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    holder.isOpening = false
                    camera.close()
                    if (holder.device == camera) {
                        holder.device = null
                    }
                    Toast.makeText(context, "Camera error $error", Toast.LENGTH_SHORT).show()
                }
            }
        )
    } catch (e: Exception) {
        holder.isOpening = false
        Log.e("Camera2Preview", "Failed to open camera $cameraId", e)
    }
}

private fun configureTransform(
    isMirrored: Boolean,
    isNoCrop: Boolean,
    textureView: TextureView,
    viewWidth: Int,
    viewHeight: Int,
    previewSize: Size,
    sensorOrientation: Int = 90,
    deviceRotationDegrees: Int = 0
) {
    if (viewWidth <= 0 || viewHeight <= 0 || previewSize.width <= 0 || previewSize.height <= 0) return

    val isPortrait = viewHeight > viewWidth

    val bufferW = maxOf(previewSize.width, previewSize.height).toFloat()
    val bufferH = minOf(previewSize.width, previewSize.height).toFloat()

    val isRotated90or270 = (sensorOrientation == 90 || sensorOrientation == 270)

    val orientedWidth: Float
    val orientedHeight: Float

    if (isPortrait) {
        if (isRotated90or270) {
            orientedWidth = bufferH
            orientedHeight = bufferW
        } else {
            orientedWidth = bufferW
            orientedHeight = bufferH
        }
    } else {
        if (isRotated90or270) {
            orientedWidth = bufferW
            orientedHeight = bufferH
        } else {
            orientedWidth = bufferH
            orientedHeight = bufferW
        }
    }

    val scaleS = if (isNoCrop) {
        minOf(
            viewWidth.toFloat() / orientedWidth,
            viewHeight.toFloat() / orientedHeight
        )
    } else {
        maxOf(
            viewWidth.toFloat() / orientedWidth,
            viewHeight.toFloat() / orientedHeight
        )
    }

    val scaleX = (orientedWidth * scaleS) / viewWidth.toFloat()
    val scaleY = (orientedHeight * scaleS) / viewHeight.toFloat()

    val centerX = viewWidth / 2f
    val centerY = viewHeight / 2f

    val matrix = Matrix()
    matrix.setScale(scaleX, scaleY, centerX, centerY)

    if (isMirrored) {
        val normalizedRotation = ((deviceRotationDegrees % 360) + 360) % 360
        val isLandscape = normalizedRotation == 90 || normalizedRotation == 270
        if (isLandscape) {
            matrix.postScale(1f, -1f, centerX, centerY)
        } else {
            matrix.postScale(-1f, 1f, centerX, centerY)
        }
    }

    textureView.setTransform(matrix)
}

private fun chooseOptimalPreviewSize(
    cameraManager: CameraManager,
    cameraId: String,
    width: Int,
    height: Int,
    isNoCrop: Boolean
): Size {
    return try {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val choices = map?.getOutputSizes(SurfaceTexture::class.java)

        if (choices.isNullOrEmpty()) {
            return Size(1920, 1080)
        }

        val targetArea = if (width > 0 && height > 0) width * height else 1920 * 1080

        if (isNoCrop) {
            val activeArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            val pixelArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
            val sensorW = activeArray?.width() ?: pixelArray?.width ?: 0
            val sensorH = activeArray?.height() ?: pixelArray?.height ?: 0

            val sensorRatio = if (sensorW > 0 && sensorH > 0) {
                maxOf(sensorW, sensorH).toFloat() / minOf(sensorW, sensorH).toFloat()
            } else {
                16f / 9f
            }

            val matchingRatioChoices = choices.filter { size ->
                val sizeRatio = maxOf(size.width, size.height).toFloat() / minOf(size.width, size.height).toFloat()
                kotlin.math.abs(sizeRatio - sensorRatio) < 0.08f
            }

            if (matchingRatioChoices.isNotEmpty()) {
                matchingRatioChoices.minByOrNull { size ->
                    kotlin.math.abs(size.width * size.height - targetArea)
                } ?: matchingRatioChoices[0]
            } else {
                choices.minByOrNull { size ->
                    val sizeRatio = maxOf(size.width, size.height).toFloat() / minOf(size.width, size.height).toFloat()
                    kotlin.math.abs(sizeRatio - sensorRatio)
                } ?: choices[0]
            }
        } else {
            choices.minByOrNull { size ->
                kotlin.math.abs(size.width * size.height - targetArea)
            } ?: choices[0]
        }
    } catch (_: Exception) {
        Size(1920, 1080)
    }
}
