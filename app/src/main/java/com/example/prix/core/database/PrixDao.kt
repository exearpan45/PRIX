package com.example.prix.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY name ASC")
    fun observeAllFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE parentFolderId IS :parentId ORDER BY name ASC")
    fun observeFoldersInParent(parentId: Long?): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolderById(id: Long): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity): Long

    @Update
    suspend fun updateFolder(folder: FolderEntity)

    @Query("UPDATE folders SET parentFolderId = :newParentId, updatedAt = :updatedAt WHERE parentFolderId = :oldParentId")
    suspend fun reparentChildFolders(oldParentId: Long, newParentId: Long?, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolderById(id: Long)
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    fun observeAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    fun observeDocumentById(id: Long): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentById(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents")
    suspend fun getAllDocumentsOnce(): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE formatName = 'PDF' ORDER BY updatedAt DESC")
    fun observePdfDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE formatName IN ('PNG', 'JPEG', 'WEBP') ORDER BY updatedAt DESC")
    fun observeImageDocuments(): Flow<List<DocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Query("UPDATE documents SET isStarred = :starred, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setStarred(id: Long, starred: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET lastOpenedAt = :openedAt WHERE id = :id")
    suspend fun markOpened(id: Long, openedAt: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET folderId = :folderId, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun moveDocumentsToFolder(ids: List<Long>, folderId: Long?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET folderId = NULL WHERE folderId = :folderId")
    suspend fun detachDocumentsFromFolder(folderId: Long)

    @Query("DELETE FROM documents WHERE id IN (:ids)")
    suspend fun deleteDocumentsByIds(ids: List<Long>)

    @Query("UPDATE documents SET ocrText = :ocrText, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateOcrText(id: Long, ocrText: String, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface SignatureDao {
    @Query("SELECT * FROM saved_signatures ORDER BY createdAt DESC")
    fun observeAllSignatures(): Flow<List<SavedSignatureEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignature(signature: SavedSignatureEntity): Long

    @Query("DELETE FROM saved_signatures WHERE id = :id")
    suspend fun deleteSignatureById(id: Long)
}
