package com.example

import android.content.Context
import android.content.res.XmlResourceParser
import android.database.sqlite.SQLiteException
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.prix.core.data.PrixRepository
import com.example.prix.core.database.PrixDatabase
import com.example.prix.core.datastore.PreferencesRepository
import com.example.prix.core.error.CorruptedDocumentException
import com.example.prix.core.error.InsufficientStorageException
import com.example.prix.core.error.PrixErrorMapper
import com.example.prix.core.error.PrixOperation
import com.example.prix.core.error.ProtectedPdfException
import com.example.prix.core.model.DocumentFormat
import com.example.prix.core.model.NormalizedImageOverlay
import com.example.prix.core.model.NormalizedPoint
import com.example.prix.core.model.NormalizedShape
import com.example.prix.core.model.NormalizedStroke
import com.example.prix.core.model.NormalizedTextBox
import com.example.prix.core.model.PageBackground
import com.example.prix.core.model.PageNumberPosition
import com.example.prix.core.model.PageTransformItem
import com.example.prix.core.model.PdfMargin
import com.example.prix.core.model.PdfOrientation
import com.example.prix.core.model.PdfPageSize
import com.example.prix.core.model.PdfQuality
import com.example.prix.core.model.PerformanceMode
import com.example.prix.core.model.PerformanceProfile
import com.example.prix.core.model.ShapeKind
import com.example.prix.core.model.ThemeMode
import com.example.prix.core.model.WatermarkPosition
import com.example.prix.core.ocr.OcrEngine
import com.example.prix.core.pdf.PdfEngine
import com.example.prix.core.storage.DocumentStorageManager
import com.example.prix.core.storage.TempCleanupWorker
import com.example.prix.feature.tools.parsePageRanges
import com.example.ui.theme.resolveEffectivePerformanceProfile
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrixCoreStabilizationTest {

    private lateinit var context: Context
    private lateinit var db: PrixDatabase
    private lateinit var storage: DocumentStorageManager
    private lateinit var pdfEngine: PdfEngine
    private lateinit var repository: PrixRepository
    private lateinit var workDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DocumentStorageManager.clearActiveRegistryForTest()
        db = Room.inMemoryDatabaseBuilder(context, PrixDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        storage = DocumentStorageManager(context)
        pdfEngine = PdfEngine(context)
        repository = PrixRepository(
            context = context,
            documentDao = db.documentDao(),
            folderDao = db.folderDao(),
            signatureDao = db.signatureDao(),
            storage = storage,
            pdfEngine = pdfEngine,
            ocrEngine = OcrEngine()
        )
        workDir = File(context.cacheDir, "prix_core_test_${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        DocumentStorageManager.clearActiveRegistryForTest()
        db.close()
        workDir.deleteRecursively()
        storage.vaultRoot.deleteRecursively()
        storage.tempDir.deleteRecursively()
    }

    @Test
    fun errorMapperCoversAllOperationalFailuresWithoutLeakingTraces() {
        val cancelErr = PrixErrorMapper.map(PrixOperation.MERGE_PDF, CancellationException("Job cancelled"))
        assertEquals("Operation Cancelled", cancelErr.title)

        val oomErr = PrixErrorMapper.map(PrixOperation.CREATE_PDF, OutOfMemoryError("Heap exhausted"))
        assertEquals("Memory Limit Reached", oomErr.title)
        assertTrue(oomErr.nextStep.contains("Performance Mode", ignoreCase = true))

        val storageErr = PrixErrorMapper.map(PrixOperation.CREATE_PDF, InsufficientStorageException())
        assertEquals("Insufficient Storage Space", storageErr.title)

        val enospcErr = PrixErrorMapper.map(PrixOperation.COMPRESS_PDF, IOException("write failed: ENOSPC (No space left on device)"))
        assertEquals("Insufficient Storage Space", enospcErr.title)

        val permErr = PrixErrorMapper.map(PrixOperation.SCAN_CAPTURE, SecurityException("Camera denied"))
        assertEquals("Access Permission Needed", permErr.title)

        val badPwErr = PrixErrorMapper.map(PrixOperation.DECRYPT_PDF, IllegalArgumentException("Incorrect password"))
        assertEquals("Incorrect PDF Password", badPwErr.title)

        val lockedErr = PrixErrorMapper.map(PrixOperation.EDIT_PDF, ProtectedPdfException())
        assertEquals("Password-Protected PDF", lockedErr.title)

        val missingErr = PrixErrorMapper.map(PrixOperation.OPEN_PDF, FileNotFoundException("/data/user/0/secret.pdf"))
        assertEquals("Source File Missing", missingErr.title)
        assertFalse(missingErr.toDisplayMessage().contains("/data/user/0/secret.pdf"))

        val dbErr = PrixErrorMapper.map(PrixOperation.DATABASE_OPERATION, SQLiteException("disk I/O"))
        assertEquals("Local Library Error", dbErr.title)

        val corruptErr = PrixErrorMapper.map(PrixOperation.REPAIR_PDF, CorruptedDocumentException("bad xref"))
        assertEquals("Invalid or Corrupted PDF", corruptErr.title)

        val imageErr = PrixErrorMapper.map(PrixOperation.IMPORT_IMAGE, IllegalStateException("decode failed"))
        assertEquals("Unsupported or Unreadable Image", imageErr.title)

        val camErr = PrixErrorMapper.map(PrixOperation.SCAN_CAPTURE, IllegalStateException("camera busy"))
        assertEquals("Camera Unavailable", camErr.title)

        val argErr = PrixErrorMapper.map(PrixOperation.SPLIT_PDF, IllegalArgumentException("Enter valid page numbers like 1-3, 5."))
        assertEquals("Check Input", argErr.title)
        assertEquals("Enter valid page numbers like 1-3, 5.", argErr.whatHappened)

        val genericErr = PrixErrorMapper.map(PrixOperation.WATERMARK_PDF, IllegalStateException("raw internal stack detail"))
        assertEquals("Add watermark Failed", genericErr.title)
        assertFalse(genericErr.toDisplayMessage().contains("raw internal stack detail"))
    }

    @Test
    fun performanceProfileResolutionRespectsAutoRamAndManualOverrides() {
        assertEquals(
            PerformanceProfile.LOW,
            resolveEffectivePerformanceProfile(PerformanceMode.AUTO, totalMemMb = 2048, memoryClassMb = 128, isLowRamDevice = false)
        )
        assertEquals(
            PerformanceProfile.LOW,
            resolveEffectivePerformanceProfile(PerformanceMode.AUTO, totalMemMb = 6144, memoryClassMb = 256, isLowRamDevice = true)
        )
        assertEquals(
            PerformanceProfile.BALANCED,
            resolveEffectivePerformanceProfile(PerformanceMode.AUTO, totalMemMb = 4096, memoryClassMb = 256, isLowRamDevice = false)
        )
        assertEquals(
            PerformanceProfile.HIGH,
            resolveEffectivePerformanceProfile(PerformanceMode.AUTO, totalMemMb = 8192, memoryClassMb = 512, isLowRamDevice = false)
        )
        assertEquals(
            PerformanceProfile.LOW,
            resolveEffectivePerformanceProfile(PerformanceMode.LOW, totalMemMb = 12000, memoryClassMb = 512, isLowRamDevice = false)
        )
        assertEquals(
            PerformanceProfile.HIGH,
            resolveEffectivePerformanceProfile(PerformanceMode.HIGH, totalMemMb = 2048, memoryClassMb = 128, isLowRamDevice = true)
        )
    }

    @Test
    fun storageManagerOwnershipActiveRegistryAndTempCleanupProtectVaultAndActiveFiles() {
        val vaultDoc = storage.createVaultFile("vault_doc", "pdf").apply {
            writeText("%PDF-1.4\n%%EOF")
        }
        val oldTemp = storage.createTempImageFile("expired_").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            setLastModified(System.currentTimeMillis() - 48L * 60L * 60L * 1000L)
        }
        val activeOldTemp = storage.createTempImageFile("active_").apply {
            writeBytes(byteArrayOf(4, 5, 6))
            setLastModified(System.currentTimeMillis() - 48L * 60L * 60L * 1000L)
        }
        val freshTemp = storage.createTempImageFile("fresh_").apply {
            writeBytes(byteArrayOf(7, 8, 9))
        }
        val oldExif = File(context.cacheDir, "exif_old_test.img").apply {
            writeBytes(byteArrayOf(9, 8, 7))
            setLastModified(System.currentTimeMillis() - 48L * 60L * 60L * 1000L)
        }
        val outsideFile = File(context.filesDir, "outside_file.txt").apply {
            writeText("do not touch")
        }

        storage.markPathActive(activeOldTemp.absolutePath)
        assertTrue(DocumentStorageManager.isPathActive(activeOldTemp))
        assertTrue(storage.isOwnedPath(vaultDoc))
        assertTrue(storage.isOwnedPath(oldTemp))
        assertTrue(storage.isOwnedPath(oldExif))
        assertFalse(storage.isOwnedPath(outsideFile))

        // Safe delete refuses files outside vault/temp
        assertFalse(storage.deleteFileSafely(outsideFile.absolutePath))
        assertTrue(outsideFile.exists())
        outsideFile.delete()

        val stats = TempCleanupWorker.performCleanup(context)
        assertTrue(stats.deletedCount >= 2)
        assertFalse(oldTemp.exists())
        assertFalse(oldExif.exists())
        assertTrue(activeOldTemp.exists())
        assertTrue(freshTemp.exists())
        assertTrue(vaultDoc.exists())

        storage.unmarkPathActive(activeOldTemp.absolutePath)
        assertFalse(DocumentStorageManager.isPathActive(activeOldTemp))
        assertTrue(storage.deleteFileSafely(activeOldTemp.absolutePath))
        assertFalse(activeOldTemp.exists())

        assertTrue(storage.isLikelyValidPdf(vaultDoc))
        val badPdf = storage.createVaultFile("not_pdf", "pdf").apply { writeText("hello") }
        assertFalse(storage.isLikelyValidPdf(badPdf))

        val avail = storage.getAvailableDeviceBytes()
        if (avail > 0L) {
            var threw = false
            try {
                storage.requireSufficientStorage(avail + 1024L * 1024L)
            } catch (_: InsufficientStorageException) {
                threw = true
            }
            assertTrue(threw)
        }
    }

    @Test
    fun backupRulesStrictlyExcludeVaultAndDatabaseFiles() {
        val backupXml = readXmlAttributes(context.resources.getXml(R.xml.backup_rules))
        assertTrue(backupXml.any { it.contains("file:prix_vault") })
        assertTrue(backupXml.any { it.contains("database:prix_documents.db") })

        val extractionXml = readXmlAttributes(context.resources.getXml(R.xml.data_extraction_rules))
        assertTrue(extractionXml.count { it.contains("file:prix_vault") } >= 2)
        assertTrue(extractionXml.count { it.contains("database:prix_documents.db") } >= 2)
    }

    @Test
    fun parsePageRangesIsStrictAcrossValidAndInvalidInputs() {
        assertEquals(listOf(1..1, 3..5), parsePageRanges("1, 3-5", maxPages = 6))
        assertEquals(listOf(2..2), parsePageRanges(" 2 ", maxPages = 4))
        assertTrue(parsePageRanges("", maxPages = 5).isEmpty())
        assertTrue(parsePageRanges("   ", maxPages = 5).isEmpty())
        assertTrue(parsePageRanges("5-2", maxPages = 6).isEmpty())
        assertTrue(parsePageRanges("99", maxPages = 6).isEmpty())
        assertTrue(parsePageRanges("1-2, 5-2", maxPages = 6).isEmpty())
        assertTrue(parsePageRanges("1-2-3", maxPages = 6).isEmpty())
        assertTrue(parsePageRanges("0-2", maxPages = 6).isEmpty())
        assertTrue(parsePageRanges("1-2", maxPages = 0).isEmpty())
    }

    @Test
    fun pdfEngineRotationCoordinateMappingAndMultiOrientationEditsWorkCorrectly() = runBlocking {
        assertEquals(0, PdfEngine.normalizeQuarterTurn(0))
        assertEquals(90, PdfEngine.normalizeQuarterTurn(90))
        assertEquals(270, PdfEngine.normalizeQuarterTurn(-90))
        assertEquals(180, PdfEngine.normalizeQuarterTurn(540))

        val box = PDRectangle(0f, 0f, 600f, 800f)
        val p0 = PdfEngine.mapNormalizedToPdfPoint(0.25f, 0.25f, box, 0)
        assertEquals(150f, p0.first, 0.5f)
        assertEquals(600f, p0.second, 0.5f)

        val p90 = PdfEngine.mapNormalizedToPdfPoint(0.25f, 0.25f, box, 90)
        assertEquals(150f, p90.first, 0.5f)
        assertEquals(200f, p90.second, 0.5f)

        val p180 = PdfEngine.mapNormalizedToPdfPoint(0.25f, 0.25f, box, 180)
        assertEquals(450f, p180.first, 0.5f)
        assertEquals(200f, p180.second, 0.5f)

        val p270 = PdfEngine.mapNormalizedToPdfPoint(0.25f, 0.25f, box, 270)
        assertEquals(450f, p270.first, 0.5f)
        assertEquals(600f, p270.second, 0.5f)

        // Create sample images & overlay signature bitmap
        val img = createTestImageFile("page_img.jpg", 420, 600, Color.WHITE)
        val sigImg = createTestImageFile("sig_img.png", 160, 80, Color.BLUE, Bitmap.CompressFormat.PNG)

        // Test various page sizes, orientations, and fitToPage options
        for ((size, orient, fit) in listOf(
            Triple(PdfPageSize.A4, PdfOrientation.PORTRAIT, false),
            Triple(PdfPageSize.LETTER, PdfOrientation.LANDSCAPE, true),
            Triple(PdfPageSize.ORIGINAL, PdfOrientation.PORTRAIT, false)
        )) {
            val out = File(workDir, "create_${size.name}_${orient.name}.pdf")
            val r = pdfEngine.createPdfFromImages(
                imagePaths = listOf(img.absolutePath, img.absolutePath, img.absolutePath, img.absolutePath),
                outputFile = out,
                pageSize = size,
                orientation = orient,
                margin = PdfMargin.NONE,
                quality = PdfQuality.BALANCED,
                background = PageBackground.WARM_CREAM,
                fitToPage = fit
            )
            assertTrue(r.isSuccess)
            assertEquals(4, r.getOrNull())
        }

        val sourcePdf = File(workDir, "create_${PdfPageSize.A4.name}_${PdfOrientation.PORTRAIT.name}.pdf")
        val editedPdf = File(workDir, "edited_rotations.pdf")
        val rotations = listOf(0, 90, 180, 270)
        val editRes = pdfEngine.applyEdits(
            inputFile = sourcePdf,
            pagePlan = rotations.mapIndexed { idx, deg -> PageTransformItem(idx, deg) },
            strokes = rotations.indices.map { idx ->
                NormalizedStroke("s$idx", idx, listOf(NormalizedPoint(0.1f, 0.1f), NormalizedPoint(0.3f, 0.3f)), Color.RED, 0.01f, 0.8f)
            },
            textBoxes = rotations.indices.map { idx ->
                NormalizedTextBox("t$idx", idx, "RotTag$idx", 0.15f, 0.25f, 14f, Color.BLACK)
            },
            shapes = rotations.indices.map { idx ->
                NormalizedShape("sh$idx", idx, ShapeKind.RECTANGLE, 0.2f, 0.2f, 0.5f, 0.5f, Color.GREEN, 0.01f)
            },
            imageOverlays = listOf(
                NormalizedImageOverlay("ov0", 1, sigImg.absolutePath, 0.3f, 0.6f, 0.2f, 0.1f)
            ),
            outputFile = editedPdf
        )
        assertTrue(editRes.isSuccess)
        assertEquals(4, editRes.getOrNull())

        val extracted = pdfEngine.extractTextPerPage(editedPdf).getOrThrow()
        assertEquals(4, extracted.size)
        rotations.indices.forEach { idx ->
            assertTrue("Missing RotTag$idx on page $idx", extracted[idx].contains("RotTag$idx"))
        }

        // Rotate, rebuild page plan, split every page, wrong password decrypt, and repair failure
        val reordered = File(workDir, "reordered.pdf")
        assertEquals(2, pdfEngine.rebuildPdfWithPagePlan(editedPdf, listOf(PageTransformItem(3, 0), PageTransformItem(0, 90)), reordered).getOrThrow())

        val rotated = File(workDir, "rotated.pdf")
        assertEquals(2, pdfEngine.rotatePages(reordered, mapOf(0 to 90), rotated).getOrThrow())

        val splitPages = pdfEngine.splitEveryPage(rotated) { p -> File(workDir, "every_$p.pdf") }.getOrThrow()
        assertEquals(2, splitPages.size)

        val enc = File(workDir, "pw.pdf")
        assertTrue(pdfEngine.encryptPdf(rotated, "pass1234", "owner1234", enc).isSuccess)
        val wrongUnlock = pdfEngine.decryptPdf(enc, "wrong_pw", File(workDir, "fail_dec.pdf"))
        assertTrue(wrongUnlock.isFailure)

        val corrupt = File(workDir, "corrupt.pdf").apply { writeText("NOT_A_PDF_AT_ALL") }
        val repairRes = pdfEngine.repairPdf(corrupt, File(workDir, "repaired.pdf"))
        assertTrue(repairRes.isFailure)
        assertTrue(repairRes.exceptionOrNull() is CorruptedDocumentException)
    }

    @Test
    fun repositoryHandlesFolderReparentingDocumentLifecycleAndSignaturesSafely() = runBlocking {
        val parentId = repository.createFolder("Work").getOrThrow()
        val childId = repository.createFolder("2026 Taxes", parentFolderId = parentId).getOrThrow()
        assertTrue(repository.renameFolder(childId, "2026 Returns").isSuccess)

        val img = createTestImageFile("scan_src.jpg", 320, 480, Color.WHITE)
        val created = repository.createPdfFromImagePaths(
            imagePaths = listOf(img.absolutePath),
            documentName = "TaxPacket",
            pageSize = PdfPageSize.A4,
            orientation = PdfOrientation.PORTRAIT,
            margin = PdfMargin.NARROW,
            quality = PdfQuality.BALANCED,
            background = PageBackground.WHITE,
            fitToPage = false,
            folderId = childId
        ).getOrThrow()

        assertEquals(childId, created.folderId)
        assertTrue(File(created.filePath).exists())

        // Delete child folder: document is detached (not deleted from disk!) and grandchild folder would reparent
        val grandChildId = repository.createFolder("Receipts", parentFolderId = childId).getOrThrow()
        repository.deleteFolder(childId)
        val refreshedDoc = repository.getDocument(created.id)
        assertNotNull(refreshedDoc)
        assertNull(refreshedDoc?.folderId)
        assertTrue(File(created.filePath).exists())
        assertEquals(parentId, db.folderDao().getFolderById(grandChildId)?.parentFolderId)

        // Move, star, rename, duplicate
        repository.moveDocumentsToFolder(listOf(created.id), parentId)
        repository.toggleStar(created.id, true)
        assertTrue(repository.renameDocument(created.id, "TaxPacket_Final").isSuccess)
        val afterRename = repository.getDocument(created.id)!!
        assertEquals("TaxPacket_Final", afterRename.name)
        assertEquals(parentId, afterRename.folderId)
        assertTrue(afterRename.isStarred)

        val copy = repository.duplicateDocument(created.id).getOrThrow()
        assertTrue(File(copy.filePath).exists())

        // Save OCR text doc
        val txtDoc = repository.saveOcrAsTextDocument("TaxOCR", "Invoice total 420.00", sourceDocId = created.id).getOrThrow()
        assertEquals(DocumentFormat.TXT, txtDoc.format)
        assertEquals("Invoice total 420.00", repository.getDocument(created.id)?.ocrText)

        // Signature create & delete
        val sigBmp = Bitmap.createBitmap(120, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        val sig = repository.saveSignatureBitmap("MySig", sigBmp).getOrThrow()
        sigBmp.recycle()
        assertTrue(File(sig.imagePath).exists())
        repository.deleteSignature(sig)
        assertFalse(File(sig.imagePath).exists())

        // Delete documents cleans both DB and vault files
        val delRes = repository.deleteDocuments(listOf(created.id, copy.id, txtDoc.id))
        assertEquals(3, delRes.getOrNull())
        assertNull(repository.getDocument(created.id))
        assertFalse(File(created.filePath).exists())
        assertFalse(File(copy.filePath).exists())
        assertFalse(File(txtDoc.filePath).exists())
    }

    @Test
    fun preferencesRepositoryPersistsSettingsAndSanitizesExampleUrl() = runBlocking {
        val prefsRepo = PreferencesRepository(context)
        prefsRepo.setThemeMode(ThemeMode.DARK)
        prefsRepo.setPerformanceMode(PerformanceMode.HIGH)
        prefsRepo.setReducedMotion(true)
        prefsRepo.setAnimationsEnabled(false)
        prefsRepo.setHasSeenOnboarding(true)
        prefsRepo.setPrivacyPolicyUrl("https://example.com/prix/privacy-policy")

        val prefs = prefsRepo.preferences.first()
        assertEquals(ThemeMode.DARK, prefs.themeMode)
        assertEquals(PerformanceMode.HIGH, prefs.performanceMode)
        assertTrue(prefs.reducedMotion)
        assertFalse(prefs.animationsEnabled)
        assertTrue(prefs.hasSeenOnboarding)
        assertEquals("", prefs.privacyPolicyUrl)
    }

    private fun createTestImageFile(
        name: String,
        w: Int,
        h: Int,
        bgColor: Int,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG
    ): File {
        val file = File(workDir, name)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            drawColor(bgColor)
            drawRect(20f, 20f, w - 20f, h - 20f, Paint().apply { color = Color.DKGRAY })
        }
        FileOutputStream(file).use { bmp.compress(format, 90, it) }
        bmp.recycle()
        return file
    }

    private fun readXmlAttributes(parser: XmlResourceParser): List<String> {
        val items = mutableListOf<String>()
        var event = parser.eventType
        while (event != XmlResourceParser.END_DOCUMENT) {
            if (event == XmlResourceParser.START_TAG && parser.name == "exclude") {
                val domain = parser.getAttributeValue(null, "domain").orEmpty()
                val path = parser.getAttributeValue(null, "path").orEmpty()
                items.add("$domain:$path")
            }
            event = parser.next()
        }
        parser.close()
        return items
    }
}
