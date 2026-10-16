package com.markdrop.app.conversion.layout

/**
 * Pure Kotlin data model for positioned layout elements and lines.
 * Free of Android framework dependencies to ensure full JVM unit testability.
 */
data class LayoutElement(
    val text: String,
    val left: Float,
    val right: Float,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val confidence: Float? = null
)

data class LayoutLine(
    val text: String,
    val y: Float,
    val height: Float,
    val fontSize: Float = height,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val left: Float = 0f,
    val right: Float = 0f,
    val elements: List<LayoutElement> = emptyList()
)

data class LayoutPage(
    val pageIndex: Int,
    val width: Float,
    val height: Float,
    val lines: List<LayoutLine>,
    val averageConfidence: Float = 1.0f
)
