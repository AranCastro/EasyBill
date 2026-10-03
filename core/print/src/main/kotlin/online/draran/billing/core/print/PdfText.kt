package online.draran.billing.core.print

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import online.draran.billing.core.common.IndianFormat
import online.draran.billing.core.designsystem.R
import online.draran.billing.core.model.Money

/** Fonts and paints shared by the PDF renderers. */
internal class PdfFonts(private val context: Context) {
    private fun load(id: Int, fallbackStyle: Int): Typeface =
        runCatching { ResourcesCompat.getFont(context, id) }.getOrNull() ?: Typeface.create(Typeface.SANS_SERIF, fallbackStyle)

    val regular: Typeface = load(R.font.inter_regular, Typeface.NORMAL)
    val medium: Typeface = load(R.font.inter_medium, Typeface.NORMAL)
    val bold: Typeface = load(R.font.inter_semibold, Typeface.BOLD)

    fun paint(size: Float, typeface: Typeface = regular, color: Int = INK, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = size
            this.color = color
            textAlign = align
            fontFeatureSettings = "tnum"
        }

    companion object {
        const val INK = 0xFF14142B.toInt()
        const val MUTED = 0xFF5A5A72.toInt()
        const val LINE = 0xFFDCDCE8.toInt()
        const val BRAND = 0xFF4F46E5.toInt()
        const val BRAND_TINT = 0xFFEEF0FF.toInt()
        const val ROW_TINT = 0xFFF7F7FB.toInt()
    }
}

/** Splits text into lines that fit [width] using [paint]. */
internal fun wrap(text: String, paint: Paint, width: Float): List<String> {
    if (text.isBlank()) return listOf("")
    val out = mutableListOf<String>()
    text.split("\n").forEach { paragraph ->
        var line = ""
        paragraph.split(" ").forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) <= width) {
                line = candidate
            } else {
                if (line.isNotEmpty()) out += line
                // Hard-break very long words
                var rest = word
                while (paint.measureText(rest) > width && rest.length > 1) {
                    var cut = rest.length
                    while (cut > 1 && paint.measureText(rest.substring(0, cut)) > width) cut--
                    out += rest.substring(0, cut)
                    rest = rest.substring(cut)
                }
                line = rest
            }
        }
        out += line
    }
    return out
}

internal fun rs(money: Money) = IndianFormat.rupees(money)
