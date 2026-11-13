package com.eric.personalledger.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class WidgetExpenseTotalsTest {
    private val zone = ZoneId.of("Asia/Taipei")
    private val now = Instant.parse("2026-11-13T02:00:00Z")

    @Test fun countsSavedExpensesOnlyAndKeepsCentsExact() {
        val entries = listOf(
            entry("2026-11-13T00:00:00Z", 10),
            entry("2026-11-13T01:00:00Z", 20),
            entry("2026-11-01T00:00:00Z", 100),
            entry("2026-11-13T00:00:00Z", 900, EntryType.INCOME),
            entry("2026-11-13T00:00:00Z", 800, EntryType.TRANSFER),
        )
        assertEquals(WidgetExpenseTotals(30, 130), WidgetExpenseTotals.calculate(entries, now, zone))
    }

    @Test fun usesLocalMidnightAndHalfOpenMonthBoundaries() {
        val entries = listOf(
            entry("2026-10-31T15:59:59.999Z", 1),
            entry("2026-10-31T16:00:00Z", 2),
            entry("2026-11-12T15:59:59.999Z", 4),
            entry("2026-11-12T16:00:00Z", 8),
            entry("2026-11-13T15:59:59.999Z", 16),
            entry("2026-11-13T16:00:00Z", 32),
            entry("2026-11-30T15:59:59.999Z", 64),
            entry("2026-11-30T16:00:00Z", 128),
        )
        assertEquals(WidgetExpenseTotals(24, 126), WidgetExpenseTotals.calculate(entries, now, zone))
    }

    @Test fun recomputesAfterYearAndMonthRollover() {
        val entries = listOf(entry("2026-12-31T15:59:59Z", 100), entry("2026-12-31T16:00:00Z", 200))
        assertEquals(WidgetExpenseTotals(100, 100), WidgetExpenseTotals.calculate(entries, Instant.parse("2026-12-31T15:59:59Z"), zone))
        assertEquals(WidgetExpenseTotals(200, 200), WidgetExpenseTotals.calculate(entries, Instant.parse("2026-12-31T16:00:00Z"), zone))
    }

    @Test fun respectsTimeZoneChangesAndLeapDay() {
        val entries = listOf(entry("2024-02-29T16:00:00Z", 99))
        val time = Instant.parse("2024-02-29T16:30:00Z")
        assertEquals(WidgetExpenseTotals(99, 99), WidgetExpenseTotals.calculate(entries, time, zone))
        assertEquals(WidgetExpenseTotals(99, 99), WidgetExpenseTotals.calculate(entries, time, ZoneId.of("UTC")))
        assertEquals(Instant.parse("2024-03-01T00:00:00Z"), WidgetExpenseTotals.nextDayStart(time, ZoneId.of("UTC")))
        assertEquals(Instant.parse("2024-03-01T16:00:00Z"), WidgetExpenseTotals.nextDayStart(time, zone))
    }

    @Test fun nextRefreshUsesCalendarMidnightAcrossDstRatherThanTwentyFourHours() {
        val newYork = ZoneId.of("America/New_York")
        assertEquals(Instant.parse("2026-03-09T04:00:00Z"), WidgetExpenseTotals.nextDayStart(Instant.parse("2026-03-08T05:00:00Z"), newYork))
        assertEquals(Instant.parse("2026-11-02T05:00:00Z"), WidgetExpenseTotals.nextDayStart(Instant.parse("2026-11-01T04:00:00Z"), newYork))
        assertEquals(Instant.parse("2026-12-31T16:00:00Z"), WidgetExpenseTotals.nextDayStart(Instant.parse("2026-12-31T15:59:59Z"), zone))
    }

    @Test fun emptyLedgerHasZeroTotals() {
        assertEquals(WidgetExpenseTotals(0, 0), WidgetExpenseTotals.calculate(emptyList(), now, zone))
    }

    @Test(expected = ArithmeticException::class) fun rejectsOverflowInsteadOfShowingWrongMoney() {
        WidgetExpenseTotals.calculate(listOf(entry(now.toString(), Long.MAX_VALUE), entry(now.toString(), 1)), now, zone)
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidExpenseAmounts() {
        WidgetExpenseTotals.calculate(listOf(entry(now.toString(), -1)), now, zone)
    }

    private fun entry(time: String, cents: Long, type: EntryType = EntryType.EXPENSE) = LedgerEntry(
        id = "$time-$cents-$type", type = type, amountCents = cents, category = "其他", accountId = 1,
        occurredAtEpochMillis = Instant.parse(time).toEpochMilli(),
        transferTargetAccountId = if (type == EntryType.TRANSFER) 2 else null,
    )
}
