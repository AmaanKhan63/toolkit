package com.amaankhan.toolkit.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import com.amaankhan.toolkit.MainActivity
import com.amaankhan.toolkit.ToolkitViewModel
import com.amaankhan.toolkit.RunPhase
import com.amaankhan.toolkit.WorkflowEditorModel
import com.amaankhan.toolkit.documents.WorkflowOperation
import org.junit.Rule
import org.junit.Test
import java.io.File

class ToolkitNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun reveal(text: String, clickable: Boolean = false): SemanticsNodeInteraction {
        val target = if (clickable) hasText(text) and hasClickAction() else hasText(text)
        compose.onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.ScrollToIndex)).performScrollToNode(target)
        return compose.onNode(target)
    }
    private fun reviewFrame(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureReview") != "true") return
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        // Semantics can settle before SurfaceFlinger presents the new frame.
        android.os.SystemClock.sleep(300)
        val directory = File(compose.activity.cacheDir, "design-review").apply { mkdirs() }
        val screen = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try { File(directory, "$name.png").outputStream().use { stream -> screen.compress(Bitmap.CompressFormat.PNG, 100, stream) } }
        finally { screen.recycle() }
    }
    @Test fun categoriesSettingsAndSavedReplaceEmptyHomeSections() {
        compose.onNodeWithText("Toolkit").assertIsDisplayed()
        compose.onNodeWithText("Recent").assertDoesNotExist()
        compose.onNodeWithText("Favorites").assertDoesNotExist()
        reviewFrame("home")
        compose.onNodeWithText("Create PDF").assertIsDisplayed().performClick()
        compose.onNodeWithText("Images to PDF").assertIsDisplayed()
        compose.onNodeWithText("Images to PDF").performClick()
        compose.onNodeWithText("Choose images").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Themes").performClick()
        reviewFrame("themes")
        reveal("Jade", true).performClick()
        reveal("Platinum", true).performClick()
        reveal("Appearance", true).performClick()
        compose.onAllNodesWithText("Dark").onLast().performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("User profile").performClick()
        compose.onNodeWithText("Display name").assertIsDisplayed()
        compose.onNodeWithText("Save profile").assertIsDisplayed()
        reviewFrame("profile")
    }
    @Test fun twentyImagesKeepLayoutReachableAndPreviewUsesRealGeneratedPdf() {
        lateinit var model: ToolkitViewModel
        val fixtures = mutableListOf<File>()
        compose.activityRule.scenario.onActivity { activity ->
            model = ViewModelProvider(activity)[ToolkitViewModel::class.java]
            model.reset()
            repeat(20) { index ->
                val file = File(activity.cacheDir, "ui-image-$index.png")
                Bitmap.createBitmap(60, 80, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(if (index % 2 == 0) Color.RED else Color.BLUE)
                    file.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }; recycle()
                }
                fixtures += file
            }
            model.addImages(fixtures.map(Uri::fromFile))
        }
        compose.waitUntil(10000) { model.composer.value.images.size == 20 }
        compose.onNodeWithText("Create PDF").performClick()
        compose.onNodeWithText("Images to PDF").performClick()
        compose.onNodeWithText("20 images").assertIsDisplayed()
        reviewFrame("twenty-images")
        compose.onNodeWithText("Manage").performClick()
        compose.onNodeWithText("Arrange images").assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Page layout").performClick()
        compose.onNodeWithText("Page size").performScrollTo().performClick()
        compose.onNodeWithText("Letter").performClick()
        compose.onNodeWithText("Preview PDF").performClick()
        compose.waitUntil(30000) { model.composer.value.phase == RunPhase.READY }
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("PDF page 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("PDF page 1").assertIsDisplayed()
        reviewFrame("pdf-preview")
        compose.onNodeWithContentDescription("Next page").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("PDF page 2").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Save as").assertIsDisplayed()
        compose.onNodeWithText("Back to edit").performClick()
        compose.onNodeWithText("20 images").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { model.reset() }
        fixtures.forEach { it.delete() }
    }
    @Test fun workflowCanStartWithExcelAndSavesCompatibleSteps() {
        reveal("Workflows", true).performClick()
        compose.onNode(hasText("Workflows") and hasClickAction()).performClick()
        compose.onNodeWithText("Use Excel → protected PDF example").performClick()
        lateinit var editor: WorkflowEditorModel
        compose.activityRule.scenario.onActivity { editor = ViewModelProvider(it)[WorkflowEditorModel::class.java] }
        compose.runOnIdle {
            org.junit.Assert.assertEquals(listOf(WorkflowOperation.EXCEL_UNLOCK, WorkflowOperation.EXCEL_PDF, WorkflowOperation.PDF_LOCK), editor.template.value.steps.map { it.operation })
        }
        compose.onNodeWithText("Choose input").assertIsEnabled()
        reviewFrame("excel-workflow")
        compose.onNodeWithText("Save workflow").assertIsDisplayed().performClick()
        val id = editor.template.value.id
        compose.waitUntil(10000) { editor.saved.value.any { it.id == id } }
        reveal("Workflow saved. Passwords will be requested each run.")
        compose.onNodeWithText("Workflow saved. Passwords will be requested each run.").assertIsDisplayed()
        compose.runOnIdle { editor.move(1, -1) }
        compose.onNodeWithText("Choose input").assertIsNotEnabled()
        compose.runOnIdle { editor.delete(id) }
        compose.waitUntil(10000) { editor.saved.value.none { it.id == id } }
    }
    @Test fun everySelectedDocumentToolOpensAnOperationalScreen() {
        for (group in toolGroups) {
            reveal(group.title, true).performClick()
            for (tool in documentTools.filter { it.id in group.ids }) {
                reveal(tool.name, true).performClick()
                compose.onNodeWithText("In development").assertDoesNotExist()
                compose.onNodeWithText("Coming soon").assertDoesNotExist()
                reviewFrame(tool.id)
                compose.onNodeWithContentDescription("Back").assertIsDisplayed().performClick()
            }
            compose.onNodeWithContentDescription("Back").performClick()
        }
    }
    @Test fun allDownloaderSourcesHaveFormsAndTelegramKeepsCredentialsLocal() {
        reveal("Download tools", true).performClick()
        compose.onNodeWithText("Video & audio").performClick()
        compose.onNodeWithText("Inspect formats / playlist").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Add to queue").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Direct link").performClick()
        compose.onNodeWithText("Filename (optional for a single link)").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Torrent / magnet").performClick()
        compose.onNodeWithText("Read torrent files").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Telegram").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Connect your Telegram account").fetchSemanticsNodes().isNotEmpty() }
        reveal("API ID").assertIsDisplayed()
        reveal("API hash").assertIsDisplayed()
        reveal("Connect").assertIsNotEnabled()
        reviewFrame("telegram-credentials")
    }
}
