package online.draran.billing.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CapitalTest {
    private val day = LocalDate.of(2026, 10, 1)
    private fun entry(kind: CapitalKind, person: String, rupees: Long, days: Long = 0, id: Long = 0) =
        CapitalEntry(id = id, kind = kind, person = person, date = day.plusDays(days), amount = Money.rupees(rupees))

    @Test fun loanFromFamilyIsTrackedUntilRepaid() {
        val s = Capital.summarise(
            listOf(
                entry(CapitalKind.LOAN_RECEIVED, "Father", 1_000),
                entry(CapitalKind.LOAN_REPAID, "Father", 400, days = 30),
            ),
        )
        val father = s.loans.single()
        assertEquals("Father", father.lender)
        assertEquals(Money.rupees(1_000), father.received)
        assertEquals(Money.rupees(400), father.repaid)
        assertEquals(Money.rupees(600), father.outstanding)
        assertFalse(father.isCleared)
        assertEquals(Money.rupees(600), s.toRepay)
        assertEquals(Money.ZERO, s.ownCapital)
    }

    @Test fun lenderNamesMatchWithoutCaseOrExtraSpaces() {
        val s = Capital.summarise(
            listOf(
                entry(CapitalKind.LOAN_RECEIVED, "Ravi  Kumar", 500),
                entry(CapitalKind.LOAN_RECEIVED, " ravi kumar ", 300, days = 1),
                entry(CapitalKind.LOAN_REPAID, "RAVI KUMAR", 800, days = 2),
            ),
        )
        assertEquals(1, s.loans.size)
        assertEquals("Ravi Kumar", s.loans.single().lender)
        assertTrue(s.loans.single().isCleared)
        assertEquals(Money.ZERO, s.toRepay)
    }

    @Test fun ownMoneyIsCapitalNotALoan() {
        val s = Capital.summarise(
            listOf(
                entry(CapitalKind.OWNER_IN, "", 5_000),
                entry(CapitalKind.OWNER_OUT, "", 1_200, days = 5),
                entry(CapitalKind.LOAN_RECEIVED, "Bank", 20_000),
            ),
        )
        assertEquals(Money.rupees(3_800), s.ownCapital)
        assertEquals(Money.rupees(20_000), s.toRepay)
        assertEquals(listOf("Bank"), s.loans.map { it.lender })
    }

    @Test fun loansStillOwedComeFirst() {
        val s = Capital.summarise(
            listOf(
                entry(CapitalKind.LOAN_RECEIVED, "Uncle", 100),
                entry(CapitalKind.LOAN_REPAID, "Uncle", 100, days = 1),
                entry(CapitalKind.LOAN_RECEIVED, "Sister", 200),
                entry(CapitalKind.LOAN_RECEIVED, "Bank", 900),
            ),
        )
        assertEquals(listOf("Bank", "Sister", "Uncle"), s.loans.map { it.lender })
    }
}
