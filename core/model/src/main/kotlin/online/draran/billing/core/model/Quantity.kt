package online.draran.billing.core.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Quantities are stored as whole thousandths (milli-units) so 1.5 kg = 1500.
 */
object Qty {
    const val ONE = 1000L

    fun of(units: Long) = units * ONE

    fun parse(text: String): Long? = MoneyParse.normalise(text).takeIf { it.isNotEmpty() }?.let {
        runCatching { BigDecimal(it).movePointRight(3).setScale(0, RoundingMode.HALF_UP).longValueExact() }.getOrNull()
    }

    /** "1.5", "2", "0.25" — no trailing zeros. */
    fun format(milli: Long): String =
        BigDecimal.valueOf(milli, 3).stripTrailingZeros().let {
            if (it.scale() < 0) it.setScale(0).toPlainString() else it.toPlainString()
        }
}

object MoneyParse {
    /** Parses "1,250.5" or "₹99" into paise; null when not a number. */
    fun parse(text: String): Money? = normalise(text).takeIf { it.isNotEmpty() }?.let {
        runCatching { Money(BigDecimal(it).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()) }
            .getOrNull()
    }

    /**
     * "1,00,000.50" -> "100000.50". A single comma followed by one or two digits ("12,50",
     * from a decimal-comma keyboard) is a decimal point: grouping commas always have three digits after them.
     */
    fun normalise(text: String): String {
        val t = text.replace("₹", "").replace(" ", "").trim()
        return if (t.count { it == ',' } == 1 && !t.contains('.') && Regex(",\\d{1,2}$").containsMatchIn(t)) t.replace(',', '.')
        else t.replace(",", "")
    }

    /** Plain editable text: 1250.5 -> "1250.50", 99 -> "99". */
    fun toInput(money: Money): String {
        val bd = BigDecimal.valueOf(money.paise, 2)
        return if (money.paise % 100 == 0L) bd.setScale(0).toPlainString() else bd.toPlainString()
    }
}

/** Percent values (tax, discount) stored in basis points: 18 % = 1800. */
object Percent {
    fun parse(text: String): Int? = text.replace("%", "").trim().takeIf { it.isNotEmpty() }?.let {
        runCatching { BigDecimal(it).movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact() }.getOrNull()
    }

    fun format(bp: Int): String = BigDecimal.valueOf(bp.toLong(), 2).stripTrailingZeros().let {
        if (it.scale() < 0) it.setScale(0).toPlainString() else it.toPlainString()
    }
}
