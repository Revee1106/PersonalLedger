package com.eric.personalledger.domain

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class WidgetExpenseTotals(val todayCents: Long, val monthCents: Long) {
    companion object {
        fun calculate(entries: Iterable<LedgerEntry>, now: Instant, zoneId: ZoneId): WidgetExpenseTotals {
            val today = now.atZone(zoneId).toLocalDate()
            val month = YearMonth.from(today)
            var daily = 0L
            var monthly = 0L
            entries.forEach { entry ->
                if (entry.type != EntryType.EXPENSE) return@forEach
                val date = Instant.ofEpochMilli(entry.occurredAtEpochMillis).atZone(zoneId).toLocalDate()
                if (YearMonth.from(date) != month) return@forEach
                require(entry.amountCents > 0L) { "Expense amounts must be positive." }
                monthly = Math.addExact(monthly, entry.amountCents)
                if (date == today) daily = Math.addExact(daily, entry.amountCents)
            }
            return WidgetExpenseTotals(daily, monthly)
        }

        fun nextDayStart(now: Instant, zoneId: ZoneId): Instant =
            now.atZone(zoneId).toLocalDate().plusDays(1).atStartOfDay(zoneId).toInstant()
    }
}
