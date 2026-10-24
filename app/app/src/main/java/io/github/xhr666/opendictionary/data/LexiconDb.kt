package io.github.xhr666.opendictionary.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File

data class LexEntry(val word: String, val phonetic: String, val translation: String, val definition: String, val pos: String, val tag: String, val exchange: String, val frq: Int)
data class LexPhrase(val phrase: String, val translation: String, val kind: String, val license: String)
data class Sense(val word: String, val pos: String, val meaning: String, val example: String, val source: String)

data class LexLemma(val form: String, val lemma: String, val kind: String)
data class WordData(
    val entry: LexEntry,
    val tags: List<String>,
    val phrases: List<LexPhrase>,
    val lemmas: List<LexLemma>,
    val related: List<Pair<String, String>> = emptyList(),
    val senses: List<Sense> = emptyList(),
    val caseNote: String? = null,
    val alternate: String? = null,
)

class LexiconDb private constructor(private val db: SQLiteDatabase) {
    private fun <T> q(sql: String, args: Array<String> = emptyArray(), block: (Cursor) -> T): T {
        val c = db.rawQuery(sql, args)
        return try { block(c) } finally { c.close() }
    }

    private fun row(c: Cursor): LexEntry = LexEntry(
        word = c.getString(0), phonetic = c.getString(1), translation = c.getString(2).replace("\\n", "\n"),
        definition = c.getString(3).replace("\\n", "\n"), pos = c.getString(4), tag = c.getString(5),
        exchange = c.getString(6), frq = c.getInt(7)
    )

    private fun findExact(word: String): LexEntry? = q("SELECT word, phonetic, translation, definition, pos, tag, exchange, frq FROM entries WHERE word=?", arrayOf(word)) { c ->
        if (c.moveToFirst()) row(c) else null
    }

    private fun findCompact(word: String): LexEntry? {
        val compact = WordForms.compact(word)
        if (compact.isEmpty()) return null
        return q(
            "SELECT word, phonetic, translation, definition, pos, tag, exchange, frq FROM entries WHERE REPLACE(REPLACE(word,'-',''),' ','') = ? COLLATE NOCASE ORDER BY LENGTH(word) LIMIT 1",
            arrayOf(compact)
        ) { c -> if (c.moveToFirst()) row(c) else null }
    }

    private fun findHyphenVariant(word: String): String? {
        val compact = WordForms.compact(word)
        if (compact.isEmpty()) return null
        return q(
            "SELECT word FROM entries WHERE word LIKE '%-%' AND REPLACE(word,'-','') = ? COLLATE NOCASE LIMIT 1",
            arrayOf(compact)
        ) { c -> if (c.moveToFirst()) c.getString(0) else null }
    }

    private fun findAny(word: String): LexEntry? = q(
        "SELECT word, phonetic, translation, definition, pos, tag, exchange, frq FROM entries WHERE word=? COLLATE NOCASE LIMIT 1",
        arrayOf(word)) { c -> if (c.moveToFirst()) row(c) else null }

    fun detail(query: String): WordData? {
        val raw = query.trim()
        if (raw.isEmpty()) return null
        val lower = raw.lowercase()
        val upper = raw.replaceFirstChar { it.uppercase() }
        val exact = findExact(raw)
        var entry = exact
        var note: String? = null
        var alternate: String? = null
        if (entry == null) {
            val theLower = findExact(lower)
            val theUpper = findExact(upper)
            if (raw.first().isUpperCase()) {
                entry = theUpper ?: theLower
                if (theUpper == null && theLower != null) note = "以下为 ${theLower.word} 的结果 · 首字母大写形式无独立词条"
                if (theUpper != null && theLower != null && theLower.word != theUpper.word) alternate = theLower.word
            } else {
                entry = theLower ?: theUpper
                if (theLower != null && theUpper != null && theUpper.word != theLower.word) alternate = theUpper.word
            }
        } else {
            val other = if (raw.first().isUpperCase()) findExact(lower) else findExact(upper)
            if (other != null && other.word != entry?.word) alternate = other.word
        }
        if (entry == null) {
            val compactHit = findCompact(raw) ?: findCompact(lower)
            if (compactHit != null) {
                entry = compactHit
                note = "已匹配词库中的写法：${compactHit.word}（连字符/空格差异）"
            }
        }
        if (entry == null) return null
        if (note == null) {
            val hy = findHyphenVariant(entry.word)
            if (hy != null && hy != entry.word) note = "词库也收录连字符写法：$hy"
        }
        val tags = q("SELECT tag FROM word_tags WHERE word=?", arrayOf(entry.word)) { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }
        val phrases = q(
            "SELECT phrase, translation, kind, license FROM phrases WHERE phrase = ? COLLATE NOCASE OR phrase LIKE ? COLLATE NOCASE ORDER BY LENGTH(phrase) LIMIT 14",
            arrayOf(entry.word, entry.word + " %")) { c ->
            buildList {
                while (c.moveToNext()) add(LexPhrase(c.getString(0), c.getString(1), c.getString(2), c.getString(3)))
            }
        }
        val lemmas = q("SELECT form, lemma, kind FROM lemmas WHERE lemma=? COLLATE NOCASE LIMIT 12", arrayOf(entry.word)) { c ->
            buildList { while (c.moveToNext()) add(LexLemma(c.getString(0), c.getString(1), c.getString(2))) }
        }
        val related = runCatching {
            q("SELECT related_word, relation FROM related WHERE word=? COLLATE NOCASE LIMIT 12", arrayOf(entry.word)) { c ->
                buildList { while (c.moveToNext()) if (!c.getString(0).equals(entry.word, true)) add(c.getString(0) to c.getString(1)) }
            }
        }.getOrDefault(emptyList())
        val senses = q("SELECT word, pos, meaning, example, source FROM sense WHERE word=? COLLATE NOCASE", arrayOf(entry.word)) { c ->
            buildList { while (c.moveToNext()) add(Sense(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4))) }
        }
        return WordData(entry, tags, phrases, lemmas, related, senses, note, alternate)
    }

    fun searchByWord(query: String, limit: Int = 30): List<LexEntry> {
        val qq = query.trim()
        if (qq.isEmpty()) return emptyList()
        val exact = q("SELECT word, phonetic, translation, definition, pos, tag, exchange, frq FROM entries WHERE word=? COLLATE NOCASE LIMIT 1", arrayOf(qq)) { c ->
            if (c.moveToFirst()) listOf(row(c)) else emptyList()
        }
        val compact = q(
            "SELECT word, phonetic, translation, definition, pos, tag, exchange, frq FROM entries WHERE REPLACE(REPLACE(word,'-',''),' ','') = ? COLLATE NOCASE ORDER BY LENGTH(word), word LIMIT ?",
            arrayOf(WordForms.compact(qq), limit.toString())) { c -> buildList { while (c.moveToNext()) add(row(c)) } }
        val prefix = q("SELECT word, phonetic, translation, definition, pos, tag, exchange, frq FROM entries WHERE word LIKE ? COLLATE NOCASE ORDER BY LENGTH(word), word LIMIT ?",
            arrayOf("$qq%", limit.toString())) { c -> buildList { while (c.moveToNext()) add(row(c)) } }
        val contains = q("SELECT word, phonetic, translation, definition, pos, tag, exchange, frq FROM entries WHERE word LIKE ? COLLATE NOCASE AND word NOT LIKE ? COLLATE NOCASE ORDER BY LENGTH(word), word LIMIT ?",
            arrayOf("%$qq%", "$qq%", limit.toString())) { c -> buildList { while (c.moveToNext()) add(row(c)) } }
        // 简单拼写纠错：相邻换位/删一字符的候选
        val variants = mutableListOf<String>()
        if (qq.length >= 4) {
            for (i in 0 until qq.length - 1) {
                variants.add(qq.substring(0, i) + qq[i + 1] + qq[i] + qq.substring(i + 2))
                variants.add(qq.removeRange(i, i + 1))
            }
        }
        val fuzzy = mutableListOf<LexEntry>()
        variants.distinct().take(12).forEach { v ->
            fuzzy.addAll(q("SELECT word, phonetic, translation, definition, pos, tag, exchange, frq FROM entries WHERE word=? COLLATE NOCASE LIMIT 1", arrayOf(v)) { c ->
                buildList { if (c.moveToFirst()) add(row(c)) }
            })
        }
        val seen = hashSetOf<String>()
        return (exact + compact + prefix + contains + fuzzy).filter { seen.add(it.word) }.take(limit)
    }

    fun searchByChinese(query: String, limit: Int = 30): List<LexEntry> {
        val qq = query.trim()
        if (qq.isEmpty()) return emptyList()
        val out = LinkedHashMap<String, LexEntry>()
        // 1) 常用中文词→英文候选（离线语义兜底）
        val aliases = runCatching {
            q("SELECT words FROM zh_alias WHERE zh=?", arrayOf(qq)) { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
        if (aliases != null) {
            aliases.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { w ->
                q("SELECT word, phonetic, translation, definition, pos, tag, exchange, frq FROM entries WHERE word=? COLLATE NOCASE LIMIT 1", arrayOf(w)) { c ->
                    if (c.moveToFirst()) { val e = row(c); out.putIfAbsent(e.word, e) }
                }
            }
        }
        // 2) 释义片段匹配
        q("SELECT word, phonetic, translation, definition, pos, tag, exchange, frq FROM entries WHERE translation LIKE ? ORDER BY (CASE WHEN tag != '' THEN 0 ELSE 1 END), frq LIMIT ?",
            arrayOf("%$qq%", limit.toString())) { c ->
            while (c.moveToNext()) { val e = row(c); out.putIfAbsent(e.word, e) }
        }
        return out.values.take(limit)
    }

    fun gkWords(limit: Int = 30): List<LexEntry> = q(
        "SELECT e.word, e.phonetic, e.translation, e.definition, e.pos, e.tag, e.exchange, e.frq FROM wordbook_entries w JOIN entries e ON e.word=w.word WHERE w.book_id='gk' ORDER BY w.rank LIMIT ?",
        arrayOf(limit.toString())) { c -> buildList { while (c.moveToNext()) add(row(c)) } }

    fun entriesByWords(words: List<String>): List<LexEntry> {
        if (words.isEmpty()) return emptyList()
        val marks = words.joinToString(",") { "?" }
        return q("SELECT word, phonetic, translation, definition, pos, tag, exchange, frq FROM entries WHERE word IN ($marks) ORDER BY word",
            words.toTypedArray()) { c -> buildList { while (c.moveToNext()) add(row(c)) } }
    }

    fun stats(): Triple<Int, Int, Int> {
        val entries = q("SELECT COUNT(*) FROM entries") { if (it.moveToFirst()) it.getInt(0) else 0 }
        val phrases = q("SELECT COUNT(*) FROM phrases") { if (it.moveToFirst()) it.getInt(0) else 0 }
        val gk = q("SELECT COUNT(*) FROM wordbook_entries WHERE book_id='gk'") { if (it.moveToFirst()) it.getInt(0) else 0 }
        return Triple(entries, phrases, gk)
    }

    companion object {
        @Volatile private var inst: LexiconDb? = null
        private const val ASSET = "lexicon.db"

        fun get(context: Context): LexiconDb = inst ?: synchronized(this) {
            inst ?: run {
                val target = File(context.filesDir, ASSET)
                val prefs = context.getSharedPreferences("od_lexicon", Context.MODE_PRIVATE)
                val assetVersion = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
                if (!target.exists() || prefs.getLong("version", -1) != assetVersion) {
                    context.assets.open(ASSET).use { input -> target.outputStream().use { out -> input.copyTo(out) } }
                    prefs.edit().putLong("version", assetVersion).apply()
                }
                val db = SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY)
                LexiconDb(db).also { inst = it }
            }
        }
    }
}
