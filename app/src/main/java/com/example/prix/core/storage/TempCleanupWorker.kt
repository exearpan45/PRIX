package com.example.prix.core.storage

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.prix.core.error.PrixErrorMapper
import java.io.File

/**
 * Periodically removes expired application-owned temporary scan/export files older than 24 hours.
 * Never deletes user vault documents or active session files.
 */
class TempCleanupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    internal data class CleanupStats(val deletedCount: Int, val skippedCount: Int)

    override suspend fun doWork(): Result {
        performCleanup(applicationContext)
        return Result.success()
    }

    companion object {
        internal fun performCleanup(context: Context): CleanupStats {
            var deletedCount = 0
            var skippedCount = 0
            val cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L

            val tempDir = File(context.cacheDir, "prix_tmp")
            if (tempDir.exists() && tempDir.isDirectory) {
                tempDir.listFiles()?.forEach { entry ->
                    try {
                        if (DocumentStorageManager.isPathActive(entry)) {
                            skippedCount++
                        } else if (entry.lastModified() < cutoff) {
                            if (entry.deleteRecursively()) {
                                deletedCount++
                            } else {
                                skippedCount++
                            }
                        }
                    } catch (e: Exception) {
                        skippedCount++
                        PrixErrorMapper.logWarning("TempCleanupWorker_entry", e)
                    }
                }
            }

            // Also sweep any orphaned exif_*.img temporary decode files in cacheDir older than 24h
            context.cacheDir.listFiles()?.forEach { entry ->
                try {
                    if (entry.isFile &&
                        entry.name.startsWith("exif_") &&
                        entry.name.endsWith(".img") &&
                        !DocumentStorageManager.isPathActive(entry) &&
                        entry.lastModified() < cutoff
                    ) {
                        if (entry.delete()) deletedCount++ else skippedCount++
                    }
                } catch (e: Exception) {
                    skippedCount++
                    PrixErrorMapper.logWarning("TempCleanupWorker_exif", e)
                }
            }

            if (skippedCount > 0) {
                PrixErrorMapper.logWarning("TempCleanupWorker_skipped_$skippedCount")
            }
            return CleanupStats(deletedCount = deletedCount, skippedCount = skippedCount)
        }
    }
}
