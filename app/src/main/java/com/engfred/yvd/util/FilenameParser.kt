package com.engfred.yvd.util

/**
 * Turns messy YouTube titles into clean, conventional "Artist - Song" names.
 *
 * Example input:  `Fik Fameica - Sikiwa (Official Music Video)`
 * Example output: `Fik Fameica - Sikiwa.m4a` / `.mp3`
 *
 * Pipeline:
 * 1. Drop bracketed/parenthesised segments that contain junk keywords
 *    (official, lyrics, feat, hd, 4k, visual…).
 * 2. Drop a trailing `| UploaderName` channel suffix.
 * 3. Split on ` - `; the part before the first dash is the artist, everything
 *    after is the song. When no dash exists the uploader name is used as artist.
 * 4. [sanitize] replaces filesystem-illegal characters while keeping readable spaces.
 */
object FilenameParser {

    data class TrackName(val artist: String, val song: String)

    private const val DEFAULT_ARTIST = "Unknown Artist"

    private val BRACKET_SEGMENT: Regex = Regex("\\[[^\\]]*\\]|\\([^)]*\\)")

    private val JUNK_KEYWORDS = listOf(
        "official", "music video", "video", "audio", "lyric", "with lyrics",
        "lyrics video", "featuring", "feat", "ft", "hd", "4k", "8k",
        "1080p", "720p", "480p", "360p", "2160p", "visual", "visualizer",
        "visuals", "remix", "master", "album", "single", "letra", "karaoke",
        "cover", "acoustic", "session", "extended", "club mix", "radio edit",
        "dj", "prod", "officiallive", "live", "official album", "official audio",
        "official visualizer", "official lyric video", "official music video"
    )

    /**
     * Parses a YouTube title (plus optional uploader name) into artist + song.
     */
    fun parse(title: String, uploaderName: String?): TrackName {
        var cleaned = title.trim()
        cleaned = removeBracketSegments(cleaned)
        cleaned = stripChannelSuffix(cleaned, uploaderName)
        cleaned = cleaned.replace(Regex("\\s{2,}"), " ").trim()

        val dash = Regex("\\s*-\\s*").find(cleaned)
        return if (dash != null && dash.range.last + 1 < cleaned.length) {
            val artist = cleaned.substring(0, dash.range.first).trim()
            val song = cleaned.substring(dash.range.last + 1).trim()
            TrackName(
                artist = artist.ifBlank { uploaderName?.takeIf { it.isNotBlank() } ?: DEFAULT_ARTIST },
                song = song.ifBlank { cleaned }
            )
        } else {
            val fallbackArtist = uploaderName?.takeIf { it.isNotBlank() } ?: DEFAULT_ARTIST
            TrackName(artist = fallbackArtist, song = cleaned.ifBlank { fallbackArtist })
        }
    }

    /**
     * Builds the final audio filename (no extension): `"Artist - Song"`.
     */
    fun buildAudioBaseName(track: TrackName): String {
        val artist = track.artist.trim()
        val song = track.song.trim()
        return when {
            artist.isNotEmpty() && song.isNotEmpty() && artist != song ->
                sanitize("$artist - $song")
            else ->
                sanitize(song.ifBlank { artist.ifBlank { "audio" } })
        }
    }

    /**
     * Replaces characters illegal in filenames while preserving spaces, and
     * truncates over-long names with a short hash so names stay unique.
     */
    fun sanitize(name: String, maxLength: Int = 60): String {
        var s = name.trim().replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
        s = s.replace(Regex("\\s+"), " ").trim()
        if (s.length <= maxLength) return s

        val hash = s.hashCode().toLong().and(0xFFFFFFL).toString(16).padStart(6, '0')
        return "${s.take(maxLength - 7).trimEnd()}_$hash"
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Removes bracket/paren segments that contain a junk keyword.
     * Segments WITHOUT a keyword (e.g. "(Part 2)") are preserved.
     */
    private fun removeBracketSegments(input: String): String {
        var result = input
        BRACKET_SEGMENT.findAll(input).forEach { match ->
            val inner = match.value.removeSurrounding("[", "]").removeSurrounding("(", ")")
            val lower = inner.lowercase()
            if (JUNK_KEYWORDS.any { lower.contains(it) }) {
                result = result.replace(match.value, " ")
            }
        }
        return result
    }

    /**
     * Removes a trailing `| UploaderName` channel marker if it matches the uploader.
     */
    private fun stripChannelSuffix(input: String, uploaderName: String?): String {
        val idx = input.indexOf("|")
        if (idx <= 0 || uploaderName.isNullOrBlank()) return input
        val suffix = input.substring(idx + 1).trim()
        return if (suffix.equals(uploaderName, ignoreCase = true) ||
            suffix.startsWith(uploaderName, ignoreCase = true)
        ) {
            input.take(idx).trim()
        } else {
            input
        }
    }
}