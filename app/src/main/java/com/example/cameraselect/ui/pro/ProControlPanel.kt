package com.example.cameraselect.ui.pro

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cameraselect.capture.CameraCaptureController
import com.example.cameraselect.model.ManualCameraSettings
import com.example.cameraselect.model.ManualCameraValues
import com.example.cameraselect.model.ManualCapabilities
import com.example.cameraselect.model.ManualControlTarget
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val WheelRowHeight = 36.dp

private const val WHEEL_STIFFNESS = 700f
private const val WHEEL_DAMPING_RATIO = 0.78f

/**
 * Slim vertical Pro control panel for ISO and Shutter Speed parameters.
 * Opens on the right side of the screen as a floating overlay above the Pro dock.
 */
@Composable
fun ProControlPanel(
    target: ManualControlTarget,
    controller: CameraCaptureController,
    settings: ManualCameraSettings,
    capabilities: ManualCapabilities,
    onStopSelected: (Int) -> Unit,
    onAutoToggle: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    visibleRows: Int = 5,
    rotationDegrees: Float = 0f
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

    val title: String
    val autoLabel: String
    val manualLabel: String
    val isAuto: Boolean
    val preciseValue: String
    val unitLabel: String
    val labels: List<String>
    val selectedIndex: Int

    when (target) {
        ManualControlTarget.ISO -> {
            val stops = remember(capabilities) { ManualCameraValues.isoStops(capabilities) }
            val currentIso = if (manualExposure) settings.iso else (liveIso ?: settings.iso)
            title = "ISO"
            autoLabel = "AUTO"
            manualLabel = "MANUAL"
            isAuto = !manualExposure
            preciseValue = ManualCameraValues.formatIso(currentIso)
            unitLabel = "ISO"
            labels = stops.map { it.label }
            selectedIndex = ManualCameraValues.nearestIsoIndex(stops, currentIso)
        }

        ManualControlTarget.SHUTTER -> {
            val stops = remember(capabilities) { ManualCameraValues.shutterStops(capabilities) }
            val currentShutter =
                if (manualExposure) settings.shutterNs else (liveShutter ?: settings.shutterNs)
            title = "SHUTTER"
            autoLabel = "AUTO"
            manualLabel = "MANUAL"
            isAuto = !manualExposure
            preciseValue = ManualCameraValues.formatShutter(currentShutter)
            unitLabel = "SPEED"
            labels = stops.map { it.label }
            selectedIndex = ManualCameraValues.nearestShutterIndex(stops, currentShutter)
        }

        ManualControlTarget.FOCUS -> {
            val stops = remember(capabilities) { ManualCameraValues.focusStops(capabilities) }
            val currentFocus =
                if (manualFocus) settings.focusDioptres else (liveFocus ?: settings.focusDioptres)
            title = "FOCUS"
            autoLabel = "AF"
            manualLabel = "MF"
            isAuto = !manualFocus
            preciseValue = ManualCameraValues.formatFocus(currentFocus)
            unitLabel = "DISTANCE"
            labels = stops.map { it.label }
            selectedIndex = ManualCameraValues.nearestFocusIndex(stops, currentFocus)
        }
    }

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
        // Header Row: Title on Left | Close button on Right
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                color = ProYellow,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
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

        // Selected Value Card (Compact badge, no wasted whitespace)
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
                    text = preciseValue,
                    color = Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
                Text(
                    text = unitLabel,
                    color = Color.Black.copy(alpha = 0.6f),
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Vertical Ruler / List
        if (labels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WheelRowHeight * visibleRows),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Not supported",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WheelRowHeight * visibleRows)
            ) {
                key(target) {
                    MotionRuler(
                        labels = labels,
                        selectedIndex = selectedIndex,
                        manual = !isAuto,
                        visibleRows = visibleRows,
                        onSelect = onStopSelected,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // AUTO / MANUAL Toggle Button at the Bottom
        Surface(
            onClick = onAutoToggle,
            shape = RoundedCornerShape(8.dp),
            color = if (isAuto) ProYellow else Color.White.copy(alpha = 0.15f),
            contentColor = if (isAuto) Color.Black else Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isAuto) autoLabel else manualLabel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }
        }
    }
}

/**
 * Vertical ruler with a fixed center band for parameter selection.
 */
@Composable
fun MotionRuler(
    labels: List<String>,
    selectedIndex: Int,
    manual: Boolean,
    visibleRows: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (labels.isEmpty()) return

    val rowHeightPx = with(LocalDensity.current) { WheelRowHeight.toPx() }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val lastIndex = labels.lastIndex
    val maxScrollPx = lastIndex * rowHeightPx
    val clampedSelected = selectedIndex.coerceIn(0, lastIndex)

    var scrollPx by remember { mutableFloatStateOf(clampedSelected * rowHeightPx) }
    var reportedIndex by remember { mutableIntStateOf(clampedSelected) }
    var dragging by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<Job?>(null) }

    fun report(value: Float) {
        val index = (value / rowHeightPx).roundToInt().coerceIn(0, lastIndex)
        if (index == reportedIndex) return
        reportedIndex = index
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onSelect(index)
    }

    val draggableState = rememberDraggableState { delta ->
        scrollPx = (scrollPx - delta).coerceIn(0f, maxScrollPx)
        report(scrollPx)
    }

    LaunchedEffect(clampedSelected, lastIndex) {
        scrollPx = scrollPx.coerceIn(0f, maxScrollPx)
        reportedIndex = reportedIndex.coerceIn(0, lastIndex)

        if (dragging || settleJob?.isActive == true) return@LaunchedEffect
        if (clampedSelected == reportedIndex) return@LaunchedEffect
        reportedIndex = clampedSelected
        animate(
            initialValue = scrollPx,
            targetValue = clampedSelected * rowHeightPx,
            animationSpec = spring(dampingRatio = 1f, stiffness = 900f)
        ) { value, _ -> scrollPx = value }
    }

    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .draggable(
                state = draggableState,
                orientation = Orientation.Vertical,
                onDragStarted = {
                    dragging = true
                    settleJob?.cancel()
                    settleJob = null
                },
                onDragStopped = { velocity ->
                    dragging = false
                    val start = scrollPx
                    val projected = (start - velocity * 0.15f).coerceIn(0f, maxScrollPx)
                    val targetIndex = (projected / rowHeightPx).roundToInt().coerceIn(0, lastIndex)
                    settleJob = scope.launch {
                        animate(
                            initialValue = start,
                            targetValue = targetIndex * rowHeightPx,
                            initialVelocity = -velocity,
                            animationSpec = spring(
                                dampingRatio = WHEEL_DAMPING_RATIO,
                                stiffness = WHEEL_STIFFNESS
                            )
                        ) { value, _ ->
                            scrollPx = value
                            report(value)
                        }
                    }
                }
            )
    ) {
        val containerHeightPx = constraints.maxHeight.toFloat()
        val topPx = containerHeightPx / 2f - rowHeightPx / 2f
        val safeReported = reportedIndex.coerceIn(0, lastIndex)

        val halfWindow = visibleRows / 2
        val firstRendered = (safeReported - halfWindow - 3).coerceAtLeast(0)
        val lastRendered = (safeReported + halfWindow + 3).coerceAtMost(lastIndex)

        // Highlight box for currently selected row
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(WheelRowHeight)
                .clip(RoundedCornerShape(6.dp))
                .background(if (manual) Color(0xFF383838) else Color(0xFF2E2E2E))
        )

        for (index in firstRendered..lastRendered) {
            val isSelected = index == safeReported
            val distance = abs(index - safeReported)
            val alpha = when {
                isSelected -> 1f
                distance == 1 -> 0.45f
                else -> 0.22f
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WheelRowHeight)
                    .offset {
                        IntOffset(
                            x = 0,
                            y = (topPx + index * rowHeightPx - scrollPx).roundToInt()
                        )
                    }
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = labels[index],
                    color = if (isSelected) Color.White else Color.White.copy(alpha = alpha),
                    fontSize = if (isSelected) 16.sp else 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(if (isSelected) 12.dp else 6.dp)
                        .background(
                            Color.White.copy(alpha = if (isSelected) 0.85f else 0.25f),
                            RoundedCornerShape(1.dp)
                        )
                )
            }
        }
    }
}
