package com.example.cameraselect.ui.pro

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cameraselect.capture.CameraCaptureController
import com.example.cameraselect.model.ManualCameraSettings
import com.example.cameraselect.model.ManualCameraValues
import com.example.cameraselect.model.ManualCapabilities

/**
 * Horizontal manual focus slider showing distance from minimum focus distance to infinity (∞),
 * with a floating live value badge above the thumb and a circular "A" button to toggle Auto Focus.
 */
@Composable
fun ProFocusSlider(
    controller: CameraCaptureController,
    settings: ManualCameraSettings,
    capabilities: ManualCapabilities,
    onFocusChanged: (Float) -> Unit,
    onAutoToggle: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    rotationDegrees: Float = 0f,
) {
    val liveFocus = controller.liveFocusState.value
    val maxDioptres = capabilities.minimumFocusDistance ?: 0f
    val supportsFocus = capabilities.supportsManualFocus && maxDioptres > 0f

    val manualFocus = settings.manualFocus && supportsFocus && settings.focusDioptres != null
    val currentDioptres: Float? = if (manualFocus) {
        settings.focusDioptres
    } else {
        liveFocus ?: settings.focusDioptres
    }

    val activeDioptres = currentDioptres ?: 0f
    val sliderPos = if (supportsFocus) {
        (1f - (activeDioptres / maxDioptres)).coerceIn(0f, 1f)
    } else {
        1f
    }

    val formattedValue = ManualCameraValues.formatFocus(currentDioptres)
    val minText = if (supportsFocus) ManualCameraValues.formatFocus(maxDioptres) else "—"
    val maxText = "∞"
    val isAuto = !settings.manualFocus

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.8f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Header Row: Title & Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "FOCUS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = Color.White
                    )
                    Surface(
                        shape = CircleShape,
                        color = if (isAuto) ProYellow else Color.White.copy(alpha = 0.15f),
                        contentColor = if (isAuto) Color.Black else Color.White,
                        modifier = Modifier.height(18.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isAuto) "AUTO" else "MANUAL",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp
                            )
                        }
                    }
                }

                Surface(
                    onClick = onClose,
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.12f),
                    contentColor = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close focus controls",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (!supportsFocus) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Manual focus not supported on this lens",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Slider Column with floating value badge
                    BoxWithConstraints(
                        modifier = Modifier.weight(1f)
                    ) {
                        val badgeWidthDp = 50.dp
                        val minX = 0.dp
                        val maxX = (this.maxWidth - badgeWidthDp).coerceAtLeast(0.dp)
                        val calculatedX = (this.maxWidth * sliderPos) - (badgeWidthDp / 2)
                        val clampedX = calculatedX.coerceIn(minX, maxX)

                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Floating value badge above thumb
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = ProYellow,
                                    contentColor = Color.Black,
                                    modifier = Modifier.offset(x = clampedX)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .width(badgeWidthDp)
                                            .padding(vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = formattedValue,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            // Min Label | Slider | Infinity Label
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = minText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.padding(end = 4.dp)
                                )

                                Slider(
                                    value = sliderPos,
                                    onValueChange = { newPos ->
                                        val targetDioptres = (1f - newPos) * maxDioptres
                                        onFocusChanged(targetDioptres)
                                    },
                                    valueRange = 0f..1f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = ProYellow,
                                        activeTrackColor = ProYellow,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                                    ),
                                    modifier = Modifier.weight(1f)
                                )

                                Text(
                                    text = maxText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // "A" Auto Focus Toggle Button
                    Surface(
                        onClick = onAutoToggle,
                        shape = CircleShape,
                        color = if (isAuto) ProYellow else Color.White.copy(alpha = 0.15f),
                        contentColor = if (isAuto) Color.Black else Color.White,
                        border = BorderStroke(
                            width = 1.5.dp,
                            color = if (isAuto) ProYellow else Color.White.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "A",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }
    }
}
