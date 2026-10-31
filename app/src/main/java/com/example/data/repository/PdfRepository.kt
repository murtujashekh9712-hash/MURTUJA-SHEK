package com.example.data.repository

import android.net.Uri
import com.example.data.db.CategoryDao
import com.example.data.db.PdfDao
import com.example.data.model.BookmarkEntity
import com.example.data.model.CategoryEntity
import com.example.data.model.CategoryWithCount
import com.example.data.model.PdfDocumentEntity
import com.example.util.PdfStorageManager
import kotlinx.coroutines.flow.Flow
import java.io.File

class PdfRepository(
    private val pdfDao: PdfDao,
    private val categoryDao: CategoryDao,
    private val storageManager: PdfStorageManager
) {
    val allPdfs: Flow<List<PdfDocumentEntity>> = pdfDao.getAllPdfs()
    val favoritePdfs: Flow<List<PdfDocumentEntity>> = pdfDao.getFavoritePdfs()
    val allCategories: Flow<List<CategoryEntity>> = categoryDao.getAllCategories()
    val categoryCounts: Flow<List<CategoryWithCount>> = pdfDao.getCategoryCounts()

    fun getPdfById(id: Long): Flow<PdfDocumentEntity?> = pdfDao.getPdfById(id)

    suspend fun getPdfByIdDirect(id: Long): PdfDocumentEntity? = pdfDao.getPdfByIdDirect(id)

    suspend fun insertPdf(pdf: PdfDocumentEntity): Long = pdfDao.insertPdf(pdf)

    suspend fun updatePdf(pdf: PdfDocumentEntity) = pdfDao.updatePdf(pdf)

    suspend fun deletePdf(pdf: PdfDocumentEntity) {
        storageManager.deletePdfFiles(pdf)
        pdfDao.deletePdf(pdf)
    }

    suspend fun updateReadingProgress(id: Long, page: Int) {
        pdfDao.updateReadingProgress(id, page, System.currentTimeMillis())
    }

    suspend fun toggleFavorite(id: Long, isFavorite: Boolean) {
        pdfDao.setFavorite(id, isFavorite)
    }

    suspend fun updateMetadata(id: Long, title: String, category: String, notes: String) {
        pdfDao.updateMetadata(id, title, category, notes)
    }

    suspend fun updatePdfCategory(id: Long, category: String) {
        pdfDao.updatePdfCategory(id, category)
    }

    fun getBookmarks(pdfId: Long): Flow<List<BookmarkEntity>> = pdfDao.getBookmarksForPdf(pdfId)

    suspend fun addBookmark(pdfId: Long, pageNumber: Int, note: String): Long {
        return pdfDao.insertBookmark(BookmarkEntity(pdfId = pdfId, pageNumber = pageNumber, note = note))
    }

    suspend fun deleteBookmark(id: Long) = pdfDao.deleteBookmark(id)

    suspend fun importPdf(uri: Uri, category: String): Boolean {
        val entity = storageManager.importPdfFromUri(uri, category) ?: return false
        pdfDao.insertPdf(entity)
        return true
    }

    suspend fun createTextDocument(
        title: String,
        author: String,
        category: String,
        content: String,
        notes: String
    ): Boolean {
        val entity = storageManager.createTextDocumentPdf(title, author, category, content, notes) ?: return false
        pdfDao.insertPdf(entity)
        return true
    }

    suspend fun createPdfFromImages(
        title: String,
        category: String,
        imageUris: List<Uri>
    ): Boolean {
        val entity = storageManager.createPdfFromImages(title, category, imageUris) ?: return false
        pdfDao.insertPdf(entity)
        return true
    }

    suspend fun ensureInitialSamples() {
        val samples = storageManager.createInitialSamplePdfs()
        for (sample in samples) {
            pdfDao.insertPdf(sample)
        }
    }

    // Category Management
    suspend fun ensureDefaultCategories() {
        if (categoryDao.getCategoryCount() == 0) {
            val defaults = listOf(
                CategoryEntity(name = "Work", colorHex = "#2563EB", iconName = "Work"),
                CategoryEntity(name = "Personal", colorHex = "#7C3AED", iconName = "Home"),
                CategoryEntity(name = "Invoices", colorHex = "#059669", iconName = "Receipt"),
                CategoryEntity(name = "Study", colorHex = "#0284C7", iconName = "School"),
                CategoryEntity(name = "Books", colorHex = "#9333EA", iconName = "MenuBook"),
                CategoryEntity(name = "Important", colorHex = "#DC2626", iconName = "Lock"),
                CategoryEntity(name = "Guide", colorHex = "#E11D48", iconName = "Help"),
                CategoryEntity(name = "General", colorHex = "#64748B", iconName = "Folder")
            )
            categoryDao.insertCategories(defaults)
        }
    }

    suspend fun insertCategory(name: String, colorHex: String, iconName: String): Long {
        val entity = CategoryEntity(name = name, colorHex = colorHex, iconName = iconName)
        return categoryDao.insertCategory(entity)
    }

    suspend fun updateCategory(category: CategoryEntity, oldName: String) {
        categoryDao.updateCategory(category)
        if (oldName != category.name) {
            pdfDao.renameCategoryInPdfs(oldName, category.name)
        }
    }

    suspend fun deleteCategory(category: CategoryEntity) {
        pdfDao.reassignDeletedCategory(category.name)
        categoryDao.deleteCategory(category)
    }

    fun getPdfFile(fileName: String): File = storageManager.getPdfFile(fileName)

    fun getThumbnailFile(fileName: String): File = storageManager.getThumbnailFile(fileName)

    fun getShareIntent(pdf: PdfDocumentEntity) = storageManager.sharePdf(pdf)

    suspend fun renderPage(file: File, pageIndex: Int, targetWidth: Int = 1200) =
        storageManager.renderPage(file, pageIndex, targetWidth)
}
