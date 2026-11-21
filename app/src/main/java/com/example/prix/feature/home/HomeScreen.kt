package com.example.prix.feature.home

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.prix.core.database.DocumentEntity
import com.example.prix.core.model.StorageStats
import com.example.prix.core.storage.DocumentStorageManager
import com.example.prix.core.ui.DocumentListCard
import com.example.prix.core.ui.EmptyDocumentsState
import com.example.prix.core.ui.GlassCard
import com.example.prix.core.ui.GlassIconButton
import com.example.prix.core.ui.GlassSearchBar
import com.example.prix.core.ui.PrixLogoMark
import com.example.prix.ui.navigation.ToolIds
import com.example.ui.theme.LocalPrixColors
import com.example.ui.theme.LocalPrixEffects
import com.example.ui.theme.PrixAccentBlue
import com.example.ui.theme.PrixPurpleDeep
import com.example.ui.theme.PrixPurplePrimary
import com.example.ui.theme.PrixSuccess
import com.example.ui.theme.PrixWarning
import java.util.Calendar

@Composable
fun HomeScreen(
    recentDocuments: List<DocumentEntity>,
    storageStats: StorageStats,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onScanClick: () -> Unit,
    onImagesToPdfClick: () -> Unit,
    onEditPdfClick: () -> Unit,
    onImportPdfClick: () -> Unit,
    onQuickToolClick: (String) -> Unit,
    onSeeAllDocuments: () -> Unit,
    onOpenDocument: (DocumentEntity) -> Unit,
    onToggleStar: (DocumentEntity) -> Unit,
    onRenameDocument: (DocumentEntity) -> Unit,
    onDuplicateDocument: (DocumentEntity) -> Unit,
    onShareDocument: (DocumentEntity) -> Unit,
    onDeleteDocument: (DocumentEntity) -> Unit
) {
    val prixColors = LocalPrixColors.current
    val effects = LocalPrixEffects.current
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PrixLogoMark(size = 44.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = greeting,
                                style = MaterialTheme.typography.bodySmall,
                                color = prixColors.secondaryText
                            )
                            Text(
                                text = "PRIX Studio",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassIconButton(
                            icon = Icons.Filled.Search,
                            contentDescription = "Search documents",
                            onClick = onSearchClick,
                            modifier = Modifier.testTag("home_search_button")
                        )
                        GlassIconButton(
                            icon = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            onClick = onSettingsClick,
                            modifier = Modifier.testTag("home_settings_button")
                        )
                    }
                }
            }

            // Glass Search Bar shortcut
            item {
                GlassSearchBar(
                    query = "",
                    onQueryChange = {},
                    placeholder = "Search PDFs, folders, or OCR text…",
                    readOnlyClick = onSearchClick
                )
            }

            // Main 2x2 Liquid Glass Hero Action Grid
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HeroActionCard(
                            title = "Scan Document",
                            subtitle = "Camera & edge crop",
                            icon = Icons.Filled.DocumentScanner,
                            gradientColors = listOf(PrixPurplePrimary, PrixPurpleDeep),
                            isPrimary = true,
                            onClick = onScanClick,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("action_scan_document")
                        )
                        HeroActionCard(
                            title = "Images to PDF",
                            subtitle = "Batch photo maker",
                            icon = Icons.Filled.PhotoLibrary,
                            accentColor = PrixAccentBlue,
                            onClick = onImagesToPdfClick,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("action_images_to_pdf")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HeroActionCard(
                            title = "Edit PDF",
                            subtitle = "Annotate, text & sign",
                            icon = Icons.Filled.EditNote,
                            accentColor = PrixSuccess,
                            onClick = onEditPdfClick,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("action_edit_pdf")
                        )
                        HeroActionCard(
                            title = "Import PDF",
                            subtitle = "Open from device",
                            icon = Icons.Filled.FileUpload,
                            accentColor = PrixWarning,
                            onClick = onImportPdfClick,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("action_import_pdf")
                        )
                    }
                }
            }

            // Quick Tools strip
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Quick Tools", style = MaterialTheme.typography.titleMedium)
                    val quickTools = remember {
                        listOf(
                            QuickToolItem(ToolIds.MERGE_PDF, "Merge PDF", Icons.Filled.MergeType, PrixPurplePrimary),
                            QuickToolItem(ToolIds.SPLIT_PDF, "Split PDF", Icons.Filled.CallSplit, PrixAccentBlue),
                            QuickToolItem(ToolIds.COMPRESS_PDF, "Compress", Icons.Filled.Compress, PrixSuccess),
                            QuickToolItem(ToolIds.ADD_SIGNATURE, "Sign PDF", Icons.Filled.Draw, PrixWarning)
                        )
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(quickTools, key = { it.toolId }) { tool ->
                            GlassCard(
                                shape = RoundedCornerShape(18.dp),
                                elevated = true,
                                onClick = { onQuickToolClick(tool.toolId) },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
                                modifier = Modifier.testTag("quick_tool_${tool.toolId}")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(tool.accent.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = tool.icon,
                                            contentDescription = tool.label,
                                            tint = tool.accent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(tool.label, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }

            // Local Storage Overview GlassCard
            item {
                GlassCard(
                    shape = RoundedCornerShape(22.dp),
                    elevated = true,
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Storage,
                                contentDescription = "Local Storage",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${storageStats.totalDocuments} Saved Documents • ${storageStats.recentCount24h} active today",
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "PRIX Vault: ${DocumentStorageManager.formatFileSize(storageStats.vaultSizeBytes)} • Device free: ${DocumentStorageManager.formatFileSize(storageStats.availableDeviceBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = prixColors.secondaryText
                            )
                        }
                    }
                }
            }

            // Recent Documents Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Recent Documents", style = MaterialTheme.typography.titleMedium)
                    if (recentDocuments.isNotEmpty()) {
                        TextButton(onClick = onSeeAllDocuments) {
                            Text("See all")
                        }
                    }
                }
            }

            if (recentDocuments.isEmpty()) {
                item {
                    EmptyDocumentsState(
                        title = "Your document workspace is ready",
                        subtitle = "Scan physical paperwork, convert photos to PDF, or import an existing PDF from your device.",
                        ctaLabel = "Create Your First Document",
                        onCtaClick = onScanClick
                    )
                }
            } else {
                items(recentDocuments.take(8), key = { it.id }) { doc ->
                    DocumentListCard(
                        document = doc,
                        onClick = { onOpenDocument(doc) },
                        onToggleStar = { onToggleStar(doc) },
                        onRename = { onRenameDocument(doc) },
                        onDuplicate = { onDuplicateDocument(doc) },
                        onShare = { onShareDocument(doc) },
                        onDelete = { onDeleteDocument(doc) },
                        modifier = if (effects.shouldAnimate) Modifier.animateItem() else Modifier
                    )
                }
            }
        }
    }
}

private data class QuickToolItem(
    val toolId: String,
    val label: String,
    val icon: ImageVector,
    val accent: Color
)

@Composable
private fun HeroActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gradientColors: List<Color>? = null,
    accentColor: Color = PrixPurplePrimary,
    isPrimary: Boolean = false
) {
    val prixColors = LocalPrixColors.current
    GlassCard(
        modifier = modifier.height(126.dp),
        shape = RoundedCornerShape(24.dp),
        elevated = !isPrimary,
        tintOverlay = if (isPrimary) Color.Transparent else null,
        borderColor = if (isPrimary) Color.White.copy(alpha = 0.26f) else null,
        onClick = onClick,
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isPrimary && gradientColors != null) {
                        Modifier.background(Brush.linearGradient(gradientColors))
                    } else Modifier
                )
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(
                            if (isPrimary) Color.White.copy(alpha = 0.22f)
                            else accentColor.copy(alpha = 0.14f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = if (isPrimary) Color.White else accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isPrimary) Color.White else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isPrimary) Color.White.copy(alpha = 0.85f) else prixColors.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
