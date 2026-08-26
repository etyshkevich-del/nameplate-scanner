package com.example.nameplateexcel

import java.util.Locale

/**
 * Проверяет ответ модели перед показом пользователю и записью в Excel.
 *
 * Мультимодальная модель иногда верно читает число, но относит его к соседней
 * колонке таблицы. Поэтому здесь принимаются только значения, форма и единицы
 * которых соответствуют конкретной характеристике. Сомнительное значение
 * лучше оставить пустым для ручной проверки, чем записать в паспорт как факт.
 */
object NameplateResultNormalizer {
    data class Result(
        val values: Map<String, String>,
        val rejectedFields: Set<String>,
    )

    private val knownKeys = listOf(
        "ip_rating",
        "efficiency",
        "rated_power",
        "rated_current",
        "explosion_protection",
        "rated_speed",
        "power_factor",
        "phases",
        "rated_voltage",
    )

    fun normalize(input: Map<String, String>): Result {
        val rejected = linkedSetOf<String>()
        val output = knownKeys.associateWith { key ->
            val source = input[key].orEmpty().clean()
            val normalized = when (key) {
                "ip_rating" -> normalizeIp(source)
                "efficiency" -> normalizeEfficiency(source)
                "rated_power" -> normalizePower(source)
                "rated_current" -> normalizeCurrent(source)
                "explosion_protection" -> normalizeExplosionProtection(source)
                "rated_speed" -> normalizeSpeed(source)
                "power_factor" -> normalizePowerFactor(source)
                "phases" -> normalizePhases(source)
                "rated_voltage" -> normalizeVoltage(source)
                else -> ""
            }
            if (source.isNotEmpty() && normalized.isEmpty()) rejected += key
            normalized
        }
        return Result(output, rejected)
    }

    private fun normalizeIp(value: String): String {
        val match = Regex("(?i)\\bIP\\s*([0-9]{2})\\b").find(value) ?: return ""
        return "IP${match.groupValues[1]}"
    }

    private fun normalizeEfficiency(value: String): String {
        if (value.isEmpty()) return ""
        val normalized = value.replace(',', '.')
        val explicitEta = Regex("(?i)(?:η|eta|eff(?:iciency)?|кпд)\\s*[:=]?\\s*(\\d{1,3}(?:\\.\\d+)?)")
            .find(normalized)?.groupValues?.get(1)
        val percent = Regex("(\\d{1,3}(?:\\.\\d+)?)\\s*%")
            .find(normalized)?.groupValues?.get(1)
        // Некоторые шильдики печатают класс и КПД рядом: IE2-87,6.
        val afterEfficiencyClass = Regex("(?i)\\bIE\\s*[1-5]\\s*[-–/]\\s*(\\d{2,3}(?:\\.\\d+)?)")
            .find(normalized)?.groupValues?.get(1)
        val candidate = explicitEta ?: percent ?: afterEfficiencyClass ?: return ""
        val number = candidate.toDoubleOrNull() ?: return ""
        if (number !in 20.0..100.0) return ""
        return "${formatNumber(number)} %"
    }

    private fun normalizePower(value: String): String {
        if (value.isEmpty() || containsAny(value, "rpm", "r/min", "1/min", "min-1", "min⁻¹", "об/мин", "hz", "гц")) return ""
        val match = Regex("(?i)(\\d+(?:[.,]\\d+)?)\\s*(kW|кВт|MW|МВт|W|Вт)\\b").find(value) ?: return ""
        val number = parseNumber(match.groupValues[1]) ?: return ""
        if (number <= 0.0) return ""
        val unit = when (match.groupValues[2].lowercase(Locale.ROOT)) {
            "kw", "квт" -> "kW"
            "mw", "мвт" -> "MW"
            else -> "W"
        }
        return "${formatNumber(number)} $unit"
    }

    private fun normalizeCurrent(value: String): String {
        if (value.isEmpty() || containsAny(value, "kw", "квт", "rpm", "r/min", "1/min", "min-1", "min⁻¹", "об/мин", "hz", "гц")) return ""
        val match = Regex("(?i)((?:\\d+(?:[.,]\\d+)?)(?:\\s*[/;]\\s*\\d+(?:[.,]\\d+)?)*)\\s*(A|А)\\b")
            .find(value) ?: return ""
        val numbers = match.groupValues[1].split(Regex("\\s*[/;]\\s*")).mapNotNull(::parseNumber)
        if (numbers.isEmpty() || numbers.any { it <= 0.0 }) return ""
        return numbers.joinToString("/") { formatNumber(it) } + " A"
    }

    private fun normalizeExplosionProtection(value: String): String {
        if (value.isEmpty()) return ""
        if (Regex("(?i)\\bPTC\\b").containsMatchIn(value)) return ""
        val compact = value.trim()
        if (compact.equals("Ex", ignoreCase = true)) return ""
        if (!Regex("(?i)ex").containsMatchIn(compact)) return ""
        // Полная маркировка содержит исполнение/группу/температурный класс,
        // например 1Ex d IIB T4. Один логотип Ex ничего не говорит об уровне.
        val meaningfulTail = compact.replace(Regex("(?i)^\\s*\\d*\\s*Ex\\s*"), "").trim()
        return if (meaningfulTail.length >= 2 && meaningfulTail.any(Char::isLetterOrDigit)) compact else ""
    }

    private fun normalizeSpeed(value: String): String {
        if (value.isEmpty() || containsAny(value, "kw", "квт", "hz", "гц")) return ""
        val match = Regex("(?i)(\\d{2,6}(?:[.,]\\d+)?)\\s*(rpm|r/min|1/min|min(?:\\^?-?1|⁻¹)|об/мин)")
            .find(value) ?: return ""
        val number = parseNumber(match.groupValues[1]) ?: return ""
        if (number !in 100.0..100_000.0) return ""
        return "${formatNumber(number)} 1/min"
    }

    private fun normalizePowerFactor(value: String): String {
        if (value.isEmpty()) return ""
        val normalized = value.replace(',', '.')
        val match = Regex("(?i)(?:cos\\s*[φϕf]?\\s*[:=]?\\s*)?(0(?:\\.\\d+)?|1(?:\\.0+)?)").find(normalized)
            ?: return ""
        val number = match.groupValues[1].toDoubleOrNull() ?: return ""
        if (number !in 0.0..1.0) return ""
        return formatNumber(number)
    }

    private fun normalizePhases(value: String): String {
        if (value.isEmpty()) return ""
        val match = Regex("(?i)(?:^|\\b)([13])\\s*(?:~|ph|фаз(?:а|ы)?|phase(?:s)?)?(?:$|\\b)").find(value)
            ?: return ""
        return match.groupValues[1]
    }

    private fun normalizeVoltage(value: String): String {
        if (value.isEmpty() || containsAny(value, "kw", "квт", "rpm", "r/min", "1/min", "min⁻¹", "об/мин", "hz", "гц")) return ""
        if (!Regex("(?i)(?:\\bV\\b|В\\b|[Δ∆△YУ]/?|/\\s*[YУ])").containsMatchIn(value)) return ""
        val numbers = Regex("\\d+(?:[.,]\\d+)?").findAll(value).map { it.value }.toList()
        if (numbers.isEmpty()) return ""
        val normalized = value
            .replace(',', '.')
            .replace(Regex("(?i)\\bvolts?\\b"), "V")
            .replace(Regex("(?i)\\s*[VВ]\\s*$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return "$normalized V"
    }

    private fun String.clean(): String = trim()
        .replace('−', '-')
        .replace('–', '-')
        .replace(Regex("\\s+"), " ")

    private fun containsAny(value: String, vararg tokens: String): Boolean {
        val lower = value.lowercase(Locale.ROOT)
        return tokens.any { lower.contains(it.lowercase(Locale.ROOT)) }
    }

    private fun parseNumber(value: String): Double? = value.replace(',', '.').toDoubleOrNull()

    private fun formatNumber(value: Double): String {
        val whole = value.toLong()
        return if (value == whole.toDouble()) whole.toString()
        else String.format(Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
    }
}
