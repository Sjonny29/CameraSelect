package com.example.cameraselect.model

enum class CaptureMode {
    PHOTO,
    VIDEO,
    PRO_PHOTO,
    PRO_VIDEO;

    /** True for both the normal photo mode and the manual Pro photo mode. */
    val isPhoto: Boolean
        get() = this == PHOTO || this == PRO_PHOTO

    /** True for both the normal video mode and the manual Pro video mode. */
    val isVideo: Boolean
        get() = this == VIDEO || this == PRO_VIDEO

    /** True while the manual Pro controls are available. */
    val isPro: Boolean
        get() = this == PRO_PHOTO || this == PRO_VIDEO
}
