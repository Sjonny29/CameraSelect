package com.example.cameraselect

import com.example.cameraselect.model.CameraLensInfo
import com.example.cameraselect.model.LensFacing
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun cameraLenses_sortedFromSmallestToLargestFocalLength() {
        val lens1xA = CameraLensInfo(
            id = "0",
            lensFacing = LensFacing.BACK,
            isHidden = false,
            zoomLabel = "1x",
            classification = "Main",
            focalLengthMm = 26,
            focalLengthActual = 4.3f,
            displayName = "1x ID0 Main Focal length 26mm",
        )
        val lens03x = CameraLensInfo(
            id = "1",
            lensFacing = LensFacing.BACK,
            isHidden = true,
            zoomLabel = "0.3x",
            classification = "Ultra wide",
            focalLengthMm = 13,
            focalLengthActual = 1.8f,
            displayName = "0.3x ID1 Ultra wide Focal length 13mm",
        )
        val lens13x = CameraLensInfo(
            id = "2",
            lensFacing = LensFacing.BACK,
            isHidden = true,
            zoomLabel = "1.3x",
            classification = "Telephoto",
            focalLengthMm = 35,
            focalLengthActual = 5.6f,
            displayName = "1.3x ID2 Telephoto Focal length 35mm",
        )
        val lens1xB = CameraLensInfo(
            id = "3",
            lensFacing = LensFacing.BACK,
            isHidden = false,
            zoomLabel = "1x",
            classification = "Main",
            focalLengthMm = 26,
            focalLengthActual = 4.3f,
            displayName = "1x ID3 Main Focal length 26mm",
        )

        val unsortedLenses = listOf(lens1xA, lens03x, lens13x, lens1xB)

        val sortedLenses = unsortedLenses.sortedWith(
            compareBy<CameraLensInfo> { it.focalLengthActual }
                .thenBy { it.focalLengthMm }
                .thenBy { it.id.toIntOrNull() ?: Int.MAX_VALUE }
                .thenBy { it.id }
        )

        val expectedOrder = listOf(lens03x, lens1xA, lens1xB, lens13x)
        assertEquals(expectedOrder, sortedLenses)
        assertEquals("0.3x", sortedLenses[0].zoomLabel)
        assertEquals("1x", sortedLenses[1].zoomLabel)
        assertEquals("1x", sortedLenses[2].zoomLabel)
        assertEquals("1.3x", sortedLenses[3].zoomLabel)
    }
}
