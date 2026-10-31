package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.PdfDatabase
import com.example.data.model.BookmarkEntity
import com.example.data.model.CategoryEntity
import com.example.data.model.CategoryWithCount
import com.example.data.model.PdfDocumentEntity
import com.example.data.repository.PdfRepository
import com.example.util.PdfStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SortOption(val label: String) {
    DATE_DESC("Newest Added"),
    DATE_ASC("Oldest Added"),
    TITLE_ASC("Title (A to Z)"),
    TITLE_DESC("Title (Z to A)"),
    SIZE_DESC("Largest Size"),
    SIZE_ASC("Smallest Size"),
    RECENTLY_READ("Recently Read")
}

data class StorageStats(
    val totalCount: Int = 0,
    val totalSizeBytes: Long = 0L,
    val totalPages: Int = 0
) {
    val formattedTotalSize: String
        get() {
            val mb = totalSizeBytes / (1024.0 * 1024.0)
            return if (mb >= 1.0) {
                String.format("%.1f MB", mb)
            } else {
                val kb = totalSizeBytes / 1024.0
                String.format("%.0f KB", kb)
            }
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PdfViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: PdfRepository

    init {
        val db = PdfDatabase.getDatabase(application)
        val storageManager = PdfStorageManager(application)
        repository = PdfRepository(db.pdfDao(), db.categoryDao(), storageManager)

        // Seed initial categories and sample documents if first run
        viewModelScope.launch {
            repository.ensureDefaultCategories()
            repository.allPdfs.collect { list ->
                if (list.isEmpty()) {
                    repository.ensureInitialSamples()
                }
            }
        }
    }

    // UI State for Library
    val allPdfs = repository.allPdfs

    // Room Categories and Counts
    val categories: StateFlow<List<CategoryEntity>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categoryCounts: StateFlow<List<CategoryWithCount>> = repository.categoryCounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory = _selectedCategory.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.DATE_DESC)
    val sortOption = _sortOption.asStateFlow()

    private val _isGridView = MutableStateFlow(true)
    val isGridView = _isGridView.asStateFlow()

    val filteredPdfs: StateFlow<List<PdfDocumentEntity>> = combine(
        allPdfs,
        _searchQuery,
        _selectedCategory,
        _sortOption
    ) { list, query, category, sort ->
        var filtered = list

        if (category == "Favorites") {
            filtered = filtered.filter { it.isFavorite }
        } else if (category != "All") {
            filtered = filtered.filter { it.category.equals(category, ignoreCase = true) }
        }

        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            filtered = filtered.filter {
                it.title.lowercase().contains(q) ||
                it.category.lowercase().contains(q) ||
                it.notes.lowercase().contains(q)
            }
        }

        when (sort) {
            SortOption.DATE_DESC -> filtered.sortedByDescending { it.addedTimestamp }
            SortOption.DATE_ASC -> filtered.sortedBy { it.addedTimestamp }
            SortOption.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
            SortOption.TITLE_DESC -> filtered.sortedByDescending { it.title.lowercase() }
            SortOption.SIZE_DESC -> filtered.sortedByDescending { it.fileSize }
            SortOption.SIZE_ASC -> filtered.sortedBy { it.fileSize }
            SortOption.RECENTLY_READ -> filtered.sortedByDescending { it.lastReadTimestamp }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val storageStats: StateFlow<StorageStats> = allPdfs.combine(MutableStateFlow(Unit)) { list, _ ->
        val totalBytes = list.sumOf { it.fileSize }
        val totalPages = list.sumOf { it.pageCount }
        StorageStats(
            totalCount = list.size,
            totalSizeBytes = totalBytes,
            totalPages = totalPages
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StorageStats())

    // Active Reader State
    private val _activePdfId = MutableStateFlow<Long?>(null)
    val activePdfId = _activePdfId.asStateFlow()

    val activePdf: StateFlow<PdfDocumentEntity?> = _activePdfId.flatMapLatest { id ->
        if (id == null) flowOf(null) else repository.getPdfById(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _currentPageIndex = MutableStateFlow(0)
    val currentPageIndex = _currentPageIndex.asStateFlow()

    private val _currentPageBitmap = MutableStateFlow<Bitmap?>(null)
    val currentPageBitmap = _currentPageBitmap.asStateFlow()

    private val _isRenderingPage = MutableStateFlow(false)
    val isRenderingPage = _isRenderingPage.asStateFlow()

    private val _isNightMode = MutableStateFlow(false)
    val isNightMode = _isNightMode.asStateFlow()

    private val _isControlsVisible = MutableStateFlow(true)
    val isControlsVisible = _isControlsVisible.asStateFlow()

    val activeBookmarks: StateFlow<List<BookmarkEntity>> = _activePdfId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.getBookmarks(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Dialog & Feedback states
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    private val _editingDocument = MutableStateFlow<PdfDocumentEntity?>(null)
    val editingDocument = _editingDocument.asStateFlow()

    private val _deletingDocument = MutableStateFlow<PdfDocumentEntity?>(null)
    val deletingDocument = _deletingDocument.asStateFlow()

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog = _showCreateDialog.asStateFlow()

    private val _showScanDialog = MutableStateFlow(false)
    val showScanDialog = _showScanDialog.asStateFlow()

    private val _showAddBookmarkDialog = MutableStateFlow(false)
    val showAddBookmarkDialog = _showAddBookmarkDialog.asStateFlow()

    private val _showBookmarksSheet = MutableStateFlow(false)
    val showBookmarksSheet = _showBookmarksSheet.asStateFlow()

    // Category Management Dialog states
    private val _showManageCategoriesSheet = MutableStateFlow(false)
    val showManageCategoriesSheet = _showManageCategoriesSheet.asStateFlow()

    private val _showCreateCategoryDialog = MutableStateFlow(false)
    val showCreateCategoryDialog = _showCreateCategoryDialog.asStateFlow()

    private val _editingCategory = MutableStateFlow<CategoryEntity?>(null)
    val editingCategory = _editingCategory.asStateFlow()

    private val _deletingCategory = MutableStateFlow<CategoryEntity?>(null)
    val deletingCategory = _deletingCategory.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setSortOption(sort: SortOption) {
        _sortOption.value = sort
    }

    fun toggleViewMode() {
        _isGridView.value = !_isGridView.value
    }

    fun toggleFavorite(pdf: PdfDocumentEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(pdf.id, !pdf.isFavorite)
        }
    }

    fun openDocument(pdf: PdfDocumentEntity) {
        _activePdfId.value = pdf.id
        val initialPage = maxOf(0, minOf(pdf.lastReadPage - 1, pdf.pageCount - 1))
        _currentPageIndex.value = initialPage
        renderCurrentPage(pdf, initialPage)
    }

    fun closeDocument() {
        val currentId = _activePdfId.value
        val page = _currentPageIndex.value + 1
        if (currentId != null) {
            viewModelScope.launch {
                repository.updateReadingProgress(currentId, page)
            }
        }
        _activePdfId.value = null
        _currentPageBitmap.value = null
    }

    fun goToPage(pageIndex: Int) {
        val pdf = activePdf.value ?: return
        val clamped = pageIndex.coerceIn(0, pdf.pageCount - 1)
        if (clamped != _currentPageIndex.value) {
            _currentPageIndex.value = clamped
            renderCurrentPage(pdf, clamped)
            viewModelScope.launch {
                repository.updateReadingProgress(pdf.id, clamped + 1)
            }
        }
    }

    fun nextPage() {
        val pdf = activePdf.value ?: return
        if (_currentPageIndex.value < pdf.pageCount - 1) {
            goToPage(_currentPageIndex.value + 1)
        }
    }

    fun previousPage() {
        if (_currentPageIndex.value > 0) {
            goToPage(_currentPageIndex.value - 1)
        }
    }

    fun toggleNightMode() {
        _isNightMode.value = !_isNightMode.value
    }

    fun toggleControlsVisibility() {
        _isControlsVisible.value = !_isControlsVisible.value
    }

    private fun renderCurrentPage(pdf: PdfDocumentEntity, pageIndex: Int) {
        viewModelScope.launch {
            _isRenderingPage.value = true
            val file = repository.getPdfFile(pdf.fileName)
            val bitmap = repository.renderPage(file, pageIndex, 1400)
            _currentPageBitmap.value = bitmap
            _isRenderingPage.value = false
        }
    }

    fun addBookmark(note: String) {
        val pdf = activePdf.value ?: return
        val page = _currentPageIndex.value + 1
        viewModelScope.launch {
            repository.addBookmark(pdf.id, page, note)
            _statusMessage.value = "Page $page bookmarked"
            _showAddBookmarkDialog.value = false
        }
    }

    fun deleteBookmark(bookmarkId: Long) {
        viewModelScope.launch {
            repository.deleteBookmark(bookmarkId)
        }
    }

    fun importPdf(uri: Uri, category: String = "General") {
        viewModelScope.launch {
            _statusMessage.value = "Importing PDF document..."
            val success = repository.importPdf(uri, category)
            _statusMessage.value = if (success) "Document added to vault!" else "Failed to import document"
        }
    }

    fun createDocument(title: String, author: String, category: String, content: String, notes: String) {
        viewModelScope.launch {
            _statusMessage.value = "Generating PDF document..."
            val success = repository.createTextDocument(title, author, category, content, notes)
            _showCreateDialog.value = false
            _statusMessage.value = if (success) "New PDF created successfully!" else "Failed to generate PDF"
        }
    }

    fun scanImagesToPdf(title: String, category: String, imageUris: List<Uri>) {
        viewModelScope.launch {
            _statusMessage.value = "Converting images to PDF..."
            val success = repository.createPdfFromImages(title, category, imageUris)
            _showScanDialog.value = false
            _statusMessage.value = if (success) "Scanned document created!" else "Failed to convert images"
        }
    }

    fun updateDocumentMetadata(id: Long, title: String, category: String, notes: String) {
        viewModelScope.launch {
            repository.updateMetadata(id, title, category, notes)
            _editingDocument.value = null
            _statusMessage.value = "Document updated"
        }
    }

    fun updatePdfCategory(pdfId: Long, category: String) {
        viewModelScope.launch {
            repository.updatePdfCategory(pdfId, category)
            _statusMessage.value = "Category updated"
        }
    }

    // Category Operations
    fun createCategory(name: String, colorHex: String, iconName: String) {
        viewModelScope.launch {
            repository.insertCategory(name, colorHex, iconName)
            _showCreateCategoryDialog.value = false
            _statusMessage.value = "Category \"$name\" created"
        }
    }

    fun updateCategory(category: CategoryEntity, oldName: String) {
        viewModelScope.launch {
            repository.updateCategory(category, oldName)
            _editingCategory.value = null
            _statusMessage.value = "Category updated"
            if (_selectedCategory.value.equals(oldName, ignoreCase = true)) {
                _selectedCategory.value = category.name
            }
        }
    }

    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch {
            repository.deleteCategory(category)
            _deletingCategory.value = null
            _statusMessage.value = "Category deleted. Documents moved to General."
            if (_selectedCategory.value.equals(category.name, ignoreCase = true)) {
                _selectedCategory.value = "All"
            }
        }
    }

    fun setShowManageCategoriesSheet(show: Boolean) {
        _showManageCategoriesSheet.value = show
    }

    fun setShowCreateCategoryDialog(show: Boolean) {
        _showCreateCategoryDialog.value = show
    }

    fun setEditingCategory(category: CategoryEntity?) {
        _editingCategory.value = category
    }

    fun setDeletingCategory(category: CategoryEntity?) {
        _deletingCategory.value = category
    }

    fun confirmDelete(pdf: PdfDocumentEntity) {
        _deletingDocument.value = pdf
    }

    fun cancelDelete() {
        _deletingDocument.value = null
    }

    fun executeDelete() {
        val pdf = _deletingDocument.value ?: return
        viewModelScope.launch {
            if (_activePdfId.value == pdf.id) {
                closeDocument()
            }
            repository.deletePdf(pdf)
            _deletingDocument.value = null
            _statusMessage.value = "Document removed from vault"
        }
    }

    fun setEditingDocument(pdf: PdfDocumentEntity?) {
        _editingDocument.value = pdf
    }

    fun setShowCreateDialog(show: Boolean) {
        _showCreateDialog.value = show
    }

    fun setShowScanDialog(show: Boolean) {
        _showScanDialog.value = show
    }

    fun setShowAddBookmarkDialog(show: Boolean) {
        _showAddBookmarkDialog.value = show
    }

    fun setShowBookmarksSheet(show: Boolean) {
        _showBookmarksSheet.value = show
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun getShareIntent(pdf: PdfDocumentEntity) = repository.getShareIntent(pdf)
}
