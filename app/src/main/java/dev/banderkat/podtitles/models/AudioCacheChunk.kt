package dev.banderkat.podtitles.models

/**
 * Represents a downloaded chunk of an audio file cached by ExoPlayer.
 * Used in transcribing a series of chunks to generate subtitles for an entire audio file.
 */
data class AudioCacheChunk(
    val position: Int, // order of this chunk in the set chunks for this file
    val filePath: String, // may be either path to audio file or its transcript
    val duration: Double? = null // duration in seconds for this chunk, as read by FFmpeg
) {
    override fun toString(): String {
        return "AudioCacheChunk (position: $position path: $filePath duration $duration)"
    }
}