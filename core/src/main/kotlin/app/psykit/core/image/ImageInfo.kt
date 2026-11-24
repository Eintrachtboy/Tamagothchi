package app.psykit.core.image

/**
 * Eckdaten eines Bildes direkt aus dem Dateikopf (ohne zu dekodieren):
 * Format, Größe in Pixeln, Anzahl Frames und Gesamtdauer. Dieselbe Logik steckt im PC-Prüfskript
 * tools/psykit_pruefen.py.
 */
data class ImageInfo(
    val format: String,
    val width: Int,
    val height: Int,
    val frames: Int = 1,
    val durationMs: Long = 0,
    val alpha: Boolean? = null,
    /** Animiertes PNG: die App übernimmt nur das erste Bild. */
    val apngFrames: Int = 0,
    /** GIF-Frames ohne Verzögerung (Android zeigt sie je 100 ms). */
    val zeroDelayFrames: Int = 0,
) {
    val animated get() = frames > 1
    val fps get() = if (durationMs > 0) frames * 1000.0 / durationMs else 0.0
    val maxSide get() = maxOf(width, height)

    companion object {
        fun parse(b: ByteArray): ImageInfo? =
            runCatching { webp(b) ?: gif(b) ?: png(b) ?: jpeg(b) }.getOrNull()

        /** Animiert im Sinne der App: GIF, oder WebP mit Animations-Flag im VP8X-Block. */
        fun isAnimatedContainer(b: ByteArray): Boolean {
            if (b.size > 6 && b[0] == 'G'.code.toByte() && b[1] == 'I'.code.toByte() && b[2] == 'F'.code.toByte()) return true
            if (!isWebp(b)) return false
            var o = 12
            while (o + 8 <= b.size) {
                val size = le32(b, o + 4)
                if (tag(b, o) == "VP8X" && size >= 10) return (b[o + 8].toInt() and 0x02) != 0
                if (tag(b, o) == "ANIM" || tag(b, o) == "ANMF") return true
                if (size < 0) return false
                o += 8 + size + (size and 1)
            }
            return false
        }

        private fun u8(b: ByteArray, o: Int) = b[o].toInt() and 0xFF
        private fun le16(b: ByteArray, o: Int) = u8(b, o) or (u8(b, o + 1) shl 8)
        private fun le24(b: ByteArray, o: Int) = le16(b, o) or (u8(b, o + 2) shl 16)
        private fun le32(b: ByteArray, o: Int) = le24(b, o) or (u8(b, o + 3) shl 24)
        private fun be16(b: ByteArray, o: Int) = (u8(b, o) shl 8) or u8(b, o + 1)
        private fun be32(b: ByteArray, o: Int) = (be16(b, o) shl 16) or be16(b, o + 2)
        private fun tag(b: ByteArray, o: Int) = String(b, o, 4, Charsets.ISO_8859_1)
        private fun isWebp(b: ByteArray) = b.size >= 16 && tag(b, 0) == "RIFF" && tag(b, 8) == "WEBP"

        private fun webp(b: ByteArray): ImageInfo? {
            if (!isWebp(b)) return null
            var w = 0; var h = 0; var frames = 0; var dur = 0L; var alpha = false
            var o = 12
            while (o + 8 <= b.size) {
                val id = tag(b, o)
                val size = le32(b, o + 4)
                if (size < 0) break
                val p = o + 8
                when {
                    id == "VP8X" && size >= 10 -> { alpha = (u8(b, p) and 0x10) != 0; w = le24(b, p + 4) + 1; h = le24(b, p + 7) + 1 }
                    id == "ANMF" && size >= 16 -> { frames++; dur += le24(b, p + 12) }
                    id == "VP8 " && size >= 10 && w == 0 -> { w = le16(b, p + 6) and 0x3FFF; h = le16(b, p + 8) and 0x3FFF }
                    id == "VP8L" && size >= 5 && w == 0 -> {
                        val bits = le32(b, p + 1)
                        w = (bits and 0x3FFF) + 1; h = ((bits ushr 14) and 0x3FFF) + 1; alpha = ((bits ushr 28) and 1) != 0
                    }
                    id == "ALPH" -> alpha = true
                }
                o = p + size + (size and 1)
            }
            return ImageInfo("WebP", w, h, maxOf(frames, 1), dur, alpha)
        }

        private fun gif(b: ByteArray): ImageInfo? {
            if (b.size < 13 || tag(b, 0).substring(0, 3) != "GIF") return null
            val w = le16(b, 6); val h = le16(b, 8)
            val flags = u8(b, 10)
            var o = 13 + if (flags and 0x80 != 0) 3 * (1 shl ((flags and 7) + 1)) else 0
            var frames = 0; var dur = 0L; var zero = 0; var delay = 0; var alpha = false
            fun skipSub(start: Int): Int {
                var i = start
                while (i < b.size) { val n = u8(b, i); i++; if (n == 0) return i; i += n }
                return i
            }
            while (o < b.size) {
                when (u8(b, o)) {
                    0x21 -> {
                        if (o + 1 >= b.size) break
                        if (u8(b, o + 1) == 0xF9 && o + 7 < b.size) { alpha = alpha || (u8(b, o + 3) and 1) != 0; delay = le16(b, o + 4) * 10 }
                        o = skipSub(o + 2)
                    }
                    0x2C -> {
                        if (o + 10 > b.size) break
                        val f = u8(b, o + 9)
                        o += 10 + if (f and 0x80 != 0) 3 * (1 shl ((f and 7) + 1)) else 0
                        o = skipSub(o + 1)
                        frames++
                        if (delay <= 10) { zero++; delay = 100 }
                        dur += delay; delay = 0
                    }
                    else -> break
                }
            }
            return ImageInfo("GIF", w, h, maxOf(frames, 1), dur, alpha, zeroDelayFrames = if (frames > 1) zero else 0)
        }

        private val PNG_SIG = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)

        private fun png(b: ByteArray): ImageInfo? {
            if (b.size < 26 || !b.copyOfRange(0, 8).contentEquals(PNG_SIG)) return null
            val w = be32(b, 16); val h = be32(b, 20)
            var alpha = u8(b, 25) == 4 || u8(b, 25) == 6
            var apng = 0
            var o = 8
            while (o + 8 <= b.size) {
                val size = be32(b, o)
                val id = tag(b, o + 4)
                if (id == "tRNS") alpha = true
                if (id == "acTL" && size >= 8) apng = be32(b, o + 8)
                if (id == "IDAT" || id == "IEND" || size < 0) break
                o += 12 + size
            }
            return ImageInfo("PNG", w, h, 1, 0, alpha, apngFrames = if (apng > 1) apng else 0)
        }

        private fun jpeg(b: ByteArray): ImageInfo? {
            if (b.size < 4 || u8(b, 0) != 0xFF || u8(b, 1) != 0xD8) return null
            var o = 2
            while (o + 9 < b.size) {
                if (u8(b, o) != 0xFF) { o++; continue }
                val m = u8(b, o + 1)
                if (m == 0xD8 || m == 0x01 || m in 0xD0..0xD7 || m == 0xFF) { o += if (m == 0xFF) 1 else 2; continue }
                val seg = be16(b, o + 2)
                if (m in 0xC0..0xCF && m != 0xC4 && m != 0xC8 && m != 0xCC) return ImageInfo("JPEG", be16(b, o + 7), be16(b, o + 5), alpha = false)
                o += 2 + seg
            }
            return ImageInfo("JPEG", 0, 0, alpha = false)
        }
    }
}
