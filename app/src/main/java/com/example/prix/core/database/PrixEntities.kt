package com.example.prix.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.prix.core.model.DocumentFormat

@Entity(
    tableName = "folders",
    indices = [Index("parentFolderId")]
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentFolderId: Long? = null,
    val colorHex: Long = 0xFF7657F6,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "documents",
    indices = [Index("folderId"), Index("updatedAt"), Index("lastOpenedAt")]
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val filePath: String,
    val thumbnailPath: String? = null,
    val mimeType: String,
    val formatName: String = DocumentFormat.PDF.name,
    val sizeBytes: Long = 0L,
    val pageCount: Int = 1,
    val folderId: Long? = null,
    val isStarred: Boolean = false,
    val isPasswordProtected: Boolean = false,
    val ocrText: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = 0L
) {
    val format: DocumentFormat
        get() = DocumentFormat.entries.firstOrNull { it.name == formatName } ?: DocumentFormat.PDF
}

@Entity(tableName = "saved_signatures")
data class SavedSignatureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val imagePath: String,
    val createdAt: Long = System.currentTimeMillis()
)
