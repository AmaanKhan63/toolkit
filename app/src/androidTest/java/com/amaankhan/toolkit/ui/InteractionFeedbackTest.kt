package com.amaankhan.toolkit.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.amaankhan.toolkit.MainActivity
import com.amaankhan.toolkit.data.Palette
import com.amaankhan.toolkit.data.ThemeChoice
import com.amaankhan.toolkit.documents.PdfResult
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class InteractionFeedbackTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun show(content: @Composable () -> Unit) {
        compose.activityRule.scenario.onActivity { activity -> activity.setContent {
            ToolkitTheme(ThemeChoice.DARK) { content() }
        } }
    }
    private fun awaitDescription(description: String) {
        compose.waitUntil(15000) { compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun outsideTapsDismissInputsWithoutBreakingFieldSwitchesOrButtons() {
        show {
            var first by remember { mutableStateOf("") }
            var second by remember { mutableStateOf("") }
            var password by remember { mutableStateOf("") }
            var clicks by remember { mutableIntStateOf(0) }
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                ToolkitTextField(first, { first = it }, label = { Text("First input") })
                ToolkitTextField(second, { second = it }, label = { Text("Second input") })
                PasswordField("Password input", password, true) { password = it }
                Button(onClick = { clicks++ }) { Text("Action $clicks") }
                Box(Modifier.fillMaxWidth().height(60.dp).testTag("blank"))
            }
        }
        val first = compose.onNodeWithText("First input")
        val second = compose.onNodeWithText("Second input")
        first.performTouchInput { click() }; first.assertIsFocused()
        first.performTextInput("Kept text")
        first.performTouchInput { click() }; first.assertIsFocused()
        second.performTouchInput { click() }; second.assertIsFocused(); first.assertIsNotFocused()
        compose.onNodeWithTag("blank").performTouchInput { click() }; second.assertIsNotFocused()
        compose.onNodeWithText("Kept text").assertExists()
        val password = compose.onNodeWithText("Password input")
        password.performTouchInput { click() }; password.performTextInput("test-password")
        compose.onNodeWithText("Show").performTouchInput { click() }; password.assertIsFocused()
        compose.onNodeWithText("Action 0").performTouchInput { click() }
        password.assertIsNotFocused(); compose.onNodeWithText("Action 1").assertExists()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun outsideTapAlsoDismissesInputInsideBottomSheet() {
        show {
            ModalBottomSheet(onDismissRequest = {}, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                InputFocusArea {
                    Column(Modifier.fillMaxWidth()) {
                        var text by remember { mutableStateOf("") }
                        ToolkitTextField(text, { text = it }, label = { Text("Sheet input") })
                        Box(Modifier.fillMaxWidth().height(72.dp).testTag("sheet blank"))
                    }
                }
            }
        }
        val input = compose.onNodeWithText("Sheet input")
        input.performTouchInput { click() }; input.assertIsFocused()
        compose.onNodeWithTag("sheet blank").performTouchInput { click() }; input.assertIsNotFocused()
    }

    @Test fun actionIconsHaveTransparentInteriorsInEveryDarkPalette() {
        val glyphs = listOf(Glyph.PLUS, Glyph.CLOSE, Glyph.CHECK, Glyph.CONVERT, Glyph.COMPRESS)
        show {
            Column {
                Palette.entries.forEach { palette ->
                    val colors = toolkitColors(palette, true)
                    Row {
                        glyphs.forEach { glyph ->
                            Box(Modifier.size(40.dp).background(colors.background).testTag("$palette-$glyph")) {
                                GlyphIcon(glyph, modifier = Modifier.fillMaxSize(), tint = colors.primary)
                            }
                        }
                    }
                }
            }
        }
        for (palette in Palette.entries) for (glyph in glyphs) {
            val pixels = compose.onNodeWithTag("$palette-$glyph").captureToImage().toPixelMap()
            val expected = toolkitColors(palette, true).background
            // Inside the former decorative rectangle, away from foreground strokes.
            val actual = pixels[(pixels.width * .25f).toInt(), (pixels.height * if (glyph == Glyph.CONVERT) .5f else .35f).toInt()]
            assertEquals("$palette $glyph background red", expected.red, actual.red, .015f)
            assertEquals("$palette $glyph background green", expected.green, actual.green, .015f)
            assertEquals("$palette $glyph background blue", expected.blue, actual.blue, .015f)
        }
    }

    @Test fun pdfSwipesNavigateBothWaysRespectBoundsAndPanWhenZoomed() {
        val file = File(compose.activity.cacheDir, "swipe-feedback.pdf")
        PDDocument().use { doc -> repeat(2) { doc.addPage(PDPage()) }; doc.save(file) }
        try {
            show { PdfPreview(Uri.fromFile(file).toString(), Modifier.fillMaxSize()) }
            awaitDescription("PDF page 1")
            compose.onNodeWithContentDescription("PDF page 1").performTouchInput { swipeRight() }
            compose.onNodeWithText("1 / 2").assertExists()
            compose.onNodeWithContentDescription("PDF page 1").performTouchInput { swipeLeft() }
            awaitDescription("PDF page 2"); compose.onNodeWithText("2 / 2").assertExists()
            compose.onNodeWithContentDescription("PDF page 2").performTouchInput { swipeLeft() }
            compose.onNodeWithText("2 / 2").assertExists()
            compose.onNodeWithContentDescription("PDF page 2").performTouchInput { swipeRight() }
            awaitDescription("PDF page 1")
            compose.onNodeWithContentDescription("PDF page 1").performTouchInput {
                pinch(Offset(width * .4f, height * .5f), Offset(width * .2f, height * .5f),
                    Offset(width * .6f, height * .5f), Offset(width * .8f, height * .5f))
            }
            compose.onNodeWithText("1 / 2").assertExists()
            compose.onNodeWithText("100%").assertDoesNotExist()
            compose.onNode(hasText("%", substring = true) and hasClickAction()).performClick()
            compose.onNodeWithContentDescription("Zoom in").performClick()
            compose.onNodeWithContentDescription("PDF page 1").performTouchInput { swipeLeft() }
            compose.onNodeWithText("1 / 2").assertExists(); compose.onNodeWithText("150%").assertExists()
            compose.onNodeWithText("150%").performClick()
            compose.onNodeWithContentDescription("PDF page 1").performTouchInput { swipeUp() }
            compose.onNodeWithText("1 / 2").assertExists()
            compose.onNodeWithContentDescription("Next page").performClick(); awaitDescription("PDF page 2")
        } finally { file.delete() }
    }

    @Test fun exportedImageGallerySupportsSwipingAndZoomWithoutChangingPage() {
        val images = (1..2).map { n -> File(compose.activity.cacheDir, "swipe-image-$n.png").apply {
            val bitmap = Bitmap.createBitmap(80, 100, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(if (n == 1) android.graphics.Color.GREEN else android.graphics.Color.BLUE)
            outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        } }
        try {
            val result = PdfResult(images[0], images[0], 2, images = images, sourcePages = listOf(1, 3))
            show { ImageResultPreview(result, Modifier.fillMaxSize()) }
            awaitDescription("Exported image 1")
            compose.onNodeWithContentDescription("Exported image 1").performTouchInput { swipeLeft() }
            awaitDescription("Exported image 2")
            compose.onNodeWithContentDescription("Zoom image").performClick()
            compose.onNodeWithContentDescription("Exported image 2").performTouchInput { swipeRight() }
            compose.onNodeWithText("2 / 2").assertExists()
            compose.onNodeWithText("Fit").performClick()
            compose.onNodeWithContentDescription("Exported image 2").performTouchInput { swipeRight() }
            awaitDescription("Exported image 1")
        } finally { images.forEach { it.delete() } }
    }

    @Test fun spreadsheetSwipeChangesActualSheet() {
        val file = File(compose.activity.cacheDir, "swipe-sheets.xlsx")
        XSSFWorkbook().use { book ->
            listOf("First worksheet", "Second worksheet").forEach { title ->
                book.createSheet(title).createRow(0).createCell(0).setCellValue(title)
            }
            file.outputStream().use(book::write)
        }
        try {
            show { LocalViewerScreen(Uri.fromFile(file).toString(), {}) }
            compose.waitUntil(15000) { compose.onAllNodesWithText("First worksheet").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Fullscreen").performClick()
            fun swipe(left: Boolean) {
                val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
                compose.onRoot().performTouchInput {
                    swipe(Offset(root.width * if (left) .8f else .2f, root.height * .6f),
                        Offset(root.width * if (left) .2f else .8f, root.height * .6f), 250)
                }
            }
            compose.waitForIdle()
            swipe(true)
            compose.waitUntil(5000) { compose.onAllNodesWithText("Second worksheet").fetchSemanticsNodes().isNotEmpty() }
            swipe(false)
            compose.waitUntil(5000) { compose.onAllNodesWithText("First worksheet").fetchSemanticsNodes().isNotEmpty() }
        } finally { file.delete() }
    }

    @Test fun longTextSwipesBetweenChunks() {
        val file = File(compose.activity.cacheDir, "swipe-long.txt").apply { writeText("A".repeat(65536) + "Last part") }
        try {
            show { TextPreview(Uri.fromFile(file).toString(), Modifier.fillMaxSize().testTag("text reader")) }
            compose.waitUntil(10000) { compose.onAllNodesWithText("A".repeat(65536)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("text reader").performTouchInput { swipeLeft() }
            compose.waitUntil(10000) { compose.onAllNodesWithText("Last part").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Part 2").assertExists()
            compose.onNodeWithTag("text reader").performTouchInput { swipeRight() }
            compose.onNodeWithText("Part 1").assertExists()
        } finally { file.delete() }
    }

    @Test fun wideDocumentScrollingDoesNotTurnSections() {
        var web: android.webkit.WebView? = null
        var moves = 0
        show {
            SafeDocumentWebView(
                com.amaankhan.toolkit.documents.documentHtml("<div style='width:2800px;height:2000px'>Wide worksheet content</div>"),
                modifier = Modifier.fillMaxSize().testTag("wide document"),
                onNavigate = { moves += it }, onView = { web = it })
        }
        compose.waitUntil(10000) { compose.runOnIdle { web?.progress == 100 && web?.canScrollHorizontally(1) == true } }
        compose.onNodeWithTag("wide document").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(0, moves); assertTrue("Table should scroll horizontally", web!!.scrollX > 0) }
        compose.onNodeWithTag("wide document").performTouchInput { swipeUp() }
        compose.runOnIdle { assertEquals(0, moves); assertTrue("Document should scroll vertically", web!!.scrollY > 0) }
    }
}
