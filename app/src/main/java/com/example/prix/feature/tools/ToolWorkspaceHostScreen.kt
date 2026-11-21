package com.example.prix.feature.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.prix.core.data.PrixRepository
import com.example.prix.core.database.DocumentEntity
import com.example.prix.core.model.DocumentFormat
import com.example.prix.core.storage.DocumentStorageManager
import com.example.prix.core.ui.GlassButton
import com.example.prix.core.ui.GlassCard
import com.example.prix.core.ui.GlassTopBar
import com.example.prix.core.ui.LiquidGlassBackground
import com.example.prix.feature.pdfeditor.PdfEditorScreen
import com.example.prix.ui.navigation.ToolIds
import com.example.ui.theme.LocalPrixColors

@Composable
fun ToolWorkspaceHostScreen(
    toolId: String,
    initialDocumentId: Long?,
    repository: PrixRepository,
    onBack: () -> Unit,
    onOpenViewer: (Long) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val validDocs by repository.validDocuments.collectAsStateWithLifecycle(initialValue = emptyList())
    val pdfDocs = remember(validDocs) {
        validDocs.filter { it.format == DocumentFormat.PDF }
    }
    val imageDocs = remember(validDocs) {
        validDocs.filter { it.format in listOf(DocumentFormat.PNG, DocumentFormat.JPEG, DocumentFormat.WEBP) }
    }

    var selectedDoc by remember(initialDocumentId, validDocs) {
        mutableStateOf(validDocs.firstOrNull { it.id == initialDocumentId })
    }
    var completedDocs by remember { mutableStateOf<List<DocumentEntity>>(emptyList()) }

    if (toolId == ToolIds.ADD_SIGNATURE && selectedDoc != null) {
        PdfEditorScreen(
            documentId = selectedDoc!!.id,
            repository = repository,
            startWithSignature = true,
            onBack = onBack,
            onSavedDocument = { saved -> onOpenViewer(saved.id) }
        )
        return
    }

    val title = remember(toolId) {
        when (toolId) {
            ToolIds.MERGE_PDF -> "Merge PDFs"
            ToolIds.SPLIT_PDF -> "Split PDF"
            ToolIds.COMPRESS_PDF -> "Compress PDF"
            ToolIds.ROTATE_PDF -> "Rotate PDF"
            ToolIds.REORDER_PAGES -> "Reorder Pages"
            ToolIds.DELETE_PAGES -> "Delete Pages"
            ToolIds.EXTRACT_PAGES -> "Extract Pages"
            ToolIds.PDF_TO_IMAGES -> "PDF to Images"
            ToolIds.ADD_WATERMARK -> "Add Watermark"
            ToolIds.ADD_PAGE_NUMBERS -> "Add Page Numbers"
            ToolIds.ADD_PASSWORD -> "Protect PDF"
            ToolIds.REMOVE_PASSWORD -> "Unlock PDF"
            ToolIds.ADD_SIGNATURE -> "Sign PDF"
            ToolIds.PDF_INFO -> "PDF Information"
            ToolIds.REPAIR_PDF -> "Repair PDF"
            ToolIds.DUPLICATE_PDF -> "Duplicate PDF"
            ToolIds.PRINT_PDF -> "Print PDF"
            ToolIds.OCR_EXTRACT -> "OCR Text Extraction"
            ToolIds.IMAGE_TOOLKIT -> "Image Toolkit"
            ToolIds.SIGNATURE_LIBRARY -> "Signature Manager"
            else -> "PDF Tool"
        }
    }

    val needsSinglePdfFirst = toolId !in listOf(
        ToolIds.MERGE_PDF,
        ToolIds.OCR_EXTRACT,
        ToolIds.IMAGE_TOOLKIT,
        ToolIds.SIGNATURE_LIBRARY
    )

    LiquidGlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                GlassTopBar(title = title, subtitle = "Local on-device processing", onBack = onBack)
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (completedDocs.isNotEmpty()) {
                    val prixColors = LocalPrixColors.current
                    GlassCard(
                        shape = RoundedCornerShape(22.dp),
                        elevated = true,
                        tintOverlay = prixColors.success.copy(alpha = 0.12f),
                        borderColor = prixColors.success,
                        contentPadding = PaddingValues(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = prixColors.success)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Created ${completedDocs.size} file(s) successfully!",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        completedDocs.forEach { created ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(created.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                if (created.format == DocumentFormat.PDF) {
                                    OutlinedButton(onClick = { onOpenViewer(created.id) }) {
                                        Text("Open")
                                    }
                                }
                            }
                        }
                        GlassButton(
                            text = "Done",
                            onClick = onBack,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        )
                    }
                } else if (needsSinglePdfFirst && selectedDoc == null) {
                    PdfSelectorList(
                        pdfDocs = pdfDocs,
                        onSelect = { selectedDoc = it }
                    )
                } else {
                    when (toolId) {
                        ToolIds.MERGE_PDF -> MergePdfWorkspace(pdfDocs, repository) { completedDocs = listOf(it) }
                        ToolIds.SPLIT_PDF -> SplitPdfWorkspace(selectedDoc!!, repository) { completedDocs = it }
                        ToolIds.COMPRESS_PDF -> CompressPdfWorkspace(selectedDoc!!, repository, onOpenViewer)
                        ToolIds.ROTATE_PDF, ToolIds.REORDER_PAGES, ToolIds.DELETE_PAGES, ToolIds.EXTRACT_PAGES ->
                            PageOrganizeWorkspace(toolId, selectedDoc!!, repository) { completedDocs = listOf(it) }
                        ToolIds.PDF_TO_IMAGES -> PdfToImagesWorkspace(selectedDoc!!, repository) { completedDocs = it }
                        ToolIds.ADD_WATERMARK -> WatermarkWorkspace(selectedDoc!!, repository) { completedDocs = listOf(it) }
                        ToolIds.ADD_PAGE_NUMBERS -> PageNumbersWorkspace(selectedDoc!!, repository) { completedDocs = listOf(it) }
                        ToolIds.ADD_PASSWORD -> PasswordProtectionWorkspace(selectedDoc!!, true, repository) { completedDocs = listOf(it) }
                        ToolIds.REMOVE_PASSWORD -> PasswordProtectionWorkspace(selectedDoc!!, false, repository) { completedDocs = listOf(it) }
                        ToolIds.PDF_INFO -> PdfInfoWorkspace(selectedDoc!!, repository)
                        ToolIds.REPAIR_PDF -> RepairDuplicatePrintWorkspace("repair", selectedDoc!!, repository) { completedDocs = listOf(it) }
                        ToolIds.DUPLICATE_PDF -> RepairDuplicatePrintWorkspace("duplicate", selectedDoc!!, repository) { completedDocs = listOf(it) }
                        ToolIds.PRINT_PDF -> RepairDuplicatePrintWorkspace("print", selectedDoc!!, repository) {}
                        ToolIds.OCR_EXTRACT -> OcrExtractWorkspace(
                            initialDoc = selectedDoc,
                            allDocs = validDocs,
                            repository = repository,
                            onSavedTextDoc = { completedDocs = listOf(it) },
                            onShowMessage = onShowMessage
                        )
                        ToolIds.IMAGE_TOOLKIT -> ImageToolkitWorkspace(
                            initialDoc = selectedDoc,
                            imageDocs = imageDocs,
                            repository = repository,
                            onSavedImage = { completedDocs = listOf(it) }
                        )
                        ToolIds.SIGNATURE_LIBRARY -> SignatureLibraryWorkspace(
                            repository = repository,
                            onShowMessage = onShowMessage
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfSelectorList(
    pdfDocs: List<DocumentEntity>,
    onSelect: (DocumentEntity) -> Unit
) {
    val prixColors = LocalPrixColors.current
    Text("Select a PDF document to continue", style = MaterialTheme.typography.titleMedium)
    if (pdfDocs.isEmpty()) {
        GlassCard(
            shape = RoundedCornerShape(18.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "No PDF documents found in your PRIX vault yet. Scan a document, convert images, or import a PDF first.",
                style = MaterialTheme.typography.bodyMedium,
                color = prixColors.secondaryText
            )
        }
    } else {
        pdfDocs.forEach { doc ->
            GlassCard(
                shape = RoundedCornerShape(18.dp),
                onClick = { onSelect(doc) },
                contentPadding = PaddingValues(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(doc.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${doc.pageCount} pages • ${DocumentStorageManager.formatFileSize(doc.sizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = prixColors.secondaryText
                        )
                    }
                }
            }
        }
    }
}
