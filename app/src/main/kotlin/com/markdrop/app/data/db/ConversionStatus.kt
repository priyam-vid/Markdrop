package com.markdrop.app.data.db

enum class ConversionStatus {
    PENDING,
    WAITING,
    PROCESSING,
    DONE,
    PARTIAL,
    FAILED,
    PAUSED,
    CANCELLED
}
