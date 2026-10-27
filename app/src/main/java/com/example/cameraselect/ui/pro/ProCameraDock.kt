package com.example.cameraselect.ui.pro

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * Compact pills showing the live ISO, shutter, focus values, and a reset button.
 * Tapping a control pill opens (or closes) its wheel; values stream from CaptureResult
 * while exposure/focus are automatic. Tapping RESET resets all manual settings to AUTO.
 *
 * The control pills stay in portrait orientation regardless of device rotation.
 */
@Composable
fun ProCameraDock(
    controller: CameraCaptureController,
    settings: ManualCameraSettings,
    capabilities: ManualCapabilities,
    openControl: ManualControlTarget?,
    onControlClick: (ManualControlTarget) -> Unit,
    modifier: Modifier = Modifier,
    onResetClick: () -> Unit = {},
) {
    val liveIso = controller.liveIsoState.value
    val liveShutter = controller.liveShutterState.value
    val liveFocus = controller.liveFocusState.value

    val manualExposure = (settings.manualExposure && capabilities.supportsManualSensor) &&
        (settings.iso != null && settings.shutterNs != null)
    val manualFocus = settings.manualFocus && capabilities.supportsManualFocus && (settings.focusDioptres != null)

    val hasManualSettings = manualExposure || manualFocus

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                .fillMaxHeight()
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
                .fillMaxHeight()
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
                .fillMaxHeight()
        )
        ProResetPill(
            enabled = capabilities.supportsManualSensor || capabilities.supportsManualFocus,
            hasManualSettings = hasManualSettings,
            onClick = onResetClick,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
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
    modifier: Modifier = Modifier,
    isAuto: Boolean = false,
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
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Text(
                text = title,
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                color = contentColor.copy(alpha = 0.75f),
                maxLines = 1
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isAuto && enabled) {
                    Text(
                        text = "AUTO",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) Color.Black else ProYellow,
                        maxLines = 1
                    )
                }
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
) {
    val contentColor = when {
        hasManualSettings -> ProYellow
        enabled -> Color.White
        else -> Color.White.copy(alpha = 0.35f)
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
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
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            Text(
                text = "RESET",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
    }
}

