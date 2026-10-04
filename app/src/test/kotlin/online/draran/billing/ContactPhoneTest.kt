package online.draran.billing

import online.draran.billing.core.designsystem.component.cleanPhone
import org.junit.Assert.assertEquals
import org.junit.Test

class ContactPhoneTest {
    @Test fun contactNumbersBecomeTenDigits() {
        assertEquals("9876543210", cleanPhone("+91 98765 43210"))
        assertEquals("9876543210", cleanPhone("098765 43210"))
        assertEquals("9876543210", cleanPhone("98765-43210"))
        assertEquals("9876543210", cleanPhone("(91) 9876543210"))
        assertEquals("9876543210", cleanPhone("9876543210"))
    }

    @Test fun otherCountriesKeepAllDigits() {
        assertEquals("447911123456", cleanPhone("+44 7911 123456"))
        assertEquals("", cleanPhone("no number"))
    }
}
