package com.example.prix.feature.pdfeditor

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.prix.core.data.PrixRepository
import com.example.prix.core.database.DocumentEntity
import com.example.prix.core.error.PrixErrorMapper
import com.example.prix.core.error.PrixOperation
import com.example.prix.core.image.ImageProcessor
import com.example.prix.core.model.EditorTool
import com.example.prix.core.model.NormalizedImageOverlay
import com.example.prix.core.model.NormalizedPoint
import com.example.prix.core.model.NormalizedShape
import com.example.prix.core.model.NormalizedStroke
import com.example.prix.core.model.NormalizedTextBox
import com.example.prix.core.model.PageTransformItem
import com.example.prix.core.model.ShapeKind
import com.example.prix.core.ui.GlassChip
import com.example.prix.core.ui.GlassDialog
import com.example.prix.core.ui.GlassIconButton
import com.example.prix.core.ui.LiquidGlassBackground
import com.example.ui.theme.LocalPrixColors
import com.example.ui.theme.LocalPrixEffects
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private data class EditorSnapshot(
    val pagePlan: List<PageTransformItem>,
    val currentPageIdx: Int,
    val strokes: List<NormalizedStroke>,
    val shapes: List<NormalizedShape>,
    val textBoxes: List<NormalizedTextBox>,
    val imageOverlays: List<NormalizedImageOverlay>
)

@Composable
fun PdfEditorScreen(
    documentId: Long,
    repository: PrixRepository,
    startWithSignature: Boolean = false,
    onBack: () -> Unit,
    onSavedDocument: (DocumentEntity) -> Unit
) {
    val scope = rememberCoroutineScope()
    val prixColors = LocalPrixColors.current
    val effects = LocalPrixEffects.current
    val document by repository.observeDocument(documentId).collectAsStateWithLifecycle(initialValue = null)
    val savedSignatures by repository.allSignatures.collectAsStateWithLifecycle(initialValue = emptyList())

    val pagePlan = remember { mutableStateListOf<PageTransformItem>() }
    var currentPageIdx by remember { mutableIntStateOf(0) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var displayBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var activeTool by remember { mutableStateOf(if (startWithSignature) EditorTool.SIGNATURE else EditorTool.PEN) }
    var activeColorArgb by remember { mutableIntStateOf(0xFF7657F6.toInt()) }
    var strokeThicknessNorm by remember { mutableFloatStateOf(0.008f) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val strokes = remember { mutableStateListOf<NormalizedStroke>() }
    val textBoxes = remember { mutableStateListOf<NormalizedTextBox>() }
    val shapes = remember { mutableStateListOf<NormalizedShape>() }
    val imageOverlays = remember { mutableStateListOf<NormalizedImageOverlay>() }
    val tempOverlayPaths = remember { mutableStateListOf<String>() }

    val undoStack = remember { mutableStateListOf<EditorSnapshot>() }
    val redoStack = remember { mutableStateListOf<EditorSnapshot>() }

    fun captureSnapshot(): EditorSnapshot = EditorSnapshot(
        pagePlan = pagePlan.toList(),
        currentPageIdx = currentPageIdx,
        strokes = strokes.toList(),
        shapes = shapes.toList(),
        textBoxes = textBoxes.toList(),
        imageOverlays = imageOverlays.toList()
    )

    fun restoreSnapshot(snap: EditorSnapshot) {
        pagePlan.clear()
        pagePlan.addAll(snap.pagePlan)
        currentPageIdx = snap.currentPageIdx.coerceIn(0, (pagePlan.size - 1).coerceAtLeast(0))
        strokes.clear()
        strokes.addAll(snap.strokes)
        shapes.clear()
        shapes.addAll(snap.shapes)
        textBoxes.clear()
        textBoxes.addAll(snap.textBoxes)
        imageOverlays.clear()
        imageOverlays.addAll(snap.imageOverlays)
    }

    fun recordUndoPoint() {
        undoStack.add(captureSnapshot())
        if (undoStack.size > 40) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
    }

    fun performUndo() {
        if (undoStack.isEmpty()) return
        redoStack.add(captureSnapshot())
        val prev = undoStack.removeAt(undoStack.lastIndex)
        restoreSnapshot(prev)
    }

    fun performRedo() {
        if (redoStack.isEmpty()) return
        undoStack.add(captureSnapshot())
        val next = redoStack.removeAt(redoStack.lastIndex)
        restoreSnapshot(next)
    }

    var activeStrokePoints by remember { mutableStateOf<List<NormalizedPoint>>(emptyList()) }
    var activeShapeStart by remember { mutableStateOf<NormalizedPoint?>(null) }
    var activeShapeEnd by remember { mutableStateOf<NormalizedPoint?>(null) }

    var showSignatureDialog by remember { mutableStateOf(startWithSignature) }
    var pendingTextPosition by remember { mutableStateOf<NormalizedPoint?>(null) }
    var editingTextBox by remember { mutableStateOf<NormalizedTextBox?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var activeSaveJob by remember { mutableStateOf<Job?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    val hasUnsavedChanges = undoStack.isNotEmpty() ||
        strokes.isNotEmpty() ||
        shapes.isNotEmpty() ||
        textBoxes.isNotEmpty() ||
        imageOverlays.isNotEmpty() ||
        pagePlan.any { it.extraRotationDegrees != 0 }

    val requestCloseEditor: () -> Unit = {
        if (isSaving) {
            activeSaveJob?.cancel()
            isSaving = false
        } else if (hasUnsavedChanges) {
            showDiscardDialog = true
        } else {
            onBack()
        }
    }

    BackHandler(enabled = isSaving || hasUnsavedChanges) {
        requestCloseEditor()
    }

    DisposableEffect(Unit) {
        onDispose {
            activeSaveJob?.cancel()
            val disp = displayBitmap
            val raw = pageBitmap
            displayBitmap = null
            pageBitmap = null
            if (disp != null && disp !== raw && !disp.isRecycled) disp.recycle()
            if (raw != null && !raw.isRecycled) raw.recycle()
            tempOverlayPaths.forEach { path ->
                repository.storage.unmarkPathActive(path)
                repository.storage.deleteFileSafely(path)
            }
        }
    }

    val imageOverlayPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val tmp = repository.importImageUriToTemp(uri, effects.performanceProfile)
                if (tmp != null) {
                    repository.storage.markPathActive(tmp.absolutePath)
                    tempOverlayPaths.add(tmp.absolutePath)
                    recordUndoPoint()
                    imageOverlays.add(
                        NormalizedImageOverlay(
                            id = UUID.randomUUID().toString(),
                            pageIndex = currentPageIdx,
                            imagePath = tmp.absolutePath,
                            xNorm = 0.25f,
                            yNorm = 0.35f,
                            widthNorm = 0.42f,
                            heightNorm = 0.22f,
                            isSignature = false
                        )
                    )
                    activeTool = EditorTool.PAN
                } else {
                    loadError = PrixErrorMapper.map(PrixOperation.IMPORT_IMAGE, null).toInlineMessage()
                }
            }
        }
    }

    LaunchedEffect(document?.id) {
        val doc = document ?: return@LaunchedEffect
        val file = File(doc.filePath)
        if (!file.exists()) {
            loadError = PrixErrorMapper.map(
                PrixOperation.OPEN_PDF,
                java.io.FileNotFoundException("Document missing from storage.")
            ).toDisplayMessage()
            return@LaunchedEffect
        }
        val info = repository.pdfEngine.inspectPdfInfo(file)
        if (info.isFailure) {
            loadError = PrixErrorMapper.map(PrixOperation.OPEN_PDF, info.exceptionOrNull()).toDisplayMessage()
            return@LaunchedEffect
        }
        val count = info.getOrNull()?.pageCount ?: repository.pdfEngine.getPageCount(file)
        if (pagePlan.isEmpty() && count > 0) {
            for (i in 0 until count) {
                pagePlan.add(PageTransformItem(originalPageIndex = i))
            }
        }
    }

    val currentPlanItem = pagePlan.getOrNull(currentPageIdx)
    val renderWidthPx = effects.performanceProfile.editorRenderWidthPx
    LaunchedEffect(document?.id, currentPlanItem?.originalPageIndex, renderWidthPx) {
        val doc = document ?: return@LaunchedEffect
        val item = currentPlanItem ?: return@LaunchedEffect
        val rendered = repository.pdfEngine.renderPdfPageBitmap(
            File(doc.filePath),
            item.originalPageIndex,
            renderWidthPx
        )
        val oldDisp = displayBitmap
        val oldRaw = pageBitmap
        pageBitmap = rendered
        displayBitmap = rendered
        if (oldDisp != null && oldDisp !== oldRaw && oldDisp !== rendered && !oldDisp.isRecycled) {
            oldDisp.recycle()
        }
        if (oldRaw != null && oldRaw !== rendered && !oldRaw.isRecycled) {
            oldRaw.recycle()
        }
    }

    val extraRot = currentPlanItem?.extraRotationDegrees ?: 0
    LaunchedEffect(pageBitmap, extraRot) {
        val raw = pageBitmap ?: return@LaunchedEffect
        if (raw.isRecycled) return@LaunchedEffect
        val rotated = if (extraRot % 360 == 0) raw else ImageProcessor.rotateBitmap(raw, extraRot)
        val prevDisp = displayBitmap
        displayBitmap = rotated
        if (prevDisp != null && prevDisp !== raw && prevDisp !== rotated && !prevDisp.isRecycled) {
            prevDisp.recycle()
        }
    }

    LiquidGlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
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
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            GlassIconButton(
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close editor",
                                onClick = requestCloseEditor
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Edit PDF", style = MaterialTheme.typography.titleMedium)
                                if (pagePlan.isNotEmpty()) {
                                    Text(
                                        "Page ${currentPageIdx + 1}/${pagePlan.size} • ${(zoomScale * 100).roundToInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = prixColors.secondaryText
                                    )
                                }
                            }
                            GlassIconButton(
                                icon = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Undo",
                                enabled = undoStack.isNotEmpty() && !isSaving,
                                onClick = ::performUndo,
                                modifier = Modifier.testTag("editor_undo_button")
                            )
                            GlassIconButton(
                                icon = Icons.AutoMirrored.Filled.Redo,
                                contentDescription = "Redo",
                                enabled = redoStack.isNotEmpty() && !isSaving,
                                onClick = ::performRedo,
                                modifier = Modifier.testTag("editor_redo_button")
                            )
                            Button(
                                onClick = { showSaveDialog = true },
                                enabled = pagePlan.isNotEmpty() && !isSaving && loadError == null,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.testTag("editor_save_button")
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save")
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Annotation & content overlays are saved onto a new PDF copy without altering original embedded fonts.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            },
            bottomBar = {
                Surface(
                    color = prixColors.glassElevated,
                    border = BorderStroke(1.dp, prixColors.glassOuterBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (pagePlan.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                itemsIndexed(pagePlan) { idx, _ ->
                                    GlassChip(
                                        label = "P${idx + 1}",
                                        selected = idx == currentPageIdx,
                                        onClick = { currentPageIdx = idx }
                                    )
                                }
                                item {
                                    IconButton(
                                        onClick = { zoomScale = (zoomScale - 0.25f).coerceAtLeast(1f).also { if (it == 1f) panOffset = Offset.Zero } },
                                        enabled = zoomScale > 1f,
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom out")
                                    }
                                }
                                item {
                                    IconButton(
                                        onClick = { zoomScale = (zoomScale + 0.25f).coerceAtMost(2.5f) },
                                        enabled = zoomScale < 2.5f,
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom in")
                                    }
                                }
                                item {
                                    IconButton(
                                        onClick = {
                                            recordUndoPoint()
                                            val item = pagePlan[currentPageIdx]
                                            pagePlan[currentPageIdx] = item.copy(
                                                extraRotationDegrees = (item.extraRotationDegrees + 90) % 360
                                            )
                                        },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate page")
                                    }
                                }
                                item {
                                    IconButton(
                                        onClick = {
                                            recordUndoPoint()
                                            val insertIdx = currentPageIdx + 1
                                            val item = pagePlan[currentPageIdx]
                                            pagePlan.add(insertIdx, item.copy())
                                            remapOverlayIndicesOnDuplicate(
                                                sourceIdx = currentPageIdx,
                                                insertIdx = insertIdx,
                                                strokes = strokes,
                                                shapes = shapes,
                                                textBoxes = textBoxes,
                                                imageOverlays = imageOverlays
                                            )
                                        },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate page")
                                    }
                                }
                                if (pagePlan.size > 1) {
                                    item {
                                        IconButton(
                                            onClick = {
                                                recordUndoPoint()
                                                val deletedIdx = currentPageIdx
                                                pagePlan.removeAt(deletedIdx)
                                                remapOverlayIndicesOnDelete(
                                                    deletedIdx = deletedIdx,
                                                    strokes = strokes,
                                                    shapes = shapes,
                                                    textBoxes = textBoxes,
                                                    imageOverlays = imageOverlays
                                                )
                                                currentPageIdx = currentPageIdx.coerceAtMost(pagePlan.lastIndex)
                                            },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete page", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }

                        if (activeTool in listOf(EditorTool.PEN, EditorTool.HIGHLIGHTER, EditorTool.RECTANGLE, EditorTool.CIRCLE, EditorTool.LINE, EditorTool.UNDERLINE, EditorTool.TEXT_BOX)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                val palette = listOf(
                                    0xFF7657F6.toInt() to "Purple ink",
                                    0xFF3985FF.toInt() to "Blue ink",
                                    0xFF171725.toInt() to "Black ink",
                                    0xFFE45161.toInt() to "Red ink",
                                    0xFF20B879.toInt() to "Green ink",
                                    0xFFF0A53B.toInt() to "Amber ink"
                                )
                                palette.forEach { (argb, label) ->
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .clickable(onClickLabel = "Select $label") { activeColorArgb = argb },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(Color(argb))
                                                .border(
                                                    width = if (activeColorArgb == argb) 2.5.dp else 1.dp,
                                                    color = if (activeColorArgb == argb) MaterialTheme.colorScheme.onSurface else Color.LightGray,
                                                    shape = CircleShape
                                                )
                                        )
                                    }
                                }
                                Slider(
                                    value = strokeThicknessNorm,
                                    onValueChange = { strokeThicknessNorm = it },
                                    valueRange = 0.003f..0.025f,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val toolSpecs = listOf(
                                Triple(EditorTool.PEN, "Pen", Icons.Filled.Brush),
                                Triple(EditorTool.HIGHLIGHTER, "Highlight", Icons.Filled.AutoFixHigh),
                                Triple(EditorTool.ERASER, "Eraser", Icons.Filled.Delete),
                                Triple(EditorTool.TEXT_BOX, "Text", Icons.Filled.TextFields),
                                Triple(EditorTool.SIGNATURE, "Sign", Icons.Filled.Draw),
                                Triple(EditorTool.IMAGE_OVERLAY, "Image", Icons.Filled.AddPhotoAlternate),
                                Triple(EditorTool.RECTANGLE, "Box", Icons.Filled.CropSquare),
                                Triple(EditorTool.CIRCLE, "Circle", Icons.Filled.Circle),
                                Triple(EditorTool.UNDERLINE, "Underline", Icons.Filled.FormatUnderlined),
                                Triple(EditorTool.LINE, "Line", Icons.Filled.HorizontalRule),
                                Triple(EditorTool.PAN, "Pan / Zoom", Icons.Filled.PanTool)
                            )
                            items(toolSpecs) { (tool, label, icon) ->
                                GlassChip(
                                    label = label,
                                    selected = activeTool == tool,
                                    leadingIcon = icon,
                                    onClick = {
                                        activeTool = tool
                                        if (tool == EditorTool.SIGNATURE) showSignatureDialog = true
                                        if (tool == EditorTool.IMAGE_OVERLAY) {
                                            imageOverlayPicker.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                    }
                                )
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
                    .padding(12.dp)
                    .clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
                val bmp = displayBitmap
                if (loadError != null && bmp == null) {
                    Text(
                        text = loadError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                } else if (bmp == null || bmp.isRecycled) {
                    CircularProgressIndicator()
                } else {
                    val aspect = bmp.width.toFloat() / bmp.height.coerceAtLeast(1)
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(aspect)
                            .graphicsLayer(
                                scaleX = zoomScale,
                                scaleY = zoomScale,
                                translationX = panOffset.x,
                                translationY = panOffset.y
                            )
                            .then(
                                if (activeTool == EditorTool.PAN) {
                                    Modifier.pointerInput(Unit) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            val nextScale = (zoomScale * zoom).coerceIn(1f, 2.5f)
                                            zoomScale = nextScale
                                            panOffset = if (nextScale <= 1.01f) {
                                                Offset.Zero
                                            } else {
                                                val maxOffset = 420f * (nextScale - 1f)
                                                Offset(
                                                    (panOffset.x + pan.x).coerceIn(-maxOffset, maxOffset),
                                                    (panOffset.y + pan.y).coerceIn(-maxOffset, maxOffset)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Modifier
                                }
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .border(1.dp, prixColors.glassOuterBorder, RoundedCornerShape(12.dp))
                    ) {
                        val density = LocalDensity.current
                        val canvasW = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
                        val canvasH = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "PDF editing page",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(activeTool, currentPageIdx, activeColorArgb, strokeThicknessNorm) {
                                    detectTapGestures { pos ->
                                        val nx = (pos.x / canvasW).coerceIn(0f, 1f)
                                        val ny = (pos.y / canvasH).coerceIn(0f, 1f)
                                        if (activeTool == EditorTool.TEXT_BOX) {
                                            pendingTextPosition = NormalizedPoint(nx, ny)
                                        } else if (activeTool == EditorTool.ERASER) {
                                            val snapshotBefore = captureSnapshot()
                                            val erased = eraseAt(nx, ny, currentPageIdx, strokes, shapes, textBoxes, imageOverlays)
                                            if (erased) {
                                                undoStack.add(snapshotBefore)
                                                if (undoStack.size > 40) undoStack.removeAt(0)
                                                redoStack.clear()
                                            }
                                        }
                                    }
                                }
                                .pointerInput(activeTool, currentPageIdx, activeColorArgb, strokeThicknessNorm) {
                                    var eraserSnapshotTaken = false
                                    detectDragGestures(
                                        onDragStart = { start ->
                                            eraserSnapshotTaken = false
                                            val pt = NormalizedPoint(
                                                (start.x / canvasW).coerceIn(0f, 1f),
                                                (start.y / canvasH).coerceIn(0f, 1f)
                                            )
                                            when (activeTool) {
                                                EditorTool.PEN, EditorTool.HIGHLIGHTER -> activeStrokePoints = listOf(pt)
                                                EditorTool.RECTANGLE, EditorTool.CIRCLE, EditorTool.LINE, EditorTool.UNDERLINE -> {
                                                    activeShapeStart = pt
                                                    activeShapeEnd = pt
                                                }
                                                EditorTool.ERASER -> {
                                                    val snap = captureSnapshot()
                                                    if (eraseAt(pt.x, pt.y, currentPageIdx, strokes, shapes, textBoxes, imageOverlays)) {
                                                        undoStack.add(snap)
                                                        if (undoStack.size > 40) undoStack.removeAt(0)
                                                        redoStack.clear()
                                                        eraserSnapshotTaken = true
                                                    }
                                                }
                                                else -> Unit
                                            }
                                        },
                                        onDrag = { change, _ ->
                                            val pt = NormalizedPoint(
                                                (change.position.x / canvasW).coerceIn(0f, 1f),
                                                (change.position.y / canvasH).coerceIn(0f, 1f)
                                            )
                                            when (activeTool) {
                                                EditorTool.PEN, EditorTool.HIGHLIGHTER -> {
                                                    change.consume()
                                                    activeStrokePoints = activeStrokePoints + pt
                                                }
                                                EditorTool.RECTANGLE, EditorTool.CIRCLE, EditorTool.LINE, EditorTool.UNDERLINE -> {
                                                    change.consume()
                                                    activeShapeEnd = pt
                                                }
                                                EditorTool.ERASER -> {
                                                    change.consume()
                                                    val snap = if (!eraserSnapshotTaken) captureSnapshot() else null
                                                    if (eraseAt(pt.x, pt.y, currentPageIdx, strokes, shapes, textBoxes, imageOverlays) && snap != null) {
                                                        undoStack.add(snap)
                                                        if (undoStack.size > 40) undoStack.removeAt(0)
                                                        redoStack.clear()
                                                        eraserSnapshotTaken = true
                                                    }
                                                }
                                                else -> Unit
                                            }
                                        },
                                        onDragEnd = {
                                            eraserSnapshotTaken = false
                                            when (activeTool) {
                                                EditorTool.PEN, EditorTool.HIGHLIGHTER -> {
                                                    if (activeStrokePoints.size >= 2) {
                                                        recordUndoPoint()
                                                        val isHL = activeTool == EditorTool.HIGHLIGHTER
                                                        strokes.add(
                                                            NormalizedStroke(
                                                                id = UUID.randomUUID().toString(),
                                                                pageIndex = currentPageIdx,
                                                                points = activeStrokePoints,
                                                                colorArgb = activeColorArgb,
                                                                strokeWidthNorm = if (isHL) strokeThicknessNorm * 3.2f else strokeThicknessNorm,
                                                                alpha = if (isHL) 0.35f else 1f,
                                                                isHighlighter = isHL
                                                            )
                                                        )
                                                    }
                                                    activeStrokePoints = emptyList()
                                                }
                                                EditorTool.RECTANGLE, EditorTool.CIRCLE, EditorTool.LINE, EditorTool.UNDERLINE -> {
                                                    val s = activeShapeStart
                                                    val e = activeShapeEnd
                                                    if (s != null && e != null) {
                                                        recordUndoPoint()
                                                        val kind = when (activeTool) {
                                                            EditorTool.CIRCLE -> ShapeKind.CIRCLE
                                                            EditorTool.LINE -> ShapeKind.LINE
                                                            EditorTool.UNDERLINE -> ShapeKind.UNDERLINE
                                                            else -> ShapeKind.RECTANGLE
                                                        }
                                                        shapes.add(
                                                            NormalizedShape(
                                                                id = UUID.randomUUID().toString(),
                                                                pageIndex = currentPageIdx,
                                                                kind = kind,
                                                                startX = s.x,
                                                                startY = s.y,
                                                                endX = e.x,
                                                                endY = if (kind == ShapeKind.UNDERLINE) s.y else e.y,
                                                                colorArgb = activeColorArgb,
                                                                strokeWidthNorm = strokeThicknessNorm
                                                            )
                                                        )
                                                    }
                                                    activeShapeStart = null
                                                    activeShapeEnd = null
                                                }
                                                else -> Unit
                                            }
                                        },
                                        onDragCancel = {
                                            eraserSnapshotTaken = false
                                            activeStrokePoints = emptyList()
                                            activeShapeStart = null
                                            activeShapeEnd = null
                                        }
                                    )
                                }
                        ) {
                            strokes.filter { it.pageIndex == currentPageIdx }.forEach { s ->
                                drawNormalizedStroke(s, size)
                            }
                            if (activeStrokePoints.size >= 2) {
                                val isHL = activeTool == EditorTool.HIGHLIGHTER
                                drawNormalizedStroke(
                                    NormalizedStroke(
                                        id = "live",
                                        pageIndex = currentPageIdx,
                                        points = activeStrokePoints,
                                        colorArgb = activeColorArgb,
                                        strokeWidthNorm = if (isHL) strokeThicknessNorm * 3.2f else strokeThicknessNorm,
                                        alpha = if (isHL) 0.35f else 1f,
                                        isHighlighter = isHL
                                    ),
                                    size
                                )
                            }

                            shapes.filter { it.pageIndex == currentPageIdx }.forEach { sh ->
                                drawNormalizedShape(sh, size)
                            }
                            val st = activeShapeStart
                            val en = activeShapeEnd
                            if (st != null && en != null) {
                                val kind = when (activeTool) {
                                    EditorTool.CIRCLE -> ShapeKind.CIRCLE
                                    EditorTool.LINE -> ShapeKind.LINE
                                    EditorTool.UNDERLINE -> ShapeKind.UNDERLINE
                                    else -> ShapeKind.RECTANGLE
                                }
                                drawNormalizedShape(
                                    NormalizedShape("live", currentPageIdx, kind, st.x, st.y, en.x, if (kind == ShapeKind.UNDERLINE) st.y else en.y, activeColorArgb, strokeThicknessNorm),
                                    size
                                )
                            }
                        }

                        imageOverlays.filter { it.pageIndex == currentPageIdx }.forEach { overlay ->
                            val idx = imageOverlays.indexOfFirst { it.id == overlay.id }
                            AsyncImage(
                                model = File(overlay.imagePath),
                                contentDescription = if (overlay.isSignature) "Signature overlay" else "Image overlay",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            (overlay.xNorm * canvasW).roundToInt(),
                                            (overlay.yNorm * canvasH).roundToInt()
                                        )
                                    }
                                    .size(
                                        width = with(density) { (overlay.widthNorm * canvasW).toDp() },
                                        height = with(density) { (overlay.heightNorm * canvasH).toDp() }
                                    )
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                    .pointerInput(overlay.id) {
                                        detectDragGestures(
                                            onDragStart = { recordUndoPoint() }
                                        ) { change, drag ->
                                            change.consume()
                                            if (idx >= 0) {
                                                val cur = imageOverlays[idx]
                                                imageOverlays[idx] = cur.copy(
                                                    xNorm = (cur.xNorm + drag.x / canvasW).coerceIn(0f, 1f - cur.widthNorm),
                                                    yNorm = (cur.yNorm + drag.y / canvasH).coerceIn(0f, 1f - cur.heightNorm)
                                                )
                                            }
                                        }
                                    }
                            )
                        }

                        textBoxes.filter { it.pageIndex == currentPageIdx }.forEach { tb ->
                            val idx = textBoxes.indexOfFirst { it.id == tb.id }
                            Box(
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            (tb.xNorm * canvasW).roundToInt(),
                                            (tb.yNorm * canvasH).roundToInt()
                                        )
                                    }
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .pointerInput(tb.id) {
                                        detectTapGestures { editingTextBox = tb }
                                    }
                                    .pointerInput(tb.id) {
                                        detectDragGestures(
                                            onDragStart = { recordUndoPoint() }
                                        ) { change, drag ->
                                            change.consume()
                                            if (idx >= 0) {
                                                val cur = textBoxes[idx]
                                                textBoxes[idx] = cur.copy(
                                                    xNorm = (cur.xNorm + drag.x / canvasW).coerceIn(0f, 0.9f),
                                                    yNorm = (cur.yNorm + drag.y / canvasH).coerceIn(0f, 0.92f)
                                                )
                                            }
                                        }
                                    }
                            ) {
                                Text(
                                    text = tb.text,
                                    color = Color(tb.colorArgb),
                                    fontSize = (tb.fontSizePt * 0.85f).sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSignatureDialog) {
        SignaturePadDialog(
            repository = repository,
            savedSignatures = savedSignatures,
            onDismiss = {
                showSignatureDialog = false
                if (activeTool == EditorTool.SIGNATURE) activeTool = EditorTool.PAN
            },
            onSignatureSelected = { imgPath ->
                recordUndoPoint()
                imageOverlays.add(
                    NormalizedImageOverlay(
                        id = UUID.randomUUID().toString(),
                        pageIndex = currentPageIdx,
                        imagePath = imgPath,
                        xNorm = 0.30f,
                        yNorm = 0.65f,
                        widthNorm = 0.38f,
                        heightNorm = 0.16f,
                        isSignature = true
                    )
                )
                showSignatureDialog = false
                activeTool = EditorTool.PAN
            }
        )
    }

    pendingTextPosition?.let { pt ->
        TextBoxDialog(
            initialText = "",
            initialSizePt = 16f,
            onDismiss = { pendingTextPosition = null },
            onConfirm = { text, sizePt ->
                recordUndoPoint()
                textBoxes.add(
                    NormalizedTextBox(
                        id = UUID.randomUUID().toString(),
                        pageIndex = currentPageIdx,
                        text = text,
                        xNorm = pt.x,
                        yNorm = pt.y,
                        fontSizePt = sizePt,
                        colorArgb = activeColorArgb
                    )
                )
                pendingTextPosition = null
            }
        )
    }

    editingTextBox?.let { tb ->
        TextBoxDialog(
            initialText = tb.text,
            initialSizePt = tb.fontSizePt,
            onDismiss = { editingTextBox = null },
            onDelete = {
                recordUndoPoint()
                textBoxes.removeAll { it.id == tb.id }
                editingTextBox = null
            },
            onConfirm = { text, sizePt ->
                val idx = textBoxes.indexOfFirst { it.id == tb.id }
                if (idx >= 0) {
                    recordUndoPoint()
                    textBoxes[idx] = tb.copy(text = text, fontSizePt = sizePt, colorArgb = activeColorArgb)
                }
                editingTextBox = null
            }
        )
    }

    if (showDiscardDialog) {
        GlassDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = "Discard Unsaved Edits?",
            content = {
                Text("You have unsaved annotations or page changes on this document. Close without saving?")
            },
            confirmButton = {
                Button(onClick = {
                    showDiscardDialog = false
                    onBack()
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Keep Editing") }
            }
        )
    }

    if (showSaveDialog && document != null) {
        var outName by remember(document!!.name) { mutableStateOf("${document!!.name}_edited") }
        var overwrite by remember { mutableStateOf(false) }

        GlassDialog(
            onDismissRequest = { if (!isSaving) showSaveDialog = false },
            title = "Export Edited PDF",
            content = {
                if (!overwrite) {
                    OutlinedTextField(
                        value = outName,
                        onValueChange = { outName = it },
                        label = { Text("New File Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = overwrite, onCheckedChange = { overwrite = it })
                    Text("Overwrite original file instead of creating a copy")
                }
                if (saveError != null) {
                    Text(saveError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    enabled = !isSaving && (overwrite || outName.isNotBlank()),
                    onClick = {
                        activeSaveJob?.cancel()
                        activeSaveJob = scope.launch {
                            isSaving = true
                            saveError = null
                            try {
                                val res = repository.applyPdfEdits(
                                    docId = document!!.id,
                                    outputName = outName,
                                    overwriteOriginal = overwrite,
                                    pagePlan = pagePlan.toList(),
                                    strokes = strokes.toList(),
                                    textBoxes = textBoxes.toList(),
                                    shapes = shapes.toList(),
                                    imageOverlays = imageOverlays.toList()
                                )
                                res.onSuccess { savedDoc ->
                                    showSaveDialog = false
                                    onSavedDocument(savedDoc)
                                }.onFailure { err ->
                                    saveError = PrixErrorMapper.map(PrixOperation.EDIT_PDF, err).toDisplayMessage()
                                }
                            } catch (ce: CancellationException) {
                                saveError = PrixErrorMapper.map(PrixOperation.EDIT_PDF, ce).toInlineMessage()
                            } finally {
                                isSaving = false
                            }
                        }
                    }
                ) {
                    Text(if (isSaving) "Saving…" else "Save PDF")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (isSaving) {
                            activeSaveJob?.cancel()
                        } else {
                            showSaveDialog = false
                        }
                    }
                ) { Text("Cancel") }
            }
        )
    }
}

private fun remapOverlayIndicesOnDelete(
    deletedIdx: Int,
    strokes: MutableList<NormalizedStroke>,
    shapes: MutableList<NormalizedShape>,
    textBoxes: MutableList<NormalizedTextBox>,
    imageOverlays: MutableList<NormalizedImageOverlay>
) {
    strokes.removeAll { it.pageIndex == deletedIdx }
    for (i in strokes.indices) {
        if (strokes[i].pageIndex > deletedIdx) {
            strokes[i] = strokes[i].copy(pageIndex = strokes[i].pageIndex - 1)
        }
    }
    shapes.removeAll { it.pageIndex == deletedIdx }
    for (i in shapes.indices) {
        if (shapes[i].pageIndex > deletedIdx) {
            shapes[i] = shapes[i].copy(pageIndex = shapes[i].pageIndex - 1)
        }
    }
    textBoxes.removeAll { it.pageIndex == deletedIdx }
    for (i in textBoxes.indices) {
        if (textBoxes[i].pageIndex > deletedIdx) {
            textBoxes[i] = textBoxes[i].copy(pageIndex = textBoxes[i].pageIndex - 1)
        }
    }
    imageOverlays.removeAll { it.pageIndex == deletedIdx }
    for (i in imageOverlays.indices) {
        if (imageOverlays[i].pageIndex > deletedIdx) {
            imageOverlays[i] = imageOverlays[i].copy(pageIndex = imageOverlays[i].pageIndex - 1)
        }
    }
}

private fun remapOverlayIndicesOnDuplicate(
    sourceIdx: Int,
    insertIdx: Int,
    strokes: MutableList<NormalizedStroke>,
    shapes: MutableList<NormalizedShape>,
    textBoxes: MutableList<NormalizedTextBox>,
    imageOverlays: MutableList<NormalizedImageOverlay>
) {
    for (i in strokes.indices) {
        if (strokes[i].pageIndex >= insertIdx) {
            strokes[i] = strokes[i].copy(pageIndex = strokes[i].pageIndex + 1)
        }
    }
    strokes.addAll(strokes.filter { it.pageIndex == sourceIdx }.map {
        it.copy(id = UUID.randomUUID().toString(), pageIndex = insertIdx)
    })

    for (i in shapes.indices) {
        if (shapes[i].pageIndex >= insertIdx) {
            shapes[i] = shapes[i].copy(pageIndex = shapes[i].pageIndex + 1)
        }
    }
    shapes.addAll(shapes.filter { it.pageIndex == sourceIdx }.map {
        it.copy(id = UUID.randomUUID().toString(), pageIndex = insertIdx)
    })

    for (i in textBoxes.indices) {
        if (textBoxes[i].pageIndex >= insertIdx) {
            textBoxes[i] = textBoxes[i].copy(pageIndex = textBoxes[i].pageIndex + 1)
        }
    }
    textBoxes.addAll(textBoxes.filter { it.pageIndex == sourceIdx }.map {
        it.copy(id = UUID.randomUUID().toString(), pageIndex = insertIdx)
    })

    for (i in imageOverlays.indices) {
        if (imageOverlays[i].pageIndex >= insertIdx) {
            imageOverlays[i] = imageOverlays[i].copy(pageIndex = imageOverlays[i].pageIndex + 1)
        }
    }
    imageOverlays.addAll(imageOverlays.filter { it.pageIndex == sourceIdx }.map {
        it.copy(id = UUID.randomUUID().toString(), pageIndex = insertIdx)
    })
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNormalizedStroke(
    stroke: NormalizedStroke,
    canvasSize: Size
) {
    if (stroke.points.size < 2) return
    val path = Path().apply {
        val f = stroke.points.first()
        moveTo(f.x * canvasSize.width, f.y * canvasSize.height)
        stroke.points.drop(1).forEach { pt ->
            lineTo(pt.x * canvasSize.width, pt.y * canvasSize.height)
        }
    }
    drawPath(
        path = path,
        color = Color(stroke.colorArgb).copy(alpha = stroke.alpha),
        style = Stroke(
            width = (stroke.strokeWidthNorm * canvasSize.width).coerceIn(2f, 48f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNormalizedShape(
    shape: NormalizedShape,
    canvasSize: Size
) {
    val x1 = shape.startX * canvasSize.width
    val y1 = shape.startY * canvasSize.height
    val x2 = shape.endX * canvasSize.width
    val y2 = shape.endY * canvasSize.height
    val sw = (shape.strokeWidthNorm * canvasSize.width).coerceIn(2f, 24f)
    val col = Color(shape.colorArgb)
    when (shape.kind) {
        ShapeKind.RECTANGLE -> {
            drawRect(
                color = col,
                topLeft = Offset(min(x1, x2), min(y1, y2)),
                size = Size(abs(x2 - x1), abs(y2 - y1)),
                style = Stroke(width = sw)
            )
        }
        ShapeKind.CIRCLE -> {
            drawOval(
                color = col,
                topLeft = Offset(min(x1, x2), min(y1, y2)),
                size = Size(abs(x2 - x1), abs(y2 - y1)),
                style = Stroke(width = sw)
            )
        }
        ShapeKind.LINE, ShapeKind.UNDERLINE -> {
            drawLine(color = col, start = Offset(x1, y1), end = Offset(x2, y2), strokeWidth = sw, cap = StrokeCap.Round)
        }
    }
}

private fun eraseAt(
    nx: Float,
    ny: Float,
    pageIndex: Int,
    strokes: MutableList<NormalizedStroke>,
    shapes: MutableList<NormalizedShape>,
    textBoxes: MutableList<NormalizedTextBox>,
    imageOverlays: MutableList<NormalizedImageOverlay>
): Boolean {
    val tol = 0.045f
    var removed = false
    if (strokes.removeAll { s ->
            s.pageIndex == pageIndex && s.points.any { abs(it.x - nx) < tol && abs(it.y - ny) < tol }
        }) removed = true
    if (shapes.removeAll { sh ->
            sh.pageIndex == pageIndex &&
                nx in (min(sh.startX, sh.endX) - tol)..(maxOf(sh.startX, sh.endX) + tol) &&
                ny in (min(sh.startY, sh.endY) - tol)..(maxOf(sh.startY, sh.endY) + tol)
        }) removed = true
    if (textBoxes.removeAll { tb ->
            tb.pageIndex == pageIndex && abs(tb.xNorm - nx) < 0.14f && abs(tb.yNorm - ny) < 0.07f
        }) removed = true
    if (imageOverlays.removeAll { ov ->
            ov.pageIndex == pageIndex && nx in ov.xNorm..(ov.xNorm + ov.widthNorm) && ny in ov.yNorm..(ov.yNorm + ov.heightNorm)
        }) removed = true
    return removed
}

@Composable
private fun TextBoxDialog(
    initialText: String,
    initialSizePt: Float,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onConfirm: (String, Float) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    var sizePt by remember { mutableFloatStateOf(initialSizePt) }

    GlassDialog(
        onDismissRequest = onDismiss,
        title = if (initialText.isEmpty()) "Add Text Box" else "Edit Text Box",
        content = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Text content") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Text("Font Size: ${sizePt.toInt()} pt", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = sizePt,
                onValueChange = { sizePt = it },
                valueRange = 10f..34f
            )
        },
        confirmButton = {
            Button(enabled = text.isNotBlank(), onClick = { onConfirm(text.trim(), sizePt) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
