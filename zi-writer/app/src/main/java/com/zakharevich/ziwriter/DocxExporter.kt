package com.zakharevich.ziwriter

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DocxExporter {

    // Creates minimal DOCX (Office Open XML) with title and content
    fun exportAsDocx(context: Context, title: String, content: String): File? {
        return try {
            val fileName = "ZIWriter_${title.replace(Regex("[^A-Za-zА-Яа-я0-9]"), "_").take(20)}_${System.currentTimeMillis()}.docx"
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            val file = File(dir, fileName)

            ZipOutputStream(FileOutputStream(file)).use { zip ->

                // [Content_Types].xml
                zip.putNextEntry(ZipEntry("[Content_Types].xml"))
                zip.write(
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>""".toByteArray()
                )
                zip.closeEntry()

                // _rels/.rels
                zip.putNextEntry(ZipEntry("_rels/.rels"))
                zip.write(
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>""".toByteArray()
                )
                zip.closeEntry()

                // word/_rels/document.xml.rels
                zip.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
                zip.write(
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>""".toByteArray()
                )
                zip.closeEntry()

                // word/styles.xml
                zip.putNextEntry(ZipEntry("word/styles.xml"))
                zip.write(
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:pPr><w:jc w:val="center"/></w:pPr><w:rPr><w:b/><w:sz w:val="32"/><w:color w:val="1A237E"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Normal"><w:name w:val="Normal"/><w:rPr><w:sz w:val="22"/></w:rPr></w:style>
</w:styles>""".toByteArray()
                )
                zip.closeEntry()

                // word/document.xml
                zip.putNextEntry(ZipEntry("word/document.xml"))
                val escapedTitle = escapeXml(title)
                val paragraphsXml = content.split("\n").joinToString("") { para ->
                    val trimmed = para.trim()
                    if (trimmed.isEmpty()) {
                        """<w:p><w:r><w:t></w:t></w:r></w:p>"""
                    } else if (trimmed.startsWith("— — — Страница") || trimmed.uppercase() == trimmed && trimmed.length < 80 && trimmed.length > 3) {
                        // Chapter title
                        val esc = escapeXml(trimmed)
                        """<w:p><w:pPr><w:jc w:val="center"/><w:pStyle w:val="Title"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val="28"/><w:color w:val="FF6F00"/></w:rPr><w:t>$esc</w:t></w:r></w:p>"""
                    } else {
                        val esc = escapeXml(trimmed)
                        """<w:p><w:r><w:t xml:space="preserve">$esc</w:t></w:r></w:p>"""
                    }
                }

                val docXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:body>
<w:p><w:pPr><w:pStyle w:val="Title"/><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val="36"/><w:color w:val="1A237E"/></w:rPr><w:t>$escapedTitle</w:t></w:r></w:p>
<w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:sz w:val="16"/><w:color w:val="888888"/></w:rPr><w:t>ZI Writer • Zakharevich Igor +375293371412</w:t></w:r></w:p>
<w:p><w:r><w:t></w:t></w:r></w:p>
$paragraphsXml
<w:p><w:r><w:t></w:t></w:r></w:p>
<w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:sz w:val="16"/><w:color w:val="FF6F00"/></w:rPr><w:t>— Конец —</w:t></w:r></w:p>
<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/></w:sectPr>
</w:body>
</w:document>"""

                zip.write(docXml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            // Copy to Downloads
            try {
                val downloadsDir = File("/storage/emulated/0/Download")
                if (downloadsDir.exists() || downloadsDir.mkdirs()) {
                    val dlFile = File(downloadsDir, file.name)
                    file.copyTo(dlFile, true)
                    return dlFile
                }
            } catch (e: Exception) {}

            file
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
