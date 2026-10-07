package com.example.nameplateexcel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RagPromptBuilderTest {
    @Test
    fun limitsContextHistoryAndQuestionForMobileInference() {
        val hits = (1..6).map { number ->
            MultiDocumentRagHit("manual-$number.pdf", number, "x".repeat(1_250), 10.0 - number)
        }
        val history = (1..6).map { number ->
            RagChatMessage(if (number % 2 == 0) "assistant" else "user", "h".repeat(800), number.toLong())
        }

        val payload = RagPromptBuilder.build("q".repeat(900), hits, history)

        assertEquals(3, payload.usedHits.size)
        assertTrue(payload.prompt.contains("manual-3.pdf"))
        assertFalse(payload.prompt.contains("manual-4.pdf"))
        assertFalse(payload.prompt.contains("q".repeat(501)))
        assertTrue(payload.prompt.length < 5_000)
    }

    @Test
    fun preservesSourcesAndShortAnswerInstruction() {
        val hit = MultiDocumentRagHit("guide.pdf", 7, "Гарантия составляет 24 месяца.", 2.0)

        val payload = RagPromptBuilder.build("Какой срок гарантии?", listOf(hit), emptyList())

        assertTrue(payload.prompt.contains("[Документ «guide.pdf», фрагмент 7]"))
        assertTrue(payload.prompt.contains("не более 8 пунктов и 1200 символов"))
    }
}
