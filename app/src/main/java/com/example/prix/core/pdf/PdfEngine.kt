package com.example.prix.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.example.prix.core.error.CorruptedDocumentException
import com.example.prix.core.error.ProtectedPdfException
import com.example.prix.core.image.ImageProcessor
import com.example.prix.core.model.CompressionLevel
import com.example.prix.core.model.CompressionResult
import com.example.prix.core.model.ImageOutputFormat
import com.example.prix.core.model.NormalizedImageOverlay
import com.example.prix.core.model.NormalizedShape
import com.example.prix.core.model.NormalizedStroke
import com.example.prix.core.model.NormalizedTextBox
import com.example.prix.core.model.PageBackground
import com.example.prix.core.model.PageNumberPosition
import com.example.prix.core.model.PageTransformItem
import com.example.prix.core.model.PdfInfoMetadata
import com.example.prix.core.model.PdfMargin
import com.example.prix.core.model.PdfOrientation
import com.example.prix.core.model.PdfPageSize
import com.example.prix.core.model.PdfQuality
import com.example.prix.core.model.ShapeKind
import com.example.prix.core.model.WatermarkPosition
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.multipdf.Splitter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class PdfEngine(private val context: Context) {

    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    suspend fun createPdfFromImages(
        imagePaths: List<String>,
        outputFile: File,
        pageSize: PdfPageSize = PdfPageSize.A4,
        orientation: PdfOrientation = PdfOrientation.PORTRAIT,
        margin: PdfMargin = PdfMargin.NARROW,
        quality: PdfQuality = PdfQuality.BALANCED,
        background: PageBackground = PageBackground.WHITE,
        fitToPage: Boolean = false,
        onProgress: (Float) -> Unit = {}
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (imagePaths.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Select at least one image."))
        }
        val doc = PDDocument(MemoryUsageSetting.setupTempFileOnly())
        try {
            val total = imagePaths.size
            imagePaths.forEachIndexed { idx, path ->
                currentCoroutineContext().ensureActive()
                val bmp = ImageProcessor.decodeSampledBitmap(path, quality.maxDimensionPx)
                    ?: throw IllegalStateException("Could not decode image ${idx + 1}.")

                try {
                    val rect = resolvePageRect(pageSize, orientation, bmp.width, bmp.height)
                    val page = PDPage(rect)
                    doc.addPage(page)

                    val pdImage = JPEGFactory.createFromImage(
                        doc,
                        bmp,
                        quality.jpegQuality / 100f
                    )

                    PDPageContentStream(doc, page).use { cs ->
                        if (background != PageBackground.WHITE) {
                            val r = Color.red(background.argb)
                            val g = Color.green(background.argb)
                            val b = Color.blue(background.argb)
                            cs.setNonStrokingColor(r, g, b)
                            cs.addRect(0f, 0f, rect.width, rect.height)
                            cs.fill()
                        }

                        val m = margin.points.coerceAtMost(min(rect.width, rect.height) * 0.25f)
                        val availW = max(20f, rect.width - 2f * m)
                        val availH = max(20f, rect.height - 2f * m)

                        if (fitToPage) {
                            cs.drawImage(pdImage, m, m, availW, availH)
                        } else {
                            val imgRatio = bmp.width.toFloat() / max(1, bmp.height).toFloat()
                            val boxRatio = availW / availH
                            val drawW: Float
                            val drawH: Float
                            if (imgRatio > boxRatio) {
                                drawW = availW
                                drawH = availW / imgRatio
                            } else {
                                drawH = availH
                                drawW = availH * imgRatio
                            }
                            val x = m + (availW - drawW) / 2f
                            val y = m + (availH - drawH) / 2f
                            cs.drawImage(pdImage, x, y, drawW, drawH)
                        }
                    }
                } finally {
                    if (!bmp.isRecycled) bmp.recycle()
                }
                onProgress((idx + 1).toFloat() / total.toFloat())
            }
            currentCoroutineContext().ensureActive()
            outputFile.parentFile?.mkdirs()
            doc.save(outputFile)
            verifySavedPdf(outputFile)
            Result.success(doc.numberOfPages)
        } catch (ce: CancellationException) {
            outputFile.delete()
            throw ce
        } catch (e: Exception) {
            outputFile.delete()
            Result.failure(e)
        } finally {
            try { doc.close() } catch (_: Exception) {}
        }
    }

    private fun resolvePageRect(
        size: PdfPageSize,
        orientation: PdfOrientation,
        imgW: Int,
        imgH: Int
    ): PDRectangle {
        if (size == PdfPageSize.ORIGINAL) {
            val scale = if (max(imgW, imgH) > 1600) 1600f / max(imgW, imgH) else 1f
            val w = max(120f, imgW * scale * 0.72f)
            val h = max(120f, imgH * scale * 0.72f)
            return if (orientation == PdfOrientation.LANDSCAPE && h > w) PDRectangle(h, w)
            else PDRectangle(w, h)
        }
        val w = size.widthPt
        val h = size.heightPt
        return if (orientation == PdfOrientation.LANDSCAPE) PDRectangle(h, w) else PDRectangle(w, h)
    }

    suspend fun mergePdfs(
        inputFiles: List<File>,
        outputFile: File
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (inputFiles.size < 2) {
            return@withContext Result.failure(IllegalArgumentException("Select at least 2 PDFs to merge."))
        }
        try {
            outputFile.parentFile?.mkdirs()
            val merger = PDFMergerUtility()
            inputFiles.forEach { file ->
                currentCoroutineContext().ensureActive()
                if (!file.exists()) throw IllegalArgumentException("Missing file: ${file.name}")
                PDDocument.load(file).use { check ->
                    if (check.isEncrypted) {
                        throw ProtectedPdfException("${file.name} is password-protected. Unlock it first.")
                    }
                }
                merger.addSource(file)
            }
            currentCoroutineContext().ensureActive()
            merger.destinationFileName = outputFile.absolutePath
            merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
            verifySavedPdf(outputFile)
            val pages = getPageCount(outputFile)
            Result.success(pages)
        } catch (ce: CancellationException) {
            outputFile.delete()
            throw ce
        } catch (e: Exception) {
            outputFile.delete()
            Result.failure(e)
        }
    }

    suspend fun splitPdfByRanges(
        inputFile: File,
        ranges: List<IntRange>,
        outputFilesProvider: (index: Int) -> File
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        var source: PDDocument? = null
        val created = mutableListOf<File>()
        try {
            source = PDDocument.load(inputFile)
            if (source.isEncrypted) {
                return@withContext Result.failure(ProtectedPdfException("PDF is password-protected. Remove password first."))
            }
            val total = source.numberOfPages
            if (total == 0) return@withContext Result.failure(CorruptedDocumentException("PDF has 0 pages."))

            ranges.forEachIndexed { idx, range ->
                currentCoroutineContext().ensureActive()
                val start = range.first.coerceIn(1, total)
                val end = range.last.coerceIn(start, total)
                val splitter = Splitter().apply {
                    setStartPage(start)
                    setEndPage(end)
                    setSplitAtPage(end - start + 1)
                }
                val parts = splitter.split(source)
                if (parts.isNotEmpty()) {
                    val out = outputFilesProvider(idx)
                    out.parentFile?.mkdirs()
                    parts.first().use { partDoc ->
                        partDoc.save(out)
                    }
                    parts.drop(1).forEach { it.close() }
                    verifySavedPdf(out)
                    created.add(out)
                }
            }
            Result.success(created)
        } catch (ce: CancellationException) {
            created.forEach { it.delete() }
            throw ce
        } catch (e: Exception) {
            created.forEach { it.delete() }
            Result.failure(e)
        } finally {
            try { source?.close() } catch (_: Exception) {}
        }
    }

    suspend fun splitEveryPage(
        inputFile: File,
        outputFilesProvider: (pageNumber1Based: Int) -> File
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        var source: PDDocument? = null
        val created = mutableListOf<File>()
        try {
            source = PDDocument.load(inputFile)
            if (source.isEncrypted) {
                return@withContext Result.failure(ProtectedPdfException("PDF is password-protected."))
            }
            val splitter = Splitter()
            val parts = splitter.split(source)
            parts.forEachIndexed { idx, partDoc ->
                currentCoroutineContext().ensureActive()
                val out = outputFilesProvider(idx + 1)
                out.parentFile?.mkdirs()
                partDoc.use { it.save(out) }
                verifySavedPdf(out)
                created.add(out)
            }
            Result.success(created)
        } catch (ce: CancellationException) {
            created.forEach { it.delete() }
            throw ce
        } catch (e: Exception) {
            created.forEach { it.delete() }
            Result.failure(e)
        } finally {
            try { source?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Genuine PDF compression by rendering pages at controlled DPI and JPEG quality.
     * Honestly compares output bytes vs original bytes and never claims reduction if larger/unchanged.
     */
    suspend fun compressPdf(
        inputFile: File,
        level: CompressionLevel,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<CompressionResult> = withContext(Dispatchers.IO) {
        val originalSize = inputFile.length()
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        val outDoc = PDDocument(MemoryUsageSetting.setupTempFileOnly())
        try {
            pfd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = try {
                PdfRenderer(pfd)
            } catch (_: SecurityException) {
                throw ProtectedPdfException("PDF is password-protected. Unlock it before compressing.")
            }
            val count = renderer.pageCount
            if (count <= 0) {
                return@withContext Result.failure(CorruptedDocumentException("PDF contains no pages."))
            }

            val scaleFactor = level.dpi / 72f
            for (i in 0 until count) {
                currentCoroutineContext().ensureActive()
                renderer.openPage(i).use { page ->
                    val ptW = page.width.toFloat().coerceAtLeast(72f)
                    val ptH = page.height.toFloat().coerceAtLeast(72f)
                    val pxW = (ptW * scaleFactor).toInt().coerceIn(240, 2200)
                    val pxH = (ptH * scaleFactor).toInt().coerceIn(240, 3000)

                    val bmp = Bitmap.createBitmap(pxW, pxH, Bitmap.Config.ARGB_8888)
                    try {
                        Canvas(bmp).drawColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        val newPage = PDPage(PDRectangle(ptW, ptH))
                        outDoc.addPage(newPage)
                        val pdImage = JPEGFactory.createFromImage(outDoc, bmp, level.jpegQuality / 100f)

                        PDPageContentStream(outDoc, newPage).use { cs ->
                            cs.drawImage(pdImage, 0f, 0f, ptW, ptH)
                        }
                    } finally {
                        if (!bmp.isRecycled) bmp.recycle()
                    }
                }
                onProgress((i + 1).toFloat() / count.toFloat())
            }

            currentCoroutineContext().ensureActive()
            outputFile.parentFile?.mkdirs()
            outDoc.save(outputFile)
            verifySavedPdf(outputFile)
            val outSize = outputFile.length()

            if (outSize >= originalSize) {
                outputFile.delete()
                Result.success(
                    CompressionResult(
                        originalBytes = originalSize,
                        outputBytes = outSize,
                        savedDocumentId = null,
                        reduced = false,
                        message = "This PDF is already highly optimized. Re-encoding at ${level.label} would not reduce its size ($outSize B vs $originalSize B original), so the original was kept untouched."
                    )
                )
            } else {
                val pct = (((originalSize - outSize).toFloat() / originalSize.toFloat()) * 100).toInt()
                Result.success(
                    CompressionResult(
                        originalBytes = originalSize,
                        outputBytes = outSize,
                        savedDocumentId = null,
                        reduced = true,
                        message = "Reduced file size by $pct% using ${level.label}."
                    )
                )
            }
        } catch (ce: CancellationException) {
            outputFile.delete()
            throw ce
        } catch (e: Exception) {
            outputFile.delete()
            Result.failure(e)
        } finally {
            try { outDoc.close() } catch (_: Exception) {}
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    suspend fun rotatePages(
        inputFile: File,
        rotationsByPage0Based: Map<Int, Int>,
        outputFile: File
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            PDDocument.load(inputFile).use { doc ->
                if (doc.isEncrypted) return@withContext Result.failure(ProtectedPdfException("PDF is password-protected."))
                for (i in 0 until doc.numberOfPages) {
                    currentCoroutineContext().ensureActive()
                    val delta = rotationsByPage0Based[i] ?: 0
                    if (delta != 0) {
                        val page = doc.getPage(i)
                        page.rotation = ((page.rotation + delta) % 360 + 360) % 360
                    }
                }
                outputFile.parentFile?.mkdirs()
                doc.save(outputFile)
                verifySavedPdf(outputFile)
                Result.success(doc.numberOfPages)
            }
        } catch (ce: CancellationException) {
            outputFile.delete()
            throw ce
        } catch (e: Exception) {
            outputFile.delete()
            Result.failure(e)
        }
    }

    suspend fun rebuildPdfWithPagePlan(
        inputFile: File,
        pagePlan: List<PageTransformItem>,
        outputFile: File
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (pagePlan.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Cannot save a PDF with 0 pages."))
        }
        var source: PDDocument? = null
        val dest = PDDocument(MemoryUsageSetting.setupTempFileOnly())
        try {
            source = PDDocument.load(inputFile)
            if (source.isEncrypted) return@withContext Result.failure(ProtectedPdfException("PDF is password-protected."))
            pagePlan.forEach { item ->
                currentCoroutineContext().ensureActive()
                val srcPage = source.getPage(item.originalPageIndex)
                val imported = dest.importPage(srcPage)
                if (item.extraRotationDegrees != 0) {
                    imported.rotation = ((imported.rotation + item.extraRotationDegrees) % 360 + 360) % 360
                }
            }
            outputFile.parentFile?.mkdirs()
            dest.save(outputFile)
            verifySavedPdf(outputFile)
            Result.success(dest.numberOfPages)
        } catch (ce: CancellationException) {
            outputFile.delete()
            throw ce
        } catch (e: Exception) {
            outputFile.delete()
            Result.failure(e)
        } finally {
            try { dest.close() } catch (_: Exception) {}
            try { source?.close() } catch (_: Exception) {}
        }
    }

    suspend fun pdfToImages(
        inputFile: File,
        format: ImageOutputFormat,
        dpi: Int = 150,
        outputFileProvider: (pageNumber1Based: Int) -> File
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        val created = mutableListOf<File>()
        try {
            pfd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = try {
                PdfRenderer(pfd)
            } catch (_: SecurityException) {
                throw ProtectedPdfException("PDF is password-protected. Unlock it before exporting images.")
            }
            val scale = dpi / 72f
            for (i in 0 until renderer.pageCount) {
                currentCoroutineContext().ensureActive()
                renderer.openPage(i).use { page ->
                    val w = (page.width * scale).toInt().coerceIn(360, 2600)
                    val h = (page.height * scale).toInt().coerceIn(360, 3400)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val out = outputFileProvider(i + 1)
                    val saved = try {
                        Canvas(bmp).drawColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        ImageProcessor.saveBitmapToFile(bmp, out, format, 92)
                    } finally {
                        if (!bmp.isRecycled) bmp.recycle()
                    }
                    if (saved) created.add(out)
                }
            }
            Result.success(created)
        } catch (ce: CancellationException) {
            created.forEach { it.delete() }
            throw ce
        } catch (e: Exception) {
            created.forEach { it.delete() }
            Result.failure(e)
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    suspend fun addWatermark(
        inputFile: File,
        watermarkText: String,
        opacity: Float,
        fontSize: Float,
        colorArgb: Int,
        position: WatermarkPosition,
        outputFile: File
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (watermarkText.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Watermark text cannot be empty."))
        }
        try {
            PDDocument.load(inputFile).use { doc ->
                if (doc.isEncrypted) return@withContext Result.failure(ProtectedPdfException("PDF is password-protected."))
                val font = PDType1Font.HELVETICA_BOLD
                val r = Color.red(colorArgb)
                val g = Color.green(colorArgb)
                val b = Color.blue(colorArgb)

                for (i in 0 until doc.numberOfPages) {
                    currentCoroutineContext().ensureActive()
                    val page = doc.getPage(i)
                    val box = page.cropBox ?: page.mediaBox
                    val gs = PDExtendedGraphicsState().apply {
                        nonStrokingAlphaConstant = opacity.coerceIn(0.08f, 0.9f)
                    }
                    PDPageContentStream(
                        doc,
                        page,
                        PDPageContentStream.AppendMode.APPEND,
                        true,
                        true
                    ).use { cs ->
                        cs.setGraphicsStateParameters(gs)
                        cs.beginText()
                        cs.setFont(font, fontSize)
                        cs.setNonStrokingColor(r, g, b)

                        val textW = try {
                            (font.getStringWidth(watermarkText) / 1000f) * fontSize
                        } catch (_: Exception) {
                            watermarkText.length * fontSize * 0.5f
                        }

                        when (position) {
                            WatermarkPosition.DIAGONAL_CENTER -> {
                                val cx = box.lowerLeftX + box.width / 2f
                                val cy = box.lowerLeftY + box.height / 2f
                                val rad = Math.toRadians(42.0)
                                val dx = -(textW / 2f) * cos(rad).toFloat()
                                val dy = -(textW / 2f) * sin(rad).toFloat()
                                cs.setTextMatrix(Matrix.getRotateInstance(rad, cx + dx, cy + dy))
                            }
                            WatermarkPosition.TOP_CENTER -> {
                                val x = box.lowerLeftX + (box.width - textW) / 2f
                                val y = box.upperRightY - fontSize - 28f
                                cs.newLineAtOffset(x, y)
                            }
                            WatermarkPosition.BOTTOM_CENTER -> {
                                val x = box.lowerLeftX + (box.width - textW) / 2f
                                val y = box.lowerLeftY + 28f
                                cs.newLineAtOffset(x, y)
                            }
                        }
                        cs.showText(sanitizePdfText(watermarkText))
                        cs.endText()
                    }
                }
                outputFile.parentFile?.mkdirs()
                doc.save(outputFile)
                verifySavedPdf(outputFile)
                Result.success(doc.numberOfPages)
            }
        } catch (ce: CancellationException) {
            outputFile.delete()
            throw ce
        } catch (e: Exception) {
            outputFile.delete()
            Result.failure(e)
        }
    }

    suspend fun addPageNumbers(
        inputFile: File,
        position: PageNumberPosition,
        startFrom: Int = 1,
        includeTotal: Boolean = true,
        outputFile: File
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            PDDocument.load(inputFile).use { doc ->
                if (doc.isEncrypted) return@withContext Result.failure(ProtectedPdfException("PDF is password-protected."))
                val font = PDType1Font.HELVETICA
                val fontSize = 11f
                val total = doc.numberOfPages

                for (i in 0 until total) {
                    currentCoroutineContext().ensureActive()
                    val page = doc.getPage(i)
                    val box = page.cropBox ?: page.mediaBox
                    val label = if (includeTotal) "${startFrom + i} / ${startFrom + total - 1}" else "${startFrom + i}"
                    val textW = (font.getStringWidth(label) / 1000f) * fontSize

                    val x = when (position) {
                        PageNumberPosition.BOTTOM_CENTER -> box.lowerLeftX + (box.width - textW) / 2f
                        PageNumberPosition.BOTTOM_RIGHT, PageNumberPosition.TOP_RIGHT -> box.upperRightX - textW - 28f
                    }
                    val y = when (position) {
                        PageNumberPosition.BOTTOM_CENTER, PageNumberPosition.BOTTOM_RIGHT -> box.lowerLeftY + 22f
                        PageNumberPosition.TOP_RIGHT -> box.upperRightY - 28f
                    }

                    PDPageContentStream(
                        doc,
                        page,
                        PDPageContentStream.AppendMode.APPEND,
                        true,
                        true
                    ).use { cs ->
                        cs.beginText()
                        cs.setFont(font, fontSize)
                        cs.setNonStrokingColor(70, 70, 88)
                        cs.newLineAtOffset(x, y)
                        cs.showText(label)
                        cs.endText()
                    }
                }
                outputFile.parentFile?.mkdirs()
                doc.save(outputFile)
                verifySavedPdf(outputFile)
                Result.success(total)
            }
        } catch (ce: CancellationException) {
            outputFile.delete()
            throw ce
        } catch (e: Exception) {
            outputFile.delete()
            Result.failure(e)
        }
    }

    suspend fun encryptPdf(
        inputFile: File,
        userPassword: String,
        ownerPassword: String,
        outputFile: File
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (userPassword.length < 4) {
            return@withContext Result.failure(IllegalArgumentException("Password must be at least 4 characters."))
        }
        try {
            PDDocument.load(inputFile).use { doc ->
                if (doc.isEncrypted) return@withContext Result.failure(ProtectedPdfException("PDF is already password-protected."))
                val ap = AccessPermission()
                val spp = StandardProtectionPolicy(ownerPassword.ifBlank { userPassword }, userPassword, ap).apply {
                    encryptionKeyLength = 128
                }
                doc.protect(spp)
                outputFile.parentFile?.mkdirs()
                doc.save(outputFile)
                verifySavedPdf(outputFile)
            }
            Result.success(Unit)
        } catch (ce: CancellationException) {
            outputFile.delete()
            throw ce
        } catch (e: Exception) {
            outputFile.delete()
            Result.failure(e)
        }
    }

    suspend fun decryptPdf(
        inputFile: File,
        password: String,
        outputFile: File
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            PDDocument.load(inputFile, password).use { doc ->
                doc.isAllSecurityToBeRemoved = true
                outputFile.parentFile?.mkdirs()
                doc.save(outputFile)
                verifySavedPdf(outputFile)
            }
            Result.success(Unit)
        } catch (_: InvalidPasswordException) {
            Result.failure(IllegalArgumentException("Incorrect password. Please verify your authorization password."))
        } catch (ce: CancellationException) {
            outputFile.delete()
            throw ce
        } catch (e: Exception) {
            outputFile.delete()
            Result.failure(e)
        }
    }

    suspend fun inspectPdfInfo(file: File, password: String? = null): Result<PdfInfoMetadata> =
        withContext(Dispatchers.IO) {
            try {
                val doc = if (password.isNullOrEmpty()) PDDocument.load(file) else PDDocument.load(file, password)
                doc.use {
                    val info = it.documentInformation
                    val firstBox = if (it.numberOfPages > 0) {
                        val p = it.getPage(0)
                        p.cropBox ?: p.mediaBox
                    } else PDRectangle.A4

                    Result.success(
                        PdfInfoMetadata(
                            fileName = file.name,
                            sizeBytes = file.length(),
                            pageCount = it.numberOfPages,
                            pdfVersion = "PDF ${it.version}",
                            title = info?.title.orEmpty(),
                            author = info?.author.orEmpty(),
                            creator = info?.creator.orEmpty(),
                            producer = info?.producer.orEmpty(),
                            isEncrypted = it.isEncrypted,
                            firstPageWidthPt = firstBox.width,
                            firstPageHeightPt = firstBox.height
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun repairPdf(inputFile: File, outputFile: File): Result<Int> = withContext(Dispatchers.IO) {
        try {
            PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                if (doc.numberOfPages <= 0) {
                    return@withContext Result.failure(CorruptedDocumentException("No recoverable pages found."))
                }
                outputFile.parentFile?.mkdirs()
                doc.save(outputFile)
                verifySavedPdf(outputFile)
                Result.success(doc.numberOfPages)
            }
        } catch (ce: CancellationException) {
            outputFile.delete()
            throw ce
        } catch (e: Exception) {
            outputFile.delete()
            Result.failure(CorruptedDocumentException("Could not repair this PDF structure: ${e.localizedMessage ?: "Unsupported corruption"}"))
        }
    }

    /**
     * Applies non-destructive overlay annotations, text boxes, shapes, signatures, image overlays,
     * and page ordering/rotation edits onto a PDF document. Correctly transforms normalized visual
     * coordinates (0..1) into unrotated PDF user space across 0, 90, 180, and 270 page rotations.
     */
    suspend fun applyEdits(
        inputFile: File,
        pagePlan: List<PageTransformItem>,
        strokes: List<NormalizedStroke>,
        textBoxes: List<NormalizedTextBox>,
        shapes: List<NormalizedShape>,
        imageOverlays: List<NormalizedImageOverlay>,
        outputFile: File
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (pagePlan.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Cannot save an empty PDF."))
        }
        var source: PDDocument? = null
        val dest = PDDocument(MemoryUsageSetting.setupTempFileOnly())
        try {
            source = PDDocument.load(inputFile)
            if (source.isEncrypted) {
                return@withContext Result.failure(ProtectedPdfException("Unlock password-protected PDF before editing."))
            }

            pagePlan.forEachIndexed { destIndex, item ->
                currentCoroutineContext().ensureActive()
                val srcPage = source.getPage(item.originalPageIndex)
                val page = dest.importPage(srcPage)
                if (item.extraRotationDegrees != 0) {
                    page.rotation = ((page.rotation + item.extraRotationDegrees) % 360 + 360) % 360
                }
                val effectiveRotation = normalizeQuarterTurn(page.rotation)
                val box = page.cropBox ?: page.mediaBox
                val visualW = if (effectiveRotation == 90 || effectiveRotation == 270) box.height else box.width
                val visualH = if (effectiveRotation == 90 || effectiveRotation == 270) box.width else box.height
                val thetaRad = Math.toRadians(effectiveRotation.toDouble())

                val pageStrokes = strokes.filter { it.pageIndex == destIndex }
                val pageTexts = textBoxes.filter { it.pageIndex == destIndex }
                val pageShapes = shapes.filter { it.pageIndex == destIndex }
                val pageImages = imageOverlays.filter { it.pageIndex == destIndex }

                if (pageStrokes.isNotEmpty() || pageTexts.isNotEmpty() || pageShapes.isNotEmpty() || pageImages.isNotEmpty()) {
                    PDPageContentStream(
                        dest,
                        page,
                        PDPageContentStream.AppendMode.APPEND,
                        true,
                        true
                    ).use { cs ->
                        // Draw images / signatures upright relative to visual page rotation
                        pageImages.forEach { overlay ->
                            val bmp = ImageProcessor.decodeSampledBitmap(overlay.imagePath, 1000)
                            if (bmp != null) {
                                try {
                                    val pdImg = LosslessFactory.createFromImage(dest, bmp)
                                    val w = overlay.widthNorm * visualW
                                    val h = overlay.heightNorm * visualH
                                    val origin = mapNormalizedToPdfPoint(
                                        u = overlay.xNorm,
                                        v = overlay.yNorm + overlay.heightNorm,
                                        box = box,
                                        effectiveRotationDeg = effectiveRotation
                                    )
                                    cs.saveGraphicsState()
                                    cs.transform(Matrix.getRotateInstance(thetaRad, origin.first, origin.second))
                                    cs.drawImage(pdImg, 0f, 0f, w, h)
                                    cs.restoreGraphicsState()
                                } finally {
                                    if (!bmp.isRecycled) bmp.recycle()
                                }
                            }
                        }

                        // Draw shapes
                        pageShapes.forEach { shape ->
                            val r = Color.red(shape.colorArgb)
                            val g = Color.green(shape.colorArgb)
                            val b = Color.blue(shape.colorArgb)
                            cs.setStrokingColor(r, g, b)
                            cs.setLineWidth((shape.strokeWidthNorm * visualW).coerceIn(1f, 18f))

                            val p1 = mapNormalizedToPdfPoint(shape.startX, shape.startY, box, effectiveRotation)
                            val p2 = mapNormalizedToPdfPoint(shape.endX, shape.endY, box, effectiveRotation)

                            when (shape.kind) {
                                ShapeKind.RECTANGLE -> {
                                    val rx = min(p1.first, p2.first)
                                    val ry = min(p1.second, p2.second)
                                    cs.addRect(rx, ry, kotlin.math.abs(p2.first - p1.first), kotlin.math.abs(p2.second - p1.second))
                                    cs.stroke()
                                }
                                ShapeKind.CIRCLE -> {
                                    val rx = min(p1.first, p2.first)
                                    val ry = min(p1.second, p2.second)
                                    val rw = kotlin.math.abs(p2.first - p1.first)
                                    val rh = kotlin.math.abs(p2.second - p1.second)
                                    drawEllipseApprox(cs, rx + rw / 2f, ry + rh / 2f, rw / 2f, rh / 2f)
                                    cs.stroke()
                                }
                                ShapeKind.LINE, ShapeKind.UNDERLINE -> {
                                    cs.moveTo(p1.first, p1.second)
                                    cs.lineTo(p2.first, p2.second)
                                    cs.stroke()
                                }
                            }
                        }

                        // Draw strokes (pen + highlighter)
                        pageStrokes.forEach { stroke ->
                            if (stroke.points.size >= 2) {
                                val gs = PDExtendedGraphicsState().apply {
                                    strokingAlphaConstant = stroke.alpha.coerceIn(0.1f, 1f)
                                }
                                cs.setGraphicsStateParameters(gs)
                                val r = Color.red(stroke.colorArgb)
                                val g = Color.green(stroke.colorArgb)
                                val b = Color.blue(stroke.colorArgb)
                                cs.setStrokingColor(r, g, b)
                                cs.setLineWidth((stroke.strokeWidthNorm * visualW).coerceIn(1f, 36f))
                                cs.setLineCapStyle(1)
                                cs.setLineJoinStyle(1)

                                val firstPt = stroke.points.first()
                                val firstMapped = mapNormalizedToPdfPoint(firstPt.x, firstPt.y, box, effectiveRotation)
                                cs.moveTo(firstMapped.first, firstMapped.second)
                                stroke.points.drop(1).forEach { pt ->
                                    val mapped = mapNormalizedToPdfPoint(pt.x, pt.y, box, effectiveRotation)
                                    cs.lineTo(mapped.first, mapped.second)
                                }
                                cs.stroke()
                            }
                        }

                        // Reset opacity to 1f for text
                        val solidGs = PDExtendedGraphicsState().apply {
                            nonStrokingAlphaConstant = 1f
                        }
                        cs.setGraphicsStateParameters(solidGs)

                        // Draw text boxes upright relative to visual page rotation
                        val font = PDType1Font.HELVETICA_BOLD
                        pageTexts.forEach { tb ->
                            val r = Color.red(tb.colorArgb)
                            val g = Color.green(tb.colorArgb)
                            val b = Color.blue(tb.colorArgb)
                            val lines = tb.text.lines()
                            lines.forEachIndexed { lineIdx, line ->
                                val yDownPt = tb.fontSizePt + lineIdx * (tb.fontSizePt * 1.25f)
                                val vLine = (tb.yNorm + yDownPt / max(1f, visualH)).coerceIn(0f, 1f)
                                val textOrigin = mapNormalizedToPdfPoint(tb.xNorm, vLine, box, effectiveRotation)
                                cs.beginText()
                                cs.setFont(font, tb.fontSizePt)
                                cs.setNonStrokingColor(r, g, b)
                                cs.setTextMatrix(Matrix.getRotateInstance(thetaRad, textOrigin.first, textOrigin.second))
                                cs.showText(sanitizePdfText(line))
                                cs.endText()
                            }
                        }
                    }
                }
            }

            currentCoroutineContext().ensureActive()
            outputFile.parentFile?.mkdirs()
            dest.save(outputFile)
            verifySavedPdf(outputFile)
            Result.success(dest.numberOfPages)
        } catch (ce: CancellationException) {
            outputFile.delete()
            throw ce
        } catch (e: Exception) {
            outputFile.delete()
            Result.failure(e)
        } finally {
            try { dest.close() } catch (_: Exception) {}
            try { source?.close() } catch (_: Exception) {}
        }
    }

    private fun drawEllipseApprox(
        cs: PDPageContentStream,
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float
    ) {
        val k = 0.5522848f
        val ox = rx * k
        val oy = ry * k
        cs.moveTo(cx - rx, cy)
        cs.curveTo(cx - rx, cy + oy, cx - ox, cy + ry, cx, cy + ry)
        cs.curveTo(cx + ox, cy + ry, cx + rx, cy + oy, cx + rx, cy)
        cs.curveTo(cx + rx, cy - oy, cx + ox, cy - ry, cx, cy - ry)
        cs.curveTo(cx - ox, cy - ry, cx - rx, cy - oy, cx - rx, cy)
    }

    suspend fun extractTextPerPage(file: File): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            PDDocument.load(file).use { doc ->
                if (doc.isEncrypted) return@withContext Result.failure(ProtectedPdfException("PDF is encrypted."))
                val stripper = PDFTextStripper()
                val pages = mutableListOf<String>()
                for (i in 1..doc.numberOfPages) {
                    currentCoroutineContext().ensureActive()
                    stripper.startPage = i
                    stripper.endPage = i
                    pages.add(stripper.getText(doc).trim())
                }
                Result.success(pages)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun verifySavedPdf(file: File) {
        if (!file.exists() || file.length() < 8L) {
            throw CorruptedDocumentException("Generated PDF output is empty.")
        }
    }

    companion object {
        fun normalizeQuarterTurn(degrees: Int): Int {
            val m = ((degrees % 360) + 360) % 360
            return ((m + 45) / 90 * 90) % 360
        }

        /**
         * Maps normalized visual viewer coordinates (u: 0..1 left->right, v: 0..1 top->bottom)
         * onto unrotated PDF user-space coordinates inside [box] for a page whose /Rotate is [effectiveRotationDeg].
         */
        fun mapNormalizedToPdfPoint(
            u: Float,
            v: Float,
            box: PDRectangle,
            effectiveRotationDeg: Int
        ): Pair<Float, Float> {
            val rot = normalizeQuarterTurn(effectiveRotationDeg)
            val uc = u.coerceIn(0f, 1f)
            val vc = v.coerceIn(0f, 1f)
            val llx = box.lowerLeftX
            val lly = box.lowerLeftY
            val urx = box.upperRightX
            val ury = box.upperRightY
            val w = box.width
            val h = box.height
            return when (rot) {
                90 -> (llx + vc * w) to (lly + uc * h)
                180 -> (urx - uc * w) to (lly + vc * h)
                270 -> (urx - vc * w) to (ury - uc * h)
                else -> (llx + uc * w) to (ury - vc * h)
            }
        }
    }

    suspend fun getPageCount(file: File): Int = withContext(Dispatchers.IO) {
        try {
            PDDocument.load(file).use { return@withContext it.numberOfPages }
        } catch (_: Throwable) {
            var pfd: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            try {
                pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(pfd)
                renderer.pageCount
            } catch (_: Throwable) {
                0
            } finally {
                try { renderer?.close() } catch (_: Throwable) {}
                try { pfd?.close() } catch (_: Throwable) {}
            }
        }
    }

    suspend fun renderPdfPageBitmap(
        file: File,
        pageIndex: Int,
        targetWidthPx: Int = 1080
    ): Bitmap? = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null
            renderer.openPage(pageIndex).use { page ->
                val clampedW = targetWidthPx.coerceIn(180, 2200)
                val targetH = ((clampedW.toFloat() / page.width.coerceAtLeast(1)) * page.height)
                    .toInt().coerceIn(180, 3200)
                val bmp = Bitmap.createBitmap(clampedW, targetH, Bitmap.Config.ARGB_8888)
                Canvas(bmp).drawColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        } catch (_: Throwable) {
            null
        } finally {
            try { renderer?.close() } catch (_: Throwable) {}
            try { pfd?.close() } catch (_: Throwable) {}
        }
    }

    fun printPdf(activityContext: Context, file: File, jobName: String) {
        if (!file.exists()) return
        var ctx = activityContext
        while (ctx is android.content.ContextWrapper && ctx !is android.app.Activity) {
            ctx = ctx.baseContext
        }
        val printManager = ctx.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(jobName)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onWriteCancelled()
                    return
                }
                val fd = destination?.fileDescriptor
                if (fd == null) {
                    callback?.onWriteFailed("Print destination unavailable")
                    return
                }
                try {
                    FileInputStream(file).use { input ->
                        FileOutputStream(fd).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.localizedMessage ?: "Print failed")
                }
            }
        }
        printManager.print(jobName, adapter, PrintAttributes.Builder().build())
    }

    private fun sanitizePdfText(raw: String): String {
        // PDType1Font Helvetica supports WinAnsiEncoding; replace unsupported glyphs safely
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            if (ch.code in 32..126 || ch.code in 160..255) {
                sb.append(ch)
            } else {
                sb.append(' ')
            }
        }
        return sb.toString()
    }
}
