package com.zakharevich.ziwriter

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object EpubExporter {

    fun exportAsEpub(context: Context, title: String, content: String, author: String = "ZI Writer"): File? {
        return try {
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val safeTitle = title.replace(Regex("[^a-zA-Zа-яА-Я0-9 ]"), "").take(20).ifEmpty { "Book" }
            val fileName = "ZIWriter_${safeTitle}_${sdf.format(Date())}.epub"

            val internalFile = File(context.getExternalFilesDir(null), fileName)
            val downloadFile = File("/storage/emulated/0/Download", fileName)

            // Create EPUB structure
            ZipOutputStream(internalFile.outputStream()).use { zip ->

                // mimetype must be first and uncompressed
                val mimetypeEntry = ZipEntry("mimetype")
                mimetypeEntry.method = ZipEntry.STORED
                val mimetypeBytes = "application/epub+zip".toByteArray()
                mimetypeEntry.size = mimetypeBytes.size.toLong()
                mimetypeEntry.compressedSize = mimetypeBytes.size.toLong()
                val crc = java.util.zip.CRC32()
                crc.update(mimetypeBytes)
                mimetypeEntry.crc = crc.value
                zip.putNextEntry(mimetypeEntry)
                zip.write(mimetypeBytes)
                zip.closeEntry()

                // META-INF/container.xml
                zip.putNextEntry(ZipEntry("META-INF/container.xml"))
                zip.write(
                    """<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>""".toByteArray()
                )
                zip.closeEntry()

                // OEBPS/content.opf
                val escapedTitle = escapeXml(title)
                val escapedAuthor = escapeXml(author)
                val bookId = "urn:uuid:${UUID.randomUUID()}"
                val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date())

                zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
                zip.write(
                    """<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookId" version="2.0">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
    <dc:title>$escapedTitle</dc:title>
    <dc:creator opf:role="aut">$escapedAuthor</dc:creator>
    <dc:language>ru</dc:language>
    <dc:identifier id="BookId">$bookId</dc:identifier>
    <dc:date>$now</dc:date>
    <dc:publisher>ZI Writer</dc:publisher>
    <meta name="cover" content="cover"/>
  </metadata>
  <manifest>
    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
    <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
    <item id="style" href="style.css" media-type="text/css"/>
  </manifest>
  <spine toc="ncx">
    <itemref idref="chapter"/>
  </spine>
  <guide>
    <reference type="text" title="$escapedTitle" href="chapter.xhtml"/>
  </guide>
</package>""".toByteArray()
                )
                zip.closeEntry()

                // OEBPS/toc.ncx
                zip.putNextEntry(ZipEntry("OEBPS/toc.ncx"))
                zip.write(
                    """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE ncx PUBLIC "-//NISO//DTD ncx 2005-1//EN" "http://www.daisy.org/z3986/2005/ncx-2005-1.dtd">
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head>
    <meta name="dtb:uid" content="$bookId"/>
    <meta name="dtb:depth" content="1"/>
    <meta name="dtb:totalPageCount" content="0"/>
    <meta name="dtb:maxPageNumber" content="0"/>
  </head>
  <docTitle><text>$escapedTitle</text></docTitle>
  <navMap>
    <navPoint id="navpoint-1" playOrder="1">
      <navLabel><text>$escapedTitle</text></navLabel>
      <content src="chapter.xhtml"/>
    </navPoint>
  </navMap>
</ncx>""".toByteArray()
                )
                zip.closeEntry()

                // OEBPS/style.css
                zip.putNextEntry(ZipEntry("OEBPS/style.css"))
                zip.write(
                    """
body { font-family: serif; margin: 5%; line-height: 1.6; color: #1A1C1E; }
h1 { text-align: center; font-size: 1.8em; margin-bottom: 0.5em; color: #2D1B69; }
h2 { text-align: center; font-size: 1.3em; margin-top: 2em; color: #2D1B69; }
p { text-indent: 1.5em; margin: 0.5em 0; text-align: justify; }
.author { text-align: center; font-style: italic; margin-bottom: 2em; color: #5A5D60; }
.page-break { page-break-after: always; }
.cover-title { font-size: 2em; font-weight: bold; text-align: center; margin-top: 30%; }
""".trimIndent().toByteArray()
                )
                zip.closeEntry()

                // OEBPS/chapter.xhtml
                zip.putNextEntry(ZipEntry("OEBPS/chapter.xhtml"))
                val paragraphs = content.split("\n\n").filter { it.isNotBlank() }
                val xhtmlBody = buildString {
                    append("""<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
<title>${escapeXml(title)}</title>
<link rel="stylesheet" type="text/css" href="style.css"/>
</head>
<body>
<h1>${escapeXml(title)}</h1>
<div class="author">Автор: ${escapeXml(author)} • ZI Writer v6.0 MEGA</div>
""")
                    for (para in paragraphs) {
                        val trimmed = para.trim()
                        if (trimmed.startsWith("— — — Страница")) {
                            append("<div class=\"page-break\"></div>\n")
                            append("<h2>${escapeXml(trimmed)}</h2>\n")
                        } else if (trimmed.startsWith("Глава") || trimmed.startsWith("ГЛАВА") || trimmed.startsWith("Пролог") || trimmed.startsWith("ПРОЛОГ") || trimmed.startsWith("Эпилог") || trimmed.startsWith("ЭПИЛОГ")) {
                            append("<h2>${escapeXml(trimmed)}</h2>\n")
                        } else if (trimmed == "— Конец —") {
                            append("<h2 style=\"text-align:center; margin-top:3em;\">${escapeXml(trimmed)}</h2>\n")
                        } else {
                            append("<p>${escapeXml(trimmed)}</p>\n")
                        }
                    }
                    append("</body></html>")
                }
                zip.write(xhtmlBody.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            // Try copy to Download
            try {
                internalFile.copyTo(downloadFile, true)
                downloadFile
            } catch (e: Exception) {
                internalFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun escapeXml(s: String): String {
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
