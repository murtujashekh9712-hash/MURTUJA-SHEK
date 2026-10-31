package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_documents")
data class PdfDocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val fileName: String,
    val relativePath: String,
    val fileSize: Long,
    val pageCount: Int,
    val category: String = "General",
    val tags: String = "",
    val isFavorite: Boolean = false,
    val lastReadPage: Int = 1,
    val lastReadTimestamp: Long = System.currentTimeMillis(),
    val addedTimestamp: Long = System.currentTimeMillis(),
    val notes: String = "",
    val thumbnailFileName: String? = null
)
