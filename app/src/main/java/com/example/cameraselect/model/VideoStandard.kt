package com.example.cameraselect.model

/**
 * Video broadcast standard selected in the settings.
 *
 * It decides the recording frame-rate target and the anti-banding frequency:
 * PAL shoots at up to 50 fps with a 25 fps fallback, NTSC at up to 60 fps with
 * a 30 fps fallback. The fallback is used when the lens cannot deliver the
 * preferred rate at the chosen recording size.
 */
enum class VideoStandard(
    val label: String,
    val preferredFps: Int,
    val fallbackFps: Int,
    val antiBandingHz: Int
) {
    PAL("PAL", preferredFps = 50, fallbackFps = 25, antiBandingHz = 50),
    NTSC("NTSC", preferredFps = 60, fallbackFps = 30, antiBandingHz = 60);

    companion object {
        fun fromName(name: String?): VideoStandard =
            entries.firstOrNull { it.name == name } ?: NTSC
    }
}
