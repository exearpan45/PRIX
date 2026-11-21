package com.example.prix.feature.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import com.example.prix.core.data.PrixRepository
import com.example.prix.core.database.DocumentEntity
import com.example.prix.core.error.PrixErrorMapper
import com.example.prix.core.error.PrixOperation
import com.example.prix.core.model.CompressionLevel
import com.example.prix.core.model.CompressionResult
import com.example.prix.core.model.DocumentFormat
import com.example.prix.core.model.ImageOutputFormat
import com.example.prix.core.model.PageTransformItem
import com.example.prix.core.storage.DocumentStorageManager
import com.example.prix.core.ui.GlassButton
import com.example.prix.core.ui.GlassCard
import com.example.prix.core.ui.GlassChip
import com.example.prix.core.ui.GlassProgressIndicator
import com.example.prix.ui.navigation.ToolIds
import com.example.ui.theme.LocalPrixColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun MergePdfWorkspace(
    pdfDocs: List<DocumentEntity>,
    repository: PrixRepository,
    onDone: (DocumentEntity) -> Unit
) {
    val scope = rememberCoroutineScope()
    val prixColors = LocalPrixColors.current
    val selectedOrder = remember { mutableStateListOf<DocumentEntity>() }
    var outName by remember { mutableStateOf("Merged_${DocumentStorageManager.timestampTag()}") }
    var isBusy by remember { mutableStateOf(false) }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { activeJob?.cancel() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("1. Select 2 or more PDFs in order", style = MaterialTheme.typography.titleSmall)
        GlassCard(
            shape = RoundedCornerShape(20.dp),
            elevated = true,
            contentPadding = PaddingValues(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = outName,
                onValueChange = { outName = it },
                label = { Text("Output PDF Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (selectedOrder.isNotEmpty()) {
            Text("Merge Order (${selectedOrder.size})", style = MaterialTheme.typography.labelLarge)
            selectedOrder.forEachIndexed { idx, doc ->
                GlassCard(
                    shape = RoundedCornerShape(16.dp),
                    tintOverlay = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${idx + 1}. ${doc.name}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        IconButton(enabled = !isBusy && idx > 0, onClick = {
                            val item = selectedOrder.removeAt(idx)
                            selectedOrder.add(idx - 1, item)
                        }) { Icon(Icons.Filled.ArrowUpward, "Move up") }
                        IconButton(enabled = !isBusy && idx < selectedOrder.lastIndex, onClick = {
                            val item = selectedOrder.removeAt(idx)
                            selectedOrder.add(idx + 1, item)
                        }) { Icon(Icons.Filled.ArrowDownward, "Move down") }
                    }
                }
            }
        }

        Text("Available PDFs", style = MaterialTheme.typography.labelLarge)
        pdfDocs.forEach { doc ->
            val checked = selectedOrder.any { it.id == doc.id }
            GlassCard(
                shape = RoundedCornerShape(16.dp),
                borderColor = if (checked) MaterialTheme.colorScheme.primary else null,
                onClick = {
                    if (!isBusy) {
                        if (checked) selectedOrder.removeAll { it.id == doc.id }
                        else selectedOrder.add(doc)
                    }
                },
                contentPadding = PaddingValues(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = checked,
                        enabled = !isBusy,
                        onCheckedChange = {
                            if (checked) selectedOrder.removeAll { it.id == doc.id } else selectedOrder.add(doc)
                        }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(doc.name, style = MaterialTheme.typography.titleSmall)
                        Text("${doc.pageCount} pages • ${DocumentStorageManager.formatFileSize(doc.sizeBytes)}", style = MaterialTheme.typography.bodySmall, color = prixColors.secondaryText)
                    }
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
            text = if (isBusy) "Merging PDFs…" else "Merge ${selectedOrder.size} PDFs",
            enabled = selectedOrder.size >= 2 && outName.isNotBlank() && !isBusy,
            onClick = {
                activeJob?.cancel()
                activeJob = scope.launch {
                    isBusy = true
                    error = null
                    try {
                        val res = repository.mergePdfs(selectedOrder.map { it.id }, outName)
                        res.onSuccess(onDone).onFailure {
                            error = PrixErrorMapper.map(PrixOperation.MERGE_PDF, it).toDisplayMessage()
                        }
                    } catch (ce: CancellationException) {
                        error = PrixErrorMapper.map(PrixOperation.MERGE_PDF, ce).toInlineMessage()
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
fun SplitPdfWorkspace(
    selectedDoc: DocumentEntity,
    repository: PrixRepository,
    onDoneMany: (List<DocumentEntity>) -> Unit
) {
    val scope = rememberCoroutineScope()
    var splitEveryPage by remember { mutableStateOf(true) }
    var rangeText by remember { mutableStateOf("1-${selectedDoc.pageCount.coerceAtLeast(1)}") }
    var isBusy by remember { mutableStateOf(false) }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { activeJob?.cancel() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(
            shape = RoundedCornerShape(20.dp),
            elevated = true,
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Selected: ${selectedDoc.name} (${selectedDoc.pageCount} pages)", style = MaterialTheme.typography.titleSmall)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 10.dp)
            ) {
                GlassChip(label = "Split Every Page", selected = splitEveryPage, onClick = { splitEveryPage = true })
                GlassChip(label = "Custom Ranges", selected = !splitEveryPage, onClick = { splitEveryPage = false })
            }
            if (!splitEveryPage) {
                OutlinedTextField(
                    value = rangeText,
                    onValueChange = { rangeText = it },
                    label = { Text("Ranges (e.g. 1-2, 3-5)") },
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
            text = if (isBusy) "Splitting PDF…" else "Split PDF",
            enabled = !isBusy,
            onClick = {
                val ranges = if (splitEveryPage) emptyList() else parsePageRanges(rangeText, selectedDoc.pageCount)
                if (!splitEveryPage && ranges.isEmpty()) {
                    error = "Enter valid page ranges between 1 and ${selectedDoc.pageCount} (e.g. 1-2, 3-4)."
                    return@GlassButton
                }
                activeJob?.cancel()
                activeJob = scope.launch {
                    isBusy = true
                    error = null
                    try {
                        val res = repository.splitPdf(selectedDoc.id, ranges, splitEveryPage)
                        res.onSuccess(onDoneMany).onFailure {
                            error = PrixErrorMapper.map(PrixOperation.SPLIT_PDF, it).toDisplayMessage()
                        }
                    } catch (ce: CancellationException) {
                        error = PrixErrorMapper.map(PrixOperation.SPLIT_PDF, ce).toInlineMessage()
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
fun CompressPdfWorkspace(
    selectedDoc: DocumentEntity,
    repository: PrixRepository,
    onOpenDoc: (Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    val prixColors = LocalPrixColors.current
    var level by remember { mutableStateOf(CompressionLevel.BALANCED) }
    var overwrite by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var result by remember { mutableStateOf<CompressionResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { activeJob?.cancel() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Original: ${selectedDoc.name} (${DocumentStorageManager.formatFileSize(selectedDoc.sizeBytes)})",
            style = MaterialTheme.typography.titleSmall
        )
        CompressionLevel.entries.forEach { item ->
            GlassCard(
                shape = RoundedCornerShape(18.dp),
                elevated = level == item,
                tintOverlay = if (level == item) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else null,
                borderColor = if (level == item) MaterialTheme.colorScheme.primary else null,
                onClick = { if (!isBusy) level = item },
                contentPadding = PaddingValues(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(item.label, style = MaterialTheme.typography.titleSmall)
                Text(item.description, style = MaterialTheme.typography.bodySmall, color = prixColors.secondaryText)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = overwrite, enabled = !isBusy, onCheckedChange = { overwrite = it })
            Text("Replace original file if smaller (otherwise saves a new copy)")
        }

        if (isBusy) {
            GlassProgressIndicator(progress = progress, label = "Optimizing pages…")
            androidx.compose.material3.TextButton(
                onClick = { activeJob?.cancel() },
                modifier = Modifier.align(Alignment.End)
            ) { Text("Cancel") }
        }

        result?.let { comp ->
            GlassCard(
                shape = RoundedCornerShape(18.dp),
                tintOverlay = (if (comp.reduced) prixColors.success else prixColors.warning).copy(alpha = 0.14f),
                borderColor = if (comp.reduced) prixColors.success else prixColors.warning,
                contentPadding = PaddingValues(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(comp.message, style = MaterialTheme.typography.titleSmall)
                Text(
                    "Original: ${DocumentStorageManager.formatFileSize(comp.originalBytes)} → Output: ${DocumentStorageManager.formatFileSize(comp.outputBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
                comp.savedDocumentId?.let { id ->
                    OutlinedButton(onClick = { onOpenDoc(id) }, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Open Compressed PDF")
                    }
                }
            }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        GlassButton(
            text = if (isBusy) "Compressing…" else "Compress PDF",
            enabled = !isBusy,
            onClick = {
                activeJob?.cancel()
                activeJob = scope.launch {
                    isBusy = true
                    progress = 0f
                    error = null
                    result = null
                    try {
                        val res = repository.compressPdf(selectedDoc.id, level, overwrite) { progress = it }
                        res.onSuccess { result = it }.onFailure {
                            error = PrixErrorMapper.map(PrixOperation.COMPRESS_PDF, it).toDisplayMessage()
                        }
                    } catch (ce: CancellationException) {
                        error = PrixErrorMapper.map(PrixOperation.COMPRESS_PDF, ce).toInlineMessage()
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
fun PageOrganizeWorkspace(
    toolId: String,
    selectedDoc: DocumentEntity,
    repository: PrixRepository,
    onDone: (DocumentEntity) -> Unit
) {
    val scope = rememberCoroutineScope()
    val prixColors = LocalPrixColors.current
    val pages = remember { mutableStateListOf<PageTransformItem>() }
    val selectedPageIndices = remember { mutableStateListOf<Int>() }
    var isBusy by remember { mutableStateOf(false) }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { activeJob?.cancel() }
    }

    LaunchedEffect(selectedDoc.id) {
        val count = repository.pdfEngine.getPageCount(File(selectedDoc.filePath))
        pages.clear()
        selectedPageIndices.clear()
        for (i in 0 until count) {
            pages.add(PageTransformItem(i, 0))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val instruction = when (toolId) {
            ToolIds.ROTATE_PDF -> "Tap rotate on individual pages or rotate all pages below."
            ToolIds.REORDER_PAGES -> "Move pages up or down to set the final page sequence."
            ToolIds.DELETE_PAGES -> "Check pages to remove, then save the updated PDF."
            ToolIds.EXTRACT_PAGES -> "Check pages to extract into a new standalone PDF."
            else -> ""
        }
        Text(instruction, style = MaterialTheme.typography.bodyMedium, color = prixColors.secondaryText)

        if (toolId == ToolIds.ROTATE_PDF) {
            OutlinedButton(
                enabled = !isBusy,
                onClick = {
                    for (i in pages.indices) {
                        pages[i] = pages[i].copy(extraRotationDegrees = (pages[i].extraRotationDegrees + 90) % 360)
                    }
                }
            ) {
                Icon(Icons.AutoMirrored.Filled.RotateRight, null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Rotate All Pages +90°")
            }
        }

        pages.forEachIndexed { idx, item ->
            val checked = idx in selectedPageIndices
            GlassCard(
                shape = RoundedCornerShape(16.dp),
                borderColor = if (checked) MaterialTheme.colorScheme.primary else null,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (toolId == ToolIds.DELETE_PAGES || toolId == ToolIds.EXTRACT_PAGES) {
                        Checkbox(checked = checked, enabled = !isBusy, onCheckedChange = {
                            if (checked) selectedPageIndices.remove(idx) else selectedPageIndices.add(idx)
                        })
                    }
                    Text(
                        text = "Page ${item.originalPageIndex + 1}" +
                            if (item.extraRotationDegrees != 0) " (${item.extraRotationDegrees}°)" else "",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (toolId == ToolIds.ROTATE_PDF) {
                        IconButton(
                            enabled = !isBusy,
                            onClick = {
                                pages[idx] = item.copy(extraRotationDegrees = (item.extraRotationDegrees + 90) % 360)
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate 90°")
                        }
                    }
                    if (toolId == ToolIds.REORDER_PAGES) {
                        IconButton(enabled = !isBusy && idx > 0, onClick = {
                            val moved = pages.removeAt(idx)
                            pages.add(idx - 1, moved)
                        }) { Icon(Icons.Filled.ArrowUpward, "Move up") }
                        IconButton(enabled = !isBusy && idx < pages.lastIndex, onClick = {
                            val moved = pages.removeAt(idx)
                            pages.add(idx + 1, moved)
                        }) { Icon(Icons.Filled.ArrowDownward, "Move down") }
                    }
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
            text = if (isBusy) "Processing Pages…" else "Save Resulting PDF",
            enabled = pages.isNotEmpty() && !isBusy,
            onClick = {
                val finalPlan = when (toolId) {
                    ToolIds.DELETE_PAGES -> pages.filterIndexed { idx, _ -> idx !in selectedPageIndices }
                    ToolIds.EXTRACT_PAGES -> pages.filterIndexed { idx, _ -> idx in selectedPageIndices }
                    else -> pages.toList()
                }
                if (finalPlan.isEmpty()) {
                    error = "Resulting PDF must contain at least 1 page."
                    return@GlassButton
                }
                val suffix = when (toolId) {
                    ToolIds.ROTATE_PDF -> "rotated"
                    ToolIds.REORDER_PAGES -> "reordered"
                    ToolIds.DELETE_PAGES -> "trimmed"
                    else -> "extracted"
                }
                activeJob?.cancel()
                activeJob = scope.launch {
                    isBusy = true
                    error = null
                    val out = repository.storage.createVaultFile("${selectedDoc.name}_$suffix", "pdf")
                    try {
                        repository.storage.requireSufficientStorage(4L * 1024L * 1024L)
                        val res = repository.pdfEngine.rebuildPdfWithPagePlan(File(selectedDoc.filePath), finalPlan, out)
                        res.onSuccess { count ->
                            val created = repository.registerCreatedFile(out, out.nameWithoutExtension, DocumentFormat.PDF, count, selectedDoc.folderId)
                            onDone(created)
                        }.onFailure {
                            repository.storage.deleteFileSafely(out.absolutePath)
                            error = PrixErrorMapper.map(PrixOperation.ORGANIZE_PDF, it).toDisplayMessage()
                        }
                    } catch (ce: CancellationException) {
                        repository.storage.deleteFileSafely(out.absolutePath)
                        error = PrixErrorMapper.map(PrixOperation.ORGANIZE_PDF, ce).toInlineMessage()
                    } catch (e: Exception) {
                        repository.storage.deleteFileSafely(out.absolutePath)
                        error = PrixErrorMapper.map(PrixOperation.ORGANIZE_PDF, e).toDisplayMessage()
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
fun PdfToImagesWorkspace(
    selectedDoc: DocumentEntity,
    repository: PrixRepository,
    onDoneMany: (List<DocumentEntity>) -> Unit
) {
    val scope = rememberCoroutineScope()
    var format by remember { mutableStateOf(ImageOutputFormat.PNG) }
    var dpi by remember { mutableIntStateOf(150) }
    var isBusy by remember { mutableStateOf(false) }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { activeJob?.cancel() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(
            shape = RoundedCornerShape(20.dp),
            elevated = true,
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Export all ${selectedDoc.pageCount} pages of ${selectedDoc.name} as images.", style = MaterialTheme.typography.titleSmall)
            Text("Format", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImageOutputFormat.entries.forEach { f ->
                    GlassChip(label = f.label, selected = format == f, onClick = { if (!isBusy) format = f })
                }
            }
            Text("Resolution", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(100 to "Standard (100 DPI)", 150 to "Crisp (150 DPI)", 220 to "High (220 DPI)")) { (d, label) ->
                    GlassChip(label = label, selected = dpi == d, onClick = { if (!isBusy) dpi = d })
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
            text = if (isBusy) "Rendering Images…" else "Convert to ${format.label} Images",
            enabled = !isBusy,
            onClick = {
                activeJob?.cancel()
                activeJob = scope.launch {
                    isBusy = true
                    error = null
                    try {
                        repository.storage.requireSufficientStorage(8L * 1024L * 1024L)
                        val res = repository.pdfEngine.pdfToImages(File(selectedDoc.filePath), format, dpi) { p ->
                            repository.storage.createVaultFile("${selectedDoc.name}_p$p", format.extension)
                        }
                        res.onSuccess { files ->
                            val docFmt = when (format) {
                                ImageOutputFormat.PNG -> DocumentFormat.PNG
                                ImageOutputFormat.WEBP -> DocumentFormat.WEBP
                                ImageOutputFormat.JPEG -> DocumentFormat.JPEG
                            }
                            val entities = files.map { f ->
                                repository.registerCreatedFile(f, f.nameWithoutExtension, docFmt, 1, selectedDoc.folderId)
                            }
                            onDoneMany(entities)
                        }.onFailure {
                            error = PrixErrorMapper.map(PrixOperation.PDF_TO_IMAGES, it).toDisplayMessage()
                        }
                    } catch (ce: CancellationException) {
                        error = PrixErrorMapper.map(PrixOperation.PDF_TO_IMAGES, ce).toInlineMessage()
                    } catch (e: Exception) {
                        error = PrixErrorMapper.map(PrixOperation.PDF_TO_IMAGES, e).toDisplayMessage()
                    } finally {
                        isBusy = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

internal fun parsePageRanges(raw: String, maxPages: Int): List<IntRange> {
    if (raw.isBlank() || maxPages <= 0) return emptyList()
    val result = mutableListOf<IntRange>()
    val tokens = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return emptyList()
    for (clean in tokens) {
        if (clean.contains('-')) {
            val parts = clean.split('-')
            if (parts.size != 2) return emptyList()
            val a = parts[0].trim().toIntOrNull() ?: return emptyList()
            val b = parts[1].trim().toIntOrNull() ?: return emptyList()
            if (a !in 1..maxPages || b !in a..maxPages) return emptyList()
            result.add(a..b)
        } else {
            val single = clean.toIntOrNull() ?: return emptyList()
            if (single !in 1..maxPages) return emptyList()
            result.add(single..single)
        }
    }
    return result
}
