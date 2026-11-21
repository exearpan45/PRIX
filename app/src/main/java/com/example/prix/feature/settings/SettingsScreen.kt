package com.example.prix.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.prix.core.database.FolderEntity
import com.example.prix.core.datastore.PreferencesRepository
import com.example.prix.core.datastore.UserPreferences
import com.example.prix.core.model.PdfPageSize
import com.example.prix.core.model.PdfQuality
import com.example.prix.core.model.PerformanceMode
import com.example.prix.core.model.StorageStats
import com.example.prix.core.model.ThemeMode
import com.example.prix.core.storage.DocumentStorageManager
import com.example.prix.core.ui.GlassButton
import com.example.prix.core.ui.GlassCard
import com.example.prix.core.ui.GlassChip
import com.example.prix.core.ui.GlassSettingsItem
import com.example.prix.ui.navigation.LegalSections
import com.example.ui.theme.LocalPrixColors
import com.example.ui.theme.LocalPrixEffects
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    preferencesRepo: PreferencesRepository,
    folders: List<FolderEntity>,
    storageStats: StorageStats,
    onClearTempFiles: () -> Unit,
    onOpenSignatureManager: () -> Unit,
    onOpenLegal: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val prixColors = LocalPrixColors.current
    val effects = LocalPrixEffects.current

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .testTag("settings_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column {
                    Text("Settings", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = "Adaptive Liquid Glass • Active Profile: ${effects.performanceProfile.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = prixColors.secondaryText
                    )
                }
            }

            // Appearance & Performance
            item {
                SettingsSectionCard(title = "Appearance & Performance") {
                    Text("Theme Mode", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            GlassChip(
                                label = mode.label,
                                selected = preferences.themeMode == mode,
                                onClick = { scope.launch { preferencesRepo.setThemeMode(mode) } },
                                modifier = Modifier.testTag("theme_${mode.name.lowercase()}")
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Performance Mode (Effective: ${effects.performanceProfile.label})",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Lower profiles disable shadows/blur and reduce memory usage during PDF & image processing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = prixColors.secondaryText
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(PerformanceMode.entries) { perfMode ->
                            GlassChip(
                                label = perfMode.label,
                                selected = preferences.performanceMode == perfMode,
                                onClick = { scope.launch { preferencesRepo.setPerformanceMode(perfMode) } },
                                modifier = Modifier.testTag("perf_${perfMode.name.lowercase()}")
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = prixColors.glassOuterBorder)
                    GlassSettingsItem(
                        icon = Icons.Filled.TouchApp,
                        title = "Haptic Feedback",
                        subtitle = "Subtle touch feedback on captures and selections",
                        checked = preferences.hapticFeedbackEnabled,
                        onCheckedChange = { scope.launch { preferencesRepo.setHapticFeedback(it) } }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = prixColors.glassOuterBorder)
                    GlassSettingsItem(
                        icon = Icons.Filled.Animation,
                        title = "Interface Animations",
                        subtitle = "Smooth transitions and splash reveal",
                        checked = preferences.animationsEnabled,
                        onCheckedChange = { scope.launch { preferencesRepo.setAnimationsEnabled(it) } }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = prixColors.glassOuterBorder)
                    GlassSettingsItem(
                        icon = Icons.Filled.AccessibilityNew,
                        title = "Reduced Motion",
                        subtitle = "Minimize transitions and shadows for speed or comfort",
                        checked = preferences.reducedMotion,
                        onCheckedChange = { scope.launch { preferencesRepo.setReducedMotion(it) } },
                        modifier = Modifier.testTag("settings_reduced_motion_row")
                    )
                }
            }

            // PDF Defaults
            item {
                SettingsSectionCard(title = "PDF Defaults") {
                    Text("Default PDF Page Size", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(PdfPageSize.entries) { size ->
                            GlassChip(
                                label = size.label,
                                selected = preferences.defaultPageSize == size,
                                onClick = { scope.launch { preferencesRepo.setDefaultPageSize(size) } }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Default PDF Quality", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(PdfQuality.entries) { quality ->
                            GlassChip(
                                label = quality.label,
                                selected = preferences.defaultPdfQuality == quality,
                                onClick = { scope.launch { preferencesRepo.setDefaultPdfQuality(quality) } }
                            )
                        }
                    }
                    if (folders.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Default Save Folder", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                GlassChip(
                                    label = "All Documents",
                                    selected = preferences.defaultSaveFolderId == null,
                                    onClick = { scope.launch { preferencesRepo.setDefaultSaveFolderId(null) } }
                                )
                            }
                            items(folders, key = { it.id }) { folder ->
                                GlassChip(
                                    label = folder.name,
                                    selected = preferences.defaultSaveFolderId == folder.id,
                                    onClick = { scope.launch { preferencesRepo.setDefaultSaveFolderId(folder.id) } }
                                )
                            }
                        }
                    }
                }
            }

            // Scanner & Signatures
            item {
                SettingsSectionCard(title = "Scanner & Signatures") {
                    GlassSettingsItem(
                        icon = Icons.Filled.AutoFixHigh,
                        title = "Auto-Enhance Scans",
                        subtitle = "Apply Auto Enhance filter by default after capture",
                        checked = preferences.scannerAutoEnhance,
                        onCheckedChange = { scope.launch { preferencesRepo.setScannerAutoEnhance(it) } }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = prixColors.glassOuterBorder)
                    GlassSettingsItem(
                        icon = Icons.Filled.Timer,
                        title = "Auto-Capture Timer",
                        subtitle = "Start 3-second countdown automatically in scanner",
                        checked = preferences.scannerAutoCapture,
                        onCheckedChange = { scope.launch { preferencesRepo.setScannerAutoCapture(it) } }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = prixColors.glassOuterBorder)
                    GlassSettingsItem(
                        icon = Icons.Filled.Draw,
                        title = "Signature Manager",
                        subtitle = "Create, preview, or delete reusable handwritten signatures",
                        onClick = onOpenSignatureManager,
                        modifier = Modifier.testTag("settings_signature_manager_row")
                    )
                }
            }

            // Storage & Maintenance
            item {
                SettingsSectionCard(title = "Storage & Cache") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${storageStats.totalDocuments} Documents • ${storageStats.totalFolders} Folders",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "Vault: ${DocumentStorageManager.formatFileSize(storageStats.vaultSizeBytes)} • Temp: ${DocumentStorageManager.formatFileSize(storageStats.tempCacheBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = prixColors.secondaryText
                            )
                            Text(
                                "Device Available: ${DocumentStorageManager.formatFileSize(storageStats.availableDeviceBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = prixColors.secondaryText
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    GlassButton(
                        text = "Clear Temporary Files",
                        icon = Icons.Filled.CleaningServices,
                        isPrimary = false,
                        onClick = onClearTempFiles,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("clear_temp_files")
                    )
                }
            }

            // Privacy & Legal
            item {
                SettingsSectionCard(title = "Privacy, Legal & About") {
                    GlassSettingsItem(
                        icon = Icons.Filled.PrivacyTip,
                        title = "Privacy Policy",
                        subtitle = "Local-first storage, permissions & policy URL",
                        onClick = { onOpenLegal(LegalSections.PRIVACY) },
                        modifier = Modifier.testTag("settings_privacy_row")
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = prixColors.glassOuterBorder)
                    GlassSettingsItem(
                        icon = Icons.Filled.Gavel,
                        title = "Terms & Conditions",
                        subtitle = "App usage, user responsibilities & terms",
                        onClick = { onOpenLegal(LegalSections.TERMS) },
                        modifier = Modifier.testTag("settings_terms_row")
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = prixColors.glassOuterBorder)
                    GlassSettingsItem(
                        icon = Icons.Filled.WarningAmber,
                        title = "Disclaimer",
                        subtitle = "OCR, compression & signature legal notice",
                        onClick = { onOpenLegal(LegalSections.DISCLAIMER) },
                        modifier = Modifier.testTag("settings_disclaimer_row")
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = prixColors.glassOuterBorder)
                    GlassSettingsItem(
                        icon = Icons.Filled.Info,
                        title = "About PRIX",
                        subtitle = "Version 1.0.0 • Scan. Create. Edit. Organize.",
                        onClick = { onOpenLegal(LegalSections.ABOUT) },
                        modifier = Modifier.testTag("settings_about_row")
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = prixColors.glassOuterBorder)
                    GlassSettingsItem(
                        icon = Icons.Filled.Code,
                        title = "Open-Source Licenses",
                        subtitle = "Apache PDFBox, AndroidX, CameraX, Coil & ML Kit",
                        onClick = { onOpenLegal(LegalSections.LICENSES) },
                        modifier = Modifier.testTag("settings_licenses_row")
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    GlassCard(
        shape = RoundedCornerShape(22.dp),
        elevated = true,
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}
