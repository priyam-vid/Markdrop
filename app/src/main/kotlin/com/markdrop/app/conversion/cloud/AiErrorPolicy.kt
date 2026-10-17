package com.markdrop.app.conversion.cloud

enum class PageErrorAction {
    MARK_FAILED,
    FALLBACK_TO_OCR
}

data class PageErrorDecision(
    val action: PageErrorAction,
    val pageStatus: String,
    val markerComment: String? = null
)

class AiErrorPolicy(
    val fallbackToOcrOnError: Boolean = false,
    val maxConsecutiveErrors: Int = 3
) {
    private var consecutiveErrorClass: String? = null
    private var consecutiveErrorCount: Int = 0

    fun isFailFast(error: Throwable): Boolean {
        return error is VisionException.Auth ||
                error is VisionException.BadRequest ||
                error is VisionException.ModelNotFound
    }

    fun recordErrorAndCheckTripped(error: Throwable): Boolean {
        val errClass = error.javaClass.simpleName
        if (errClass == consecutiveErrorClass) {
            consecutiveErrorCount++
        } else {
            consecutiveErrorClass = errClass
            consecutiveErrorCount = 1
        }
        return consecutiveErrorCount >= maxConsecutiveErrors
    }

    fun recordSuccess() {
        consecutiveErrorClass = null
        consecutiveErrorCount = 0
    }

    fun handlePageError(pageIndex: Int, error: Throwable): PageErrorDecision {
        return if (fallbackToOcrOnError) {
            val errClass = error.javaClass.simpleName
            PageErrorDecision(
                action = PageErrorAction.FALLBACK_TO_OCR,
                pageStatus = "FALLBACK",
                markerComment = "<!-- page ${pageIndex + 1}: AI failed, OCR fallback ($errClass) -->"
            )
        } else {
            PageErrorDecision(
                action = PageErrorAction.MARK_FAILED,
                pageStatus = "FAILED",
                markerComment = "<!-- Error transcribing page ${pageIndex + 1}: ${error.message} -->"
            )
        }
    }
}
