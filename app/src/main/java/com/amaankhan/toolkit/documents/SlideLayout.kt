package com.amaankhan.toolkit.documents

import android.graphics.*
import android.text.Html
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Base64
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI
import java.util.zip.ZipFile

data class SlideShape(val x: Float, val y: Float, val width: Float, val height: Float, val kind: String,
    val html: String = "", val image: String = "", val fill: String = "", val stroke: String = "",
    val fontSize: Float = 20f, val color: String = "#202124", val align: String = "left", val rotation: Float = 0f)
data class SlideLayout(val width: Float, val height: Float, val background: String, val shapes: List<SlideShape>) {
    fun html(): String = buildString {
        // SVG scales the complete slide, including text, to the viewport without JS.
        // A fixed CSS width clipped the right side on portrait phones.
        append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $width $height\" width=\"$width\" height=\"$height\" style=\"display:block;width:100%;height:auto;background:$background;overflow:hidden\">")
        shapes.forEach { shape ->
            append("<foreignObject x=\"${shape.x}\" y=\"${shape.y}\" width=\"${shape.width}\" height=\"${shape.height}\" transform=\"rotate(${shape.rotation} ${shape.x + shape.width / 2} ${shape.y + shape.height / 2})\"><div xmlns=\"http://www.w3.org/1999/xhtml\" style=\"box-sizing:border-box;width:100%;height:100%;font-size:${shape.fontSize}px;color:${shape.color};text-align:${shape.align};line-height:1.15;")
            if (shape.fill.isNotBlank()) append("background:${shape.fill};")
            if (shape.stroke.isNotBlank()) append("border:1px solid ${shape.stroke};")
            if (shape.kind == "ellipse") append("border-radius:50%;")
            if (shape.kind == "roundRect") append("border-radius:12px;")
            append("\">")
            if (shape.image.isNotEmpty()) append("<img style=\"width:100%;height:100%;object-fit:contain\" src=\"${shape.image}\">")
            else append(shape.html)
            append("</div></foreignObject>")
        }
        append("</svg>")
    }
    suspend fun draw(canvas: Canvas, pageWidth: Int, pageHeight: Int) {
        canvas.drawColor(Color.parseColor(background))
        val scale = minOf(pageWidth / width, pageHeight / height)
        canvas.save(); canvas.translate((pageWidth - width * scale) / 2, (pageHeight - height * scale) / 2); canvas.scale(scale, scale)
        try { shapes.forEach { shape ->
            currentCoroutineContext().ensureActive()
            canvas.save()
            try {
                canvas.translate(shape.x, shape.y); canvas.rotate(shape.rotation, shape.width / 2, shape.height / 2)
                val bounds = RectF(0f, 0f, shape.width, shape.height)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                fun outline() { when (shape.kind) { "ellipse" -> canvas.drawOval(bounds, paint); "roundRect" -> canvas.drawRoundRect(bounds, 10f, 10f, paint); "line" -> canvas.drawLine(0f, 0f, shape.width, shape.height, paint); else -> canvas.drawRect(bounds, paint) } }
                if (shape.fill.isNotBlank()) { paint.color = Color.parseColor(shape.fill); outline() }
                if (shape.stroke.isNotBlank()) { paint.color = Color.parseColor(shape.stroke); paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f; outline() }
                if (shape.image.isNotEmpty()) {
                    val bytes = Base64.decode(shape.image.substringAfter(','), Base64.DEFAULT)
                    val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeByteArray(bytes, 0, bytes.size, size)
                    var sample = 1; while (size.outWidth / sample > 2800 || size.outHeight / sample > 2800) sample *= 2
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    if (bitmap != null) try {
                        val fit = minOf(shape.width / bitmap.width, shape.height / bitmap.height)
                        val w = bitmap.width * fit; val h = bitmap.height * fit
                        canvas.drawBitmap(bitmap, null, RectF((shape.width - w) / 2, (shape.height - h) / 2, (shape.width + w) / 2, (shape.height + h) / 2), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                    } finally { bitmap.recycle() }
                } else if (shape.html.isNotBlank()) {
                    val text = Html.fromHtml(shape.html, Html.FROM_HTML_MODE_COMPACT)
                    val ink = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(shape.color); textSize = shape.fontSize; typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
                    val layout = StaticLayout.Builder.obtain(text, 0, text.length, ink, shape.width.toInt().coerceAtLeast(1)).setIncludePad(false)
                        .setAlignment(when (shape.align) { "center" -> Layout.Alignment.ALIGN_CENTER; "right" -> Layout.Alignment.ALIGN_OPPOSITE; else -> Layout.Alignment.ALIGN_NORMAL }).build()
                    // Text remains searchable in the PDF; fixed geometry follows the slide.
                    canvas.clipRect(bounds); layout.draw(canvas)
                }
            } finally { canvas.restore() }
        } } finally { canvas.restore() }
    }
}

/** OOXML slide geometry without desktop AWT or any remote rendering service. */
class PresentationReader(private val zip: ZipFile) {
    private fun xml(path: String): org.jsoup.nodes.Document {
        val entry = zip.getEntry(path) ?: error("Missing presentation part: $path")
        require(entry.size in 0..16L * 1024 * 1024) { "Presentation part is too large." }
        return zip.getInputStream(entry).bufferedReader().use { Jsoup.parse(it.readText(), "", org.jsoup.parser.Parser.xmlParser()) }
    }
    private fun related(path: String): Map<String, String> {
        val rel = path.substringBeforeLast('/') + "/_rels/" + path.substringAfterLast('/') + ".rels"
        if (zip.getEntry(rel) == null) return emptyMap()
        return xml(rel).getElementsByTag("Relationship").filter { it.attr("TargetMode") != "External" }.associate {
            val uri = URI("https://local.invalid/$path").resolve(it.attr("Target")).normalize()
            require(uri.host == "local.invalid" && uri.scheme == "https")
            it.attr("Id") to uri.path.removePrefix("/")
        }
    }
    private fun Element.first(tag: String) = getElementsByTag(tag).firstOrNull()
    private fun Float.safe(default: Float) = if (isFinite()) this else default
    private fun Element.number(attribute: String, default: Float = 0f) = attr(attribute).toFloatOrNull()?.safe(default) ?: default
    private fun point(value: Float) = value / 12700f
    private fun color(element: Element?, palette: Map<String, String>, fallback: String = ""): String {
        if (element == null || element.first("a:noFill") != null) return fallback
        val rgb = element.first("a:srgbClr")?.attr("val") ?: element.first("a:sysClr")?.attr("lastClr")
        if (rgb != null && rgb.matches(Regex("[0-9a-fA-F]{6}"))) return "#$rgb"
        return palette[element.first("a:schemeClr")?.attr("val")] ?: fallback
    }
    private fun bitmap(path: String): String {
        val type = when (path.substringAfterLast('.').lowercase()) { "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "gif" -> "image/gif"; else -> return "" }
        val entry = zip.getEntry(path) ?: return ""
        require(entry.size in 0..12L * 1024 * 1024) { "Slide image is too large." }
        return "data:$type;base64," + zip.getInputStream(entry).use { Base64.encodeToString(it.readBytes(), Base64.NO_WRAP) }
    }
    suspend fun read(title: String): LocalDocument {
        val presentation = xml("ppt/presentation.xml"); val relations = related("ppt/presentation.xml")
        val size = presentation.first("p:sldSz")
        val width = point(size?.number("cx", 9144000f) ?: 9144000f).coerceIn(72f, 4000f)
        val height = point(size?.number("cy", 5143500f) ?: 5143500f).coerceIn(72f, 4000f)
        val slideParts = presentation.getElementsByTag("p:sldId").mapNotNull { relations[it.attr("r:id")] }
        require(slideParts.size in 1..500) { "Open a presentation with 1–500 slides." }
        val sections = slideParts.mapIndexed { index, path ->
            currentCoroutineContext().ensureActive()
            val slide = xml(path); val resources = related(path)
            val layoutPath = resources.values.firstOrNull { it.contains("/slideLayouts/") }
            val layout = layoutPath?.let(::xml)
            val masterPath = layoutPath?.let(::related)?.values?.firstOrNull { it.contains("/slideMasters/") }
            val master = masterPath?.let(::xml)
            val themePath = masterPath?.let(::related)?.values?.firstOrNull { it.contains("/theme/") }
            val scheme = themePath?.let(::xml)?.first("a:clrScheme")
            val palette = mutableMapOf("tx1" to "#202124", "bg1" to "#FFFFFF", "accent1" to "#385D8A")
            scheme?.children()?.forEach { palette[it.tagName().substringAfter(':')] = color(it, emptyMap(), "#202124") }
            palette["tx1"] = palette["dk1"] ?: "#202124"; palette["bg1"] = palette["lt1"] ?: "#FFFFFF"
            palette["tx2"] = palette["dk2"] ?: "#202124"; palette["bg2"] = palette["lt2"] ?: "#FFFFFF"
            val background = color(slide.first("p:bg") ?: layout?.first("p:bg") ?: master?.first("p:bg"), palette, "#FFFFFF")
            val shapes = mutableListOf<SlideShape>()
            fun inherited(shape: Element): Element? {
                val ph = shape.first("p:ph") ?: return null
                return listOfNotNull(layout, master).flatMap { it.getElementsByTag("p:sp") }.firstOrNull { candidate ->
                    val other = candidate.first("p:ph")
                    other != null && if (ph.hasAttr("idx")) ph.attr("idx") == other.attr("idx") else ph.attr("type") == other.attr("type")
                }
            }
            fun add(tree: Element?, sourceResources: Map<String, String>, offsetX: Float = 0f, offsetY: Float = 0f, scaleX: Float = 1f, scaleY: Float = 1f, inheritedLayer: Boolean = false) {
                tree?.children()?.forEach { shape ->
                    if (inheritedLayer && shape.first("p:ph") != null) return@forEach
                    if (shape.tagName() == "p:grpSp") {
                        val transform = shape.children().firstOrNull { it.tagName() == "p:grpSpPr" }?.first("a:xfrm")
                        val off = transform?.first("a:off"); val ext = transform?.first("a:ext"); val childOff = transform?.first("a:chOff"); val childExt = transform?.first("a:chExt")
                        val sx = (ext?.number("cx", 1f) ?: 1f) / (childExt?.number("cx", 1f) ?: 1f).coerceAtLeast(1f)
                        val sy = (ext?.number("cy", 1f) ?: 1f) / (childExt?.number("cy", 1f) ?: 1f).coerceAtLeast(1f)
                        add(shape, sourceResources, offsetX + point((off?.number("x") ?: 0f) - (childOff?.number("x") ?: 0f) * sx) * scaleX, offsetY + point((off?.number("y") ?: 0f) - (childOff?.number("y") ?: 0f) * sy) * scaleY, scaleX * sx, scaleY * sy, inheritedLayer)
                    } else if (shape.tagName() in listOf("p:sp", "p:pic", "p:cxnSp", "p:graphicFrame")) {
                        val fallback = inherited(shape)
                        val transform = shape.first("a:xfrm") ?: shape.first("p:xfrm") ?: fallback?.first("a:xfrm") ?: return@forEach
                        val off = transform.first("a:off"); val ext = transform.first("a:ext")
                        val x = offsetX + point(off?.number("x") ?: 0f) * scaleX; val y = offsetY + point(off?.number("y") ?: 0f) * scaleY
                        val w = point(ext?.number("cx", 12700f) ?: 12700f) * scaleX; val h = point(ext?.number("cy", 12700f) ?: 12700f) * scaleY
                        if (w <= 0 || h <= 0) return@forEach
                        val properties = shape.children().firstOrNull { it.tagName() == "p:spPr" }
                        val fill = color(properties?.children()?.firstOrNull { it.tagName() == "a:solidFill" }, palette)
                        val stroke = color(properties?.first("a:ln"), palette)
                        val preset = properties?.first("a:prstGeom")?.attr("prst").orEmpty().ifBlank { if (shape.tagName() == "p:cxnSp") "line" else "rect" }
                        val paragraphs = shape.getElementsByTag("a:p")
                        val html = buildString { paragraphs.forEach { p ->
                            if (isNotEmpty()) append("<br>")
                            if (p.first("a:buChar") != null) append("• ")
                            p.children().filter { it.tagName() in listOf("a:r", "a:fld", "a:br") }.forEach { run ->
                                if (run.tagName() == "a:br") append("<br>") else {
                                    val style = run.first("a:rPr"); val bold = style?.attr("b") == "1"; val italic = style?.attr("i") == "1"
                                    val runColor = color(style, palette); if (runColor.isNotBlank()) append("<font color=\"$runColor\">")
                                    if (bold) append("<b>"); if (italic) append("<i>"); append(htmlEscape(run.first("a:t")?.wholeText().orEmpty())); if (italic) append("</i>"); if (bold) append("</b>")
                                    if (runColor.isNotBlank()) append("</font>")
                                }
                            }
                        } }
                        val font = (shape.first("a:rPr")?.number("sz", 0f)?.takeIf { it > 0 } ?: fallback?.first("a:defRPr")?.number("sz", 2000f) ?: 2000f) / 100f * scaleY
                        val image = shape.first("a:blip")?.attr("r:embed")?.let { sourceResources[it] }?.let(::bitmap).orEmpty()
                        shapes += SlideShape(x, y, w, h, preset, html, image, fill, stroke, font.coerceIn(4f, 200f), color(shape.first("a:rPr"), palette, palette.getValue("tx1")), when (shape.first("a:pPr")?.attr("algn")) { "ctr" -> "center"; "r" -> "right"; else -> "left" }, transform.number("rot") / 60000f)
                    }
                }
            }
            if (master != null && masterPath != null) add(master.first("p:spTree"), related(masterPath), inheritedLayer = true)
            if (layout != null && layoutPath != null) add(layout.first("p:spTree"), related(layoutPath), inheritedLayer = true)
            add(slide.first("p:spTree"), resources)
            val scene = SlideLayout(width, height, background, shapes)
            DocumentSection("Slide ${index + 1}", scene.html(), scene)
        }
        return LocalDocument(title, "Presentation", sections, note = "Slide layout · text, images and basic shapes. Charts, SmartArt, effects and animations may differ.")
    }
}
