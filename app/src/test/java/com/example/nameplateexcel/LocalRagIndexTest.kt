package com.example.nameplateexcel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRagIndexTest {
    private val text = """
        [Страница 1]
        Электродвигатель должен проходить техническое обслуживание каждые 500 часов работы.
        Перед обслуживанием необходимо отключить питание и дождаться полной остановки ротора.

        [Страница 2]
        Номинальная мощность двигателя составляет 2,2 кВт. Степень защиты оболочки IP54.
        Допустимая температура эксплуатации находится в диапазоне от минус 45 до плюс 40 градусов.

        [Страница 3]
        Гарантийный срок составляет 24 месяца с даты ввода оборудования в эксплуатацию.
        Гарантия не распространяется на повреждения, возникшие из-за неправильного монтажа.
    """.trimIndent()

    @Test
    fun retrievesRelevantRussianFragment() {
        val index = LocalRagIndex.build("manual.pdf", text)
        val hits = index.search("Как часто проводить техническое обслуживание?")

        assertTrue(hits.isNotEmpty())
        assertTrue(hits.first().text.contains("500 часов"))
    }

    @Test
    fun returnsNothingForUnrelatedQuestion() {
        val index = LocalRagIndex.build("manual.pdf", text)

        assertTrue(index.search("Какой адрес ближайшего магазина?").isEmpty())
    }

    @Test
    fun summaryRequestFallsBackToBeginning() {
        val index = LocalRagIndex.build("manual.pdf", text)
        val hits = index.search("Кратко изложи содержание")

        assertTrue(hits.isNotEmpty())
        assertEquals(1, hits.first().number)
    }

    @Test
    fun matchesDifferentRussianWordForms() {
        val index = LocalRagIndex.build("manual.pdf", text)
        val hits = index.search("Что необходимо сделать перед техобслуживанием?")

        assertTrue(hits.isNotEmpty())
        assertTrue(hits.first().text.contains("отключить питание"))
    }

    @Test
    fun expandsCommonSynonymsAndRanksTheRightChunk() {
        val filler = (1..18).joinToString("\n\n") { number ->
            "Раздел $number. Описание комплектующих, упаковки, транспортировки и маркировки оборудования."
        }
        val longText = "$filler\n\nГарантийный срок составляет 24 месяца с даты ввода в эксплуатацию."
        val index = LocalRagIndex.build("manual.pdf", longText)
        val hits = index.search("Сколько длится гарантия?")

        assertTrue(hits.isNotEmpty())
        assertTrue(hits.first().text.contains("24 месяца"))
    }

    @Test
    fun searchesAcrossSeveralDocumentsAndKeepsSourceName() {
        val index = MultiDocumentRagIndex.build(listOf(
            "installation.pdf" to "Монтаж выполняется на ровном основании. Перед запуском проверьте крепления и заземление. Работы выполняет квалифицированный специалист.",
            "warranty.docx" to "Гарантийный срок оборудования составляет 36 месяцев с даты поставки заказчику. Сохраните документы, подтверждающие дату поставки.",
        ))

        val hits = index.search("Какой гарантийный срок?")

        assertTrue(hits.isNotEmpty())
        assertEquals("warranty.docx", hits.first().documentName)
        assertTrue(hits.first().text.contains("36 месяцев"))
    }
}
