package com.example.prix.feature.legal

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.prix.core.datastore.PreferencesRepository
import com.example.prix.core.datastore.UserPreferences
import com.example.prix.core.ui.GlassButton
import com.example.prix.core.ui.GlassCard
import com.example.prix.core.ui.GlassTopBar
import com.example.prix.core.ui.LiquidGlassBackground
import com.example.prix.core.ui.PrixLogoMark
import com.example.prix.ui.navigation.LegalSections
import com.example.ui.theme.LocalPrixColors
import kotlinx.coroutines.launch

private const val PLACEHOLDER_SUPPORT_EMAIL = "[SUPPORT_EMAIL]"

@Composable
fun LegalDetailScreen(
    section: String,
    preferences: UserPreferences,
    preferencesRepo: PreferencesRepository,
    onBack: () -> Unit,
    onNavigateSection: (String) -> Unit
) {
    val title = when (section) {
        LegalSections.PRIVACY -> "Privacy Policy"
        LegalSections.TERMS -> "Terms & Conditions"
        LegalSections.DISCLAIMER -> "Disclaimer"
        LegalSections.ABOUT -> "About PRIX"
        LegalSections.LICENSES -> "Open-Source Licenses"
        else -> "Legal"
    }

    LiquidGlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                GlassTopBar(title = title, subtitle = "Offline-first & transparent", onBack = onBack)
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (section) {
                        LegalSections.PRIVACY -> PrivacyPolicyContent(preferences, preferencesRepo)
                        LegalSections.TERMS -> TermsContent()
                        LegalSections.DISCLAIMER -> DisclaimerContent()
                        LegalSections.ABOUT -> AboutPrixContent(onNavigateSection)
                        LegalSections.LICENSES -> LicensesContent()
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun LegalSectionBlock(heading: String, body: String) {
    GlassCard(
        shape = RoundedCornerShape(20.dp),
        elevated = false,
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(heading, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PrivacyPolicyContent(
    preferences: UserPreferences,
    preferencesRepo: PreferencesRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prixColors = LocalPrixColors.current
    var urlInput by remember(preferences.privacyPolicyUrl) { mutableStateOf(preferences.privacyPolicyUrl) }
    var actionNote by remember { mutableStateOf<String?>(null) }

    LegalSectionBlock(
        "1. Privacy-First Zero-Network Architecture",
        "PRIX operates offline-first without user accounts, cloud servers, or internet permissions. Scanned pages, PDFs, signatures, folders, and OCR text stay strictly inside your device's private app storage unless you explicitly share, print, or export them."
    )
    LegalSectionBlock(
        "2. Information Collected & Diagnostics",
        "PRIX does not collect, sell, or transmit personal data, document contents, advertising identifiers, or usage telemetry."
    )
    LegalSectionBlock(
        "3. Camera Permission",
        "Camera access (android.permission.CAMERA) is requested only when you open the Document Scanner. Captured frames are processed locally on-device."
    )
    LegalSectionBlock(
        "4. Photo & Document Access",
        "PRIX uses the zero-permission Android Photo Picker and Storage Access Framework (SAF) so you grant access only to the specific files you choose."
    )
    LegalSectionBlock(
        "5. On-Device OCR Processing",
        "Text extraction runs locally on your device using Google ML Kit Text Recognition (Latin script). Non-Latin scripts are not supported by the bundled offline model. Images and PDFs are never uploaded."
    )
    LegalSectionBlock(
        "6. Android Backup & Device Transfer Rules",
        "PRIX explicitly excludes private vault files (PDFs, images, thumbnails, signatures) and the local document database (including OCR text) from Android Cloud Backup and implicit Device-to-Device transfers. Only non-sensitive DataStore UI preferences remain backup-eligible."
    )
    LegalSectionBlock(
        "7. Third-Party Libraries",
        "PRIX bundles Apache PDFBox-Android, AndroidX CameraX, Room, DataStore, WorkManager, Coil, and ML Kit Text Recognition for local document processing."
    )
    LegalSectionBlock(
        "8. Data Retention, User Rights & Deletion",
        "You have full control over all locally stored documents. Deleting files inside PRIX, clearing temporary cache in Settings, or uninstalling PRIX permanently removes local vault data."
    )
    LegalSectionBlock(
        "9. Contact Information (Release Placeholder)",
        "Privacy Contact: [PRIVACY_CONTACT_EMAIL]\nPublisher / Owner: [APP_OWNER_OR_COMPANY_NAME]\nEffective Date: [EFFECTIVE_DATE]"
    )

    GlassCard(
        shape = RoundedCornerShape(20.dp),
        elevated = true,
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Public Privacy Policy URL (Play Store Config)", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Configure your hosted HTTPS privacy policy URL before Play Store release.",
            style = MaterialTheme.typography.bodySmall,
            color = prixColors.secondaryText
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = urlInput,
            onValueChange = {
                urlInput = it
                actionNote = null
            },
            label = { Text("Hosted Privacy Policy URL") },
            placeholder = { Text("https://[YOUR_DOMAIN]/prix/privacy-policy") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        actionNote?.let {
            Spacer(modifier = Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(10.dp))
        val trimmedUrl = urlInput.trim()
        val isRealHttpUrl = (trimmedUrl.startsWith("https://") || trimmedUrl.startsWith("http://")) &&
            !trimmedUrl.contains("[") && !trimmedUrl.contains("]")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(
                text = "Save URL",
                onClick = {
                    scope.launch {
                        preferencesRepo.setPrivacyPolicyUrl(trimmedUrl)
                        actionNote = if (trimmedUrl.isBlank()) "Cleared URL." else "Saved policy URL locally."
                    }
                },
                modifier = Modifier.weight(1f)
            )
            GlassButton(
                text = "Open URL",
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                isPrimary = false,
                enabled = isRealHttpUrl,
                onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(trimmedUrl)))
                    } catch (_: Exception) {
                        actionNote = "No browser app is available to open this URL."
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TermsContent() {
    LegalSectionBlock("1. Acceptance of Terms", "By installing or using PRIX, you agree to these Terms and Conditions. If you do not agree, do not use the application.")
    LegalSectionBlock("2. Application Usage & Permitted Use", "PRIX is provided as a local document scanning, PDF creation, editing, and organization productivity tool for lawful personal, academic, and business use.")
    LegalSectionBlock("3. User Responsibilities & Prohibited Usage", "You are solely responsible for all documents scanned, edited, encrypted, or shared using PRIX, and for safeguarding passwords applied to PDFs. You agree not to use PRIX to infringe intellectual property rights or process unlawful content.")
    LegalSectionBlock("4. Intellectual Property", "The PRIX name, visual identity, and application source code remain the property of [APP_OWNER_OR_COMPANY_NAME]. You retain full ownership of all documents you create or import.")
    LegalSectionBlock("5. Third-Party Services & Libraries", "PRIX relies on open-source and Android platform libraries; their respective licenses govern those components.")
    LegalSectionBlock("6. Service Availability & Limitation of Liability", "PRIX is provided \"as is\" without warranties of uninterrupted availability or error-free output. To the maximum extent permitted by law, [APP_OWNER_OR_COMPANY_NAME] shall not be liable for data loss, missed deadlines, or indirect damages.")
    LegalSectionBlock("7. Updates, Termination & Governing Law", "Terms may be updated with future releases. Continued use constitutes acceptance. Governed by the laws of [JURISDICTION / COUNTRY].\n\nOwner: [APP_OWNER_OR_COMPANY_NAME]\nAddress: [OWNER_BUSINESS_ADDRESS]\nSupport Contact: [SUPPORT_EMAIL]")
}

@Composable
private fun DisclaimerContent() {
    LegalSectionBlock("Productivity Tool Notice", "PRIX is a mobile document scanning and PDF productivity tool designed for convenience and everyday organization.")
    LegalSectionBlock("OCR Accuracy & Language Support", "On-device optical character recognition (OCR) supports Latin-script languages and may contain misread characters or formatting differences. Always verify critical numbers, names, dates, and legal clauses manually.")
    LegalSectionBlock("PDF Text Editing & Overlays", "Text boxes and annotations placed in the PDF Editor add new visual content overlays onto pages rather than modifying or rewriting original embedded PDF fonts or glyphs.")
    LegalSectionBlock("Compression & Image Quality", "High PDF or image compression reduces resolution and detail. Inspect compressed outputs before deleting original files.")
    LegalSectionBlock("Legal Validity & Signatures", "Scanned copies produced with PRIX are not automatically certified originals. Drawn signatures added inside PRIX are visual overlays and are NOT verified cryptographic or legally binding digital signatures.")
    LegalSectionBlock("Security & Backups", "Private vault documents and the local database are excluded from automatic cloud backups for privacy. Users are responsible for exporting and backing up critical records.")
}

@Composable
private fun AboutPrixContent(onNavigateSection: (String) -> Unit) {
    val context = LocalContext.current
    val prixColors = LocalPrixColors.current
    var supportNote by remember { mutableStateOf<String?>(null) }
    val supportConfigured = !PLACEHOLDER_SUPPORT_EMAIL.contains("[")

    GlassCard(
        shape = RoundedCornerShape(24.dp),
        elevated = true,
        contentPadding = PaddingValues(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PrixLogoMark(size = 64.dp)
            Text("PRIX", style = MaterialTheme.typography.headlineMedium)
            Text("Version 1.0.0", style = MaterialTheme.typography.labelMedium, color = prixColors.secondaryText)
            Text("Scan. Create. Edit. Organize.", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "A privacy-first, offline-capable native Android document scanner, photo-to-PDF maker, PDF viewer, and PDF editor.",
                style = MaterialTheme.typography.bodyMedium
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = prixColors.glassOuterBorder)
            Text("Developer / Publisher: [APP_OWNER_OR_COMPANY_NAME]", style = MaterialTheme.typography.bodySmall, color = prixColors.secondaryText)
            Text("Support Email: $PLACEHOLDER_SUPPORT_EMAIL", style = MaterialTheme.typography.bodySmall, color = prixColors.secondaryText)
            GlassButton(
                text = "Contact Support ($PLACEHOLDER_SUPPORT_EMAIL)",
                icon = Icons.Filled.Email,
                isPrimary = false,
                enabled = supportConfigured,
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:$PLACEHOLDER_SUPPORT_EMAIL")
                        putExtra(Intent.EXTRA_SUBJECT, "PRIX App Support (v1.0.0)")
                    }
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        supportNote = "No email app is configured on this device."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (!supportConfigured) {
                Text(
                    "Release placeholder: replace [SUPPORT_EMAIL] with publisher support address before distribution.",
                    style = MaterialTheme.typography.labelSmall,
                    color = prixColors.secondaryText
                )
            }
            supportNote?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { onNavigateSection(LegalSections.PRIVACY) }, modifier = Modifier.weight(1f)) {
                    Text("Privacy")
                }
                OutlinedButton(onClick = { onNavigateSection(LegalSections.TERMS) }, modifier = Modifier.weight(1f)) {
                    Text("Terms")
                }
                OutlinedButton(onClick = { onNavigateSection(LegalSections.DISCLAIMER) }, modifier = Modifier.weight(1f)) {
                    Text("Disclaimer")
                }
            }
        }
    }
}

@Composable
private fun LicensesContent() {
    LegalSectionBlock("Apache PDFBox-Android (TomRoush)", "Licensed under the Apache License, Version 2.0. Used locally for PDF generation, merging, splitting, annotations, encryption, and text extraction.")
    LegalSectionBlock("AndroidX Jetpack Libraries", "CameraX, Compose, Material 3, Room, Navigation, DataStore, WorkManager, Core SplashScreen, ExifInterface — Licensed under the Apache License, Version 2.0.")
    LegalSectionBlock("Google ML Kit Text Recognition", "On-device Latin text recognition SDK — Subject to Google APIs Terms of Service.")
    LegalSectionBlock("Coil Image Loading", "Licensed under the Apache License, Version 2.0.")
    LegalSectionBlock("JetBrains Kotlin & Kotlinx Serialization / Coroutines", "Licensed under the Apache License, Version 2.0.")
}
