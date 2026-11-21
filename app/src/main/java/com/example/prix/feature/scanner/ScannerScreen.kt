package com.example.prix.feature.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.prix.core.data.PrixRepository
import com.example.prix.core.database.DocumentEntity
import com.example.prix.core.database.FolderEntity
import com.example.prix.core.datastore.UserPreferences
import com.example.prix.core.error.PrixErrorMapper
import com.example.prix.core.error.PrixOperation
import com.example.prix.core.image.ImageProcessor
import com.example.prix.core.model.NormalizedPoint
import com.example.prix.core.model.PdfOrientation
import com.example.prix.core.model.PdfPageSize
import com.example.prix.core.model.PdfQuality
import com.example.prix.core.model.QuadPoints
import com.example.prix.core.model.ScanFilter
import com.example.prix.core.model.ScannedPageDraft
import com.example.prix.core.storage.DocumentStorageManager
import com.example.prix.core.ui.GlassButton
import com.example.prix.core.ui.GlassCard
import com.example.prix.core.ui.GlassChip
import com.example.prix.core.ui.GlassDialog
import com.example.prix.core.ui.GlassIconButton
import com.example.prix.core.ui.GlassProgressIndicator
import com.example.prix.core.ui.GlassTopBar
import com.example.prix.core.ui.LiquidGlassBackground
import com.example.ui.theme.LocalPrixColors
import com.example.ui.theme.LocalPrixEffects
import com.example.ui.theme.PrixAccentBlue
import com.example.ui.theme.PrixPurplePrimary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.math.hypot

private enum class ScannerStage { CAMERA, REVIEW, EXPORT }

@Composable
fun ScannerScreen(
    repository: PrixRepository,
    preferences: UserPreferences,
    folders: List<FolderEntity>,
    onClose: () -> Unit,
    onSavedPdf: (DocumentEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val effects = LocalPrixEffects.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var stage by remember { mutableStateOf(ScannerStage.CAMERA) }
    val drafts = remember { mutableStateListOf<ScannedPageDraft>() }
    var activePageIndex by remember { mutableIntStateOf(0) }
    var retakeTargetIndex by remember { mutableStateOf<Int?>(null) }

    // Camera state
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var autoCapture by remember { mutableStateOf(preferences.scannerAutoCapture) }
    var shutterFlash by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var showDiscardScanDialog by remember { mutableStateOf(false) }

    // Export state
    var docName by remember { mutableStateOf("Scan_${DocumentStorageManager.timestampTag()}") }
    var pageSize by remember { mutableStateOf(preferences.defaultPageSize) }
    var orientation by remember { mutableStateOf(PdfOrientation.PORTRAIT) }
    var quality by remember { mutableStateOf(preferences.defaultPdfQuality) }
    var selectedFolderId by remember { mutableStateOf(preferences.defaultSaveFolderId) }
    var isExporting by remember { mutableStateOf(false) }
    var activeExportJob by remember { mutableStateOf<Job?>(null) }
    var exportProgress by remember { mutableFloatStateOf(0f) }
    var exportError by remember { mutableStateOf<String?>(null) }

    // OCR preview state
    var ocrDialogText by remember { mutableStateOf<String?>(null) }
    var isRunningOcr by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            activeExportJob?.cancel()
            drafts.forEach { d ->
                repository.storage.unmarkPathActive(d.rawImagePath)
                repository.storage.deleteFileSafely(d.rawImagePath)
            }
        }
    }

    val requestCloseScanner: () -> Unit = {
        if (isExporting) {
            activeExportJob?.cancel()
            isExporting = false
        } else if (drafts.isNotEmpty()) {
            showDiscardScanDialog = true
        } else {
            onClose()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                var failedCount = 0
                uris.forEach { uri ->
                    val temp = repository.importImageUriToTemp(uri, effects.performanceProfile)
                    if (temp != null) {
                        repository.storage.markPathActive(temp.absolutePath)
                        val bmp = ImageProcessor.decodeSampledBitmap(temp.absolutePath, 600)
                        val quad = if (bmp != null) {
                            ImageProcessor.detectDocumentQuad(bmp).also { bmp.recycle() }
                        } else QuadPoints()
                        val newDraft = ScannedPageDraft(
                            id = UUID.randomUUID().toString(),
                            rawImagePath = temp.absolutePath,
                            quad = quad,
                            filter = if (preferences.scannerAutoEnhance) ScanFilter.AUTO_ENHANCE else ScanFilter.ORIGINAL
                        )
                        val targetIdx = retakeTargetIndex
                        if (targetIdx != null && targetIdx in drafts.indices) {
                            val previous = drafts[targetIdx]
                            repository.storage.unmarkPathActive(previous.rawImagePath)
                            repository.storage.deleteFileSafely(previous.rawImagePath)
                            drafts[targetIdx] = newDraft
                            retakeTargetIndex = null
                            activePageIndex = targetIdx
                        } else {
                            drafts.add(newDraft)
                            activePageIndex = drafts.lastIndex
                        }
                    } else {
                        failedCount++
                    }
                }
                if (failedCount > 0) {
                    cameraError = PrixErrorMapper.map(PrixOperation.IMPORT_IMAGE, null).toInlineMessage()
                }
                if (drafts.isNotEmpty()) {
                    stage = ScannerStage.REVIEW
                }
            }
        }
    }

    BackHandler {
        when (stage) {
            ScannerStage.EXPORT -> {
                if (isExporting) activeExportJob?.cancel() else stage = ScannerStage.REVIEW
            }
            ScannerStage.REVIEW -> {
                if (drafts.isEmpty()) onClose() else stage = ScannerStage.CAMERA
            }
            ScannerStage.CAMERA -> requestCloseScanner()
        }
    }

    AnimatedContent(
        targetState = stage,
        transitionSpec = {
            if (effects.shouldAnimate) {
                fadeIn(tween(200)) togetherWith fadeOut(tween(150))
            } else {
                fadeIn(tween(0)) togetherWith fadeOut(tween(0))
            }
        },
        label = "scannerStage"
    ) { activeStage ->
        when (activeStage) {
            ScannerStage.CAMERA -> {
                CameraCaptureStage(
                    hasCameraPermission = hasCameraPermission,
                    lensFacing = lensFacing,
                    flashMode = flashMode,
                    autoCapture = autoCapture,
                    shutterFlash = shutterFlash,
                    isCapturing = isCapturing,
                    capturedCount = drafts.size,
                    cameraError = cameraError,
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onOpenAppSettings = {
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                            )
                        } catch (_: Exception) {
                            cameraError = "Unable to open Android App Settings on this device."
                        }
                    },
                    onClose = requestCloseScanner,
                    onToggleFlash = {
                        flashMode = when (flashMode) {
                            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
                            else -> ImageCapture.FLASH_MODE_AUTO
                        }
                    },
                    onToggleLens = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    },
                    onToggleAutoCapture = { autoCapture = !autoCapture },
                    onImportGallery = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onReviewPages = {
                        if (drafts.isNotEmpty()) {
                            activePageIndex = drafts.lastIndex
                            stage = ScannerStage.REVIEW
                        }
                    },
                    onCameraError = { cameraError = it },
                    onCaptureWithController = { captureFn ->
                        if (!isCapturing) {
                            isCapturing = true
                            val file = repository.storage.createTempImageFile("cam_")
                            repository.storage.markPathActive(file.absolutePath)
                            captureFn(file) { success, err ->
                                scope.launch {
                                    if (success && file.exists() && file.length() > 0L) {
                                        shutterFlash = true
                                        val bmp = ImageProcessor.decodeSampledBitmap(file.absolutePath, 600)
                                        val quad = if (bmp != null) {
                                            ImageProcessor.detectDocumentQuad(bmp).also { bmp.recycle() }
                                        } else QuadPoints()
                                        val newDraft = ScannedPageDraft(
                                            id = UUID.randomUUID().toString(),
                                            rawImagePath = file.absolutePath,
                                            quad = quad,
                                            filter = if (preferences.scannerAutoEnhance) ScanFilter.AUTO_ENHANCE else ScanFilter.ORIGINAL
                                        )
                                        val targetIdx = retakeTargetIndex
                                        if (targetIdx != null && targetIdx in drafts.indices) {
                                            val old = drafts[targetIdx]
                                            repository.storage.unmarkPathActive(old.rawImagePath)
                                            repository.storage.deleteFileSafely(old.rawImagePath)
                                            drafts[targetIdx] = newDraft
                                            retakeTargetIndex = null
                                            activePageIndex = targetIdx
                                            stage = ScannerStage.REVIEW
                                        } else {
                                            drafts.add(newDraft)
                                            activePageIndex = drafts.lastIndex
                                        }
                                        delay(140)
                                        shutterFlash = false
                                    } else {
                                        repository.storage.unmarkPathActive(file.absolutePath)
                                        repository.storage.deleteFileSafely(file.absolutePath)
                                        cameraError = PrixErrorMapper.map(
                                            PrixOperation.SCAN_CAPTURE,
                                            if (err != null) IllegalStateException(err) else null
                                        ).toInlineMessage()
                                    }
                                    isCapturing = false
                                }
                            }
                        }
                    }
                )
            }

            ScannerStage.REVIEW -> {
                if (drafts.isEmpty()) {
                    stage = ScannerStage.CAMERA
                } else {
                    val safeIdx = activePageIndex.coerceIn(0, drafts.lastIndex)
                    activePageIndex = safeIdx
                    ReviewAndCropStage(
                        drafts = drafts,
                        activeIndex = safeIdx,
                        previewMaxDim = effects.performanceProfile.previewDecodeMaxDim,
                        isRunningOcr = isRunningOcr,
                        onSelectPage = { activePageIndex = it },
                        onUpdateDraft = { updated -> drafts[safeIdx] = updated },
                        onAutoDetectQuad = {
                            scope.launch {
                                val bmp = ImageProcessor.decodeSampledBitmap(drafts[safeIdx].rawImagePath, 600)
                                if (bmp != null) {
                                    val detected = ImageProcessor.detectDocumentQuad(bmp)
                                    bmp.recycle()
                                    drafts[safeIdx] = drafts[safeIdx].copy(quad = detected)
                                }
                            }
                        },
                        onMoveLeft = {
                            if (safeIdx > 0) {
                                val item = drafts.removeAt(safeIdx)
                                drafts.add(safeIdx - 1, item)
                                activePageIndex = safeIdx - 1
                            }
                        },
                        onMoveRight = {
                            if (safeIdx < drafts.lastIndex) {
                                val item = drafts.removeAt(safeIdx)
                                drafts.add(safeIdx + 1, item)
                                activePageIndex = safeIdx + 1
                            }
                        },
                        onRetake = {
                            retakeTargetIndex = safeIdx
                            stage = ScannerStage.CAMERA
                        },
                        onDeletePage = {
                            val removed = drafts.removeAt(safeIdx)
                            repository.storage.unmarkPathActive(removed.rawImagePath)
                            repository.storage.deleteFileSafely(removed.rawImagePath)
                            if (drafts.isEmpty()) {
                                stage = ScannerStage.CAMERA
                            } else {
                                activePageIndex = (safeIdx - 1).coerceAtLeast(0)
                            }
                        },
                        onAddMoreCamera = {
                            retakeTargetIndex = null
                            stage = ScannerStage.CAMERA
                        },
                        onAddMoreGallery = {
                            retakeTargetIndex = null
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onRunOcr = {
                            scope.launch {
                                isRunningOcr = true
                                val current = drafts[safeIdx]
                                val raw = ImageProcessor.decodeSampledBitmap(
                                    current.rawImagePath,
                                    effects.performanceProfile.ocrMaxDim
                                )
                                if (raw != null) {
                                    val warped = ImageProcessor.applyPerspectiveWarp(raw, current.quad)
                                    raw.recycle()
                                    val rot = ImageProcessor.rotateBitmap(warped, current.rotationDegrees)
                                    if (rot != warped) warped.recycle()
                                    val ocr = repository.ocrEngine.extractFromBitmap(rot)
                                    rot.recycle()
                                    ocrDialogText = ocr.getOrNull()?.fullText?.ifBlank {
                                        "No recognizable text was found on this page."
                                    } ?: PrixErrorMapper.map(PrixOperation.OCR_EXTRACT, ocr.exceptionOrNull()).toDisplayMessage()
                                } else {
                                    ocrDialogText = PrixErrorMapper.map(PrixOperation.IMPORT_IMAGE, null).toDisplayMessage()
                                }
                                isRunningOcr = false
                            }
                        },
                        onBackToCamera = { stage = ScannerStage.CAMERA },
                        onProceedExport = { stage = ScannerStage.EXPORT }
                    )
                }
            }

            ScannerStage.EXPORT -> {
                ScannerExportStage(
                    pageCount = drafts.size,
                    docName = docName,
                    pageSize = pageSize,
                    orientation = orientation,
                    quality = quality,
                    folders = folders,
                    selectedFolderId = selectedFolderId,
                    isExporting = isExporting,
                    progress = exportProgress,
                    error = exportError,
                    onDocNameChange = { docName = it },
                    onPageSizeChange = { pageSize = it },
                    onOrientationChange = { orientation = it },
                    onQualityChange = { quality = it },
                    onFolderChange = { selectedFolderId = it },
                    onBack = { stage = ScannerStage.REVIEW },
                    onCancelExport = { activeExportJob?.cancel() },
                    onSavePdf = {
                        activeExportJob?.cancel()
                        activeExportJob = scope.launch {
                            isExporting = true
                            exportProgress = 0f
                            exportError = null
                            try {
                                val res = repository.saveScannedDraftsAsPdf(
                                    drafts = drafts.toList(),
                                    documentName = docName,
                                    pageSize = pageSize,
                                    orientation = orientation,
                                    quality = quality,
                                    folderId = selectedFolderId,
                                    performanceProfile = effects.performanceProfile,
                                    onProgress = { exportProgress = it }
                                )
                                res.onSuccess { savedDoc ->
                                    drafts.forEach {
                                        repository.storage.unmarkPathActive(it.rawImagePath)
                                        repository.storage.deleteFileSafely(it.rawImagePath)
                                    }
                                    drafts.clear()
                                    onSavedPdf(savedDoc)
                                }.onFailure { err ->
                                    exportError = PrixErrorMapper.map(PrixOperation.CREATE_PDF, err).toDisplayMessage()
                                }
                            } catch (ce: CancellationException) {
                                exportError = PrixErrorMapper.map(PrixOperation.CREATE_PDF, ce).toInlineMessage()
                            } finally {
                                isExporting = false
                            }
                        }
                    }
                )
            }
        }
    }

    if (showDiscardScanDialog) {
        GlassDialog(
            onDismissRequest = { showDiscardScanDialog = false },
            title = "Discard Scanned Pages?",
            content = {
                Text("You have ${drafts.size} unsaved scanned page(s). Discard them and exit the scanner?")
            },
            confirmButton = {
                Button(onClick = {
                    showDiscardScanDialog = false
                    drafts.forEach {
                        repository.storage.unmarkPathActive(it.rawImagePath)
                        repository.storage.deleteFileSafely(it.rawImagePath)
                    }
                    drafts.clear()
                    onClose()
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardScanDialog = false }) { Text("Keep Scanning") }
            }
        )
    }

    ocrDialogText?.let { text ->
        GlassDialog(
            onDismissRequest = { ocrDialogText = null },
            title = "Extracted Page Text (OCR)",
            content = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = { ocrDialogText = null }) { Text("Done") }
            }
        )
    }
}

@Composable
private fun CameraCaptureStage(
    hasCameraPermission: Boolean,
    lensFacing: Int,
    flashMode: Int,
    autoCapture: Boolean,
    shutterFlash: Boolean,
    isCapturing: Boolean,
    capturedCount: Int,
    cameraError: String?,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onClose: () -> Unit,
    onToggleFlash: () -> Unit,
    onToggleLens: () -> Unit,
    onToggleAutoCapture: () -> Unit,
    onImportGallery: () -> Unit,
    onReviewPages: () -> Unit,
    onCameraError: (String?) -> Unit,
    onCaptureWithController: (((File, (Boolean, String?) -> Unit) -> Unit) -> Unit)
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }

    val triggerCapture: () -> Unit = {
        val ic = imageCapture
        if (ic == null) {
            onCameraError("Camera is still initializing or unavailable on this device. You can import photos from Gallery instead.")
        } else {
            onCaptureWithController { targetFile, callback ->
                val outputOpts = ImageCapture.OutputFileOptions.Builder(targetFile).build()
                ic.takePicture(
                    outputOpts,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            callback(true, null)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            callback(false, exception.localizedMessage)
                        }
                    }
                )
            }
        }
    }

    DisposableEffect(hasCameraPermission, lensFacing) {
        if (!hasCameraPermission) {
            onDispose { }
        } else {
            val future = ProcessCameraProvider.getInstance(context)
            val executor = ContextCompat.getMainExecutor(context)
            future.addListener({
                try {
                    val provider = future.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setFlashMode(flashMode)
                        .build()
                    val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                    provider.unbindAll()
                    boundCamera = provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                    imageCapture = capture
                    onCameraError(null)
                } catch (e: Exception) {
                    imageCapture = null
                    onCameraError("Camera preview unavailable (${e.localizedMessage ?: "No camera hardware"}). Use Import Gallery below to scan existing photos.")
                }
            }, executor)
            onDispose {
                try {
                    future.get().unbindAll()
                } catch (_: Exception) {
                }
            }
        }
    }

    LaunchedEffect(flashMode, imageCapture) {
        imageCapture?.flashMode = flashMode
    }

    var autoCountdown by remember { mutableIntStateOf(0) }
    LaunchedEffect(autoCapture, hasCameraPermission, imageCapture) {
        if (autoCapture && hasCameraPermission && imageCapture != null) {
            while (autoCapture) {
                autoCountdown = 3
                while (autoCountdown > 0 && autoCapture) {
                    delay(1000)
                    autoCountdown--
                }
                if (autoCapture && !isCapturing) {
                    triggerCapture()
                    delay(1500)
                }
            }
        } else {
            autoCountdown = 0
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
            // Subtle glass framing guide
            Canvas(modifier = Modifier.fillMaxSize()) {
                val padX = size.width * 0.08f
                val padY = size.height * 0.16f
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.45f),
                    topLeft = Offset(padX, padY),
                    size = androidx.compose.ui.geometry.Size(size.width - padX * 2, size.height - padY * 2),
                    style = Stroke(width = 3f)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Camera Access Needed",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "PRIX uses your camera locally to capture documents. No photos leave your device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.78f)
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = onRequestPermission, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Text("Allow Camera")
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onOpenAppSettings) {
                    Text("Open App Settings", color = Color.White)
                }
            }
        }

        AnimatedVisibility(visible = shutterFlash, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.55f))
            )
        }

        // Frosted Dark Glass Top Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xAA161A2C),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("scanner_close_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close scanner", tint = Color.White)
                }
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xAA161A2C),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(onClick = onToggleFlash, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(
                            imageVector = when (flashMode) {
                                ImageCapture.FLASH_MODE_ON -> Icons.Filled.FlashOn
                                ImageCapture.FLASH_MODE_OFF -> Icons.Filled.FlashOff
                                else -> Icons.Filled.FlashAuto
                            },
                            contentDescription = "Toggle flash",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = onToggleAutoCapture, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(
                            Icons.Filled.Timer,
                            contentDescription = "Toggle auto capture",
                            tint = if (autoCapture) PrixPurplePrimary else Color.White
                        )
                    }
                    IconButton(onClick = onToggleLens, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Filled.FlipCameraAndroid, contentDescription = "Switch camera", tint = Color.White)
                    }
                }
            }
        }

        if (autoCapture && autoCountdown > 0) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = PrixPurplePrimary.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 70.dp)
            ) {
                Text(
                    text = "Auto-capturing in ${autoCountdown}s…",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }

        if (cameraError != null) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.94f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            ) {
                Text(
                    text = cameraError,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        // Frosted Bottom Camera Dock
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = Color(0xBB101322),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onImportGallery,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f))
                            .testTag("scanner_import_gallery")
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = "Import from gallery", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Gallery", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }

                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(if (isCapturing) Color.Gray else Color.White)
                        .clickable(enabled = hasCameraPermission && !isCapturing) { triggerCapture() }
                        .testTag("scanner_shutter_button"),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCapturing) {
                        CircularProgressIndicator(modifier = Modifier.size(26.dp), color = PrixPurplePrimary)
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (capturedCount > 0) PrixPurplePrimary else Color.White.copy(alpha = 0.16f),
                        onClick = onReviewPages,
                        enabled = capturedCount > 0,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (capturedCount > 0) "$capturedCount" else "0",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Review scanned pages",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Review", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ReviewAndCropStage(
    drafts: List<ScannedPageDraft>,
    activeIndex: Int,
    previewMaxDim: Int,
    isRunningOcr: Boolean,
    onSelectPage: (Int) -> Unit,
    onUpdateDraft: (ScannedPageDraft) -> Unit,
    onAutoDetectQuad: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onRetake: () -> Unit,
    onDeletePage: () -> Unit,
    onAddMoreCamera: () -> Unit,
    onAddMoreGallery: () -> Unit,
    onRunOcr: () -> Unit,
    onBackToCamera: () -> Unit,
    onProceedExport: () -> Unit
) {
    val active = drafts[activeIndex]
    val prixColors = LocalPrixColors.current
    var cropMode by remember { mutableStateOf(false) }

    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var rawThumbBitmap by remember { mutableStateOf<Bitmap?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            val prev = previewBitmap
            val raw = rawThumbBitmap
            if (prev != null && prev !== raw && !prev.isRecycled) {
                prev.recycle()
            }
            if (raw != null && !raw.isRecycled) {
                raw.recycle()
            }
        }
    }

    LaunchedEffect(active.id, active.rawImagePath, previewMaxDim) {
        val nextRaw = ImageProcessor.decodeSampledBitmap(active.rawImagePath, previewMaxDim)
        val oldRaw = rawThumbBitmap
        if (previewBitmap === oldRaw) {
            previewBitmap = nextRaw
        }
        rawThumbBitmap = nextRaw
        if (oldRaw != null && oldRaw !== nextRaw && !oldRaw.isRecycled) {
            oldRaw.recycle()
        }
    }

    LaunchedEffect(
        active.id,
        active.rawImagePath,
        active.quad,
        active.rotationDegrees,
        active.filter,
        active.brightness,
        active.contrast,
        cropMode,
        rawThumbBitmap
    ) {
        val raw = rawThumbBitmap ?: return@LaunchedEffect
        if (raw.isRecycled) return@LaunchedEffect
        if (cropMode) {
            val oldPrev = previewBitmap
            previewBitmap = raw
            if (oldPrev != null && oldPrev !== raw && !oldPrev.isRecycled) {
                oldPrev.recycle()
            }
        } else {
            delay(35)
            val warped = ImageProcessor.applyPerspectiveWarp(raw, active.quad)
            val rotated = ImageProcessor.rotateBitmap(warped, active.rotationDegrees)
            if (rotated !== warped && !warped.isRecycled) warped.recycle()
            val filtered = ImageProcessor.applyFilterAndAdjustments(
                rotated, active.filter, active.brightness, active.contrast
            )
            if (filtered !== rotated && !rotated.isRecycled) rotated.recycle()

            val oldPrev = previewBitmap
            previewBitmap = filtered
            if (oldPrev != null && oldPrev !== raw && oldPrev !== filtered && !oldPrev.isRecycled) {
                oldPrev.recycle()
            }
        }
    }

    LiquidGlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                GlassTopBar(
                    title = "Page ${activeIndex + 1} of ${drafts.size}",
                    subtitle = "Crop corners, filter & enhance",
                    onBack = onBackToCamera
                ) {
                    GlassIconButton(
                        icon = Icons.Filled.TextSnippet,
                        contentDescription = "Extract OCR text",
                        onClick = onRunOcr,
                        enabled = !isRunningOcr
                    )
                    Button(
                        onClick = onProceedExport,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("scanner_next_export")
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save PDF")
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF0E1120))
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = previewBitmap
                    if (bmp != null && !bmp.isRecycled) {
                        if (cropMode) {
                            QuadCropEditor(
                                bitmap = bmp,
                                quad = active.quad,
                                onQuadChange = { onUpdateDraft(active.copy(quad = it)) }
                            )
                        } else {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Scanned page preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        CircularProgressIndicator(color = Color.White)
                    }
                }

                // Liquid Glass Editing Deck
                Surface(
                    color = prixColors.glassElevated,
                    border = BorderStroke(1.dp, prixColors.glassOuterBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                GlassChip(
                                    label = if (cropMode) "Done Crop" else "Crop Corners",
                                    selected = cropMode,
                                    leadingIcon = Icons.Filled.Crop,
                                    onClick = { cropMode = !cropMode }
                                )
                            }
                            if (cropMode) {
                                item {
                                    GlassChip(
                                        label = "Auto Detect",
                                        selected = false,
                                        leadingIcon = Icons.Filled.AutoAwesome,
                                        onClick = onAutoDetectQuad
                                    )
                                }
                            }
                            item {
                                GlassChip(
                                    label = "Rotate",
                                    selected = false,
                                    leadingIcon = Icons.AutoMirrored.Filled.RotateRight,
                                    onClick = {
                                        onUpdateDraft(active.copy(rotationDegrees = (active.rotationDegrees + 90) % 360))
                                    }
                                )
                            }
                            item {
                                GlassChip(
                                    label = "Retake",
                                    selected = false,
                                    leadingIcon = Icons.Filled.Refresh,
                                    onClick = onRetake
                                )
                            }
                            item {
                                GlassChip(
                                    label = "Delete",
                                    selected = false,
                                    leadingIcon = Icons.Filled.Delete,
                                    onClick = onDeletePage
                                )
                            }
                            if (drafts.size > 1) {
                                item {
                                    GlassChip(
                                        label = "Move ◀",
                                        selected = false,
                                        enabled = activeIndex > 0,
                                        onClick = onMoveLeft
                                    )
                                }
                                item {
                                    GlassChip(
                                        label = "Move ▶",
                                        selected = false,
                                        enabled = activeIndex < drafts.lastIndex,
                                        onClick = onMoveRight
                                    )
                                }
                            }
                            item {
                                GlassChip(
                                    label = "+ Camera",
                                    selected = false,
                                    leadingIcon = Icons.Filled.AddAPhoto,
                                    onClick = onAddMoreCamera
                                )
                            }
                            item {
                                GlassChip(
                                    label = "+ Photo",
                                    selected = false,
                                    leadingIcon = Icons.Filled.PhotoLibrary,
                                    onClick = onAddMoreGallery
                                )
                            }
                        }

                        if (!cropMode) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                itemsIndexed(ScanFilter.entries) { _, filter ->
                                    GlassChip(
                                        label = filter.label,
                                        selected = active.filter == filter,
                                        onClick = { onUpdateDraft(active.copy(filter = filter)) }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Brightness", style = MaterialTheme.typography.labelSmall, color = prixColors.secondaryText)
                                    Slider(
                                        value = active.brightness,
                                        onValueChange = { onUpdateDraft(active.copy(brightness = it)) },
                                        valueRange = -55f..55f
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Contrast", style = MaterialTheme.typography.labelSmall, color = prixColors.secondaryText)
                                    Slider(
                                        value = active.contrast,
                                        onValueChange = { onUpdateDraft(active.copy(contrast = it)) },
                                        valueRange = 0.65f..1.65f
                                    )
                                }
                            }
                        }

                        if (drafts.size > 1) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                itemsIndexed(drafts) { index, _ ->
                                    GlassChip(
                                        label = "Page ${index + 1}",
                                        selected = index == activeIndex,
                                        onClick = { onSelectPage(index) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuadCropEditor(
    bitmap: Bitmap,
    quad: QuadPoints,
    onQuadChange: (QuadPoints) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        val bmpAspect = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
        val boxAspect = boxW / boxH.coerceAtLeast(1f)

        val drawW: Float
        val drawH: Float
        if (bmpAspect > boxAspect) {
            drawW = boxW
            drawH = boxW / bmpAspect
        } else {
            drawH = boxH
            drawW = boxH * bmpAspect
        }
        val offsetX = (boxW - drawW) / 2f
        val offsetY = (boxH - drawH) / 2f

        var draggingCorner by remember { mutableIntStateOf(-1) }

        if (!bitmap.isRecycled) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Crop document corners",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(quad, drawW, drawH) {
                    detectDragGestures(
                        onDragStart = { start ->
                            val pts = listOf(
                                Offset(offsetX + quad.topLeft.x * drawW, offsetY + quad.topLeft.y * drawH),
                                Offset(offsetX + quad.topRight.x * drawW, offsetY + quad.topRight.y * drawH),
                                Offset(offsetX + quad.bottomRight.x * drawW, offsetY + quad.bottomRight.y * drawH),
                                Offset(offsetX + quad.bottomLeft.x * drawW, offsetY + quad.bottomLeft.y * drawH)
                            )
                            draggingCorner = pts.indices.minByOrNull { idx ->
                                hypot(pts[idx].x - start.x, pts[idx].y - start.y)
                            } ?: -1
                        },
                        onDragEnd = { draggingCorner = -1 },
                        onDragCancel = { draggingCorner = -1 },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val dx = dragAmount.x / drawW
                            val dy = dragAmount.y / drawH
                            val updated = when (draggingCorner) {
                                0 -> quad.copy(
                                    topLeft = NormalizedPoint(
                                        (quad.topLeft.x + dx).coerceIn(0f, 0.8f),
                                        (quad.topLeft.y + dy).coerceIn(0f, 0.8f)
                                    )
                                )
                                1 -> quad.copy(
                                    topRight = NormalizedPoint(
                                        (quad.topRight.x + dx).coerceIn(0.2f, 1f),
                                        (quad.topRight.y + dy).coerceIn(0f, 0.8f)
                                    )
                                )
                                2 -> quad.copy(
                                    bottomRight = NormalizedPoint(
                                        (quad.bottomRight.x + dx).coerceIn(0.2f, 1f),
                                        (quad.bottomRight.y + dy).coerceIn(0.2f, 1f)
                                    )
                                )
                                3 -> quad.copy(
                                    bottomLeft = NormalizedPoint(
                                        (quad.bottomLeft.x + dx).coerceIn(0f, 0.8f),
                                        (quad.bottomLeft.y + dy).coerceIn(0.2f, 1f)
                                    )
                                )
                                else -> quad
                            }
                            onQuadChange(updated)
                        }
                    )
                }
        ) {
            val tl = Offset(offsetX + quad.topLeft.x * drawW, offsetY + quad.topLeft.y * drawH)
            val tr = Offset(offsetX + quad.topRight.x * drawW, offsetY + quad.topRight.y * drawH)
            val br = Offset(offsetX + quad.bottomRight.x * drawW, offsetY + quad.bottomRight.y * drawH)
            val bl = Offset(offsetX + quad.bottomLeft.x * drawW, offsetY + quad.bottomLeft.y * drawH)

            val poly = Path().apply {
                moveTo(tl.x, tl.y)
                lineTo(tr.x, tr.y)
                lineTo(br.x, br.y)
                lineTo(bl.x, bl.y)
                close()
            }
            drawPath(poly, color = PrixAccentBlue.copy(alpha = 0.14f))
            drawPath(poly, color = PrixAccentBlue, style = Stroke(width = 4f))

            listOf(tl, tr, br, bl).forEach { corner ->
                drawCircle(color = Color.White, radius = 22f, center = corner)
                drawCircle(color = PrixPurplePrimary, radius = 15f, center = corner)
            }
        }
    }
}

@Composable
private fun ScannerExportStage(
    pageCount: Int,
    docName: String,
    pageSize: PdfPageSize,
    orientation: PdfOrientation,
    quality: PdfQuality,
    folders: List<FolderEntity>,
    selectedFolderId: Long?,
    isExporting: Boolean,
    progress: Float,
    error: String?,
    onDocNameChange: (String) -> Unit,
    onPageSizeChange: (PdfPageSize) -> Unit,
    onOrientationChange: (PdfOrientation) -> Unit,
    onQualityChange: (PdfQuality) -> Unit,
    onFolderChange: (Long?) -> Unit,
    onBack: () -> Unit,
    onCancelExport: () -> Unit,
    onSavePdf: () -> Unit
) {
    val prixColors = LocalPrixColors.current
    LiquidGlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                GlassTopBar(
                    title = "Save Scanned PDF",
                    subtitle = "$pageCount page(s) ready",
                    onBack = if (!isExporting) onBack else null
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                GlassCard(
                    shape = RoundedCornerShape(22.dp),
                    elevated = true,
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = docName,
                        onValueChange = onDocNameChange,
                        label = { Text("Document Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Page Size", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(PdfPageSize.entries) { _, item ->
                            GlassChip(
                                label = item.label,
                                selected = pageSize == item,
                                onClick = { onPageSizeChange(item) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Orientation", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PdfOrientation.entries.forEach { item ->
                            GlassChip(
                                label = item.label,
                                selected = orientation == item,
                                onClick = { onOrientationChange(item) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Output Quality", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PdfQuality.entries.forEach { item ->
                            GlassChip(
                                label = item.label,
                                selected = quality == item,
                                onClick = { onQualityChange(item) }
                            )
                        }
                    }

                    if (folders.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Save to Folder", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                GlassChip(
                                    label = "All Documents",
                                    selected = selectedFolderId == null,
                                    onClick = { onFolderChange(null) }
                                )
                            }
                            itemsIndexed(folders) { _, f ->
                                GlassChip(
                                    label = f.name,
                                    selected = selectedFolderId == f.id,
                                    onClick = { onFolderChange(f.id) }
                                )
                            }
                        }
                    }
                }

                GlassCard(
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Notice: Scanned PDFs are stored locally on your device and are not automatically certified legal originals.",
                        style = MaterialTheme.typography.bodySmall,
                        color = prixColors.secondaryText
                    )
                }

                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }

                if (isExporting) {
                    GlassProgressIndicator(
                        progress = progress,
                        label = "Building PDF…"
                    )
                    TextButton(
                        onClick = onCancelExport,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cancel Export")
                    }
                }

                GlassButton(
                    text = if (isExporting) "Generating PDF…" else "Generate & Save PDF",
                    enabled = !isExporting && docName.isNotBlank(),
                    onClick = onSavePdf,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("scanner_confirm_save_pdf")
                )
            }
        }
    }
}
