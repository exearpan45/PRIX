package com.example.prix.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.prix.core.model.PdfPageSize
import com.example.prix.core.model.PdfQuality
import com.example.prix.core.model.PerformanceMode
import com.example.prix.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.prixDataStore: DataStore<Preferences> by preferencesDataStore(name = "prix_settings")

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val performanceMode: PerformanceMode = PerformanceMode.AUTO,
    val defaultPageSize: PdfPageSize = PdfPageSize.A4,
    val defaultPdfQuality: PdfQuality = PdfQuality.BALANCED,
    val defaultSaveFolderId: Long? = null,
    val scannerAutoEnhance: Boolean = true,
    val scannerAutoCapture: Boolean = false,
    val hapticFeedbackEnabled: Boolean = true,
    val animationsEnabled: Boolean = true,
    val reducedMotion: Boolean = false,
    val hasSeenOnboarding: Boolean = false,
    val privacyPolicyUrl: String = ""
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PERFORMANCE_MODE = stringPreferencesKey("performance_mode")
        val DEFAULT_PAGE_SIZE = stringPreferencesKey("default_page_size")
        val DEFAULT_PDF_QUALITY = stringPreferencesKey("default_pdf_quality")
        val DEFAULT_SAVE_FOLDER_ID = longPreferencesKey("default_save_folder_id")
        val SCANNER_AUTO_ENHANCE = booleanPreferencesKey("scanner_auto_enhance")
        val SCANNER_AUTO_CAPTURE = booleanPreferencesKey("scanner_auto_capture")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val ANIMATIONS_ENABLED = booleanPreferencesKey("animations_enabled")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
        val PRIVACY_POLICY_URL = stringPreferencesKey("privacy_policy_url")
    }

    val preferences: Flow<UserPreferences> = context.prixDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            UserPreferences(
                themeMode = prefs[Keys.THEME_MODE]?.let { name ->
                    ThemeMode.entries.firstOrNull { it.name == name }
                } ?: ThemeMode.SYSTEM,
                performanceMode = prefs[Keys.PERFORMANCE_MODE]?.let { name ->
                    PerformanceMode.entries.firstOrNull { it.name == name }
                } ?: PerformanceMode.AUTO,
                defaultPageSize = prefs[Keys.DEFAULT_PAGE_SIZE]?.let { name ->
                    PdfPageSize.entries.firstOrNull { it.name == name }
                } ?: PdfPageSize.A4,
                defaultPdfQuality = prefs[Keys.DEFAULT_PDF_QUALITY]?.let { name ->
                    PdfQuality.entries.firstOrNull { it.name == name }
                } ?: PdfQuality.BALANCED,
                defaultSaveFolderId = prefs[Keys.DEFAULT_SAVE_FOLDER_ID]?.takeIf { it > 0L },
                scannerAutoEnhance = prefs[Keys.SCANNER_AUTO_ENHANCE] ?: true,
                scannerAutoCapture = prefs[Keys.SCANNER_AUTO_CAPTURE] ?: false,
                hapticFeedbackEnabled = prefs[Keys.HAPTIC_FEEDBACK] ?: true,
                animationsEnabled = prefs[Keys.ANIMATIONS_ENABLED] ?: true,
                reducedMotion = prefs[Keys.REDUCED_MOTION] ?: false,
                hasSeenOnboarding = prefs[Keys.HAS_SEEN_ONBOARDING] ?: false,
                privacyPolicyUrl = prefs[Keys.PRIVACY_POLICY_URL]
                    ?.takeIf { !it.equals("https://example.com/prix/privacy-policy", ignoreCase = true) }
                    ?: ""
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.prixDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setPerformanceMode(mode: PerformanceMode) {
        context.prixDataStore.edit { it[Keys.PERFORMANCE_MODE] = mode.name }
    }

    suspend fun setDefaultPageSize(size: PdfPageSize) {
        context.prixDataStore.edit { it[Keys.DEFAULT_PAGE_SIZE] = size.name }
    }

    suspend fun setDefaultPdfQuality(quality: PdfQuality) {
        context.prixDataStore.edit { it[Keys.DEFAULT_PDF_QUALITY] = quality.name }
    }

    suspend fun setDefaultSaveFolderId(folderId: Long?) {
        context.prixDataStore.edit {
            if (folderId == null) it.remove(Keys.DEFAULT_SAVE_FOLDER_ID)
            else it[Keys.DEFAULT_SAVE_FOLDER_ID] = folderId
        }
    }

    suspend fun setScannerAutoEnhance(enabled: Boolean) {
        context.prixDataStore.edit { it[Keys.SCANNER_AUTO_ENHANCE] = enabled }
    }

    suspend fun setScannerAutoCapture(enabled: Boolean) {
        context.prixDataStore.edit { it[Keys.SCANNER_AUTO_CAPTURE] = enabled }
    }

    suspend fun setHapticFeedback(enabled: Boolean) {
        context.prixDataStore.edit { it[Keys.HAPTIC_FEEDBACK] = enabled }
    }

    suspend fun setAnimationsEnabled(enabled: Boolean) {
        context.prixDataStore.edit { it[Keys.ANIMATIONS_ENABLED] = enabled }
    }

    suspend fun setReducedMotion(enabled: Boolean) {
        context.prixDataStore.edit { it[Keys.REDUCED_MOTION] = enabled }
    }

    suspend fun setHasSeenOnboarding(seen: Boolean) {
        context.prixDataStore.edit { it[Keys.HAS_SEEN_ONBOARDING] = seen }
    }

    suspend fun setPrivacyPolicyUrl(url: String) {
        context.prixDataStore.edit { it[Keys.PRIVACY_POLICY_URL] = url.trim() }
    }
}
