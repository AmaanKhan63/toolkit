package com.amaankhan.toolkit.ui

data class Tool(val id: String, val name: String, val glyph: Glyph = Glyph.FILE, val available: Boolean = false)
val documentTools = listOf(
    Tool("D01", "Images to PDF", Glyph.IMAGE, true), Tool("D02", "Merge PDF", Glyph.LAYERS),
    Tool("D03", "Lock / unlock PDF", Glyph.LOCK), Tool("D04", "Excel protection", Glyph.LOCK),
    Tool("D05", "Compress PDF", Glyph.COMPRESS), Tool("D06", "Word to PDF"), Tool("D07", "Excel to PDF"),
    Tool("D08", "Presentation to PDF"), Tool("D09", "PDF to Word"), Tool("D10", "PDF to Excel"),
    Tool("D11", "PDF to presentation"), Tool("D12", "PDF to images"), Tool("D13", "HTML to PDF"),
    Tool("P01", "Split / extract pages"), Tool("P02", "Organize pages"), Tool("P03", "Crop pages"),
    Tool("P04", "Watermark / page numbers"), Tool("P05", "Scan to PDF", Glyph.IMAGE, true),
    Tool("P09", "Draw / place signature"), Tool("P14", "PDF to text / Markdown"),
    Tool("P15", "Text / Markdown to PDF"), Tool("P16", "Document metadata"), Tool("P17", "Workflows", Glyph.FLOW),
)
