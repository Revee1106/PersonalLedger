package com.eric.personalledger.domain

import java.time.YearMonth
import java.time.ZoneId

object LedgerRules {
    fun summarize(entries: Iterable<LedgerEntry>): MonthlySummary {
        var expense = 0L
        var income = 0L
        entries.forEach { entry ->
            require(entry.amountCents > 0L) { "Ledger amounts must be positive." }
            when (entry.type) {
                EntryType.EXPENSE -> expense = Math.addExact(expense, entry.amountCents)
                EntryType.INCOME -> income = Math.addExact(income, entry.amountCents)
                EntryType.TRANSFER -> Unit
            }
        }
        return MonthlySummary(expenseCents = expense, incomeCents = income)
    }

    fun entriesInMonth(
        entries: Iterable<LedgerEntry>,
        month: YearMonth,
        zoneId: ZoneId,
    ): List<LedgerEntry> {
        val start = month.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return entries.filter { it.occurredAtEpochMillis >= start && it.occurredAtEpochMillis < end }
    }

    fun categoryTotals(entries: Iterable<LedgerEntry>, type: EntryType): List<MonthlyCategoryTotal> {
        require(type != EntryType.TRANSFER) { "Transfers do not have income or expense category totals." }
        val totals = linkedMapOf<String, Long>()
        entries.asSequence()
            .filter { it.type == type }
            .forEach { entry ->
                require(entry.amountCents > 0L) { "Ledger amounts must be positive." }
                totals[entry.category] = Math.addExact(totals[entry.category] ?: 0L, entry.amountCents)
            }
        return totals.map { (category, amount) -> MonthlyCategoryTotal(category, amount) }
            .sortedByDescending { it.amountCents }
    }

    fun accountBalanceCents(entries: Iterable<LedgerEntry>, accountId: Long): Long {
        var balance = 0L
        entries.forEach { entry ->
            require(entry.amountCents > 0L) { "Ledger amounts must be positive." }
            val change = when (entry.type) {
                EntryType.EXPENSE -> if (entry.accountId == accountId) -entry.amountCents else 0L
                EntryType.INCOME -> if (entry.accountId == accountId) entry.amountCents else 0L
                EntryType.TRANSFER -> when (accountId) {
                    entry.accountId -> -entry.amountCents
                    entry.transferTargetAccountId -> entry.amountCents
                    else -> 0L
                }
            }
            balance = Math.addExact(balance, change)
        }
        return balance
    }
}
