package online.draran.billing.core.common

import online.draran.billing.core.model.Money
import kotlin.math.abs

/**
 * Converts an amount to words using the Indian numbering system
 * (thousand, lakh, crore), as printed on tax invoices.
 *
 * Example: 1,25,050.75 -> "Rupees One Lakh Twenty-Five Thousand Fifty and Seventy-Five Paise Only"
 */
object AmountInWords {

    private val ones = arrayOf(
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
        "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
        "Seventeen", "Eighteen", "Nineteen",
    )
    private val tens = arrayOf(
        "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety",
    )

    fun format(money: Money): String {
        val absPaise = abs(money.paise)
        val rupees = absPaise / 100
        val paise = (absPaise % 100).toInt()
        val prefix = if (money.isNegative) "Minus " else ""

        val rupeePart = if (rupees == 0L) "Zero" else numberToWords(rupees)
        val paisePart = if (paise > 0) " and ${belowHundred(paise)} Paise" else ""
        return "${prefix}Rupees $rupeePart$paisePart Only"
    }

    /** Words for a whole number using crore / lakh / thousand / hundred. */
    fun numberToWords(number: Long): String {
        require(number >= 0) { "number must be non-negative" }
        if (number == 0L) return "Zero"

        val parts = mutableListOf<String>()
        var n = number

        val crore = n / 1_00_00_000
        n %= 1_00_00_000
        if (crore > 0) parts += "${numberToWords(crore)} Crore"

        val lakh = (n / 1_00_000).toInt()
        n %= 1_00_000
        if (lakh > 0) parts += "${belowHundred(lakh)} Lakh"

        val thousand = (n / 1_000).toInt()
        n %= 1_000
        if (thousand > 0) parts += "${belowHundred(thousand)} Thousand"

        val hundred = (n / 100).toInt()
        n %= 100
        if (hundred > 0) parts += "${ones[hundred]} Hundred"

        if (n > 0) parts += belowHundred(n.toInt())
        return parts.joinToString(" ")
    }

    private fun belowHundred(n: Int): String = when {
        n < 20 -> ones[n]
        n % 10 == 0 -> tens[n / 10]
        else -> "${tens[n / 10]}-${ones[n % 10]}"
    }
}
