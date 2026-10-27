package io.github.xhr666.opendictionary.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {
    @Test
    fun normalLrc_linesSortedAndWordsEstimated() {
        val doc = LrcParser.parse("[00:03.00]World\n[00:01.00]Hello\n[00:05.00]Again")
        assertEquals(3, doc.lines.size)
        assertEquals("Hello", doc.lines[0].third)
        assertTrue(doc.words.isNotEmpty())
        assertTrue(doc.words.all { it.second.first >= 0 && it.second.second >= it.second.first })
    }

    @Test
    fun offsetTagShiftsTimestamps() {
        val doc = LrcParser.parse("[offset:500]\n[00:01.00]Hello")
        assertEquals(1500L, doc.lines.first().first)
    }

    @Test
    fun enhancedWordTagsAreParsed() {
        val doc = LrcParser.parse("[00:10.00]<00:10.00>Hello <00:10.50>world")
        assertTrue(doc.words.size >= 2)
        assertEquals("hello", doc.words[0].first)
        assertEquals("world", doc.words[1].first)
        assertTrue(doc.words[1].second.first >= doc.words[0].second.first)
    }

    @Test
    fun multiTimestampLineExpands() {
        val doc = LrcParser.parse("[00:01.00][00:05.00]Repeat me")
        assertEquals(2, doc.lines.size)
        assertTrue(doc.lines.all { it.third == "Repeat me" })
    }

    @Test
    fun enhancedWordTimesIncludeOffset() {
        val doc = LrcParser.parse("[offset:500]\n[00:10.00]<00:10.00>Hello <00:10.50>world")
        assertTrue(doc.words.size >= 2)
        assertTrue(doc.words.all { it.second.first >= 10500 })
    }

    @Test
    fun leadingTextBeforeFirstTagIsKept() {
        val doc = LrcParser.parse("[00:10.00]Hello <00:10.50>world")
        val firsts = doc.words.map { it.first }
        assertTrue(firsts.contains("hello"))
        assertTrue(firsts.contains("world"))
    }

    @Test
    fun multiTimestampEnhancedShiftsWords() {
        val doc = LrcParser.parse("[00:01.00][00:05.00]<00:01.00>Go <00:01.50>now")
        val go = doc.words.filter { it.first == "go" }.map { it.second.first }
        val now = doc.words.filter { it.first == "now" }.map { it.second.first }
        assertEquals(2, go.size)
        assertEquals(2, now.size)
        assertTrue(go.contains(1000L) && go.contains(5000L))
        assertTrue(now.contains(1500L) && now.contains(5500L))
    }
}
