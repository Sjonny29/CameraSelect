package com.example.cameraselect.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cameraselect.model.CameraLensInfo
import com.example.cameraselect.model.LensFacing
import com.example.cameraselect.ui.theme.CameraSelectTheme

private fun Modifier.drawScrollbar(
    scrollState: ScrollState,
    color: Color = Color(0xFF38ACFA),
    width: Dp = 4.dp
): Modifier = drawWithContent {
    drawContent()

    val maxScroll = scrollState.maxValue
    if (maxScroll > 0) {
        val visibleHeight = size.height
        val totalHeight = maxScroll.toFloat() + visibleHeight
        val scrollbarHeight = (visibleHeight / totalHeight * visibleHeight).coerceAtLeast(24.dp.toPx())
        val scrollFraction = scrollState.value.toFloat() / maxScroll.toFloat()
        val scrollbarOffsetY = scrollFraction * (visibleHeight - scrollbarHeight)

        drawRoundRect(
            color = color,
            topLeft = Offset(size.width - width.toPx() - 2.dp.toPx(), scrollbarOffsetY),
            size = Size(width.toPx(), scrollbarHeight),
            cornerRadius = CornerRadius(width.toPx() / 2f, width.toPx() / 2f)
        )
    }
}

@Composable
fun CameraSettingsDialog(
    facing: LensFacing,
    allCameras: List<CameraLensInfo>,
    enabledCameraIds: Set<String>,
    showHidden: Boolean,
    isMirrored: Boolean,
    noCrop: Boolean,
    onToggleShowHidden: () -> Unit,
    onToggleIsMirrored: () -> Unit,
    onToggleNoCrop: () -> Unit,
    onToggleCameraEnabled: (String) -> Unit,
    onDismissRequest: () -> Unit,
    rotationAngle: Float = 0f
) {
    val facingCameras = allCameras.filter { it.lensFacing == facing }
    val standardCameras = facingCameras.filter { !it.isHidden }
    val hiddenCameras = facingCameras.filter { it.isHidden }

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    val isLandscape = kotlin.math.abs(rotationAngle % 180f) in 45f..135f

    // Card dimensions relative to device orientation:
    // The app is portrait-locked, so screenWidth is always the narrow side and
    // screenHeight the long side.  When the card is rotated 90°/270° its
    // unrotated width maps to the visual height and its unrotated height maps
    // to the visual width.  We therefore size the unrotated height (→ visual
    // width after rotation) against the narrow side, and the unrotated width
    // (→ visual height after rotation) against the long side.
    val dialogWidth = if (isLandscape) screenHeight * 0.72f else screenWidth * 0.88f
    val dialogMaxHeight = if (isLandscape) screenWidth * 0.80f else screenHeight * 0.82f

    val scrollState = rememberScrollState()

    // Fullscreen Overlay Box (captures taps outside the dialog card to dismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onDismissRequest),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .clickable(enabled = false, onClick = {}) // Prevent taps on card from dismissing overlay
                .width(dialogWidth)
                .heightIn(max = dialogMaxHeight)
                .rotate(rotationAngle),
            shape = RoundedCornerShape(32.dp),
            color = Color.White,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .drawScrollbar(scrollState)
                    .verticalScroll(scrollState)
                    .padding(
                        horizontal = if (isLandscape) 18.dp else 24.dp,
                        vertical = if (isLandscape) 18.dp else 28.dp
                    )
            ) {
                // Title: Front or Rear
                Text(
                    text = if (facing == LensFacing.FRONT) "Front" else "Rear",
                    fontSize = if (isLandscape) 20.sp else 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = if (isLandscape) 12.dp else 20.dp)
                )

                // 1. Standard Lenses List
                for (camera in standardCameras) {
                    CameraSettingItem(
                        camera = camera,
                        isChecked = enabledCameraIds.contains(camera.id),
                        onToggle = { onToggleCameraEnabled(camera.id) },
                        isLandscape = isLandscape
                    )
                }

                Spacer(modifier = Modifier.height(if (isLandscape) 6.dp else 12.dp))

                // 2. Mirror image Toggle Row
                SettingToggleRow(
                    label = "Mirror image",
                    checked = isMirrored,
                    onToggle = onToggleIsMirrored,
                    isLandscape = isLandscape
                )

                // 3. No camera crop Toggle Row
                SettingToggleRow(
                    label = "No camera crop",
                    checked = noCrop,
                    onToggle = onToggleNoCrop,
                    isLandscape = isLandscape
                )

                // 4. Show hidden lenses Toggle Row
                SettingToggleRow(
                    label = "Show hidden lenses",
                    checked = showHidden,
                    onToggle = onToggleShowHidden,
                    isLandscape = isLandscape
                )

                // 5. Hidden Lenses List (Shown when showHidden is true)
                if (showHidden) {
                    Spacer(modifier = Modifier.height(if (isLandscape) 4.dp else 8.dp))
                    if (hiddenCameras.isNotEmpty()) {
                        for (camera in hiddenCameras) {
                            CameraSettingItem(
                                camera = camera,
                                isChecked = enabledCameraIds.contains(camera.id),
                                onToggle = { onToggleCameraEnabled(camera.id) },
                                isLandscape = isLandscape
                            )
                        }
                    } else {
                        Text(
                            text = "No hidden lenses detected on this device",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    isLandscape: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (isLandscape) 4.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = if (isLandscape) 16.sp else 20.sp,
            fontWeight = FontWeight.Normal,
            color = Color.Black
        )
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF38ACFA),
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFE0E0E0)
            )
        )
    }
}

@Composable
private fun CameraSettingItem(
    camera: CameraLensInfo,
    isChecked: Boolean,
    onToggle: () -> Unit,
    isLandscape: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = if (isLandscape) 4.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFF4CAF50),
                uncheckedColor = Color.Gray,
                checkmarkColor = Color.White
            )
        )
        Spacer(modifier = Modifier.width(if (isLandscape) 6.dp else 8.dp))
        Text(
            text = camera.displayName,
            fontSize = if (isLandscape) 15.sp else 18.sp,
            color = Color.Black
        )
    }
}

@Preview
@Composable
fun FrontCameraSettingsDialogPreview() {
    CameraSelectTheme {
        CameraSettingsDialog(
            facing = LensFacing.FRONT,
            allCameras = listOf(
                CameraLensInfo(
                    id = "4",
                    lensFacing = LensFacing.FRONT,
                    isHidden = false,
                    zoomLabel = "1x",
                    classification = "Main",
                    focalLengthMm = 30,
                    focalLengthActual = 30f,
                    displayName = "1x ID4 Main Focal length 30mm"
                )
            ),
            enabledCameraIds = setOf("4"),
            showHidden = true,
            isMirrored = true,
            noCrop = false,
            onToggleShowHidden = {},
            onToggleIsMirrored = {},
            onToggleNoCrop = {},
            onToggleCameraEnabled = {},
            onDismissRequest = {}
        )
    }
}

@Preview
@Composable
fun RearCameraSettingsDialogPreview() {
    CameraSelectTheme {
        CameraSettingsDialog(
            facing = LensFacing.BACK,
            allCameras = listOf(
                CameraLensInfo(
                    id = "1",
                    lensFacing = LensFacing.BACK,
                    isHidden = false,
                    zoomLabel = "1x",
                    classification = "Main",
                    focalLengthMm = 35,
                    focalLengthActual = 35f,
                    displayName = "1x ID1 Main Focal length 35mm"
                ),
                CameraLensInfo(
                    id = "2",
                    lensFacing = LensFacing.BACK,
                    isHidden = true,
                    zoomLabel = "0.6x",
                    classification = "Ultra wide",
                    focalLengthMm = 20,
                    focalLengthActual = 20f,
                    displayName = "0.6x ID2 Ultra wide Focal length 20mm"
                ),
                CameraLensInfo(
                    id = "3",
                    lensFacing = LensFacing.BACK,
                    isHidden = true,
                    zoomLabel = "2.5x",
                    classification = "Telephoto",
                    focalLengthMm = 50,
                    focalLengthActual = 50f,
                    displayName = "2.5x ID3 Telephoto Focal length 50mm"
                )
            ),
            enabledCameraIds = setOf("1", "2", "3"),
            showHidden = true,
            isMirrored = false,
            noCrop = false,
            onToggleShowHidden = {},
            onToggleIsMirrored = {},
            onToggleNoCrop = {},
            onToggleCameraEnabled = {},
            onDismissRequest = {}
        )
    }
}
