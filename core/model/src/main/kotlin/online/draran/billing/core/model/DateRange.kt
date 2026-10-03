package online.draran.billing.core.model

import java.time.LocalDate
import java.time.Month
import java.time.temporal.TemporalAdjusters

data class DateRange(val start: LocalDate, val end: LocalDate, val label: String) {
    operator fun contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(end)

    companion object {
        fun today(t: LocalDate) = DateRange(t, t, "Today")
        fun thisWeek(t: LocalDate) = DateRange(t.minusDays(6), t, "Last 7 days")
        fun thisMonth(t: LocalDate) = DateRange(t.withDayOfMonth(1), t, "This month")
        fun lastMonth(t: LocalDate): DateRange {
            val first = t.minusMonths(1).withDayOfMonth(1)
            return DateRange(first, first.with(TemporalAdjusters.lastDayOfMonth()), "Last month")
        }

        /** Indian financial year: 1 April to 31 March. */
        fun financialYear(t: LocalDate): DateRange {
            val startYear = if (t.month >= Month.APRIL) t.year else t.year - 1
            return DateRange(LocalDate.of(startYear, 4, 1), t, "This FY ${startYear % 100}-${(startYear + 1) % 100}")
        }

        fun presets(t: LocalDate) = listOf(today(t), thisWeek(t), thisMonth(t), lastMonth(t), financialYear(t))
    }
}
