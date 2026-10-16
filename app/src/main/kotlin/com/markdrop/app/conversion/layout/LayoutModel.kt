package com.markdrop.app.conversion.layout

/**
 * Pure Kotlin data model for positioned words, lines, blocks, and layout pages.
 * Free of Android framework dependencies to ensure full JVM unit testability.
 */
data class LayoutWord(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val fontSize: Float,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val confidence: Float? = null
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
}

data class FigureBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val description: String? = null
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
}

data class PageGeometry(
    val width: Float,
    val height: Float,
    val rotation: Int = 0
)

data class LayoutLine(
    val words: List<LayoutWord>,
    val y: Float,
    val height: Float,
    val fontSize: Float = height,
    val left: Float = words.minOfOrNull { it.left } ?: 0f,
    val right: Float = words.maxOfOrNull { it.right } ?: 0f,
    val text: String = words.joinToString(" ") { it.text },
    val isBold: Boolean = words.isNotEmpty() && words.all { it.bold },
    val isItalic: Boolean = words.isNotEmpty() && words.all { it.italic }
) {
    constructor(
        text: String,
        y: Float,
        height: Float,
        fontSize: Float = height,
        left: Float = 0f,
        right: Float = 0f,
        isBold: Boolean = false,
        isItalic: Boolean = false
    ) : this(
        words = if (text.isBlank()) emptyList() else text.split(" ").filter { it.isNotEmpty() }.map { LayoutWord(it, left, y, right, y + height, fontSize, isBold, isItalic) },
        y = y,
        height = height,
        fontSize = fontSize,
        left = left,
        right = right,
        text = text,
        isBold = isBold,
        isItalic = isItalic
    )
}

data class LayoutBlock(
    val lines: List<LayoutLine>,
    val left: Float = lines.minOfOrNull { it.left } ?: 0f,
    val top: Float = lines.minOfOrNull { it.y } ?: 0f,
    val right: Float = lines.maxOfOrNull { it.right } ?: 0f,
    val bottom: Float = lines.maxOfOrNull { it.y + it.height } ?: 0f
)

data class LayoutPage(
    val pageIndex: Int,
    val width: Float,
    val height: Float,
    val words: List<LayoutWord> = emptyList(),
    val lines: List<LayoutLine> = emptyList(),
    val figures: List<FigureBox> = emptyList(),
    val averageConfidence: Float = 1.0f
)
