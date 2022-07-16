package dev.banderkat.podtitles.models

/**
 * Represents a single subtitle cue
 */
data class WebVttCue(
    val start: Double,
    val end: Double,
    val text: String
) {
    override fun toString(): String {
        return "WebVttCue (start: $start end: $end text: $text)"
    }
}