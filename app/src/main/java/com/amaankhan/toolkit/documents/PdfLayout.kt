package com.amaankhan.toolkit.documents

import kotlin.math.min
import kotlin.math.sqrt

enum class Paper(val label: String, val width: Int, val height: Int) {
    A4("A4", 595, 842), LETTER("Letter", 612, 792)
}
enum class Orientation(val label: String) { AUTO("Auto"), PORTRAIT("Portrait"), LANDSCAPE("Landscape") }
data class MarginsPx(val top: Int = 32, val right: Int = 32, val bottom: Int = 32, val left: Int = 32) {
    fun valid() = listOf(top, right, bottom, left).all { it in 0..160 }
}
data class PdfOptions(val paper: Paper = Paper.A4, val orientation: Orientation = Orientation.AUTO, val margin: Int = 24, val marginsPx: MarginsPx? = null)
data class PageLayout(val width: Int, val height: Int, val left: Float, val top: Float, val imageWidth: Float, val imageHeight: Float)

object PdfLayout {
    const val MAX_IMAGES = 20
    const val TOTAL_PIXEL_BUDGET = 12_000_000
    fun decodeEdge(count: Int): Int {
        require(count in 1..MAX_IMAGES)
        return min(1800, sqrt(TOTAL_PIXEL_BUDGET.toDouble() / count).toInt())
    }
    fun sampleSize(width: Int, height: Int, edge: Int): Int {
        require(width > 0 && height > 0 && edge > 0)
        var sample = 1
        while (width.toLong() / sample > edge || height.toLong() / sample > edge) sample *= 2
        return sample
    }
    fun fit(imageWidth: Int, imageHeight: Int, options: PdfOptions): PageLayout {
        require(imageWidth > 0 && imageHeight > 0)
        require(options.margin in 0..72)
        require(options.marginsPx?.valid() != false)
        val landscape = options.orientation == Orientation.LANDSCAPE ||
            (options.orientation == Orientation.AUTO && imageWidth > imageHeight)
        val width = if (landscape) options.paper.height else options.paper.width
        val height = if (landscape) options.paper.width else options.paper.height
        val margins = options.marginsPx
        val left = margins?.left?.times(0.75f) ?: options.margin.toFloat()
        val right = margins?.right?.times(0.75f) ?: options.margin.toFloat()
        val top = margins?.top?.times(0.75f) ?: options.margin.toFloat()
        val bottom = margins?.bottom?.times(0.75f) ?: options.margin.toFloat()
        require(left + right < width && top + bottom < height)
        val availableWidth = width - left - right
        val availableHeight = height - top - bottom
        val scale = min(availableWidth / imageWidth, availableHeight / imageHeight)
        val fittedWidth = imageWidth * scale
        val fittedHeight = imageHeight * scale
        return PageLayout(width, height, left + (availableWidth - fittedWidth) / 2, top + (availableHeight - fittedHeight) / 2, fittedWidth, fittedHeight)
    }
}
