package com.markdrop.app.conversion.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.Closeable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MlKitOcrEngine : Closeable {

    private val recognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun processImage(
        bitmap: Bitmap,
        pageIndex: Int
    ): OcrPageResult = withContext(Dispatchers.Default) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val visionText = recognizer.process(image).awaitTask()

        val blocks = visionText.textBlocks.map { mlBlock ->
            val lines = mlBlock.lines.map { mlLine ->
                val lineBox = mlLine.boundingBox
                val lineHeight = lineBox?.height() ?: 0
                val elements = mlLine.elements.map { mlElement ->
                    OcrElement(
                        text = mlElement.text,
                        boundingBox = mlElement.boundingBox,
                        confidence = mlElement.confidence
                    )
                }
                OcrLine(
                    text = mlLine.text,
                    boundingBox = lineBox,
                    lineHeight = lineHeight,
                    elements = elements
                )
            }
            OcrBlock(
                text = mlBlock.text,
                boundingBox = mlBlock.boundingBox,
                lines = lines
            )
        }

        OcrPageResult(
            pageIndex = pageIndex,
            width = bitmap.width,
            height = bitmap.height,
            blocks = blocks
        )
    }

    override fun close() {
        recognizer.close()
    }
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result -> cont.resume(result) }
        addOnFailureListener { exception -> cont.resumeWithException(exception) }
        addOnCanceledListener { cont.cancel() }
    }
