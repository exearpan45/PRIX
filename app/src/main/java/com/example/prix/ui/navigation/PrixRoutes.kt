package com.example.prix.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object RouteHome

@Serializable
data class RouteDocuments(val focusSearch: Boolean = false)

@Serializable
data object RouteTools

@Serializable
data object RouteSettings

@Serializable
data object RouteScanner

@Serializable
data object RouteImageToPdf

@Serializable
data class RoutePdfViewer(val documentId: Long)

@Serializable
data class RoutePdfEditor(val documentId: Long)

@Serializable
data class RouteToolWorkspace(
    val toolId: String,
    val initialDocumentId: Long? = null
)

@Serializable
data class RouteLegal(val section: String)

object ToolIds {
    const val MERGE_PDF = "merge_pdf"
    const val SPLIT_PDF = "split_pdf"
    const val COMPRESS_PDF = "compress_pdf"
    const val ROTATE_PDF = "rotate_pdf"
    const val REORDER_PAGES = "reorder_pages"
    const val DELETE_PAGES = "delete_pages"
    const val EXTRACT_PAGES = "extract_pages"
    const val PDF_TO_IMAGES = "pdf_to_images"
    const val ADD_WATERMARK = "add_watermark"
    const val ADD_PAGE_NUMBERS = "add_page_numbers"
    const val ADD_PASSWORD = "add_password"
    const val REMOVE_PASSWORD = "remove_password"
    const val ADD_SIGNATURE = "add_signature"
    const val PDF_INFO = "pdf_info"
    const val REPAIR_PDF = "repair_pdf"
    const val DUPLICATE_PDF = "duplicate_pdf"
    const val PRINT_PDF = "print_pdf"
    const val OCR_EXTRACT = "ocr_extract"
    const val IMAGE_TOOLKIT = "image_toolkit"
    const val SIGNATURE_LIBRARY = "signature_library"
}

object LegalSections {
    const val PRIVACY = "privacy"
    const val TERMS = "terms"
    const val DISCLAIMER = "disclaimer"
    const val ABOUT = "about"
    const val LICENSES = "licenses"
}
