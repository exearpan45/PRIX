package com.example.prix.core.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.prix.core.database.DocumentDao
import com.example.prix.core.database.DocumentEntity
import com.example.prix.core.database.FolderDao
import com.example.prix.core.database.FolderEntity
import com.example.prix.core.database.SavedSignatureEntity
import com.example.prix.core.database.SignatureDao
import com.example.prix.core.image.ImageProcessor
import com.example.prix.core.model.CompressionLevel
import com.example.prix.core.model.CompressionResult
import com.example.prix.core.model.DocumentFormat
import com.example.prix.core.model.ImageOutputFormat
import com.example.prix.core.model.NormalizedImageOverlay
import com.example.prix.core.model.NormalizedShape
import com.example.prix.core.model.NormalizedStroke
import com.example.prix.core.model.NormalizedTextBox
import com.example.prix.core.model.OcrResultData
import com.example.prix.core.model.PageBackground
import com.example.prix.core.model.PageNumberPosition
import com.example.prix.core.model.PageTransformItem
import com.example.prix.core.model.PdfInfoMetadata
import com.example.prix.core.model.PdfMargin
import com.example.prix.core.model.PdfOrientation
import com.example.prix.core.model.PdfPageSize
import com.example.prix.core.model.PdfQuality
import com.example.prix.core.model.PerformanceProfile
import com.example.prix.core.model.ScanFilter
import com.example.prix.core.model.ScannedPageDraft
import com.example.prix.core.model.StorageStats
import com.example.prix.core.model.WatermarkPosition
import com.example.prix.core.ocr.OcrEngine
import com.example.prix.core.pdf.PdfEngine
import com.example.prix.core.storage.DocumentStorageManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class PrixRepository(
    private val context: Context,
    private val documentDao: DocumentDao,
    private val folderDao: FolderDao,
    private val signatureDao: SignatureDao,
    val storage: DocumentStorageManager,
    val pdfEngine: PdfEngine,
    val ocrEngine: OcrEngine
) {
    val allDocuments: Flow<List<DocumentEntity>> = documentDao.observeAllDocuments()
    val validDocuments: Flow<List<DocumentEntity>> = allDocuments
        .map { docs -> docs.filter { File(it.filePath).exists() } }
        .flowOn(Dispatchers.IO)
    val pdfDocuments: Flow<List<DocumentEntity>> = documentDao.observePdfDocuments()
    val imageDocuments: Flow<List<DocumentEntity>> = documentDao.observeImageDocuments()
    val allFolders: Flow<List<FolderEntity>> = folderDao.observeAllFolders()
    val allSignatures: Flow<List<SavedSignatureEntity>> = signatureDao.observeAllSignatures()

    fun observeDocument(id: Long): Flow<DocumentEntity?> = documentDao.observeDocumentById(id)

    suspend fun getDocument(id: Long): DocumentEntity? = documentDao.getDocumentById(id)

    val storageStats: Flow<StorageStats> = combine(allDocuments, allFolders) { docs, folders ->
        val validDocs = docs.filter { File(it.filePath).exists() }
        val cutoff24h = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
        StorageStats(
            totalDocuments = validDocs.size,
            totalFolders = folders.size,
            vaultSizeBytes = storage.calculateDirectoryBytes(storage.vaultRoot),
            tempCacheBytes = storage.calculateDirectoryBytes(storage.tempDir),
            availableDeviceBytes = storage.getAvailableDeviceBytes(),
            recentCount24h = validDocs.count { it.updatedAt >= cutoff24h }
        )
    }.flowOn(Dispatchers.IO)

    suspend fun createFolder(name: String, parentFolderId: Long? = null, colorHex: Long = 0xFF7657F6): Result<Long> {
        val clean = name.trim()
        if (clean.isEmpty()) return Result.failure(IllegalArgumentException("Folder name cannot be empty."))
        val id = folderDao.insertFolder(
            FolderEntity(name = clean, parentFolderId = parentFolderId, colorHex = colorHex)
        )
        return Result.success(id)
    }

    suspend fun renameFolder(folderId: Long, newName: String): Result<Unit> {
        val clean = newName.trim()
        if (clean.isEmpty()) return Result.failure(IllegalArgumentException("Folder name cannot be empty."))
        val existing = folderDao.getFolderById(folderId)
            ?: return Result.failure(IllegalArgumentException("Folder not found."))
        folderDao.updateFolder(existing.copy(name = clean, updatedAt = System.currentTimeMillis()))
        return Result.success(Unit)
    }

    suspend fun deleteFolder(folderId: Long) {
        val target = folderDao.getFolderById(folderId)
        folderDao.reparentChildFolders(oldParentId = folderId, newParentId = target?.parentFolderId)
        documentDao.detachDocumentsFromFolder(folderId)
        folderDao.deleteFolderById(folderId)
    }

    suspend fun importPdfUri(uri: Uri, folderId: Long? = null): Result<DocumentEntity> = withContext(Dispatchers.IO) {
        try {
            storage.requireSufficientStorage(4L * 1024L * 1024L)
            val copied = storage.copyUriToVault(uri, "pdf")
                ?: return@withContext Result.failure(IllegalStateException("Unable to read the selected PDF file."))

            if (!storage.isLikelyValidPdf(copied)) {
                copied.delete()
                return@withContext Result.failure(IllegalArgumentException("The selected file does not have a valid PDF header."))
            }

            val infoResult = pdfEngine.inspectPdfInfo(copied)
            val isEncrypted = infoResult.exceptionOrNull()?.message?.contains("password", ignoreCase = true) == true
            val pages = infoResult.getOrNull()?.pageCount ?: pdfEngine.getPageCount(copied).coerceAtLeast(1)
            val thumb = if (!isEncrypted) storage.generateThumbnail(copied, DocumentFormat.PDF) else null

            val entity = DocumentEntity(
                name = copied.nameWithoutExtension,
                filePath = copied.absolutePath,
                thumbnailPath = thumb,
                mimeType = DocumentFormat.PDF.mimeType,
                formatName = DocumentFormat.PDF.name,
                sizeBytes = copied.length(),
                pageCount = pages.coerceAtLeast(1),
                folderId = folderId,
                isPasswordProtected = isEncrypted
            )
            val id = documentDao.insertDocument(entity)
            Result.success(entity.copy(id = id))
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importImageUriToTemp(
        uri: Uri,
        performanceProfile: PerformanceProfile = PerformanceProfile.BALANCED
    ): File? {
        val temp = storage.createTempImageFile("imp_")
        val maxDim = performanceProfile.imageDecodeMaxDim.coerceIn(1400, 2600)
        val ok = ImageProcessor.decodeUriToTempFile(context, uri, temp, maxDim)
        if (!ok) storage.deleteFileSafely(temp.absolutePath)
        return if (ok) temp else null
    }

    suspend fun importImageUriToVault(
        uri: Uri,
        folderId: Long? = null,
        performanceProfile: PerformanceProfile = PerformanceProfile.BALANCED
    ): Result<DocumentEntity> = withContext(Dispatchers.IO) {
        val copiedTemp = storage.createTempImageFile("raw_")
        try {
            storage.requireSufficientStorage(6L * 1024L * 1024L)
            storage.markPathActive(copiedTemp.absolutePath)
            val display = storage.resolveDisplayName(uri, "Image_${DocumentStorageManager.timestampTag()}")
            val ext = when {
                display.endsWith(".png", ignoreCase = true) -> "png"
                display.endsWith(".webp", ignoreCase = true) -> "webp"
                else -> "jpg"
            }
            val dest = storage.createVaultFile(display.substringBeforeLast('.'), ext)
            val maxDim = performanceProfile.imageDecodeMaxDim.coerceIn(1400, 2600)
            val decodedOk = ImageProcessor.decodeUriToTempFile(context, uri, copiedTemp, maxDim)
            if (!decodedOk) {
                return@withContext Result.failure(IllegalArgumentException("Unsupported or corrupted image file."))
            }
            val format = when (ext) {
                "png" -> ImageOutputFormat.PNG
                "webp" -> ImageOutputFormat.WEBP
                else -> ImageOutputFormat.JPEG
            }
            val bmp = ImageProcessor.decodeSampledBitmap(copiedTemp.absolutePath, maxDim)
                ?: return@withContext Result.failure(IllegalStateException("Failed to decode image."))
            val saved = try {
                ImageProcessor.saveBitmapToFile(bmp, dest, format, 92)
            } finally {
                if (!bmp.isRecycled) bmp.recycle()
            }
            if (!saved) {
                dest.delete()
                return@withContext Result.failure(IllegalStateException("Failed to save imported image."))
            }

            val docFormat = when (format) {
                ImageOutputFormat.PNG -> DocumentFormat.PNG
                ImageOutputFormat.WEBP -> DocumentFormat.WEBP
                ImageOutputFormat.JPEG -> DocumentFormat.JPEG
            }
            val thumb = storage.generateThumbnail(dest, docFormat)
            val entity = DocumentEntity(
                name = dest.nameWithoutExtension,
                filePath = dest.absolutePath,
                thumbnailPath = thumb,
                mimeType = docFormat.mimeType,
                formatName = docFormat.name,
                sizeBytes = dest.length(),
                pageCount = 1,
                folderId = folderId
            )
            val id = documentDao.insertDocument(entity)
            Result.success(entity.copy(id = id))
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            storage.unmarkPathActive(copiedTemp.absolutePath)
            storage.deleteFileSafely(copiedTemp.absolutePath)
        }
    }

    suspend fun registerCreatedFile(
        file: File,
        displayName: String,
        format: DocumentFormat,
        pageCount: Int = 1,
        folderId: Long? = null,
        isEncrypted: Boolean = false,
        ocrText: String = ""
    ): DocumentEntity {
        val thumb = if (!isEncrypted) storage.generateThumbnail(file, format) else null
        val entity = DocumentEntity(
            name = displayName,
            filePath = file.absolutePath,
            thumbnailPath = thumb,
            mimeType = format.mimeType,
            formatName = format.name,
            sizeBytes = file.length(),
            pageCount = pageCount,
            folderId = folderId,
            isPasswordProtected = isEncrypted,
            ocrText = ocrText
        )
        val id = documentDao.insertDocument(entity)
        return entity.copy(id = id)
    }

    suspend fun saveScannedDraftsAsPdf(
        drafts: List<ScannedPageDraft>,
        documentName: String,
        pageSize: PdfPageSize,
        orientation: PdfOrientation,
        quality: PdfQuality,
        folderId: Long? = null,
        performanceProfile: PerformanceProfile = PerformanceProfile.BALANCED,
        onProgress: (Float) -> Unit = {}
    ): Result<DocumentEntity> = withContext(Dispatchers.IO) {
        if (drafts.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Capture or import at least one page."))
        }
        val processedPaths = mutableListOf<String>()
        var outPdf: File? = null
        try {
            storage.requireSufficientStorage((drafts.size.toLong() + 2L) * 2L * 1024L * 1024L)
            val totalSteps = drafts.size * 2f
            val maxDecodeDim = minOf(quality.maxDimensionPx, performanceProfile.imageDecodeMaxDim)
            drafts.forEachIndexed { idx, draft ->
                currentCoroutineContext().ensureActive()
                var currentBmp = ImageProcessor.decodeSampledBitmap(draft.rawImagePath, maxDecodeDim)
                    ?: throw IllegalStateException("Could not read scanned page ${idx + 1}.")
                try {
                    val warped = ImageProcessor.applyPerspectiveWarp(currentBmp, draft.quad)
                    if (warped !== currentBmp && !currentBmp.isRecycled) currentBmp.recycle()
                    currentBmp = warped

                    val rotated = ImageProcessor.rotateBitmap(currentBmp, draft.rotationDegrees)
                    if (rotated !== currentBmp && !currentBmp.isRecycled) currentBmp.recycle()
                    currentBmp = rotated

                    val filtered = ImageProcessor.applyFilterAndAdjustments(
                        currentBmp,
                        draft.filter,
                        draft.brightness,
                        draft.contrast
                    )
                    if (filtered !== currentBmp && !currentBmp.isRecycled) currentBmp.recycle()
                    currentBmp = filtered

                    val procFile = storage.createTempImageFile("proc_${idx}_")
                    storage.markPathActive(procFile.absolutePath)
                    processedPaths.add(procFile.absolutePath)
                    val savedPage = ImageProcessor.saveBitmapToFile(
                        currentBmp,
                        procFile,
                        ImageOutputFormat.JPEG,
                        quality.jpegQuality
                    )
                    if (!savedPage) {
                        throw IllegalStateException("Could not encode scanned page ${idx + 1}.")
                    }
                } finally {
                    if (!currentBmp.isRecycled) currentBmp.recycle()
                }
                onProgress((idx + 1) / totalSteps)
            }

            val createdOutPdf = storage.createVaultFile(documentName, "pdf")
            outPdf = createdOutPdf
            val pdfResult = pdfEngine.createPdfFromImages(
                imagePaths = processedPaths,
                outputFile = createdOutPdf,
                pageSize = pageSize,
                orientation = orientation,
                margin = PdfMargin.NARROW,
                quality = quality,
                background = PageBackground.WHITE,
                fitToPage = false,
                onProgress = { p -> onProgress(0.5f + p * 0.5f) }
            )
            if (pdfResult.isFailure) {
                createdOutPdf.delete()
                return@withContext Result.failure(pdfResult.exceptionOrNull()!!)
            }
            val pageCount = pdfResult.getOrDefault(drafts.size)
            val saved = registerCreatedFile(
                file = createdOutPdf,
                displayName = createdOutPdf.nameWithoutExtension,
                format = DocumentFormat.PDF,
                pageCount = pageCount,
                folderId = folderId
            )
            Result.success(saved)
        } catch (ce: CancellationException) {
            outPdf?.delete()
            throw ce
        } catch (e: Exception) {
            outPdf?.delete()
            Result.failure(e)
        } finally {
            processedPaths.forEach { path ->
                storage.unmarkPathActive(path)
                storage.deleteFileSafely(path)
            }
        }
    }

    suspend fun createPdfFromImagePaths(
        imagePaths: List<String>,
        documentName: String,
        pageSize: PdfPageSize,
        orientation: PdfOrientation,
        margin: PdfMargin,
        quality: PdfQuality,
        background: PageBackground,
        fitToPage: Boolean,
        folderId: Long? = null,
        onProgress: (Float) -> Unit = {}
    ): Result<DocumentEntity> {
        return try {
            storage.requireSufficientStorage(8L * 1024L * 1024L)
            val outFile = storage.createVaultFile(documentName, "pdf")
            val res = pdfEngine.createPdfFromImages(
                imagePaths = imagePaths,
                outputFile = outFile,
                pageSize = pageSize,
                orientation = orientation,
                margin = margin,
                quality = quality,
                background = background,
                fitToPage = fitToPage,
                onProgress = onProgress
            )
            res.map { pages ->
                registerCreatedFile(outFile, outFile.nameWithoutExtension, DocumentFormat.PDF, pages, folderId)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameDocument(docId: Long, newName: String): Result<Unit> {
        val clean = storage.sanitizeFileName(newName).ifBlank {
            return Result.failure(IllegalArgumentException("Document name cannot be empty."))
        }
        val doc = documentDao.getDocumentById(docId)
            ?: return Result.failure(IllegalArgumentException("Document not found."))
        documentDao.updateDocument(doc.copy(name = clean, updatedAt = System.currentTimeMillis()))
        return Result.success(Unit)
    }

    suspend fun duplicateDocument(docId: Long): Result<DocumentEntity> = withContext(Dispatchers.IO) {
        try {
            val doc = documentDao.getDocumentById(docId)
                ?: return@withContext Result.failure(IllegalArgumentException("Document not found."))
            val src = File(doc.filePath)
            if (!src.exists()) return@withContext Result.failure(IllegalStateException("Original file no longer exists on disk."))
            storage.requireSufficientStorage((src.length() * 2L).coerceAtLeast(2L * 1024L * 1024L))
            val copyFile = storage.createVaultFile("${doc.name}_copy", doc.format.extension)
            src.copyTo(copyFile, overwrite = true)
            val created = registerCreatedFile(
                file = copyFile,
                displayName = copyFile.nameWithoutExtension,
                format = doc.format,
                pageCount = doc.pageCount,
                folderId = doc.folderId,
                isEncrypted = doc.isPasswordProtected,
                ocrText = doc.ocrText
            )
            Result.success(created)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteDocuments(ids: List<Long>): Result<Int> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext Result.success(0)
        val deletedIds = mutableListOf<Long>()
        var fileDeleteFailures = 0
        ids.forEach { id ->
            val doc = documentDao.getDocumentById(id)
            if (doc == null) {
                deletedIds.add(id)
            } else {
                val mainRemoved = storage.deleteFileSafely(doc.filePath)
                if (mainRemoved) {
                    storage.deleteFileSafely(doc.thumbnailPath)
                    deletedIds.add(id)
                } else {
                    fileDeleteFailures++
                }
            }
        }
        if (deletedIds.isNotEmpty()) {
            documentDao.deleteDocumentsByIds(deletedIds)
        }
        if (fileDeleteFailures > 0) {
            Result.failure(IllegalStateException("Could not remove $fileDeleteFailures file(s) from storage; database entries were retained for safety."))
        } else {
            Result.success(deletedIds.size)
        }
    }

    suspend fun toggleStar(docId: Long, starred: Boolean) {
        documentDao.setStarred(docId, starred)
    }

    suspend fun markOpened(docId: Long) {
        documentDao.markOpened(docId)
    }

    suspend fun moveDocumentsToFolder(ids: List<Long>, folderId: Long?) {
        documentDao.moveDocumentsToFolder(ids, folderId)
    }

    suspend fun saveOcrAsTextDocument(
        title: String,
        text: String,
        sourceDocId: Long? = null,
        folderId: Long? = null
    ): Result<DocumentEntity> = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext Result.failure(IllegalArgumentException("No text to save."))
        val outFile = storage.createVaultFile(title, "txt")
        outFile.writeText(text)
        sourceDocId?.let { documentDao.updateOcrText(it, text) }
        val saved = registerCreatedFile(
            file = outFile,
            displayName = outFile.nameWithoutExtension,
            format = DocumentFormat.TXT,
            pageCount = 1,
            folderId = folderId,
            ocrText = text
        )
        Result.success(saved)
    }

    suspend fun saveSignatureBitmap(name: String, bitmap: Bitmap): Result<SavedSignatureEntity> =
        withContext(Dispatchers.IO) {
            val file = storage.createSignatureFile(name)
            val saved = ImageProcessor.saveBitmapToFile(bitmap, file, ImageOutputFormat.PNG, 100)
            if (!saved) return@withContext Result.failure(IllegalStateException("Could not save signature."))
            val entity = SavedSignatureEntity(name = name.ifBlank { "Signature" }, imagePath = file.absolutePath)
            val id = signatureDao.insertSignature(entity)
            Result.success(entity.copy(id = id))
        }

    suspend fun deleteSignature(sig: SavedSignatureEntity) = withContext(Dispatchers.IO) {
        storage.deleteFileQuietly(sig.imagePath)
        signatureDao.deleteSignatureById(sig.id)
    }

    suspend fun mergePdfs(docIds: List<Long>, outputName: String): Result<DocumentEntity> {
        return try {
            storage.requireSufficientStorage(8L * 1024L * 1024L)
            val files = docIds.mapNotNull { id ->
                documentDao.getDocumentById(id)?.filePath?.let { File(it) }?.takeIf { it.exists() }
            }
            val out = storage.createVaultFile(outputName.ifBlank { "Merged_${DocumentStorageManager.timestampTag()}" }, "pdf")
            pdfEngine.mergePdfs(files, out).map { pages ->
                registerCreatedFile(out, out.nameWithoutExtension, DocumentFormat.PDF, pages)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun splitPdf(docId: Long, ranges: List<IntRange>, splitEveryPage: Boolean): Result<List<DocumentEntity>> {
        return try {
            storage.requireSufficientStorage(8L * 1024L * 1024L)
            val doc = documentDao.getDocumentById(docId)
                ?: return Result.failure(IllegalArgumentException("Document not found."))
            val src = File(doc.filePath)
            val res = if (splitEveryPage) {
                pdfEngine.splitEveryPage(src) { p ->
                    storage.createVaultFile("${doc.name}_page_$p", "pdf")
                }
            } else {
                pdfEngine.splitPdfByRanges(src, ranges) { idx ->
                    storage.createVaultFile("${doc.name}_part_${idx + 1}", "pdf")
                }
            }
            res.map { files ->
                files.map { f ->
                    val count = pdfEngine.getPageCount(f).coerceAtLeast(1)
                    registerCreatedFile(f, f.nameWithoutExtension, DocumentFormat.PDF, count, doc.folderId)
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun compressPdf(
        docId: Long,
        level: CompressionLevel,
        overwriteOriginal: Boolean = false,
        onProgress: (Float) -> Unit = {}
    ): Result<CompressionResult> = withContext(Dispatchers.IO) {
        val doc = documentDao.getDocumentById(docId)
            ?: return@withContext Result.failure(IllegalArgumentException("Document not found."))
        val src = File(doc.filePath)
        if (!src.exists()) return@withContext Result.failure(IllegalStateException("Original file no longer exists on disk."))
        try {
            storage.requireSufficientStorage(maxOf(6L * 1024L * 1024L, src.length() * 2L))
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }

        val out = if (overwriteOriginal) {
            File(storage.tempDir, "${storage.sanitizeFileName(doc.name).ifBlank { "doc" }}_comp_tmp_${System.currentTimeMillis()}.pdf")
        } else {
            storage.createVaultFile("${doc.name}_compressed", "pdf")
        }
        storage.markPathActive(out.absolutePath)
        try {
            val res = pdfEngine.compressPdf(src, level, out, onProgress)
            res.map { comp ->
                if (!comp.reduced) {
                    storage.deleteFileSafely(out.absolutePath)
                    comp
                } else if (overwriteOriginal) {
                    replaceFileAtomically(stagingFile = out, targetFile = src)
                    val thumb = storage.generateThumbnail(src, DocumentFormat.PDF)
                    storage.deleteFileSafely(doc.thumbnailPath)
                    documentDao.updateDocument(
                        doc.copy(
                            sizeBytes = src.length(),
                            thumbnailPath = thumb,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    comp.copy(savedDocumentId = doc.id)
                } else {
                    val created = registerCreatedFile(
                        out,
                        out.nameWithoutExtension,
                        DocumentFormat.PDF,
                        doc.pageCount,
                        doc.folderId
                    )
                    comp.copy(savedDocumentId = created.id)
                }
            }
        } catch (ce: CancellationException) {
            storage.deleteFileSafely(out.absolutePath)
            throw ce
        } catch (e: Exception) {
            storage.deleteFileSafely(out.absolutePath)
            Result.failure(e)
        } finally {
            storage.unmarkPathActive(out.absolutePath)
        }
    }

    suspend fun applyPdfEdits(
        docId: Long,
        outputName: String,
        overwriteOriginal: Boolean,
        pagePlan: List<PageTransformItem>,
        strokes: List<NormalizedStroke>,
        textBoxes: List<NormalizedTextBox>,
        shapes: List<NormalizedShape>,
        imageOverlays: List<NormalizedImageOverlay>
    ): Result<DocumentEntity> = withContext(Dispatchers.IO) {
        val doc = documentDao.getDocumentById(docId)
            ?: return@withContext Result.failure(IllegalArgumentException("Document not found."))
        val src = File(doc.filePath)
        if (!src.exists()) return@withContext Result.failure(IllegalStateException("Original file no longer exists on disk."))
        try {
            storage.requireSufficientStorage(maxOf(6L * 1024L * 1024L, src.length() * 2L))
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }

        val targetOut = if (overwriteOriginal) {
            File(storage.tempDir, "${storage.sanitizeFileName(doc.name).ifBlank { "doc" }}_edit_tmp_${System.currentTimeMillis()}.pdf")
        } else {
            storage.createVaultFile(outputName.ifBlank { "${doc.name}_edited" }, "pdf")
        }
        storage.markPathActive(targetOut.absolutePath)

        try {
            val res = pdfEngine.applyEdits(
                inputFile = src,
                pagePlan = pagePlan,
                strokes = strokes,
                textBoxes = textBoxes,
                shapes = shapes,
                imageOverlays = imageOverlays,
                outputFile = targetOut
            )
            res.map { pages ->
                if (overwriteOriginal) {
                    replaceFileAtomically(stagingFile = targetOut, targetFile = src)
                    val newThumb = storage.generateThumbnail(src, DocumentFormat.PDF)
                    storage.deleteFileSafely(doc.thumbnailPath)
                    val updated = doc.copy(
                        pageCount = pages,
                        sizeBytes = src.length(),
                        thumbnailPath = newThumb,
                        updatedAt = System.currentTimeMillis()
                    )
                    documentDao.updateDocument(updated)
                    updated
                } else {
                    registerCreatedFile(
                        file = targetOut,
                        displayName = targetOut.nameWithoutExtension,
                        format = DocumentFormat.PDF,
                        pageCount = pages,
                        folderId = doc.folderId
                    )
                }
            }
        } catch (ce: CancellationException) {
            storage.deleteFileSafely(targetOut.absolutePath)
            throw ce
        } catch (e: Exception) {
            storage.deleteFileSafely(targetOut.absolutePath)
            Result.failure(e)
        } finally {
            storage.unmarkPathActive(targetOut.absolutePath)
        }
    }

    suspend fun saveEditedImage(
        sourceImagePath: String,
        outputName: String,
        format: ImageOutputFormat,
        quality: Int,
        filter: ScanFilter,
        brightness: Float,
        contrast: Float,
        rotationDegrees: Int,
        targetWidth: Int? = null,
        targetHeight: Int? = null,
        cropBounds: FloatArray? = null, // [left, top, right, bottom] 0..1
        overwriteDocId: Long? = null,
        performanceProfile: PerformanceProfile = PerformanceProfile.BALANCED
    ): Result<DocumentEntity> = withContext(Dispatchers.IO) {
        storage.requireSufficientStorage(6L * 1024L * 1024L)
        val raw = ImageProcessor.decodeSampledBitmap(sourceImagePath, performanceProfile.imageDecodeMaxDim)
            ?: return@withContext Result.failure(IllegalStateException("Could not decode image."))
        var working = raw
        val stagingFile = storage.createTempImageFile("img_stage_")
        storage.markPathActive(stagingFile.absolutePath)
        try {
            currentCoroutineContext().ensureActive()
            if (cropBounds != null && cropBounds.size == 4) {
                val cropped = ImageProcessor.cropBitmapNormalized(
                    working, cropBounds[0], cropBounds[1], cropBounds[2], cropBounds[3]
                )
                if (cropped !== working) working.recycle()
                working = cropped
            }
            if (rotationDegrees % 360 != 0) {
                val rot = ImageProcessor.rotateBitmap(working, rotationDegrees)
                if (rot !== working) working.recycle()
                working = rot
            }
            if (targetWidth != null && targetHeight != null && targetWidth > 0 && targetHeight > 0) {
                val resized = ImageProcessor.resizeBitmap(working, targetWidth, targetHeight)
                if (resized !== working) working.recycle()
                working = resized
            }
            val filtered = ImageProcessor.applyFilterAndAdjustments(working, filter, brightness, contrast)
            if (filtered !== working) working.recycle()
            working = filtered

            val docFormat = when (format) {
                ImageOutputFormat.JPEG -> DocumentFormat.JPEG
                ImageOutputFormat.PNG -> DocumentFormat.PNG
                ImageOutputFormat.WEBP -> DocumentFormat.WEBP
            }

            val savedStage = ImageProcessor.saveBitmapToFile(working, stagingFile, format, quality)
            if (!savedStage) return@withContext Result.failure(IllegalStateException("Failed to write image file."))

            val existing = overwriteDocId?.let { documentDao.getDocumentById(it) }
            val dest = if (existing != null) File(existing.filePath) else storage.createVaultFile(outputName, format.extension)
            if (existing != null) {
                replaceFileAtomically(stagingFile = stagingFile, targetFile = dest)
                val newThumb = storage.generateThumbnail(dest, docFormat)
                storage.deleteFileSafely(existing.thumbnailPath)
                val updated = existing.copy(
                    sizeBytes = dest.length(),
                    thumbnailPath = newThumb,
                    updatedAt = System.currentTimeMillis()
                )
                documentDao.updateDocument(updated)
                Result.success(updated)
            } else {
                stagingFile.copyTo(dest, overwrite = true)
                Result.success(registerCreatedFile(dest, dest.nameWithoutExtension, docFormat, 1))
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            storage.unmarkPathActive(stagingFile.absolutePath)
            storage.deleteFileSafely(stagingFile.absolutePath)
            if (!working.isRecycled) working.recycle()
        }
    }

    private fun replaceFileAtomically(stagingFile: File, targetFile: File) {
        if (!stagingFile.exists() || stagingFile.length() == 0L) {
            throw IllegalStateException("Staged output file is empty.")
        }
        val backupFile = File(targetFile.parentFile, "${targetFile.name}.bak_${System.currentTimeMillis()}")
        var movedOriginalToBackup = false
        try {
            if (targetFile.exists()) {
                movedOriginalToBackup = targetFile.renameTo(backupFile)
                if (!movedOriginalToBackup) {
                    targetFile.copyTo(backupFile, overwrite = true)
                    movedOriginalToBackup = true
                }
            }
            stagingFile.copyTo(targetFile, overwrite = true)
            if (!targetFile.exists() || targetFile.length() == 0L) {
                throw IllegalStateException("Failed to finalize target document.")
            }
            if (movedOriginalToBackup) {
                backupFile.delete()
            }
            stagingFile.delete()
        } catch (e: Exception) {
            if (movedOriginalToBackup && backupFile.exists()) {
                try {
                    backupFile.copyTo(targetFile, overwrite = true)
                } catch (_: Exception) {}
                backupFile.delete()
            }
            throw e
        }
    }

    suspend fun inspectPdf(docId: Long, password: String? = null): Result<PdfInfoMetadata> {
        val doc = documentDao.getDocumentById(docId)
            ?: return Result.failure(IllegalArgumentException("Document not found."))
        return pdfEngine.inspectPdfInfo(File(doc.filePath), password)
    }

    suspend fun runOcrOnDocument(
        doc: DocumentEntity,
        performanceProfile: PerformanceProfile = PerformanceProfile.BALANCED
    ): Result<OcrResultData> {
        val file = File(doc.filePath)
        if (!file.exists()) return Result.failure(IllegalStateException("File missing from storage."))
        val res = if (doc.format == DocumentFormat.PDF) {
            ocrEngine.extractFromPdfPages(file, maxWidthPx = performanceProfile.ocrMaxDim)
        } else {
            ocrEngine.extractFromImagePath(file.absolutePath, maxDimension = performanceProfile.ocrMaxDim)
        }
        res.getOrNull()?.let { data ->
            if (data.fullText.isNotBlank()) {
                documentDao.updateOcrText(doc.id, data.fullText)
            }
        }
        return res
    }
}
