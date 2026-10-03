package online.draran.billing.core.model

/** One line as entered on a bill. */
data class LineInput(
    val qtyMilli: Long,
    /** Price per unit in paise, as typed (may include tax). */
    val rate: Money,
    /** Line discount in basis points of the gross amount. */
    val discountBp: Int = 0,
    val taxRateBp: Int = 0,
    /** True when [rate] already includes GST. */
    val taxInclusive: Boolean = false,
)

data class LineAmounts(
    val gross: Money,
    val discount: Money,
    val taxable: Money,
    val tax: Money,
    val cgst: Money,
    val sgst: Money,
    val igst: Money,
    val total: Money,
)

data class TaxSlab(val rateBp: Int, val taxable: Money, val cgst: Money, val sgst: Money, val igst: Money) {
    val tax: Money get() = cgst + sgst + igst
}

data class BillTotals(
    val lines: List<LineAmounts>,
    val subtotal: Money,
    val discount: Money,
    val taxable: Money,
    val cgst: Money,
    val sgst: Money,
    val igst: Money,
    val roundOff: Money,
    val total: Money,
    val slabs: List<TaxSlab>,
) {
    val tax: Money get() = cgst + sgst + igst
}

/**
 * GST calculation. All arithmetic is on whole paise with half-up rounding,
 * applied per line (as most billing software and GST portals expect).
 *
 * - Intra-state supply: tax is split equally into CGST and SGST.
 * - Inter-state supply: the full tax is IGST.
 * - When [gstEnabled] is false (non-GST shop / bill of supply), no tax is charged
 *   and tax-inclusive prices are used as-is.
 */
object TaxEngine {

    fun line(input: LineInput, interState: Boolean, gstEnabled: Boolean = true): LineAmounts {
        val gross = divRound(input.qtyMilli * input.rate.paise, Qty.ONE)
        val discount = divRound(gross * input.discountBp, 10_000)
        val net = gross - discount
        val rate = if (gstEnabled) input.taxRateBp else 0
        val taxable: Long
        val tax: Long
        if (rate == 0) {
            taxable = net
            tax = 0
        } else if (input.taxInclusive) {
            taxable = divRound(net * 10_000, 10_000L + rate)
            tax = net - taxable
        } else {
            taxable = net
            tax = divRound(net * rate, 10_000)
        }
        val (cgst, sgst, igst) = if (interState) {
            Triple(0L, 0L, tax)
        } else {
            val half = divRound(tax, 2)
            Triple(half, tax - half, 0L)
        }
        return LineAmounts(
            gross = Money(gross),
            discount = Money(discount),
            taxable = Money(taxable),
            tax = Money(tax),
            cgst = Money(cgst),
            sgst = Money(sgst),
            igst = Money(igst),
            total = Money(taxable + tax),
        )
    }

    fun bill(
        inputs: List<LineInput>,
        interState: Boolean,
        gstEnabled: Boolean = true,
        roundOff: Boolean = true,
    ): BillTotals {
        val lines = inputs.map { line(it, interState, gstEnabled) }
        val exact = lines.sumOf { it.total.paise }
        val rounded = if (roundOff) divRound(exact, 100) * 100 else exact
        val slabs = inputs.zip(lines)
            .groupBy { (input, _) -> if (gstEnabled) input.taxRateBp else 0 }
            .map { (rate, pairs) ->
                TaxSlab(
                    rateBp = rate,
                    taxable = Money(pairs.sumOf { it.second.taxable.paise }),
                    cgst = Money(pairs.sumOf { it.second.cgst.paise }),
                    sgst = Money(pairs.sumOf { it.second.sgst.paise }),
                    igst = Money(pairs.sumOf { it.second.igst.paise }),
                )
            }
            .sortedBy { it.rateBp }
        return BillTotals(
            lines = lines,
            subtotal = Money(lines.sumOf { it.gross.paise }),
            discount = Money(lines.sumOf { it.discount.paise }),
            taxable = Money(lines.sumOf { it.taxable.paise }),
            cgst = Money(lines.sumOf { it.cgst.paise }),
            sgst = Money(lines.sumOf { it.sgst.paise }),
            igst = Money(lines.sumOf { it.igst.paise }),
            roundOff = Money(rounded - exact),
            total = Money(rounded),
            slabs = slabs,
        )
    }

    /** Rounds a / b half away from zero. */
    fun divRound(a: Long, b: Long): Long {
        require(b > 0)
        return if (a >= 0) (a + b / 2) / b else -((-a + b / 2) / b)
    }
}
