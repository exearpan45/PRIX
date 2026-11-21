# PRIX Privacy Policy

**Effective Date:** `[EFFECTIVE_DATE]`  
**Owner / Publisher:** `[APP_OWNER_OR_COMPANY_NAME]`  
**Privacy Contact Email:** `[PRIVACY_CONTACT_EMAIL]`  
**Support Contact Email:** `[SUPPORT_EMAIL]`  
**Public Policy URL:** `[PRIVACY_POLICY_URL]`

## 1. Privacy-First, Zero-Network Offline Architecture
PRIX processes documents strictly locally on your Android device. PRIX does not declare `INTERNET` permission, does not require user accounts, and makes zero network requests. Scanned pages, PDFs, signatures, images, and extracted OCR text remain on your device unless you explicitly share or export them using Android's system Share sheet, Print service, or Storage Access Framework (SAF).

## 2. Information Collected
PRIX does not collect personal profiles, analytics, crash telemetry, advertising identifiers, or document contents.

## 3. Permissions Used
- **Camera (`android.permission.CAMERA`)**: Requested only when you open the Document Scanner to capture document pages locally. Camera frames are never uploaded.
- **Photo & Document Selection**: Uses the zero-permission Android Photo Picker and Android Storage Access Framework (SAF), granting access only to specific files you explicitly select.

## 4. On-Device OCR Processing
Optical Character Recognition (OCR) runs strictly on-device via Google ML Kit Text Recognition (`Latin` script). Non-Latin scripts are not supported by the bundled offline model. Images and PDF pages never leave your device.

## 5. Android Backup & Device Transfer Exclusions
PRIX explicitly configures Android `backup_rules.xml` and `data_extraction_rules.xml` to **exclude** your private document vault (`prix_vault/`), thumbnails, saved signatures, and local Room database (`prix_documents.db`, including saved OCR text) from both Cloud Backup and Device-to-Device transfer. Only non-sensitive UI preferences (such as theme, paper defaults, and performance profile) stored in DataStore are eligible for Android backup.

## 6. Editor & Signature Disclosures
- **Text Boxes & Annotations**: PDF text additions place content overlays onto pages rather than rewriting original embedded PDF fonts or glyphs.
- **Drawn Signatures**: Signatures created or placed in PRIX are visual overlay graphics and are **not** verified cryptographic digital certificates.

## 7. Third-Party Libraries
PRIX bundles Apache PDFBox-Android, AndroidX Jetpack libraries (CameraX, Room, DataStore, Navigation, WorkManager, Core SplashScreen, ExifInterface), Coil, and Google ML Kit Text Recognition for local document processing.

## 8. Data Retention & Deletion
All documents remain inside your device's private app storage until you delete them in PRIX, clear temporary cache in Settings, or uninstall PRIX.

## 9. User Rights & Contact
For questions regarding this Privacy Policy or Google Play Store compliance inquiries, contact `[PRIVACY_CONTACT_EMAIL]`.
