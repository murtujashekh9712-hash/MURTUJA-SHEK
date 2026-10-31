package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.data.model.PdfDocumentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class PdfStorageManager(private val context: Context) {

    private val pdfsDir: File
        get() = File(context.filesDir, "pdfs").apply { if (!exists()) mkdirs() }

    private val thumbnailsDir: File
        get() = File(context.filesDir, "thumbnails").apply { if (!exists()) mkdirs() }

    fun getPdfFile(fileName: String): File {
        return File(pdfsDir, fileName)
    }

    fun getThumbnailFile(fileName: String): File {
        return File(thumbnailsDir, fileName)
    }

    suspend fun importPdfFromUri(uri: Uri, category: String = "General"): PdfDocumentEntity? = withContext(Dispatchers.IO) {
        try {
            var displayName = "Document_${System.currentTimeMillis()}.pdf"
            var fileSize: Long = 0

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        displayName = cursor.getString(nameIndex) ?: displayName
                    }
                    if (sizeIndex != -1) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }

            // Clean title
            val cleanTitle = if (displayName.endsWith(".pdf", ignoreCase = true)) {
                displayName.substring(0, displayName.length - 4)
            } else displayName

            val uniqueFileName = "pdf_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.pdf"
            val targetFile = File(pdfsDir, uniqueFileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            if (fileSize == 0L || !targetFile.exists()) {
                fileSize = targetFile.length()
            }

            val pageCount = getPdfPageCount(targetFile)
            val thumbnailName = generateThumbnail(targetFile)

            PdfDocumentEntity(
                title = cleanTitle,
                fileName = uniqueFileName,
                relativePath = targetFile.absolutePath,
                fileSize = fileSize,
                pageCount = maxOf(1, pageCount),
                category = category,
                addedTimestamp = System.currentTimeMillis(),
                thumbnailFileName = thumbnailName
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getPdfPageCount(file: File): Int {
        if (!file.exists() || file.length() == 0L) return 1
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            renderer.pageCount
        } catch (e: Exception) {
            e.printStackTrace()
            1
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    suspend fun renderPage(file: File, pageIndex: Int, targetWidth: Int = 1200): Bitmap? = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) return@withContext null
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        return@withContext try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null

            page = renderer.openPage(pageIndex)
            val originalWidth = page.width
            val originalHeight = page.height

            val scale = targetWidth.toFloat() / originalWidth.toFloat()
            val finalWidth = targetWidth
            val finalHeight = (originalHeight * scale).toInt()

            val bitmap = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try { page?.close() } catch (_: Exception) {}
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    private fun generateThumbnail(pdfFile: File): String? {
        val bitmap = renderPageSync(pdfFile, 0, 480) ?: return null
        val thumbName = "thumb_${pdfFile.nameWithoutExtension}.jpg"
        val thumbFile = File(thumbnailsDir, thumbName)
        return try {
            FileOutputStream(thumbFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            bitmap.recycle()
            thumbName
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun renderPageSync(file: File, pageIndex: Int, targetWidth: Int = 480): Bitmap? {
        if (!file.exists()) return null
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        return try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) return null
            page = renderer.openPage(pageIndex)
            val scale = targetWidth.toFloat() / page.width.toFloat()
            val w = targetWidth
            val h = (page.height * scale).toInt()
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try { page?.close() } catch (_: Exception) {}
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    fun deletePdfFiles(pdf: PdfDocumentEntity) {
        try {
            val file = getPdfFile(pdf.fileName)
            if (file.exists()) file.delete()
            pdf.thumbnailFileName?.let { thumb ->
                val tFile = getThumbnailFile(thumb)
                if (tFile.exists()) tFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun sharePdf(pdf: PdfDocumentEntity): Intent? {
        val file = getPdfFile(pdf.fileName)
        if (!file.exists()) return null

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, pdf.title)
            putExtra(Intent.EXTRA_TEXT, "Sharing PDF: ${pdf.title}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(shareIntent, "Share ${pdf.title}")
    }

    suspend fun createTextDocumentPdf(
        title: String,
        author: String,
        category: String,
        content: String,
        notes: String = ""
    ): PdfDocumentEntity? = withContext(Dispatchers.IO) {
        try {
            val document = PdfDocument()
            val pageWidth = 595 // A4 standard width in points (72 dpi)
            val pageHeight = 842 // A4 standard height

            val margin = 48f
            val contentWidth = pageWidth - (margin * 2)

            // Setup paints
            val titlePaint = Paint().apply {
                color = Color.rgb(15, 23, 42)
                textSize = 22f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }

            val metaPaint = Paint().apply {
                color = Color.rgb(100, 116, 139)
                textSize = 10f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                isAntiAlias = true
            }

            val headerLinePaint = Paint().apply {
                color = Color.rgb(225, 29, 72)
                strokeWidth = 2.5f
            }

            val bodyPaint = Paint().apply {
                color = Color.rgb(30, 41, 59)
                textSize = 12f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                isAntiAlias = true
            }

            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            // Background
            canvas.drawColor(Color.WHITE)

            // Header Banner
            var yPos = margin + 16f
            canvas.drawText(title, margin, yPos, titlePaint)

            yPos += 14f
            val dateStr = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(Date())
            val authorStr = if (author.isNotBlank()) "Author: $author  •  " else ""
            canvas.drawText("$authorStr$dateStr  •  Category: $category", margin, yPos, metaPaint)

            yPos += 12f
            canvas.drawLine(margin, yPos, margin + contentWidth, yPos, headerLinePaint)

            yPos += 26f

            // Multi-line body text wrapping
            val lines = wrapText(content, bodyPaint, contentWidth)
            val lineHeight = 18f

            for (line in lines) {
                if (yPos > pageHeight - margin) {
                    // Stop on page 1 for simple notes or let's break cleanly
                    break
                }
                canvas.drawText(line, margin, yPos, bodyPaint)
                yPos += lineHeight
            }

            // Footer
            val footerPaint = Paint().apply {
                color = Color.rgb(148, 163, 184)
                textSize = 9f
                isAntiAlias = true
            }
            canvas.drawText("Generated with PDF Stored", margin, pageHeight - margin + 12f, footerPaint)

            document.finishPage(page)

            val uniqueFileName = "doc_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.pdf"
            val targetFile = File(pdfsDir, uniqueFileName)
            FileOutputStream(targetFile).use { out ->
                document.writeTo(out)
            }
            document.close()

            val thumbName = generateThumbnail(targetFile)

            PdfDocumentEntity(
                title = title.ifBlank { "Untitled Document" },
                fileName = uniqueFileName,
                relativePath = targetFile.absolutePath,
                fileSize = targetFile.length(),
                pageCount = 1,
                category = category,
                addedTimestamp = System.currentTimeMillis(),
                notes = notes,
                thumbnailFileName = thumbName
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun createPdfFromImages(
        title: String,
        category: String,
        imageUris: List<Uri>
    ): PdfDocumentEntity? = withContext(Dispatchers.IO) {
        if (imageUris.isEmpty()) return@withContext null
        try {
            val document = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842

            var pageNum = 1
            for (uri in imageUris) {
                val inputStream = context.contentResolver.openInputStream(uri) ?: continue
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                if (originalBitmap == null) continue

                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas

                canvas.drawColor(Color.WHITE)

                // Scale image to fit within page margins
                val margin = 32f
                val availableWidth = pageWidth - (margin * 2)
                val availableHeight = pageHeight - (margin * 2)

                val scale = minOf(
                    availableWidth / originalBitmap.width.toFloat(),
                    availableHeight / originalBitmap.height.toFloat()
                )

                val drawWidth = originalBitmap.width * scale
                val drawHeight = originalBitmap.height * scale

                val left = margin + (availableWidth - drawWidth) / 2f
                val top = margin + (availableHeight - drawHeight) / 2f

                val destRect = Rect(left.toInt(), top.toInt(), (left + drawWidth).toInt(), (top + drawHeight).toInt())
                canvas.drawBitmap(originalBitmap, null, destRect, null)

                document.finishPage(page)
                originalBitmap.recycle()
                pageNum++
            }

            if (pageNum == 1) {
                document.close()
                return@withContext null
            }

            val uniqueFileName = "scan_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.pdf"
            val targetFile = File(pdfsDir, uniqueFileName)
            FileOutputStream(targetFile).use { out ->
                document.writeTo(out)
            }
            document.close()

            val thumbName = generateThumbnail(targetFile)

            PdfDocumentEntity(
                title = title.ifBlank { "Scanned Document" },
                fileName = uniqueFileName,
                relativePath = targetFile.absolutePath,
                fileSize = targetFile.length(),
                pageCount = pageNum - 1,
                category = category,
                addedTimestamp = System.currentTimeMillis(),
                thumbnailFileName = thumbName
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        val paragraphs = text.split("\n")
        for (paragraph in paragraphs) {
            if (paragraph.isBlank()) {
                result.add("")
                continue
            }
            val words = paragraph.split(" ")
            var currentLine = StringBuilder()
            for (word in words) {
                val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                if (paint.measureText(testLine) <= maxWidth) {
                    currentLine = StringBuilder(testLine)
                } else {
                    if (currentLine.isNotEmpty()) {
                        result.add(currentLine.toString())
                    }
                    currentLine = StringBuilder(word)
                }
            }
            if (currentLine.isNotEmpty()) {
                result.add(currentLine.toString())
            }
        }
        return result
    }

    suspend fun createInitialSamplePdfs(): List<PdfDocumentEntity> = withContext(Dispatchers.IO) {
        val list = mutableListOf<PdfDocumentEntity>()
        try {
            // Sample 1: Welcome & Quick Start Guide (Multi-page)
            val guideDoc = PdfDocument()
            val pw = 595
            val ph = 842
            val margin = 48f
            val cWidth = pw - (margin * 2)

            // Page 1
            val page1Info = PdfDocument.PageInfo.Builder(pw, ph, 1).create()
            val page1 = guideDoc.startPage(page1Info)
            val c1 = page1.canvas
            c1.drawColor(Color.WHITE)

            val redHeaderPaint = Paint().apply {
                color = Color.rgb(225, 29, 72)
                style = Paint.Style.FILL
            }
            c1.drawRect(0f, 0f, pw.toFloat(), 90f, redHeaderPaint)

            val h1Paint = Paint().apply {
                color = Color.WHITE
                textSize = 24f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }
            c1.drawText("PDF Stored • User Guide", margin, 52f, h1Paint)

            val subPaint = Paint().apply {
                color = Color.rgb(255, 228, 230)
                textSize = 12f
                isAntiAlias = true
            }
            c1.drawText("Your Private On-Device PDF Vault & Modern Reader", margin, 74f, subPaint)

            var y = 130f
            val secPaint = Paint().apply {
                color = Color.rgb(15, 23, 42)
                textSize = 16f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }
            val bodyPaint = Paint().apply {
                color = Color.rgb(51, 65, 85)
                textSize = 11.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                isAntiAlias = true
            }

            c1.drawText("1. Welcome to PDF Stored", margin, y, secPaint)
            y += 20f
            val p1Text = "PDF Stored provides secure, local-first storage for all your critical PDF documents. From invoices and academic notes to contracts and eBooks, keep your collection organized, instantly searchable, and accessible anytime without relying on cloud servers or internet connections."
            for (line in wrapText(p1Text, bodyPaint, cWidth)) {
                c1.drawText(line, margin, y, bodyPaint)
                y += 16f
            }

            y += 16f
            c1.drawText("2. Key Capabilities & Features", margin, y, secPaint)
            y += 20f
            val features = listOf(
                "• Import Any PDF: Pick documents directly from internal storage, Downloads, or Drive.",
                "• Built-in High-Definition Reader: Native hardware-accelerated rendering.",
                "• Reading Filter & Night Mode: Switch to inverted dark reading mode for eye comfort.",
                "• Bookmarks & Notes: Save favorite pages with custom annotations.",
                "• Create New Documents: Generate structured PDF notes right within the application.",
                "• Image to PDF Converter: Convert photos and scanned papers into crisp multi-page PDFs.",
                "• Categories & Organization: Tag documents as Work, Personal, Invoices, Books, or Study."
            )
            for (f in features) {
                c1.drawText(f, margin, y, bodyPaint)
                y += 18f
            }

            y += 16f
            c1.drawText("3. Privacy & Security Assurance", margin, y, secPaint)
            y += 20f
            val privText = "Your files stay strictly inside this device's sandboxed storage. No external tracking, no telemetry, and zero third-party upload. You retain 100% ownership and confidentiality over all your stored documents."
            for (line in wrapText(privText, bodyPaint, cWidth)) {
                c1.drawText(line, margin, y, bodyPaint)
                y += 16f
            }

            // Footer
            val footerPaint = Paint().apply {
                color = Color.rgb(148, 163, 184)
                textSize = 9f
                isAntiAlias = true
            }
            c1.drawText("Page 1 of 2  •  PDF Stored Official Guide", margin, ph - 24f, footerPaint)

            guideDoc.finishPage(page1)

            // Page 2
            val page2Info = PdfDocument.PageInfo.Builder(pw, ph, 2).create()
            val page2 = guideDoc.startPage(page2Info)
            val c2 = page2.canvas
            c2.drawColor(Color.WHITE)

            c2.drawRect(0f, 0f, pw.toFloat(), 60f, Paint().apply { color = Color.rgb(15, 23, 42) })
            c2.drawText("PDF Stored • Gestures & Quick Tips", margin, 38f, h1Paint.apply { textSize = 18f })

            y = 100f
            c2.drawText("4. Navigation & Reading Controls", margin, y, secPaint)
            y += 20f
            val navTips = listOf(
                "• Single Tap: Toggle toolbar and reading status bar overlay.",
                "• Page Scrubber: Slide the bottom scrubber to rapidly jump across long documents.",
                "• Bookmark Button: Tap the bookmark icon in the top bar to save current page.",
                "• Export & Share: Tap the share button to send your document via email or messaging apps."
            )
            for (tip in navTips) {
                c2.drawText(tip, margin, y, bodyPaint)
                y += 18f
            }

            y += 24f
            c2.drawText("5. Shortcuts & Best Practices", margin, y, secPaint)
            y += 20f
            val shortcuts = listOf(
                "• Categorize Early: Assign categories like 'Invoices' or 'Work' for rapid filtering.",
                "• Star Important Files: Tap the star icon on any card to access it from Favorites.",
                "• Document Notes: Long-press or tap Edit to attach private metadata and memos."
            )
            for (s in shortcuts) {
                c2.drawText(s, margin, y, bodyPaint)
                y += 18f
            }

            c2.drawText("Page 2 of 2  •  PDF Stored Official Guide", margin, ph - 24f, footerPaint)
            guideDoc.finishPage(page2)

            val sample1File = File(pdfsDir, "guide_user_manual.pdf")
            FileOutputStream(sample1File).use { out -> guideDoc.writeTo(out) }
            guideDoc.close()
            val thumb1 = generateThumbnail(sample1File)

            list.add(
                PdfDocumentEntity(
                    title = "PDF Stored User Manual",
                    fileName = sample1File.name,
                    relativePath = sample1File.absolutePath,
                    fileSize = sample1File.length(),
                    pageCount = 2,
                    category = "Guide",
                    isFavorite = true,
                    notes = "Official quick start documentation and features walkthrough.",
                    thumbnailFileName = thumb1
                )
            )

            // Sample 2: Sample Contract & Invoice
            val invoiceDoc = PdfDocument()
            val invPageInfo = PdfDocument.PageInfo.Builder(pw, ph, 1).create()
            val invPage = invoiceDoc.startPage(invPageInfo)
            val ci = invPage.canvas
            ci.drawColor(Color.WHITE)

            // Header
            ci.drawText("TAX INVOICE & RECEIPT", margin, 60f, secPaint.apply { textSize = 20f })
            ci.drawText("Invoice #: INV-2026-0881", margin, 80f, bodyPaint)
            ci.drawText("Date: October 28, 2026", margin, 96f, bodyPaint)

            ci.drawLine(margin, 110f, pw - margin, 110f, redHeaderPaint)

            y = 140f
            ci.drawText("Billed To:", margin, y, secPaint.apply { textSize = 13f })
            y += 18f
            ci.drawText("Acme Corporation Ltd.", margin, y, bodyPaint)
            y += 16f
            ci.drawText("742 Evergreen Terrace, Springfield", margin, y, bodyPaint)
            y += 16f
            ci.drawText("Tax ID: US-9876543210", margin, y, bodyPaint)

            y += 30f
            // Table Header
            val tableBg = Paint().apply { color = Color.rgb(241, 245, 249) }
            ci.drawRect(margin, y - 14f, pw - margin, y + 10f, tableBg)
            ci.drawText("Description", margin + 8f, y, secPaint.apply { textSize = 11f })
            ci.drawText("Qty", 320f, y, secPaint)
            ci.drawText("Rate", 400f, y, secPaint)
            ci.drawText("Amount", 480f, y, secPaint)

            y += 24f
            val items = listOf(
                Triple("Mobile App UI/UX Architecture", "1", "$1,450.00"),
                Triple("PDF Engine Optimization & Integration", "1", "$1,200.00"),
                Triple("Local Security & File Sandboxing", "1", "$850.00"),
                Triple("Maintenance & Quality Verification", "1", "$400.00")
            )
            for (item in items) {
                ci.drawText(item.first, margin + 8f, y, bodyPaint)
                ci.drawText(item.second, 320f, y, bodyPaint)
                ci.drawText(item.third, 400f, y, bodyPaint)
                ci.drawText(item.third, 480f, y, bodyPaint)
                y += 20f
            }

            ci.drawLine(margin, y + 4f, pw - margin, y + 4f, Paint().apply { color = Color.rgb(203, 213, 225) })
            y += 24f
            ci.drawText("Total Due: $3,900.00 USD", 360f, y, secPaint.apply { textSize = 14f; color = Color.rgb(225, 29, 72) })

            y += 40f
            ci.drawText("Status: PAID IN FULL", margin, y, secPaint.apply { color = Color.rgb(16, 185, 129); textSize = 12f })
            ci.drawText("Thank you for your business!", margin, y + 20f, bodyPaint)

            invoiceDoc.finishPage(invPage)
            val sample2File = File(pdfsDir, "invoice_sample_oct2026.pdf")
            FileOutputStream(sample2File).use { out -> invoiceDoc.writeTo(out) }
            invoiceDoc.close()
            val thumb2 = generateThumbnail(sample2File)

            list.add(
                PdfDocumentEntity(
                    title = "Sample Consulting Invoice",
                    fileName = sample2File.name,
                    relativePath = sample2File.absolutePath,
                    fileSize = sample2File.length(),
                    pageCount = 1,
                    category = "Invoices",
                    isFavorite = false,
                    notes = "Sample professional tax receipt & service agreement invoice.",
                    thumbnailFileName = thumb2
                )
            )

        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }
}
