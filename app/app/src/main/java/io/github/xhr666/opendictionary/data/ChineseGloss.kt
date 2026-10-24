package io.github.xhr666.opendictionary.data

/** 为在线查询结果生成可核对的中文释义：优先本地词库、组合词拆分、近义词中文，最后返回空。 */
object ChineseGloss {
    fun describe(word: String, result: OnlineDictionary.OnlineResult, lex: LexiconDb): String {
        direct(lex, word)?.let { return it }
        components(lex, word)?.let { return it }
        synonyms(result, lex)?.let { return it }
        return ""
    }

    private fun firstLine(s: String): String = s.split("\n").firstOrNull()?.trim().orEmpty()

    private fun direct(lex: LexiconDb, word: String): String? {
        val tr = lex.detail(word)?.entry?.translation ?: return null
        return firstLine(tr).ifBlank { null }
    }

    private fun pieces(word: String): List<String> {
        val spaced = word.replace(Regex("([a-z0-9])([A-Z])"), "\$1-\$2").replace('_', '-').replace(' ', '-')
        return spaced.split('-').map { it.trim() }.filter { it.length >= 2 && it.any { c -> c.isLetter() } }.take(6)
    }

    private fun components(lex: LexiconDb, word: String): String? {
        val parts = pieces(word)
        if (parts.size < 2) return null
        val translated = parts.map { p -> p to direct(lex, p) }
        if (translated.any { it.second == null }) return null
        val joined = translated.joinToString(" + ") { "${it.first} ${it.second}" }
        val compact = translated.joinToString("") { it.second.orEmpty().replace(Regex("^[a-z]+\\.\\s*"), "") }
        return "$joined → $compact"
    }

    private fun synonyms(result: OnlineDictionary.OnlineResult, lex: LexiconDb): String? {
        val hits = result.synonyms.mapNotNull { s -> direct(lex, s)?.let { s to it } }.take(5)
        if (hits.isEmpty()) return null
        return "近义： " + hits.joinToString("、") { "${it.first} ${it.second}" }
    }
}
