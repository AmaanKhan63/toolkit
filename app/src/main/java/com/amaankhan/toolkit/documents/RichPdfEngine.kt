package com.amaankhan.toolkit.documents

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Html
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Base64
import kotlinx.coroutines.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.io.File
import java.util.UUID

data class RichPdfRequest(val source: String, val pages: String = "", val sections: String = "", val marginPx: Int = 48, val fontSize: Int = 12, val password: String = "")

/** Local flow layout. Keeps searchable text and paginates every line, including long table cells. */
class RichPdfEngine(private val context: Context, private val directory: File) {
    suspend fun execute(request: RichPdfRequest, progress: (String) -> Unit = {}): PdfResult = withContext(Dispatchers.IO) {
        val document = LocalDocumentReader(context).read(request.source, request.password)
        create(document, request, progress)
    }
    suspend fun create(document: LocalDocument, request: RichPdfRequest, progress: (String) -> Unit = {}): PdfResult = withContext(Dispatchers.IO) {
        directory.mkdirs(); require(request.marginPx in 0..160 && request.fontSize in 8..28)
        if (document.sections.isNotEmpty() && document.sections.all { it.slide != null }) return@withContext createSlides(document, request, progress)
        val output = File(directory, "layout-${UUID.randomUUID()}.pdf")
        try {
            val indices = if (request.sections.isBlank()) document.sections.indices.toList() else PageSelection.parse(request.sections, document.sections.size)
            val slides = document.kind == "Presentation"
            val width = if (slides) 842 else 595; val height = if (slides) 595 else 842
            val margin = (request.marginPx * .75f).toInt(); val contentWidth = width - margin * 2
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(25, 27, 31); textSize = request.fontSize.toFloat(); typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
            var count = 0
            val pdf = PdfDocument()
            var page: PdfDocument.Page? = null
            try {
                var y = margin.toFloat()
                fun nextPage() {
                    page?.let { pdf.finishPage(it) }; page = null; count++; require(count <= 2000) { "This document exceeds 2,000 output pages." }
                    page = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, count).create()); page!!.canvas.drawColor(Color.WHITE); y = margin.toFloat()
                    progress("Laying out page $count")
                }
                fun layout(text: CharSequence, w: Int = contentWidth, size: Float = request.fontSize.toFloat()): StaticLayout {
                    val paint = TextPaint(textPaint).apply { textSize = size }
                    return StaticLayout.Builder.obtain(text, 0, text.length, paint, w.coerceAtLeast(1)).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing(3f, 1f).build()
                }
                suspend fun drawText(text: CharSequence, size: Float = request.fontSize.toFloat()) {
                    if (text.isBlank()) return
                    val textLayout = layout(text, size = size); var line = 0
                    while (line < textLayout.lineCount) {
                        currentCoroutineContext().ensureActive()
                        if (page == null || y + textLayout.getLineBottom(line) - textLayout.getLineTop(line) > height - margin) nextPage()
                        val top = textLayout.getLineTop(line); var end = line
                        while (end < textLayout.lineCount && y + textLayout.getLineBottom(end) - top <= height - margin) end++
                        check(end > line) { "Text is too large for the selected page layout." }
                        val used = textLayout.getLineBottom(end - 1) - top
                        page!!.canvas.save(); page!!.canvas.clipRect(margin.toFloat(), y, (width - margin).toFloat(), y + used)
                        page!!.canvas.translate(margin.toFloat(), y - top); textLayout.draw(page!!.canvas); page!!.canvas.restore()
                        y += used; line = end
                    }
                    y += 8
                }
                suspend fun drawImage(element: Element) {
                    val src = element.attr("src")
                    if (!src.startsWith("data:image/") || !src.contains(";base64,")) return
                    currentCoroutineContext().ensureActive()
                    val data = Base64.decode(src.substringAfter(','), Base64.DEFAULT); require(data.size <= 16 * 1024 * 1024)
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeByteArray(data, 0, data.size, options)
                    require(options.outWidth > 0 && options.outHeight > 0) { "Unsupported image in document." }
                    var sample = 1; while (options.outWidth / sample > 2400 || options.outHeight / sample > 2400) sample *= 2
                    val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return
                    try {
                        val scale = minOf(contentWidth.toFloat() / bitmap.width, (height - 2 * margin - 8).toFloat() / bitmap.height, 1f)
                        val w = bitmap.width * scale; val h = bitmap.height * scale
                        if (page == null || y + h > height - margin) nextPage()
                        page!!.canvas.drawBitmap(bitmap, null, RectF(margin.toFloat(), y, margin + w, y + h), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)); y += h + 10
                    } finally { bitmap.recycle() }
                }
                suspend fun drawTable(table: Element) {
                    val rows = table.select("tr"); val columns = rows.maxOfOrNull { it.children().count { cell -> cell.tagName() in listOf("td", "th") } } ?: 0
                    if (columns == 0) return
                    // Wide tables are split into labeled column bands, never clipped off-page.
                    val perBand = (contentWidth / 85).coerceAtLeast(1)
                    for (first in 0 until columns step perBand) {
                        val last = minOf(columns, first + perBand); val cellWidth = contentWidth / (last - first)
                        if (columns > perBand) drawText("Columns ${first + 1}–$last", 10f)
                        for (row in rows) {
                            currentCoroutineContext().ensureActive()
                            val cells = row.children().filter { it.tagName() in listOf("td", "th") }
                            val layouts = (first until last).map { c -> layout(cells.getOrNull(c)?.wholeText().orEmpty(), cellWidth - 12, request.fontSize.toFloat().coerceAtMost(12f)) }
                            val lines = IntArray(layouts.size)
                            do {
                                if (page == null || y + 30 > height - margin) nextPage()
                                val maxSpace = height - margin - y - 12
                                val ends = layouts.mapIndexed { i, l -> var end = lines[i]; val top = if (end < l.lineCount) l.getLineTop(end) else l.height
                                    while (end < l.lineCount && l.getLineBottom(end) - top <= maxSpace) end++; end }
                                val rowHeight = (layouts.indices.maxOfOrNull { i -> val l = layouts[i]; if (ends[i] > lines[i]) l.getLineBottom(ends[i] - 1) - l.getLineTop(lines[i]) else 0 } ?: 0) + 12
                                val stroke = Paint().apply { color = Color.LTGRAY; style = Paint.Style.STROKE; strokeWidth = .5f }
                                layouts.forEachIndexed { i, l ->
                                    val x = margin + i * cellWidth
                                    page!!.canvas.drawRect(x.toFloat(), y, (x + cellWidth).toFloat(), y + rowHeight, stroke)
                                    if (ends[i] > lines[i]) {
                                        page!!.canvas.save(); page!!.canvas.clipRect((x + 6).toFloat(), y + 6, (x + cellWidth - 6).toFloat(), y + rowHeight - 6)
                                        page!!.canvas.translate((x + 6).toFloat(), y + 6 - l.getLineTop(lines[i])); l.draw(page!!.canvas); page!!.canvas.restore()
                                    }
                                    lines[i] = ends[i]
                                }
                                y += rowHeight
                            } while (layouts.indices.any { lines[it] < layouts[it].lineCount })
                        }
                        y += 12
                    }
                }
                suspend fun render(element: Element) {
                    currentCoroutineContext().ensureActive()
                    when (element.tagName()) {
                        "img" -> drawImage(element)
                        "table" -> drawTable(element)
                        "hr" -> if (element.hasClass("page-break")) { if (page != null) nextPage() }
                        "p", "h1", "h2", "h3", "h4", "h5", "h6", "li", "pre", "blockquote" -> {
                            val copy = element.clone(); copy.select("img").remove()
                            val text = Html.fromHtml(copy.outerHtml(), Html.FROM_HTML_MODE_COMPACT).trim()
                            drawText(if (element.tagName() == "li") "• $text" else text, when (element.tagName()) { "h1" -> 24f; "h2" -> 20f; "h3" -> 16f; else -> request.fontSize.toFloat() })
                            for (image in element.select("img")) drawImage(image)
                        }
                        else -> element.childNodes().forEach { child -> when (child) { is Element -> render(child); is TextNode -> if (child.text().isNotBlank()) drawText(child.text()) } }
                    }
                }
                for ((order, index) in indices.withIndex()) {
                    if (slides && order > 0 && page != null) nextPage()
                    render(Jsoup.parse(document.sections[index].html).body())
                }
                if (page == null) nextPage()
                page?.let(pdf::finishPage)
                page = null
                output.outputStream().use(pdf::writeTo)
            } finally { page?.let { runCatching { pdf.finishPage(it) } }; pdf.close() }
            currentCoroutineContext().ensureActive()
            if (request.pages.isNotBlank()) {
                val selected = PdfToolEngine(context, directory).execute(PdfRequest(PdfOperation.EXTRACT, listOf(Uri.fromFile(output).toString()), request.pages, false))
                output.delete(); selected
            } else PdfResult(output, output, count)
        } catch (failure: Throwable) { output.delete(); throw failure }
    }
    private suspend fun createSlides(document: LocalDocument, request: RichPdfRequest, progress: (String) -> Unit): PdfResult {
        val output = File(directory, "slides-${UUID.randomUUID()}.pdf")
        val indices = if (request.sections.isBlank()) document.sections.indices.toList() else PageSelection.parse(request.sections, document.sections.size)
        val pdf = PdfDocument(); var page: PdfDocument.Page? = null
        try {
            indices.forEachIndexed { order, index ->
                currentCoroutineContext().ensureActive(); progress("Rendering slide ${order + 1}/${indices.size}")
                val slide = requireNotNull(document.sections[index].slide)
                page = pdf.startPage(PdfDocument.PageInfo.Builder(slide.width.toInt(), slide.height.toInt(), order + 1).create())
                slide.draw(page!!.canvas, slide.width.toInt(), slide.height.toInt())
                pdf.finishPage(page); page = null
            }
            output.outputStream().use(pdf::writeTo)
            if (request.pages.isNotBlank()) {
                val subset = PdfToolEngine(context, directory).execute(PdfRequest(PdfOperation.EXTRACT, listOf(Uri.fromFile(output).toString()), request.pages, false))
                output.delete(); return subset
            }
            return PdfResult(output, output, indices.size)
        } catch (error: Throwable) { output.delete(); throw error }
        finally { page?.let { runCatching { pdf.finishPage(it) } }; pdf.close() }
    }
}
