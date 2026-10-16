package com.markdrop.app.conversion

enum class ConversionMode(
    val title: String,
    val description: String
) {
    FAST(
        title = "Fast",
        description = "Free & offline. Uses on-device OCR for quick text extraction. Complex tables and diagrams will be approximate."
    ),
    SMART(
        title = "Smart",
        description = "Hybrid intelligent routing. Analyzes layout complexity per page: routes plain text to fast OCR and complex charts/tables/math to AI."
    ),
    PRECISION(
        title = "Precision",
        description = "Free (local AI). Runs an on-device vision model for higher accuracy formatting. Requires model download and 6GB+ RAM."
    ),
    ADVANCED(
        title = "Advanced",
        description = "Bring-your-own-key cloud vision. Sends pages to frontier AI models (OpenAI, Gemini, Claude) via your private API key."
    )
}
