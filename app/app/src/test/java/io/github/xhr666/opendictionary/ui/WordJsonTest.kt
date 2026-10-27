package io.github.xhr666.opendictionary.ui

import io.github.xhr666.opendictionary.data.LexEntry
import io.github.xhr666.opendictionary.data.LexLemma
import io.github.xhr666.opendictionary.data.LexPhrase
import io.github.xhr666.opendictionary.data.Sense
import io.github.xhr666.opendictionary.data.WordData
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordJsonTest {
    private fun sample() = WordData(
        entry = LexEntry("reputation", "ˌrepjuˈteɪʃn", "n. 名誉", "the opinion people have", "n", "cet4 cet6", "s:reputations", 1200),
        tags = listOf("cet4", "gk"),
        phrases = listOf(LexPhrase("have a reputation for", "以…闻名", "phrase", "CC BY-SA 4.0")),
        lemmas = listOf(LexLemma("reputations", "reputation", "plural")),
        related = listOf("fame" to "名声"),
        senses = listOf(Sense("reputation", "n", "声望；名誉", "He has a good reputation.", "ECDICT")),
    )

    @Test
    fun jsonContainsCoreFields() {
        val obj = JSONObject(wordJson(sample()))
        assertEquals("reputation", obj.getString("word"))
        assertEquals("n. 名誉", obj.getString("translation"))
        assertEquals("cet4 cet6", obj.getString("tag"))
        assertTrue(obj.getJSONArray("tags").length() == 2)
        assertTrue(obj.getJSONArray("phrases").length() == 1)
        assertTrue(obj.getJSONArray("lemmas").length() == 1)
        assertTrue(obj.getJSONArray("related").length() == 1)
        assertTrue(obj.getJSONArray("senses").length() == 1)
        assertEquals("fame", obj.getJSONArray("related").getJSONObject(0).getString("word"))
    }
}
