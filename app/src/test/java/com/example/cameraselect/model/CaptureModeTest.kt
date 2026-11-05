package com.example.cameraselect.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureModeTest {

    @Test
    fun photoModesReportPhotoOnly() {
        for (mode in listOf(CaptureMode.PHOTO, CaptureMode.PRO_PHOTO)) {
            assertTrue(mode.isPhoto)
            assertFalse(mode.isVideo)
        }
    }

    @Test
    fun videoModesReportVideoOnly() {
        for (mode in listOf(CaptureMode.VIDEO, CaptureMode.PRO_VIDEO)) {
            assertTrue(mode.isVideo)
            assertFalse(mode.isPhoto)
        }
    }

    @Test
    fun proModesReportProOnly() {
        assertTrue(CaptureMode.PRO_PHOTO.isPro)
        assertTrue(CaptureMode.PRO_VIDEO.isPro)
        assertFalse(CaptureMode.PHOTO.isPro)
        assertFalse(CaptureMode.VIDEO.isPro)
    }
}
