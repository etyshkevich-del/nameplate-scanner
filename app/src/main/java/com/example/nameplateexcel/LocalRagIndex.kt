package com.example.nameplateexcel

import java.util.Locale
import kotlin.math.ln

data class RagHit(
    val number: Int,
    val text: String,
    val score: Double,
)

/**
 * Lightweight offline lexical retriever.
 *
 * BM25 is enriched with normalized word forms, character 4-grams and a small
 * synonym map. It deliberately needs neither a server nor an embedding model.
 */
class LocalRagIndex private constructor(
    val documentName: String,
    val sourceCharacters: Int,
    private val chunks: List<IndexedChunk>,
    private val documentFrequency: Map<String, Int>,
    private val averageLength: Double,
) {
    val chunkCount: Int get() = chunks.size

    fun search(question: String, limit: Int = 4): List<RagHit> {
        val directWords = tokenizeWords(question).distinct()
        if (directWords.isEmpty()) return chunks.take(limit).map {
            RagHit(it.number, it.text, 0.0)
        }
        val summaryRequest = directWords.any { word ->
            SUMMARY_STEMS.any(word::startsWith)
        }
        if (summaryRequest && directWords.size <= 4) return chunks.take(limit).map {
            RagHit(it.number, it.text, 0.0)
        }
            val queryFeatures = queryFeatures(directWords)

        val total = chunks.size.toDouble()
        val normalizedQuestion = normalizeForPhrase(question)
        val ranked = chunks.map { chunk ->
            var score = 0.0
            queryFeatures.forEach { (term, weight) ->
                val tf = chunk.termFrequency[term] ?: 0
                if (tf == 0) return@forEach
                val df = documentFrequency[term] ?: 0
                val idf = ln(1.0 + (total - df + 0.5) / (df + 0.5))
                val denominator = tf + BM25_K1 * (
                    1.0 - BM25_B + BM25_B * chunk.length / averageLength
                )
                score += weight * idf * (tf * (BM25_K1 + 1.0)) / denominator
            }
            if (normalizedQuestion.length >= 12 &&
                normalizeForPhrase(chunk.text).contains(normalizedQuestion)
            ) {
                score += 4.0
            }
            RagHit(chunk.number, chunk.text, score)
        }
            .filter { it.score >= MIN_RELEVANCE_SCORE }
            .sortedByDescending { it.score }
            .take(limit)
        if (ranked.isNotEmpty()) return ranked
        return if (summaryRequest) chunks.take(limit).map {
            RagHit(it.number, it.text, 0.0)
        } else {
            emptyList()
        }
    }

    companion object {
        private const val TARGET_CHARS = 1_250
        private const val OVERLAP_CHARS = 180
        private const val BM25_K1 = 1.35
        private const val BM25_B = 0.72
        private const val MIN_RELEVANCE_SCORE = 0.08
        private const val DIRECT_GRAM_WEIGHT = 0.16
        private const val EXPANDED_WORD_WEIGHT = 0.48
        private const val EXPANDED_GRAM_WEIGHT = 0.06
        private val SUMMARY_STEMS = setOf("кратк", "содерж", "резюм", "summary", "summar", "overview")
        private val tokenRegex = Regex("[\\p{L}\\p{N}]{2,}")
        private val stopWords = setOf(
            "а", "без", "был", "быть", "в", "во", "вот", "все", "для", "до", "его",
            "ее", "если", "есть", "же", "за", "и", "из", "или", "их", "как", "к", "ко",
            "ли", "мы", "на", "не", "но", "о", "об", "он", "она", "они", "от", "по", "при",
            "с", "со", "та", "так", "то", "у", "что", "это", "этот", "я", "the", "and", "for",
            "from", "into", "of", "on", "or", "that", "this", "to", "with",
        )
        private val stopWordStems by lazy { stopWords.map(::normalizeWord).toSet() }
        private val synonymGroups by lazy { listOf(
            setOf("срок", "период", "длительность", "продолжительность", "время"),
            setOf("стоимость", "цена", "тариф", "оплата", "платеж"),
            setOf("обслуживание", "техобслуживание", "сервис", "ремонт", "профилактика"),
            setOf("требование", "условие", "правило", "обязанность", "необходимо"),
            setOf("гарантия", "гарантийный", "гарантийное"),
            setOf("мощность", "киловатт", "квт"),
            setOf("напряжение", "вольт", "в"),
            setOf("ток", "ампер", "а"),
            setOf("частота", "герц", "гц"),
            setOf("адрес", "место", "расположение", "местонахождение"),
            setOf("deadline", "period", "duration", "term"),
            setOf("cost", "price", "payment", "fee"),
            setOf("maintenance", "service", "repair"),
            setOf("requirement", "condition", "rule"),
            setOf("warranty", "guarantee"),
        ).map { group -> group.map(::normalizeWord).filter { it.length >= 2 }.toSet() } }

        fun build(documentName: String, rawText: String): LocalRagIndex {
            val cleaned = rawText
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace(Regex("[ \\t]+"), " ")
                .replace(Regex("\n{3,}"), "\n\n")
                .trim()
            require(cleaned.length >= 80) {
                "В документе найдено слишком мало текста. Возможно, это скан без текстового слоя."
            }
            val texts = makeChunks(cleaned)
            require(texts.isNotEmpty()) { "Не удалось разбить документ на фрагменты." }
            val indexed = texts.mapIndexed { index, text ->
                val words = tokenizeWords(text)
                val terms = documentFeatures(words)
                IndexedChunk(
                    number = index + 1,
                    text = text,
                    termFrequency = terms.groupingBy { it }.eachCount(),
                    length = words.size.coerceAtLeast(1),
                )
            }
            val frequency = mutableMapOf<String, Int>()
            indexed.forEach { chunk ->
                chunk.termFrequency.keys.forEach { term ->
                    frequency[term] = (frequency[term] ?: 0) + 1
                }
            }
            return LocalRagIndex(
                documentName = documentName,
                sourceCharacters = cleaned.length,
                chunks = indexed,
                documentFrequency = frequency,
                averageLength = indexed.map { it.length }.average().coerceAtLeast(1.0),
            )
        }

        private fun makeChunks(text: String): List<String> {
            val paragraphs = text.split(Regex("\n{2,}"))
                .map { it.replace(Regex("\\s+"), " ").trim() }
                .filter { it.isNotBlank() }
                .flatMap(::splitLongParagraph)
            val result = mutableListOf<String>()
            val current = StringBuilder()
            paragraphs.forEach { paragraph ->
                if (current.isNotEmpty() && current.length + paragraph.length + 2 > TARGET_CHARS) {
                    val completed = current.toString().trim()
                    result += completed
                    current.clear()
                    // Carry the tail into the next chunk so facts spanning a
                    // boundary remain retrievable from either side.
                    val overlap = completed.takeLast(OVERLAP_CHARS)
                        .substringAfter(' ', "")
                        .trim()
                    if (overlap.isNotBlank()) current.append(overlap).append("\n\n")
                }
                current.append(paragraph).append("\n\n")
            }
            if (current.isNotBlank()) result += current.toString().trim()
            return result.filter { it.length >= 40 }
        }

        private fun splitLongParagraph(paragraph: String): List<String> {
            if (paragraph.length <= TARGET_CHARS) return listOf(paragraph)
            val words = paragraph.split(' ')
            val pieces = mutableListOf<String>()
            var start = 0
            while (start < words.size) {
                val part = StringBuilder()
                var end = start
                while (end < words.size && part.length + words[end].length + 1 <= TARGET_CHARS) {
                    if (part.isNotEmpty()) part.append(' ')
                    part.append(words[end])
                    end++
                }
                if (end == start) {
                    val word = words[start]
                    pieces += word.chunked(TARGET_CHARS)
                    start++
                    continue
                }
                pieces += part.toString()
                var overlapLength = 0
                var nextStart = end
                while (nextStart > start && overlapLength < OVERLAP_CHARS) {
                    nextStart--
                    overlapLength += words[nextStart].length + 1
                }
                start = if (nextStart <= start) end else nextStart
            }
            return pieces
        }

        private fun tokenizeWords(value: String): List<String> = tokenRegex
            .findAll(value.lowercase(Locale.ROOT).replace('ё', 'е'))
            .map { normalizeWord(it.value) }
            .filterNot { it in stopWordStems }
            .filter { it.length >= 2 }
            .toList()

        private fun documentFeatures(words: List<String>): List<String> = buildList {
            words.forEach { word ->
                add("w:$word")
                characterGrams(word).forEach { add("g:$it") }
            }
        }

        private fun queryFeatures(directWords: List<String>): Map<String, Double> {
            val result = linkedMapOf<String, Double>()
            directWords.forEach { word ->
                result["w:$word"] = 1.0
                characterGrams(word).forEach { gram ->
                    result["g:$gram"] = maxOf(result["g:$gram"] ?: 0.0, DIRECT_GRAM_WEIGHT)
                }
            }
            val expanded = linkedSetOf<String>()
            synonymGroups.forEach { group ->
                if (directWords.any { it in group }) expanded += group
            }
            expanded.removeAll(directWords.toSet())
            expanded.forEach { word ->
                result["w:$word"] = maxOf(result["w:$word"] ?: 0.0, EXPANDED_WORD_WEIGHT)
                characterGrams(word).forEach { gram ->
                    result["g:$gram"] = maxOf(result["g:$gram"] ?: 0.0, EXPANDED_GRAM_WEIGHT)
                }
            }
            return result
        }

        private fun characterGrams(word: String): List<String> {
            if (word.length < 4 || word.all(Char::isDigit)) return emptyList()
            return word.windowed(size = 4, step = 1, partialWindows = false).distinct()
        }

        private fun normalizeWord(raw: String): String {
            val word = raw.lowercase(Locale.ROOT).replace('ё', 'е')
            if (word.length <= 4 || word.any(Char::isDigit)) return word
            val suffix = RUSSIAN_SUFFIXES.firstOrNull {
                word.endsWith(it) && word.length - it.length >= 4
            }
            if (suffix != null) return word.dropLast(suffix.length)
            val englishSuffix = ENGLISH_SUFFIXES.firstOrNull {
                word.endsWith(it) && word.length - it.length >= 4
            }
            return if (englishSuffix == null) word else word.dropLast(englishSuffix.length)
        }

        private val RUSSIAN_SUFFIXES = listOf(
            "иями", "ями", "ами", "иями", "ового", "евого", "ировать", "ировать",
            "ение", "ения", "ению", "ением", "ений", "ание", "ания", "анию", "анием",
            "остью", "ости", "ого", "ему", "ому", "ими", "ыми", "ий", "ый", "ой",
            "ая", "яя", "ое", "ее", "ие", "ые", "ую", "юю", "ам", "ям", "ах", "ях",
            "ом", "ем", "ов", "ев", "ей", "ию", "ью", "ия", "ья", "ы", "и", "ь", "й",
            "а", "я", "у", "ю", "е", "о",
        ).distinct().sortedByDescending(String::length)

        private val ENGLISH_SUFFIXES = listOf(
            "ments", "ment", "ations", "ation", "ingly", "edly", "ing", "ed", "ies", "es", "s",
        ).sortedByDescending(String::length)

        private fun normalizeForPhrase(value: String): String = value
            .lowercase(Locale.ROOT)
            .replace('ё', 'е')
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
    }

    private data class IndexedChunk(
        val number: Int,
        val text: String,
        val termFrequency: Map<String, Int>,
        val length: Int,
    )
}
