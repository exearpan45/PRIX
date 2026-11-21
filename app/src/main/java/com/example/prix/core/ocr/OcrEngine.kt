package com.example.prix.core.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.prix.core.error.ProtectedPdfException
import com.example.prix.core.image.ImageProcessor
import com.example.prix.core.model.OcrResultData
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun extractFromImagePath(
        imagePath: String,
        maxDimension: Int = 2000
    ): Result<OcrResultData> = withContext(Dispatchers.IO) {
        try {
            currentCoroutineContext().ensureActive()
            val bitmap = ImageProcessor.decodeSampledBitmap(imagePath, maxDimension)
                ?: return@withContext Result.failure(IllegalArgumentException("Unable to decode image for OCR."))
            try {
                extractFromBitmap(bitmap)
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun extractFromBitmap(bitmap: Bitmap): Result<OcrResultData> = withContext(Dispatchers.Default) {
        try {
            currentCoroutineContext().ensureActive()
            val image = InputImage.fromBitmap(bitmap, 0)
            val result: Text = suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { text ->
                        if (cont.isActive) cont.resume(text)
                    }
                    .addOnFailureListener { exc ->
                        if (cont.isActive) cont.resumeWithException(exc)
                    }
            }
            val blocks = result.textBlocks.mapNotNull { it.text.trim().takeIf { t -> t.isNotEmpty() } }
            val full = result.text.trim()
            val note = if (full.isEmpty()) {
                "No recognizable Latin-script text was detected in this image."
            } else {
                "Processed locally on-device using Latin-script OCR. Non-Latin scripts may require a dedicated language pack."
            }
            Result.success(OcrResultData(fullText = full, blocks = blocks, note = note))
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun extractFromPdfPages(
        pdfFile: File,
        maxPages: Int = 10,
        maxWidthPx: Int = 1800
    ): Result<OcrResultData> = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            currentCoroutineContext().ensureActive()
            pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = try {
                PdfRenderer(pfd)
            } catch (sec: SecurityException) {
                throw ProtectedPdfException("PDF is password-protected. Unlock it before running OCR.")
            }
            val count = minOf(renderer.pageCount, maxPages)
            val allBlocks = mutableListOf<String>()
            val builder = StringBuilder()

            for (i in 0 until count) {
                currentCoroutineContext().ensureActive()
                renderer.openPage(i).use { page ->
                    val w = (page.width * 1.6f).toInt().coerceIn(720, maxWidthPx.coerceAtLeast(720))
                    val h = ((w.toFloat() / page.width.coerceAtLeast(1)) * page.height).toInt().coerceIn(720, 2400)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    try {
                        Canvas(bmp).drawColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val pageOcr = extractFromBitmap(bmp)
                        pageOcr.getOrNull()?.let { data ->
                            if (data.fullText.isNotBlank()) {
                                if (count > 1) builder.append("--- Page ${i + 1} ---\n")
                                builder.append(data.fullText).append("\n\n")
                                allBlocks.addAll(data.blocks)
                            }
                        }
                    } finally {
                        if (!bmp.isRecycled) bmp.recycle()
                    }
                }
            }
            val extraNote = if (renderer.pageCount > maxPages) {
                "Processed first $maxPages of ${renderer.pageCount} pages on-device (Latin script)."
            } else {
                "Processed locally on-device (Latin script)."
            }
            Result.success(
                OcrResultData(
                    fullText = builder.toString().trim(),
                    blocks = allBlocks,
                    note = extraNote
                )
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }
}
