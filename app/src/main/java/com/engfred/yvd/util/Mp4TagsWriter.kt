package com.engfred.yvd.util

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal, dependency-free M4A/MP4 iTunes-tag writer.
 *
 * jaudiotagger 2.2.3 cannot parse the files Android's `MediaMuxer` produces:
 * it chokes on the 64-bit (`size == 1`) `mdat` atom and on the non-iTunes
 * `meta` box layout, so M4A downloads never received title/artist/artwork.
 *
 * The iTunes `ilst`/`covr` structure is simple and deterministic for the
 * remuxed layout we generate (`ftyp + mdat + moov` with `moov` at the end),
 * so this writer replaces jaudiotagger for M4A files. Only the `moov` box is
 * rebuilt — the `mdat` bytes and every absolute sample offset stay untouched.
 */
object Mp4TagsWriter {

    private const val TAG = "Mp4TagsWriter"

    /** M4A boxes. We only care about sizes, since we never move sample data. */
    private class Box(val start: Int, val headerLen: Int, val size: Int, val id: String) {
        val payloadStart: Int get() = start + headerLen
    }

    class Mp4TagException(message: String) : Exception(message)

    /**
     * Writes [title] / [artist] and optional JPEG [artworkBytes] into the M4A at [path].
     * Rewrites the whole small file in memory (audio files are a few MB max).
     */
    fun writeTags(path: String, title: String, artist: String, artworkBytes: ByteArray?) {
        if (title.isBlank() && artist.isBlank() && artworkBytes == null) return

        val raw = File(path).readBytes()
        if (raw.size < 8) throw Mp4TagException("file too small")

        normalizeMdatSize(raw)

        val moov = findTopLevel(raw, "moov")
            ?: throw Mp4TagException("no moov atom found")

        val moovPayload = raw.copyOfRange(moov.payloadStart, moov.start + moov.size)
        val moovChildren = parseChildren(moovPayload, 0, moovPayload.size)

        val rebuilt = ArrayList<ByteArray>(moovChildren.size + 1)
        var udtaPayload: ByteArray? = null
        for (c in moovChildren) {
            val childPayload = moovPayload.sliceArray(c.payloadStart until c.start + c.size)
            when (c.id) {
                "udta" -> udtaPayload = childPayload // rebuild below with meta added
                "meta" -> Unit // drop MediaMuxer's meta; we insert our own inside udta
                else -> rebuilt.add(box(c.id, childPayload))
            }
        }

        val meta = buildMeta(title.trim(), artist.trim(), artworkBytes)
        val udta = box("udta", (udtaPayload ?: ByteArray(0)) + meta)
        rebuilt.add(udta)

        val moovBody = rebuilt.fold(ByteArrayOutputStream()) { acc, b -> acc.write(b); acc }
            .toByteArray()
        val newMoov = box("moov", moovBody)

        val out = ByteArrayOutputStream(raw.size + moovBody.size)
        out.write(raw, 0, moov.start)
        out.write(newMoov)
        File(path).writeBytes(out.toByteArray())
        Log.d(TAG, "Tags written: \"$title\" — \"$artist\" (art=${artworkBytes?.size ?: 0}B)")
    }

    // ─── Atom parsing ─────────────────────────────────────────────────────

    /**
     * Rewrites a 64-bit-extended-size `mdat` header (`size == 1` + 8-byte
     * largesize) into a plain 32-bit one when the size fits. Android MediaMuxer
     * emits the 64-bit form, which older parsers (and jaudiotagger) reject.
     *
     * Only the 8 header bytes change; sample data and all absolute offsets
     * (co64/stco) are untouched, so no other bookkeeping is needed.
     */
    private fun normalizeMdatSize(raw: ByteArray) {
        var pos = 0
        while (pos + 8 <= raw.size) {
            val s32 = readInt(raw, pos)
            if (s32 == 1) {
                val largesize = readLong(raw, pos + 8)
                if (largesize in 1..0xFFFFFFFFL) {
                    val out = ByteBuffer.wrap(raw, pos, 4).order(ByteOrder.BIG_ENDIAN)
                    out.putInt(largesize.toInt())
                    Log.d(TAG, "Normalized oversized mdat header to 32-bit size $largesize")
                }
                return
            }
            if (s32 <= 0) return
            pos += s32
        }
    }

    private fun findTopLevel(data: ByteArray, target: String): Box? {
        var pos = 0
        while (pos + 8 <= data.size) {
            val box = Box(pos, headerLen(data, pos), boxSize(data, pos), id(data, pos))
            if (box.id == target) return box
            if (box.size <= 0) return null
            pos += box.size
        }
        return null
    }

    private fun parseChildren(data: ByteArray, start: Int, end: Int): List<Box> {
        val result = ArrayList<Box>()
        var pos = start
        while (pos + 8 <= end) {
            val box = Box(pos, headerLen(data, pos), boxSize(data, pos), id(data, pos))
            result.add(box)
            if (box.size <= 0) break
            pos += box.size
        }
        return result
    }

    private fun boxSize(data: ByteArray, start: Int): Int {
        val s32 = readInt(data, start)
        return when {
            s32 == 1 -> readLong(data, start + 8).toInt() // 64-bit extended size
            s32 == 0 -> data.size - start
            else -> s32
        }
    }

    private fun headerLen(data: ByteArray, start: Int): Int =
        if (readInt(data, start) == 1) 16 else 8

    private fun id(data: ByteArray, start: Int): String =
        String(data, start + 4, 4, Charsets.ISO_8859_1)

    private fun readInt(data: ByteArray, off: Int): Int =
        ((data[off].toInt() and 0xFF) shl 24) or
            ((data[off + 1].toInt() and 0xFF) shl 16) or
            ((data[off + 2].toInt() and 0xFF) shl 8) or
            (data[off + 3].toInt() and 0xFF)

    private fun readLong(data: ByteArray, off: Int): Long =
        (readInt(data, off).toLong() and 0xFFFFFFFFL) shl 32 or
            (readInt(data, off + 4).toLong() and 0xFFFFFFFFL)

    // ─── Atom building ────────────────────────────────────────────────────

    private fun buildMeta(title: String, artist: String, artworkBytes: ByteArray?): ByteArray {
        val ilstPayload = ByteArrayOutputStream()

        if (title.isNotBlank()) ilstPayload.write(textItem("©nam", title))
        if (artist.isNotBlank()) ilstPayload.write(textItem("©ART", artist))

        artworkBytes?.let {
            // MediaMetadataRetriever/players accept covr with data type 13 (JPEG) or 14 (PNG).
            val isJpeg = it.size >= 3 && it[0] == 0xFF.toByte() && it[1] == 0xD8.toByte() && it[2] == 0xFF.toByte()
            ilstPayload.write(box("covr", dataBox(if (isJpeg) 13 else 14, it)))
        }

        val hdlrPayload = ByteBuffer.allocate(25).order(ByteOrder.BIG_ENDIAN)
        hdlrPayload.putInt(0)            // version + flags
        hdlrPayload.putInt(0)            // pre_defined
        hdlrPayload.put("mdta".toByteArray(Charsets.ISO_8859_1)) // handler type
        hdlrPayload.put(ByteArray(12))   // reserved
        hdlrPayload.put(0)               // zero-terminated name

        val metaPayload = ByteBuffer.allocate(4)
        metaPayload.order(ByteOrder.BIG_ENDIAN)
        metaPayload.putInt(0)            // fullbox version + flags (must be 0)

        return box(
            "meta",
            metaPayload.array() + box("hdlr", hdlrPayload.array()) + box("ilst", ilstPayload.toByteArray())
        )
    }

    /** iTunes text tag item: [key box] containing a `data` atom (type 1 = UTF-8). */
    private fun textItem(key: String, value: String): ByteArray =
        box(key, dataBox(1, value.toByteArray(Charsets.UTF_8) + byteArrayOf(0)))

    /** `data` atom: version/flags/type (4 bytes) + locale (4 bytes) + value. */
    private fun dataBox(type: Int, bytes: ByteArray): ByteArray {
        val data = ByteBuffer.allocate(8 + bytes.size).order(ByteOrder.BIG_ENDIAN)
        data.putInt(type)
        data.putInt(0) // locale
        data.put(bytes)
        return box("data", data.array())
    }

    private fun box(id: String, payload: ByteArray): ByteArray {
        val out = ByteBuffer.allocate(8 + payload.size).order(ByteOrder.BIG_ENDIAN)
        out.putInt(8 + payload.size)
        out.put(id.toByteArray(Charsets.ISO_8859_1))
        out.put(payload)
        return out.array()
    }
}