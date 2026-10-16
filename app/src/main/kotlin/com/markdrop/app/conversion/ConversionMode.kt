package com.markdrop.app.conversion

enum class ConversionMode(
    val title: String,
    val description: String
) {
    OFFLINE(
        title = "Offline",
        description = "Free & 100% private. Uses embedded text layer when available, else on-device OCR. Never touches the network."
    ),
    AI(
        title = "AI",
        description = "Frontier quality. Uses embedded text layer when available, else frontier cloud vision (Gemini, Claude, OpenAI) or local AI."
    ),

    // Legacy aliases maintained for Room database backward compatibility
    FAST(
        title = "Offline",
        description = "Free & 100% private. Uses embedded text layer when available, else on-device OCR."
    ),
    SMART(
        title = "AI",
        description = "Frontier quality with intelligent page routing."
    ),
    PRECISION(
        title = "AI (Local)",
        description = "On-device local vision model."
    ),
    ADVANCED(
        title = "AI (Cloud)",
        description = "Frontier cloud vision model."
    );

    val isOffline: Boolean
        get() = this == OFFLINE || this == FAST

    val isAi: Boolean
        get() = this == AI || this == ADVANCED || this == PRECISION || this == SMART
}
