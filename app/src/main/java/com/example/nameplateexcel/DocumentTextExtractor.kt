package com.example.nameplateexcel

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser

data class ExtractedDocument(
    val text: String,
    val pageCount: Int?,
)

/** Extracts bounded plain text from supported document containers. */
object DocumentTextExtractor {
    private const val MAX_TEXT_CHARACTERS = 5_000_000
    private const val MAX_PDF_PAGES = 500

    fun extract(
        context: Context,
        uri: Uri,
        displayName: String,
        mimeType: String?,
    ): ExtractedDocument {
        val lowerName = displayName.lowercase(Locale.ROOT)
        return when {
            mimeType == "application/pdf" || lowerName.endsWith(".pdf") -> extractPdf(context, uri)
            mimeType == DOCX_MIME || lowerName.endsWith(".docx") -> extractDocx(context, uri)
            else -> error("Поддерживаются только PDF и DOCX.")
        }
    }

    private fun extractPdf(context: Context, uri: Uri): ExtractedDocument {
        PDFBoxResourceLoader.init(context.applicationContext)
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Не удалось открыть PDF.")
        input.use { stream ->
            PDDocument.load(stream).use { document ->
                require(!document.isEncrypted) { "PDF защищён паролем и не может быть прочитан." }
                require(document.numberOfPages <= MAX_PDF_PAGES) {
                    "В PDF ${document.numberOfPages} страниц, допустимо не более $MAX_PDF_PAGES. " +
                        "Размер файла в мегабайтах на этот лимит не влияет."
                }
                val text = StringBuilder()
                // Page markers survive chunking and give the model a useful
                // reference without asking it to invent page numbers.
                for (page in 1..document.numberOfPages) {
                    val pageText = PDFTextStripper().apply {
                        sortByPosition = true
                        startPage = page
                        endPage = page
                    }.getText(document).trim()
                    if (pageText.isNotBlank()) {
                        text.append("[Страница ").append(page).append("]\n")
                            .append(pageText).append("\n\n")
                    }
                    require(text.length <= MAX_TEXT_CHARACTERS) {
                        "После страницы $page извлечено ${text.length} символов, допустимо не более " +
                            "$MAX_TEXT_CHARACTERS. Это объём распакованного текста, а не размер PDF."
                    }
                }
                return ExtractedDocument(text.toString().trim(), document.numberOfPages)
            }
        }
    }

    private fun extractDocx(context: Context, uri: Uri): ExtractedDocument {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Не удалось открыть DOCX.")
        input.use { stream ->
            val text = readWordDocumentXml(stream)
            require(text.length <= MAX_TEXT_CHARACTERS) {
                "Из DOCX извлечено ${text.length} символов, допустимо не более " +
                    "$MAX_TEXT_CHARACTERS. Это объём распакованного текста, а не размер файла."
            }
            return ExtractedDocument(text.trim(), null)
        }
    }

    private fun readWordDocumentXml(input: InputStream): String {
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                // DOCX is a ZIP package. Streaming only the main document XML
                // avoids unpacking the archive or loading embedded media.
                if (entry.name == "word/document.xml") {
                    val parser = Xml.newPullParser().apply {
                        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                        setInput(zip, "UTF-8")
                    }
                    val result = StringBuilder()
                    var insideText = false
                    var event = parser.eventType
                    while (event != XmlPullParser.END_DOCUMENT) {
                        when (event) {
                            XmlPullParser.START_TAG -> when (parser.name) {
                                "t" -> insideText = true
                                "tab" -> result.append('\t')
                                "br", "cr" -> result.append('\n')
                            }
                            XmlPullParser.TEXT -> if (insideText) result.append(parser.text)
                            XmlPullParser.END_TAG -> when (parser.name) {
                                "t" -> insideText = false
                                "p" -> result.append("\n\n")
                                "tc" -> result.append('\t')
                            }
                        }
                        require(result.length <= MAX_TEXT_CHARACTERS) {
                            "Из DOCX извлечено ${result.length} символов, допустимо не более " +
                                "$MAX_TEXT_CHARACTERS. Это объём распакованного текста, а не размер файла."
                        }
                        event = parser.next()
                    }
                    return result.toString()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        error("Файл не содержит word/document.xml и не похож на DOCX.")
    }

    const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
}
