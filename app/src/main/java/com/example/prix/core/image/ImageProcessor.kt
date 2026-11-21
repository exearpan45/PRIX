package com.example.prix.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.example.prix.core.model.ImageOutputFormat
import com.example.prix.core.model.NormalizedPoint
import com.example.prix.core.model.QuadPoints
import com.example.prix.core.model.ScanFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ImageProcessor {

    suspend fun decodeSampledBitmap(
        filePath: String,
        maxDimension: Int = 2200
    ): Bitmap? = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val file = File(filePath)
        if (!file.exists()) return@withContext null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(filePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = try {
            BitmapFactory.decodeFile(filePath, decodeOpts)
        } catch (_: OutOfMemoryError) {
            null
        } ?: return@withContext null
        applyExifOrientation(filePath, decoded)
    }

    suspend fun decodeUriToTempFile(
        context: Context,
        uri: Uri,
        destFile: File,
        maxDimension: Int = 2400
    ): Boolean = withContext(Dispatchers.IO) {
        val tempRoot = File(context.cacheDir, "prix_tmp").apply { mkdirs() }
        val rawCopy = File.createTempFile("exif_", ".img", tempRoot)
        try {
            currentCoroutineContext().ensureActive()
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(rawCopy).use { output -> input.copyTo(output) }
            } ?: return@withContext false

            val bitmap = decodeSampledBitmap(rawCopy.absolutePath, maxDimension) ?: return@withContext false
            try {
                destFile.parentFile?.mkdirs()
                FileOutputStream(destFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
                true
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        } catch (_: Exception) {
            destFile.delete()
            false
        } catch (_: OutOfMemoryError) {
            destFile.delete()
            false
        } finally {
            rawCopy.delete()
        }
    }

    private fun applyExifOrientation(path: String, bitmap: Bitmap): Bitmap {
        return try {
            val exif = ExifInterface(path)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                else -> return bitmap
            }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            rotated
        } catch (_: Exception) {
            bitmap
        }
    }

    fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var sample = 1
        val largest = max(width, height)
        while (largest / sample > maxDim * 1.4f) {
            sample *= 2
        }
        return max(1, sample)
    }

    /**
     * Real gradient-projection document boundary detector.
     * Downsamples to ~200px, computes horizontal/vertical luminance gradients,
     * and searches outer margins for dominant paper edges.
     */
    suspend fun detectDocumentQuad(bitmap: Bitmap): QuadPoints = withContext(Dispatchers.Default) {
        val targetW = 180
        val aspect = bitmap.height.toFloat() / max(1, bitmap.width).toFloat()
        val targetH = max(80, (targetW * aspect).roundToInt().coerceAtMost(320))
        val small = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)

        val pixels = IntArray(targetW * targetH)
        small.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
        if (small != bitmap) small.recycle()

        val luma = FloatArray(targetW * targetH)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            luma[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        val colEnergy = FloatArray(targetW)
        val rowEnergy = FloatArray(targetH)
        for (y in 1 until targetH - 1) {
            val rowOff = y * targetW
            for (x in 1 until targetW - 1) {
                val gx = abs(luma[rowOff + x + 1] - luma[rowOff + x - 1])
                val gy = abs(luma[rowOff + targetW + x] - luma[rowOff - targetW + x])
                colEnergy[x] += gx
                rowEnergy[y] += gy
            }
        }

        val avgCol = colEnergy.average().toFloat()
        val avgRow = rowEnergy.average().toFloat()

        var leftIdx = (targetW * 0.06f).roundToInt()
        for (x in 3..(targetW * 0.35f).roundToInt()) {
            if (colEnergy[x] > avgCol * 1.45f) {
                leftIdx = x
                break
            }
        }

        var rightIdx = (targetW * 0.94f).roundToInt()
        for (x in (targetW - 4) downTo (targetW * 0.65f).roundToInt()) {
            if (colEnergy[x] > avgCol * 1.45f) {
                rightIdx = x
                break
            }
        }

        var topIdx = (targetH * 0.06f).roundToInt()
        for (y in 3..(targetH * 0.35f).roundToInt()) {
            if (rowEnergy[y] > avgRow * 1.45f) {
                topIdx = y
                break
            }
        }

        var bottomIdx = (targetH * 0.94f).roundToInt()
        for (y in (targetH - 4) downTo (targetH * 0.65f).roundToInt()) {
            if (rowEnergy[y] > avgRow * 1.45f) {
                bottomIdx = y
                break
            }
        }

        val l = (leftIdx.toFloat() / targetW).coerceIn(0.02f, 0.35f)
        val r = (rightIdx.toFloat() / targetW).coerceIn(0.65f, 0.98f)
        val t = (topIdx.toFloat() / targetH).coerceIn(0.02f, 0.35f)
        val b = (bottomIdx.toFloat() / targetH).coerceIn(0.65f, 0.98f)

        QuadPoints(
            topLeft = NormalizedPoint(l, t),
            topRight = NormalizedPoint(r, t),
            bottomRight = NormalizedPoint(r, b),
            bottomLeft = NormalizedPoint(l, b)
        )
    }

    /**
     * Real 4-point perspective warp using Matrix.setPolyToPoly.
     */
    suspend fun applyPerspectiveWarp(
        source: Bitmap,
        quad: QuadPoints
    ): Bitmap = withContext(Dispatchers.Default) {
        val w = source.width.toFloat()
        val h = source.height.toFloat()

        val sx0 = quad.topLeft.x.coerceIn(0f, 1f) * w
        val sy0 = quad.topLeft.y.coerceIn(0f, 1f) * h
        val sx1 = quad.topRight.x.coerceIn(0f, 1f) * w
        val sy1 = quad.topRight.y.coerceIn(0f, 1f) * h
        val sx2 = quad.bottomRight.x.coerceIn(0f, 1f) * w
        val sy2 = quad.bottomRight.y.coerceIn(0f, 1f) * h
        val sx3 = quad.bottomLeft.x.coerceIn(0f, 1f) * w
        val sy3 = quad.bottomLeft.y.coerceIn(0f, 1f) * h

        val topWidth = hypot(sx1 - sx0, sy1 - sy0)
        val bottomWidth = hypot(sx2 - sx3, sy2 - sy3)
        val leftHeight = hypot(sx3 - sx0, sy3 - sy0)
        val rightHeight = hypot(sx2 - sx1, sy2 - sy1)

        val outW = max(120, max(topWidth, bottomWidth).roundToInt()).coerceAtMost(2800)
        val outH = max(120, max(leftHeight, rightHeight).roundToInt()).coerceAtMost(3600)

        val srcPts = floatArrayOf(sx0, sy0, sx1, sy1, sx2, sy2, sx3, sy3)
        val dstPts = floatArrayOf(
            0f, 0f,
            outW.toFloat(), 0f,
            outW.toFloat(), outH.toFloat(),
            0f, outH.toFloat()
        )

        val matrix = Matrix()
        val ok = matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)
        if (!ok) {
            return@withContext Bitmap.createBitmap(source)
        }

        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, matrix, paint)
        out
    }

    fun rotateBitmap(source: Bitmap, degrees: Int): Bitmap {
        val norm = ((degrees % 360) + 360) % 360
        if (norm == 0) return source
        val matrix = Matrix().apply { postRotate(norm.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    fun cropBitmapNormalized(
        source: Bitmap,
        leftNorm: Float,
        topNorm: Float,
        rightNorm: Float,
        bottomNorm: Float
    ): Bitmap {
        val l = (leftNorm.coerceIn(0f, 0.95f) * source.width).roundToInt()
        val t = (topNorm.coerceIn(0f, 0.95f) * source.height).roundToInt()
        val r = (rightNorm.coerceIn(leftNorm + 0.05f, 1f) * source.width).roundToInt()
        val b = (bottomNorm.coerceIn(topNorm + 0.05f, 1f) * source.height).roundToInt()
        val w = max(16, r - l)
        val h = max(16, b - t)
        return Bitmap.createBitmap(source, l, t, w, h)
    }

    fun resizeBitmap(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        return Bitmap.createScaledBitmap(
            source,
            targetWidth.coerceIn(32, 4096),
            targetHeight.coerceIn(32, 4096),
            true
        )
    }

    suspend fun applyFilterAndAdjustments(
        source: Bitmap,
        filter: ScanFilter,
        brightness: Float = 0f, // -80..80
        contrast: Float = 1f    // 0.5..2.0
    ): Bitmap = withContext(Dispatchers.Default) {
        if (filter == ScanFilter.ORIGINAL && abs(brightness) < 1f && abs(contrast - 1f) < 0.02f) {
            return@withContext source.copy(Bitmap.Config.ARGB_8888, false)
        }

        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cm = ColorMatrix()

        when (filter) {
            ScanFilter.ORIGINAL -> Unit
            ScanFilter.AUTO_ENHANCE -> {
                val boost = ColorMatrix(
                    floatArrayOf(
                        1.18f, 0f, 0f, 0f, -14f,
                        0f, 1.18f, 0f, 0f, -14f,
                        0f, 0f, 1.18f, 0f, -14f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(boost)
            }
            ScanFilter.DOCUMENT -> {
                val gray = ColorMatrix().apply { setSaturation(0.15f) }
                val crisp = ColorMatrix(
                    floatArrayOf(
                        1.55f, 0f, 0f, 0f, -38f,
                        0f, 1.55f, 0f, 0f, -38f,
                        0f, 0f, 1.55f, 0f, -38f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(gray)
                cm.postConcat(crisp)
            }
            ScanFilter.GRAYSCALE -> {
                cm.setSaturation(0f)
            }
            ScanFilter.COLOR_ENHANCE -> {
                val sat = ColorMatrix().apply { setSaturation(1.38f) }
                val pop = ColorMatrix(
                    floatArrayOf(
                        1.12f, 0f, 0f, 0f, -8f,
                        0f, 1.12f, 0f, 0f, -8f,
                        0f, 0f, 1.12f, 0f, -8f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(sat)
                cm.postConcat(pop)
            }
            ScanFilter.BLACK_AND_WHITE -> {
                // Handled pixel-wise below after brightness/contrast
            }
        }

        // Apply manual contrast + brightness
        val translate = brightness + (1f - contrast) * 128f
        val bc = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        cm.postConcat(bc)

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)

        if (filter == ScanFilter.BLACK_AND_WHITE) {
            applyAdaptiveMonochrome(result)
        }
        result
    }

    private fun applyAdaptiveMonochrome(bitmap: Bitmap) {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val gray = IntArray(pixels.size)
        var sum = 0L
        for (i in pixels.indices) {
            val c = pixels[i]
            val l = (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
            gray[i] = l
            sum += l
        }
        val mean = (sum / max(1, pixels.size)).toInt()
        val threshold = (mean - 12).coerceIn(75, 195)

        for (i in pixels.indices) {
            val v = if (gray[i] >= threshold) 255 else 18
            pixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    suspend fun saveBitmapToFile(
        bitmap: Bitmap,
        destFile: File,
        format: ImageOutputFormat = ImageOutputFormat.JPEG,
        quality: Int = 90
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            destFile.parentFile?.mkdirs()
            val compressFormat = when (format) {
                ImageOutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
                ImageOutputFormat.PNG -> Bitmap.CompressFormat.PNG
                ImageOutputFormat.WEBP -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        @Suppress("DEPRECATION")
                        Bitmap.CompressFormat.WEBP
                    }
                }
            }
            FileOutputStream(destFile).use { out ->
                bitmap.compress(compressFormat, quality.coerceIn(10, 100), out)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
