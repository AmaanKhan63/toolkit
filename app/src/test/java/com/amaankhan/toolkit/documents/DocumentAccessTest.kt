package com.amaankhan.toolkit.documents

import org.junit.Assert.*
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException

class DocumentAccessTest {
    @Test fun pdfRoutingUsesMimeOrDisplayNameWhenProvidersReturnGenericMime() {
        assertTrue(isPdfDocument("application/pdf", "download"))
        assertTrue(isPdfDocument("application/octet-stream", "REPORT.PDF"))
        assertTrue(isPdfDocument(null, "report.pdf"))
        assertFalse(isPdfDocument("text/plain", "report.pdf.txt"))
        assertFalse(isPdfDocument("application/epub+zip", "book.epub"))
    }

    @Test fun providerFailuresNeverRevealRawMessagesAndKeepRecoveryGuidance() {
        val denied = documentReadError(IOException("private wrapper", SecurityException("Permission Denial private.provider")))
        assertTrue(denied.contains("access", ignoreCase = true))
        assertTrue(denied.contains("again", ignoreCase = true))
        assertFalse(denied.contains("private"))
        val missing = documentReadError(FileNotFoundException("private download path"))
        assertTrue(missing.contains("moved") || missing.contains("unavailable"))
        assertFalse(missing.contains("private"))
        val broken = documentReadError(IOException("private parser offsets"), pdf = true)
        assertTrue(broken.contains("PDF"))
        assertFalse(broken.contains("private"))
    }
}
