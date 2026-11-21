package com.example.prix.feature.pdfeditor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.prix.core.data.PrixRepository
import com.example.prix.core.database.SavedSignatureEntity
import com.example.prix.core.ui.GlassDialog
import com.example.ui.theme.LocalPrixColors
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SignaturePadDialog(
    repository: PrixRepository,
    savedSignatures: List<SavedSignatureEntity>,
    onDismiss: () -> Unit,
    onSignatureSelected: (imagePath: String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val prixColors = LocalPrixColors.current
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize(600, 260)) }
    var inkColor by remember { mutableIntStateOf(0xFF171725.toInt()) }
    var strokeWidth by remember { mutableFloatStateOf(7f) }
    var sigName by remember { mutableStateOf("My Signature") }

    val importImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val tmp = repository.importImageUriToTemp(uri)
                if (tmp != null) {
                    onSignatureSelected(tmp.absolutePath)
                }
            }
        }
    }

    GlassDialog(
        onDismissRequest = onDismiss,
        title = "Create or Select Signature",
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (savedSignatures.isNotEmpty()) {
                    Text("Saved Signatures", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(savedSignatures, key = { it.id }) { sig ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, prixColors.glassOuterBorder),
                                onClick = { onSignatureSelected(sig.imagePath) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(6.dp)
                                ) {
                                    AsyncImage(
                                        model = File(sig.imagePath),
                                        contentDescription = sig.name,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.size(width = 76.dp, height = 38.dp)
                                    )
                                    IconButton(
                                        onClick = { scope.launch { repository.deleteSignature(sig) } },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete signature", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Text("Draw Signature Below", style = MaterialTheme.typography.labelLarge)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .border(1.dp, prixColors.glassOuterBorder, RoundedCornerShape(16.dp))
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { start -> currentStroke = listOf(start) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentStroke = currentStroke + change.position
                                },
                                onDragEnd = {
                                    if (currentStroke.size >= 2) strokes.add(currentStroke)
                                    currentStroke = emptyList()
                                },
                                onDragCancel = { currentStroke = emptyList() }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val all = strokes + listOfNotNull(currentStroke.takeIf { it.size >= 2 })
                        all.forEach { pts ->
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(pts.first().x, pts.first().y)
                                pts.drop(1).forEach { lineTo(it.x, it.y) }
                            }
                            drawPath(
                                path = path,
                                color = Color(inkColor),
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }
                    if (strokes.isEmpty() && currentStroke.isEmpty()) {
                        Text(
                            text = "Sign here with your finger",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0xFF171725.toInt(), 0xFF1D4ED8.toInt(), 0xFF7657F6.toInt()).forEach { col ->
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color(col))
                                    .border(
                                        width = if (inkColor == col) 2.5.dp else 1.dp,
                                        color = if (inkColor == col) MaterialTheme.colorScheme.primary else Color.LightGray,
                                        shape = CircleShape
                                    )
                                    .clickable { inkColor = col }
                            )
                        }
                    }
                    Row {
                        IconButton(onClick = { strokes.clear() }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear signature")
                        }
                        IconButton(onClick = {
                            importImageLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }) {
                            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Import signature image")
                        }
                    }
                }

                OutlinedTextField(
                    value = sigName,
                    onValueChange = { sigName = it },
                    label = { Text("Signature label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Note: Drawn signatures are visual overlays and are not verified cryptographic digital certificates.",
                    style = MaterialTheme.typography.bodySmall,
                    color = prixColors.secondaryText
                )
            }
        },
        confirmButton = {
            Button(
                enabled = strokes.isNotEmpty(),
                onClick = {
                    scope.launch {
                        val bmp = renderSignatureToTransparentBitmap(
                            strokes = strokes.toList(),
                            width = canvasSize.width.coerceAtLeast(300),
                            height = canvasSize.height.coerceAtLeast(140),
                            colorArgb = inkColor,
                            strokeWidth = strokeWidth * 1.4f
                        )
                        val saved = repository.saveSignatureBitmap(sigName, bmp)
                        bmp.recycle()
                        saved.getOrNull()?.let { onSignatureSelected(it.imagePath) }
                    }
                }
            ) {
                Text("Use Signature")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun renderSignatureToTransparentBitmap(
    strokes: List<List<Offset>>,
    width: Int,
    height: Int,
    colorArgb: Int,
    strokeWidth: Float
): Bitmap {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorArgb
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.strokeWidth = strokeWidth
    }
    strokes.forEach { pts ->
        if (pts.size >= 2) {
            val path = Path().apply {
                moveTo(pts.first().x, pts.first().y)
                pts.drop(1).forEach { lineTo(it.x, it.y) }
            }
            canvas.drawPath(path, paint)
        }
    }
    return bmp
}
