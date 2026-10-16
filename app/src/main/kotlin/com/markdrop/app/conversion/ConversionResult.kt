package com.markdrop.app.conversion

data class ConversionResult(
    val markdown: String,
    val pageCount: Int,
    val durationMs: Long,
    val mode: ConversionMode,
    val warningMessage: String? = null
)
