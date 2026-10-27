package io.github.xhr666.opendictionary.ui

import kotlin.random.Random

data class ExerciseQuestion(val prompt: String, val options: List<String> = emptyList(), val answer: String)
data class ExerciseSet(val type: String, val passage: String, val questions: List<ExerciseQuestion>)

/** 基于 RSS 标题/摘要的离线练习生成（非原题，仅作学习）。 */
object ExerciseGenerator {
    private val stop = setOf("the", "and", "for", "that", "with", "this", "from", "have", "will", "your", "about")

    private fun words(text: String): List<String> = text.split(Regex("[^A-Za-z']+")).filter { it.length in 5..14 && it.lowercase() !in stop }.distinct()

    private fun pickWords(text: String, n: Int): List<String> = words(text).shuffled(Random(42)).take(n)

    fun build(text: String, type: String): ExerciseSet {
        val clean = text.replace("\n", " ").replace(Regex("\\s+"), " ").trim()
        if (clean.length < 120) return ExerciseSet(type, clean, emptyList())
        val picks = pickWords(clean, 3)
        return when (type) {
            "choice" -> choice(clean, picks)
            "seven" -> seven(clean)
            "grammar" -> grammar(clean)
            else -> cloze(clean, picks)
        }
    }

    private fun cloze(text: String, picks: List<String>): ExerciseSet {
        var out = text
        val qs = mutableListOf<ExerciseQuestion>()
        picks.forEachIndexed { i, w ->
            val regex = Regex("\\b" + Regex.escape(w) + "\\b")
            if (regex.containsMatchIn(out)) { out = regex.replaceFirst(out, "____(${i + 1})"); qs.add(ExerciseQuestion("第 ${i + 1} 空", emptyList(), w)) }
        }
        return ExerciseSet("cloze", out, qs)
    }

    private fun choice(text: String, picks: List<String>): ExerciseSet {
        val pool = words(text)
        var out = text
        val qs = mutableListOf<ExerciseQuestion>()
        picks.forEachIndexed { i, correct ->
            val regex = Regex("\\b" + Regex.escape(correct) + "\\b")
            if (!regex.containsMatchIn(out)) return@forEachIndexed
            out = regex.replaceFirst(out, "(${i + 1})____")
            val distractors = pool.filter { it != correct }.shuffled(Random(i + 7)).take(2)
            val opts = (distractors + correct).shuffled(Random(i + 13))
            qs.add(ExerciseQuestion("第 ${i + 1} 空选择", opts, opts[opts.indexOf(correct)]))
        }
        return ExerciseSet("choice", out, qs)
    }

    private fun seven(text: String): ExerciseSet {
        val sentences = text.split(Regex("(?<=[.!?])\\s+" )).filter { it.length in 30..220 }
        if (sentences.size < 4) return cloze(text, pickWords(text, 3))
        val remove = sentences.shuffled(Random(5)).take(2)
        var out = text
        val distractors = sentences.filter { it !in remove }.shuffled(Random(11)).take(2).map { it.take(120) }
        val options = (remove.map { it.take(120) } + distractors).shuffled(Random(9))
        val qs = mutableListOf<ExerciseQuestion>()
        remove.forEachIndexed { i, s ->
            out = out.replace(s, "[${'A' + i}]")
            qs.add(ExerciseQuestion("第 ${i + 1} 处（[${'A' + i}]）应填入", options, s.take(120)))
        }
        return ExerciseSet("seven", out, qs)
    }

    private fun grammar(text: String): ExerciseSet {
        val fn = listOf("the", "a", "an", "to", "of", "in", "on", "at", "is", "are", "was", "were", "has", "have", "by", "for")
        var out = text
        val qs = mutableListOf<ExerciseQuestion>()
        var n = 0
        fn.shuffled(Random(21)).take(4).forEach { w ->
            val regex = Regex("\\b" + Regex.escape(w) + "\\b")
            if (regex.containsMatchIn(out)) {
                n++
                out = regex.replaceFirst(out, "[$n]____")
                qs.add(ExerciseQuestion("第 $n 空（语法填空）", emptyList(), w))
            }
        }
        return ExerciseSet("grammar", out, qs)
    }
}
