package com.example.cameraselect.ui.pro

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cameraselect.capture.CameraCaptureController
import com.example.cameraselect.model.ManualCameraSettings
import com.example.cameraselect.model.ManualCameraValues
import com.example.cameraselect.model.ManualCapabilities
import com.example.cameraselect.model.ManualControlTarget

val ProYellow = Color(0xFFFFD700)

/**
 * Compact pills showing the live ISO, shutter, focus values, and a reset button.
 * Tapping a control pill opens (or closes) its wheel; values stream from CaptureResult
 * while exposure/focus are automatic. Tapping RESET resets all manual settings to AUTO.
 *
 * The pills themselves stay screen-aligned like the rest of the dock; only their
 * contents follow the device orientation so the labels stay upright while the
 * phone is held in landscape.
 */
@Composable
fun ProCameraDock(
    controller: CameraCaptureController,
    settings: ManualCameraSettings,
    capabilities: ManualCapabilities,
    openControl: ManualControlTarget?,
    onControlClick: (ManualControlTarget) -> Unit,
    modifier: Modifier = Modifier,
    rotationDegrees: Float = 0f,
    onResetClick: () -> Unit = {},
) {
    val liveIso = controller.liveIsoState.value
    val liveShutter = controller.liveShutterState.value
    val liveFocus = controller.liveFocusState.value

    val manualExposure = (settings.manualExposure && capabilities.supportsManualSensor) &&
        (settings.iso != null && settings.shutterNs != null)
    val manualFocus = settings.manualFocus && capabilities.supportsManualFocus && (settings.focusDioptres != null)

    val hasManualSettings = manualExposure || manualFocus

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth()) {
            // Fixed height sized for the longest pill content. In landscape this
            // height becomes the pill's visual width, so it must fit labels like
            // "1/64000" (~43dp) and "102400" (~39dp) with room to spare: 68dp
            // leaves ~66dp of reading length, which keeps the full shutter value
            // legible (roughly three digits more than the old 48dp) even when the
            // system font scale is raised.
            val dockHeight = 68.dp

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dockHeight),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProDockPill(
                    title = "ISO",
                    value = if (manualExposure) {
                        ManualCameraValues.formatIso(settings.iso)
                    } else {
                        ManualCameraValues.formatIso(liveIso ?: settings.iso)
                    },
                    isActive = openControl == ManualControlTarget.ISO,
                    enabled = capabilities.supportsIso,
                    isAuto = !manualExposure,
                    onClick = { onControlClick(ManualControlTarget.ISO) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentRotationDegrees = rotationDegrees
                )
                ProDockPill(
                    title = "SHUTTER",
                    value = if (manualExposure) {
                        ManualCameraValues.formatShutter(settings.shutterNs)
                    } else {
                        ManualCameraValues.formatShutter(liveShutter ?: settings.shutterNs)
                    },
                    isActive = openControl == ManualControlTarget.SHUTTER,
                    enabled = capabilities.supportsShutter,
                    isAuto = !manualExposure,
                    onClick = { onControlClick(ManualControlTarget.SHUTTER) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentRotationDegrees = rotationDegrees
                )
                ProDockPill(
                    title = "FOCUS",
                    value = ManualCameraValues.formatFocus(
                        if (manualFocus) settings.focusDioptres else (liveFocus ?: settings.focusDioptres)
                    ),
                    isActive = openControl == ManualControlTarget.FOCUS,
                    enabled = capabilities.supportsManualFocus,
                    isAuto = !manualFocus,
                    onClick = { onControlClick(ManualControlTarget.FOCUS) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentRotationDegrees = rotationDegrees
                )
                ProResetPill(
                    enabled = capabilities.supportsManualSensor || capabilities.supportsManualFocus,
                    hasManualSettings = hasManualSettings,
                    onClick = onResetClick,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentRotationDegrees = rotationDegrees
                )
            }
        }
    }
}

private fun isSidewaysRotation(rotationDegrees: Float): Boolean {
    val normalized = ((rotationDegrees % 180f) + 180f) % 180f
    return normalized in 45f..135f
}

private fun Modifier.rotatedContentLayout(rotationDegrees: Float): Modifier = this
    .layout { measurable, constraints ->
        val sideways = isSidewaysRotation(rotationDegrees)
        if (!sideways) {
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                placeable.place(0, 0)
            }
        } else {
            val swappedConstraints = constraints.copy(
                minWidth = constraints.minHeight,
                maxWidth = constraints.maxHeight,
                minHeight = constraints.minWidth,
                maxHeight = constraints.maxWidth,
            )
            val placeable = measurable.measure(swappedConstraints)
            layout(placeable.height, placeable.width) {
                placeable.place(
                    (placeable.height - placeable.width) / 2,
                    (placeable.width - placeable.height) / 2
                )
            }
        }
    }
    .rotate(rotationDegrees)

@Composable
private fun ProDockPill(
    title: String,
    value: String,
    isActive: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isAuto: Boolean = false,
    contentRotationDegrees: Float = 0f,
) {
    val contentColor = when {
        isActive -> Color.Black
        enabled -> Color.White
        else -> Color.White.copy(alpha = 0.35f)
    }
    // Portrait stacks three lines inside a 38dp pill, so it needs explicit tight
    // leading: the inherited bodyLarge line height (24sp) would make the block
    // ~72dp tall and clip it. Landscape keeps the inherited leading, which is
    // what spreads the three rotated labels evenly across the pill.
    val sideways = isSidewaysRotation(contentRotationDegrees)

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = when {
            isActive -> ProYellow
            enabled -> Color.Black.copy(alpha = 0.55f)
            else -> Color.Black.copy(alpha = 0.3f)
        },
        contentColor = contentColor,
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) ProYellow else Color.White.copy(alpha = if (enabled) 0.22f else 0.1f)
        ),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 2.dp, vertical = 1.dp)
                    .rotatedContentLayout(contentRotationDegrees),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    fontSize = 7.5.sp,
                    lineHeight = if (sideways) TextUnit.Unspecified else 8.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                    color = contentColor.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
                if (isAuto && enabled) {
                    Text(
                        text = "AUTO",
                        fontSize = 7.sp,
                        lineHeight = if (sideways) TextUnit.Unspecified else 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = if (isActive) Color.Black else ProYellow,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }
                Text(
                    text = value,
                    fontSize = 11.5.sp,
                    lineHeight = if (sideways) TextUnit.Unspecified else 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}

@Composable
private fun ProResetPill(
    enabled: Boolean,
    hasManualSettings: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentRotationDegrees: Float = 0f,
) {
    val contentColor = when {
        hasManualSettings -> ProYellow
        enabled -> Color.White
        else -> Color.White.copy(alpha = 0.35f)
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = when {
            hasManualSettings -> Color.Black.copy(alpha = 0.65f)
            enabled -> Color.Black.copy(alpha = 0.55f)
            else -> Color.Black.copy(alpha = 0.3f)
        },
        contentColor = contentColor,
        border = BorderStroke(
            width = 1.dp,
            color = if (hasManualSettings) ProYellow else Color.White.copy(alpha = if (enabled) 0.22f else 0.1f)
        ),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "RESET",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.rotatedContentLayout(contentRotationDegrees)
            )
        }
    }
}
