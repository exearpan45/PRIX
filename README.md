# PRIX — Document Scanner, Photo-to-PDF Maker & PDF Editor

**Tagline:** *Scan. Create. Edit. Organize.*  
**Application ID:** `com.aistudio.prix.xkqmzp`  
**Version:** `1.0.0`

## Overview
PRIX is an offline-first, privacy-focused native Android application built with Kotlin, Jetpack Compose, Material 3, CameraX, Room, DataStore, WorkManager, Apache PDFBox-Android, and on-device ML Kit Text Recognition.

## Core Capabilities
- **Document Scanner**: Live CameraX preview, flash & lens toggle, 3s auto-capture option, gradient edge detection, interactive 4-corner perspective crop (`Matrix.setPolyToPoly`), 6 enhancement filters, brightness/contrast controls, multi-page capture, and PDF export.
- **Photo-to-PDF Maker**: Multi-image selection via Android Photo Picker, page reordering, rotation, paper sizes (A4, A3, Letter, Legal, Original), orientation, margins, quality, backgrounds, and aspect-ratio vs fit-to-page options.
- **PDF Viewer**: Memory-safe page rendering with `LruCache` + `PdfRenderer`, pinch/button zoom, fit modes, thumbnails, view rotation, text search (`PDFTextStripper`), jump-to-page, print (`PrintManager`), share (`FileProvider`), and SAF export.
- **PDF Editor & Signatures**: Freehand pen, highlighter, eraser, movable text boxes, shapes, image overlays, drawn & saved signatures, page rotation/duplication/deletion, and non-destructive copy export (or explicit overwrite).
- **PDF Tools Center**: Merge, Split, Compress (honest size comparison), Rotate, Reorder, Delete, Extract, PDF-to-Images (PNG/JPEG/WebP), Watermark, Page Numbers, Password Encrypt/Decrypt, PDF Info, Repair PDF, Duplicate, Print, OCR Text Extraction, and Image Toolkit.
- **Document Management**: Room-backed folders & subfolders, full-text local search (name, format, folder, OCR text), sorting, grid/list views, starring, multi-select batch operations.

## Architecture & Stabilization
- **Offline-First & Zero Network Telemetry**: No analytics SDKs, no ads, and no background uploads. `AndroidManifest.xml` does not request `INTERNET` permissions.
- **Backup & Device Transfer Rules**: `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml` explicitly exclude private scanned files (`prix_vault`) and the local document index (`prix_documents.db`) from Android cloud backups and automatic device transfers, while allowing UI preferences (theme, reduced motion, performance mode) to be restored cleanly.
- **Adaptive Performance Profiles**: Automatic device tier detection (`Low`, `Balanced`, `High`) and manual user override in **Settings -> Performance Mode** regulate PDF render widths, page bitmap cache counts, preview resolution, and ambient visual complexity.

## Known Limitations
- On-device ML Kit OCR bundled in this offline build supports Latin-script languages; non-Latin scripts require dedicated language models.
- Drawn signatures added inside PRIX are visual overlays, not cryptographic digital certificates.
- PDF text editing adds content overlays onto pages rather than rewriting original embedded PDF font glyphs.
- Hardware camera capture and physical printer output require testing on an Android emulator or physical device.

## Development Environment & Build Commands
- **JDK Requirement:** JDK 11–21 (verified with **JDK 21 / Temurin** and **Gradle 9.3.1**).
- **Local Checkout / Android Studio:** Open the project root in Android Studio or run via the bundled Gradle wrapper:
```bash
# Compile Kotlin sources
./gradlew :app:compileDebugKotlin

# Run all JVM unit & Robolectric UI tests
./gradlew :app:testDebugUnitTest

# Run Android Lint checks
./gradlew :app:lintDebug
```
*(In pre-configured CI/container environments where `gradle` is installed on `PATH`, substitute `gradle` for `./gradlew`.)*

