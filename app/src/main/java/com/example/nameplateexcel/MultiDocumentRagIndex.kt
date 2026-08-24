package com.example.nameplateexcel

data class MultiDocumentRagHit(
    val documentName: String,
    val chunkNumber: Int,
    val text: String,
    val score: Double,
)

/**
 * Searches the independent BM25 index of every document attached to a chat and
 * merges the results into one globally ranked list with source attribution.
 */
class MultiDocumentRagIndex private constructor(
    private val indices: List<LocalRagIndex>,
) {
    val documentCount: Int get() = indices.size
    val chunkCount: Int get() = indices.sumOf { it.chunkCount }

    fun search(question: String, limit: Int = 6): List<MultiDocumentRagHit> = indices
        .flatMap { index ->
            index.search(question, limit).map { hit ->
                MultiDocumentRagHit(
                    documentName = index.documentName,
                    chunkNumber = hit.number,
                    text = hit.text,
                    score = hit.score,
                )
            }
        }
        .sortedByDescending { it.score }
        .take(limit)

    companion object {
        fun build(documents: List<Pair<String, String>>): MultiDocumentRagIndex {
            require(documents.isNotEmpty()) { "К диалогу не подключены документы." }
            return MultiDocumentRagIndex(documents.map { (name, text) ->
                LocalRagIndex.build(name, text)
            })
        }
    }
}
