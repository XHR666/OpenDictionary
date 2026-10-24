package io.github.xhr666.opendictionary.data

/** 连字符/空格查词变体与规则词形提示（纯函数，可单测）。 */
object WordForms {
    fun compact(s: String): String = s.trim().lowercase().filter { it.isLetterOrDigit() }

    /** 查询时尝试的书写变体（去连字符、连字符换空格、空格换连字符）。 */
    fun variants(s: String): List<String> {
        val t = s.trim()
        if (t.isEmpty()) return emptyList()
        return listOf(
            t.replace("-", ""),
            t.replace("-", " "),
            t.replace(" ", "-"),
            t.replace(" ", ""),
        ).map { it.trim() }.filter { it.isNotEmpty() && it != t }.distinct()
    }

    /**
     * 规则复数提示，仅用于连字符复合词等词库未收录词形的情况。
     * 返回“词形 to 说明”，说明注明为规则推导，避免冒充词典数据。
     */
    fun pluralHints(word: String): List<Pair<String, String>> {
        val w = word.trim().lowercase()
        if (w.isEmpty()) return emptyList()
        if (w.contains("-")) {
            val parts = w.split("-")
            if (parts.size >= 2) {
                val first = parts.first()
                val pluralFirst = pluralize(first)
                if (pluralFirst != first) return listOf((listOf(pluralFirst) + parts.drop(1)).joinToString("-") to "规则复数（自动推导）")
            }
            return emptyList()
        }
        // passerby → passers-by；仅对 -erby 结尾的常见复合词做保守推导
        if (w.endsWith("erby") && w.length > 5) {
            return listOf(w.dropLast(2) + "s-by" to "规则复数（自动推导）")
        }
        return emptyList()
    }

    private fun pluralize(s: String): String {
        if (s.isEmpty()) return s
        if (s.endsWith("s") || s.endsWith("x") || s.endsWith("z") || s.endsWith("ch") || s.endsWith("sh")) return s + "es"
        if (s.length > 1 && s.endsWith("y") && s[s.length - 2] !in "aeiou") return s.dropLast(1) + "ies"
        return s + "s"
    }
}
