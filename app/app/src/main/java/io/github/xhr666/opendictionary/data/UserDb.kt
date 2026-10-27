package io.github.xhr666.opendictionary.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

data class DayStat(val day: String, val queries: Int, val words: Int, val minutes: Int)
data class DayDetail(val day: String, val queries: List<String>, val stat: DayStat?)
data class ProfileStats(val totalQueries: Int, val learnedWords: Int, val noteCount: Int, val streak: Int, val activeDays: Int)
data class RecentQuery(val word: String, val ts: Long)

data class WordBook(val id: Long, val name: String, val description: String, val readme: String, val isDefault: Boolean, val count: Int)

data class FoldItem(
    val word: String,
    val meaning: String,
    val e2cStreak: Int,
    val c2eStreak: Int,
    val weakFlag: Boolean,
    val nextReviewAt: Long,
    val intervalDays: Double,
)

data class MediaTrack(
    val id: Long, val uri: String, val name: String, val title: String,
    val artist: String, val duration: Long, val hasLrc: Boolean, val addedAt: Long,
)
data class MediaClip(
    val mediaId: Long, val uri: String, val title: String, val word: String,
    val startMs: Long, val endMs: Long, val lineText: String,
)

class UserDb private constructor(context: Context) : SQLiteOpenHelper(context, "user.db", null, 13) {
    data class DocumentRow(val id: Long, val uri: String, val title: String, val addedAt: Long)
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS activity(day TEXT PRIMARY KEY, queries INTEGER NOT NULL DEFAULT 0, words INTEGER NOT NULL DEFAULT 0, minutes INTEGER NOT NULL DEFAULT 0, demo INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE IF NOT EXISTS query_log(id INTEGER PRIMARY KEY AUTOINCREMENT, word TEXT NOT NULL, ts INTEGER NOT NULL, source TEXT NOT NULL DEFAULT 'search')")
        db.execSQL("CREATE TABLE IF NOT EXISTS wordbook(word TEXT PRIMARY KEY, added_at INTEGER NOT NULL, learned INTEGER NOT NULL DEFAULT 0, note TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE IF NOT EXISTS notes(id INTEGER PRIMARY KEY AUTOINCREMENT, word TEXT NOT NULL, content TEXT NOT NULL, updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY, value TEXT)")
        onCreateMedia(db)
        onCreateBooks(db)
        onCreateDocs(db)
        onCreateReviews(db)
        onCreateAi(db)
        onCreateFold(db)
        onCreateExternal(db)
        onCreateWordAudio(db)
        onCreateCustomEntries(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onCreateExternal(db)
        onCreateWordAudio(db)
        onCreateCustomEntries(db)
        onCreateFold(db)
        onCreateMedia(db)
        onCreateBooks(db)
        migrateBooks(db)
        onCreateDocs(db)
        onCreateReviews(db)
        onCreateAi(db)
        runCatching { db.execSQL("ALTER TABLE wordbooks ADD COLUMN readme TEXT NOT NULL DEFAULT ''") }
    }

    private fun onCreateBooks(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS wordbooks(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, description TEXT NOT NULL DEFAULT '', readme TEXT NOT NULL DEFAULT '', is_default INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE IF NOT EXISTS wordbook_entries(book_id INTEGER, word TEXT, added_at INTEGER, learned INTEGER NOT NULL DEFAULT 0, UNIQUE(book_id, word))")
    }

    private fun migrateBooks(db: SQLiteDatabase) {
        val c = db.rawQuery("SELECT id FROM wordbooks WHERE is_default=1 LIMIT 1", null)
        val has = c.moveToFirst(); c.close()
        if (!has) db.execSQL("INSERT INTO wordbooks(name, description, is_default, created_at) VALUES('我的生词本','默认单词本',1,?)", arrayOf(System.currentTimeMillis()))
        // 把旧 wordbook 表迁移到默认本（幂等）
        runCatching {
            db.execSQL("INSERT OR IGNORE INTO wordbook_entries(book_id, word, added_at, learned) SELECT (SELECT id FROM wordbooks WHERE is_default=1 LIMIT 1), word, added_at, learned FROM wordbook")
        }
    }

    private fun onCreateWordAudio(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS word_audio(word TEXT NOT NULL, uri TEXT NOT NULL, label TEXT NOT NULL DEFAULT '', added_at INTEGER NOT NULL, PRIMARY KEY(word, uri))")
    }

    data class WordAudio(val word: String, val uri: String, val label: String, val addedAt: Long)

    fun wordAudio(word: String): List<WordAudio> = query("SELECT word, uri, label, added_at FROM word_audio WHERE word=? ORDER BY added_at DESC", arrayOf(word)) { c ->
        buildList { while (c.moveToNext()) add(WordAudio(c.getString(0), c.getString(1), c.getString(2) ?: "", c.getLong(3))) }
    }

    fun addWordAudio(word: String, uri: String, label: String = "本地音频") =
        exec("INSERT OR REPLACE INTO word_audio(word, uri, label, added_at) VALUES(?,?,?,?)", arrayOf<Any?>(word, uri, label, System.currentTimeMillis()))

    fun removeWordAudio(word: String, uri: String) = exec("DELETE FROM word_audio WHERE word=? AND uri=?", arrayOf(word, uri))

    private fun onCreateCustomEntries(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS custom_entry(word TEXT PRIMARY KEY, phonetic TEXT NOT NULL DEFAULT '', translation TEXT NOT NULL DEFAULT '', definition TEXT NOT NULL DEFAULT '', pos TEXT NOT NULL DEFAULT '', tag TEXT NOT NULL DEFAULT 'custom', exchange TEXT NOT NULL DEFAULT '', source TEXT NOT NULL DEFAULT '', updated_at INTEGER NOT NULL)")
    }

    fun saveCustomEntry(e: LexEntry, source: String) {
        exec("INSERT OR REPLACE INTO custom_entry(word, phonetic, translation, definition, pos, tag, exchange, source, updated_at) VALUES(?,?,?,?,?,?,?,?,?)",
            arrayOf<Any?>(e.word, e.phonetic, e.translation, e.definition, e.pos, e.tag, e.exchange, source, System.currentTimeMillis()))
    }

    fun customEntry(word: String): LexEntry? = query("SELECT word, phonetic, translation, definition, pos, tag, exchange, 0 AS frq FROM custom_entry WHERE word=? COLLATE NOCASE LIMIT 1", arrayOf(word)) { c ->
        if (c.moveToFirst()) LexEntry(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5), c.getString(6), 9999) else null
    }

    fun searchCustom(query: String, limit: Int = 10): List<LexEntry> = query(
        "SELECT word, phonetic, translation, definition, pos, tag, exchange, 0 AS frq FROM custom_entry WHERE word LIKE ? OR translation LIKE ? ORDER BY LENGTH(word) LIMIT ?",
        arrayOf("$query%", "%$query%", limit.toString())
    ) { c -> buildList { while (c.moveToNext()) add(LexEntry(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5), c.getString(6), 9999)) } }

    fun customEntryCount(): Int = query("SELECT COUNT(*) FROM custom_entry") { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    fun allCustomEntries(): List<LexEntry> = query("SELECT word, phonetic, translation, definition, pos, tag, exchange FROM custom_entry ORDER BY word") { c ->
        buildList { while (c.moveToNext()) add(LexEntry(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5), c.getString(6), 9999)) }
    }

    fun deleteCustomEntry(word: String) = exec("DELETE FROM custom_entry WHERE word=?", arrayOf(word))

    fun onCreateExternal(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS external_content(word TEXT PRIMARY KEY, source TEXT NOT NULL, title TEXT, content TEXT NOT NULL, url TEXT, fetched_at INTEGER NOT NULL)")
    }

    data class ExternalContent(val word: String, val source: String, val title: String, val content: String, val url: String, val fetchedAt: Long)

    fun externalContent(word: String): ExternalContent? = query("SELECT word, source, title, content, url, fetched_at FROM external_content WHERE word=?", arrayOf(word)) { c ->
        if (c.moveToFirst()) ExternalContent(c.getString(0), c.getString(1), c.getString(2) ?: "", c.getString(3), c.getString(4) ?: "", c.getLong(5)) else null
    }

    fun saveExternalContent(content: ExternalContent) {
        exec("INSERT OR REPLACE INTO external_content(word, source, title, content, url, fetched_at) VALUES(?,?,?,?,?,?)",
            arrayOf<Any?>(content.word, content.source, content.title, content.content, content.url, content.fetchedAt))
    }

    fun allExternalContent(): List<ExternalContent> = query("SELECT word, source, title, content, url, fetched_at FROM external_content ORDER BY fetched_at DESC") { c ->
        buildList { while (c.moveToNext()) add(ExternalContent(c.getString(0), c.getString(1), c.getString(2) ?: "", c.getString(3), c.getString(4) ?: "", c.getLong(5))) }
    }

    private fun onCreateFold(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS fold_item(word TEXT PRIMARY KEY, meaning TEXT, e2c_streak INTEGER NOT NULL DEFAULT 0, c2e_streak INTEGER NOT NULL DEFAULT 0, weak_flag INTEGER NOT NULL DEFAULT 0, next_review_at INTEGER NOT NULL DEFAULT 0, interval_days REAL NOT NULL DEFAULT 0, last_result INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE IF NOT EXISTS fold_result(word TEXT, direction TEXT, correct INTEGER, ts INTEGER)")
    }

    private fun onCreateAi(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS ai_sense(word TEXT PRIMARY KEY, content TEXT NOT NULL, created_at INTEGER NOT NULL)")
    }

    fun aiSense(word: String): String? = query("SELECT content FROM ai_sense WHERE word=?", arrayOf(word)) { if (it.moveToFirst()) it.getString(0) else null }

    fun saveAiSense(word: String, content: String) = exec("INSERT OR REPLACE INTO ai_sense(word, content, created_at) VALUES(?,?,?)", arrayOf(word, content, System.currentTimeMillis()))

    private fun onCreateReviews(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS word_review(word TEXT PRIMARY KEY, ease REAL NOT NULL DEFAULT 2.5, interval_days REAL NOT NULL DEFAULT 0, due_at INTEGER NOT NULL DEFAULT 0, reps INTEGER NOT NULL DEFAULT 0, lapses INTEGER NOT NULL DEFAULT 0)")
    }

    private fun onCreateDocs(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS documents(id INTEGER PRIMARY KEY AUTOINCREMENT, uri TEXT UNIQUE, title TEXT, added_at INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS doc_marks(doc_id INTEGER, line INTEGER, token TEXT, type TEXT, PRIMARY KEY(doc_id,line,token))")
    }

    private fun onCreateMedia(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS media(id INTEGER PRIMARY KEY AUTOINCREMENT, uri TEXT UNIQUE, name TEXT, title TEXT, artist TEXT, duration INTEGER, has_lrc INTEGER, added_at INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS media_lyrics(media_id INTEGER, line_index INTEGER, start_ms INTEGER, end_ms INTEGER, text TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS media_words(media_id INTEGER, word TEXT, line_index INTEGER, start_ms INTEGER, end_ms INTEGER, line_text TEXT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_media_words ON media_words(word)")
    }

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun exec(sql: String, args: Array<Any?> = emptyArray()) {
        writableDatabase.execSQL(sql, args)
    }

    private fun <T> query(sql: String, args: Array<String> = emptyArray(), block: (Cursor) -> T): T {
        val c = readableDatabase.rawQuery(sql, args)
        return try { block(c) } finally { c.close() }
    }

    fun recordQuery(word: String, source: String = "search") {
        val w = word.trim()
        if (w.isEmpty()) return
        val day = today()
        exec("INSERT INTO activity(day, queries) VALUES(?, 1) ON CONFLICT(day) DO UPDATE SET queries = queries + 1", arrayOf(day))
        exec("INSERT INTO query_log(word, ts, source) VALUES(?,?,?)", arrayOf(w, System.currentTimeMillis(), source))
    }

    fun wordbooks(): List<WordBook> {
        migrateBooks(writableDatabase)
        return query("SELECT b.id, b.name, b.description, b.readme, b.is_default, (SELECT COUNT(*) FROM wordbook_entries e WHERE e.book_id=b.id) FROM wordbooks b ORDER BY b.is_default DESC, b.created_at") { c ->
            buildList { while (c.moveToNext()) add(WordBook(c.getLong(0), c.getString(1), c.getString(2), c.getString(3) ?: "", c.getInt(4) == 1, c.getInt(5))) }
        }
    }

    fun createWordBook(name: String, description: String = "", readme: String = ""): Long {
        exec("INSERT INTO wordbooks(name, description, readme, created_at) VALUES(?,?,?,?)", arrayOf(name.trim().ifBlank { "新单词本" }, description, readme, System.currentTimeMillis()))
        return query("SELECT id FROM wordbooks ORDER BY id DESC LIMIT 1") { if (it.moveToFirst()) it.getLong(0) else -1L }
    }

    fun updateWordBookReadme(id: Long, readme: String) = exec("UPDATE wordbooks SET readme=? WHERE id=?", arrayOf(readme, id))

    fun renameWordBook(id: Long, name: String) = exec("UPDATE wordbooks SET name=? WHERE id=? AND is_default=0", arrayOf(name.trim().ifBlank { "未命名词书" }, id))

    fun deleteWordBook(id: Long) {
        exec("DELETE FROM wordbooks WHERE id=? AND is_default=0", arrayOf(id))
        exec("DELETE FROM wordbook_entries WHERE book_id=? AND book_id NOT IN (SELECT id FROM wordbooks WHERE is_default=1)", arrayOf(id))
    }

    fun addWordToBook(bookId: Long, word: String) {
        val inserted = writableDatabase.insertWithOnConflict("wordbook_entries", null,
            android.content.ContentValues().apply { put("book_id", bookId); put("word", word.trim()); put("added_at", System.currentTimeMillis()) },
            android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE)
        if (inserted != -1L) exec("INSERT INTO activity(day, words) VALUES(?, 1) ON CONFLICT(day) DO UPDATE SET words = words + 1", arrayOf(today()))
    }

    /** 批量导入（资源包/文件导入）：事务写入，不计入“今日学习词”，避免统计失真。 */
    fun importWordsToBook(bookId: Long, words: List<String>): Int {
        val db = writableDatabase
        var added = 0
        db.beginTransaction()
        try {
            words.map { it.trim() }.filter { it.isNotBlank() }.distinct().forEach { w ->
                val id = db.insertWithOnConflict("wordbook_entries", null,
                    android.content.ContentValues().apply { put("book_id", bookId); put("word", w); put("added_at", System.currentTimeMillis()) },
                    android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE)
                if (id != -1L) added++
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return added
    }

    fun removeWordFromBook(bookId: Long, word: String) = exec("DELETE FROM wordbook_entries WHERE book_id=? AND word=?", arrayOf(bookId, word))

    fun learnedInBook(bookId: Long): Int = query("SELECT COUNT(*) FROM wordbook_entries WHERE book_id=? AND learned=1", arrayOf(bookId.toString())) { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun wordsInBook(bookId: Long): List<String> = query("SELECT word FROM wordbook_entries WHERE book_id=? ORDER BY added_at DESC", arrayOf(bookId.toString())) { c ->
        buildList { while (c.moveToNext()) add(c.getString(0)) }
    }

    fun addToWordbook(word: String) {
        val id = wordbooks().firstOrNull { it.isDefault }?.id ?: createWordBook("我的生词本")
        addWordToBook(id, word)
    }

    fun removeFromWordbook(word: String) {
        val id = wordbooks().firstOrNull { it.isDefault }?.id ?: return
        removeWordFromBook(id, word)
    }

    fun inWordbook(word: String): Boolean = query("SELECT 1 FROM wordbook_entries WHERE word=? LIMIT 1", arrayOf(word)) { it.moveToFirst() }

    fun wordbookWords(): List<String> {
        val id = wordbooks().firstOrNull { it.isDefault }?.id
        return if (id != null) wordsInBook(id) else emptyList()
    }

    fun recordReview(word: String, level: Int) {
        addToWordbook(word)
        if (level >= 2) exec("UPDATE wordbook_entries SET learned=1 WHERE word=?", arrayOf(word))
        scheduleReview(word, level)
        exec("INSERT INTO activity(day, minutes, queries) VALUES(?, 1, 1) ON CONFLICT(day) DO UPDATE SET minutes = minutes + 1, queries = queries + 1", arrayOf(today()))
    }

    /** 间隔重复（SM-2 简化版）：level 0=忘记，1=模糊，2=记住。 */
    fun scheduleReview(word: String, level: Int) {
        val row = query("SELECT ease, interval_days, reps, lapses FROM word_review WHERE word=?", arrayOf(word)) { c ->
            if (c.moveToFirst()) doubleArrayOf(c.getDouble(0), c.getDouble(1), c.getInt(2).toDouble(), c.getInt(3).toDouble()) else null
        } ?: doubleArrayOf(2.5, 0.0, 0.0, 0.0)
        var ease = row[0]; var interval = row[1]; var reps = row[2].toInt(); var lapses = row[3].toInt()
        when (level) {
            0 -> { lapses++; reps = 0; interval = 0.5; ease = (ease - 0.2).coerceAtLeast(1.3) }
            1 -> { reps++; interval = if (interval <= 0) 1.0 else (interval * 1.2).coerceAtLeast(1.0); ease = (ease - 0.05).coerceAtLeast(1.3) }
            else -> { reps++; interval = if (interval <= 0) 1.0 else (interval * ease).coerceAtMost(365.0); ease = (ease + 0.1).coerceAtMost(2.8) }
        }
        val due = System.currentTimeMillis() + (interval * 24 * 60 * 60 * 1000).toLong()
        exec("INSERT OR REPLACE INTO word_review(word, ease, interval_days, due_at, reps, lapses) VALUES(?,?,?,?,?,?)",
            arrayOf<Any?>(word, ease, interval, due, reps, lapses))
    }

    fun dueReviewWords(words: List<String>): List<String> {
        if (words.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val dueSet = query("SELECT word FROM word_review WHERE due_at<=?", arrayOf(now.toString())) { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0)) }
        }
        val due = words.filter { it in dueSet }
        return due + words.filter { it !in dueSet }
    }

    fun reviewDueCount(): Int = query("SELECT COUNT(*) FROM word_review WHERE due_at<=?", arrayOf(System.currentTimeMillis().toString())) { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun saveNote(word: String, content: String) =
        exec("INSERT INTO notes(word, content, updated_at) VALUES(?,?,?)", arrayOf(word, content, System.currentTimeMillis()))

    fun notesFor(word: String): List<String> = query("SELECT content FROM notes WHERE word=? ORDER BY updated_at DESC", arrayOf(word)) { c ->
        buildList { while (c.moveToNext()) add(c.getString(0)) }
    }

    fun foldItems(words: List<Pair<String, String>>, limit: Int = 30): List<FoldItem> {
        words.forEach { (w, m) ->
            exec("INSERT OR IGNORE INTO fold_item(word, meaning) VALUES(?,?)", arrayOf(w, m))
            exec("UPDATE fold_item SET meaning=? WHERE word=? AND (meaning IS NULL OR meaning='')", arrayOf(m, w))
        }
        if (words.isEmpty()) return emptyList()
        val marks = words.joinToString(",") { "?" }
        return query("SELECT word, meaning, e2c_streak, c2e_streak, weak_flag, next_review_at, interval_days FROM fold_item WHERE word IN ($marks) ORDER BY weak_flag DESC, next_review_at ASC LIMIT ?",
            words.map { it.first }.toMutableList().also { it.add(limit.toString()) }.toTypedArray()) { c ->
            buildList {
                while (c.moveToNext()) add(FoldItem(c.getString(0), c.getString(1) ?: "", c.getInt(2), c.getInt(3), c.getInt(4) == 1, c.getLong(5), c.getDouble(6)))
            }
        }
    }

    fun foldResult(word: String, direction: String, correct: Boolean) {
        exec("INSERT OR IGNORE INTO fold_item(word, meaning) VALUES(?, '')", arrayOf(word))
        val col = if (direction == "e2c") "e2c_streak" else "c2e_streak"
        if (correct) exec("UPDATE fold_item SET $col = $col + 1, weak_flag = 0 WHERE word=?", arrayOf(word))
        else exec("UPDATE fold_item SET $col = 0, weak_flag = 1 WHERE word=?", arrayOf(word))
        val row = query("SELECT e2c_streak, c2e_streak, weak_flag, interval_days FROM fold_item WHERE word=?", arrayOf(word)) { c ->
            if (c.moveToFirst()) intArrayOf(c.getInt(0), c.getInt(1), c.getInt(2), c.getDouble(3).toInt()) else null
        } ?: return
        val bothOk = row[0] > 0 && row[1] > 0 && row[2] == 0
        val prev = row[3].toDouble()
        val interval: Double = when {
            bothOk -> (prev.coerceAtLeast(1.0) * 2.2).coerceAtMost(180.0)
            correct -> prev.coerceAtLeast(0.5)
            else -> 0.5
        }
        val next = System.currentTimeMillis() + (interval * 24 * 60 * 60 * 1000).toLong()
        exec("UPDATE fold_item SET interval_days=?, next_review_at=? WHERE word=?", arrayOf<Any?>(interval, next, word))
        exec("INSERT INTO fold_result(word, direction, correct, ts) VALUES(?,?,?,?)", arrayOf(word, direction, if (correct) 1 else 0, System.currentTimeMillis()))
    }

    fun resetFold(weakOnly: Boolean = false) {
        if (weakOnly) exec("UPDATE fold_item SET weak_flag=0") else exec("DELETE FROM fold_item")
    }

    fun addMedia(uri: String, name: String, title: String, artist: String, duration: Long, hasLrc: Boolean): Long {
        exec("INSERT OR REPLACE INTO media(uri, name, title, artist, duration, has_lrc, added_at) VALUES(?,?,?,?,?,?,?)",
            arrayOf<Any?>(uri, name, title, artist, duration, if (hasLrc) 1 else 0, System.currentTimeMillis()))
        return query("SELECT id FROM media WHERE uri=?", arrayOf(uri)) { if (it.moveToFirst()) it.getLong(0) else -1L }
    }

    fun setMediaLyrics(mediaId: Long, lines: List<Triple<Long, Long, String>>, segments: List<Pair<String, Triple<Long, Long, String>>>) {
        exec("DELETE FROM media_lyrics WHERE media_id=?", arrayOf(mediaId))
        exec("DELETE FROM media_words WHERE media_id=?", arrayOf(mediaId))
        lines.forEachIndexed { i, (start, end, text) ->
            exec("INSERT INTO media_lyrics(media_id, line_index, start_ms, end_ms, text) VALUES(?,?,?,?,?)",
                arrayOf<Any?>(mediaId, i, start, end, text))
        }
        segments.forEach { (word, info) ->
            val (start, end, line) = info
            exec("INSERT INTO media_words(media_id, word, line_index, start_ms, end_ms, line_text) VALUES(?,?,?,?,?,?)",
                arrayOf<Any?>(mediaId, word.lowercase(), 0, start, end, line))
        }
    }

    fun mediaTracks(): List<MediaTrack> = query("SELECT id, uri, name, title, artist, duration, has_lrc, added_at FROM media ORDER BY added_at DESC") { c ->
        buildList {
            while (c.moveToNext()) add(MediaTrack(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getLong(5), c.getInt(6) == 1, c.getLong(7)))
        }
    }

    fun deleteMedia(id: Long) {
        exec("DELETE FROM media WHERE id=?", arrayOf(id))
        exec("DELETE FROM media_lyrics WHERE media_id=?", arrayOf(id))
        exec("DELETE FROM media_words WHERE media_id=?", arrayOf(id))
    }

    fun findMediaClips(word: String, limit: Int = 6): List<MediaClip> = query(
        "SELECT m.id, m.uri, m.title, w.word, w.start_ms, w.end_ms, w.line_text FROM media_words w JOIN media m ON m.id=w.media_id WHERE w.word=? ORDER BY m.added_at DESC LIMIT ?",
        arrayOf(word.lowercase(), limit.toString())) { c ->
        buildList { while (c.moveToNext()) add(MediaClip(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4), c.getLong(5), c.getString(6))) }
    }

    fun lyricsFor(mediaId: Long): List<Triple<Long, Long, String>> = query(
        "SELECT start_ms, end_ms, text FROM media_lyrics WHERE media_id=? ORDER BY line_index", arrayOf(mediaId.toString())) { c ->
        buildList { while (c.moveToNext()) add(Triple(c.getLong(0), c.getLong(1), c.getString(2) ?: "")) }
    }

    fun wordsFor(mediaId: Long): List<Pair<String, Triple<Long, Long, String>>> = query(
        "SELECT word, start_ms, end_ms, line_text FROM media_words WHERE media_id=? ORDER BY start_ms", arrayOf(mediaId.toString())) { c ->
        buildList { while (c.moveToNext()) add(c.getString(0) to Triple(c.getLong(1), c.getLong(2), c.getString(3) ?: "")) }
    }

    fun mediaById(id: Long): MediaTrack? = query("SELECT id, uri, name, title, artist, duration, has_lrc, added_at FROM media WHERE id=?", arrayOf(id.toString())) { c ->
        if (c.moveToFirst()) MediaTrack(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getLong(5), c.getInt(6) == 1, c.getLong(7)) else null
    }

    fun exportBackupJson(): String {
        val root = org.json.JSONObject()
        root.put("schema", 1).put("exportedAt", System.currentTimeMillis())
        val activity = org.json.JSONArray()
        query("SELECT day, queries, words, minutes FROM activity") { c ->
            while (c.moveToNext()) activity.put(org.json.JSONObject().put("day", c.getString(0)).put("queries", c.getInt(1)).put("words", c.getInt(2)).put("minutes", c.getInt(3)))
        }
        val history = org.json.JSONArray()
        query("SELECT word, ts, source FROM query_log ORDER BY ts DESC LIMIT 5000") { c ->
            while (c.moveToNext()) history.put(org.json.JSONObject().put("word", c.getString(0)).put("ts", c.getLong(1)).put("source", c.getString(2)))
        }
        val notes = org.json.JSONArray()
        query("SELECT word, content, updated_at FROM notes") { c ->
            while (c.moveToNext()) notes.put(org.json.JSONObject().put("word", c.getString(0)).put("content", c.getString(1)).put("updatedAt", c.getLong(2)))
        }
        val books = org.json.JSONArray()
        wordbooks().forEach { b ->
            val arr = org.json.JSONArray()
            wordsInBook(b.id).forEach { arr.put(it) }
            val learned = org.json.JSONArray()
            query("SELECT word FROM wordbook_entries WHERE book_id=? AND learned=1", arrayOf(b.id.toString())) { c -> while (c.moveToNext()) learned.put(c.getString(0)) }
            books.put(org.json.JSONObject().put("name", b.name).put("description", b.description).put("readme", b.readme).put("isDefault", b.isDefault).put("words", arr).put("learned", learned))
        }
        val external = org.json.JSONArray()
        allExternalContent().forEach { x ->
            external.put(org.json.JSONObject().put("word", x.word).put("source", x.source).put("title", x.title).put("content", x.content).put("url", x.url).put("fetchedAt", x.fetchedAt))
        }
        val wordAudio = org.json.JSONArray()
        query("SELECT word, uri, label, added_at FROM word_audio") { c ->
            while (c.moveToNext()) wordAudio.put(org.json.JSONObject().put("word", c.getString(0)).put("uri", c.getString(1)).put("label", c.getString(2)).put("addedAt", c.getLong(3)))
        }
        val customEntries = org.json.JSONArray()
        query("SELECT word, phonetic, translation, definition, pos, tag, exchange, source FROM custom_entry") { c ->
            while (c.moveToNext()) customEntries.put(org.json.JSONObject().put("word", c.getString(0)).put("phonetic", c.getString(1)).put("translation", c.getString(2)).put("definition", c.getString(3)).put("pos", c.getString(4)).put("tag", c.getString(5)).put("exchange", c.getString(6)).put("source", c.getString(7)))
        }
        root.put("activity", activity).put("history", history).put("notes", notes).put("wordbooks", books).put("external", external).put("wordAudio", wordAudio).put("customEntries", customEntries)
        return root.toString()
    }

    fun importBackupJson(text: String): Int {
        val root = org.json.JSONObject(text)
        if (root.optInt("schema", 0) != 1) error("备份版本不支持")
        var count = 0
        writableDatabase.beginTransaction()
        try {
            val activity = root.optJSONArray("activity") ?: org.json.JSONArray()
            for (i in 0 until activity.length()) {
                val o = activity.getJSONObject(i)
                exec("INSERT INTO activity(day, queries, words, minutes) VALUES(?,?,?,?) ON CONFLICT(day) DO UPDATE SET queries=MAX(queries,excluded.queries), words=MAX(words,excluded.words), minutes=MAX(minutes,excluded.minutes)",
                    arrayOf<Any?>(o.optString("day"), o.optInt("queries"), o.optInt("words"), o.optInt("minutes")))
                count++
            }
            val books = root.optJSONArray("wordbooks") ?: org.json.JSONArray()
            var defaultId = wordbooks().firstOrNull { it.isDefault }?.id
            if (defaultId == null) defaultId = createWordBook("我的生词本")
            for (i in 0 until books.length()) {
                val b = books.getJSONObject(i)
                val name = b.optString("name").ifBlank { "导入词书" }
                val id = if (b.optBoolean("isDefault")) defaultId else
                    (wordbooks().firstOrNull { !it.isDefault && it.name == name }?.id ?: createWordBook(name, b.optString("description"), b.optString("readme")))
                if (b.optString("readme").isNotBlank()) updateWordBookReadme(id, b.optString("readme"))
                val words = b.optJSONArray("words") ?: org.json.JSONArray()
                val wordList = buildList { for (j in 0 until words.length()) add(words.getString(j)) }
                importWordsToBook(id, wordList)
                val learned = b.optJSONArray("learned") ?: org.json.JSONArray()
                for (j in 0 until learned.length()) exec("UPDATE wordbook_entries SET learned=1 WHERE book_id=? AND word=?", arrayOf(id.toString(), learned.getString(j)))
            }
            val customEntries = root.optJSONArray("customEntries") ?: org.json.JSONArray()
            for (i in 0 until customEntries.length()) {
                val o = customEntries.getJSONObject(i)
                val w = o.optString("word").trim()
                if (w.isBlank()) continue
                saveCustomEntry(
                    LexEntry(w, o.optString("phonetic"), o.optString("translation"), o.optString("definition"), o.optString("pos"), o.optString("tag", "custom"), o.optString("exchange"), 9999),
                    o.optString("source", "导入")
                )
            }
            val wordAudio = root.optJSONArray("wordAudio") ?: org.json.JSONArray()
            for (i in 0 until wordAudio.length()) {
                val o = wordAudio.getJSONObject(i)
                val w = o.optString("word"); val u = o.optString("uri")
                if (w.isNotBlank() && u.isNotBlank()) addWordAudio(w, u, o.optString("label", "本地音频"))
            }
            val external = root.optJSONArray("external") ?: org.json.JSONArray()
            for (i in 0 until external.length()) {
                val o = external.getJSONObject(i)
                val w = o.optString("word").trim()
                if (w.isBlank()) continue
                val ts = o.optLong("fetchedAt", System.currentTimeMillis())
                val old = externalContent(w)
                if (old == null || ts >= old.fetchedAt) saveExternalContent(ExternalContent(w, o.optString("source", "导入"), o.optString("title"), o.optString("content"), o.optString("url"), ts))
            }
            val history = root.optJSONArray("history") ?: org.json.JSONArray()
            for (i in 0 until history.length()) {
                val o = history.getJSONObject(i)
                val word = o.optString("word"); val ts = o.optLong("ts", System.currentTimeMillis())
                exec("INSERT INTO query_log(word, ts, source) SELECT ?,?,? WHERE NOT EXISTS(SELECT 1 FROM query_log WHERE word=? AND ts=?)",
                    arrayOf<Any?>(word, ts, o.optString("source", "import"), word, ts))
            }
            val notes = root.optJSONArray("notes") ?: org.json.JSONArray()
            for (i in 0 until notes.length()) {
                val o = notes.getJSONObject(i)
                val word = o.optString("word"); val content = o.optString("content")
                exec("INSERT INTO notes(word, content, updated_at) SELECT ?,?,? WHERE NOT EXISTS(SELECT 1 FROM notes WHERE word=? AND content=?)",
                    arrayOf<Any?>(word, content, o.optLong("updatedAt", System.currentTimeMillis()), word, content))
            }
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
        return count
    }

    data class NoteRow(val id: Long, val word: String, val content: String, val updatedAt: Long)

    fun allNotes(): List<NoteRow> = query("SELECT id, word, content, updated_at FROM notes ORDER BY updated_at DESC LIMIT 500") { c ->
        buildList { while (c.moveToNext()) add(NoteRow(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3))) }
    }

    fun deleteNote(id: Long) = exec("DELETE FROM notes WHERE id=?", arrayOf(id))

    fun documents(): List<DocumentRow> = query("SELECT id, uri, title, added_at FROM documents ORDER BY added_at DESC") { c ->
        buildList { while (c.moveToNext()) add(DocumentRow(c.getLong(0), c.getString(1), c.getString(2) ?: "", c.getLong(3))) }
    }

    fun addDocument(uri: String, title: String): Long {
        val existing = query("SELECT id FROM documents WHERE uri=?", arrayOf(uri)) { if (it.moveToFirst()) it.getLong(0) else -1L }
        if (existing > 0) {
            exec("UPDATE documents SET title=?, added_at=? WHERE id=?", arrayOf(title, System.currentTimeMillis(), existing))
            return existing
        }
        exec("INSERT INTO documents(uri, title, added_at) VALUES(?,?,?)", arrayOf(uri, title, System.currentTimeMillis()))
        return query("SELECT id FROM documents WHERE uri=?", arrayOf(uri)) { if (it.moveToFirst()) it.getLong(0) else -1L }
    }

    fun deleteDocument(id: Long) {
        exec("DELETE FROM documents WHERE id=?", arrayOf(id)); exec("DELETE FROM doc_marks WHERE doc_id=?", arrayOf(id))
    }

    fun docMarks(docId: Long): Map<String, String> = query("SELECT line, token, type FROM doc_marks WHERE doc_id=?", arrayOf(docId.toString())) { c ->
        buildMap { while (c.moveToNext()) put(c.getInt(0).toString() + ":" + c.getString(1), c.getString(2)) }
    }

    fun toggleDocMark(docId: Long, line: Int, token: String, type: String) {
        val key = "$line:$token"
        val existing = query("SELECT type FROM doc_marks WHERE doc_id=? AND line=? AND token=?", arrayOf(docId.toString(), line.toString(), token)) { if (it.moveToFirst()) it.getString(0) else null }
        if (existing == type) exec("DELETE FROM doc_marks WHERE doc_id=? AND line=? AND token=?", arrayOf(docId.toString(), line.toString(), token))
        else exec("INSERT OR REPLACE INTO doc_marks(doc_id,line,token,type) VALUES(?,?,?,?)", arrayOf(docId.toString(), line.toString(), token, type))
    }

    fun stats(): ProfileStats {
        val total = query("SELECT COUNT(*) FROM query_log") { if (it.moveToFirst()) it.getInt(0) else 0 }
        val learned = query("SELECT COUNT(*) FROM wordbook_entries WHERE learned=1") { if (it.moveToFirst()) it.getInt(0) else 0 }
        val notes = query("SELECT COUNT(*) FROM notes") { if (it.moveToFirst()) it.getInt(0) else 0 }
        val days = query("SELECT day FROM activity WHERE queries>0 ORDER BY day DESC") { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }
        var streak = 0
        val cursor = Calendar.getInstance()
        while (true) {
            val key = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cursor.time)
            if (days.contains(key)) { streak++; cursor.add(Calendar.DAY_OF_YEAR, -1) } else break
        }
        return ProfileStats(total, learned, notes, streak, days.size)
    }

    fun activityMap(): Map<String, Int> = query("SELECT day, queries FROM activity") { c ->
        buildMap { while (c.moveToNext()) put(c.getString(0), c.getInt(1)) }
    }

    fun dayDetail(day: String): DayDetail {
        val stat = query("SELECT queries, words, minutes FROM activity WHERE day=?", arrayOf(day)) { c ->
            if (c.moveToFirst()) DayStat(day, c.getInt(0), c.getInt(1), c.getInt(2)) else null
        }
        val queries = query(
            "SELECT word FROM query_log WHERE date(ts/1000,'unixepoch','localtime')=? GROUP BY lower(word) ORDER BY MAX(ts) DESC LIMIT 200",
            arrayOf(day)) { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        return DayDetail(day, queries, stat)
    }

    fun recentQueries(limit: Int = 20): List<RecentQuery> = query(
        "SELECT word, MAX(ts) AS ts FROM query_log GROUP BY lower(word) ORDER BY ts DESC LIMIT ?",
        arrayOf(limit.toString())) { c ->
        buildList { while (c.moveToNext()) add(RecentQuery(c.getString(0), c.getLong(1))) }
    }

    fun isDemoSeeded(): Boolean = query("SELECT 1 FROM meta WHERE key='demo_seeded'") { it.moveToFirst() }

    fun isDemoCleared(): Boolean = query("SELECT 1 FROM meta WHERE key='demo_cleared'") { it.moveToFirst() }

    fun seedDemoIfNeeded() {
        if (isDemoSeeded() || isDemoCleared()) return
        val rnd = Random(20261022)
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -239)
        repeat(240) {
            val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
            val n = when {
                rnd.nextInt(100) < 16 -> 0
                rnd.nextInt(100) < 62 -> rnd.nextInt(6) + 1
                rnd.nextInt(100) < 92 -> rnd.nextInt(12) + 6
                else -> rnd.nextInt(20) + 14
            }
            if (n > 0) exec("INSERT OR IGNORE INTO activity(day, queries, demo) VALUES(?,?,1)", arrayOf(day, n))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        exec("INSERT OR REPLACE INTO meta(key,value) VALUES('demo_seeded','1')")
    }

    fun clearDemoData() {
        exec("DELETE FROM activity WHERE demo=1")
        exec("DELETE FROM meta WHERE key='demo_seeded'")
        exec("INSERT OR REPLACE INTO meta(key,value) VALUES('demo_cleared','1')")
    }

    companion object {
        @Volatile private var inst: UserDb? = null
        fun get(context: Context): UserDb = inst ?: synchronized(this) {
            inst ?: UserDb(context.applicationContext).also { inst = it }
        }
    }
}
