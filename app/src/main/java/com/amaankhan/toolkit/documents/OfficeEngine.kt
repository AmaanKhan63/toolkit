package com.amaankhan.toolkit.documents

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import kotlinx.coroutines.*
import org.apache.poi.poifs.crypt.*
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.openxml4j.opc.PackageAccess
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
const val XLS_MIME = "application/vnd.ms-excel"
enum class OfficeAction { LOCK_FILE, UNLOCK_FILE, LOCK_EDITING, UNLOCK_EDITING, TO_PDF }
data class OfficeRequest(val source: String, val action: OfficeAction, val password: String = "", val newPassword: String = "",
    val sheets: String = "", val pages: String = "", val allPages: Boolean = true)

/** Password encryption wraps the original OOXML bytes; it does not resave the workbook. */
class OfficeEngine(private val context: Context, private val directory: File) {
    companion object {
        private val binaryProvider by lazy {
            WorkbookFactory.addProvider(org.apache.poi.hssf.usermodel.HSSFWorkbookFactory())
            true
        }
    }
    init {
        check(binaryProvider)
        System.setProperty("org.apache.poi.javax.xml.stream.XMLInputFactory", "com.fasterxml.aalto.stax.InputFactoryImpl")
        System.setProperty("org.apache.poi.javax.xml.stream.XMLOutputFactory", "com.fasterxml.aalto.stax.OutputFactoryImpl")
        System.setProperty("org.apache.poi.javax.xml.stream.XMLEventFactory", "com.fasterxml.aalto.stax.EventFactoryImpl")
        org.apache.poi.util.TempFile.setTempFileCreationStrategy(org.apache.poi.util.DefaultTempFileCreationStrategy(File(context.cacheDir, "office-tmp").apply { mkdirs() }))
    }
    private suspend fun copy(input: InputStream, output: OutputStream, limit: Long = 64L * 1024 * 1024) {
        val buffer = ByteArray(65536); var total = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer); if (count == -1) break
            total += count; require(total <= limit) { "This workbook exceeds the 64 MB processing limit." }
            output.write(buffer, 0, count)
        }
    }
    private suspend fun plainPackage(input: File, password: String, plain: File): Boolean {
        val ole = input.inputStream().use { it.read() == 0xd0 }
        if (!ole) { input.copyTo(plain, overwrite = true); return false }
        POIFSFileSystem(input, true).use { fs ->
            require(fs.root.hasEntry("EncryptedPackage")) { "Choose an XLSX workbook. Legacy XLS protection is not supported here." }
            require(password.isNotEmpty()) { "Enter the current file-open password." }
            val decryptor = Decryptor.getInstance(EncryptionInfo(fs))
            require(decryptor.verifyPassword(password)) { "The current file-open password is incorrect." }
            decryptor.getDataStream(fs).use { decoded -> plain.outputStream().use { copy(decoded, it) } }
        }
        return true
    }
    private fun validatePackage(file: File) {
        ZipFile(file).use { zip ->
            require(zip.getEntry("xl/workbook.xml") != null && zip.getEntry("[Content_Types].xml") != null) { "Choose a valid XLSX workbook." }
            var size = 0L; var count = 0
            for (entry in zip.entries()) {
                count++; size += entry.size.coerceAtLeast(0)
                require(count <= 10000 && size <= 128L * 1024 * 1024 && entry.size <= 32L * 1024 * 1024) { "The expanded workbook is too large." }
                require(!entry.name.startsWith("_xmlsignatures/") && entry.name != "xl/vbaProject.bin") { "Signed or macro-enabled workbooks are not supported by this tool." }
            }
        }
    }
    suspend fun execute(request: OfficeRequest, progress: (String) -> Unit = {}): PdfResult {
        val created = mutableListOf<File>()
        fun temp(suffix: String): File = File.createTempFile("office-", suffix, directory).also { created += it }
        try {
            return withContext(Dispatchers.IO) {
                directory.mkdirs()
                require(directory.usableSpace > 128L * 1024 * 1024) { "Free at least 128 MB of storage and try again." }
                val input = temp(".input"); val plain = temp(".xlsx")
                val uri = Uri.parse(request.source)
                val stream = if (uri.scheme == "file") File(requireNotNull(uri.path)).inputStream() else context.contentResolver.openInputStream(uri)
                requireNotNull(stream) { "Select the file again; it is no longer available." }.use { source -> input.outputStream().use { copy(source, it) } }
                progress("Opening workbook…")
                val legacy = input.inputStream().use { it.read() == 0xd0 } && POIFSFileSystem(input, true).use { it.root.hasEntry("Workbook") || it.root.hasEntry("Book") }
                if (legacy) {
                    val result = executeLegacy(input, request, ::temp, progress)
                    created.filter { it != result.file && it != result.preview }.forEach(File::delete)
                    return@withContext result
                }
                val encrypted = plainPackage(input, request.password, plain)
                validatePackage(plain)
                val output = temp(if (request.action == OfficeAction.TO_PDF) ".pdf" else ".xlsx")
                var preview: File
                when (request.action) {
                    OfficeAction.LOCK_FILE -> {
                        require(request.newPassword.isNotEmpty() && request.newPassword.length <= 255) { "Enter a new password of 1–255 characters." }
                        progress("Encrypting workbook…")
                        POIFSFileSystem().use { fs ->
                            val encryptor = EncryptionInfo(EncryptionMode.agile, CipherAlgorithm.aes256, HashAlgorithm.sha512, 256, 16, ChainingMode.cbc).encryptor
                            encryptor.confirmPassword(request.newPassword)
                            encryptor.getDataStream(fs).use { encoded -> plain.inputStream().use { copy(it, encoded) } }
                            output.outputStream().use { fs.writeFilesystem(it) }
                        }
                        val verified = temp(".xlsx")
                        plainPackage(output, request.newPassword, verified)
                        require(sha(verified).contentEquals(sha(plain))) { "The encrypted workbook failed verification." }
                    }
                    OfficeAction.UNLOCK_FILE -> {
                        require(encrypted) { "This workbook has no file-open password. Choose editing protection to remove sheet restrictions." }
                        plain.copyTo(output, overwrite = true)
                    }
                    OfficeAction.LOCK_EDITING, OfficeAction.UNLOCK_EDITING -> rewriteProtection(plain, output, request.action == OfficeAction.LOCK_EDITING, request.newPassword)
                    OfficeAction.TO_PDF -> Unit
                }
                currentCoroutineContext().ensureActive()
                progress("Preparing workbook preview…")
                preview = if (request.action == OfficeAction.TO_PDF) output else temp(".pdf")
                val renderInput = if (request.action in setOf(OfficeAction.LOCK_EDITING, OfficeAction.UNLOCK_EDITING)) output else plain
                var pages = renderTables(renderInput, preview, request.sheets, progress)
                if (request.action == OfficeAction.TO_PDF && !request.allPages) {
                    val selected = PdfToolEngine(context, directory).execute(PdfRequest(PdfOperation.EXTRACT, listOf(Uri.fromFile(output).toString()), request.pages, false))
                    try { selected.file.copyTo(output, overwrite = true); pages = selected.pages } finally { selected.delete() }
                }
                created.filter { it != output && it != preview }.forEach { it.delete() }
                PdfResult(output, preview, pages, if (request.action == OfficeAction.TO_PDF) "application/pdf" else XLSX_MIME)
            }
        } catch (failure: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) { created.forEach { it.delete() } }; throw failure
        }
    }
    private fun sha(file: File) = java.security.MessageDigest.getInstance("SHA-256").run {
        file.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { val n = input.read(buffer); if (n < 0) break; update(buffer, 0, n) } }; digest()
    }
    private suspend fun executeLegacy(input: File, request: OfficeRequest, temp: (String) -> File, progress: (String) -> Unit): PdfResult {
        val plain = temp(".xls")
        val output = temp(if (request.action == OfficeAction.TO_PDF) ".pdf" else ".xls")
        // POI's binary format password is thread-local. Keep each use synchronous
        // and clear it before any suspension can switch coroutine threads.
        org.apache.poi.hssf.record.crypto.Biff8EncryptionKey.setCurrentUserPassword(request.password.ifEmpty { null })
        val workbook = try { input.inputStream().use { org.apache.poi.hssf.usermodel.HSSFWorkbook(it, true) } }
            finally { org.apache.poi.hssf.record.crypto.Biff8EncryptionKey.setCurrentUserPassword(null) }
        workbook.use { book ->
            val encrypted = book.encryptionInfo != null
            if (request.action == OfficeAction.UNLOCK_FILE) require(encrypted) { "This workbook has no file-open password." }
            if (request.action in setOf(OfficeAction.LOCK_FILE, OfficeAction.LOCK_EDITING)) require(request.newPassword.length in 1..15) { "Legacy XLS passwords must have 1–15 characters. XLSX supports longer passwords." }
            if (request.action in setOf(OfficeAction.LOCK_EDITING, OfficeAction.UNLOCK_EDITING)) {
                val lock = request.action == OfficeAction.LOCK_EDITING
                book.forEach { it.protectSheet(if (lock) request.newPassword else null) }
                val records = book.internalWorkbook.records
                val protect = records.filterIsInstance<org.apache.poi.hssf.record.ProtectRecord>().firstOrNull()
                val password = records.filterIsInstance<org.apache.poi.hssf.record.PasswordRecord>().firstOrNull()
                if (protect != null) protect.protect = lock
                else records.add(records.indexOfFirst { it is org.apache.poi.hssf.record.WindowOneRecord }.coerceAtLeast(1), org.apache.poi.hssf.record.ProtectRecord(lock))
                val hash = if (lock) CryptoFunctions.createXorVerifier1(request.newPassword) else 0
                if (password != null) password.password = hash
                else records.add(records.indexOfFirst { it is org.apache.poi.hssf.record.ProtectRecord } + 1, org.apache.poi.hssf.record.PasswordRecord(hash))
                if (!lock) book.unwriteProtectWorkbook()
            }
            plain.outputStream().use(book::write)
            if (request.action != OfficeAction.TO_PDF) {
                if (request.action == OfficeAction.LOCK_FILE) {
                    book.encryptionMode = EncryptionMode.binaryRC4
                    org.apache.poi.hssf.record.crypto.Biff8EncryptionKey.setCurrentUserPassword(request.newPassword)
                    try { output.outputStream().use(book::write) } finally { org.apache.poi.hssf.record.crypto.Biff8EncryptionKey.setCurrentUserPassword(null) }
                    org.apache.poi.hssf.record.crypto.Biff8EncryptionKey.setCurrentUserPassword(request.newPassword)
                    try { output.inputStream().use { org.apache.poi.hssf.usermodel.HSSFWorkbook(it).use { verified -> require(verified.numberOfSheets == book.numberOfSheets && verified.encryptionInfo != null) } } }
                    finally { org.apache.poi.hssf.record.crypto.Biff8EncryptionKey.setCurrentUserPassword(null) }
                } else plain.copyTo(output, overwrite = true)
            }
        }
        val preview = if (request.action == OfficeAction.TO_PDF) output else temp(".pdf")
        var pages = renderTables(plain, preview, request.sheets, progress)
        if (request.action == OfficeAction.TO_PDF && !request.allPages) {
            val selected = PdfToolEngine(context, directory).execute(PdfRequest(PdfOperation.EXTRACT, listOf(Uri.fromFile(output).toString()), request.pages, false))
            try { selected.file.copyTo(output, overwrite = true); pages = selected.pages } finally { selected.delete() }
        }
        return PdfResult(output, preview, pages, if (request.action == OfficeAction.TO_PDF) "application/pdf" else XLS_MIME)
    }
    private suspend fun rewriteProtection(source: File, output: File, protect: Boolean, password: String) {
        if (protect) require(password.isNotEmpty()) { "Enter the editing protection password." }
        ZipFile(source).use { zip -> ZipOutputStream(output.outputStream().buffered()).use { target ->
            for (entry in zip.entries()) {
                currentCoroutineContext().ensureActive()
                target.putNextEntry(ZipEntry(entry.name))
                val workbook = entry.name == "xl/workbook.xml"
                val sheet = entry.name.matches(Regex("xl/worksheets/[^/]+\\.xml"))
                if (workbook || sheet) {
                    // Android's DOM factory does not implement Xerces security feature names.
                    // Reject DTDs with its non-resolving pull parser before constructing a DOM.
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    val guard = android.util.Xml.newPullParser().apply {
                        setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
                        setInput(bytes.inputStream(), null)
                    }
                    while (guard.nextToken() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                        require(guard.eventType != org.xmlpull.v1.XmlPullParser.DOCDECL) { "Workbook XML cannot contain document type declarations." }
                    }
                    val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true; isExpandEntityReferences = false }
                    val builder = factory.newDocumentBuilder().apply { setEntityResolver { _, _ -> throw org.xml.sax.SAXException("External XML entities are not supported.") } }
                    val doc = bytes.inputStream().use { builder.parse(it) }
                    val root = doc.documentElement; val ns = root.namespaceURI
                    val name = if (workbook) "workbookProtection" else "sheetProtection"
                    val old = root.getElementsByTagNameNS(ns, name)
                    (0 until old.length).map { old.item(it) }.forEach { node -> node.parentNode?.removeChild(node) }
                    if (protect) {
                        val node = doc.createElementNS(ns, name)
                        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
                        val hash = CryptoFunctions.hashPassword(password, HashAlgorithm.sha512, salt, 100000, false)
                        val prefix = if (workbook) "workbook" else ""
                        fun attr(key: String, value: String) { node.setAttribute(if (workbook) prefix + key.replaceFirstChar { it.uppercaseChar() } else key, value) }
                        attr("algorithmName", "SHA-512"); attr("hashValue", Base64.getEncoder().encodeToString(hash)); attr("saltValue", Base64.getEncoder().encodeToString(salt)); attr("spinCount", "100000")
                        node.setAttribute(if (workbook) "lockStructure" else "sheet", "1")
                        if (!workbook) { node.setAttribute("objects", "1"); node.setAttribute("scenarios", "1") }
                        val preceding = if (workbook) setOf("fileVersion", "fileSharing", "workbookPr") else setOf("sheetPr", "dimension", "sheetViews", "sheetFormatPr", "cols", "sheetData", "sheetCalcPr")
                        var before = root.firstChild
                        while (before != null && (before.nodeType != org.w3c.dom.Node.ELEMENT_NODE || before.localName in preceding)) before = before.nextSibling
                        root.insertBefore(node, before)
                    }
                    TransformerFactory.newInstance().newTransformer().transform(DOMSource(doc), StreamResult(target))
                } else zip.getInputStream(entry).use { copy(it, target, 128L * 1024 * 1024) }
                target.closeEntry()
            }
        } }
        validatePackage(output)
    }
    /** Deliberately labelled table layout; no claim to emulate Excel's print engine. */
    private suspend fun renderTables(source: File, target: File, sheetRange: String, progress: (String) -> Unit): Int {
        source.inputStream().buffered().use { WorkbookFactory.create(it) }.use { workbook ->
            require(workbook.numberOfSheets in 1..100) { "Preview supports 1–100 worksheets." }
            val selected = if (sheetRange.isBlank()) (0 until workbook.numberOfSheets).filter { !workbook.isSheetHidden(it) && !workbook.isSheetVeryHidden(it) }
                else PageSelection.parse(sheetRange, workbook.numberOfSheets)
            require(selected.isNotEmpty()) { "Select at least one visible worksheet." }
            val formatter = DataFormatter(Locale.getDefault()).apply { setUseCachedValuesForFormulaCells(true) }
            val ink = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 9f; typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
            val line = Paint().apply { color = Color.LTGRAY; strokeWidth = .5f }
            var count = 0
            val pdf = PdfDocument()
            try {
                for (sheetIndex in selected) {
                    currentCoroutineContext().ensureActive()
                    val sheet = workbook.getSheetAt(sheetIndex)
                    require(sheet.lastRowNum < 20000) { "Preview supports up to 20,000 rows per sheet." }
                    var columns = 1
                    for (row in sheet) columns = maxOf(columns, row.lastCellNum.toInt())
                    require(columns <= 256) { "Preview supports up to 256 columns per sheet." }
                    // Paginate wide sheets in groups instead of shrinking cells into unreadable text.
                    val widths = (0 until columns).map { (sheet.getColumnWidth(it) / 256f * 5f).coerceIn(64f, 180f) }
                    val bands = mutableListOf<IntRange>(); var start = 0
                    while (start < columns) { var end = start; var width = 0f; while (end < columns && (width + widths[end] <= 523f || end == start)) { width += widths[end]; end++ }; bands += start until end; start = end }
                    for (band in bands) {
                        var rowIndex = sheet.firstRowNum
                        var rowLine = 0
                        do {
                            currentCoroutineContext().ensureActive()
                            require(count < 2000) { "Preview exceeds 2,000 pages. Select fewer sheets." }
                            val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, ++count).create())
                            val canvas = page.canvas
                            ink.textSize = 12f; ink.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                            canvas.drawText(sheet.sheetName.take(70), 36f, 40f, ink)
                            ink.textSize = 9f; ink.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                            var y = 58f
                            do {
                                val row = sheet.getRow(rowIndex)
                                if (row?.zeroHeight != true) {
                                    val layouts = band.map { col ->
                                        val value = row?.getCell(col)?.let { formatter.formatCellValue(it) }.orEmpty()
                                        android.text.StaticLayout.Builder.obtain(value, 0, value.length, ink, (widths[col] - 8).toInt())
                                            .setIncludePad(false).build()
                                    }
                                    val lines = layouts.maxOf { it.lineCount }.coerceAtLeast(1)
                                    val lineHeight = kotlin.math.ceil(ink.fontSpacing) + 2f
                                    val availableLines = ((790f - y - 8f) / lineHeight).toInt()
                                    if (availableLines < 1) break
                                    val remaining = lines - rowLine
                                    if (rowLine == 0 && remaining > availableLines && remaining * lineHeight + 8 < 732 && y > 58) break
                                    val take = minOf(remaining, availableLines)
                                    val height = maxOf(take * lineHeight + 8, (row?.heightInPoints ?: 18f).coerceIn(18f, 90f)).coerceAtMost(790 - y)
                                    var x = 36f
                                    for ((offset, col) in band.withIndex()) {
                                        val width = widths[col]
                                        val layout = layouts[offset]
                                        if (rowLine < layout.lineCount) {
                                            val lineTop = layout.getLineTop(rowLine)
                                            val textHeight = layout.getLineBottom(minOf(rowLine + take, layout.lineCount) - 1) - lineTop
                                            canvas.save(); canvas.clipRect(x + 3, y + 3, x + width - 3, y + 4 + textHeight)
                                            canvas.translate(x + 4, y + 4 - lineTop); layout.draw(canvas); canvas.restore()
                                        }
                                        canvas.drawLine(x, y, x, y + height, line); x += width
                                    }
                                    canvas.drawLine(36f, y, x, y, line); canvas.drawLine(x, y, x, y + height, line)
                                    y += height; canvas.drawLine(36f, y, x, y, line)
                                    rowLine += take
                                    if (rowLine < lines) break
                                }
                                rowIndex++; rowLine = 0
                            } while (rowIndex <= sheet.lastRowNum)
                            canvas.drawText("${sheetIndex + 1} · ${count}", 36f, 815f, ink)
                            pdf.finishPage(page); progress("Preparing page $count…")
                        } while (rowIndex <= sheet.lastRowNum)
                    }
                }
                target.outputStream().use { pdf.writeTo(it) }
            } finally { pdf.close() }
            return count
        }
    }
}
