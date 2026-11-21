package com.example.prix.feature.pdfviewer

import android.graphics.Bitmap
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.prix.core.data.PrixRepository
import com.example.prix.core.database.DocumentEntity
import com.example.prix.core.error.PrixErrorMapper
import com.example.prix.core.error.PrixOperation
import com.example.prix.core.image.ImageProcessor
import com.example.prix.core.ui.GlassButton
import com.example.prix.core.ui.GlassChip
import com.example.prix.core.ui.GlassDialog
import com.example.prix.core.ui.GlassIconButton
import com.example.prix.core.ui.LiquidGlassBackground
import com.example.prix.ui.navigation.ToolIds
import com.example.ui.theme.LocalPrixColors
import com.example.ui.theme.LocalPrixEffects
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun PdfViewerScreen(
    documentId: Long,
    repository: PrixRepository,
    onBack: () -> Unit,
    onOpenEditor: (Long) -> Unit,
    onOpenTool: (String, Long) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prixColors = LocalPrixColors.current
    val effects = LocalPrixEffects.current

    val document by repository.observeDocument(documentId).collectAsStateWithLifecycle(initialValue = null)
    var pageCount by remember { mutableIntStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isEncrypted by remember { mutableStateOf(false) }

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var fitPageMode by remember { mutableStateOf(false) }
    var viewRotation by remember { mutableIntStateOf(0) }
    var fullScreen by remember { mutableStateOf(false) }
    var showThumbnails by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var extractedPages by remember { mutableStateOf<List<String>?>(null) }
    var isSearchingText by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var matchingPageIndices by remember { mutableStateOf<List<Int>>(emptyList()) }
    var matchCursor by remember { mutableIntStateOf(0) }

    var showJumpDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    val maxCachedPages = effects.performanceProfile.pdfViewerCachePages.coerceIn(2, 8)
    val bitmapCache = remember(documentId, maxCachedPages) {
        LruCache<Int, Bitmap>(maxCachedPages)
    }

    DisposableEffect(documentId) {
        onDispose {
            searchJob?.cancel()
            bitmapCache.evictAll()
        }
    }

    LaunchedEffect(document?.id, document?.updatedAt) {
        val doc = document ?: return@LaunchedEffect
        repository.markOpened(doc.id)
        val file = File(doc.filePath)
        if (!file.exists()) {
            loadError = PrixErrorMapper.map(
                PrixOperation.OPEN_PDF,
                java.io.FileNotFoundException("Document missing from storage.")
            ).toInlineMessage()
            return@LaunchedEffect
        }
        val info = repository.pdfEngine.inspectPdfInfo(file)
        if (info.isFailure) {
            val err = info.exceptionOrNull()
            val msg = err?.message.orEmpty()
            if (msg.contains("password", ignoreCase = true) || doc.isPasswordProtected) {
                isEncrypted = true
                loadError = PrixErrorMapper.map(PrixOperation.OPEN_PDF, err).toInlineMessage()
            } else {
                isEncrypted = false
                loadError = PrixErrorMapper.map(PrixOperation.OPEN_PDF, err).toInlineMessage()
            }
        } else {
            isEncrypted = false
            loadError = null
            pageCount = info.getOrNull()?.pageCount ?: repository.pdfEngine.getPageCount(file)
            extractedPages = null
            bitmapCache.evictAll()
        }
    }

    val exportSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val doc = document ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            scope.launch {
                val ok = repository.storage.exportFileToUri(File(doc.filePath), uri)
                onShowMessage(
                    if (ok) "PDF exported successfully."
                    else PrixErrorMapper.map(PrixOperation.EXPORT_FILE, null).toInlineMessage()
                )
            }
        }
    }

    val currentVisiblePage = listState.firstVisibleItemIndex + 1

    LiquidGlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                AnimatedVisibility(visible = !fullScreen) {
                    Surface(
                        color = prixColors.glassElevated,
                        border = BorderStroke(1.dp, prixColors.glassOuterBorder)
                    ) {
                        Column(modifier = Modifier.statusBarsPadding()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                GlassIconButton(
                                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    onClick = onBack,
                                    modifier = Modifier.testTag("viewer_back")
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = document?.name ?: "PDF Viewer",
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (pageCount > 0) {
                                        Text(
                                            text = "Page $currentVisiblePage of $pageCount",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = prixColors.secondaryText
                                        )
                                    }
                                }
                                GlassIconButton(
                                    icon = Icons.Filled.Search,
                                    contentDescription = "Search text",
                                    onClick = { showSearchBar = !showSearchBar }
                                )
                                GlassIconButton(
                                    icon = Icons.Filled.Edit,
                                    contentDescription = "Edit PDF",
                                    enabled = document != null && !isEncrypted && loadError == null,
                                    onClick = { document?.let { onOpenEditor(it.id) } },
                                    modifier = Modifier.testTag("viewer_edit_button")
                                )
                                Box {
                                    GlassIconButton(
                                        icon = Icons.Filled.MoreVert,
                                        contentDescription = "More options",
                                        onClick = { overflowOpen = true }
                                    )
                                    DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                                        DropdownMenuItem(
                                            text = { Text("Share PDF") },
                                            leadingIcon = { Icon(Icons.Filled.Share, null) },
                                            onClick = {
                                                overflowOpen = false
                                                document?.let { doc ->
                                                    try {
                                                        context.startActivity(
                                                            repository.storage.buildShareIntent(
                                                                File(doc.filePath),
                                                                doc.mimeType,
                                                                doc.name
                                                            )
                                                        )
                                                    } catch (_: Exception) {
                                                        onShowMessage("No app available to share PDF.")
                                                    }
                                                }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Export / Save As") },
                                            leadingIcon = { Icon(Icons.Filled.Download, null) },
                                            onClick = {
                                                overflowOpen = false
                                                document?.let { exportSafLauncher.launch("${it.name}.pdf") }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Print PDF") },
                                            leadingIcon = { Icon(Icons.Filled.Print, null) },
                                            onClick = {
                                                overflowOpen = false
                                                document?.let {
                                                    repository.pdfEngine.printPdf(context, File(it.filePath), it.name)
                                                }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Jump to Page") },
                                            onClick = {
                                                overflowOpen = false
                                                showJumpDialog = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Rename") },
                                            onClick = {
                                                overflowOpen = false
                                                showRenameDialog = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("PDF Info") },
                                            onClick = {
                                                overflowOpen = false
                                                document?.let { onOpenTool(ToolIds.PDF_INFO, it.id) }
                                            }
                                        )
                                    }
                                }
                            }

                            AnimatedVisibility(visible = showSearchBar) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = searchQuery,
                                            onValueChange = { q ->
                                                searchQuery = q
                                                searchJob?.cancel()
                                                searchJob = scope.launch {
                                                    val file = document?.filePath?.let { File(it) } ?: return@launch
                                                    var pages = extractedPages
                                                    if (pages == null) {
                                                        isSearchingText = true
                                                        pages = repository.pdfEngine.extractTextPerPage(file).getOrDefault(emptyList())
                                                        extractedPages = pages
                                                        isSearchingText = false
                                                    }
                                                    matchingPageIndices = if (q.isBlank()) {
                                                        emptyList()
                                                    } else {
                                                        pages.mapIndexedNotNull { idx, text ->
                                                            val normalized = text.replace(Regex("\\s+"), " ")
                                                            if (normalized.contains(q, ignoreCase = true)) idx else null
                                                        }
                                                    }
                                                    matchCursor = 0
                                                    matchingPageIndices.firstOrNull()?.let { listState.animateScrollToItem(it) }
                                                }
                                            },
                                            placeholder = { Text("Find text in PDF…") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isSearchingText) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        } else if (matchingPageIndices.isNotEmpty()) {
                                            Text(
                                                "${matchCursor + 1}/${matchingPageIndices.size}",
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                            TextButton(onClick = {
                                                matchCursor = (matchCursor + 1) % matchingPageIndices.size
                                                scope.launch { listState.animateScrollToItem(matchingPageIndices[matchCursor]) }
                                            }) {
                                                Text("Next")
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                searchJob?.cancel()
                                                showSearchBar = false
                                                searchQuery = ""
                                                matchingPageIndices = emptyList()
                                            },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(Icons.Filled.Close, contentDescription = "Close search")
                                        }
                                    }
                                    val scannedOnly = extractedPages?.let { pages -> pages.isNotEmpty() && pages.all { it.isBlank() } } == true
                                    if (scannedOnly && document != null) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "No embedded text found (scanned PDF).",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = prixColors.secondaryText
                                            )
                                            TextButton(onClick = { onOpenTool(ToolIds.OCR_EXTRACT, document!!.id) }) {
                                                Text("Run OCR")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (loadError != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (isEncrypted) Icons.Filled.Lock else Icons.Filled.Build,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(loadError!!, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(18.dp))
                        if (isEncrypted && document != null) {
                            GlassButton(
                                text = "Unlock PDF with Password",
                                onClick = { onOpenTool(ToolIds.REMOVE_PASSWORD, document!!.id) }
                            )
                        } else if (document != null) {
                            GlassButton(
                                text = "Attempt PDF Repair",
                                onClick = { onOpenTool(ToolIds.REPAIR_PDF, document!!.id) }
                            )
                        }
                    }
                } else if (pageCount == 0) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        AnimatedVisibility(visible = showThumbnails && !fullScreen) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items((0 until pageCount).toList()) { idx ->
                                    GlassChip(
                                        label = "P${idx + 1}",
                                        selected = idx == currentVisiblePage - 1 || idx in matchingPageIndices,
                                        onClick = { scope.launch { listState.animateScrollToItem(idx) } }
                                    )
                                }
                            }
                        }

                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, _, zoom, _ ->
                                        if (zoom != 1f) {
                                            zoomScale = (zoomScale * zoom).coerceIn(0.7f, 3.0f)
                                        }
                                    }
                                }
                        ) {
                            items((0 until pageCount).toList(), key = { it }) { pageIndex ->
                                PdfRenderedPageCard(
                                    doc = document!!,
                                    pageIndex = pageIndex,
                                    targetWidthPx = effects.performanceProfile.pdfViewerWidthPx,
                                    fitPageMode = fitPageMode,
                                    zoomScale = zoomScale,
                                    rotationDegrees = viewRotation,
                                    isSearchMatch = pageIndex in matchingPageIndices,
                                    cache = bitmapCache,
                                    repository = repository
                                )
                            }
                        }
                    }
                }

                // Floating Liquid Glass Control Dock
                AnimatedVisibility(
                    visible = !fullScreen && pageCount > 0,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = prixColors.glassElevated,
                        border = BorderStroke(1.dp, prixColors.glassOuterBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { zoomScale = (zoomScale - 0.25f).coerceAtLeast(0.7f) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom out")
                            }
                            IconButton(
                                onClick = { zoomScale = (zoomScale + 0.25f).coerceAtMost(3.0f) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom in")
                            }
                            IconButton(
                                onClick = {
                                    fitPageMode = !fitPageMode
                                    zoomScale = 1f
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.FitScreen, contentDescription = "Toggle fit width or page")
                            }
                            IconButton(
                                onClick = { viewRotation = (viewRotation + 90) % 360 },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate view")
                            }
                            IconButton(
                                onClick = { showThumbnails = !showThumbnails },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.ViewCarousel, contentDescription = "Toggle thumbnails")
                            }
                            IconButton(
                                onClick = { fullScreen = true },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.Fullscreen, contentDescription = "Fullscreen")
                            }
                        }
                    }
                }

                if (fullScreen) {
                    IconButton(
                        onClick = { fullScreen = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(12.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.FullscreenExit, contentDescription = "Exit fullscreen", tint = Color.White)
                    }
                }
            }
        }
    }

    if (showJumpDialog) {
        var pageInput by remember { mutableStateOf(currentVisiblePage.toString()) }
        GlassDialog(
            onDismissRequest = { showJumpDialog = false },
            title = "Jump to Page (1–$pageCount)",
            content = {
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = { pageInput = it.filter { ch -> ch.isDigit() } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val target = pageInput.toIntOrNull()?.coerceIn(1, pageCount)
                    if (target != null) {
                        scope.launch { listState.scrollToItem(target - 1) }
                    }
                    showJumpDialog = false
                }) { Text("Go") }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showRenameDialog && document != null) {
        var name by remember(document!!.name) { mutableStateOf(document!!.name) }
        GlassDialog(
            onDismissRequest = { showRenameDialog = false },
            title = "Rename PDF",
            content = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        repository.renameDocument(document!!.id, name)
                        showRenameDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PdfRenderedPageCard(
    doc: DocumentEntity,
    pageIndex: Int,
    targetWidthPx: Int,
    fitPageMode: Boolean,
    zoomScale: Float,
    rotationDegrees: Int,
    isSearchMatch: Boolean,
    cache: LruCache<Int, Bitmap>,
    repository: PrixRepository
) {
    var baseBitmap by remember(doc.id, doc.updatedAt, pageIndex) {
        mutableStateOf(cache.get(pageIndex)?.takeIf { !it.isRecycled })
    }
    var rotatedBitmap by remember(doc.id, doc.updatedAt, pageIndex) {
        mutableStateOf<Bitmap?>(null)
    }

    LaunchedEffect(doc.id, doc.updatedAt, pageIndex, targetWidthPx) {
        if (baseBitmap == null || baseBitmap!!.isRecycled) {
            val rendered = repository.pdfEngine.renderPdfPageBitmap(
                File(doc.filePath),
                pageIndex,
                targetWidthPx = targetWidthPx
            )
            if (rendered != null) {
                cache.put(pageIndex, rendered)
                baseBitmap = rendered
            }
        }
    }

    LaunchedEffect(baseBitmap, rotationDegrees) {
        val raw = baseBitmap
        if (raw == null || raw.isRecycled) {
            rotatedBitmap = null
        } else if (rotationDegrees % 360 == 0) {
            val old = rotatedBitmap
            rotatedBitmap = raw
            if (old != null && old !== raw && !old.isRecycled) old.recycle()
        } else {
            val rot = ImageProcessor.rotateBitmap(raw, rotationDegrees)
            val old = rotatedBitmap
            rotatedBitmap = rot
            if (old != null && old !== raw && old !== rot && !old.isRecycled) old.recycle()
        }
    }

    DisposableEffect(doc.id, pageIndex) {
        onDispose {
            val rot = rotatedBitmap
            val raw = baseBitmap
            rotatedBitmap = null
            if (rot != null && rot !== raw && !rot.isRecycled) {
                rot.recycle()
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(
            width = if (isSearchMatch) 2.5.dp else 1.dp,
            color = if (isSearchMatch) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
        ),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (fitPageMode) Modifier.height(520.dp) else Modifier)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(
                    scaleX = zoomScale,
                    scaleY = zoomScale
                ),
            contentAlignment = Alignment.Center
        ) {
            val bmp = rotatedBitmap
            if (bmp != null && !bmp.isRecycled) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    contentScale = if (fitPageMode) ContentScale.Fit else ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.71f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}
