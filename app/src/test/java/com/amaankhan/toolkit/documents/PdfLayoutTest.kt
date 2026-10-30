package com.amaankhan.toolkit.documents

import org.junit.Assert.*
import org.junit.Test

class PdfLayoutTest {
    @Test fun portraitImageFitsWithoutCroppingAndIsCentered() {
        val page = PdfLayout.fit(100, 200, PdfOptions(margin = 24))
        assertEquals(595, page.width)
        assertEquals(842, page.height)
        assertEquals(24f, page.top, 0.01f)
        assertEquals(2f, page.imageHeight / page.imageWidth, 0.01f)
        assertEquals(page.width.toFloat(), 2 * page.left + page.imageWidth, 0.01f)
    }
    @Test fun autoLandscapeUsesRotatedPageDimensions() {
        val page = PdfLayout.fit(200, 100, PdfOptions())
        assertEquals(842, page.width)
        assertEquals(595, page.height)
        assertTrue(page.left >= 24 && page.top >= 24)
    }
    @Test fun forcedPortraitAndLetterAreRespected() {
        val page = PdfLayout.fit(400, 100, PdfOptions(Paper.LETTER, Orientation.PORTRAIT, 48))
        assertEquals(612, page.width)
        assertEquals(792, page.height)
        assertEquals(48f, page.left, 0.01f)
    }
    @Test fun samplingBoundsHugeAndOddImageSizes() {
        for ((w, h) in listOf(40000 to 25000, 1801 to 1, 9999 to 9999, 80 to 50)) {
            val sample = PdfLayout.sampleSize(w, h, 1000)
            assertTrue(sample > 0 && (sample and (sample - 1)) == 0)
            assertTrue(w / sample <= 1000 && h / sample <= 1000)
        }
    }
    @Test fun largeBatchesKeepTheSameBoundedImageResolution() {
        for (count in listOf(1, 20, 21, 100, 500)) {
            assertEquals("Batch size must not make individual pages unreadable", 1800, PdfLayout.decodeEdge(count))
        }
    }
    @Test(expected = IllegalArgumentException::class) fun invalidDimensionsAreRejected() { PdfLayout.fit(0, 20, PdfOptions()) }
    @Test(expected = IllegalArgumentException::class) fun excessiveMarginsAreRejected() { PdfLayout.fit(20, 20, PdfOptions(margin = 600)) }
    @Test(expected = IllegalArgumentException::class) fun oversizedBatchIsRejected() { PdfLayout.decodeEdge(501) }
    @Test fun numericMarginsUseFixedReferencePixelsAndHonorAsymmetry() {
        val page = PdfLayout.fit(100, 100, PdfOptions(orientation = Orientation.PORTRAIT, marginsPx = MarginsPx(96, 0, 0, 96)))
        assertEquals(72f, page.left, 0.01f)
        assertEquals(523f, page.imageWidth, 0.01f)
        assertEquals(195.5f, page.top, 0.01f)
    }
    @Test(expected = IllegalArgumentException::class) fun negativePixelMarginIsRejected() {
        PdfLayout.fit(100, 100, PdfOptions(marginsPx = MarginsPx(left = -1)))
    }
}
