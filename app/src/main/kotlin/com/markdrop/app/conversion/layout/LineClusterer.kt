package com.markdrop.app.conversion.layout

import kotlin.math.abs

/**
 * Clusters LayoutWords into coherent horizontal lines based on font-relative baseline overlap.
 * Free of Android framework dependencies.
 */
object LineClusterer {

    fun cluster(words: List<LayoutWord>): List<LayoutLine> {
        if (words.isEmpty()) return emptyList()

        class LineBucket(var top: Float, var bottom: Float, var fontSize: Float) {
            val wordsInLine = mutableListOf<LayoutWord>()
        }

        val buckets = mutableListOf<LineBucket>()
        // Sort words by top y, then left x
        val sortedWords = words.sortedWith(compareBy<LayoutWord> { it.top }.thenBy { it.left })

        for (w in sortedWords) {
            val verticalTolerance = (w.fontSize * 0.45f).coerceAtLeast(3f)

            // Find bucket where vertical overlap is significant
            val matchedBucket = buckets.find { bucket ->
                val overlapTop = maxOf(bucket.top, w.top)
                val overlapBottom = minOf(bucket.bottom, w.bottom)
                val overlap = overlapBottom - overlapTop
                val minHeight = minOf(bucket.bottom - bucket.top, w.height)

                overlap >= (minHeight * 0.4f) || abs(bucket.top - w.top) <= verticalTolerance
            }

            if (matchedBucket != null) {
                matchedBucket.wordsInLine.add(w)
                matchedBucket.top = minOf(matchedBucket.top, w.top)
                matchedBucket.bottom = maxOf(matchedBucket.bottom, w.bottom)
                matchedBucket.fontSize = maxOf(matchedBucket.fontSize, w.fontSize)
            } else {
                val newBucket = LineBucket(w.top, w.bottom, w.fontSize)
                newBucket.wordsInLine.add(w)
                buckets.add(newBucket)
            }
        }

        // Sort buckets from top to bottom
        buckets.sortBy { it.top }

        return buckets.map { bucket ->
            val orderedWords = bucket.wordsInLine.sortedBy { it.left }
            val height = (bucket.bottom - bucket.top).coerceAtLeast(bucket.fontSize)
            LayoutLine(
                words = orderedWords,
                y = bucket.top,
                height = height,
                fontSize = bucket.fontSize,
                left = orderedWords.first().left,
                right = orderedWords.last().right
            )
        }
    }
}
