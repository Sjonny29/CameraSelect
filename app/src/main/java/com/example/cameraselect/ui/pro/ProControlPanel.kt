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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.draw.rotate
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

private val WheelRowHeight = 38.dp

// Spring used by the ruler after a flick. Tuned to settle in roughly 250-400ms
// while still letting a hard flick travel a few rows.
private const val WHEEL_STIFFNESS = 700f
private const val WHEEL_DAMPING_RATIO = 0.78f


/**
 * Expanded MotionCam-style wheel for one parameter. Only shown after the user
 * taps the matching dock pill, so the preview stays unobstructed by default.
 */
@Composable
fun ProControlPanel(
    target: ManualControlTarget,
    controller: CameraCaptureController,
    settings: ManualCameraSettings,
    capabilities: ManualCapabilities,
    rotationDegrees: Float,
    visibleRows: Int,
    onStopSelected: (Int) -> Unit,
    onAutoToggle: () -> Unit,
    onClose: () -> Unit,
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
            .width(214.dp)
            .rotate(rotationDegrees)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xF20A0A0A))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = ProYellow,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                onClick = onAutoToggle,
                shape = CircleShape,
                color = if (isAuto) ProYellow else Color.White.copy(alpha = 0.14f),
                contentColor = if (isAuto) Color.Black else Color.White,
                modifier = Modifier.height(22.dp)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isAuto) autoLabel else manualLabel,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Surface(
                onClick = onClose,
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.1f),
                contentColor = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close pro controls",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(7.dp))

        if (labels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WheelRowHeight * visibleRows),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Not supported by this lens",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WheelRowHeight * visibleRows),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .width(30.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    NudgeButton(
                        up = true,
                        enabled = selectedIndex > 0,
                        onClick = { onStopSelected((selectedIndex - 1).coerceAtLeast(0)) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    NudgeButton(
                        up = false,
                        enabled = selectedIndex < labels.lastIndex,
                        onClick = { onStopSelected((selectedIndex + 1).coerceAtMost(labels.lastIndex)) }
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 5.dp)
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

                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = Color(0xFFEDEDED),
                    modifier = Modifier.width(60.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = preciseValue,
                            color = Color.Black,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                        Text(
                            text = unitLabel,
                            color = Color.Black.copy(alpha = 0.55f),
                            fontSize = 7.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.8.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = if (isAuto) "Drag to take manual control" else "Drag the wheel or tap the arrows",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 9.sp
        )
    }
}

@Composable
private fun NudgeButton(
    up: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = if (enabled) 0.12f else 0.04f),
        contentColor = Color.White.copy(alpha = if (enabled) 0.9f else 0.25f),
        modifier = Modifier.size(30.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (up) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (up) "Previous value" else "Next value",
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * Vertical ruler with a fixed centre band.
 *
 * The wheel keeps a continuous pixel offset and only reports a new stop when
 * the band crosses into the next row, so the drag is smooth instead of fighting
 * a snapping list. A flick settles onto the nearest row with a spring, and the
 * arrows / badge stay in sync through [selectedIndex].
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

    // scrollPx is the distance scrolled down the stop list. scrollPx / rowHeight
    // is the fractional stop currently sitting in the centre band.
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

    // Nudge arrows (or a capability change) move the wheel to the new stop.
    // A running flick owns the wheel, so it is left alone to finish.
    LaunchedEffect(clampedSelected, lastIndex) {
        // A lens switch can shorten the stop list under an open wheel.
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
                    // Light momentum so a flick travels a few extra rows.
                    val projected = (start - velocity * 0.15f).coerceIn(0f, maxScrollPx)
                    val targetIndex = (projected / rowHeightPx).roundToInt().coerceIn(0, lastIndex)
                    settleJob = scope.launch {
                        animate(
                            initialValue = start,
                            targetValue = targetIndex * rowHeightPx,
                            // Finger velocity is positive downwards; the list
                            // scrolls the other way.
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

        // Only the stops that can be on screen are composed. The half-window
        // plus the extra buffer covers the fastest flick between two frames.
        val halfWindow = visibleRows / 2
        val firstRendered = (safeReported - halfWindow - 3).coerceAtLeast(0)
        val lastRendered = (safeReported + halfWindow + 3).coerceAtMost(lastIndex)

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(WheelRowHeight)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (manual) ProYellow else Color.White.copy(alpha = 0.14f)
                )
        )

        for (index in firstRendered..lastRendered) {
            val isSelected = index == safeReported
            val distance = abs(index - safeReported)
            val alpha = when {
                isSelected -> 1f
                distance == 1 -> 0.55f
                else -> 0.28f
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WheelRowHeight)
                    // Placement-only read of scrollPx: a drag repaints the
                    // ruler without recomposing or re-measuring the stops.
                    .offset {
                        IntOffset(
                            x = 0,
                            y = (topPx + index * rowHeightPx - scrollPx).roundToInt()
                        )
                    }
                    .padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = labels[index],
                    color = if (isSelected && manual) Color.Black else Color.White.copy(alpha = alpha),
                    fontSize = if (isSelected) 18.sp else 15.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(if (isSelected) 14.dp else 8.dp)
                        .background(
                            Color.White.copy(alpha = if (isSelected) 0.7f else 0.25f),
                            RoundedCornerShape(1.dp)
                        )
                )
            }
        }
    }
}
