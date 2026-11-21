package com.example.prix.core.error

import android.database.sqlite.SQLiteException
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.CancellationException
import java.io.FileNotFoundException
import java.io.IOException

enum class PrixOperation {
    IMPORT_PDF,
    IMPORT_IMAGE,
    SCAN_CAPTURE,
    CREATE_PDF,
    OPEN_PDF,
    EDIT_PDF,
    MERGE_PDF,
    SPLIT_PDF,
    COMPRESS_PDF,
    ORGANIZE_PDF,
    PDF_TO_IMAGES,
    WATERMARK_PDF,
    PAGE_NUMBERS_PDF,
    ENCRYPT_PDF,
    DECRYPT_PDF,
    REPAIR_PDF,
    OCR_EXTRACT,
    IMAGE_TOOLKIT,
    EXPORT_FILE,
    SHARE_FILE,
    DELETE_DOCUMENT,
    DATABASE_OPERATION
}

data class PrixUserError(
    val title: String,
    val whatHappened: String,
    val operationFailed: String,
    val originalSafetyNote: String,
    val nextStep: String
) {
    fun toDisplayMessage(): String = buildString {
        append(whatHappened)
        append("\n• Operation: ").append(operationFailed)
        append("\n• File Safety: ").append(originalSafetyNote)
        append("\n• Next Step: ").append(nextStep)
    }

    fun toInlineMessage(): String = "$whatHappened $originalSafetyNote $nextStep"
}

class InsufficientStorageException(message: String = "Not enough device storage space.") : IOException(message)
class ProtectedPdfException(message: String = "Document is password-protected.") : IllegalStateException(message)
class CorruptedDocumentException(message: String = "Document could not be parsed.") : IllegalStateException(message)

object PrixErrorMapper {

    private const val TAG = "PRIX_DIAG"

    fun logWarning(category: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            Log.w(TAG, "Operation warning [$category]: ${throwable?.javaClass?.simpleName ?: "unknown"}")
        }
    }

    fun map(operation: PrixOperation, throwable: Throwable?): PrixUserError {
        if (throwable != null && throwable !is CancellationException) {
            logWarning(operation.name, throwable)
        }

        val opLabel = when (operation) {
            PrixOperation.IMPORT_PDF -> "Import PDF"
            PrixOperation.IMPORT_IMAGE -> "Import image"
            PrixOperation.SCAN_CAPTURE -> "Document camera capture"
            PrixOperation.CREATE_PDF -> "Create PDF"
            PrixOperation.OPEN_PDF -> "Open PDF"
            PrixOperation.EDIT_PDF -> "Save PDF edits"
            PrixOperation.MERGE_PDF -> "Merge PDFs"
            PrixOperation.SPLIT_PDF -> "Split PDF"
            PrixOperation.COMPRESS_PDF -> "Compress PDF"
            PrixOperation.ORGANIZE_PDF -> "Organize PDF pages"
            PrixOperation.PDF_TO_IMAGES -> "Convert PDF pages to images"
            PrixOperation.WATERMARK_PDF -> "Add watermark"
            PrixOperation.PAGE_NUMBERS_PDF -> "Add page numbers"
            PrixOperation.ENCRYPT_PDF -> "Protect PDF with password"
            PrixOperation.DECRYPT_PDF -> "Unlock PDF"
            PrixOperation.REPAIR_PDF -> "Repair PDF"
            PrixOperation.OCR_EXTRACT -> "On-device OCR text extraction"
            PrixOperation.IMAGE_TOOLKIT -> "Save processed image"
            PrixOperation.EXPORT_FILE -> "Export document"
            PrixOperation.SHARE_FILE -> "Share document"
            PrixOperation.DELETE_DOCUMENT -> "Delete document"
            PrixOperation.DATABASE_OPERATION -> "Update local library"
        }

        val rawMsg = throwable?.message.orEmpty()
        val lower = rawMsg.lowercase()

        if (throwable is CancellationException) {
            return PrixUserError(
                title = "Operation Cancelled",
                whatHappened = "The task was cancelled before completion.",
                operationFailed = opLabel,
                originalSafetyNote = "Your original documents were left untouched, and partial outputs were cleaned up.",
                nextStep = "Start the task again whenever you are ready."
            )
        }

        if (throwable is OutOfMemoryError || lower.contains("outofmemory") || lower.contains("bitmap size exceeds")) {
            return PrixUserError(
                title = "Memory Limit Reached",
                whatHappened = "This page or image batch exceeded available device memory.",
                operationFailed = opLabel,
                originalSafetyNote = "Your original files remain untouched and safe.",
                nextStep = "Switch Performance Mode to Low in Settings or process fewer pages at a lower quality setting."
            )
        }

        if (throwable is InsufficientStorageException ||
            lower.contains("enospc") ||
            lower.contains("no space") ||
            lower.contains("not enough storage")
        ) {
            return PrixUserError(
                title = "Insufficient Storage Space",
                whatHappened = "Your device does not have enough free storage to finish writing the output file.",
                operationFailed = opLabel,
                originalSafetyNote = "Your original files were preserved safely without corruption.",
                nextStep = "Free up device storage or clear temporary files in Settings, then try again."
            )
        }

        if (throwable is SecurityException || lower.contains("permission")) {
            return PrixUserError(
                title = "Access Permission Needed",
                whatHappened = "Android denied access to the selected file or hardware resource.",
                operationFailed = opLabel,
                originalSafetyNote = "Your saved PRIX documents were not modified.",
                nextStep = "Grant the requested permission or re-select the file using the system picker."
            )
        }

        if (lower.contains("incorrect password") || lower.contains("cannot decrypt") || lower.contains("invalidpassword")) {
            return PrixUserError(
                title = "Incorrect PDF Password",
                whatHappened = "The password entered did not match this PDF.",
                operationFailed = opLabel,
                originalSafetyNote = "The protected PDF was left unchanged.",
                nextStep = "Verify the password with the document owner and try again."
            )
        }

        if (throwable is ProtectedPdfException ||
            lower.contains("password-protected") ||
            lower.contains("encrypted") ||
            lower.contains("unlock")
        ) {
            return PrixUserError(
                title = "Password-Protected PDF",
                whatHappened = "This PDF is encrypted and cannot be modified or inspected without unlocking it first.",
                operationFailed = opLabel,
                originalSafetyNote = "Your original encrypted PDF remains intact.",
                nextStep = "Use the Unlock PDF tool with the authorized password, then retry."
            )
        }

        if (throwable is FileNotFoundException || lower.contains("no longer exists") || lower.contains("missing")) {
            return PrixUserError(
                title = "Source File Missing",
                whatHappened = "The underlying file could not be found in private storage.",
                operationFailed = opLabel,
                originalSafetyNote = "No other files in your vault were altered.",
                nextStep = "Re-import the file from your device or remove the stale entry."
            )
        }

        if (throwable is SQLiteException || operation == PrixOperation.DATABASE_OPERATION) {
            return PrixUserError(
                title = "Local Library Error",
                whatHappened = "Could not update the local document index safely.",
                operationFailed = opLabel,
                originalSafetyNote = "Your PDF and image files remain safe inside local storage.",
                nextStep = "Close and reopen the screen, or restart PRIX if the issue continues."
            )
        }

        if (throwable is CorruptedDocumentException ||
            lower.contains("corrupt") ||
            lower.contains("valid pdf header") ||
            lower.contains("header") ||
            lower.contains("eof") ||
            lower.contains("xref") ||
            lower.contains("0 pages")
        ) {
            return PrixUserError(
                title = "Invalid or Corrupted PDF",
                whatHappened = "This file is missing valid PDF structure or appears damaged.",
                operationFailed = opLabel,
                originalSafetyNote = "Your original file was not overwritten.",
                nextStep = "Try running Repair PDF on a copy or re-exporting the source file."
            )
        }

        if (operation == PrixOperation.IMPORT_IMAGE || lower.contains("decode image") || lower.contains("unsupported")) {
            return PrixUserError(
                title = "Unsupported or Unreadable Image",
                whatHappened = "PRIX could not decode the selected image file.",
                operationFailed = opLabel,
                originalSafetyNote = "Your existing documents remain untouched.",
                nextStep = "Choose a standard JPEG, PNG, or WebP image and try again."
            )
        }

        if (operation == PrixOperation.SCAN_CAPTURE || lower.contains("camera")) {
            return PrixUserError(
                title = "Camera Unavailable",
                whatHappened = "The device camera could not be opened or failed to capture a frame.",
                operationFailed = opLabel,
                originalSafetyNote = "Your previously captured pages remain safe.",
                nextStep = "Check camera permissions in Android Settings or import photos from Gallery instead."
            )
        }

        if (throwable is IllegalArgumentException && rawMsg.isNotBlank()) {
            return PrixUserError(
                title = "Check Input",
                whatHappened = rawMsg,
                operationFailed = opLabel,
                originalSafetyNote = "No files were changed.",
                nextStep = "Adjust the input options and try again."
            )
        }

        return PrixUserError(
            title = "$opLabel Failed",
            whatHappened = "An unexpected local processing error prevented this action from finishing.",
            operationFailed = opLabel,
            originalSafetyNote = "Your original documents remain intact and unmodified.",
            nextStep = "Retry the action, or inspect the file in PDF Info / Repair PDF."
        )
    }
}
