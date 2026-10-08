package com.amaankhan.toolkit.ui

import android.net.Uri
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.amaankhan.toolkit.MainActivity
import com.amaankhan.toolkit.data.ThemeChoice
import com.amaankhan.toolkit.documents.documentHtml
import com.amaankhan.toolkit.documents.SlideLayout
import com.amaankhan.toolkit.documents.SlideShape
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class DocumentViewerTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun csvTableKeepsDarkNavigationVisibleWhenSwitchingFromSource() {
        val source = File(compose.activity.cacheDir, "viewer-table-fixture.csv").apply { writeText("Item,Note\nNotebook,\"Two\nlines\"") }
        try {
            compose.activityRule.scenario.onActivity { activity -> activity.setContent {
                ToolkitTheme(ThemeChoice.DARK) { LocalViewerScreen(Uri.fromFile(source).toString(), {}) }
            } }
            compose.waitUntil(10000) { compose.onAllNodesWithText("Table").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Table").performClick()
            val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            // Wait for the WebView text before inspecting the composed header pixels.
            compose.waitUntil(10000) {
                fun hasText(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
                    if (node == null) return false
                    if (node.text?.contains("Notebook") == true) return true
                    return (0 until node.childCount).any { hasText(node.getChild(it)) }
                }
                hasText(automation.rootInActiveWindow)
            }
            compose.onNodeWithContentDescription("Back").assertIsDisplayed()
            val headerY = compose.onNodeWithText(source.name).fetchSemanticsNode().boundsInWindow.center.y.toInt()
            // Capture the composed display directly. Compose PixelCopy can time out
            // with a hardware WebView in the window on the emulator.
            val screen = requireNotNull(automation.takeScreenshot())
            try {
                val color = screen.getPixel(4, headerY)
                assertTrue("WebView painted white over the dark navigation area", android.graphics.Color.red(color) < 80 && android.graphics.Color.green(color) < 80 && android.graphics.Color.blue(color) < 80)
            } finally { screen.recycle() }
        } finally { source.delete() }
    }

    @Test fun entireSlideFitsPortraitViewportWithoutEnablingJavaScript() {
        var web: WebView? = null
        val scene = SlideLayout(720f, 405f, "#FFFFFF", listOf(
            SlideShape(0f, 0f, 100f, 405f, "rect", fill = "#00FF00"),
            SlideShape(620f, 0f, 100f, 405f, "rect", fill = "#FF00FF"),
            SlideShape(140f, 100f, 400f, 100f, "rect", html = "Searchable slide text")
        ))
        compose.activityRule.scenario.onActivity { activity -> activity.setContent {
            ToolkitTheme(ThemeChoice.LIGHT) { SafeDocumentWebView(documentHtml(scene.html()), modifier = Modifier.fillMaxSize(), onView = { web = it }) }
        } }
        compose.waitUntil(10000) { compose.runOnIdle { web != null && web!!.progress == 100 && web!!.width > 0 } }
        val matches = java.util.concurrent.atomic.AtomicInteger(-1)
        compose.runOnIdle { web!!.setFindListener { _, total, done -> if (done) matches.set(total) }; web!!.findAllAsync("Searchable slide text") }
        compose.waitUntil(10000) { matches.get() >= 0 }
        assertEquals(1, matches.get())
        // Inspect both edges of the actual WebView rendering, not generated markup.
        compose.waitUntil(10000) { compose.runOnIdle {
            val view = web!!
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            try {
                view.draw(android.graphics.Canvas(bitmap))
                var left = false; var right = false
                for (y in 0 until bitmap.height step 8) for (x in 0 until bitmap.width step 8) {
                    val color = bitmap.getPixel(x, y)
                    if (color == android.graphics.Color.GREEN && x < bitmap.width / 4) left = true
                    if (color == android.graphics.Color.MAGENTA && x > bitmap.width * 3 / 4) right = true
                }
                left && right
            } finally { bitmap.recycle() }
        } }
        compose.runOnIdle { assertFalse(web!!.canScrollHorizontally(1)); assertFalse(web!!.settings.javaScriptEnabled) }
    }

    @Test fun encryptedPdfRequiresPasswordThenSearchAndBookmarksNavigateRealPages() {
        val source = File(compose.activity.cacheDir, "viewer-protected-fixture.pdf")
        PDDocument().use { document ->
            repeat(2) { index ->
                val page = PDPage(); document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText(); stream.setFont(PDType1Font.HELVETICA, 18f); stream.newLineAtOffset(60f, 680f)
                    stream.showText(if (index == 0) "First page" else "Distinctive searchable phrase"); stream.endText()
                }
            }
            document.protect(StandardProtectionPolicy("owner-fixture", "viewer-fixture", AccessPermission()).apply { encryptionKeyLength = 256 })
            document.save(source)
        }
        val original = source.readBytes()
        val bookmarkKey = java.security.MessageDigest.getInstance("SHA-256").digest(Uri.fromFile(source).toString().toByteArray()).joinToString("") { "%02x".format(it) }
        val bookmarks = compose.activity.getSharedPreferences("pdf-bookmarks", 0)
        bookmarks.edit().remove(bookmarkKey).commit()
        try {
            compose.activityRule.scenario.onActivity { activity -> activity.setContent { ToolkitTheme(ThemeChoice.DARK) { PdfViewerScreen(Uri.fromFile(source).toString(), {}) } } }
            compose.waitUntil(10000) { compose.onAllNodesWithText("PDF password").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("PDF password").performTextInput("wrong")
            compose.onNodeWithText("Open PDF").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("That password did not unlock the PDF.").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("PDF password").performTextReplacement("viewer-fixture")
            compose.onNodeWithText("Open PDF").performClick()
            compose.waitUntil(15000) { compose.onAllNodesWithContentDescription("PDF page 1").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Search").performClick()
            compose.onNodeWithText("Find text").performTextInput("Distinctive")
            compose.onAllNodes(hasText("Search") and hasClickAction()).onLast().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("Page 2").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Page 2").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("PDF page 2").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Bookmark page").performClick()
            compose.onNodeWithText("Bookmarks").performClick()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("Page 2"))
            compose.onNodeWithText("Page 2").assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription("Remove page bookmark").performClick()
            assertArrayEquals(original, source.readBytes())
        } finally { source.delete(); bookmarks.edit().remove(bookmarkKey).commit() }
    }

    @Test fun localHtmlCannotRunScriptsOrOpenDeviceFilesAndSupportsRealTextRendering() {
        var web: WebView? = null
        val payload = documentHtml("<h1>Private document</h1><script>document.body.innerHTML='Executed script'</script><img src='https://example.invalid/secret'>")
        compose.activityRule.scenario.onActivity { activity -> activity.setContent { ToolkitTheme(ThemeChoice.LIGHT) { SafeDocumentWebView(payload, modifier = Modifier.fillMaxSize(), onView = { web = it }) } } }
        compose.waitUntil(10000) { compose.runOnIdle { web != null && web!!.progress == 100 && web!!.height > 0 } }
        compose.runOnIdle {
            val settings = web!!.settings
            assertFalse(settings.javaScriptEnabled); assertFalse(settings.allowFileAccess); assertFalse(settings.allowContentAccess)
            assertTrue(settings.blockNetworkLoads)
        }
        // Inspect the rendered page through native find-on-page. A JavaScript
        // DOM probe cannot validate a viewer that intentionally disables JS.
        fun matches(query: String): Int {
            val count = java.util.concurrent.atomic.AtomicInteger(-1)
            compose.runOnIdle {
                web!!.setFindListener { _, total, finished -> if (finished) count.set(total) }
                web!!.findAllAsync(query)
            }
            compose.waitUntil(10000) { count.get() >= 0 }
            return count.get()
        }
        assertEquals("Expected document text was not rendered", 1, matches("Private document"))
        assertEquals("Document script changed the visible page", 0, matches("Executed script"))
    }
}
