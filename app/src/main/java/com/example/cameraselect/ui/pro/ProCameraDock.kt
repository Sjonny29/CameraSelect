package com.example.cameraselect.ui.pro

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cameraselect.capture.CameraCaptureController
import com.example.cameraselect.model.ManualCameraSettings
import com.example.cameraselect.model.ManualCameraValues
import com.example.cameraselect.model.ManualCapabilities
import com.example.cameraselect.model.ManualControlTarget

val ProYellow = Color(0xFFFFD700)

/**
 * Three compact pills showing the live ISO, shutter and focus values.
 * Tapping a pill opens (or closes) its wheel; values stream from CaptureResult
 * while exposure/focus are automatic.
 */
@Composable
fun ProCameraDock(
    controller: CameraCaptureController,
    settings: ManualCameraSettings,
    capabilities: ManualCapabilities,
    openControl: ManualControlTarget?,
    rotationDegrees: Float,
    onControlClick: (ManualControlTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    val liveIso = controller.liveIsoState.value
    val liveShutter = controller.liveShutterState.value
    val liveFocus = controller.liveFocusState.value

    val manualExposure = settings.manualExposure &&
        capabilities.supportsManualSensor &&
        settings.iso != null &&
        settings.shutterNs != null
    val manualFocus = settings.manualFocus &&
        capabilities.supportsManualFocus &&
        settings.focusDioptres != null

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
            onClick = { onControlClick(ManualControlTarget.ISO) },
            rotationDegrees = rotationDegrees,
            modifier = Modifier.weight(1f)
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
            onClick = { onControlClick(ManualControlTarget.SHUTTER) },
            rotationDegrees = rotationDegrees,
            modifier = Modifier.weight(1f)
        )
        ProDockPill(
            title = if (manualFocus) "MF" else "AF",
            value = ManualCameraValues.formatFocus(
                if (manualFocus) settings.focusDioptres else (liveFocus ?: settings.focusDioptres)
            ),
            isActive = openControl == ManualControlTarget.FOCUS,
            enabled = capabilities.supportsManualFocus,
            onClick = { onControlClick(ManualControlTarget.FOCUS) },
            rotationDegrees = rotationDegrees,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ProDockPill(
    title: String,
    value: String,
    isActive: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    rotationDegrees: Float,
    modifier: Modifier = Modifier
) {
    val contentColor = when {
        isActive -> Color.Black
        enabled -> Color.White
        else -> Color.White.copy(alpha = 0.35f)
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
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
        modifier = modifier.rotate(rotationDegrees)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                text = title,
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                color = contentColor.copy(alpha = 0.75f),
                maxLines = 1
            )
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
    }
}
