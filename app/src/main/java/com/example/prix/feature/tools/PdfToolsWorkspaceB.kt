package com.example.prix.feature.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.prix.core.data.PrixRepository
import com.example.prix.core.database.DocumentEntity
import com.example.prix.core.error.PrixErrorMapper
import com.example.prix.core.error.PrixOperation
import com.example.prix.core.image.ImageProcessor
import com.example.prix.core.model.DocumentFormat
import com.example.prix.core.model.ImageOutputFormat
import com.example.prix.core.model.OcrResultData
import com.example.prix.core.model.PageNumberPosition
import com.example.prix.core.model.PdfInfoMetadata
import com.example.prix.core.model.ScanFilter
import com.example.prix.core.model.WatermarkPosition
import com.example.prix.core.storage.DocumentStorageManager
import com.example.prix.core.ui.GlassButton
import com.example.prix.core.ui.GlassCard
import com.example.prix.core.ui.GlassChip
import com.example.prix.feature.pdfeditor.SignaturePadDialog
import com.example.ui.theme.LocalPrixColors
import com.example.ui.theme.LocalPrixEffects
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun WatermarkWorkspace(
    selectedDoc: DocumentEntity,
    repository: PrixRepository,
    onDone: (DocumentEntity) -> Unit
) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("CONFIDENTIAL") }
    var opacity by remember { mutableFloatStateOf(0.28f) }
    var fontSize by remember { mutableFloatStateOf(44f) }
    var position by remember { mutableStateOf(WatermarkPosition.DIAGONAL_CENTER) }
    var isBusy by remember { mutableStateOf(false) }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) { onDispose { activeJob?.cancel() } }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(
            shape = RoundedCornerShape(20.dp),
            elevated = true,
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Watermark Text") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text("Position", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(WatermarkPosition.entries) { pos ->
                    GlassChip(label = pos.label, selected = position == pos, onClick = { position = pos })
                }
            }
            Text("Opacity: ${(opacity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp))
            Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 0.1f..0.85f)
            Text("Font Size: ${fontSize.toInt()} pt", style = MaterialTheme.typography.labelMedium)
            Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 18f..72f)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (isBusy) {
            androidx.compose.material3.TextButton(
                onClick = { activeJob?.cancel() },
                modifier = Modifier.align(Alignment.End)
            ) { Text("Cancel") }
        }
        GlassButton(
            text = if (isBusy) "Applying Watermark…" else "Apply Watermark",
            enabled = text.isNotBlank() && !isBusy,
            onClick = {
                activeJob?.cancel()
                activeJob = scope.launch {
                    isBusy = true
                    error = null
                    val out = repository.storage.createVaultFile("${selectedDoc.name}_watermarked", "pdf")
                    try {
                        repository.storage.requireSufficientStorage(4L * 1024L * 1024L)
                        val res = repository.pdfEngine.addWatermark(
                            File(selectedDoc.filePath), text, opacity, fontSize, 0xFF7657F6.toInt(), position, out
                        )
                        res.onSuccess { pages ->
                            onDone(repository.registerCreatedFile(out, out.nameWithoutExtension, DocumentFormat.PDF, pages, selectedDoc.folderId))
                        }.onFailure {
                            repository.storage.deleteFileSafely(out.absolutePath)
                            error = PrixErrorMapper.map(PrixOperation.WATERMARK_PDF, it).toDisplayMessage()
                        }
                    } catch (ce: CancellationException) {
                        repository.storage.deleteFileSafely(out.absolutePath)
                        error = PrixErrorMapper.map(PrixOperation.WATERMARK_PDF, ce).toInlineMessage()
                    } catch (e: Exception) {
                        repository.storage.deleteFileSafely(out.absolutePath)
                        error = PrixErrorMapper.map(PrixOperation.WATERMARK_PDF, e).toDisplayMessage()
                    } finally {
                        isBusy = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun PageNumbersWorkspace(
    selectedDoc: DocumentEntity,
    repository: PrixRepository,
    onDone: (DocumentEntity) -> Unit
) {
    val scope = rememberCoroutineScope()
    var position by remember { mutableStateOf(PageNumberPosition.BOTTOM_CENTER) }
    var includeTotal by remember { mutableStateOf(true) }
    var startFromText by remember { mutableStateOf("1") }
    var isBusy by remember { mutableStateOf(false) }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) { onDispose { activeJob?.cancel() } }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(
            shape = RoundedCornerShape(20.dp),
            elevated = true,
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Placement", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PageNumberPosition.entries) { pos ->
                    GlassChip(label = pos.label, selected = position == pos, onClick = { position = pos })
                }
            }
            OutlinedTextField(
                value = startFromText,
                onValueChange = { startFromText = it.filter { c -> c.isDigit() } },
                label = { Text("Start numbering at") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Checkbox(checked = includeTotal, onCheckedChange = { includeTotal = it })
                Text("Include total page count (e.g. 1 / ${selectedDoc.pageCount})")
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (isBusy) {
            androidx.compose.material3.TextButton(
                onClick = { activeJob?.cancel() },
                modifier = Modifier.align(Alignment.End)
            ) { Text("Cancel") }
        }
        GlassButton(
            text = if (isBusy) "Adding Page Numbers…" else "Add Page Numbers",
            enabled = !isBusy,
            onClick = {
                activeJob?.cancel()
                activeJob = scope.launch {
                    isBusy = true
                    error = null
                    val out = repository.storage.createVaultFile("${selectedDoc.name}_numbered", "pdf")
                    try {
                        repository.storage.requireSufficientStorage(4L * 1024L * 1024L)
                        val startNum = startFromText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                        val res = repository.pdfEngine.addPageNumbers(File(selectedDoc.filePath), position, startNum, includeTotal, out)
                        res.onSuccess { pages ->
                            onDone(repository.registerCreatedFile(out, out.nameWithoutExtension, DocumentFormat.PDF, pages, selectedDoc.folderId))
                        }.onFailure {
                            repository.storage.deleteFileSafely(out.absolutePath)
                            error = PrixErrorMapper.map(PrixOperation.PAGE_NUMBERS_PDF, it).toDisplayMessage()
                        }
                    } catch (ce: CancellationException) {
                        repository.storage.deleteFileSafely(out.absolutePath)
                        error = PrixErrorMapper.map(PrixOperation.PAGE_NUMBERS_PDF, ce).toInlineMessage()
                    } catch (e: Exception) {
                        repository.storage.deleteFileSafely(out.absolutePath)
                        error = PrixErrorMapper.map(PrixOperation.PAGE_NUMBERS_PDF, e).toDisplayMessage()
                    } finally {
                        isBusy = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun PasswordProtectionWorkspace(
    selectedDoc: DocumentEntity,
    addPassword: Boolean,
    repository: PrixRepository,
    onDone: (DocumentEntity) -> Unit
) {
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) { onDispose { activeJob?.cancel() } }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(
            shape = RoundedCornerShape(20.dp),
            elevated = true,
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (addPassword) {
                    "Encrypt a copy of ${selectedDoc.name} with a password (min 4 characters)."
                } else {
                    "Enter the authorized password for ${selectedDoc.name} to create an unlocked copy."
                },
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(if (addPassword) "New Password" else "Current PDF Password") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            )
            if (addPassword) {
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Confirm Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                )
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (isBusy) {
            androidx.compose.material3.TextButton(
                onClick = { activeJob?.cancel() },
                modifier = Modifier.align(Alignment.End)
            ) { Text("Cancel") }
        }
        GlassButton(
            text = if (isBusy) "Processing…" else if (addPassword) "Protect PDF" else "Unlock PDF",
            enabled = password.isNotEmpty() && !isBusy,
            onClick = {
                if (addPassword && password != confirm) {
                    error = "Passwords do not match."
                    return@GlassButton
                }
                val op = if (addPassword) PrixOperation.ENCRYPT_PDF else PrixOperation.DECRYPT_PDF
                activeJob?.cancel()
                activeJob = scope.launch {
                    isBusy = true
                    error = null
                    val out = repository.storage.createVaultFile(
                        if (addPassword) "${selectedDoc.name}_protected" else "${selectedDoc.name}_unlocked",
                        "pdf"
                    )
                    try {
                        repository.storage.requireSufficientStorage(4L * 1024L * 1024L)
                        if (addPassword) {
                            val res = repository.pdfEngine.encryptPdf(File(selectedDoc.filePath), password, password, out)
                            res.onSuccess {
                                onDone(repository.registerCreatedFile(out, out.nameWithoutExtension, DocumentFormat.PDF, selectedDoc.pageCount, selectedDoc.folderId, isEncrypted = true))
                            }.onFailure {
                                repository.storage.deleteFileSafely(out.absolutePath)
                                error = PrixErrorMapper.map(op, it).toDisplayMessage()
                            }
                        } else {
                            val res = repository.pdfEngine.decryptPdf(File(selectedDoc.filePath), password, out)
                            res.onSuccess {
                                val count = repository.pdfEngine.getPageCount(out).coerceAtLeast(1)
                                onDone(repository.registerCreatedFile(out, out.nameWithoutExtension, DocumentFormat.PDF, count, selectedDoc.folderId, isEncrypted = false))
                            }.onFailure {
                                repository.storage.deleteFileSafely(out.absolutePath)
                                error = PrixErrorMapper.map(op, it).toDisplayMessage()
                            }
                        }
                    } catch (ce: CancellationException) {
                        repository.storage.deleteFileSafely(out.absolutePath)
                        error = PrixErrorMapper.map(op, ce).toInlineMessage()
                    } catch (e: Exception) {
                        repository.storage.deleteFileSafely(out.absolutePath)
                        error = PrixErrorMapper.map(op, e).toDisplayMessage()
                    } finally {
                        isBusy = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun PdfInfoWorkspace(
    selectedDoc: DocumentEntity,
    repository: PrixRepository
) {
    val prixColors = LocalPrixColors.current
    var info by remember { mutableStateOf<PdfInfoMetadata?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedDoc.id) {
        val res = repository.inspectPdf(selectedDoc.id)
        res.onSuccess { info = it }.onFailure {
            error = PrixErrorMapper.map(PrixOperation.OPEN_PDF, it).toDisplayMessage()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (info == null && error == null) {
            CircularProgressIndicator()
        } else if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error)
        } else {
            val meta = info!!
            val rows = listOf(
                "Document Name" to selectedDoc.name,
                "File Size" to DocumentStorageManager.formatFileSize(meta.sizeBytes),
                "Page Count" to "${meta.pageCount}",
                "PDF Specification" to meta.pdfVersion,
                "First Page Size" to "${meta.firstPageWidthPt.toInt()} × ${meta.firstPageHeightPt.toInt()} pt",
                "Encrypted" to if (meta.isEncrypted) "Yes" else "No",
                "Title" to meta.title.ifBlank { "—" },
                "Author" to meta.author.ifBlank { "—" },
                "Creator" to meta.creator.ifBlank { "—" },
                "Producer" to meta.producer.ifBlank { "—" },
                "Last Modified" to DocumentStorageManager.formatDateTime(selectedDoc.updatedAt)
            )
            GlassCard(
                shape = RoundedCornerShape(20.dp),
                elevated = true,
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { (k, v) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(k, style = MaterialTheme.typography.bodyMedium, color = prixColors.secondaryText)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(v, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RepairDuplicatePrintWorkspace(
    mode: String,
    selectedDoc: DocumentEntity,
    repository: PrixRepository,
    onDone: (DocumentEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isBusy by remember { mutableStateOf(false) }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) { onDispose { activeJob?.cancel() } }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (mode) {
            "repair" -> {
                GlassCard(shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(14.dp)) {
                    Text("Attempts to parse and rebuild a clean PDF cross-reference table into a new file without altering your original.")
                }
                GlassButton(
                    text = if (isBusy) "Repairing…" else "Repair & Save Copy",
                    enabled = !isBusy,
                    onClick = {
                        activeJob?.cancel()
                        activeJob = scope.launch {
                            isBusy = true
                            message = null
                            val out = repository.storage.createVaultFile("${selectedDoc.name}_repaired", "pdf")
                            try {
                                repository.storage.requireSufficientStorage(4L * 1024L * 1024L)
                                val res = repository.pdfEngine.repairPdf(File(selectedDoc.filePath), out)
                                res.onSuccess { pages ->
                                    onDone(repository.registerCreatedFile(out, out.nameWithoutExtension, DocumentFormat.PDF, pages, selectedDoc.folderId))
                                }.onFailure {
                                    repository.storage.deleteFileSafely(out.absolutePath)
                                    message = PrixErrorMapper.map(PrixOperation.REPAIR_PDF, it).toDisplayMessage()
                                }
                            } catch (ce: CancellationException) {
                                repository.storage.deleteFileSafely(out.absolutePath)
                                message = PrixErrorMapper.map(PrixOperation.REPAIR_PDF, ce).toInlineMessage()
                            } catch (e: Exception) {
                                repository.storage.deleteFileSafely(out.absolutePath)
                                message = PrixErrorMapper.map(PrixOperation.REPAIR_PDF, e).toDisplayMessage()
                            } finally {
                                isBusy = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            "duplicate" -> {
                GlassCard(shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(14.dp)) {
                    Text("Creates an independent copy of ${selectedDoc.name} in your PRIX vault.")
                }
                GlassButton(
                    text = "Duplicate PDF",
                    enabled = !isBusy,
                    onClick = {
                        activeJob?.cancel()
                        activeJob = scope.launch {
                            isBusy = true
                            message = null
                            try {
                                val res = repository.duplicateDocument(selectedDoc.id)
                                res.onSuccess(onDone).onFailure {
                                    message = PrixErrorMapper.map(PrixOperation.CREATE_PDF, it).toDisplayMessage()
                                }
                            } finally {
                                isBusy = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            "print" -> {
                GlassCard(shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(14.dp)) {
                    Text("Opens Android's native system print spooler for ${selectedDoc.name}.")
                }
                GlassButton(
                    text = "Open Print Dialog",
                    icon = Icons.Filled.Print,
                    onClick = {
                        repository.pdfEngine.printPdf(context, File(selectedDoc.filePath), selectedDoc.name)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (isBusy) {
            androidx.compose.material3.TextButton(
                onClick = { activeJob?.cancel() },
                modifier = Modifier.align(Alignment.End)
            ) { Text("Cancel") }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
fun OcrExtractWorkspace(
    initialDoc: DocumentEntity?,
    allDocs: List<DocumentEntity>,
    repository: PrixRepository,
    onSavedTextDoc: (DocumentEntity) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prixColors = LocalPrixColors.current
    val effects = LocalPrixEffects.current
    var selectedDoc by remember { mutableStateOf(initialDoc) }
    var pickedImageFile by remember { mutableStateOf<File?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var ocrResult by remember { mutableStateOf<OcrResultData?>(null) }
    var editableText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            activeJob?.cancel()
            pickedImageFile?.let {
                repository.storage.unmarkPathActive(it.absolutePath)
                repository.storage.deleteFileSafely(it.absolutePath)
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            activeJob?.cancel()
            activeJob = scope.launch {
                val tmp = repository.importImageUriToTemp(uri, effects.performanceProfile)
                if (tmp != null) {
                    pickedImageFile?.let { old ->
                        repository.storage.unmarkPathActive(old.absolutePath)
                        repository.storage.deleteFileSafely(old.absolutePath)
                    }
                    repository.storage.markPathActive(tmp.absolutePath)
                    pickedImageFile = tmp
                    selectedDoc = null
                    isBusy = true
                    error = null
                    try {
                        val res = repository.ocrEngine.extractFromImagePath(
                            tmp.absolutePath,
                            maxDimension = effects.performanceProfile.ocrMaxDim
                        )
                        res.onSuccess {
                            ocrResult = it
                            editableText = it.fullText
                        }.onFailure {
                            error = PrixErrorMapper.map(PrixOperation.OCR_EXTRACT, it).toDisplayMessage()
                        }
                    } catch (ce: CancellationException) {
                        error = PrixErrorMapper.map(PrixOperation.OCR_EXTRACT, ce).toInlineMessage()
                    } finally {
                        isBusy = false
                    }
                } else {
                    error = PrixErrorMapper.map(PrixOperation.IMPORT_IMAGE, null).toInlineMessage()
                }
            }
        }
    }

    LaunchedEffect(initialDoc?.id) {
        if (initialDoc != null && ocrResult == null) {
            isBusy = true
            try {
                val res = repository.runOcrOnDocument(initialDoc, effects.performanceProfile)
                res.onSuccess {
                    ocrResult = it
                    editableText = it.fullText
                }.onFailure {
                    error = PrixErrorMapper.map(PrixOperation.OCR_EXTRACT, it).toDisplayMessage()
                }
            } finally {
                isBusy = false
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "On-device OCR extracts text locally without uploading images. Currently supports Latin-script languages; non-Latin scripts require dedicated models.",
            style = MaterialTheme.typography.bodySmall,
            color = prixColors.secondaryText
        )

        GlassButton(
            text = "Pick Image from Gallery",
            icon = Icons.Filled.PhotoLibrary,
            isPrimary = false,
            enabled = !isBusy,
            onClick = {
                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (allDocs.isNotEmpty()) {
            Text("Or pick from Saved Documents:", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(allDocs.filter { it.format != DocumentFormat.TXT }.take(15), key = { it.id }) { doc ->
                    GlassChip(
                        label = doc.name,
                        selected = selectedDoc?.id == doc.id,
                        onClick = {
                            if (isBusy) return@GlassChip
                            selectedDoc = doc
                            pickedImageFile?.let {
                                repository.storage.unmarkPathActive(it.absolutePath)
                                repository.storage.deleteFileSafely(it.absolutePath)
                            }
                            pickedImageFile = null
                            activeJob?.cancel()
                            activeJob = scope.launch {
                                isBusy = true
                                error = null
                                try {
                                    val res = repository.runOcrOnDocument(doc, effects.performanceProfile)
                                    res.onSuccess {
                                        ocrResult = it
                                        editableText = it.fullText
                                    }.onFailure {
                                        error = PrixErrorMapper.map(PrixOperation.OCR_EXTRACT, it).toDisplayMessage()
                                    }
                                } catch (ce: CancellationException) {
                                    error = PrixErrorMapper.map(PrixOperation.OCR_EXTRACT, ce).toInlineMessage()
                                } finally {
                                    isBusy = false
                                }
                            }
                        }
                    )
                }
            }
        }

        if (isBusy) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp))
                Text("Extracting text on-device…", modifier = Modifier.weight(1f))
                androidx.compose.material3.TextButton(onClick = { activeJob?.cancel() }) { Text("Cancel") }
            }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        ocrResult?.let { data ->
            data.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = prixColors.secondaryText) }
            OutlinedTextField(
                value = editableText,
                onValueChange = { editableText = it },
                label = { Text("Extracted Text (selectable & editable)") },
                minLines = 6,
                maxLines = 14,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    enabled = editableText.isNotBlank(),
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        if (cm != null) {
                            cm.setPrimaryClip(ClipData.newPlainText("PRIX OCR Text", editableText))
                            onShowMessage("Copied text to clipboard.")
                        } else {
                            onShowMessage("Clipboard unavailable on this device.")
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy")
                }
                OutlinedButton(
                    enabled = editableText.isNotBlank(),
                    onClick = {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, editableText)
                        }
                        try {
                            context.startActivity(Intent.createChooser(share, "Share extracted text"))
                        } catch (_: Exception) {
                            onShowMessage("No app available to share text.")
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Share, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share")
                }
                Button(
                    enabled = editableText.isNotBlank(),
                    onClick = {
                        scope.launch {
                            val title = (selectedDoc?.name ?: "OCR_Extract") + "_text"
                            val res = repository.saveOcrAsTextDocument(title, editableText, selectedDoc?.id)
                            res.onSuccess(onSavedTextDoc).onFailure {
                                error = PrixErrorMapper.map(PrixOperation.OCR_EXTRACT, it).toDisplayMessage()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Save, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save TXT")
                }
            }
        }
    }
}

@Composable
fun ImageToolkitWorkspace(
    initialDoc: DocumentEntity?,
    imageDocs: List<DocumentEntity>,
    repository: PrixRepository,
    onSavedImage: (DocumentEntity) -> Unit
) {
    val scope = rememberCoroutineScope()
    val prixColors = LocalPrixColors.current
    val effects = LocalPrixEffects.current
    var sourceDoc by remember { mutableStateOf(initialDoc) }
    var workingPath by remember { mutableStateOf(initialDoc?.filePath) }
    var tempPickedPath by remember { mutableStateOf<String?>(null) }

    var filter by remember { mutableStateOf(ScanFilter.ORIGINAL) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableIntStateOf(0) }
    var marginCropPct by remember { mutableFloatStateOf(0f) }
    var outputFormat by remember { mutableStateOf(ImageOutputFormat.JPEG) }
    var quality by remember { mutableFloatStateOf(88f) }
    var scalePercent by remember { mutableFloatStateOf(100f) }
    var overwrite by remember { mutableStateOf(false) }

    var previewBmp by remember { mutableStateOf<Bitmap?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            activeJob?.cancel()
            val bmp = previewBmp
            if (bmp != null && !bmp.isRecycled) bmp.recycle()
            tempPickedPath?.let {
                repository.storage.unmarkPathActive(it)
                repository.storage.deleteFileSafely(it)
            }
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val tmp = repository.importImageUriToTemp(uri, effects.performanceProfile)
                if (tmp != null) {
                    tempPickedPath?.let { old ->
                        repository.storage.unmarkPathActive(old)
                        repository.storage.deleteFileSafely(old)
                    }
                    repository.storage.markPathActive(tmp.absolutePath)
                    tempPickedPath = tmp.absolutePath
                    sourceDoc = null
                    workingPath = tmp.absolutePath
                    error = null
                } else {
                    error = PrixErrorMapper.map(PrixOperation.IMPORT_IMAGE, null).toInlineMessage()
                }
            }
        }
    }

    val previewMaxDim = effects.performanceProfile.previewDecodeMaxDim
    LaunchedEffect(workingPath, filter, brightness, contrast, rotation, marginCropPct, previewMaxDim) {
        val path = workingPath ?: return@LaunchedEffect
        delay(35)
        val raw = ImageProcessor.decodeSampledBitmap(path, previewMaxDim) ?: return@LaunchedEffect
        val cropped = if (marginCropPct > 0.01f) {
            ImageProcessor.cropBitmapNormalized(raw, marginCropPct, marginCropPct, 1f - marginCropPct, 1f - marginCropPct)
                .also { if (it !== raw && !raw.isRecycled) raw.recycle() }
        } else raw
        val rot = ImageProcessor.rotateBitmap(cropped, rotation)
        if (rot !== cropped && !cropped.isRecycled) cropped.recycle()
        val filtered = ImageProcessor.applyFilterAndAdjustments(rot, filter, brightness, contrast)
        if (filtered !== rot && !rot.isRecycled) rot.recycle()
        val old = previewBmp
        previewBmp = filtered
        if (old != null && old !== filtered && !old.isRecycled) {
            old.recycle()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassButton(
            text = "Pick Image from Gallery",
            icon = Icons.Filled.PhotoLibrary,
            isPrimary = false,
            enabled = !isBusy,
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            modifier = Modifier.fillMaxWidth()
        )

        if (imageDocs.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(imageDocs.take(15), key = { it.id }) { imgDoc ->
                    GlassChip(
                        label = imgDoc.name,
                        selected = sourceDoc?.id == imgDoc.id,
                        onClick = {
                            if (!isBusy) {
                                sourceDoc = imgDoc
                                workingPath = imgDoc.filePath
                            }
                        }
                    )
                }
            }
        }

        val currentBmp = previewBmp
        if (currentBmp != null && !currentBmp.isRecycled) {
            Image(
                bitmap = currentBmp.asImageBitmap(),
                contentDescription = "Edited image preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
        }

        if (workingPath != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !isBusy, onClick = { rotation = (rotation + 90) % 360 }) {
                    Icon(Icons.AutoMirrored.Filled.RotateRight, null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Rotate 90°")
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ScanFilter.entries) { f ->
                    GlassChip(label = f.label, selected = filter == f, onClick = { filter = f })
                }
            }

            GlassCard(
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Brightness: ${brightness.toInt()}", style = MaterialTheme.typography.labelSmall, color = prixColors.secondaryText)
                Slider(value = brightness, onValueChange = { brightness = it }, valueRange = -55f..55f)

                Text("Contrast: ${String.format(Locale.US, "%.2f", contrast)}x", style = MaterialTheme.typography.labelSmall, color = prixColors.secondaryText)
                Slider(value = contrast, onValueChange = { contrast = it }, valueRange = 0.6f..1.6f)

                Text("Edge Crop Inset: ${(marginCropPct * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = prixColors.secondaryText)
                Slider(value = marginCropPct, onValueChange = { marginCropPct = it }, valueRange = 0f..0.25f)

                Text("Resize Scale: ${scalePercent.toInt()}%", style = MaterialTheme.typography.labelSmall, color = prixColors.secondaryText)
                Slider(value = scalePercent, onValueChange = { scalePercent = it }, valueRange = 25f..100f)

                Text("Compression Quality: ${quality.toInt()}%", style = MaterialTheme.typography.labelSmall, color = prixColors.secondaryText)
                Slider(value = quality, onValueChange = { quality = it }, valueRange = 25f..100f)

                Text("Output Format", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImageOutputFormat.entries.forEach { fmt ->
                        GlassChip(label = fmt.label, selected = outputFormat == fmt, onClick = { outputFormat = fmt })
                    }
                }

                if (sourceDoc != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Checkbox(checked = overwrite, onCheckedChange = { overwrite = it })
                        Text("Overwrite original image")
                    }
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (isBusy) {
                androidx.compose.material3.TextButton(
                    onClick = { activeJob?.cancel() },
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Cancel") }
            }

            GlassButton(
                text = if (isBusy) "Saving Image…" else "Save Processed Image",
                enabled = !isBusy,
                onClick = {
                    val path = workingPath ?: return@GlassButton
                    activeJob?.cancel()
                    activeJob = scope.launch {
                        isBusy = true
                        error = null
                        try {
                            val base = ImageProcessor.decodeSampledBitmap(path, effects.performanceProfile.imageDecodeMaxDim)
                            val targetW = if (base != null && scalePercent < 99f) (base.width * scalePercent / 100f).toInt() else null
                            val targetH = if (base != null && scalePercent < 99f) (base.height * scalePercent / 100f).toInt() else null
                            base?.recycle()
                            val cropBounds = if (marginCropPct > 0.01f) {
                                floatArrayOf(marginCropPct, marginCropPct, 1f - marginCropPct, 1f - marginCropPct)
                            } else null
                            val res = repository.saveEditedImage(
                                sourceImagePath = path,
                                outputName = (sourceDoc?.name ?: "Image_${DocumentStorageManager.timestampTag()}") + "_edited",
                                format = outputFormat,
                                quality = quality.toInt(),
                                filter = filter,
                                brightness = brightness,
                                contrast = contrast,
                                rotationDegrees = rotation,
                                targetWidth = targetW,
                                targetHeight = targetH,
                                cropBounds = cropBounds,
                                overwriteDocId = if (overwrite) sourceDoc?.id else null,
                                performanceProfile = effects.performanceProfile
                            )
                            res.onSuccess(onSavedImage).onFailure {
                                error = PrixErrorMapper.map(PrixOperation.IMAGE_TOOLKIT, it).toDisplayMessage()
                            }
                        } catch (ce: CancellationException) {
                            error = PrixErrorMapper.map(PrixOperation.IMAGE_TOOLKIT, ce).toInlineMessage()
                        } catch (e: Exception) {
                            error = PrixErrorMapper.map(PrixOperation.IMAGE_TOOLKIT, e).toDisplayMessage()
                        } finally {
                            isBusy = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun SignatureLibraryWorkspace(
    repository: PrixRepository,
    onShowMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val prixColors = LocalPrixColors.current
    val savedSignatures by repository.allSignatures.collectAsStateWithLifecycle(initialValue = emptyList())
    var showCreatePad by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Manage your reusable handwritten signatures. Signatures created here appear automatically inside the PDF Editor.",
            style = MaterialTheme.typography.bodySmall,
            color = prixColors.secondaryText
        )

        GlassButton(
            text = "Create New Signature",
            icon = Icons.Filled.Add,
            onClick = { showCreatePad = true },
            modifier = Modifier.fillMaxWidth()
        )

        if (savedSignatures.isEmpty()) {
            GlassCard(
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Draw, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("No saved signatures yet. Tap Create New Signature to draw or import one.")
                }
            }
        } else {
            savedSignatures.forEach { sig ->
                GlassCard(
                    shape = RoundedCornerShape(18.dp),
                    elevated = true,
                    contentPadding = PaddingValues(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(width = 104.dp, height = 52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = File(sig.imagePath),
                                contentDescription = sig.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sig.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                DocumentStorageManager.formatDateShort(sig.createdAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = prixColors.secondaryText
                            )
                        }
                        IconButton(onClick = {
                            scope.launch {
                                repository.deleteSignature(sig)
                                onShowMessage("Removed signature ${sig.name}")
                            }
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete signature", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        Text(
            "Disclaimer: Visual signatures saved in PRIX are overlay graphics and are not cryptographic digital certificates.",
            style = MaterialTheme.typography.bodySmall,
            color = prixColors.secondaryText
        )
    }

    if (showCreatePad) {
        SignaturePadDialog(
            repository = repository,
            savedSignatures = savedSignatures,
            onDismiss = { showCreatePad = false },
            onSignatureSelected = {
                showCreatePad = false
                onShowMessage("Signature saved to library.")
            }
        )
    }
}
