package com.markdrop.app.conversion.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Hardened image loader handling:
 * - App-private caching of content URIs
 * - Out-of-memory prevention via proportional downsampling
 * - EXIF orientation correction
 */
object ImageLoader {

    suspend fun loadImage(
        context: Context,
        uri: Uri,
        maxDimension: Int = 2048
    ): Bitmap = withContext(Dispatchers.IO) {
        // Step 1: Copy content URI to app-private cache file
        val cacheFile = File(context.cacheDir, "img_intake_${System.currentTimeMillis()}_${(0..9999).random()}.tmp")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Could not open input stream from $uri")

            // Step 2: Read image dimensions without allocating full bitmap pixels
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(cacheFile.absolutePath, boundsOptions)

            val width = boundsOptions.outWidth
            val height = boundsOptions.outHeight
            if (width <= 0 || height <= 0) {
                throw IllegalStateException("Invalid image dimensions from $uri ($width x $height)")
            }

            // Step 3: Compute inSampleSize to downsample if larger than maxDimension
            var inSampleSize = 1
            var maxSide = maxOf(width, height)
            while (maxSide > maxDimension) {
                inSampleSize *= 2
                maxSide /= 2
            }

            // Step 4: Decode bitmap with downsampling
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decodedBitmap = BitmapFactory.decodeFile(cacheFile.absolutePath, decodeOptions)
                ?: throw IllegalStateException("BitmapFactory failed to decode $uri")

            // Step 5: Read EXIF orientation and rotate if necessary
            val exif = try {
                ExifInterface(cacheFile.absolutePath)
            } catch (_: Exception) {
                null
            }
            val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                ?: ExifInterface.ORIENTATION_NORMAL

            val rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            if (rotationDegrees != 0f) {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                val rotated = Bitmap.createBitmap(decodedBitmap, 0, 0, decodedBitmap.width, decodedBitmap.height, matrix, true)
                if (rotated != decodedBitmap) {
                    decodedBitmap.recycle()
                }
                rotated
            } else {
                decodedBitmap
            }
        } finally {
            cacheFile.delete()
        }
    }
}
