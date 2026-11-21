package com.example.prix.feature.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.LowPriority
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Output
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.prix.core.ui.GlassCard
import com.example.prix.ui.navigation.ToolIds
import com.example.ui.theme.LocalPrixColors
import com.example.ui.theme.PrixAccentBlue
import com.example.ui.theme.PrixError
import com.example.ui.theme.PrixImageTeal
import com.example.ui.theme.PrixPurplePrimary
import com.example.ui.theme.PrixSuccess
import com.example.ui.theme.PrixWarning

private data class ToolEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color
)

@Composable
fun ToolsCenterScreen(
    onOpenScanner: () -> Unit,
    onOpenImagesToPdf: () -> Unit,
    onOpenTool: (String) -> Unit
) {
    val prixColors = LocalPrixColors.current

    val categories = remember {
        listOf(
            "Create & Extract" to listOf(
                ToolEntry("scan", "Scan Document", "Camera capture & perspective crop", Icons.Filled.DocumentScanner, PrixPurplePrimary),
                ToolEntry("img_to_pdf", "Images to PDF", "Bundle photos into custom PDF", Icons.Filled.PhotoLibrary, PrixAccentBlue),
                ToolEntry(ToolIds.PDF_TO_IMAGES, "PDF to Images", "Export pages as PNG, JPEG, WebP", Icons.Filled.Image, PrixImageTeal),
                ToolEntry(ToolIds.OCR_EXTRACT, "OCR Text Extract", "On-device text recognition", Icons.Filled.TextSnippet, PrixSuccess)
            ),
            "Organize Pages" to listOf(
                ToolEntry(ToolIds.MERGE_PDF, "Merge PDF", "Combine multiple PDFs into one", Icons.Filled.MergeType, PrixPurplePrimary),
                ToolEntry(ToolIds.SPLIT_PDF, "Split PDF", "Split by page or custom ranges", Icons.Filled.CallSplit, PrixAccentBlue),
                ToolEntry(ToolIds.ROTATE_PDF, "Rotate PDF", "Rotate individual or all pages", Icons.AutoMirrored.Filled.RotateRight, PrixSuccess),
                ToolEntry(ToolIds.REORDER_PAGES, "Reorder Pages", "Arrange PDF page sequence", Icons.Filled.LowPriority, PrixWarning),
                ToolEntry(ToolIds.DELETE_PAGES, "Delete Pages", "Remove unwanted PDF pages", Icons.Filled.DeleteSweep, PrixError),
                ToolEntry(ToolIds.EXTRACT_PAGES, "Extract Pages", "Save selected pages as new PDF", Icons.Filled.Output, PrixImageTeal)
            ),
            "Enhance, Sign & Edit" to listOf(
                ToolEntry(ToolIds.COMPRESS_PDF, "Compress PDF", "Optimize PDF size with levels", Icons.Filled.Compress, PrixSuccess),
                ToolEntry(ToolIds.ADD_WATERMARK, "Add Watermark", "Stamp text diagonally or margins", Icons.Filled.WaterDrop, PrixAccentBlue),
                ToolEntry(ToolIds.ADD_PAGE_NUMBERS, "Page Numbers", "Add footer or header numbers", Icons.Filled.FormatListNumbered, PrixPurplePrimary),
                ToolEntry(ToolIds.ADD_SIGNATURE, "Sign PDF", "Draw & place signature overlay", Icons.Filled.Draw, PrixWarning),
                ToolEntry(ToolIds.SIGNATURE_LIBRARY, "Signature Manager", "Create & organize saved signatures", Icons.Filled.Gesture, PrixPurplePrimary),
                ToolEntry(ToolIds.IMAGE_TOOLKIT, "Image Toolkit", "Crop, rotate, resize & convert", Icons.Filled.Tune, PrixImageTeal)
            ),
            "Security & Utilities" to listOf(
                ToolEntry(ToolIds.ADD_PASSWORD, "Protect PDF", "Encrypt with user password", Icons.Filled.Lock, PrixPurplePrimary),
                ToolEntry(ToolIds.REMOVE_PASSWORD, "Unlock PDF", "Remove password with permission", Icons.Filled.LockOpen, PrixSuccess),
                ToolEntry(ToolIds.PDF_INFO, "PDF Information", "Inspect pages, size & metadata", Icons.Filled.Info, PrixAccentBlue),
                ToolEntry(ToolIds.REPAIR_PDF, "Repair PDF", "Rebuild readable PDF structure", Icons.Filled.Build, PrixWarning),
                ToolEntry(ToolIds.DUPLICATE_PDF, "Duplicate PDF", "Create safe working copy", Icons.Filled.ContentCopy, PrixImageTeal),
                ToolEntry(ToolIds.PRINT_PDF, "Print PDF", "Send to Android Print service", Icons.Filled.Print, PrixPurplePrimary)
            )
        )
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Column {
                    Text("PDF & Document Tools", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "All tools process files locally on your device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = prixColors.secondaryText
                    )
                }
            }

            categories.forEach { (categoryTitle, entries) ->
                item(key = categoryTitle) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(categoryTitle, style = MaterialTheme.typography.titleMedium)
                        val rows = entries.chunked(2)
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            rows.forEach { pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    pair.forEach { entry ->
                                        ToolGridTile(
                                            entry = entry,
                                            onClick = {
                                                when (entry.id) {
                                                    "scan" -> onOpenScanner()
                                                    "img_to_pdf" -> onOpenImagesToPdf()
                                                    else -> onOpenTool(entry.id)
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("tool_tile_${entry.id}")
                                        )
                                    }
                                    if (pair.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
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
private fun ToolGridTile(
    entry: ToolEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val prixColors = LocalPrixColors.current
    GlassCard(
        modifier = modifier.height(108.dp),
        shape = RoundedCornerShape(20.dp),
        elevated = true,
        onClick = onClick,
        contentPadding = PaddingValues(13.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(entry.accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = entry.icon,
                    contentDescription = entry.title,
                    tint = entry.accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    entry.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = prixColors.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
