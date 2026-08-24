package com.example.nameplateexcel

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class StoredRagDocument(
    val id: String,
    val name: String,
    val characters: Int,
    val pages: Int?,
    val bytes: Long,
    val addedAt: Long,
)

data class RagChatMessage(
    val role: String,
    val text: String,
    val createdAt: Long,
)

data class StoredRagChat(
    val id: String,
    var title: String,
    val documentIds: MutableList<String>,
    val messages: MutableList<RagChatMessage>,
    val createdAt: Long,
    var updatedAt: Long,
)

data class ImportDocumentResult(
    val document: StoredRagDocument,
    val alreadyStored: Boolean,
    val alreadyAttached: Boolean,
)

/**
 * Small on-device repository for RAG chats and extracted document text.
 *
 * The original files are identified by their SHA-256 digest. This lets several
 * chats reference the same extracted text without duplicating a large document.
 * No document content leaves [Context.getFilesDir].
 */
class RagStore(private val context: Context) {
    private val root = File(context.filesDir, "rag_v2").apply { mkdirs() }
    private val documentDirectory = File(root, "documents").apply { mkdirs() }
    private val chatDirectory = File(root, "chats").apply { mkdirs() }

    @Synchronized
    fun listChats(): List<StoredRagChat> = chatDirectory.listFiles()
        .orEmpty()
        .filter { it.extension == "json" }
        .mapNotNull { runCatching { readChat(it) }.getOrNull() }
        .sortedByDescending { it.updatedAt }

    @Synchronized
    fun createChat(): StoredRagChat {
        val now = System.currentTimeMillis()
        val chat = StoredRagChat(
            id = UUID.randomUUID().toString(),
            title = "Новый диалог",
            documentIds = mutableListOf(),
            messages = mutableListOf(),
            createdAt = now,
            updatedAt = now,
        )
        saveChat(chat)
        return chat
    }

    @Synchronized
    fun getChat(id: String): StoredRagChat? {
        val file = chatFile(id)
        return if (file.exists()) readChat(file) else null
    }

    @Synchronized
    fun getDocument(id: String): StoredRagDocument? {
        val file = documentMetaFile(id)
        return if (file.exists()) readDocument(file) else null
    }

    fun readDocumentText(id: String): String = documentTextFile(id).readText(Charsets.UTF_8)

    fun fingerprint(uri: Uri): Pair<String, Long> {
        val digest = MessageDigest.getInstance("SHA-256")
        var count = 0L
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Не удалось открыть файл." }
            // Hash while streaming: providers are not required to expose a real
            // filesystem path and a document can be too large for a byte array.
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
                count += read
                require(count <= MAX_DOCUMENT_BYTES) {
                    "Размер файла превышает 100 МБ."
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) } to count
    }

    @Synchronized
    fun attachDocument(
        chatId: String,
        id: String,
        name: String,
        extracted: ExtractedDocument?,
        bytes: Long,
    ): ImportDocumentResult {
        val chat = requireNotNull(getChat(chatId)) { "Диалог не найден." }
        // Metadata existence is the deduplication boundary. A known document is
        // only linked to the chat; extraction and text storage are not repeated.
        val existing = getDocument(id)
        val document = existing ?: run {
            requireNotNull(extracted) { "Текст нового документа не извлечён." }
            val created = StoredRagDocument(
                id = id,
                name = name,
                characters = extracted.text.length,
                pages = extracted.pageCount,
                bytes = bytes,
                addedAt = System.currentTimeMillis(),
            )
            documentTextFile(id).writeText(extracted.text, Charsets.UTF_8)
            documentMetaFile(id).writeText(documentToJson(created).toString(), Charsets.UTF_8)
            created
        }
        val wasAttached = id in chat.documentIds
        if (!wasAttached) {
            chat.documentIds += id
            if (chat.title == "Новый диалог") chat.title = titleFromDocuments(chat.documentIds)
            chat.updatedAt = System.currentTimeMillis()
            saveChat(chat)
        }
        return ImportDocumentResult(document, existing != null, wasAttached)
    }

    @Synchronized
    fun detachDocument(chatId: String, documentId: String) {
        val chat = getChat(chatId) ?: return
        if (chat.documentIds.remove(documentId)) {
            chat.title = if (chat.documentIds.isEmpty()) "Новый диалог" else titleFromDocuments(chat.documentIds)
            chat.updatedAt = System.currentTimeMillis()
            saveChat(chat)
        }
    }

    @Synchronized
    fun appendMessage(chatId: String, role: String, text: String) {
        val chat = requireNotNull(getChat(chatId)) { "Диалог не найден." }
        chat.messages += RagChatMessage(role, text, System.currentTimeMillis())
        if (chat.messages.count { it.role == "user" } == 1) {
            val shortQuestion = text.replace(Regex("\\s+"), " ").take(48).trim()
            if (shortQuestion.isNotBlank()) chat.title = shortQuestion
        }
        chat.updatedAt = System.currentTimeMillis()
        saveChat(chat)
    }

    @Synchronized
    fun documentsForChat(chatId: String): List<StoredRagDocument> = getChat(chatId)
        ?.documentIds
        .orEmpty()
        .mapNotNull(::getDocument)

    private fun titleFromDocuments(ids: List<String>): String {
        val names = ids.mapNotNull(::getDocument).map { it.name.substringBeforeLast('.') }
        return when {
            names.isEmpty() -> "Новый диалог"
            names.size == 1 -> names.first().take(48)
            else -> "${names.first().take(32)} +${names.size - 1}"
        }
    }

    private fun saveChat(chat: StoredRagChat) {
        val json = JSONObject()
            .put("id", chat.id)
            .put("title", chat.title)
            .put("createdAt", chat.createdAt)
            .put("updatedAt", chat.updatedAt)
            .put("documentIds", JSONArray(chat.documentIds))
        val messages = JSONArray()
        chat.messages.forEach { message ->
            messages.put(JSONObject()
                .put("role", message.role)
                .put("text", message.text)
                .put("createdAt", message.createdAt))
        }
        json.put("messages", messages)
        chatFile(chat.id).writeText(json.toString(), Charsets.UTF_8)
    }

    private fun readChat(file: File): StoredRagChat {
        val json = JSONObject(file.readText(Charsets.UTF_8))
        val ids = json.getJSONArray("documentIds")
        val messages = json.optJSONArray("messages") ?: JSONArray()
        return StoredRagChat(
            id = json.getString("id"),
            title = json.optString("title", "Диалог"),
            documentIds = MutableList(ids.length()) { ids.getString(it) },
            messages = MutableList(messages.length()) { index ->
                val item = messages.getJSONObject(index)
                RagChatMessage(
                    role = item.getString("role"),
                    text = item.getString("text"),
                    createdAt = item.optLong("createdAt", 0L),
                )
            },
            createdAt = json.optLong("createdAt", 0L),
            updatedAt = json.optLong("updatedAt", 0L),
        )
    }

    private fun documentToJson(document: StoredRagDocument) = JSONObject()
        .put("id", document.id)
        .put("name", document.name)
        .put("characters", document.characters)
        .put("pages", document.pages ?: JSONObject.NULL)
        .put("bytes", document.bytes)
        .put("addedAt", document.addedAt)

    private fun readDocument(file: File): StoredRagDocument {
        val json = JSONObject(file.readText(Charsets.UTF_8))
        return StoredRagDocument(
            id = json.getString("id"),
            name = json.getString("name"),
            characters = json.getInt("characters"),
            pages = if (json.isNull("pages")) null else json.getInt("pages"),
            bytes = json.optLong("bytes", 0L),
            addedAt = json.optLong("addedAt", 0L),
        )
    }

    private fun chatFile(id: String) = File(chatDirectory, "$id.json")
    private fun documentMetaFile(id: String) = File(documentDirectory, "$id.json")
    private fun documentTextFile(id: String) = File(documentDirectory, "$id.txt")

    companion object {
        const val MAX_DOCUMENT_BYTES = 100L * 1024L * 1024L
    }
}
