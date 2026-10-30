package com.amaankhan.toolkit.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.amaankhan.toolkit.MainActivity
import com.amaankhan.toolkit.ToolkitViewModel
import com.amaankhan.toolkit.PdfToolViewModel
import com.amaankhan.toolkit.ToolkitApplication
import com.amaankhan.toolkit.downloads.DownloadEntry
import com.amaankhan.toolkit.downloads.MediaFailure
import com.amaankhan.toolkit.downloads.MediaFailureReason
import com.amaankhan.toolkit.downloads.MediaReports
import com.amaankhan.toolkit.downloads.MediaTrace
import kotlinx.coroutines.runBlocking
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ToolkitRepairsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun reviewFrame(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureReview") != "true") return
        compose.waitForIdle()
        android.os.SystemClock.sleep(300)
        val folder = File(compose.activity.cacheDir, "repair-review").apply { mkdirs() }
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try { File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }

    @Test fun transferProgressAndRecoveryActionsRemainReachable() {
        val store = (compose.activity.application as ToolkitApplication).downloads
        val failure = MediaReports.failure(compose.activity, MediaFailure(MediaFailureReason.NETWORK), MediaTrace(), "download", "https://example.test/video")
        val entry = DownloadEntry(kind = "VIDEO", source = "https://example.test/video", name = "Long video sample",
            destination = "content://test/not-exported", state = "FAILED", detail = "Needs attention", bytes = 97_900_000,
            error = failure.message!!)
        runBlocking { store.dao.insert(entry) }
        try {
            compose.onNode(hasText("Transfers") and hasClickAction()).performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText(entry.name).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Save error report").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Retry").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Change folder").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Media engine").performScrollTo().assertIsDisplayed()
            reviewFrame("transfer-failure")
            runBlocking { store.dao.update(entry.copy(state = "RUNNING", error = "", detail = "Downloading · total size unknown", progress = -1, speed = 4_100_000)) }
            compose.waitUntil(10000) { compose.onAllNodesWithText("Pause").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Pause").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("total size unknown", substring = true).assertIsDisplayed()
            reviewFrame("transfer-running")
        } finally { runBlocking { store.dao.remove(entry.id) } }
    }

    @Test fun transfersAndReaderHaveClearSeparatePurposes() {
        compose.onNode(hasText("Transfers") and hasClickAction()).performClick()
        compose.onNodeWithText("New transfer").assertIsDisplayed()
        compose.onNodeWithText("Video & audio").assertDoesNotExist()
        reviewFrame("transfers")
        compose.onNodeWithText("Completed").performClick()
        compose.onNodeWithText("No completed transfers yet").assertIsDisplayed()
        compose.onNode(hasText("Files") and hasClickAction()).performClick()
        compose.onNodeWithText("Open a file").assertIsDisplayed()
        reviewFrame("files-top")
        compose.onNodeWithText("Formats you can open").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("EPUB", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Recently used tools").assertDoesNotExist()
        reviewFrame("files")
    }

    @Test fun imageRowsReorderAndKeepLayoutOptionsOutOfTheWay() {
        lateinit var model: ToolkitViewModel
        val files = (1..2).map { File(compose.activity.cacheDir, "repair-image-$it.png") }
        files.forEach { file -> Bitmap.createBitmap(30, 40, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.CYAN)
            file.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }; recycle()
        } }
        try {
            compose.activityRule.scenario.onActivity { activity -> model = ViewModelProvider(activity)[ToolkitViewModel::class.java]; model.reset(); model.addImages(files.map(Uri::fromFile)) }
            compose.waitUntil(10000) { model.composer.value.images.size == 2 }
            compose.onNodeWithText("Create PDF").performClick()
            compose.onNodeWithText("Images to PDF").performClick()
            compose.onNodeWithText("Page size").assertDoesNotExist()
            reviewFrame("images-selected")
            compose.onNodeWithText("Manage").performClick()
            compose.onNodeWithText(model.composer.value.images.first().name).assertIsDisplayed()
            reviewFrame("arrange-images")
            compose.onNodeWithContentDescription("Move page 2 up").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(Uri.fromFile(files[1]).toString(), model.composer.value.images.first().uri) }
            compose.onNodeWithContentDescription("Remove page 2").performClick()
            compose.onNodeWithText("Done").performClick()
            compose.onNodeWithText("1 image").assertIsDisplayed()
            reviewFrame("single-image")
            compose.onNodeWithText("Page layout").performClick()
            compose.onNodeWithText("Page size").performScrollTo().performClick()
            compose.onNodeWithText("Letter").performClick()
            compose.runOnIdle { assertEquals("Letter", model.composer.value.options.paper.label) }
        } finally { compose.activityRule.scenario.onActivity { model.reset() }; files.forEach { it.delete() } }
    }

    @Test fun protectionOnlyAsksForCurrentPasswordWhenThePdfNeedsIt() {
        lateinit var model: PdfToolViewModel
        val plain = File(compose.activity.cacheDir, "repair-plain.pdf")
        val locked = File(compose.activity.cacheDir, "repair-locked.pdf")
        PDDocument().use { document -> document.addPage(PDPage()); document.save(plain)
            document.protect(StandardProtectionPolicy("owner-secret", "open-secret", AccessPermission())); document.save(locked) }
        try {
            compose.activityRule.scenario.onActivity { activity -> model = ViewModelProvider(activity)["pdf-D03", PdfToolViewModel::class.java]; model.select(listOf(Uri.fromFile(plain)), false) }
            compose.waitUntil(10000) { model.state.value.inputs.isNotEmpty() && !model.state.value.busy }
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("Protect"))
            compose.onNodeWithText("Protect").performClick()
            compose.onNodeWithText("Lock / unlock PDF").performClick()
            compose.onNodeWithText("New password").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Current PDF password").assertDoesNotExist()
            compose.onNodeWithText("Allow printing").assertDoesNotExist()
            reviewFrame("protect-unlocked-input")
            compose.onNodeWithText("What would you like to do?").performScrollTo().performClick()
            compose.onNodeWithText("Remove password").performClick()
            compose.onNodeWithText("Preview result").performClick()
            compose.waitUntil(10000) { model.state.value.result != null || model.state.value.error != null }
            compose.runOnIdle { assertNull(model.state.value.error); assertNotNull(model.state.value.result); model.editResult() }
            compose.activityRule.scenario.onActivity { model.select(listOf(Uri.fromFile(locked)), false) }
            compose.waitUntil(10000) { model.state.value.passwordRequired && !model.state.value.busy }
            compose.onNodeWithText("Current PDF password").performScrollTo().assertIsDisplayed()
        } finally { plain.delete(); locked.delete() }
    }
}
