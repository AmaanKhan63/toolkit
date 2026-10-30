package com.amaankhan.toolkit.ui

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Process
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.amaankhan.toolkit.MainActivity
import com.amaankhan.toolkit.PdfReaderModel
import com.amaankhan.toolkit.data.ThemeChoice
import com.amaankhan.toolkit.documents.retainDocumentReadPermission
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ReaderAccessRecoveryTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun access(uri: Uri, grant: Boolean) {
        val testPackage = InstrumentationRegistry.getInstrumentation().context.packageName
        compose.runOnIdle {
            compose.activity.startActivity(Intent().apply {
                component = ComponentName(testPackage, ReaderFixtureGrantActivity::class.java.name)
                data = uri
                putExtra("target", compose.activity.packageName)
                putExtra("grant", grant)
            })
        }
        compose.waitUntil(10000) {
            val allowed = compose.activity.checkUriPermission(uri, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED
            allowed == grant
        }
    }

    @Test fun pickerGrantIsPersistedForOriginalUriWithReadAccessOnly() {
        val uri = Uri.parse("content://com.amaankhan.toolkit.test.readerfixtures/document/synthetic.pdf")
        val testPackage = InstrumentationRegistry.getInstrumentation().context.packageName
        var picked: Uri? = null
        var retained = false
        try {
            compose.activityRule.scenario.onActivity { activity -> activity.setContent {
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    picked = result.data?.data
                    retained = picked?.let { retainDocumentReadPermission(activity.contentResolver, it) } == true
                }
                LaunchedEffect(Unit) {
                    picker.launch(Intent().apply {
                        component = ComponentName(testPackage, ReaderFixtureGrantActivity::class.java.name)
                        data = uri
                        putExtra("target", activity.packageName)
                        putExtra("grant", true)
                        putExtra("result", true)
                    })
                }
            } }
            compose.waitUntil(10000) { picked != null }
            assertEquals(uri, picked)
            assertTrue(retained)
            val permission = compose.activity.contentResolver.persistedUriPermissions.single { it.uri == uri }
            assertTrue(permission.isReadPermission)
            assertFalse(permission.isWritePermission)
            assertTrue(compose.activity.contentResolver.openInputStream(uri)!!.use { it.read() } >= 0)
        } finally {
            if (retained) compose.activity.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            access(uri, false)
        }
    }

    @Test fun revokedPdfGrantOffersSafeRecoveryAndRetryReopensOriginalUri() {
        val uri = Uri.parse("content://com.amaankhan.toolkit.test.readerfixtures/document/synthetic.pdf")
        access(uri, true)
        assertTrue(compose.activity.contentResolver.openInputStream(uri)!!.use { it.read() } >= 0)
        access(uri, false)
        assertThrows(SecurityException::class.java) { compose.activity.contentResolver.openInputStream(uri) }
        var chooseCount = 0
        compose.activityRule.scenario.onActivity { activity -> activity.setContent {
            ToolkitTheme(ThemeChoice.LIGHT) { PdfViewerScreen(uri.toString(), {}, onChooseFile = { chooseCount++ }) }
        } }
        compose.waitUntil(10000) { compose.onAllNodesWithText("Choose another file").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Permission Denial", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Choose another file").performClick()
        compose.runOnIdle { assertEquals(1, chooseCount) }
        access(uri, true)
        compose.onNodeWithText("Retry").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithContentDescription("PDF page 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("synthetic.pdf").assertIsDisplayed()
        access(uri, false)
    }

    @Test fun revokedTextGrantWhilePagingOffersRecoveryAndRetryReadsAgain() {
        val uri = Uri.parse("content://com.amaankhan.toolkit.test.readerfixtures/document/synthetic.txt")
        access(uri, true)
        compose.activityRule.scenario.onActivity { activity -> activity.setContent {
            ToolkitTheme(ThemeChoice.LIGHT) { LocalViewerScreen(uri.toString(), {}, onChooseFile = {}) }
        } }
        compose.waitUntil(10000) { compose.onAllNodesWithText("First part", substring = true).fetchSemanticsNodes().isNotEmpty() }
        access(uri, false)
        assertThrows(SecurityException::class.java) { compose.activity.contentResolver.openInputStream(uri) }
        compose.onNodeWithText("Next part").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Choose another file").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Permission Denial", substring = true).assertDoesNotExist()
        access(uri, true)
        compose.onNodeWithText("Retry").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("First part", substring = true).fetchSemanticsNodes().isNotEmpty() }
        access(uri, false)
    }

    @Test fun damagedPdfOffersRecoveryWithoutParserDetails() {
        val file = File(compose.activity.cacheDir, "damaged-reader-fixture.pdf").apply { writeText("Synthetic invalid PDF payload") }
        try {
            compose.activityRule.scenario.onActivity { activity -> activity.setContent {
                ToolkitTheme(ThemeChoice.LIGHT) { PdfViewerScreen(Uri.fromFile(file).toString(), {}) }
            } }
            compose.waitUntil(10000) { compose.onAllNodesWithText("Choose another file").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Retry").assertIsDisplayed()
            compose.onNodeWithText("EOF", substring = true).assertDoesNotExist()
            compose.onNodeWithText(file.name).assertIsDisplayed()
        } finally { file.delete() }
    }

    @Test fun evictedPdfCacheOffersRecoveryAndRetryRebuildsFromOriginalSource() {
        val source = File(compose.activity.cacheDir, "cache-recovery-source.pdf")
        val document = PdfDocument()
        try {
            repeat(2) { index ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(300, 400, index + 1).create())
                document.finishPage(page)
            }
            source.outputStream().use(document::writeTo)
        } finally { document.close() }
        val original = source.readBytes()
        lateinit var model: PdfReaderModel
        try {
            compose.activityRule.scenario.onActivity { activity -> activity.setContent {
                ToolkitTheme(ThemeChoice.LIGHT) { PdfViewerScreen(Uri.fromFile(source).toString(), {}) }
            } }
            compose.waitUntil(15000) { compose.onAllNodesWithContentDescription("PDF page 1").fetchSemanticsNodes().isNotEmpty() }
            compose.runOnIdle { model = ViewModelProvider(compose.activity).get("pdf-reader", PdfReaderModel::class.java) }
            val evicted = requireNotNull(model.state.value.file)
            assertNotEquals(source, evicted)
            assertTrue("The test must evict the prepared copy before paging", evicted.delete())
            assertTrue("Evict the now-empty cache directory too", requireNotNull(evicted.parentFile).delete())

            compose.onNodeWithContentDescription("Next page").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Choose another file").assertIsDisplayed()
            compose.onNodeWithText(source.name).assertIsDisplayed()
            compose.runOnIdle { assertNull(model.state.value.file) }

            compose.onNodeWithText("Retry").performClick()
            compose.waitUntil(15000) { compose.onAllNodesWithContentDescription("PDF page 1").fetchSemanticsNodes().isNotEmpty() }
            val rebuilt = requireNotNull(model.state.value.file)
            assertNotEquals(evicted, rebuilt)
            assertTrue(rebuilt.isFile)
            compose.runOnIdle {
                model.renderFailed(evicted, "A previous preview failed.")
                assertEquals("A late failure must not discard the rebuilt preview", rebuilt, model.state.value.file)
            }
            compose.onNodeWithContentDescription("Next page").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("PDF page 2").fetchSemanticsNodes().isNotEmpty() }
            assertArrayEquals(original, source.readBytes())
        } finally { source.delete() }
    }
}
