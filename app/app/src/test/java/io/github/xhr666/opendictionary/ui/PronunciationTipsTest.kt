package io.github.xhr666.opendictionary.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class PronunciationTipsTest {
    @Test
    fun functionWordHasWeakFormTip() {
        assertTrue(PronunciationTips.tips("the").isNotEmpty())
        assertTrue(PronunciationTips.tips("and").isNotEmpty())
    }

    @Test
    fun connectedSpeechTipExists() {
        assertTrue(PronunciationTips.tips("button").isNotEmpty())
        assertTrue(PronunciationTips.tips("water").isNotEmpty())
    }

    @Test
    fun unrelatedWordHasNoTip() {
        assertTrue(PronunciationTips.tips("reputation").isEmpty())
    }
}
