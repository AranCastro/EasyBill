package online.draran.billing.core.common

import online.draran.billing.core.model.Money
import org.junit.Assert.assertEquals
import org.junit.Test

class AmountInWordsTest {

    @Test fun smallNumbers() {
        assertEquals("Zero", AmountInWords.numberToWords(0))
        assertEquals("Seven", AmountInWords.numberToWords(7))
        assertEquals("Nineteen", AmountInWords.numberToWords(19))
        assertEquals("Forty", AmountInWords.numberToWords(40))
        assertEquals("Ninety-Nine", AmountInWords.numberToWords(99))
        assertEquals("One Hundred Five", AmountInWords.numberToWords(105))
    }

    @Test fun indianGroups() {
        assertEquals("One Thousand", AmountInWords.numberToWords(1_000))
        assertEquals("One Lakh Twenty-Five Thousand Fifty", AmountInWords.numberToWords(1_25_050))
        assertEquals("Ten Crore", AmountInWords.numberToWords(10_00_00_000))
        assertEquals(
            "One Hundred Twenty-Three Crore Forty-Five Lakh Sixty-Seven Thousand Eight Hundred Ninety",
            AmountInWords.numberToWords(1_23_45_67_890),
        )
    }

    @Test fun formatsInvoiceAmount() {
        assertEquals(
            "Rupees One Lakh Twenty-Five Thousand Fifty and Seventy-Five Paise Only",
            AmountInWords.format(Money(1_25_050_75)),
        )
        assertEquals("Rupees Zero Only", AmountInWords.format(Money.ZERO))
        assertEquals("Rupees Five Hundred Only", AmountInWords.format(Money.rupees(500)))
    }
}
