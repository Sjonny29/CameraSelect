package com.example.cameraselect.model

enum class CaptureMode {
    PHOTO,
    VIDEO,
    PRO_PHOTO;

    /** True for both the normal photo mode and the manual Pro photo mode. */
    val isPhoto: Boolean
        get() = this != VIDEO
}
