package com.markdrop.app.conversion.ocr

import android.graphics.Rect

data class OcrElement(
    val text: String,
    val boundingBox: Rect?,
    val confidence: Float?
)

data class OcrLine(
    val text: String,
    val boundingBox: Rect?,
    val lineHeight: Int,
    val elements: List<OcrElement>
)

data class OcrBlock(
    val text: String,
    val boundingBox: Rect?,
    val lines: List<OcrLine>
)

data class OcrPageResult(
    val pageIndex: Int,
    val width: Int,
    val height: Int,
    val blocks: List<OcrBlock>
)
