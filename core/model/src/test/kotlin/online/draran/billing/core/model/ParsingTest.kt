package online.draran.billing.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParsingTest {

    @Test fun indianGroupingIsRead() {
        assertEquals(Money.rupees(100_000), MoneyParse.parse("1,00,000"))
        assertEquals(Money(10_000_050), MoneyParse.parse("₹ 1,00,000.5"))
        assertEquals(Money.rupees(1_000), MoneyParse.parse("1,000"))
    }

    @Test fun decimalCommaIsRead() {
        // Phones set to some languages type a comma for the decimal point
        assertEquals(Money(1_250), MoneyParse.parse("12,50"))
        assertEquals(Money(1_250), MoneyParse.parse("12,5"))
        assertEquals(Qty.of(1) + 500, Qty.parse("1,5"))
    }

    @Test fun thousandsInQuantities() {
        assertEquals(Qty.of(1_000), Qty.parse("1,000"))
        assertEquals(150, Percent.parse("1,5 %"))
        assertEquals(1_800, Percent.parse("18%"))
    }

    @Test fun rubbishIsRejected() {
        assertNull(MoneyParse.parse(""))
        assertNull(MoneyParse.parse("abc"))
        assertNull(Qty.parse(" "))
    }
}
