package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.prix.PrixApplication
import com.example.prix.core.datastore.UserPreferences
import com.example.prix.ui.PrixAppRoot
import com.example.ui.theme.PrixTheme

class MainActivity : ComponentActivity() {

    private var incomingPdfUriState by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as PrixApplication).container
        incomingPdfUriState = extractPdfUri(intent)

        setContent {
            val prefs by appContainer.preferencesRepository.preferences
                .collectAsStateWithLifecycle(initialValue = UserPreferences())

            PrixTheme(
                themeMode = prefs.themeMode,
                performanceMode = prefs.performanceMode,
                animationsEnabled = prefs.animationsEnabled,
                reducedMotionOverride = prefs.reducedMotion
            ) {
                PrixAppRoot(
                    container = appContainer,
                    preferences = prefs,
                    incomingPdfUri = incomingPdfUriState,
                    onConsumeIncomingPdfUri = { incomingPdfUriState = null },
                    showSplashInitially = true
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractPdfUri(intent)?.let { uri ->
            incomingPdfUriState = uri
        }
    }

    private fun extractPdfUri(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        val scheme = uri.scheme?.lowercase()
        return if (scheme == "content" || scheme == "file") uri else null
    }
}
