package online.draran.billing.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TaxEngineTest {

    private fun rs(r: Long) = Money.rupees(r)

    @Test fun exclusiveIntraStateSplitsCgstSgst() {
        // 2 x ₹500 at 18 %: taxable 1000, tax 180 -> CGST 90 + SGST 90
        val l = TaxEngine.line(LineInput(Qty.of(2), rs(500), taxRateBp = 1800), interState = false)
        assertEquals(rs(1000), l.taxable)
        assertEquals(rs(90), l.cgst)
        assertEquals(rs(90), l.sgst)
        assertEquals(Money.ZERO, l.igst)
        assertEquals(rs(1180), l.total)
    }

    @Test fun interStateUsesIgst() {
        val l = TaxEngine.line(LineInput(Qty.of(1), rs(1000), taxRateBp = 1200), interState = true)
        assertEquals(rs(120), l.igst)
        assertEquals(Money.ZERO, l.cgst)
        assertEquals(rs(1120), l.total)
    }

    @Test fun inclusivePriceBacksOutTax() {
        // ₹118 including 18 % -> taxable 100, tax 18
        val l = TaxEngine.line(LineInput(Qty.of(1), rs(118), taxRateBp = 1800, taxInclusive = true), false)
        assertEquals(rs(100), l.taxable)
        assertEquals(rs(18), l.tax)
        assertEquals(rs(118), l.total)
    }

    @Test fun oddTaxSplitsWithoutLosingPaise() {
        // taxable ₹10.01 at 5 % = 50.05 paise -> 50 paise; split 25 + 25
        val l = TaxEngine.line(LineInput(Qty.of(1), Money(1001), taxRateBp = 500), false)
        assertEquals(Money(50), l.tax)
        assertEquals(l.tax, l.cgst + l.sgst)
        val l2 = TaxEngine.line(LineInput(Qty.of(1), Money(1030), taxRateBp = 500), false)
        assertEquals(Money(52), l2.tax) // 51.5 -> 52
        assertEquals(Money(26), l2.cgst)
        assertEquals(Money(26), l2.sgst)
    }

    @Test fun discountReducesTaxableValue() {
        val l = TaxEngine.line(LineInput(Qty.of(1), rs(1000), discountBp = 1000, taxRateBp = 1800), false)
        assertEquals(rs(100), l.discount)
        assertEquals(rs(900), l.taxable)
        assertEquals(rs(162), l.tax)
    }

    @Test fun fractionalQuantity() {
        // 1.5 kg x ₹42.50 = ₹63.75
        val l = TaxEngine.line(LineInput(1500, Money(4250)), false)
        assertEquals(Money(6375), l.total)
    }

    @Test fun nonGstBillChargesNoTax() {
        val l = TaxEngine.line(LineInput(Qty.of(1), rs(100), taxRateBp = 1800), false, gstEnabled = false)
        assertEquals(Money.ZERO, l.tax)
        assertEquals(rs(100), l.total)
    }

    @Test fun billRoundsOffAndGroupsSlabs() {
        val totals = TaxEngine.bill(
            listOf(
                LineInput(Qty.of(1), Money(9999), taxRateBp = 500), // 99.99 + 5.00 = 104.99
                LineInput(Qty.of(1), Money(1050), taxRateBp = 1800), // 10.50 + 1.89 = 12.39
            ),
            interState = false,
        )
        assertEquals(Money(11738), totals.lines.sumOf { it.total.paise }.let(::Money))
        assertEquals(Money(11700), totals.total)
        assertEquals(Money(-38), totals.roundOff)
        assertEquals(listOf(500, 1800), totals.slabs.map { it.rateBp })
    }

    @Test fun divRoundIsSymmetric() {
        assertEquals(3, TaxEngine.divRound(5, 2))
        assertEquals(-3, TaxEngine.divRound(-5, 2))
        assertEquals(2, TaxEngine.divRound(4, 2))
    }

    @Test fun gstinValidation() {
        assertTrue(Gstin.isValid("27AAPFU0939F1ZV"))
        assertTrue(Gstin.isValid("29aagcb7383j1z4"))
        assertFalse(Gstin.isValid("27AAPFU0939F1ZA")) // wrong check char
        assertFalse(Gstin.isValid("99AAPFU0939F1ZV")) // unknown state
        assertFalse(Gstin.isValid("27AAPFU0939F1Z"))
        assertEquals("27", Gstin.stateCode("27AAPFU0939F1ZV"))
    }

    @Test fun quantityAndMoneyParsing() {
        assertEquals(1500L, Qty.parse("1.5"))
        assertEquals("1.5", Qty.format(1500))
        assertEquals("2", Qty.format(2000))
        assertEquals(Money(125050), MoneyParse.parse("1,250.5"))
        assertEquals("1250.50", MoneyParse.toInput(Money(125050)))
        assertEquals("99", MoneyParse.toInput(Money(9900)))
        assertEquals(1800, Percent.parse("18"))
        assertEquals("2.5", Percent.format(250))
        assertEquals(null, Qty.parse("abc"))
    }

    @Test fun financialYearStartsInApril() {
        assertEquals(LocalDate.of(2026, 4, 1), DateRange.financialYear(LocalDate.of(2026, 10, 3)).start)
        assertEquals(LocalDate.of(2025, 4, 1), DateRange.financialYear(LocalDate.of(2026, 2, 3)).start)
    }

    @Test fun docTypeEffects() {
        assertEquals(-1, DocType.SALE.stockSign)
        assertEquals(+1, DocType.SALE.ledgerSign)
        assertEquals(PaymentDirection.OUT, DocType.PURCHASE.paymentDirection)
        assertEquals(-1, PaymentDirection.IN.ledgerSign)
    }
}
