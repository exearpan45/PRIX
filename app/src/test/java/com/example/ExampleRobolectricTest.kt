package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.prix.PrixAppContainer
import com.example.prix.core.datastore.UserPreferences
import com.example.prix.core.image.ImageProcessor
import com.example.prix.core.model.NormalizedPoint
import com.example.prix.core.model.NormalizedShape
import com.example.prix.core.model.NormalizedStroke
import com.example.prix.core.model.NormalizedTextBox
import com.example.prix.core.model.PageNumberPosition
import com.example.prix.core.model.PageTransformItem
import com.example.prix.core.model.PdfMargin
import com.example.prix.core.model.PdfOrientation
import com.example.prix.core.model.PdfPageSize
import com.example.prix.core.model.PdfQuality
import com.example.prix.core.model.PerformanceMode
import com.example.prix.core.model.ScanFilter
import com.example.prix.core.model.ShapeKind
import com.example.prix.core.model.ThemeMode
import com.example.prix.core.model.WatermarkPosition
import com.example.prix.core.storage.DocumentStorageManager
import com.example.prix.ui.PrixAppRoot
import com.example.ui.theme.PrixTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun appNameMatchesPrix() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("PRIX", context.getString(R.string.app_name))
        assertEquals("Scan. Create. Edit. Organize.", context.getString(R.string.app_tagline))
    }

    @Test
    fun storageManagerSanitizesFileNamesAndFormatsSizes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = DocumentStorageManager(context)
        assertEquals("secret_file", storage.sanitizeFileName("../../secret_file"))
        assertEquals("my_scan_doc", storage.sanitizeFileName("folder/my:scan*doc"))
        assertEquals("512 B", DocumentStorageManager.formatFileSize(512))
        assertEquals("2.0 KB", DocumentStorageManager.formatFileSize(2048))
    }

    @Test
    fun imageProcessorFiltersAndPerspectiveWarpProduceValidBitmaps() {
        runBlocking {
        val src = Bitmap.createBitmap(320, 440, Bitmap.Config.ARGB_8888)
        val c = Canvas(src)
        c.drawColor(Color.DKGRAY)
        val p = Paint().apply { color = Color.WHITE }
        c.drawRect(30f, 40f, 290f, 400f, p)

        val quad = ImageProcessor.detectDocumentQuad(src)
        val warped = ImageProcessor.applyPerspectiveWarp(src, quad)
        assertTrue(warped.width >= 100 && warped.height >= 100)

        ScanFilter.entries.forEach { filter ->
            val filtered = ImageProcessor.applyFilterAndAdjustments(warped, filter, 5f, 1.1f)
            assertEquals(warped.width, filtered.width)
            assertEquals(warped.height, filtered.height)
            filtered.recycle()
        }
        warped.recycle()
        src.recycle()
        }
    }

    @Test
    fun pdfEngineCreatesMergesSplitsProtectsWatermarksAndEditsPdfs() {
        runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = PrixAppContainer(context)
        val engine = container.pdfEngine
        val tmpDir = File(context.cacheDir, "unit_pdf_test").apply {
            deleteRecursively()
            mkdirs()
        }

        val img1 = File(tmpDir, "p1.jpg")
        val img2 = File(tmpDir, "p2.jpg")
        listOf(img1, img2).forEachIndexed { idx, file ->
            val bmp = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888)
            Canvas(bmp).apply {
                drawColor(if (idx == 0) Color.WHITE else Color.LTGRAY)
                drawRect(40f, 40f, 360f, 560f, Paint().apply { color = Color.BLUE })
            }
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bmp.recycle()
        }

        val pdfA = File(tmpDir, "a.pdf")
        val createRes = engine.createPdfFromImages(
            imagePaths = listOf(img1.absolutePath, img2.absolutePath),
            outputFile = pdfA,
            pageSize = PdfPageSize.A4,
            orientation = PdfOrientation.PORTRAIT,
            margin = PdfMargin.NARROW,
            quality = PdfQuality.BALANCED
        )
        assertTrue(createRes.isSuccess)
        assertEquals(2, createRes.getOrNull())
        assertTrue(container.storageManager.isLikelyValidPdf(pdfA))

        val pdfB = File(tmpDir, "b.pdf")
        assertTrue(engine.createPdfFromImages(listOf(img1.absolutePath), pdfB).isSuccess)

        val merged = File(tmpDir, "merged.pdf")
        val mergeRes = engine.mergePdfs(listOf(pdfA, pdfB), merged)
        assertTrue(mergeRes.isSuccess)
        assertEquals(3, mergeRes.getOrNull())

        val splitRes = engine.splitPdfByRanges(merged, listOf(1..2, 3..3)) { idx ->
            File(tmpDir, "split_$idx.pdf")
        }
        assertTrue(splitRes.isSuccess)
        assertEquals(2, splitRes.getOrNull()?.size)

        val watermarked = File(tmpDir, "wm.pdf")
        assertTrue(
            engine.addWatermark(
                pdfA, "PRIX TEST", 0.3f, 36f, Color.MAGENTA, WatermarkPosition.DIAGONAL_CENTER, watermarked
            ).isSuccess
        )

        val numbered = File(tmpDir, "num.pdf")
        assertTrue(engine.addPageNumbers(watermarked, PageNumberPosition.BOTTOM_CENTER, 1, true, numbered).isSuccess)

        val encrypted = File(tmpDir, "enc.pdf")
        assertTrue(engine.encryptPdf(numbered, "secret123", "secret123", encrypted).isSuccess)
        val infoEnc = engine.inspectPdfInfo(encrypted, "secret123")
        assertTrue(infoEnc.isSuccess)
        assertTrue(infoEnc.getOrNull()?.isEncrypted == true)

        val unlocked = File(tmpDir, "dec.pdf")
        assertTrue(engine.decryptPdf(encrypted, "secret123", unlocked).isSuccess)
        val infoDec = engine.inspectPdfInfo(unlocked)
        assertTrue(infoDec.isSuccess)
        assertFalse(infoDec.getOrNull()?.isEncrypted == true)

        val edited = File(tmpDir, "edited.pdf")
        val editRes = engine.applyEdits(
            inputFile = unlocked,
            pagePlan = listOf(PageTransformItem(0, 90), PageTransformItem(1, 0)),
            strokes = listOf(
                NormalizedStroke(
                    id = "s1",
                    pageIndex = 0,
                    points = listOf(NormalizedPoint(0.1f, 0.1f), NormalizedPoint(0.4f, 0.4f)),
                    colorArgb = Color.RED,
                    strokeWidthNorm = 0.01f,
                    alpha = 1f
                )
            ),
            textBoxes = listOf(
                NormalizedTextBox("t1", 0, "Verified Overlay", 0.2f, 0.2f, 14f, Color.BLACK)
            ),
            shapes = listOf(
                NormalizedShape("sh1", 1, ShapeKind.RECTANGLE, 0.1f, 0.1f, 0.5f, 0.5f, Color.BLUE, 0.008f)
            ),
            imageOverlays = emptyList(),
            outputFile = edited
        )
        assertTrue(editRes.isSuccess)
        assertEquals(2, editRes.getOrNull())

        val extracted = engine.extractTextPerPage(edited)
        assertTrue("Extract failed: ${extracted.exceptionOrNull()}", extracted.isSuccess)
        val firstPageText = extracted.getOrNull()?.firstOrNull().orEmpty().replace("\n", "")
        assertTrue("Expected 'PRIX TEST' in page 1 text, got: <$firstPageText>", firstPageText.contains("PRIX TEST"))
        assertTrue("Expected '1 / 2' in page 1 text, got: <$firstPageText>", firstPageText.contains("1 / 2"))
        assertTrue("Expected 'Verified Overlay' in page 1 text, got: <$firstPageText>", firstPageText.contains("Verified Overlay"))

        tmpDir.deleteRecursively()
        }
    }

    @Test
    fun onboardingScreenDisplaysGetStartedButton() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = PrixAppContainer(context)

        composeTestRule.setContent {
            PrixTheme {
                PrixAppRoot(
                    container = container,
                    preferences = UserPreferences(hasSeenOnboarding = false, animationsEnabled = false),
                    showSplashInitially = false
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Welcome to PRIX").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_get_started").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun mainShellTabsAndPrivacyNavigationWork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = PrixAppContainer(context)

        composeTestRule.setContent {
            PrixTheme {
                PrixAppRoot(
                    container = container,
                    preferences = UserPreferences(hasSeenOnboarding = true, animationsEnabled = false),
                    showSplashInitially = false
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("PRIX Studio").assertIsDisplayed()

        composeTestRule.onNodeWithTag("bottom_nav_documents").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("documents_search_field").assertIsDisplayed()

        composeTestRule.onNodeWithTag("bottom_nav_tools").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("PDF & Document Tools").assertIsDisplayed()

        composeTestRule.onNodeWithTag("bottom_nav_settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_privacy_row"))
        composeTestRule.onNodeWithTag("settings_privacy_row").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Privacy Policy").assertIsDisplayed()
    }

    @Test
    fun reducedMotionPreferenceAndMultiShareIntentWork() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val container = PrixAppContainer(context)

            assertFalse(container.preferencesRepository.preferences.first().reducedMotion)
            container.preferencesRepository.setReducedMotion(true)
            assertTrue(container.preferencesRepository.preferences.first().reducedMotion)
            container.preferencesRepository.setReducedMotion(false)

            val f1 = container.storageManager.createVaultFile("qa_share_1", "pdf").apply {
                writeText("%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF")
            }
            val f2 = container.storageManager.createVaultFile("qa_share_2", "pdf").apply {
                writeText("%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF")
            }
            val chooserIntent = container.storageManager.buildShareMultipleIntent(
                listOf(f1 to "application/pdf", f2 to "application/pdf"),
                "QA Batch"
            )
            assertEquals(Intent.ACTION_CHOOSER, chooserIntent.action)
            assertTrue((chooserIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
            @Suppress("DEPRECATION")
            val wrapped = chooserIntent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            assertNotNull(wrapped)
            assertEquals(Intent.ACTION_SEND_MULTIPLE, wrapped?.action)
            assertEquals("application/pdf", wrapped?.type)
            assertNotNull(wrapped?.clipData)
            assertEquals(2, wrapped?.clipData?.itemCount)

            f1.delete()
            f2.delete()
        }
    }

    @Test
    fun settingsShowsReducedMotionAndSignatureManager() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = PrixAppContainer(context)

        composeTestRule.setContent {
            PrixTheme {
                PrixAppRoot(
                    container = container,
                    preferences = UserPreferences(hasSeenOnboarding = true, animationsEnabled = false),
                    showSplashInitially = false
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("bottom_nav_settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_list").performScrollToNode(hasText("Default PDF Page Size"))
        composeTestRule.onNodeWithTag("settings_reduced_motion_row").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_list").performScrollToIndex(3)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_signature_manager_row").assertIsDisplayed()
    }

    @Test
    fun onboardingGetStartedButtonPersistsFlagAndNavigatesToHome() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val container = PrixAppContainer(context)
            container.preferencesRepository.setHasSeenOnboarding(false)

            composeTestRule.setContent {
                PrixTheme(themeMode = ThemeMode.LIGHT) {
                    PrixAppRoot(
                        container = container,
                        preferences = UserPreferences(hasSeenOnboarding = false, animationsEnabled = false),
                        showSplashInitially = false
                    )
                }
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("onboarding_get_started").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            assertTrue(container.preferencesRepository.preferences.first().hasSeenOnboarding)
        }
    }

    @Test
    fun darkThemeAndReducedMotionSettingsAndLegalScreensRenderCleanly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = PrixAppContainer(context)

        composeTestRule.setContent {
            PrixTheme(
                themeMode = ThemeMode.DARK,
                performanceMode = PerformanceMode.LOW,
                animationsEnabled = false,
                reducedMotionOverride = true
            ) {
                PrixAppRoot(
                    container = container,
                    preferences = UserPreferences(
                        themeMode = ThemeMode.DARK,
                        performanceMode = PerformanceMode.LOW,
                        hasSeenOnboarding = true,
                        animationsEnabled = false,
                        reducedMotion = true
                    ),
                    showSplashInitially = false
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("bottom_nav_settings").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("perf_low").assertIsDisplayed()
        composeTestRule.onNodeWithTag("perf_high").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_terms_row"))
        composeTestRule.onNodeWithTag("settings_terms_row").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Terms & Conditions").assertIsDisplayed()
    }

    @Test
    fun documentsFolderCreationAndToolsWorkspaceNavigationWork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = PrixAppContainer(context)

        composeTestRule.setContent {
            PrixTheme {
                PrixAppRoot(
                    container = container,
                    preferences = UserPreferences(hasSeenOnboarding = true, animationsEnabled = false),
                    showSplashInitially = false
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("bottom_nav_documents").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("create_folder_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("New Folder").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("filter_tab_starred").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("bottom_nav_tools").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tool_tile_pdf_to_images").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("PDF to Images").assertIsDisplayed()
    }

    @Test
    fun scannerPermissionDeniedUiAndImageToPdfRenderGracefully() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = PrixAppContainer(context)

        composeTestRule.setContent {
            PrixTheme {
                PrixAppRoot(
                    container = container,
                    preferences = UserPreferences(hasSeenOnboarding = true, animationsEnabled = false),
                    showSplashInitially = false
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("action_scan_document").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Camera Access Needed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Allow Camera").assertIsDisplayed()
        composeTestRule.onNodeWithTag("scanner_import_gallery").assertIsDisplayed()
        composeTestRule.onNodeWithTag("scanner_close_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("action_images_to_pdf").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pick_images_button").assertIsDisplayed()
    }

    @Test
    fun mainActivityRejectsUnsafeUriSchemesWithoutCrashing() {
        val unsafeIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("https://evil.example/payload.pdf"), "application/pdf")
        }
        val controller = Robolectric.buildActivity(MainActivity::class.java, unsafeIntent).setup()
        val activity = controller.get()
        assertNotNull(activity)
        controller.pause().stop().destroy()
    }
}
