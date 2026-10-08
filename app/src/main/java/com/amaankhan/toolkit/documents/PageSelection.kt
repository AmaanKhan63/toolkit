package com.amaankhan.toolkit.documents

/** Page numbers are one-based in the UI and zero-based in engine requests. */
object PageSelection {
    fun parse(value: String, pageCount: Int, allowDuplicates: Boolean = false): List<Int> {
        require(pageCount > 0) { "The document has no pages." }
        require(value.isNotBlank()) { "Enter pages, for example 1, 3-5." }
        val result = mutableListOf<Int>()
        value.replace('–', '-').replace('—', '-').split(',').forEach { part ->
            val match = Regex("^\\s*(\\d+)\\s*(?:-\\s*(\\d+)\\s*)?$").matchEntire(part)
                ?: throw IllegalArgumentException("Use page numbers or ranges, such as 1, 3-5.")
            val first = match.groupValues[1].toIntOrNull() ?: throw IllegalArgumentException("Page number is too large.")
            val last = if (match.groupValues[2].isBlank()) first else match.groupValues[2].toIntOrNull() ?: throw IllegalArgumentException("Page number is too large.")
            require(first in 1..pageCount && last in first..pageCount) { "Choose ascending ranges between 1 and $pageCount." }
            require(result.size + last - first + 1 <= 2000) { "Select at most 2,000 output pages." }
            result += (first..last).map { it - 1 }
        }
        return if (allowDuplicates) result else result.distinct().sorted()
    }
}
