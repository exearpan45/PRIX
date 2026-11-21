package com.example.prix.feature.tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.prix.core.data.PrixRepository
import com.example.prix.core.database.DocumentEntity
import com.example.prix.core.datastore.UserPreferences
import com.example.prix.core.error.PrixErrorMapper
import com.example.prix.core.error.PrixOperation
import com.example.prix.core.image.ImageProcessor
import com.example.prix.core.model.ImageOutputFormat
import com.example.prix.core.model.PageBackground
import com.example.prix.core.model.PdfMargin
import com.example.prix.core.model.PdfOrientation
import com.example.prix.core.model.PdfPageSize
import com.example.prix.core.model.PdfQuality
import com.example.prix.core.storage.DocumentStorageManager
import com.example.prix.core.ui.GlassButton
import com.example.prix.core.ui.GlassCard
import com.example.prix.core.ui.GlassChip
import com.example.prix.core.ui.GlassProgressIndicator
import com.example.prix.core.ui.GlassTopBar
import com.example.prix.core.ui.LiquidGlassBackground
import com.example.ui.theme.LocalPrixColors
import com.example.ui.theme.LocalPrixEffects
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

private data class PhotoPdfItem(
    val id: String,
    val filePath: String,
    val rotation: Int = 0
)

@Composable
fun ImageToPdfScreen(
    repository: PrixRepository,
    preferences: UserPreferences,
    onBack: () -> Unit,
    onOpenScanner: () -> Unit,
    onCreatedPdf: (DocumentEntity) -> Unit
) {
    val scope = rememberCoroutineScope()
    val prixColors = LocalPrixColors.current
    val effects = LocalPrixEffects.current
    val selectedImages = remember { mutableStateListOf<PhotoPdfItem>() }

    var docName by remember { mutableStateOf("Photos_${DocumentStorageManager.timestampTag()}") }
    var pageSize by remember { mutableStateOf(preferences.defaultPageSize) }
    var orientation by remember { mutableStateOf(PdfOrientation.PORTRAIT) }
    var margin by remember { mutableStateOf(PdfMargin.NARROW) }
    var quality by remember { mutableStateOf(preferences.defaultPdfQuality) }
    var background by remember { mutableStateOf(PageBackground.WHITE) }
    var fitToPage by remember { mutableStateOf(false) }

    var isProcessing by remember { mutableStateOf(false) }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            activeJob?.cancel()
            selectedImages.forEach { item ->
                repository.storage.unmarkPathActive(item.filePath)
                repository.storage.deleteFileSafely(item.filePath)
            }
        }
    }

    val multiPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(40)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                var failedCount = 0
                uris.forEach { uri ->
                    val file = repository.importImageUriToTemp(uri, effects.performanceProfile)
                    if (file != null) {
                        repository.storage.markPathActive(file.absolutePath)
                        selectedImages.add(PhotoPdfItem(UUID.randomUUID().toString(), file.absolutePath))
                    } else {
                        failedCount++
                    }
                }
                if (failedCount > 0) {
                    errorMsg = PrixErrorMapper.map(PrixOperation.IMPORT_IMAGE, null).toInlineMessage()
                }
            }
        }
    }

    LiquidGlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                GlassTopBar(
                    title = "Images to PDF",
                    subtitle = "Batch photo PDF studio",
                    onBack = {
                        if (!isProcessing) onBack() else activeJob?.cancel()
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        GlassButton(
                            text = "Pick Photos",
                            icon = Icons.Filled.AddPhotoAlternate,
                            enabled = !isProcessing,
                            onClick = {
                                multiPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pick_images_button")
                        )
                        GlassButton(
                            text = "Camera Scan",
                            icon = Icons.Filled.CameraAlt,
                            isPrimary = false,
                            enabled = !isProcessing,
                            onClick = onOpenScanner,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (selectedImages.isEmpty()) {
                    item {
                        GlassCard(
                            shape = RoundedCornerShape(22.dp),
                            elevated = true,
                            contentPadding = PaddingValues(24.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Filled.PictureAsPdf,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("Select images to bundle into a PDF", style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Reorder pages, rotate orientation, choose paper size, margins, and image quality.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = prixColors.secondaryText
                                )
                            }
                        }
                    }
                } else {
                    item {
                        Text("${selectedImages.size} Image(s) Selected", style = MaterialTheme.typography.titleSmall)
                    }

                    itemsIndexed(selectedImages, key = { _, item -> item.id }) { idx, item ->
                        GlassCard(
                            shape = RoundedCornerShape(18.dp),
                            contentPadding = PaddingValues(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (effects.shouldAnimate) Modifier.animateItem() else Modifier)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = File(item.filePath),
                                    contentDescription = "Selected photo ${idx + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Page ${idx + 1}", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "Rotation: ${item.rotation}°",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = prixColors.secondaryText
                                    )
                                }
                                IconButton(
                                    enabled = !isProcessing,
                                    onClick = {
                                        selectedImages[idx] = item.copy(rotation = (item.rotation + 90) % 360)
                                    }
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate 90°")
                                }
                                IconButton(
                                    enabled = !isProcessing && idx > 0,
                                    onClick = {
                                        val moved = selectedImages.removeAt(idx)
                                        selectedImages.add(idx - 1, moved)
                                    }
                                ) {
                                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up")
                                }
                                IconButton(
                                    enabled = !isProcessing && idx < selectedImages.lastIndex,
                                    onClick = {
                                        val moved = selectedImages.removeAt(idx)
                                        selectedImages.add(idx + 1, moved)
                                    }
                                ) {
                                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down")
                                }
                                IconButton(
                                    enabled = !isProcessing,
                                    onClick = {
                                        selectedImages.removeAt(idx)
                                        repository.storage.unmarkPathActive(item.filePath)
                                        repository.storage.deleteFileSafely(item.filePath)
                                    }
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove")
                                }
                            }
                        }
                    }
                }

                item {
                    GlassCard(
                        shape = RoundedCornerShape(22.dp),
                        elevated = true,
                        contentPadding = PaddingValues(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = docName,
                            onValueChange = { docName = it },
                            label = { Text("PDF File Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Page Size", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(PdfPageSize.entries) { _, item ->
                                GlassChip(label = item.label, selected = pageSize == item, onClick = { pageSize = item })
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Orientation & Margins", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(PdfOrientation.entries) { _, item ->
                                GlassChip(label = item.label, selected = orientation == item, onClick = { orientation = item })
                            }
                            itemsIndexed(PdfMargin.entries) { _, item ->
                                GlassChip(label = "Margin: ${item.label}", selected = margin == item, onClick = { margin = item })
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Image Quality & Background", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(PdfQuality.entries) { _, item ->
                                GlassChip(label = item.label, selected = quality == item, onClick = { quality = item })
                            }
                            itemsIndexed(PageBackground.entries) { _, item ->
                                GlassChip(label = item.label, selected = background == item, onClick = { background = item })
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = fitToPage, onCheckedChange = { fitToPage = it })
                            Text("Stretch to fill page (unchecked preserves aspect ratio)")
                        }
                    }
                }

                if (errorMsg != null) {
                    item {
                        Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
                    }
                }

                if (isProcessing) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            GlassProgressIndicator(
                                progress = progress,
                                label = "Creating PDF…"
                            )
                            androidx.compose.material3.TextButton(
                                onClick = { activeJob?.cancel() },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }

                item {
                    GlassButton(
                        text = if (isProcessing) "Generating PDF…" else "Create PDF (${selectedImages.size} pages)",
                        enabled = selectedImages.isNotEmpty() && docName.isNotBlank() && !isProcessing,
                        onClick = {
                            activeJob?.cancel()
                            activeJob = scope.launch {
                                isProcessing = true
                                errorMsg = null
                                progress = 0f
                                val rotatedTemps = mutableListOf<String>()
                                val decodeMax = minOf(quality.maxDimensionPx, effects.performanceProfile.imageDecodeMaxDim)
                                try {
                                    val finalPaths = selectedImages.map { item ->
                                        if (item.rotation % 360 == 0) {
                                            item.filePath
                                        } else {
                                            val bmp = ImageProcessor.decodeSampledBitmap(item.filePath, decodeMax)
                                                ?: throw IllegalStateException("Could not decode image.")
                                            val rot = ImageProcessor.rotateBitmap(bmp, item.rotation)
                                            if (rot != bmp) bmp.recycle()
                                            val outTmp = repository.storage.createTempImageFile("rot_")
                                            repository.storage.markPathActive(outTmp.absolutePath)
                                            rotatedTemps.add(outTmp.absolutePath)
                                            ImageProcessor.saveBitmapToFile(rot, outTmp, ImageOutputFormat.JPEG, quality.jpegQuality)
                                            rot.recycle()
                                            outTmp.absolutePath
                                        }
                                    }
                                    val res = repository.createPdfFromImagePaths(
                                        imagePaths = finalPaths,
                                        documentName = docName,
                                        pageSize = pageSize,
                                        orientation = orientation,
                                        margin = margin,
                                        quality = quality,
                                        background = background,
                                        fitToPage = fitToPage,
                                        onProgress = { progress = it }
                                    )
                                    res.onSuccess { created ->
                                        selectedImages.forEach {
                                            repository.storage.unmarkPathActive(it.filePath)
                                            repository.storage.deleteFileSafely(it.filePath)
                                        }
                                        selectedImages.clear()
                                        onCreatedPdf(created)
                                    }.onFailure { err ->
                                        errorMsg = PrixErrorMapper.map(PrixOperation.CREATE_PDF, err).toDisplayMessage()
                                    }
                                } catch (ce: CancellationException) {
                                    errorMsg = PrixErrorMapper.map(PrixOperation.CREATE_PDF, ce).toInlineMessage()
                                } catch (e: Exception) {
                                    errorMsg = PrixErrorMapper.map(PrixOperation.CREATE_PDF, e).toDisplayMessage()
                                } finally {
                                    rotatedTemps.forEach {
                                        repository.storage.unmarkPathActive(it)
                                        repository.storage.deleteFileSafely(it)
                                    }
                                    isProcessing = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_pdf_submit")
                    )
                }
            }
        }
    }
}
