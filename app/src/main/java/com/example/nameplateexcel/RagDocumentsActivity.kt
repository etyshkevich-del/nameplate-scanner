package com.example.nameplateexcel

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import java.util.concurrent.Executors

class RagDocumentsActivity : Activity() {
    private lateinit var store: RagStore
    private lateinit var chatId: String
    private lateinit var documentList: LinearLayout
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var addButton: Button
    private lateinit var openChatButton: Button
    private val worker = Executors.newSingleThreadExecutor()
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chatId = intent.getStringExtra(RagWorkspaceActivity.EXTRA_CHAT_ID).orEmpty()
        store = RagStore(this)
        if (chatId.isBlank() || store.getChat(chatId) == null) {
            finish()
            return
        }
        setContentView(buildUi())
        refreshDocuments()
    }

    private fun buildUi(): ScrollView {
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(30))
        }
        scroll.addView(root)
        root.addView(TextView(this).apply {
            text = "Файлы диалога"
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFF0D47A1.toInt())
        })
        root.addView(TextView(this).apply {
            text = "Можно выбрать сразу несколько PDF/DOCX. Один и тот же файл хранится только один раз, но может использоваться в разных диалогах."
            textSize = 15f
            setPadding(0, dp(8), 0, dp(14))
        })
        addButton = Button(this).apply {
            text = "Добавить PDF/DOCX"
            isAllCaps = false
            setOnClickListener { chooseDocuments() }
        }
        root.addView(addButton, matchWidth())
        progress = ProgressBar(this).apply {
            isIndeterminate = true
            visibility = View.GONE
        }
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))
        status = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFF455A64.toInt())
            setPadding(0, dp(3), 0, dp(12))
        }
        root.addView(status)
        documentList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(documentList)
        openChatButton = Button(this).apply {
            text = "Перейти к диалогу"
            textSize = 16f
            isAllCaps = false
            setOnClickListener {
                startActivity(Intent(this@RagDocumentsActivity, RagChatActivity::class.java)
                    .putExtra(RagWorkspaceActivity.EXTRA_CHAT_ID, chatId))
            }
        }
        root.addView(openChatButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(16) })
        return scroll
    }

    private fun chooseDocuments() {
        if (busy) return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", DocumentTextExtractor.DOCX_MIME))
        }
        startActivityForResult(intent, REQUEST_DOCUMENTS)
    }

    @Deprecated("Kept for broad document-provider compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_DOCUMENTS || resultCode != RESULT_OK || data == null) return
        val uris = mutableListOf<Uri>()
        data.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) uris += clip.getItemAt(index).uri
        }
        if (uris.isEmpty()) data.data?.let(uris::add)
        if (uris.isNotEmpty()) importDocuments(uris.distinct())
    }

    private fun importDocuments(uris: List<Uri>) {
        setBusy(true, "Подготовка ${uris.size} файл(ов)…")
        worker.execute {
            var added = 0
            var reused = 0
            var skipped = 0
            val errors = mutableListOf<String>()
            uris.forEachIndexed { index, uri ->
                val name = getDisplayName(uri)
                runOnUiThread { status.text = "Файл ${index + 1} из ${uris.size}: $name" }
                try {
                    val (fingerprint, bytes) = store.fingerprint(uri)
                    val existing = store.getDocument(fingerprint)
                    val extracted = if (existing == null) {
                        DocumentTextExtractor.extract(this, uri, name, contentResolver.getType(uri))
                    } else null
                    val result = store.attachDocument(chatId, fingerprint, name, extracted, bytes)
                    when {
                        result.alreadyAttached -> skipped++
                        result.alreadyStored -> reused++
                        else -> added++
                    }
                } catch (e: Exception) {
                    errors += "$name: ${e.message}"
                }
            }
            runOnUiThread {
                refreshDocuments()
                val summary = "Новых: $added, подключено ранее загруженных: $reused, уже были в диалоге: $skipped."
                setBusy(false, if (errors.isEmpty()) summary else "$summary Ошибок: ${errors.size}.")
                if (errors.isNotEmpty()) Toast.makeText(this, errors.take(3).joinToString("\n"), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refreshDocuments() {
        documentList.removeAllViews()
        val documents = store.documentsForChat(chatId)
        if (documents.isEmpty()) {
            documentList.addView(TextView(this).apply {
                text = "Файлы ещё не подключены."
                textSize = 15f
                setPadding(0, dp(12), 0, dp(12))
            })
        }
        documents.forEach { document ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(10), dp(7), dp(4), dp(7))
                setBackgroundColor(0xFFF1F4F6.toInt())
            }
            row.addView(TextView(this).apply {
                text = "${document.name}\n${formatCount(document.characters)} символов" +
                    (document.pages?.let { " · $it стр." } ?: "")
                textSize = 14f
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Button(this).apply {
                text = "Убрать"
                isAllCaps = false
                setOnClickListener {
                    store.detachDocument(chatId, document.id)
                    refreshDocuments()
                }
            })
            documentList.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(8) })
        }
        openChatButton.isEnabled = documents.isNotEmpty() && !busy
    }

    private fun setBusy(value: Boolean, message: String) {
        busy = value
        progress.visibility = if (value) View.VISIBLE else View.GONE
        addButton.isEnabled = !value
        openChatButton.isEnabled = !value && store.documentsForChat(chatId).isNotEmpty()
        status.text = message
    }

    private fun getDisplayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null).use { cursor ->
            if (cursor != null && cursor.moveToFirst()) {
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0) return cursor.getString(column) ?: "document"
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "document"
    }

    private fun matchWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun formatCount(value: Int) = String.format(Locale.US, "%,d", value).replace(',', ' ')
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_DOCUMENTS = 301
    }
}
