package online.draran.billing.core.common

import online.draran.billing.core.model.Money
import org.junit.Assert.assertEquals
import org.junit.Test

class IndianFormatTest {

    @Test fun groupsDigitsIndianStyle() {
        assertEquals("0", IndianFormat.groupDigits(0))
        assertEquals("999", IndianFormat.groupDigits(999))
        assertEquals("1,000", IndianFormat.groupDigits(1_000))
        assertEquals("12,345", IndianFormat.groupDigits(12_345))
        assertEquals("1,23,456", IndianFormat.groupDigits(1_23_456))
        assertEquals("12,34,56,789", IndianFormat.groupDigits(12_34_56_789))
        assertEquals("-1,00,000", IndianFormat.groupDigits(-1_00_000))
    }

    @Test fun formatsRupeesWithPaise() {
        assertEquals("₹0.00", IndianFormat.rupees(Money(0)))
        assertEquals("₹0.05", IndianFormat.rupees(Money(5)))
        assertEquals("₹1,23,456.50", IndianFormat.rupees(Money(1_23_456_50)))
        assertEquals("-₹250.00", IndianFormat.rupees(Money(-250_00)))
    }

    @Test fun formatsWholeRupeesRoundingHalfUp() {
        assertEquals("₹1,23,457", IndianFormat.rupees(Money(1_23_456_50), showPaise = false))
        assertEquals("₹1,23,456", IndianFormat.rupees(Money(1_23_456_49), showPaise = false))
    }

    @Test fun formatsCompactAmounts() {
        assertEquals("₹950", IndianFormat.compact(Money.rupees(950)))
        assertEquals("₹12.5K", IndianFormat.compact(Money.rupees(12_500)))
        assertEquals("₹3.4L", IndianFormat.compact(Money.rupees(3_40_000)))
        assertEquals("₹1.2Cr", IndianFormat.compact(Money.rupees(1_20_00_000)))
        assertEquals("₹2K", IndianFormat.compact(Money.rupees(2_000)))
    }
}
