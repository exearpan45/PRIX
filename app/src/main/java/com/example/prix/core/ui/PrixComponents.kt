package com.example.prix.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.example.R
import com.example.prix.core.database.DocumentEntity
import com.example.prix.core.model.DocumentFormat
import com.example.prix.core.storage.DocumentStorageManager
import com.example.ui.theme.LocalPrixColors
import com.example.ui.theme.PrixAccentBlue
import com.example.ui.theme.PrixImageTeal
import com.example.ui.theme.PrixPdfRed
import com.example.ui.theme.PrixPurpleDeep
import com.example.ui.theme.PrixPurplePrimary
import com.example.ui.theme.PrixTextSlate
import java.io.File

@Composable
fun PrixLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(
                Brush.linearGradient(
                    colors = listOf(PrixPurplePrimary, PrixPurpleDeep)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize(0.62f)) {
            val w = this.size.width
            val h = this.size.height
            val fold = w * 0.28f

            val docPath = Path().apply {
                moveTo(w * 0.14f, 0f)
                lineTo(w * 0.86f - fold, 0f)
                lineTo(w * 0.86f, fold)
                lineTo(w * 0.86f, h)
                lineTo(w * 0.14f, h)
                close()
            }
            drawPath(docPath, color = Color.White.copy(alpha = 0.94f))

            val foldPath = Path().apply {
                moveTo(w * 0.86f - fold, 0f)
                lineTo(w * 0.86f, fold)
                lineTo(w * 0.86f - fold, fold)
                close()
            }
            drawPath(foldPath, color = PrixAccentBlue)

            drawRoundRect(
                color = PrixAccentBlue,
                topLeft = Offset(w * 0.04f, h * 0.44f),
                size = Size(w * 0.92f, h * 0.12f),
                cornerRadius = CornerRadius(h * 0.06f, h * 0.06f)
            )
        }
    }
}

@Composable
fun FormatBadge(format: DocumentFormat, modifier: Modifier = Modifier) {
    val bg = when (format) {
        DocumentFormat.PDF -> PrixPdfRed.copy(alpha = 0.15f)
        DocumentFormat.PNG, DocumentFormat.JPEG, DocumentFormat.WEBP -> PrixImageTeal.copy(alpha = 0.15f)
        DocumentFormat.TXT -> PrixTextSlate.copy(alpha = 0.15f)
    }
    val fg = when (format) {
        DocumentFormat.PDF -> PrixPdfRed
        DocumentFormat.PNG, DocumentFormat.JPEG, DocumentFormat.WEBP -> PrixImageTeal
        DocumentFormat.TXT -> PrixTextSlate
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = format.label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun DocumentThumbnailBox(
    document: DocumentEntity,
    modifier: Modifier = Modifier
) {
    val thumbPath = document.thumbnailPath
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)),
        contentAlignment = Alignment.Center
    ) {
        if (!thumbPath.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = remember(thumbPath) { File(thumbPath) },
                contentDescription = "${document.name} preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = { FormatFallbackIcon(document.format) },
                loading = { FormatFallbackIcon(document.format) }
            )
        } else {
            FormatFallbackIcon(document.format)
        }
        if (document.isPasswordProtected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "Protected PDF",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}

@Composable
private fun FormatFallbackIcon(format: DocumentFormat) {
    val icon = when (format) {
        DocumentFormat.PDF -> Icons.Filled.PictureAsPdf
        DocumentFormat.PNG, DocumentFormat.JPEG, DocumentFormat.WEBP -> Icons.Filled.Image
        DocumentFormat.TXT -> Icons.Filled.Description
    }
    val tint = when (format) {
        DocumentFormat.PDF -> PrixPdfRed
        DocumentFormat.PNG, DocumentFormat.JPEG, DocumentFormat.WEBP -> PrixImageTeal
        DocumentFormat.TXT -> PrixTextSlate
    }
    Icon(
        imageVector = icon,
        contentDescription = format.label,
        tint = tint,
        modifier = Modifier.size(26.dp)
    )
}

@Composable
fun DocumentListCard(
    document: DocumentEntity,
    isSelected: Boolean = false,
    selectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onToggleStar: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val prixColors = LocalPrixColors.current
    var menuExpanded by remember { mutableStateOf(false) }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("doc_card_${document.id}"),
        shape = RoundedCornerShape(20.dp),
        tintOverlay = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f) else null,
        borderColor = if (isSelected) MaterialTheme.colorScheme.primary else null,
        onClick = onClick,
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DocumentThumbnailBox(
                document = document,
                modifier = Modifier.size(width = 50.dp, height = 62.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FormatBadge(document.format)
                    Text(
                        text = DocumentStorageManager.formatFileSize(document.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = prixColors.secondaryText
                    )
                    if (document.format == DocumentFormat.PDF) {
                        Text(
                            text = "${document.pageCount}p",
                            style = MaterialTheme.typography.labelSmall,
                            color = prixColors.secondaryText
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = DocumentStorageManager.formatDateTime(document.updatedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = prixColors.secondaryText,
                    maxLines = 1
                )
            }
            if (!selectionMode) {
                IconButton(
                    onClick = onToggleStar,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = if (document.isStarred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = if (document.isStarred) "Unstar document" else "Star document",
                        tint = if (document.isStarred) prixColors.warning else prixColors.secondaryText
                    )
                }
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("doc_menu_${document.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Document options",
                            tint = prixColors.secondaryText
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Share") },
                            onClick = {
                                menuExpanded = false
                                onShare()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            onClick = {
                                menuExpanded = false
                                onDuplicate()
                            }
                        )
                        onLongClick?.let { selectAction ->
                            DropdownMenuItem(
                                text = { Text("Select") },
                                onClick = {
                                    menuExpanded = false
                                    selectAction()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DocumentGridCard(
    document: DocumentEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggleStar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val prixColors = LocalPrixColors.current
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("doc_grid_card_${document.id}"),
        shape = RoundedCornerShape(20.dp),
        tintOverlay = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else null,
        borderColor = if (isSelected) MaterialTheme.colorScheme.primary else null,
        onClick = onClick,
        contentPadding = PaddingValues(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.85f)
        ) {
            DocumentThumbnailBox(
                document = document,
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = onToggleStar,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = if (document.isStarred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = "Toggle star",
                    tint = if (document.isStarred) prixColors.warning else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = document.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            FormatBadge(document.format)
            Text(
                text = DocumentStorageManager.formatFileSize(document.sizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = prixColors.secondaryText
            )
        }
    }
}

@Composable
fun EmptyDocumentsState(
    title: String,
    subtitle: String,
    ctaLabel: String,
    onCtaClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val prixColors = LocalPrixColors.current
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        elevated = true,
        contentPadding = PaddingValues(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_documents_1795241878046),
                contentDescription = "No documents illustration",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(18.dp))
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = prixColors.secondaryText,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(18.dp))
            GlassButton(
                text = ctaLabel,
                onClick = onCtaClick,
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .testTag("empty_state_cta")
            )
        }
    }
}

@Composable
fun ErrorRecoveryDialog(
    title: String,
    message: String,
    recoveryLabel: String? = null,
    onRecovery: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    GlassDialog(
        onDismissRequest = onDismiss,
        title = title,
        icon = {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        },
        content = {
            Text(message, style = MaterialTheme.typography.bodyMedium)
        },
        confirmButton = {
            if (recoveryLabel != null && onRecovery != null) {
                Button(onClick = {
                    onDismiss()
                    onRecovery()
                }) {
                    Text(recoveryLabel)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("OK")
                }
            }
        },
        dismissButton = if (recoveryLabel != null && onRecovery != null) {
            {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        } else null
    )
}
