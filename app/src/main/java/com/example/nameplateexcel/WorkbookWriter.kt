package com.example.nameplateexcel

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Writes the nine recognized values without introducing an Apache POI runtime.
 * XLSX is a ZIP package, so only the target worksheet XML is replaced and every
 * other entry is copied through unchanged.
 */
object WorkbookWriter {
    fun build(
        template: InputStream,
        valuesByCell: Map<String, Pair<Int, String>>,
    ): ByteArray {
        val outputBytes = ByteArrayOutputStream()
        template.use { source ->
            ZipInputStream(source).use { zipIn ->
                ZipOutputStream(outputBytes).use { zipOut ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        zipOut.putNextEntry(ZipEntry(entry.name).apply { time = entry.time })
                        val data = zipIn.readBytes()
                        if (entry.name == "xl/worksheets/sheet1.xml") {
                            var xml = data.toString(Charsets.UTF_8)
                            valuesByCell.forEach { (cell, styledValue) ->
                                val (style, value) = styledValue
                                val cellPattern = Regex(
                                    """<c\s+r="$cell"\s+s="$style"(?:\s*/>|>.*?</c>)""",
                                    setOf(RegexOption.DOT_MATCHES_ALL),
                                )
                                // Inline strings keep the template self-contained;
                                // escaping prevents malformed XML from user edits.
                                val replacement =
                                    """<c r="$cell" s="$style" t="inlineStr"><is><t xml:space="preserve">${escapeXml(value)}</t></is></c>"""
                                xml = xml.replace(cellPattern, replacement)
                            }
                            zipOut.write(xml.toByteArray(Charsets.UTF_8))
                        } else {
                            zipOut.write(data)
                        }
                        zipOut.closeEntry()
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            }
        }
        return outputBytes.toByteArray()
    }

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
