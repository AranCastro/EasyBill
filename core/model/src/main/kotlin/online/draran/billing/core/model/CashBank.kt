package online.draran.billing.core.model

import java.time.LocalDate

/** Where money sits: cash in the drawer, or the bank (UPI, card, bank transfer and cheque all land there). */
enum class MoneyAccount(val label: String) {
    CASH("Cash in hand"),
    BANK("Bank and UPI"),
}

val PaymentMode.account: MoneyAccount get() = if (this == PaymentMode.CASH) MoneyAccount.CASH else MoneyAccount.BANK

/** Entries that only move money between cash and bank, or correct the cash after counting it. */
enum class CashMoveKind(val label: String) {
    DEPOSIT("Cash deposited in bank"),
    WITHDRAWAL("Cash withdrawn from bank"),

    /** The owner counted the drawer; the amount is the shortage (negative) or excess (positive). */
    COUNT("Cash count"),
}

/** The starting point: cash and bank balances on a date. Entries before that date are not counted. */
data class CashSetup(val startDate: LocalDate, val cash: Money, val bank: Money)

data class CashMove(
    val id: Long = 0,
    val kind: CashMoveKind,
    val date: LocalDate,
    /** Deposits and withdrawals: the amount moved. Counts: counted minus expected. */
    val amount: Money,
    /** Counts only: the cash actually found. */
    val counted: Money? = null,
    val note: String = "",
)

enum class MovementSource { PAYMENT, EXPENSE, CAPITAL, MOVE }

/** One change to cash or bank, worked out from an entry the user already made. */
data class CashMovement(
    val source: MovementSource,
    val id: Long,
    /** The bill a payment was taken with, if any. */
    val billId: Long?,
    val date: LocalDate,
    val account: MoneyAccount,
    /** Positive adds to the balance, negative takes from it. */
    val amount: Money,
    val title: String,
    val detail: String,
    val mode: PaymentMode?,
    val createdAt: Long,
)

data class CashBookLine(val movement: CashMovement, val balance: Money)

data class CashBalances(val setup: CashSetup?, val cash: Money, val bank: Money)

object CashBook {
    /** Movements on or after the opening date (all of them before any opening is set), oldest first. */
    fun counted(setup: CashSetup?, movements: List<CashMovement>): List<CashMovement> =
        movements.filter { setup == null || !it.date.isBefore(setup.startDate) }
            .sortedWith(compareBy({ it.date }, { it.createdAt }, { it.id }))

    fun opening(setup: CashSetup?, account: MoneyAccount): Money = when {
        setup == null -> Money.ZERO
        account == MoneyAccount.CASH -> setup.cash
        else -> setup.bank
    }

    /** Running balance of one account, oldest first. */
    fun lines(setup: CashSetup?, movements: List<CashMovement>, account: MoneyAccount): List<CashBookLine> {
        var balance = opening(setup, account)
        return counted(setup, movements).filter { it.account == account }.map { m ->
            balance += m.amount
            CashBookLine(m, balance)
        }
    }

    /** Balance at the end of [date] (today's entries included). */
    fun balanceAsOf(setup: CashSetup?, movements: List<CashMovement>, account: MoneyAccount, date: LocalDate): Money =
        opening(setup, account) + counted(setup, movements).filter { it.account == account && !it.date.isAfter(date) }.map { it.amount }.sum()

    fun balances(setup: CashSetup?, movements: List<CashMovement>): CashBalances {
        val counted = counted(setup, movements)
        fun total(a: MoneyAccount) = opening(setup, a) + counted.filter { it.account == a }.map { it.amount }.sum()
        return CashBalances(setup, total(MoneyAccount.CASH), total(MoneyAccount.BANK))
    }
}
