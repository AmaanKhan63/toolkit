package com.amaankhan.toolkit.documents

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import kotlinx.coroutines.*
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.io.File
import java.io.InputStream
import java.net.URI
import java.util.zip.ZipFile

data class DocumentSection(val title: String, val html: String, val slide: SlideLayout? = null)
data class LocalDocument(val title: String, val kind: String, val sections: List<DocumentSection>, val source: String = "", val note: String = "")

fun htmlEscape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
fun markdownHtml(source: String): String {
    val extensions = listOf(TablesExtension.create(), StrikethroughExtension.create())
    return HtmlRenderer.builder().extensions(extensions).escapeHtml(true).sanitizeUrls(true).build().render(Parser.builder().extensions(extensions).build().parse(source))
}
fun safeLocalHtml(source: String): String {
    val document = Jsoup.parse(source)
    document.select("script,iframe,object,embed,form,input,button,link,meta,base,video,audio,source").remove()
    document.allElements.forEach { element ->
        element.attributes().asList().filter { it.key.startsWith("on", true) || it.key in setOf("srcset", "action", "formaction", "ping") }.forEach { element.removeAttr(it.key) }
        if (element.hasAttr("src") && !element.attr("src").startsWith("data:image/")) { element.attr("alt", element.attr("alt").ifBlank { "External image disabled" }); element.removeAttr("src") }
        if (element.hasAttr("href") && !element.attr("href").startsWith("#")) element.removeAttr("href")
    }
    return document.body().html()
}
fun documentHtml(body: String, fontSize: Int = 16) = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src data:; style-src 'unsafe-inline'; font-src 'none'; form-action 'none'; base-uri 'none'"><style>
    body{margin:24px;color:#202126;background:#fff;font:${fontSize}px/1.6 sans-serif;overflow-wrap:anywhere} h1,h2,h3{line-height:1.25} img{max-width:100%;height:auto} table{border-collapse:collapse;max-width:100%;display:block;overflow:auto} td,th{border:1px solid #cdd0d5;padding:8px;min-width:64px;vertical-align:top} th{background:#f2f3f5} pre{white-space:pre-wrap;background:#f4f4f5;padding:16px} blockquote{border-left:3px solid #cdd0d5;margin-left:0;padding-left:16px} .slide{position:relative;aspect-ratio:16/9;min-width:600px;overflow:hidden;background:white} .shape{position:absolute;white-space:pre-wrap;line-height:1.2} hr.page-break{break-after:page;border:0}
    </style></head><body>$body</body></html>"""

class LocalDocumentReader(private val context: Context) {
    fun name(source: String): String = Uri.parse(source).let { uri ->
        if (uri.scheme == "file") File(uri.path.orEmpty()).name else context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "Document"
    }
    private fun open(source: String): InputStream = Uri.parse(source).let { uri -> if (uri.scheme == "file") File(requireNotNull(uri.path)).inputStream() else context.contentResolver.openInputStream(uri) ?: error("This file is no longer accessible.") }
    suspend fun textChunk(source: String, index: Int, size: Int = 65536): Pair<String, Boolean> = withContext(Dispatchers.IO) {
        require(index >= 0)
        open(source).bufferedReader(Charsets.UTF_8).use { reader ->
            var remaining = index.toLong() * size
            while (remaining > 0) { currentCoroutineContext().ensureActive(); val skipped = reader.skip(remaining); if (skipped == 0L) break; remaining -= skipped }
            val chars = CharArray(size + 1); var count = 0
            while (count < chars.size) { currentCoroutineContext().ensureActive(); val read = reader.read(chars, count, chars.size - count); if (read < 0) break; count += read }
            String(chars, 0, minOf(count, size)) to (count > size)
        }
    }
    private suspend fun bounded(input: InputStream, limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(32768)
        input.use { while (true) { currentCoroutineContext().ensureActive(); val size = it.read(buffer); if (size < 0) break; require(output.size() + size <= limit) { "This document is too large for rich layout. Open it as text or choose a smaller file." }; output.write(buffer, 0, size) } }
        return output.toByteArray()
    }
    suspend fun read(source: String, password: String = ""): LocalDocument = withContext(Dispatchers.IO) {
        val title = name(source); val extension = title.substringAfterLast('.', "").lowercase()
        when (extension) {
            "md", "markdown" -> { val text = bounded(open(source), 8 * 1024 * 1024).toString(Charsets.UTF_8); LocalDocument(title, "Markdown", listOf(DocumentSection("Document", safeLocalHtml(markdownHtml(text)))), text) }
            "html", "htm", "xhtml" -> { val text = bounded(open(source), 16 * 1024 * 1024).toString(Charsets.UTF_8); LocalDocument(title, "HTML", listOf(DocumentSection("Document", safeLocalHtml(text))), text, "Scripts and external resources are disabled.") }
            "epub", "docx", "pptx" -> {
                val file = File(context.cacheDir, "reader-${java.util.UUID.randomUUID()}.$extension")
                try {
                    open(source).use { input -> file.outputStream().use { output -> val buffer = ByteArray(65536); var total = 0L
                        while (true) { currentCoroutineContext().ensureActive(); val n = input.read(buffer); if (n < 0) break; total += n; require(total <= 64L * 1024 * 1024) { "Choose a document up to 64 MB." }; output.write(buffer, 0, n) }
                    } }
                    ZipFile(file).use { zip ->
                        var expanded = 0L; var entries = 0
                        zip.entries().asSequence().forEach { entry -> expanded += entry.size.coerceAtLeast(0); entries++; require(expanded <= 128L * 1024 * 1024 && entries <= 10000 && entry.size <= 32L * 1024 * 1024) { "Expanded document is too large." } }
                        when (extension) { "epub" -> epub(title, zip); "docx" -> docx(title, zip); else -> PresentationReader(zip).read(title) }
                    }
                } finally { file.delete() }
            }
            "xlsx", "xls" -> spreadsheet(source, title, password)
            "doc" -> legacyWord(source, title)
            "ppt" -> legacySlides(source, title)
            else -> { val text = bounded(open(source), 8 * 1024 * 1024).toString(Charsets.UTF_8); LocalDocument(title, "Text", listOf(DocumentSection("Document", "<pre>${htmlEscape(text)}</pre>")), text) }
        }
    }
    private suspend fun xml(zip: ZipFile, path: String): org.jsoup.nodes.Document {
        val entry = zip.getEntry(path) ?: error("The document is missing $path.")
        return Jsoup.parse(bounded(zip.getInputStream(entry), 16 * 1024 * 1024).toString(Charsets.UTF_8), "", org.jsoup.parser.Parser.xmlParser())
    }
    private fun resolve(base: String, relative: String): String {
        val uri = URI("https://document.local/$base").resolve(relative).normalize()
        require(uri.scheme == "https" && uri.host == "document.local" && uri.rawQuery == null) { "External document resources are disabled." }
        return uri.path.removePrefix("/")
    }
    private suspend fun image(zip: ZipFile, path: String): String {
        val extension = path.substringAfterLast('.').lowercase()
        val type = when (extension) { "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "gif" -> "image/gif"; "webp" -> "image/webp"; else -> return "" }
        val entry = zip.getEntry(path) ?: return ""
        return "data:$type;base64," + Base64.encodeToString(bounded(zip.getInputStream(entry), 12 * 1024 * 1024), Base64.NO_WRAP)
    }
    private suspend fun relationships(zip: ZipFile, path: String): Map<String, String> {
        val rel = path.substringBeforeLast('/', "").let { if (it.isBlank()) "" else "$it/" } + "_rels/" + path.substringAfterLast('/') + ".rels"
        if (zip.getEntry(rel) == null) return emptyMap()
        return xml(zip, rel).getElementsByTag("Relationship").filter { it.attr("TargetMode") != "External" }.associate { it.attr("Id") to resolve(path, it.attr("Target")) }
    }
    private suspend fun epub(title: String, zip: ZipFile): LocalDocument {
        if (zip.getEntry("META-INF/encryption.xml") != null) {
            val methods = xml(zip, "META-INF/encryption.xml").allElements.filter { it.tagName().substringAfter(':') == "EncryptionMethod" }
            require(methods.isNotEmpty() && methods.all { it.attr("Algorithm") in setOf("http://www.idpf.org/2008/embedding", "http://ns.adobe.com/pdf/enc#RC") }) { "Encrypted EPUB books are not supported." }
            // Font obfuscation is permitted; the reader uses its own local fonts.
        }
        val packagePath = xml(zip, "META-INF/container.xml").getElementsByTag("rootfile").firstOrNull()?.attr("full-path") ?: error("EPUB package is missing.")
        val packageXml = xml(zip, packagePath)
        val manifest = packageXml.getElementsByTag("item").associateBy { it.attr("id") }
        val sections = packageXml.getElementsByTag("itemref").mapIndexed { i, ref ->
            currentCoroutineContext().ensureActive()
            val item = manifest[ref.attr("idref")] ?: error("EPUB chapter is missing.")
            val path = resolve(packagePath, item.attr("href")); val chapter = xml(zip, path)
            for (img in chapter.getElementsByTag("img")) img.attr("src", runCatching { image(zip, resolve(path, img.attr("src"))) }.getOrDefault(""))
            DocumentSection(chapter.getElementsByTag("title").text().ifBlank { chapter.select("h1,h2").firstOrNull()?.text() ?: "Chapter ${i + 1}" }, safeLocalHtml(chapter.outerHtml()))
        }
        require(sections.isNotEmpty()) { "This EPUB has no readable chapters." }
        return LocalDocument(title, "EPUB", sections, note = "Reflowable reading · external resources disabled")
    }
    private suspend fun docx(title: String, zip: ZipFile): LocalDocument {
        val path = "word/document.xml"; val root = xml(zip, path); val rels = relationships(zip, path)
        suspend fun paragraph(p: Element): String = buildString {
            val properties = p.getElementsByTag("w:pPr").firstOrNull()
            val style = properties?.getElementsByTag("w:pStyle")?.firstOrNull()?.attr("w:val").orEmpty()
            val heading = Regex("(?i)heading([1-6])").find(style)?.groupValues?.get(1)
            val tag = if (heading != null) "h$heading" else "p"
            val align = properties?.getElementsByTag("w:jc")?.firstOrNull()?.attr("w:val").orEmpty().let { if (it in listOf("left", "right", "center", "justify")) it else "left" }
            append("<$tag style=\"text-align:$align\">")
            if (properties?.getElementsByTag("w:numPr")?.isNotEmpty() == true) append("• ")
            p.getElementsByTag("w:r").forEach { run ->
                val bold = run.getElementsByTag("w:b").firstOrNull()?.attr("w:val") !in listOf(null, "0", "false")
                val italic = run.getElementsByTag("w:i").firstOrNull()?.attr("w:val") !in listOf(null, "0", "false")
                if (bold) append("<b>"); if (italic) append("<i>")
                run.children().forEach { child -> when (child.tagName()) {
                    "w:t" -> append(htmlEscape(child.wholeText()))
                    "w:tab" -> append("&emsp;")
                    "w:br" -> append(if (child.attr("w:type") == "page") "</$tag><hr class=\"page-break\"><$tag>" else "<br>")
                    "w:drawing", "w:pict" -> {
                        val id = child.getElementsByTag("a:blip").firstOrNull()?.attr("r:embed") ?: child.getElementsByTag("v:imagedata").firstOrNull()?.attr("r:id")
                        val target = rels[id]
                        if (target != null) { val src = image(zip, target); if (src.isNotBlank()) append("<img src=\"$src\" alt=\"Document image\">") }
                    }
                } }
                if (italic) append("</i>"); if (bold) append("</b>")
            }
            append("</$tag>")
        }
        val body = root.getElementsByTag("w:body").firstOrNull() ?: error("No Word document body.")
        val html = buildString { for (element in body.children()) {
            currentCoroutineContext().ensureActive()
            when (element.tagName()) {
                "w:p" -> append(paragraph(element))
                "w:tbl" -> { append("<table>"); element.children().filter { it.tagName() == "w:tr" }.forEach { row -> append("<tr>"); row.children().filter { it.tagName() == "w:tc" }.forEach { cell -> append("<td>"); cell.children().filter { it.tagName() == "w:p" }.forEach { append(paragraph(it)) }; append("</td>") }; append("</tr>") }; append("</table>") }
            }
        } }
        return LocalDocument(title, "Word", listOf(DocumentSection("Document", html)), note = "Local reading layout. Complex page positioning, fields and charts may differ from Word.")
    }
    private suspend fun spreadsheet(source: String, title: String, password: String): LocalDocument {
        OfficeEngine(context, File(context.cacheDir, "office-tmp"))
        return open(source).buffered().use { input -> org.apache.poi.ss.usermodel.WorkbookFactory.create(input, password.ifEmpty { null }).use { workbook ->
            require(workbook.numberOfSheets <= 100)
            val formatter = org.apache.poi.ss.usermodel.DataFormatter().apply { setUseCachedValuesForFormulaCells(true) }
            val sections = (0 until workbook.numberOfSheets).filter { !workbook.isSheetHidden(it) && !workbook.isSheetVeryHidden(it) }.map { i ->
                val sheet = workbook.getSheetAt(i); require(sheet.lastRowNum <= 20000) { "Open a sheet with at most 20,000 rows." }
                val columns = sheet.maxOfOrNull { it.lastCellNum.toInt() }?.coerceAtLeast(0) ?: 0; require(columns <= 256) { "Open a sheet with at most 256 columns." }
                val html = buildString {
                    append("<table><thead><tr><th></th>"); repeat(columns) { c -> append("<th>${org.apache.poi.ss.util.CellReference.convertNumToColString(c)}</th>") }; append("</tr></thead><tbody>")
                    for (row in sheet) { currentCoroutineContext().ensureActive(); append("<tr id=\"row-${row.rowNum + 1}\"><th>${row.rowNum + 1}</th>"); repeat(columns) { c -> val cell = row.getCell(c); append("<td>${htmlEscape(if (cell == null) "" else formatter.formatCellValue(cell))}</td>") }; append("</tr>") }
                    append("</tbody></table>")
                }
                DocumentSection(sheet.sheetName, html)
            }
            LocalDocument(title, "Spreadsheet", sections, note = "Cached formula values · external links and macros never run")
        } }
    }
    private suspend fun legacyWord(source: String, title: String): LocalDocument {
        OfficeEngine(context, File(context.cacheDir, "office-tmp"))
        return open(source).use { org.apache.poi.hwpf.HWPFDocument(it).use { doc ->
            val range = doc.range; require(range.numParagraphs() <= 50000)
            val html = buildString { repeat(range.numParagraphs()) { i -> currentCoroutineContext().ensureActive(); val paragraph = range.getParagraph(i); append("<p>")
                repeat(paragraph.numCharacterRuns()) { j -> val run = paragraph.getCharacterRun(j); if (run.isBold) append("<b>"); if (run.isItalic) append("<i>"); append(htmlEscape(run.text().filter { ch -> ch >= ' ' || ch == '\n' || ch == '\t' })); if (run.isItalic) append("</i>"); if (run.isBold) append("</b>") }; append("</p>")
            } }
            LocalDocument(title, "Word", listOf(DocumentSection("Document", html)), note = "Legacy DOC reading layout. Embedded objects and page positioning may differ.")
        } }
    }
    private suspend fun legacySlides(source: String, title: String): LocalDocument {
        OfficeEngine(context, File(context.cacheDir, "office-tmp"))
        return open(source).use { org.apache.poi.hslf.usermodel.HSLFSlideShow(it).use { presentation ->
            val size = presentation.pageSize
            val sections = presentation.slides.mapIndexed { i, slide ->
                currentCoroutineContext().ensureActive()
                val shapes = mutableListOf<SlideShape>()
                for (shape in slide.shapes) {
                    currentCoroutineContext().ensureActive()
                    val box = shape.anchor
                    if (box.width <= 0 || box.height <= 0) continue
                    val text = (shape as? org.apache.poi.hslf.usermodel.HSLFTextShape)?.text.orEmpty()
                    val bytes = (shape as? org.apache.poi.hslf.usermodel.HSLFPictureShape)?.pictureData?.data
                    val image = if (bytes != null && bytes.size <= 12 * 1024 * 1024) {
                        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                        if (options.outWidth > 0) "data:${options.outMimeType};base64," + Base64.encodeToString(bytes, Base64.NO_WRAP) else ""
                    } else ""
                    val simple = shape as? org.apache.poi.hslf.usermodel.HSLFSimpleShape
                    fun rgb(value: org.apache.poi.java.awt.Color?) = value?.let { "#%06X".format(it.rgb and 0xffffff) }.orEmpty()
                    val font = (shape as? org.apache.poi.hslf.usermodel.HSLFTextShape)?.textParagraphs?.firstOrNull()?.textRuns?.firstOrNull()?.fontSize?.toFloat() ?: 20f
                    shapes += SlideShape(box.x.toFloat(), box.y.toFloat(), box.width.toFloat(), box.height.toFloat(), if (shape.shapeType.name.contains("ELLIPSE")) "ellipse" else "rect",
                        htmlEscape(text).replace("\n", "<br>"), image, rgb(simple?.fillColor), rgb(simple?.lineColor), font, rotation = shape.rotation.toFloat())
                }
                val scene = SlideLayout(size.width.toFloat(), size.height.toFloat(), "#FFFFFF", shapes)
                DocumentSection("Slide ${i + 1}", scene.html(), scene)
            }
            LocalDocument(title, "Presentation", sections, note = "Legacy slide layout · text, images and basic shapes. Charts, groups and effects may differ.")
        } }
    }
}
