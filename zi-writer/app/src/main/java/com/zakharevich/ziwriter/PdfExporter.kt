package com.zakharevich.ziwriter

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import java.io.File
import java.io.FileOutputStream

object PdfExporter {

    fun exportAsPdf(context: Context, title: String, content: String): File? {
        return try {
            val pdfDocument = PdfDocument()
            val pageWidth = 595 // A4 width in points
            val pageHeight = 842 // A4 height
            val margin = 40

            val paintTitle = Paint().apply {
                textSize = 18f
                isFakeBoldText = true
                color = android.graphics.Color.BLACK
            }

            val paintText = Paint().apply {
                textSize = 11f
                color = android.graphics.Color.BLACK
            }

            val paintSmall = Paint().apply {
                textSize = 8f
                color = android.graphics.Color.GRAY
            }

            // Split content into lines that fit page
            val lines = mutableListOf<String>()
            val paragraphs = content.split("\n")

            for (para in paragraphs) {
                if (para.trim().isEmpty()) {
                    lines.add("")
                    continue
                }
                // Simple word wrap
                val words = para.split(" ")
                var currentLine = ""
                for (word in words) {
                    val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                    val width = paintText.measureText(testLine)
                    if (width > pageWidth - 2 * margin) {
                        lines.add(currentLine)
                        currentLine = word
                    } else {
                        currentLine = testLine
                    }
                }
                if (currentLine.isNotEmpty()) lines.add(currentLine)
                lines.add("") // blank line after paragraph
            }

            var currentLineIndex = 0
            var pageNumber = 1

            while (currentLineIndex < lines.size) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                var y = margin.toFloat()

                // Title on first page
                if (pageNumber == 1) {
                    canvas.drawText(title, margin.toFloat(), y, paintTitle)
                    y += 30
                    canvas.drawText("ZI Writer • Zakharevich Igor +375293371412", margin.toFloat(), y, paintSmall)
                    y += 20
                    // Line
                    canvas.drawLine(margin.toFloat(), y, (pageWidth - margin).toFloat(), y, paintSmall)
                    y += 20
                }

                // Footer
                val footerY = pageHeight - 20f
                canvas.drawText("Стр. $pageNumber • ZI Writer v4.0", margin.toFloat(), footerY, paintSmall)
                canvas.drawText("Zakharevich Igor +375293371412", (pageWidth - margin - 200).toFloat(), footerY, paintSmall)

                while (currentLineIndex < lines.size && y < pageHeight - 50) {
                    val line = lines[currentLineIndex]
                    canvas.drawText(line, margin.toFloat(), y, paintText)
                    y += 16
                    currentLineIndex++
                }

                pdfDocument.finishPage(page)
                pageNumber++

                // Safety
                if (pageNumber > 100) break
            }

            // Save
            val fileName = "ZIWriter_${title.replace(Regex("[^A-Za-zА-Яа-я0-9]"), "_").take(20)}_${System.currentTimeMillis()}.pdf"
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            val file = File(dir, fileName)
            FileOutputStream(file).use { pdfDocument.writeTo(it) }
            pdfDocument.close()

            // Also try to save to Downloads
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloadsDir.exists() || downloadsDir.mkdirs()) {
                    val downloadFile = File(downloadsDir, fileName)
                    file.copyTo(downloadFile, overwrite = true)
                    return downloadFile
                }
            } catch (e: Exception) {
                // ignore
            }

            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
