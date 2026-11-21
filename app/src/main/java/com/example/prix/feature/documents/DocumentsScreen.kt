package com.example.prix.feature.documents

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.prix.core.database.DocumentEntity
import com.example.prix.core.database.FolderEntity
import com.example.prix.core.model.DocumentFilterTab
import com.example.prix.core.model.DocumentFormat
import com.example.prix.core.model.DocumentSortOption
import com.example.prix.core.ui.DocumentGridCard
import com.example.prix.core.ui.DocumentListCard
import com.example.prix.core.ui.EmptyDocumentsState
import com.example.prix.core.ui.GlassCard
import com.example.prix.core.ui.GlassChip
import com.example.prix.core.ui.GlassDialog
import com.example.prix.core.ui.GlassIconButton
import com.example.prix.core.ui.GlassSearchBar
import com.example.ui.theme.LocalPrixEffects

@Composable
fun DocumentsScreen(
    allDocuments: List<DocumentEntity>,
    allFolders: List<FolderEntity>,
    currentFolderId: Long?,
    searchQuery: String,
    activeTab: DocumentFilterTab,
    sortOption: DocumentSortOption,
    isGridView: Boolean,
    selectedIds: Set<Long>,
    focusSearchInitially: Boolean = false,
    onSearchChange: (String) -> Unit,
    onTabChange: (DocumentFilterTab) -> Unit,
    onSortChange: (DocumentSortOption) -> Unit,
    onToggleGridView: () -> Unit,
    onOpenFolder: (Long?) -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameFolder: (FolderEntity, String) -> Unit,
    onDeleteFolder: (FolderEntity) -> Unit,
    onOpenDocument: (DocumentEntity) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onClearSelection: () -> Unit,
    onToggleStar: (DocumentEntity) -> Unit,
    onRenameDocument: (DocumentEntity) -> Unit,
    onDuplicateDocument: (DocumentEntity) -> Unit,
    onShareDocument: (DocumentEntity) -> Unit,
    onDeleteDocument: (DocumentEntity) -> Unit,
    onBatchDelete: (List<Long>) -> Unit,
    onBatchMoveToFolder: (List<Long>, Long?) -> Unit,
    onBatchShare: (List<Long>) -> Unit,
    onBatchMergePdfs: (List<Long>) -> Unit,
    onScanFirstDocument: () -> Unit
) {
    val effects = LocalPrixEffects.current
    val searchFocusRequester = remember { FocusRequester() }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderToRename by remember { mutableStateOf<FolderEntity?>(null) }
    var showMoveSheet by remember { mutableStateOf(false) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    val currentFolder = remember(allFolders, currentFolderId) {
        allFolders.firstOrNull { it.id == currentFolderId }
    }

    LaunchedEffect(focusSearchInitially) {
        if (focusSearchInitially) {
            try {
                searchFocusRequester.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    BackHandler(enabled = selectedIds.isNotEmpty() || currentFolder != null) {
        if (selectedIds.isNotEmpty()) {
            onClearSelection()
        } else if (currentFolder != null) {
            onOpenFolder(currentFolder.parentFolderId)
        }
    }

    val subFolders = remember(allFolders, currentFolderId, searchQuery) {
        allFolders.filter { folder ->
            if (searchQuery.isNotBlank()) {
                folder.name.contains(searchQuery, ignoreCase = true)
            } else {
                folder.parentFolderId == currentFolderId
            }
        }
    }

    val folderNameMap = remember(allFolders) { allFolders.associate { it.id to it.name } }

    val filteredDocuments = remember(allDocuments, currentFolderId, searchQuery, activeTab, sortOption, folderNameMap) {
        val bySearchAndFolder = allDocuments.filter { doc ->
            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                val fName = doc.folderId?.let { folderNameMap[it] }.orEmpty()
                doc.name.contains(searchQuery, ignoreCase = true) ||
                    doc.format.label.contains(searchQuery, ignoreCase = true) ||
                    doc.ocrText.contains(searchQuery, ignoreCase = true) ||
                    fName.contains(searchQuery, ignoreCase = true)
            }
            val matchesFolder = if (searchQuery.isNotBlank() || activeTab != DocumentFilterTab.ALL) {
                true
            } else {
                doc.folderId == currentFolderId
            }
            val matchesTab = when (activeTab) {
                DocumentFilterTab.ALL -> true
                DocumentFilterTab.RECENT_OPENED -> doc.lastOpenedAt > 0L
                DocumentFilterTab.RECENT_MODIFIED -> true
                DocumentFilterTab.STARRED -> doc.isStarred
            }
            matchesSearch && matchesFolder && matchesTab
        }

        when (activeTab) {
            DocumentFilterTab.RECENT_OPENED -> bySearchAndFolder.sortedByDescending { it.lastOpenedAt }
            DocumentFilterTab.RECENT_MODIFIED -> bySearchAndFolder.sortedByDescending { it.updatedAt }
            else -> when (sortOption) {
                DocumentSortOption.DATE_DESC -> bySearchAndFolder.sortedByDescending { it.updatedAt }
                DocumentSortOption.DATE_ASC -> bySearchAndFolder.sortedBy { it.updatedAt }
                DocumentSortOption.NAME_ASC -> bySearchAndFolder.sortedBy { it.name.lowercase() }
                DocumentSortOption.NAME_DESC -> bySearchAndFolder.sortedByDescending { it.name.lowercase() }
                DocumentSortOption.SIZE_DESC -> bySearchAndFolder.sortedByDescending { it.sizeBytes }
                DocumentSortOption.SIZE_ASC -> bySearchAndFolder.sortedBy { it.sizeBytes }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Animated Header / Multi-selection Glass Bar
            AnimatedContent(
                targetState = selectedIds.isNotEmpty(),
                transitionSpec = {
                    if (effects.shouldAnimate) {
                        fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                    } else {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    }
                },
                label = "docHeaderMode"
            ) { selectionActive ->
                if (selectionActive) {
                    GlassCard(
                        shape = RoundedCornerShape(20.dp),
                        elevated = true,
                        tintOverlay = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onClearSelection) {
                                Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
                            }
                            Text(
                                text = "${selectedIds.size} selected",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { showMoveSheet = true }) {
                                Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Move to folder")
                            }
                            val selectedPdfs = filteredDocuments.filter { it.id in selectedIds && it.format == DocumentFormat.PDF }
                            if (selectedPdfs.size >= 2) {
                                IconButton(onClick = { onBatchMergePdfs(selectedPdfs.map { it.id }) }) {
                                    Icon(Icons.Filled.MergeType, contentDescription = "Merge selected PDFs")
                                }
                            }
                            IconButton(onClick = { onBatchShare(selectedIds.toList()) }) {
                                Icon(Icons.Filled.Share, contentDescription = "Share selected")
                            }
                            IconButton(
                                onClick = { showBatchDeleteConfirm = true },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete selected", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (currentFolder != null) {
                            GlassIconButton(
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to parent folder",
                                onClick = { onOpenFolder(currentFolder.parentFolderId) }
                            )
                        }
                        Text(
                            text = currentFolder?.name ?: "Documents",
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        GlassIconButton(
                            icon = Icons.Filled.CreateNewFolder,
                            contentDescription = "New folder",
                            onClick = { showCreateFolderDialog = true },
                            modifier = Modifier.testTag("create_folder_button")
                        )
                        GlassIconButton(
                            icon = if (isGridView) Icons.Filled.ViewList else Icons.Filled.GridView,
                            contentDescription = "Toggle grid or list view",
                            onClick = onToggleGridView
                        )
                        Box {
                            GlassIconButton(
                                icon = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort documents",
                                onClick = { sortMenuOpen = true }
                            )
                            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                                DocumentSortOption.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = {
                                            sortMenuOpen = false
                                            onSortChange(option)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search bar (name, format, folder, OCR text)
            GlassSearchBar(
                query = searchQuery,
                onQueryChange = onSearchChange,
                placeholder = "Search name, type, folder, or OCR text…",
                modifier = Modifier
                    .focusRequester(searchFocusRequester)
                    .testTag("documents_search_field")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(DocumentFilterTab.entries) { tab ->
                    GlassChip(
                        label = tab.label,
                        selected = activeTab == tab,
                        onClick = { onTabChange(tab) },
                        modifier = Modifier.testTag("filter_tab_${tab.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (subFolders.isEmpty() && filteredDocuments.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    EmptyDocumentsState(
                        title = if (searchQuery.isNotBlank()) "No matching documents" else "No files here yet",
                        subtitle = if (searchQuery.isNotBlank()) {
                            "Try another keyword, format, or run OCR on scanned documents to make their text searchable."
                        } else {
                            "Scan a document, create a folder, or convert photos into a PDF."
                        },
                        ctaLabel = if (searchQuery.isNotBlank()) "Clear Search" else "Scan Document",
                        onCtaClick = {
                            if (searchQuery.isNotBlank()) onSearchChange("") else onScanFirstDocument()
                        },
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            } else if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 152.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 104.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(subFolders, key = { "folder_${it.id}" }) { folder ->
                        FolderRowCard(
                            folder = folder,
                            onClick = { onOpenFolder(folder.id) },
                            onRename = { folderToRename = folder },
                            onDelete = { onDeleteFolder(folder) },
                            modifier = if (effects.shouldAnimate) Modifier.animateItem() else Modifier
                        )
                    }
                    items(filteredDocuments, key = { "doc_${it.id}" }) { doc ->
                        DocumentGridCard(
                            document = doc,
                            isSelected = doc.id in selectedIds,
                            onClick = {
                                if (selectedIds.isNotEmpty()) onToggleSelection(doc.id)
                                else onOpenDocument(doc)
                            },
                            onToggleStar = { onToggleStar(doc) },
                            modifier = if (effects.shouldAnimate) Modifier.animateItem() else Modifier
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 104.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(subFolders, key = { "folder_${it.id}" }) { folder ->
                        FolderRowCard(
                            folder = folder,
                            onClick = { onOpenFolder(folder.id) },
                            onRename = { folderToRename = folder },
                            onDelete = { onDeleteFolder(folder) },
                            modifier = if (effects.shouldAnimate) Modifier.animateItem() else Modifier
                        )
                    }
                    items(filteredDocuments, key = { "doc_${it.id}" }) { doc ->
                        DocumentListCard(
                            document = doc,
                            isSelected = doc.id in selectedIds,
                            selectionMode = selectedIds.isNotEmpty(),
                            onClick = {
                                if (selectedIds.isNotEmpty()) onToggleSelection(doc.id)
                                else onOpenDocument(doc)
                            },
                            onLongClick = { onToggleSelection(doc.id) },
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

    if (showCreateFolderDialog) {
        var name by remember { mutableStateOf("") }
        GlassDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = "New Folder",
            content = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            onCreateFolder(name.trim())
                            showCreateFolderDialog = false
                        }
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    folderToRename?.let { target ->
        var name by remember(target.id) { mutableStateOf(target.name) }
        GlassDialog(
            onDismissRequest = { folderToRename = null },
            title = "Rename Folder",
            content = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) {
                        onRenameFolder(target, name.trim())
                        folderToRename = null
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { folderToRename = null }) { Text("Cancel") }
            }
        )
    }

    if (showMoveSheet) {
        GlassDialog(
            onDismissRequest = { showMoveSheet = false },
            title = "Move ${selectedIds.size} document(s)",
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            onBatchMoveToFolder(selectedIds.toList(), null)
                            showMoveSheet = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Root (All Documents)")
                    }
                    allFolders.forEach { folder ->
                        TextButton(
                            onClick = {
                                onBatchMoveToFolder(selectedIds.toList(), folder.id)
                                showMoveSheet = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Folder, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(folder.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMoveSheet = false }) { Text("Cancel") }
            }
        )
    }

    if (showBatchDeleteConfirm && selectedIds.isNotEmpty()) {
        GlassDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = "Delete ${selectedIds.size} Document(s)?",
            content = {
                Text("Are you sure you want to permanently delete ${selectedIds.size} selected item(s) from this device?")
            },
            confirmButton = {
                Button(onClick = {
                    val targets = selectedIds.toList()
                    showBatchDeleteConfirm = false
                    onBatchDelete(targets)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun FolderRowCard(
    folder: FolderEntity,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }

    GlassCard(
        shape = RoundedCornerShape(18.dp),
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = "Folder",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Folder options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = {
                        menuOpen = false
                        onRename()
                    })
                    DropdownMenuItem(
                        text = { Text("Delete folder", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
