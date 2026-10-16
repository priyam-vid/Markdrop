package com.markdrop.app.conversion

/**
 * Root of typed conversion exceptions providing user-actionable error messages.
 */
sealed class ConversionException(message: String, cause: Throwable? = null) : Exception(message, cause)

class PdfEncryptedException(
    message: String = "The PDF is password protected and cannot be processed without a password."
) : ConversionException(message)

class PdfCorruptException(
    message: String = "The PDF file is corrupted or formatted improperly and cannot be opened.",
    cause: Throwable? = null
) : ConversionException(message, cause)

class PageRenderException(
    val pageIndex: Int,
    message: String = "Failed to render PDF page ${pageIndex + 1}.",
    cause: Throwable? = null
) : ConversionException(message, cause)

class ModelNotReadyException(
    val modelName: String,
    message: String = "Local AI model '$modelName' is not downloaded or ready. Please download it from Settings or select Cloud AI."
) : ConversionException(message)

class ApiKeyMissingException(
    val provider: String,
    message: String = "API key for $provider is missing. Please add your API key in Settings > Cloud AI."
) : ConversionException(message)

class QuotaExceededException(
    val provider: String,
    message: String = "Rate limit or quota exceeded for $provider. Please check your account usage or try again later.",
    cause: Throwable? = null
) : ConversionException(message, cause)

class ConversionCancelledException(
    message: String = "Conversion was cancelled by the user."
) : ConversionException(message)
