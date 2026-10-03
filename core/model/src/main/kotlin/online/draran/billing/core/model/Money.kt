package online.draran.billing.core.model

/**
 * An amount of Indian Rupees stored as whole paise (1 rupee = 100 paise).
 *
 * Money is never stored as Double, so totals and tax splits do not drift
 * through floating-point rounding.
 */
@JvmInline
value class Money(val paise: Long) : Comparable<Money> {

    operator fun plus(other: Money) = Money(paise + other.paise)
    operator fun minus(other: Money) = Money(paise - other.paise)
    operator fun unaryMinus() = Money(-paise)
    override fun compareTo(other: Money) = paise.compareTo(other.paise)

    val isZero: Boolean get() = paise == 0L
    val isNegative: Boolean get() = paise < 0L

    companion object {
        val ZERO = Money(0)
        fun rupees(rupees: Long) = Money(rupees * 100)
    }
}

fun Iterable<Money>.sum(): Money = Money(sumOf { it.paise })

operator fun Money.times(factor: Int) = Money(paise * factor)

fun Money.abs() = if (paise < 0) Money(-paise) else this
