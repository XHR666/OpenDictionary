package io.github.xhr666.opendictionary.media

import java.util.regex.Pattern

data class LrcDoc(
    val lines: List<Triple<Long, Long, String>>,
    val words: List<Pair<String, Triple<Long, Long, String>>>,
)

/** 解析 LRC：支持普通时间行与增强逐词 <mm:ss.xx>；无逐词时间时按字符比例估算。 */
object LrcParser {
    private val lineTag = Pattern.compile("\\[(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]")
    private val wordTag = Pattern.compile("<(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?>")

    fun parse(raw: String): LrcDoc {
        var offset = 0L
        val timed = mutableListOf<Pair<Long, String>>()
        raw.lineSequence().forEach { line ->
            if (line.startsWith("[offset:")) {
                offset = line.removePrefix("[offset:").removeSuffix("]").trim().toLongOrNull()?.times(1) ?: 0L
                return@forEach
            }
            val m = lineTag.matcher(line)
            val stamps = mutableListOf<Long>()
            var lastEnd = 0
            while (m.find()) {
                stamps.add(toMs(m.group(1), m.group(2), m.group(3)))
                lastEnd = m.end()
            }
            if (stamps.isEmpty()) return@forEach
            val text = line.substring(lastEnd).trim()
            if (text.isEmpty()) return@forEach
            stamps.forEach { timed.add((it + offset) to text) }
        }
        timed.sortBy { it.first }
        val lines = mutableListOf<Triple<Long, Long, String>>()
        val words = mutableListOf<Pair<String, Triple<Long, Long, String>>>()
        val firstStartByText = mutableMapOf<String, Long>()
        timed.forEachIndexed { i, (start, text) ->
            val end = timed.getOrNull(i + 1)?.first ?: (start + estimateDuration(text))
            val delta = (start - (firstStartByText.getOrPut(text) { start })).coerceAtLeast(0)
            val tagList = mutableListOf<Pair<IntRange, Long>>()
            wordTag.matcher(text).let { tm -> while (tm.find()) tagList.add((tm.start() until tm.end()) to toMs(tm.group(1), tm.group(2), tm.group(3))) }
            val cleanLine = wordTag.matcher(text).replaceAll("").trim()
            lines.add(Triple(start, end, cleanLine))
            if (tagList.isNotEmpty()) {
                val firstStart = tagList.first().first.first
                val leading = text.substring(0, firstStart).trim()
                    .trim(',', '.', '!', '?', '"', '\'', ':', ';', '-', '—').lowercase()
                if (leading.isNotBlank()) {
                    val firstTs = tagList.first().second + offset + delta
                    words.add(leading to Triple(start, maxOf(start + 80, firstTs), cleanLine))
                }
                tagList.forEachIndexed { k, (range, ts) ->
                    val wordStart = range.last + 1
                    val wordEnd = tagList.getOrNull(k + 1)?.first?.first ?: text.length
                    val word = text.substring(wordStart, wordEnd).trim()
                        .trim(',', '.', '!', '?', '"', '\'', ':', ';', '-', '—').lowercase()
                    if (word.isNotBlank()) {
                        val next = (tagList.getOrNull(k + 1)?.second ?: end) + offset + delta
                        val ws = ts + offset + delta
                        words.add(word to Triple(ws, maxOf(ws + 80, next), cleanLine))
                    }
                }
            } else {
                estimateWords(cleanLine, start, end).forEach { (w, s, e) -> words.add(w to Triple(s, e, cleanLine)) }
            }
        }
        return LrcDoc(lines, words)
    }

    private fun estimateDuration(text: String) = (text.length * 180L).coerceIn(1200, 6000)

    private fun estimateWords(text: String, start: Long, end: Long): List<Triple<String, Long, Long>> {
        val parts = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return emptyList()
        val totalChars = parts.sumOf { it.length }.coerceAtLeast(1)
        var acc = 0
        val out = mutableListOf<Triple<String, Long, Long>>()
        parts.forEach { raw ->
            val clean = raw.trim(',', '.', '!', '?', '"', '\'', ':', ';', '-', '—').lowercase()
            if (clean.isNotBlank() && clean.all { it.isLetter() || it == '\'' || it == '-' }) {
                val s = start + ((end - start) * acc / totalChars)
                acc += raw.length
                val e = start + ((end - start) * acc / totalChars)
                out.add(Triple(clean, s, e))
            } else acc += raw.length
        }
        return out
    }

    private fun toMs(mm: String, ss: String, frac: String?): Long {
        val base = mm.toLong() * 60_000 + ss.toLong() * 1000
        val f = frac?.padEnd(3, '0')?.take(3)?.toLongOrNull() ?: 0L
        return base + f
    }
}
