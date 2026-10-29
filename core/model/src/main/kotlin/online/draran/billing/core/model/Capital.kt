package online.draran.billing.core.model

import java.time.LocalDate
import java.util.Locale

/**
 * Money put into the business or borrowed for it, and money taken back out. None of it is
 * income or expense, so it never enters sales, profit or party balances.
 */
enum class CapitalKind(val label: String, val moneyIn: Boolean, val isLoan: Boolean) {
    /** Borrowed from family, a friend or a bank; it has to be paid back. */
    LOAN_RECEIVED("Loan received", moneyIn = true, isLoan = true),
    LOAN_REPAID("Loan repaid", moneyIn = false, isLoan = true),

    /** The owner's own money put into the business; it is not paid back. */
    OWNER_IN("Own money added", moneyIn = true, isLoan = false),

    /** Money the owner takes out of the business for personal use. */
    OWNER_OUT("Money taken out", moneyIn = false, isLoan = false),
}

data class CapitalEntry(
    val id: Long = 0,
    val kind: CapitalKind,
    /** The lender for a loan; the owner or partner (optional) for own money. */
    val person: String,
    val date: LocalDate,
    val amount: Money,
    val mode: PaymentMode = PaymentMode.CASH,
    val note: String = "",
)

/** What one lender has lent and what has been paid back so far. */
data class LoanBalance(
    val lender: String,
    val received: Money,
    val repaid: Money,
    val firstDate: LocalDate,
    val lastDate: LocalDate,
) {
    val key: String get() = Capital.key(lender)
    val outstanding: Money get() = received - repaid
    val isCleared: Boolean get() = outstanding.paise <= 0
}

data class CapitalSummary(
    val loans: List<LoanBalance> = emptyList(),
    val ownIn: Money = Money.ZERO,
    val ownOut: Money = Money.ZERO,
) {
    /** Loans still to be paid back, all lenders together. */
    val toRepay: Money get() = loans.map { it.outstanding }.filter { !it.isNegative }.sum()
    val loansReceived: Money get() = loans.map { it.received }.sum()
    val loansRepaid: Money get() = loans.map { it.repaid }.sum()

    /** Own money put in less money taken out. It can be negative when more was taken out. */
    val ownCapital: Money get() = ownIn - ownOut
}

object Capital {
    /** The name as stored: trimmed, with single spaces. */
    fun cleanName(name: String): String = name.trim().replace(Regex("\\s+"), " ")

    /** Lenders are matched without regard to case or extra spaces, so "Father" and "father " are one lender. */
    fun key(name: String): String = cleanName(name).lowercase(Locale.ROOT)

    /**
     * Balances per lender (loans still owed first, then the largest) and the owner's capital.
     * A lender keeps the spelling of their first entry.
     */
    fun summarise(entries: List<CapitalEntry>): CapitalSummary {
        val loans = entries.filter { it.kind.isLoan && cleanName(it.person).isNotEmpty() }
            .sortedWith(compareBy({ it.date }, { it.id }))
            .groupBy { key(it.person) }
            .map { (_, list) ->
                LoanBalance(
                    lender = cleanName(list.first().person),
                    received = list.filter { it.kind == CapitalKind.LOAN_RECEIVED }.map { it.amount }.sum(),
                    repaid = list.filter { it.kind == CapitalKind.LOAN_REPAID }.map { it.amount }.sum(),
                    firstDate = list.first().date,
                    lastDate = list.last().date,
                )
            }
            .sortedWith(compareBy<LoanBalance> { it.isCleared }.thenByDescending { it.outstanding.paise }.thenBy { it.key })
        return CapitalSummary(
            loans = loans,
            ownIn = entries.filter { it.kind == CapitalKind.OWNER_IN }.map { it.amount }.sum(),
            ownOut = entries.filter { it.kind == CapitalKind.OWNER_OUT }.map { it.amount }.sum(),
        )
    }
}
