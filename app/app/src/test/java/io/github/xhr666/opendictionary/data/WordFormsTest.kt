package io.github.xhr666.opendictionary.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordFormsTest {
    @Test
    fun compactIgnoresHyphenSpaceCase() {
        assertEquals("passerby", WordForms.compact("Passer-By"))
        assertEquals("email", WordForms.compact("e-mail"))
        assertEquals("envelopevalidation", WordForms.compact("envelope validation"))
    }

    @Test
    fun variantsIncludeHyphenAndSpaceForms() {
        val v = WordForms.variants("passer-by")
        assertTrue(v.contains("passerby"))
        assertTrue(v.contains("passer by"))
        assertTrue(WordForms.variants("e mail").contains("e-mail"))
    }

    @Test
    fun compoundPluralHints() {
        assertEquals(listOf("passers-by" to "规则复数（自动推导）"), WordForms.pluralHints("passerby"))
        assertEquals(listOf("passers-by" to "规则复数（自动推导）"), WordForms.pluralHints("passer-by"))
        assertEquals(listOf("mothers-in-law" to "规则复数（自动推导）"), WordForms.pluralHints("mother-in-law"))
        assertTrue(WordForms.pluralHints("standby").isEmpty())
    }
}
