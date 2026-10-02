package online.draran.billing.core.common

import online.draran.billing.core.model.Money
import kotlin.math.abs

/**
 * Formatting helpers for Indian currency.
 *
 * Implemented by hand (not java.text.NumberFormat) because the JDK formatter
 * does not apply the Indian 3-2-2 digit grouping (1,23,45,678) consistently
 * across Android versions and the JVM used for unit tests.
 */
object IndianFormat {

    const val RUPEE = "₹"

    /** Groups digits the Indian way: 12345678 -> "1,23,45,678". */
    fun groupDigits(value: Long): String {
        val digits = abs(value).toString()
        if (digits.length <= 3) return if (value < 0) "-$digits" else digits
        val lastThree = digits.takeLast(3)
        val rest = digits.dropLast(3)
        val grouped = rest.reversed().chunked(2).joinToString(",").reversed()
        val result = "$grouped,$lastThree"
        return if (value < 0) "-$result" else result
    }

    /**
     * Formats money as "₹1,23,456.50".
     * @param showPaise when false, rounds to whole rupees: "₹1,23,457".
     */
    fun rupees(money: Money, showPaise: Boolean = true, withSymbol: Boolean = true): String {
        val paise = money.paise
        val sign = if (paise < 0) "-" else ""
        val absPaise = abs(paise)
        val symbol = if (withSymbol) RUPEE else ""
        return if (showPaise) {
            val whole = absPaise / 100
            val fraction = (absPaise % 100).toString().padStart(2, '0')
            "$sign$symbol${groupDigits(whole)}.$fraction"
        } else {
            val whole = (absPaise + 50) / 100 // round half up
            "$sign$symbol${groupDigits(whole)}"
        }
    }

    /**
     * Compact form for tight spaces such as chart labels:
     * ₹950, ₹12.5K, ₹3.4L (lakh), ₹1.2Cr (crore).
     */
    fun compact(money: Money): String {
        val rupees = abs(money.paise) / 100.0
        val sign = if (money.isNegative) "-" else ""
        val text = when {
            rupees >= 1_00_00_000 -> trim(rupees / 1_00_00_000) + "Cr"
            rupees >= 1_00_000 -> trim(rupees / 1_00_000) + "L"
            rupees >= 1_000 -> trim(rupees / 1_000) + "K"
            else -> rupees.toLong().toString()
        }
        return "$sign$RUPEE$text"
    }

    private fun trim(value: Double): String {
        val rounded = Math.round(value * 10) / 10.0
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    }
}
