package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.PdfDatabase
import com.example.data.model.CategoryEntity
import com.example.data.model.PdfDocumentEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var db: PdfDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PdfDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("PDF Stored", appName)
    }

    @Test
    fun `insert and retrieve categories in Room`() = runBlocking {
        val cat1 = CategoryEntity(name = "Tax Receipts", colorHex = "#059669", iconName = "Receipt")
        val cat2 = CategoryEntity(name = "Contracts", colorHex = "#2563EB", iconName = "Work")

        db.categoryDao().insertCategory(cat1)
        db.categoryDao().insertCategory(cat2)

        val categories = db.categoryDao().getAllCategories().first()
        assertEquals(2, categories.size)
        assertEquals("Tax Receipts", categories[0].name)
        assertEquals("Contracts", categories[1].name)
    }

    @Test
    fun `group documents by category in Room`() = runBlocking {
        db.categoryDao().insertCategory(CategoryEntity(name = "Invoices", colorHex = "#059669"))

        val doc1 = PdfDocumentEntity(
            title = "Invoice 1",
            fileName = "inv1.pdf",
            relativePath = "/pdfs/inv1.pdf",
            fileSize = 1024L,
            pageCount = 1,
            category = "Invoices"
        )
        val doc2 = PdfDocumentEntity(
            title = "Invoice 2",
            fileName = "inv2.pdf",
            relativePath = "/pdfs/inv2.pdf",
            fileSize = 2048L,
            pageCount = 2,
            category = "Invoices"
        )
        val doc3 = PdfDocumentEntity(
            title = "Personal Note",
            fileName = "note.pdf",
            relativePath = "/pdfs/note.pdf",
            fileSize = 512L,
            pageCount = 1,
            category = "Personal"
        )

        db.pdfDao().insertPdf(doc1)
        db.pdfDao().insertPdf(doc2)
        db.pdfDao().insertPdf(doc3)

        val counts = db.pdfDao().getCategoryCounts().first()
        val invoiceCount = counts.find { it.category == "Invoices" }?.count
        val personalCount = counts.find { it.category == "Personal" }?.count

        assertEquals(2, invoiceCount)
        assertEquals(1, personalCount)
    }

    @Test
    fun `reassign documents when category is deleted`() = runBlocking {
        val cat = CategoryEntity(name = "Obsolete", colorHex = "#DC2626")
        db.categoryDao().insertCategory(cat)

        val doc = PdfDocumentEntity(
            title = "Old Report",
            fileName = "old.pdf",
            relativePath = "/pdfs/old.pdf",
            fileSize = 1000L,
            pageCount = 1,
            category = "Obsolete"
        )
        val docId = db.pdfDao().insertPdf(doc)

        // Delete category and reassign
        db.pdfDao().reassignDeletedCategory("Obsolete")
        db.categoryDao().deleteCategory(cat)

        val updatedDoc = db.pdfDao().getPdfByIdDirect(docId)
        assertNotNull(updatedDoc)
        assertEquals("General", updatedDoc?.category)
    }
}
