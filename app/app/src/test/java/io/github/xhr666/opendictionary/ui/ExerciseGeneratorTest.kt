package io.github.xhr666.opendictionary.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseGeneratorTest {
    private val sample = "The city council approved a new plan to improve public transport. " +
        "Many residents believe the proposal will reduce traffic congestion significantly. " +
        "However, some critics argue that the funding could be better spent on schools and hospitals. " +
        "The mayor promised to review the results after one year and adjust the policy if necessary."

    @Test
    fun cloze_hasBlanksAndAnswers() {
        val ex = ExerciseGenerator.build(sample, "cloze")
        assertTrue(ex.questions.isNotEmpty())
        ex.questions.forEach { q -> assertTrue(ex.passage.contains("____")) }
        assertTrue(ex.passage.contains("____("))
    }

    @Test
    fun choice_optionsAlwaysContainAnswer() {
        val ex = ExerciseGenerator.build(sample, "choice")
        assertTrue(ex.questions.isNotEmpty())
        ex.questions.forEach { q ->
            assertEquals(3, q.options.size)
            assertTrue(q.options.contains(q.answer))
        }
    }

    @Test
    fun grammar_hasAnswers() {
        val ex = ExerciseGenerator.build(sample, "grammar")
        assertTrue(ex.questions.isNotEmpty())
        ex.questions.forEach { q -> assertFalse(q.answer.isBlank()) }
    }

    @Test
    fun seven_hasFourOptionsIncludingAnswers() {
        val ex = ExerciseGenerator.build(sample, "seven")
        assertTrue(ex.questions.isNotEmpty())
        ex.questions.forEach { q ->
            assertEquals(4, q.options.size)
            assertTrue(q.options.contains(q.answer))
        }
    }

    @Test
    fun shortTextProducesNoQuestions() {
        val ex = ExerciseGenerator.build("Too short.", "cloze")
        assertTrue(ex.questions.isEmpty())
    }

    @Test
    fun grammar_numberingIsContinuous() {
        val ex = ExerciseGenerator.build(sample, "grammar")
        val nums = Regex("\\[(\\d+)]").findAll(ex.passage).map { it.groupValues[1].toInt() }.sorted().toList()
        assertEquals((1..ex.questions.size).toList(), nums)
    }
}
