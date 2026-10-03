package online.draran.billing.core.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Accent colour of printed bills (top bar, title, table header, total band).
 * Colours are ARGB ints; 0 in the business profile means the app's default.
 * Every colour passes through [readable] so white text on the total band stays legible.
 */
object BillColors {
    const val DEFAULT: Int = 0xFF4F46E5.toInt()

    /** Ready-made choices, all dark enough for white text. */
    val PRESETS: List<Pair<String, Int>> = listOf(
        "Indigo" to DEFAULT,
        "Royal blue" to 0xFF1D4ED8.toInt(),
        "Teal" to 0xFF0F766E.toInt(),
        "Green" to 0xFF15803D.toInt(),
        "Maroon" to 0xFF9F1239.toInt(),
        "Red" to 0xFFB91C1C.toInt(),
        "Saffron" to 0xFFC2410C.toInt(),
        "Brown" to 0xFF92400E.toInt(),
        "Purple" to 0xFF7E22CE.toInt(),
        "Pink" to 0xFFBE185D.toInt(),
        "Navy" to 0xFF1E3A8A.toInt(),
        "Charcoal" to 0xFF374151.toInt(),
    )

    /** WCAG contrast needed for small white text on the colour. */
    private const val MIN_CONTRAST = 4.5

    fun red(c: Int) = (c shr 16) and 0xFF
    fun green(c: Int) = (c shr 8) and 0xFF
    fun blue(c: Int) = c and 0xFF
    fun alpha(c: Int) = (c ushr 24) and 0xFF
    fun rgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    private fun channel(v: Int): Double {
        val s = v / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    /** WCAG relative luminance, 0 (black) to 1 (white). */
    fun luminance(c: Int): Double = 0.2126 * channel(red(c)) + 0.7152 * channel(green(c)) + 0.0722 * channel(blue(c))

    fun contrastWithWhite(c: Int): Double = 1.05 / (luminance(c) + 0.05)

    /** Darkens the colour step by step (keeping its hue) until white text on it is readable. */
    fun readable(c: Int): Int {
        var r = red(c).toDouble()
        var g = green(c).toDouble()
        var b = blue(c).toDouble()
        var out = rgb(r.toInt(), g.toInt(), b.toInt())
        var guard = 0
        while (contrastWithWhite(out) < MIN_CONTRAST && guard++ < 60) {
            r *= 0.94; g *= 0.94; b *= 0.94
            out = rgb(r.toInt(), g.toInt(), b.toInt())
        }
        return out
    }

    /** Very light version for table headers: [amount] of the colour over white. */
    fun tint(c: Int, amount: Double = 0.10): Int = rgb(
        (255 + (red(c) - 255) * amount).toInt(),
        (255 + (green(c) - 255) * amount).toInt(),
        (255 + (blue(c) - 255) * amount).toInt(),
    )

    /** The colour actually printed for a stored value (0 = default). */
    fun accentOf(stored: Int): Int = readable(if (stored == 0) DEFAULT else stored)

    fun hex(c: Int): String = "#%06X".format(c and 0xFFFFFF)

    private fun saturation(c: Int): Double {
        val mx = max(red(c), max(green(c), blue(c)))
        val mn = min(red(c), min(green(c), blue(c)))
        return if (mx == 0) 0.0 else (mx - mn) / mx.toDouble()
    }

    private fun distance(a: Int, b: Int): Double {
        val dr = (red(a) - red(b)).toDouble()
        val dg = (green(a) - green(b)).toDouble()
        val db = (blue(a) - blue(b)).toDouble()
        return sqrt(dr * dr + dg * dg + db * db)
    }

    /**
     * Main colours of a logo, most used first, already made [readable].
     * Transparent, near-white and grey pixels are ignored; an all-grey or black
     * logo gives one charcoal-like colour. [pixels] are ARGB (a small thumbnail is enough).
     */
    fun fromLogo(pixels: IntArray, max: Int = 4): List<Int> {
        val counts = HashMap<Int, IntArray>() // bucket -> [count, sumR, sumG, sumB]
        var darkCount = 0
        var darkR = 0L; var darkG = 0L; var darkB = 0L
        for (p in pixels) {
            if (alpha(p) < 128) continue
            val r = red(p); val g = green(p); val b = blue(p)
            if (min(r, min(g, b)) > 225) continue // paper / white background
            if (saturation(p) < 0.25 || max(r, max(g, b)) < 40) {
                if (max(r, max(g, b)) < 160) { darkCount++; darkR += r; darkG += g; darkB += b }
                continue
            }
            val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
            val acc = counts.getOrPut(key) { IntArray(4) }
            acc[0]++; acc[1] += r; acc[2] += g; acc[3] += b
        }
        val coloured = counts.values.sumOf { it[0] }
        // Mostly black-and-white logo: use its dark tone
        if (coloured < pixels.size / 200 + 1) {
            return if (darkCount > 0) listOf(readable(rgb((darkR / darkCount).toInt(), (darkG / darkCount).toInt(), (darkB / darkCount).toInt()))) else emptyList()
        }
        val ranked = counts.values.sortedByDescending { it[0] }
            .map { (it[0]) to rgb(it[1] / it[0], it[2] / it[0], it[3] / it[0]) }
        // Merge buckets that look the same once printed
        val merged = mutableListOf<Pair<Int, Int>>()
        for ((n, c) in ranked) {
            val i = merged.indexOfFirst { distance(it.second, c) < 70 }
            if (i >= 0) merged[i] = (merged[i].first + n) to merged[i].second else merged += n to c
        }
        return merged.sortedByDescending { it.first }
            .filter { it.first >= coloured / 50 } // ignore specks and anti-aliasing
            .map { readable(it.second) }
            .fold(mutableListOf<Int>()) { out, c -> if (out.none { abs(luminance(it) - luminance(c)) < 0.01 && distance(it, c) < 40 }) out += c; out }
            .take(max)
    }
}
