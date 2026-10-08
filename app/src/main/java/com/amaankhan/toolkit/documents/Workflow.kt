package com.amaankhan.toolkit.documents

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class WorkflowType { PDF, EXCEL, TEXT, IMAGES, WORD, PRESENTATION, HTML }
enum class WorkflowOperation(val label: String, val input: WorkflowType, val output: WorkflowType, val terminal: Boolean = false) {
    IMAGES_PDF("Images to PDF", WorkflowType.IMAGES, WorkflowType.PDF),
    WORD_PDF("Word to PDF", WorkflowType.WORD, WorkflowType.PDF),
    PRESENTATION_PDF("Presentation to PDF", WorkflowType.PRESENTATION, WorkflowType.PDF),
    TEXT_PDF("Text / Markdown to PDF", WorkflowType.TEXT, WorkflowType.PDF),
    HTML_PDF("HTML to PDF", WorkflowType.HTML, WorkflowType.PDF),
    EXCEL_UNLOCK("Unlock Excel file", WorkflowType.EXCEL, WorkflowType.EXCEL),
    EXCEL_EDIT_UNLOCK("Remove Excel editing protection", WorkflowType.EXCEL, WorkflowType.EXCEL),
    EXCEL_PDF("Excel to PDF · table layout", WorkflowType.EXCEL, WorkflowType.PDF),
    EXCEL_LOCK("Lock Excel file", WorkflowType.EXCEL, WorkflowType.EXCEL, true),
    PDF_MERGE("Merge PDFs", WorkflowType.PDF, WorkflowType.PDF),
    PDF_UNLOCK("Unlock PDF", WorkflowType.PDF, WorkflowType.PDF),
    PDF_EXTRACT("Extract PDF pages", WorkflowType.PDF, WorkflowType.PDF),
    PDF_ROTATE("Rotate PDF pages", WorkflowType.PDF, WorkflowType.PDF),
    PDF_CROP("Crop PDF", WorkflowType.PDF, WorkflowType.PDF),
    PDF_METADATA("Remove PDF metadata", WorkflowType.PDF, WorkflowType.PDF),
    PDF_COMPRESS("Compress PDF", WorkflowType.PDF, WorkflowType.PDF),
    PDF_STAMP("Watermark / page numbers", WorkflowType.PDF, WorkflowType.PDF),
    PDF_WORD("PDF to Word", WorkflowType.PDF, WorkflowType.WORD),
    PDF_EXCEL("PDF to Excel", WorkflowType.PDF, WorkflowType.EXCEL),
    PDF_PRESENTATION("PDF to presentation", WorkflowType.PDF, WorkflowType.PRESENTATION),
    PDF_TEXT("PDF to text", WorkflowType.PDF, WorkflowType.TEXT),
    PDF_IMAGES("PDF to images", WorkflowType.PDF, WorkflowType.IMAGES, true),
    PDF_LOCK("Lock PDF", WorkflowType.PDF, WorkflowType.PDF, true);
    val needsNewPassword get() = this in setOf(EXCEL_LOCK, PDF_LOCK)
    val needsPassword get() = this in setOf(EXCEL_UNLOCK, EXCEL_EDIT_UNLOCK, EXCEL_PDF, EXCEL_LOCK, PDF_UNLOCK, PDF_LOCK)
}
data class WorkflowStep(val operation: WorkflowOperation, val id: String = UUID.randomUUID().toString(),
    val pages: String = "", val rotation: Int = 90, val cropPx: Int = 24, val sheets: String = "",
    val marginPx: String = "24", val paper: String = "A4", val orientation: String = "AUTO",
    val imageFormat: String = "PNG", val imageEdge: Int = 1800,
    val stampText: String = "{page} / {total}", val stampX: Int = 50, val stampY: Int = 95, val stampOpacity: Int = 100)
data class StepPasswords(val current: String = "", val new: String = "")
data class WorkflowTemplate(val name: String, val steps: List<WorkflowStep>, val id: String = UUID.randomUUID().toString()) {
    fun validate() {
        require(name.isNotBlank() && name.length <= 80) { "Name your workflow (up to 80 characters)." }
        require(steps.size in 1..20) { "Add 1–20 steps." }
        require(steps.map { it.id }.distinct().size == steps.size) { "Workflow steps must have distinct identifiers." }
        steps.forEachIndexed { index, step ->
            require(step.rotation in listOf(0, 90, 180, 270) && step.cropPx in 0..160) { "Check the options for step ${index + 1}." }
            require((step.marginPx.toIntOrNull() ?: -1) in 0..160 && step.paper in Paper.entries.map { it.name } && step.orientation in Orientation.entries.map { it.name }) { "Check page layout and margins for step ${index + 1}." }
            require(step.imageFormat in listOf("PNG", "JPEG") && step.imageEdge in listOf(1200, 1800, 2400)) { "Check image output options for step ${index + 1}." }
            require(step.stampText.length <= 160 && step.stampX in 0..100 && step.stampY in 0..100 && step.stampOpacity in 5..100) { "Check the watermark options." }
            require(step.operation != WorkflowOperation.PDF_MERGE || index == 0) { "Merge must be the first step so all selected inputs are included." }
            if (index > 0) {
                val before = steps[index - 1].operation
                require(!before.terminal && before.output == step.operation.input) { "Step ${index + 1} cannot use the preceding output. Reorder or remove this step." }
            }
        }
    }
    fun toJson(): JSONObject {
        validate()
        // Explicit allowlist: never serialize credentials, source paths or arbitrary options.
        return JSONObject().put("id", id).put("name", name).put("steps", JSONArray().apply { steps.forEach { step ->
            put(JSONObject().put("id", step.id).put("operation", step.operation.name).put("pages", step.pages)
                .put("rotation", step.rotation).put("cropPx", step.cropPx).put("sheets", step.sheets)
                .put("marginPx", step.marginPx).put("paper", step.paper).put("orientation", step.orientation)
                .put("imageFormat", step.imageFormat).put("imageEdge", step.imageEdge)
                .put("stampText", step.stampText).put("stampX", step.stampX).put("stampY", step.stampY).put("stampOpacity", step.stampOpacity))
        } })
    }
    companion object {
        fun fromJson(value: JSONObject): WorkflowTemplate {
            val list = value.getJSONArray("steps")
            val steps = (0 until list.length()).map { i -> list.getJSONObject(i).run {
                WorkflowStep(WorkflowOperation.valueOf(getString("operation")), getString("id"), optString("pages"), optInt("rotation", 90), optInt("cropPx", 24), optString("sheets"),
                    optString("marginPx", "24"), optString("paper", "A4"), optString("orientation", "AUTO"), optString("imageFormat", "PNG"), optInt("imageEdge", 1800),
                    optString("stampText", "{page} / {total}"), optInt("stampX", 50), optInt("stampY", 95), optInt("stampOpacity", 100))
            } }
            return WorkflowTemplate(value.getString("name"), steps, value.getString("id")).also { it.validate() }
        }
    }
}

class WorkflowEngine(private val context: Context, private val directory: File) {
    suspend fun executeBatch(template: WorkflowTemplate, inputs: List<String>, passwords: Map<String, StepPasswords>, progress: (String) -> Unit = {}): PdfResult = withContext(Dispatchers.IO) {
        require(inputs.size in 1..20)
        directory.mkdirs()
        val archive = File(directory, "workflow-batch-${UUID.randomUUID()}.zip")
        var preview: File? = null
        var count = 0
        try {
            java.util.zip.ZipOutputStream(archive.outputStream().buffered()).use { zip ->
                inputs.forEachIndexed { index, input ->
                    currentCoroutineContext().ensureActive()
                    val result = execute(template, listOf(input), passwords) { progress("File ${index + 1}/${inputs.size}\n$it") }
                    try {
                        if (preview == null) preview = File(directory, "batch-preview-${UUID.randomUUID()}.${result.preview.extension}").also { result.preview.copyTo(it) }
                        val extension = result.file.extension
                        val base = LocalDocumentReader(context).name(input).substringBeforeLast('.').replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").take(100).ifBlank { "File" }
                        zip.putNextEntry(java.util.zip.ZipEntry("${index + 1}-$base.$extension")); result.file.inputStream().use { it.copyTo(zip) }; zip.closeEntry(); count += result.pages
                    } finally { result.delete() }
                }
            }
            PdfResult(archive, requireNotNull(preview), count, "application/zip", summary = "${inputs.size} separate outputs in a ZIP. Preview shows the first output.")
        } catch (failure: Throwable) { archive.delete(); preview?.delete(); throw failure }
    }
    suspend fun execute(template: WorkflowTemplate, inputs: List<String>, passwords: Map<String, StepPasswords>, progress: (String) -> Unit = {}): PdfResult {
        template.validate()
        require(inputs.isNotEmpty()) { "Choose the input files." }
        require(template.steps.first().operation in setOf(WorkflowOperation.PDF_MERGE, WorkflowOperation.IMAGES_PDF) || inputs.size == 1) { "Choose one input for this workflow." }
        var prior: PdfResult? = null
        try {
            template.steps.forEachIndexed { index, step ->
                currentCoroutineContext().ensureActive()
                val source = prior?.let { listOf(Uri.fromFile(it.file).toString()) } ?: inputs
                val secret = passwords[step.id] ?: StepPasswords()
                val report: (String) -> Unit = { progress("Step ${index + 1}/${template.steps.size} · ${step.operation.label}\n$it") }
                val next = if (step.operation == WorkflowOperation.IMAGES_PDF) {
                    val result = ImagePdfEngine(context.contentResolver, directory).create(source.mapIndexed { i, uri -> SelectedImage(uri, "Image ${i + 1}") },
                        PdfOptions(Paper.valueOf(step.paper), Orientation.valueOf(step.orientation), marginsPx = step.marginPx.toInt().let { MarginsPx(it, it, it, it) })) { done, total -> report("Preparing image $done of $total") }
                    PdfResult(result, result, source.size)
                } else if (step.operation in setOf(WorkflowOperation.WORD_PDF, WorkflowOperation.PRESENTATION_PDF, WorkflowOperation.TEXT_PDF, WorkflowOperation.HTML_PDF)) {
                    RichPdfEngine(context, directory).execute(RichPdfRequest(source.single(), step.pages, step.sheets, step.marginPx.toInt(), password = secret.current), report)
                } else if (step.operation in setOf(WorkflowOperation.PDF_WORD, WorkflowOperation.PDF_EXCEL, WorkflowOperation.PDF_PRESENTATION)) {
                    PdfOfficeEngine(context, directory).execute(PdfOfficeRequest(source.single(), when (step.operation) { WorkflowOperation.PDF_WORD -> "docx"; WorkflowOperation.PDF_EXCEL -> "xlsx"; else -> "pptx" }, step.pages, secret.current), report)
                } else if (step.operation in setOf(WorkflowOperation.PDF_COMPRESS, WorkflowOperation.PDF_STAMP)) {
                    ExtraPdfEngine(context, directory).execute(ExtraPdfRequest(if (step.operation == WorkflowOperation.PDF_COMPRESS) "COMPRESS" else "STAMP", source.single(), secret.current,
                        JSONObject().put("pages", step.pages).put("edge", step.imageEdge).put("text", step.stampText).put("x", step.stampX).put("y", step.stampY).put("opacity", step.stampOpacity / 100.0)), report)
                } else if (step.operation.input == WorkflowType.EXCEL) {
                    val action = when (step.operation) {
                        WorkflowOperation.EXCEL_UNLOCK -> OfficeAction.UNLOCK_FILE
                        WorkflowOperation.EXCEL_EDIT_UNLOCK -> OfficeAction.UNLOCK_EDITING
                        WorkflowOperation.EXCEL_LOCK -> OfficeAction.LOCK_FILE
                        else -> OfficeAction.TO_PDF
                    }
                    OfficeEngine(context, directory).execute(OfficeRequest(source.single(), action, secret.current, secret.new, step.sheets), report)
                } else {
                    val operation = when (step.operation) {
                        WorkflowOperation.PDF_MERGE -> PdfOperation.MERGE
                        WorkflowOperation.PDF_UNLOCK -> PdfOperation.UNLOCK
                        WorkflowOperation.PDF_LOCK -> PdfOperation.PROTECT
                        WorkflowOperation.PDF_EXTRACT -> PdfOperation.EXTRACT
                        WorkflowOperation.PDF_ROTATE -> PdfOperation.ORGANIZE
                        WorkflowOperation.PDF_CROP -> PdfOperation.CROP
                        WorkflowOperation.PDF_TEXT -> PdfOperation.TEXT
                        WorkflowOperation.PDF_IMAGES -> PdfOperation.IMAGES
                        else -> PdfOperation.REMOVE_METADATA
                    }
                    PdfToolEngine(context, directory).execute(PdfRequest(operation, source, step.pages, step.pages.isBlank(), secret.current, secret.new,
                        rotation = if (step.operation == WorkflowOperation.PDF_ROTATE) step.rotation else 0, cropPx = step.cropPx, imageFormat = step.imageFormat, imageEdge = step.imageEdge), report)
                }
                prior?.delete(); prior = next
            }
            currentCoroutineContext().ensureActive()
            return requireNotNull(prior)
        } catch (failure: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) { prior?.delete() }; throw failure
        }
    }
}
