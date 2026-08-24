package com.example.nameplateexcel

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan

object MarkdownText {
    private val inline = Regex("(\\*\\*([^*]+)\\*\\*)|(`([^`]+)`)|(\\*([^*]+)\\*)")

    fun render(markdown: String): CharSequence {
        val output = SpannableStringBuilder()
        markdown.replace("\\r\\n", "\\n").lines().forEachIndexed { index, original ->
            var line = original
            var headingLevel = 0
            val heading = Regex("^(#{1,3})\\s+(.+)$").matchEntire(line)
            if (heading != null) {
                headingLevel = heading.groupValues[1].length
                line = heading.groupValues[2]
            } else {
                line = line.replaceFirst(Regex("^\\s*[-*+]\\s+"), "• ")
            }
            val start = output.length
            appendInline(output, line)
            if (headingLevel > 0) {
                output.setSpan(StyleSpan(Typeface.BOLD), start, output.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                output.setSpan(
                    RelativeSizeSpan(if (headingLevel == 1) 1.28f else if (headingLevel == 2) 1.17f else 1.08f),
                    start,
                    output.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            if (index < markdown.lines().lastIndex) output.append('\n')
        }
        return output
    }

    private fun appendInline(output: SpannableStringBuilder, value: String) {
        var cursor = 0
        inline.findAll(value).forEach { match ->
            output.append(value.substring(cursor, match.range.first))
            val token = match.value
            val content = when {
                token.startsWith("**") -> match.groupValues[2]
                token.startsWith('`') -> match.groupValues[4]
                else -> match.groupValues[6]
            }
            val start = output.length
            output.append(content)
            val end = output.length
            when {
                token.startsWith("**") -> output.setSpan(
                    StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                token.startsWith('`') -> {
                    output.setSpan(TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    output.setSpan(BackgroundColorSpan(0xFFE8EAED.toInt()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    output.setSpan(ForegroundColorSpan(Color.rgb(55, 71, 79)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                else -> output.setSpan(
                    StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            cursor = match.range.last + 1
        }
        output.append(value.substring(cursor))
    }
}
