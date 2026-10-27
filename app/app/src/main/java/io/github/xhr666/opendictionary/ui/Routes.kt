package io.github.xhr666.opendictionary.ui

sealed interface Route {
    data object Home : Route
    data class Word(val q: String = "reputation") : Route
    data class Search(val q: String) : Route
    data object SearchInput : Route
    data object Settings : Route
    data object Llm : Route
    data object Academic : Route
    data object Fonts : Route
    data object Offline : Route
    data object Privacy : Route
    data object Clipboard : Route
    data object Experiment : Route
    data object Help : Route
    data object Licenses : Route
    data object Support : Route
    data object Learning : Route
    data object Review : Route
    data object FoldReview : Route
    data class CardMode(val bookId: Long = -1L) : Route
    data object Media : Route
    data object Daily : Route
    data object Notes : Route
    data object Diagnostics : Route
    data object CustomWords : Route
    data object SpeechSources : Route
    data object ExamBank : Route
    data object Sources : Route
    data object PdfReader : Route
    data object EpubReader : Route
    data object ListeningPractice : Route
    data object TranslationPractice : Route
    data class DocReader(val docId: Long = -1L) : Route
    data object ExamReading : Route
    data class MediaPlayer(val mediaId: Long) : Route
    data object Writing : Route
    data object Listening : Route
    data object Reading : Route
    data object About : Route
    data object Profile : Route
    data class Day(val day: String) : Route
    data object Notifications : Route
    data object Accessibility : Route
    data object Backup : Route
    data object DictSource : Route
    data object DictPopup : Route
}
