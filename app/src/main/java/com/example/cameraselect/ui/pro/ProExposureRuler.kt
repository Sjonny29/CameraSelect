package com.example.cameraselect.ui.pro

import android.os.SystemClock
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cameraselect.model.ExposureScale
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/** One labelled major tick on the exposure ruler. */
data class RulerTick(val position: Float, val label: String)

private val RailInset = 6.dp
private val MajorTickLength = 26.dp
private val MinorTickLength = 13.dp
private val LabelGap = 4.dp
private val ArrowTailLength = 20.dp
private val ArrowHalfHeight = 8.dp
private val ArrowNotch = 8.dp
private val ArrowShaftHalfHeight = 3.dp

private const val EMIT_INTERVAL_MS = 33L
private const val SETTLE_DURATION_MS = 140
private const val FLING_DURATION_MS = 450
private const val FLING_PROJECTION_SECONDS = 0.25f

/**
 * Scrolling exposure ruler shared by the ISO and Shutter Speed Pro panels.
 *
 * The scale moves under a fixed pointer: dragging up increases the value
 * (longer shutter / higher ISO), exactly like dragging the ruler itself. The
 * finger position is free between stops; a magnetic pull only appears when the
 * pointer is already very close to a standard stop, and releasing inside that
 * narrow window settles onto the stop. Minor ticks are visual third-stop guides
 * and are never snap targets.
 */
@Composable
fun ProExposureRuler(
    position: Float,
    majorTicks: List<RulerTick>,
    minorTicks: List<Float>,
    unitsPerScreen: Float,
    contentDescription: String,
    stateDescription: String,
    onPositionChange: (Float) -> Unit,
    onPositionChangeFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = remember {
        TextStyle(
            color = ProYellow.copy(alpha = 0.92f),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }

    // Keep the latest callbacks visible to the long-lived gesture coroutine.
    val currentOnPositionChange by rememberUpdatedState(onPositionChange)
    val currentOnPositionChangeFinished by rememberUpdatedState(onPositionChangeFinished)

    val stopPositions = remember(majorTicks) { majorTicks.map { it.position } }

    var rawPosition by remember { mutableFloatStateOf(position) }
    var displayPosition by remember { mutableFloatStateOf(position) }
    var dragging by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf(false) }
    var lastHapticStop by remember { mutableStateOf<Float?>(null) }
    var lastEmitMs by remember { mutableLongStateOf(0L) }
    var motionJob by remember { mutableStateOf<Job?>(null) }

    // External values (live metering in AUTO, or a committed manual value) only
    // move the scale while the user is not touching or flinging it.
    LaunchedEffect(position, dragging, settling) {
        if (!dragging && !settling) {
            rawPosition = position
            displayPosition = position
            lastHapticStop = null
        }
    }

    fun emit(value: Float, force: Boolean) {
        val now = SystemClock.uptimeMillis()
        if (!force && now - lastEmitMs < EMIT_INTERVAL_MS) return
        lastEmitMs = now
        currentOnPositionChange(value)
    }

    fun hapticTick(stop: Float) {
        lastHapticStop = stop
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun hapticIfNeeded(raw: Float) {
        val nearest = ExposureScale.nearestStop(raw, stopPositions) ?: return
        val inside = abs(nearest - raw) <= ExposureScale.snapWindow(raw, stopPositions)

        if (inside && lastHapticStop != nearest) {
            hapticTick(nearest)
        } else if (!inside && lastHapticStop == nearest) {
            lastHapticStop = null
        }
    }

    fun commit(value: Float) {
        rawPosition = value
        emit(value, force = true)
        currentOnPositionChangeFinished(value)
    }

    suspend fun animateDisplayTo(target: Float) {
        val start = displayPosition
        if (abs(target - start) >= 0.01f) {
            animate(
                initialValue = start,
                targetValue = target,
                animationSpec = tween(durationMillis = SETTLE_DURATION_MS)
            ) { value, _ ->
                displayPosition = value
            }
        }
        displayPosition = target
    }

    fun hapticOnSettle(target: Float) {
        val nearest = ExposureScale.nearestStop(target, stopPositions) ?: return
        if (abs(nearest - target) < 0.01f && lastHapticStop != nearest) {
            hapticTick(nearest)
        }
    }

    fun settleTo(target: Float) {
        motionJob?.cancel()
        settling = true
        motionJob = scope.launch {
            commit(target)
            animateDisplayTo(target)
            settling = false
            hapticOnSettle(target)
        }
    }

    fun fling(velocityPxPerSecond: Float, pxPerUnit: Float) {
        val unitsPerSecond = -velocityPxPerSecond / pxPerUnit
        val projected = (rawPosition + unitsPerSecond * FLING_PROJECTION_SECONDS)
            .coerceIn(ExposureScale.MIN_POSITION, ExposureScale.MAX_POSITION)

        motionJob?.cancel()
        settling = true
        motionJob = scope.launch {
            if (abs(projected - rawPosition) >= 0.01f) {
                animate(
                    initialValue = rawPosition,
                    targetValue = projected,
                    animationSpec = tween(
                        durationMillis = FLING_DURATION_MS,
                        easing = LinearOutSlowInEasing
                    )
                ) { value, _ ->
                    rawPosition = value
                    displayPosition = value
                    emit(value, force = false)
                }
            }

            val target = ExposureScale.settlePosition(projected, stopPositions)
            commit(target)
            animateDisplayTo(target)
            settling = false
            hapticOnSettle(target)
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .semantics {
                this.contentDescription = contentDescription
                this.stateDescription = stateDescription
                progressBarRangeInfo = ProgressBarRangeInfo(displayPosition, 0f..100f)
                setProgress { target ->
                    val clamped = target.coerceIn(0f, 100f)
                    motionJob?.cancel()
                    settling = false
                    displayPosition = clamped
                    commit(clamped)
                    true
                }
            }
            .pointerInput(enabled, majorTicks, unitsPerScreen) {
                if (!enabled) return@pointerInput

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)

                    // Only stop an in-flight settle/fling once a new touch
                    // starts, so the release animation is not aborted.
                    motionJob?.cancel()
                    settling = false
                    dragging = true
                    rawPosition = displayPosition
                    lastHapticStop = null

                    val heightPx = size.height.toFloat().coerceAtLeast(1f)
                    val pxPerUnit = (heightPx / unitsPerScreen.coerceAtLeast(0.001f))
                        .coerceAtLeast(0.001f)

                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break

                            if (change.changedToUp()) {
                                change.consume()
                                val velocity = velocityTracker.calculateVelocity().y
                                if (abs(velocity) > heightPx * 1.5f) {
                                    fling(velocity, pxPerUnit)
                                } else {
                                    settleTo(
                                        ExposureScale.settlePosition(rawPosition, stopPositions)
                                    )
                                }
                                break
                            }

                            if (change.positionChanged()) {
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                val deltaY = change.positionChange().y
                                rawPosition = (rawPosition - deltaY / pxPerUnit)
                                    .coerceIn(ExposureScale.MIN_POSITION, ExposureScale.MAX_POSITION)
                                displayPosition = ExposureScale.magneticPosition(
                                    rawPosition,
                                    stopPositions
                                )
                                emit(displayPosition, force = false)
                                hapticIfNeeded(rawPosition)
                                change.consume()
                            }
                        }
                    } finally {
                        dragging = false
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val heightPx = size.height
            val pxPerUnit = (heightPx / unitsPerScreen.coerceAtLeast(0.001f))
                .coerceAtLeast(0.001f)
            val railX = RailInset.toPx()
            val pointerY = heightPx / 2f
            val majorLength = MajorTickLength.toPx()
            val minorLength = MinorTickLength.toPx()
            val labelX = railX + majorLength + LabelGap.toPx()

            // Vertical rail.
            drawLine(
                color = ProYellow.copy(alpha = 0.55f),
                start = Offset(railX, 0f),
                end = Offset(railX, heightPx),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Short third-stop guides; never snap targets.
            minorTicks.forEach { tick ->
                val y = pointerY + (tick - displayPosition) * pxPerUnit
                if (y >= -minorLength && y <= heightPx + minorLength) {
                    drawLine(
                        color = ProYellow.copy(alpha = 0.35f),
                        start = Offset(railX, y),
                        end = Offset(railX + minorLength, y),
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // Standard stops with labels. The label under the pointer is hidden
            // so the fixed arrow stays clean; the value card shows that value.
            majorTicks.forEach { tick ->
                val y = pointerY + (tick.position - displayPosition) * pxPerUnit
                if (y >= -majorLength && y <= heightPx + majorLength) {
                    drawLine(
                        color = ProYellow.copy(alpha = 0.9f),
                        start = Offset(railX, y),
                        end = Offset(railX + majorLength, y),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    if (abs(y - pointerY) > 16.dp.toPx()) {
                        val layout = textMeasurer.measure(tick.label, labelStyle)
                        val halfLabelHeight = layout.size.height / 2f
                        // Keep edge labels fully inside the ruler instead of
                        // clipping them at the top and bottom.
                        if (y - halfLabelHeight >= 0f && y + halfLabelHeight <= heightPx) {
                            drawText(
                                textLayoutResult = layout,
                                topLeft = Offset(labelX, y - halfLabelHeight)
                            )
                        }
                    }
                }
            }

            // Fixed left-pointing arrow at the pointer row.
            val tipX = railX + majorLength + 1.dp.toPx()
            val notchX = tipX + ArrowNotch.toPx()
            val tailX = tipX + ArrowTailLength.toPx()
            val halfHeight = ArrowHalfHeight.toPx()
            val shaftHalf = ArrowShaftHalfHeight.toPx()

            val arrow = Path().apply {
                moveTo(tipX, pointerY)
                lineTo(notchX, pointerY - halfHeight)
                lineTo(notchX, pointerY - shaftHalf)
                lineTo(tailX, pointerY - shaftHalf)
                lineTo(tailX, pointerY + shaftHalf)
                lineTo(notchX, pointerY + shaftHalf)
                lineTo(notchX, pointerY + halfHeight)
                close()
            }
            drawPath(path = arrow, color = ProYellow)
        }
    }
}
