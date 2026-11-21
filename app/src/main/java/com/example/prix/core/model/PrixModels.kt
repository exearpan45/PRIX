package com.example.prix.core.model

import kotlinx.serialization.Serializable

enum class ThemeMode(val label: String) {
    SYSTEM("System Default"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class PerformanceMode(val label: String) {
    AUTO("Auto"),
    LOW("Low (Speed)"),
    BALANCED("Balanced"),
    HIGH("High (Quality)")
}

enum class PerformanceProfile(
    val label: String,
    val pdfViewerWidthPx: Int,
    val pdfViewerCachePages: Int,
    val editorRenderWidthPx: Int,
    val imageDecodeMaxDim: Int,
    val previewDecodeMaxDim: Int,
    val ocrMaxDim: Int
) {
    LOW(
        label = "Low",
        pdfViewerWidthPx = 820,
        pdfViewerCachePages = 3,
        editorRenderWidthPx = 820,
        imageDecodeMaxDim = 1600,
        previewDecodeMaxDim = 720,
        ocrMaxDim = 1400
    ),
    BALANCED(
        label = "Balanced",
        pdfViewerWidthPx = 1100,
        pdfViewerCachePages = 6,
        editorRenderWidthPx = 1050,
        imageDecodeMaxDim = 2200,
        previewDecodeMaxDim = 960,
        ocrMaxDim = 1800
    ),
    HIGH(
        label = "High",
        pdfViewerWidthPx = 1400,
        pdfViewerCachePages = 10,
        editorRenderWidthPx = 1280,
        imageDecodeMaxDim = 2600,
        previewDecodeMaxDim = 1200,
        ocrMaxDim = 2200
    )
}

enum class PdfPageSize(val label: String, val widthPt: Float, val heightPt: Float) {
    A4("A4", 595f, 842f),
    A3("A3", 842f, 1191f),
    LETTER("Letter", 612f, 792f),
    LEGAL("Legal", 612f, 1008f),
    ORIGINAL("Original Size", 0f, 0f)
}

enum class PdfOrientation(val label: String) {
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

enum class PdfQuality(val label: String, val jpegQuality: Int, val maxDimensionPx: Int) {
    LOW("Compact (Low)", 55, 1400),
    BALANCED("Balanced", 78, 2000),
    HIGH("High Quality", 92, 2800)
}

enum class PdfMargin(val label: String, val points: Float) {
    NONE("None", 0f),
    NARROW("Narrow", 18f),
    STANDARD("Standard", 36f),
    WIDE("Wide", 54f)
}

enum class PageBackground(val label: String, val argb: Int) {
    WHITE("Pure White", android.graphics.Color.WHITE),
    WARM_CREAM("Warm Paper", 0xFFFAF7F0.toInt()),
    DARK_SLATE("Dark Slate", 0xFF191B28.toInt())
}

enum class ScanFilter(val label: String) {
    ORIGINAL("Original"),
    AUTO_ENHANCE("Auto Enhance"),
    DOCUMENT("Document"),
    BLACK_AND_WHITE("B&W"),
    GRAYSCALE("Grayscale"),
    COLOR_ENHANCE("Color+")
}

enum class DocumentFormat(val label: String, val extension: String, val mimeType: String) {
    PDF("PDF", "pdf", "application/pdf"),
    PNG("PNG", "png", "image/png"),
    JPEG("JPEG", "jpg", "image/jpeg"),
    WEBP("WEBP", "webp", "image/webp"),
    TXT("TXT", "txt", "text/plain")
}

enum class DocumentSortOption(val label: String) {
    DATE_DESC("Newest modified"),
    DATE_ASC("Oldest modified"),
    NAME_ASC("Name (A–Z)"),
    NAME_DESC("Name (Z–A)"),
    SIZE_DESC("Largest first"),
    SIZE_ASC("Smallest first")
}

enum class DocumentFilterTab(val label: String) {
    ALL("All"),
    RECENT_OPENED("Opened"),
    RECENT_MODIFIED("Modified"),
    STARRED("Starred")
}

enum class CompressionLevel(val label: String, val description: String, val dpi: Int, val jpegQuality: Int) {
    LOW("Low Compression", "Minimal quality loss, gentle file reduction", 180, 82),
    BALANCED("Balanced", "Recommended clarity and smaller file size", 140, 65),
    HIGH("High Compression", "Maximum reduction for sharing & email", 100, 42)
}

enum class ImageOutputFormat(val label: String, val extension: String, val mimeType: String) {
    JPEG("JPEG", "jpg", "image/jpeg"),
    PNG("PNG", "png", "image/png"),
    WEBP("WebP", "webp", "image/webp")
}

enum class WatermarkPosition(val label: String) {
    DIAGONAL_CENTER("Diagonal Center"),
    TOP_CENTER("Top Center"),
    BOTTOM_CENTER("Bottom Center")
}

enum class PageNumberPosition(val label: String) {
    BOTTOM_CENTER("Bottom Center"),
    BOTTOM_RIGHT("Bottom Right"),
    TOP_RIGHT("Top Right")
}

@Serializable
data class NormalizedPoint(val x: Float, val y: Float)

@Serializable
data class QuadPoints(
    val topLeft: NormalizedPoint = NormalizedPoint(0.08f, 0.08f),
    val topRight: NormalizedPoint = NormalizedPoint(0.92f, 0.08f),
    val bottomRight: NormalizedPoint = NormalizedPoint(0.92f, 0.92f),
    val bottomLeft: NormalizedPoint = NormalizedPoint(0.08f, 0.92f)
)

data class ScannedPageDraft(
    val id: String,
    val rawImagePath: String,
    val quad: QuadPoints = QuadPoints(),
    val rotationDegrees: Int = 0,
    val filter: ScanFilter = ScanFilter.AUTO_ENHANCE,
    val brightness: Float = 0f,
    val contrast: Float = 1f
)

data class PdfInfoMetadata(
    val fileName: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val pdfVersion: String,
    val title: String,
    val author: String,
    val creator: String,
    val producer: String,
    val isEncrypted: Boolean,
    val firstPageWidthPt: Float,
    val firstPageHeightPt: Float
)

data class StorageStats(
    val totalDocuments: Int = 0,
    val totalFolders: Int = 0,
    val vaultSizeBytes: Long = 0L,
    val tempCacheBytes: Long = 0L,
    val availableDeviceBytes: Long = 0L,
    val recentCount24h: Int = 0
)

enum class EditorTool {
    PAN,
    PEN,
    HIGHLIGHTER,
    ERASER,
    TEXT_BOX,
    RECTANGLE,
    CIRCLE,
    LINE,
    UNDERLINE,
    SIGNATURE,
    IMAGE_OVERLAY
}

data class NormalizedStroke(
    val id: String,
    val pageIndex: Int,
    val points: List<NormalizedPoint>,
    val colorArgb: Int,
    val strokeWidthNorm: Float,
    val alpha: Float,
    val isHighlighter: Boolean = false
)

data class NormalizedTextBox(
    val id: String,
    val pageIndex: Int,
    val text: String,
    val xNorm: Float,
    val yNorm: Float,
    val fontSizePt: Float,
    val colorArgb: Int
)

enum class ShapeKind { RECTANGLE, CIRCLE, LINE, UNDERLINE }

data class NormalizedShape(
    val id: String,
    val pageIndex: Int,
    val kind: ShapeKind,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val colorArgb: Int,
    val strokeWidthNorm: Float
)

data class NormalizedImageOverlay(
    val id: String,
    val pageIndex: Int,
    val imagePath: String,
    val xNorm: Float,
    val yNorm: Float,
    val widthNorm: Float,
    val heightNorm: Float,
    val isSignature: Boolean = false
)

data class PageTransformItem(
    val originalPageIndex: Int,
    val extraRotationDegrees: Int = 0
)

data class OcrResultData(
    val fullText: String,
    val blocks: List<String>,
    val note: String? = null
)

data class CompressionResult(
    val originalBytes: Long,
    val outputBytes: Long,
    val savedDocumentId: Long?,
    val reduced: Boolean,
    val message: String
)

data class ToolOutputResult(
    val title: String,
    val message: String,
    val createdDocumentIds: List<Long> = emptyList()
)
