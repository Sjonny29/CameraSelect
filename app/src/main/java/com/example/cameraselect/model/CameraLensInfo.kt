package com.example.cameraselect.model

enum class LensFacing {
    FRONT,
    BACK,
    EXTERNAL
}

data class CameraLensInfo(
    val id: String,
    val lensFacing: LensFacing,
    val isHidden: Boolean,
    val zoomLabel: String,
    val classification: String,
    val focalLengthMm: Int,
    val focalLengthActual: Float,
    val displayName: String
)
