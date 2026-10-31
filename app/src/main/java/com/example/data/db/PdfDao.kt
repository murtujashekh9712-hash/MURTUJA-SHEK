package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.BookmarkEntity
import com.example.data.model.PdfDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfDao {
    @Query("SELECT * FROM pdf_documents ORDER BY addedTimestamp DESC")
    fun getAllPdfs(): Flow<List<PdfDocumentEntity>>

    @Query("SELECT * FROM pdf_documents WHERE id = :id LIMIT 1")
    fun getPdfById(id: Long): Flow<PdfDocumentEntity?>

    @Query("SELECT * FROM pdf_documents WHERE id = :id LIMIT 1")
    suspend fun getPdfByIdDirect(id: Long): PdfDocumentEntity?

    @Query("SELECT * FROM pdf_documents WHERE isFavorite = 1 ORDER BY addedTimestamp DESC")
    fun getFavoritePdfs(): Flow<List<PdfDocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPdf(pdf: PdfDocumentEntity): Long

    @Update
    suspend fun updatePdf(pdf: PdfDocumentEntity)

    @Delete
    suspend fun deletePdf(pdf: PdfDocumentEntity)

    @Query("DELETE FROM pdf_documents WHERE id = :id")
    suspend fun deletePdfById(id: Long)

    @Query("UPDATE pdf_documents SET lastReadPage = :page, lastReadTimestamp = :timestamp WHERE id = :id")
    suspend fun updateReadingProgress(id: Long, page: Int, timestamp: Long)

    @Query("UPDATE pdf_documents SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE pdf_documents SET title = :title, category = :category, notes = :notes WHERE id = :id")
    suspend fun updateMetadata(id: Long, title: String, category: String, notes: String)

    @Query("SELECT * FROM bookmarks WHERE pdfId = :pdfId ORDER BY pageNumber ASC")
    fun getBookmarksForPdf(pdfId: Long): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Long)

    @Query("SELECT category, COUNT(*) as count FROM pdf_documents GROUP BY category")
    fun getCategoryCounts(): Flow<List<com.example.data.model.CategoryWithCount>>

    @Query("UPDATE pdf_documents SET category = :newCategory WHERE category = :oldCategory")
    suspend fun renameCategoryInPdfs(oldCategory: String, newCategory: String)

    @Query("UPDATE pdf_documents SET category = 'General' WHERE category = :deletedCategory")
    suspend fun reassignDeletedCategory(deletedCategory: String)

    @Query("UPDATE pdf_documents SET category = :category WHERE id = :id")
    suspend fun updatePdfCategory(id: Long, category: String)
}
