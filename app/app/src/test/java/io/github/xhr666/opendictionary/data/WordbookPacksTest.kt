package io.github.xhr666.opendictionary.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.MessageDigest

class WordbookPacksTest {
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun validPackIsParsed() {
        val raw = """{"schema":1,"words":["apple","banana","  cherry  "]}""".toByteArray()
        val words = WordbookPacks.verifyAndParsePack(raw, sha(raw), raw.size.toLong())
        assertEquals(listOf("apple", "banana", "cherry"), words)
    }

    @Test
    fun wrongHashIsRejected() {
        val raw = """{"words":["apple"]}""".toByteArray()
        assertThrows(IllegalStateException::class.java) {
            WordbookPacks.verifyAndParsePack(raw, "0".repeat(64), raw.size.toLong())
        }
    }

    @Test
    fun wrongSizeIsRejected() {
        val raw = """{"words":["apple"]}""".toByteArray()
        assertThrows(IllegalStateException::class.java) {
            WordbookPacks.verifyAndParsePack(raw, sha(raw), raw.size.toLong() + 1)
        }
    }

    @Test
    fun wrongCountIsRejected() {
        val raw = """{"words":["apple","banana"]}""".toByteArray()
        assertThrows(IllegalStateException::class.java) {
            WordbookPacks.verifyAndParsePack(raw, sha(raw), raw.size.toLong(), 3)
        }
    }

    @Test
    fun invalidHashFormatIsRejected() {
        val raw = """{"words":["apple"]}""".toByteArray()
        assertThrows(IllegalStateException::class.java) {
            WordbookPacks.verifyAndParsePack(raw, "abc", raw.size.toLong())
        }
    }

    @Test
    fun malformedJsonIsRejected() {
        val raw = "not json".toByteArray()
        assertThrows(Exception::class.java) {
            WordbookPacks.verifyAndParsePack(raw, sha(raw), raw.size.toLong())
        }
    }

    @Test
    fun missingWordsFieldIsRejected() {
        val raw = """{"schema":1}""".toByteArray()
        assertThrows(Exception::class.java) {
            WordbookPacks.verifyAndParsePack(raw, sha(raw), raw.size.toLong())
        }
    }
}
