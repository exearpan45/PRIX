package com.example.prix.core.storage

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.prix.core.error.InsufficientStorageException
import com.example.prix.core.error.PrixErrorMapper
import com.example.prix.core.image.ImageProcessor
import com.example.prix.core.model.DocumentFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DocumentStorageManager(private val context: Context) {

    val vaultRoot: File = File(context.filesDir, "prix_vault").apply { mkdirs() }
    val documentsDir: File = File(vaultRoot, "documents").apply { mkdirs() }
    val thumbnailsDir: File = File(vaultRoot, "thumbs").apply { mkdirs() }
    val signaturesDir: File = File(vaultRoot, "signatures").apply { mkdirs() }
    val tempDir: File = File(context.cacheDir, "prix_tmp").apply { mkdirs() }

    fun createVaultFile(baseName: String, extension: String): File {
        val clean = sanitizeFileName(baseName).ifBlank { "Document_${timestampTag()}" }
        val ext = extension.lowercase(Locale.ROOT).trimStart('.')
        var candidate = File(documentsDir, "$clean.$ext")
        var counter = 1
        while (candidate.exists()) {
            candidate = File(documentsDir, "${clean}_$counter.$ext")
            counter++
        }
        return candidate
    }

    fun createSignatureFile(name: String): File {
        val clean = sanitizeFileName(name).ifBlank { "Signature" }
        return File(signaturesDir, "${clean}_${UUID.randomUUID().toString().take(8)}.png")
    }

    fun createTempImageFile(prefix: String = "scan_"): File {
        tempDir.mkdirs()
        return File(tempDir, "${prefix}${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
    }

    fun sanitizeFileName(raw: String): String {
        val noPath = raw.substringAfterLast('/').substringAfterLast('\\')
        return noPath
            .replace(Regex("[^a-zA-Z0-9._ -]"), "_")
            .trim()
            .trim('.')
            .take(80)
    }

    fun resolveDisplayName(uri: Uri, fallback: String): String {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) {
                            val value = cursor.getString(idx)
                            if (!value.isNullOrBlank()) return value
                        }
                    }
                }
        } catch (_: Exception) {
        }
        return fallback
    }

    suspend fun copyUriToVault(uri: Uri, targetExtension: String, preferredName: String? = null): File? =
        withContext(Dispatchers.IO) {
            var dest: File? = null
            try {
                val rawName = preferredName ?: resolveDisplayName(uri, "Imported_${timestampTag()}")
                val baseName = rawName.substringBeforeLast('.')
                val target = createVaultFile(baseName, targetExtension)
                dest = target
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(target).use { output ->
                        input.copyTo(output)
                    }
                } ?: return@withContext null
                if (!target.exists() || target.length() == 0L) {
                    target.delete()
                    return@withContext null
                }
                target
            } catch (e: Exception) {
                dest?.delete()
                PrixErrorMapper.logWarning("copyUriToVault", e)
                null
            }
        }

    fun isLikelyValidPdf(file: File): Boolean {
        if (!file.exists() || file.length() < 8L) return false
        return try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(16)
                val read = fis.read(header)
                if (read < 5) return false
                String(header, 0, read, Charsets.US_ASCII).contains("%PDF-")
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun generateThumbnail(file: File, format: DocumentFormat): String? =
        withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext null
            val thumbFile = File(thumbnailsDir, "thumb_${file.nameWithoutExtension}_${System.currentTimeMillis()}.jpg")
            return@withContext when (format) {
                DocumentFormat.PDF -> {
                    var pfd: ParcelFileDescriptor? = null
                    var renderer: PdfRenderer? = null
                    try {
                        pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                        renderer = PdfRenderer(pfd)
                        if (renderer.pageCount <= 0) return@withContext null
                        renderer.openPage(0).use { page ->
                            val targetW = 360
                            val targetH = ((targetW.toFloat() / page.width.coerceAtLeast(1)) * page.height)
                                .toInt().coerceIn(180, 560)
                            val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                            Canvas(bmp).drawColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            FileOutputStream(thumbFile).use { out ->
                                bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
                            }
                            bmp.recycle()
                        }
                        thumbFile.absolutePath
                    } catch (_: Throwable) {
                        thumbFile.delete()
                        null
                    } finally {
                        try { renderer?.close() } catch (_: Throwable) {}
                        try { pfd?.close() } catch (_: Throwable) {}
                    }
                }
                DocumentFormat.PNG, DocumentFormat.JPEG, DocumentFormat.WEBP -> {
                    val bmp = ImageProcessor.decodeSampledBitmap(file.absolutePath, 420) ?: return@withContext null
                    try {
                        FileOutputStream(thumbFile).use { out ->
                            bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
                        }
                        thumbFile.absolutePath
                    } catch (_: Exception) {
                        null
                    } finally {
                        bmp.recycle()
                    }
                }
                DocumentFormat.TXT -> null
            }
        }

    suspend fun exportFileToUri(sourceFile: File, targetUri: Uri): Boolean = withContext(Dispatchers.IO) {
        if (!sourceFile.exists()) return@withContext false
        try {
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(out)
                }
            } ?: return@withContext false
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getFileProviderUri(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun buildShareIntent(file: File, mimeType: String, title: String): Intent {
        val uri = getFileProviderUri(file)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            clipData = android.content.ClipData.newRawUri(title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(sendIntent, "Share $title").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun buildShareMultipleIntent(files: List<Pair<File, String>>, title: String = "Documents"): Intent {
        if (files.size <= 1) {
            val single = files.firstOrNull()
            return if (single != null) buildShareIntent(single.first, single.second, single.first.nameWithoutExtension)
            else Intent()
        }
        val uris = ArrayList<Uri>(files.size)
        val mimeTypes = files.map { it.second }.distinct()
        val commonMime = if (mimeTypes.size == 1) mimeTypes.first() else "*/*"
        files.forEach { (file, _) ->
            if (file.exists()) {
                uris.add(getFileProviderUri(file))
            }
        }
        val sendIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = commonMime
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_SUBJECT, title)
            if (uris.isNotEmpty()) {
                val cd = android.content.ClipData.newRawUri(title, uris.first())
                uris.drop(1).forEach { cd.addItem(android.content.ClipData.Item(it)) }
                clipData = cd
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(sendIntent, "Share ${uris.size} files").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun buildOpenWithIntent(file: File, mimeType: String): Intent {
        val uri = getFileProviderUri(file)
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            clipData = android.content.ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(viewIntent, "Open with").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun calculateDirectoryBytes(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun getAvailableDeviceBytes(): Long {
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBytes
        } catch (_: Exception) {
            0L
        }
    }

    fun hasSufficientStorage(requiredBytes: Long = 10L * 1024L * 1024L): Boolean {
        val avail = getAvailableDeviceBytes()
        // If StatFs cannot report (>0 check fails cleanly in test environments where stat returns 0 only on error), allow unless below floor
        return avail <= 0L || avail >= requiredBytes
    }

    fun requireSufficientStorage(estimatedBytes: Long = 8L * 1024L * 1024L) {
        val avail = getAvailableDeviceBytes()
        if (avail > 0L && avail < estimatedBytes) {
            throw InsufficientStorageException("Available storage is low for this operation.")
        }
    }

    fun markPathActive(path: String?) {
        if (path.isNullOrBlank()) return
        try {
            activeProtectedPaths.add(File(path).canonicalPath)
        } catch (_: Exception) {
            activeProtectedPaths.add(path)
        }
    }

    fun unmarkPathActive(path: String?) {
        if (path.isNullOrBlank()) return
        try {
            activeProtectedPaths.remove(File(path).canonicalPath)
        } catch (_: Exception) {
            activeProtectedPaths.remove(path)
        }
    }

    fun isOwnedPath(file: File): Boolean {
        return try {
            val canonicalTarget = file.canonicalPath
            val canonicalVault = vaultRoot.canonicalPath + File.separator
            val canonicalTemp = tempDir.canonicalPath + File.separator
            val canonicalCache = context.cacheDir.canonicalPath + File.separator
            val isOwnedExifTemp = canonicalTarget.startsWith(canonicalCache) &&
                file.name.startsWith("exif_") && file.name.endsWith(".img")
            canonicalTarget.startsWith(canonicalVault) ||
                canonicalTarget.startsWith(canonicalTemp) ||
                isOwnedExifTemp
        } catch (_: Exception) {
            false
        }
    }

    fun clearTempFiles(): Long {
        val before = calculateDirectoryBytes(tempDir)
        tempDir.listFiles()?.forEach { child ->
            if (!isPathActive(child)) {
                child.deleteRecursively()
            }
        }
        // Clean up any stale exif_*.img files in cacheDir older than 1 hour and not active
        val cutoff = System.currentTimeMillis() - 60L * 60L * 1000L
        context.cacheDir.listFiles()?.forEach { f ->
            if (f.isFile && f.name.startsWith("exif_") && f.name.endsWith(".img") &&
                f.lastModified() < cutoff && !isPathActive(f)
            ) {
                f.delete()
            }
        }
        return before
    }

    fun deleteFileSafely(path: String?): Boolean {
        if (path.isNullOrBlank()) return true
        return try {
            val file = File(path)
            if (!isOwnedPath(file)) {
                PrixErrorMapper.logWarning("deleteFileSafely_outside_scope")
                return false
            }
            unmarkPathActive(path)
            if (!file.exists()) return true
            file.delete()
        } catch (e: Exception) {
            PrixErrorMapper.logWarning("deleteFileSafely", e)
            false
        }
    }

    fun deleteFileQuietly(path: String?) {
        deleteFileSafely(path)
    }

    companion object {
        private val activeProtectedPaths: MutableSet<String> = ConcurrentHashMap.newKeySet()

        fun isPathActive(file: File): Boolean {
            return try {
                activeProtectedPaths.contains(file.canonicalPath)
            } catch (_: Exception) {
                activeProtectedPaths.contains(file.absolutePath)
            }
        }

        fun clearActiveRegistryForTest() {
            activeProtectedPaths.clear()
        }

        fun timestampTag(): String =
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

        fun formatFileSize(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024.0) return String.format(Locale.US, "%.2f MB", mb)
            val gb = mb / 1024.0
            return String.format(Locale.US, "%.2f GB", gb)
        }

        fun formatDateShort(epochMs: Long): String {
            if (epochMs <= 0L) return "Unknown"
            return SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(epochMs))
        }

        fun formatDateTime(epochMs: Long): String {
            if (epochMs <= 0L) return "Unknown"
            return SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.US).format(Date(epochMs))
        }
    }
}
