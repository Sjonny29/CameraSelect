package com.example.cameraselect.ui.pro

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cameraselect.capture.CameraCaptureController
import com.example.cameraselect.model.ExponentialScale
import com.example.cameraselect.model.ExposureScale
import com.example.cameraselect.model.ManualCameraSettings
import com.example.cameraselect.model.ManualCameraValues
import com.example.cameraselect.model.ManualCapabilities
import com.example.cameraselect.model.ManualControlTarget
import com.example.cameraselect.model.ManualStop
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val RulerRowHeight = 44.dp

/** Roughly how many standard stops the ruler shows at once. */
private const val RULER_VISIBLE_STOPS = 6f

private data class ExposurePanelConfig(
    val valueText: String,
    val unitLabel: String,
    val isAuto: Boolean,
    val position: Float,
    val majorTicks: List<RulerTick>,
    val minorTicks: List<Float>,
    val unitsPerScreen: Float,
    val onPositionChanged: (Float) -> Unit,
    val onPositionChangeFinished: (Float) -> Unit,
)

/**
 * Slim vertical Pro control popup for ISO and Shutter Speed.
 *
 * Both targets use the same [ProExposureRuler], so the two popups are a matched
 * pair: same exponential scale, rail, ticks, fixed pointer and magnetic stops.
 * The surrounding panel (header, value card and AUTO/MANUAL toggle) is
 * unchanged from the previous version.
 */
@Composable
fun ProControlPanel(
    target: ManualControlTarget,
    controller: CameraCaptureController,
    settings: ManualCameraSettings,
    capabilities: ManualCapabilities,
    onIsoChanged: (Int) -> Unit,
    onShutterChanged: (Long) -> Unit,
    onAutoToggle: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    visibleRows: Int = 5,
    rotationDegrees: Float = 0f
) {
    if (target == ManualControlTarget.FOCUS) return

    val liveIso = controller.liveIsoState.value
    val liveShutter = controller.liveShutterState.value

    val manualExposure = settings.manualExposure &&
        capabilities.supportsManualSensor &&
        settings.iso != null &&
        settings.shutterNs != null

    val isoStops = remember(capabilities) { ManualCameraValues.isoStops(capabilities) }
    val shutterStops = remember(capabilities) { ManualCameraValues.shutterStops(capabilities) }
    val isoScale = remember(capabilities) { ExposureScale.isoScale(capabilities) }
    val shutterScale = remember(capabilities) { ExposureScale.shutterScale(capabilities) }

    val isoStopPositions = remember(isoStops, isoScale) {
        isoScale?.let { scale -> isoStops.map { scale.positionOf(it.value.toDouble()) } }
            ?: emptyList()
    }
    val shutterStopPositions = remember(shutterStops, shutterScale) {
        shutterScale?.let { scale -> shutterStops.map { scale.positionOf(it.value.toDouble()) } }
            ?: emptyList()
    }

    val isoMajorTicks = remember(isoStops, isoStopPositions) {
        isoStops.zip(isoStopPositions) { stop, position -> RulerTick(position, stop.label) }
    }
    val shutterMajorTicks = remember(shutterStops, shutterStopPositions) {
        shutterStops.zip(shutterStopPositions) { stop, position -> RulerTick(position, stop.label) }
    }
    val isoMinorTicks = remember(isoStopPositions) {
        ExposureScale.minorTickPositions(isoStopPositions)
    }
    val shutterMinorTicks = remember(shutterStopPositions) {
        ExposureScale.minorTickPositions(shutterStopPositions)
    }
    val isoUnitsPerScreen = remember(isoStopPositions) {
        RULER_VISIBLE_STOPS * ExposureScale.medianStopGap(isoStopPositions)
    }
    val shutterUnitsPerScreen = remember(shutterStopPositions) {
        RULER_VISIBLE_STOPS * ExposureScale.medianStopGap(shutterStopPositions)
    }

    val config: ExposurePanelConfig? = when (target) {
        ManualControlTarget.ISO -> {
            val range = capabilities.isoRange
            val currentIso = if (manualExposure) settings.iso else (liveIso ?: settings.iso)
            if (range == null || isoScale == null || currentIso == null) {
                null
            } else {
                ExposurePanelConfig(
                    valueText = ManualCameraValues.formatIso(currentIso),
                    unitLabel = "ISO",
                    isAuto = !manualExposure,
                    position = isoScale.positionOf(currentIso.toDouble()),
                    majorTicks = isoMajorTicks,
                    minorTicks = isoMinorTicks,
                    unitsPerScreen = isoUnitsPerScreen,
                    onPositionChanged = { position ->
                        onIsoChanged(isoAt(position, isoStops, isoStopPositions, isoScale, range))
                    },
                    onPositionChangeFinished = { position ->
                        onIsoChanged(isoAt(position, isoStops, isoStopPositions, isoScale, range))
                    }
                )
            }
        }

        ManualControlTarget.SHUTTER -> {
            val range = capabilities.exposureRangeNs
            val currentShutter =
                if (manualExposure) settings.shutterNs else (liveShutter ?: settings.shutterNs)
            if (range == null || shutterScale == null || currentShutter == null) {
                null
            } else {
                ExposurePanelConfig(
                    valueText = ManualCameraValues.formatShutterWithUnit(currentShutter),
                    unitLabel = "SPEED",
                    isAuto = !manualExposure,
                    position = shutterScale.positionOf(currentShutter.toDouble()),
                    majorTicks = shutterMajorTicks,
                    minorTicks = shutterMinorTicks,
                    unitsPerScreen = shutterUnitsPerScreen,
                    onPositionChanged = { position ->
                        onShutterChanged(
                            shutterAt(
                                position,
                                shutterStops,
                                shutterStopPositions,
                                shutterScale,
                                range
                            )
                        )
                    },
                    onPositionChangeFinished = { position ->
                        onShutterChanged(
                            shutterAt(
                                position,
                                shutterStops,
                                shutterStopPositions,
                                shutterScale,
                                range
                            )
                        )
                    }
                )
            }
        }

        ManualControlTarget.FOCUS -> null
    }

    ExposurePanel(
        title = when (target) {
            ManualControlTarget.ISO -> "ISO"
            ManualControlTarget.SHUTTER -> "SHUTTER SPEED"
            ManualControlTarget.FOCUS -> "FOCUS"
        },
        config = config,
        visibleRows = visibleRows,
        onAutoToggle = onAutoToggle,
        onClose = onClose,
        modifier = modifier
    )
}

@Composable
private fun ExposurePanel(
    title: String,
    config: ExposurePanelConfig?,
    visibleRows: Int,
    onAutoToggle: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xF20A0A0A))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header: title | close button.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                color = ProYellow,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                maxLines = 1,
                modifier = Modifier.padding(start = 2.dp)
            )

            Surface(
                onClick = onClose,
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.12f),
                contentColor = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(20.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close pro controls",
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Live value card.
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFFEDEDED),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = config?.valueText ?: "—",
                    color = Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
                Text(
                    text = config?.unitLabel ?: "",
                    color = Color.Black.copy(alpha = 0.6f),
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // The ruler itself; drag anywhere in this area.
        if (config == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RulerRowHeight * visibleRows),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Not supported",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
            }
        } else {
            key(title) {
                ProExposureRuler(
                    position = config.position,
                    majorTicks = config.majorTicks,
                    minorTicks = config.minorTicks,
                    unitsPerScreen = config.unitsPerScreen,
                    contentDescription = "$title ruler",
                    stateDescription = config.valueText,
                    onPositionChange = config.onPositionChanged,
                    onPositionChangeFinished = config.onPositionChangeFinished,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(RulerRowHeight * visibleRows)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // AUTO / MANUAL pill, outlined like the reference panel.
        val isAuto = config?.isAuto ?: false
        Surface(
            onClick = onAutoToggle,
            shape = RoundedCornerShape(14.dp),
            color = if (isAuto) ProYellow else Color.Transparent,
            contentColor = if (isAuto) Color.Black else ProYellow,
            border = BorderStroke(
                width = 1.5.dp,
                color = ProYellow.copy(alpha = if (isAuto) 1f else 0.7f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    // Show the action the button performs: in AUTO it switches
                    // to manual, in MANUAL it returns to auto.
                    text = if (isAuto) "MANUAL" else "AUTO",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }
        }
    }
}

private fun isoAt(
    position: Float,
    stops: List<ManualStop<Int>>,
    stopPositions: List<Float>,
    scale: ExponentialScale,
    range: IntRange
): Int {
    val exact = stops.zip(stopPositions)
        .firstOrNull { (_, stopPosition) -> abs(stopPosition - position) < 0.01f }
    return exact?.first?.value
        ?: scale.valueAt(position).roundToInt().coerceIn(range.first, range.last)
}

private fun shutterAt(
    position: Float,
    stops: List<ManualStop<Long>>,
    stopPositions: List<Float>,
    scale: ExponentialScale,
    range: LongRange
): Long {
    val exact = stops.zip(stopPositions)
        .firstOrNull { (_, stopPosition) -> abs(stopPosition - position) < 0.01f }
    if (exact != null) return exact.first.value

    val ns = scale.valueAt(position).roundToLong().coerceIn(range.first, range.last)
    // Continuous fine-tuning does not need nanosecond churn; snapped stops
    // above still commit their exact table value.
    val quantized = when {
        ns >= 1_000_000L -> (ns / 1_000L) * 1_000L
        ns >= 100_000L -> (ns / 100L) * 100L
        else -> ns
    }
    return quantized.coerceIn(range.first, range.last)
}
