package com.example.prix

import android.app.Application
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.prix.core.data.PrixRepository
import com.example.prix.core.database.PrixDatabase
import com.example.prix.core.datastore.PreferencesRepository
import com.example.prix.core.ocr.OcrEngine
import com.example.prix.core.pdf.PdfEngine
import com.example.prix.core.storage.DocumentStorageManager
import com.example.prix.core.storage.TempCleanupWorker
import java.util.concurrent.TimeUnit

class PrixAppContainer(context: Context) {
    val database: PrixDatabase by lazy { PrixDatabase.getInstance(context) }
    val preferencesRepository: PreferencesRepository by lazy { PreferencesRepository(context) }
    val storageManager: DocumentStorageManager by lazy { DocumentStorageManager(context) }
    val pdfEngine: PdfEngine by lazy { PdfEngine(context) }
    val ocrEngine: OcrEngine by lazy { OcrEngine() }

    val repository: PrixRepository by lazy {
        PrixRepository(
            context = context.applicationContext,
            documentDao = database.documentDao(),
            folderDao = database.folderDao(),
            signatureDao = database.signatureDao(),
            storage = storageManager,
            pdfEngine = pdfEngine,
            ocrEngine = ocrEngine
        )
    }
}

class PrixApplication : Application() {
    lateinit var container: PrixAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = PrixAppContainer(this)
        scheduleTempCleanup()
    }

    private fun scheduleTempCleanup() {
        try {
            val request = PeriodicWorkRequestBuilder<TempCleanupWorker>(24, TimeUnit.HOURS).build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "prix_temp_cleanup",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        } catch (_: Exception) {
        }
    }
}
