package com.eric.personalledger.domain

import java.util.Locale
import java.math.BigInteger

enum class EntryType {
    EXPENSE,
    INCOME,
    TRANSFER,
}

data class LedgerAccount(
    val id: Long,
    val name: String,
)

data class LedgerEntry(
    val id: String,
    val type: EntryType,
    val amountCents: Long,
    val category: String,
    val accountId: Long,
    val occurredAtEpochMillis: Long,
    val memo: String = "",
    val transferTargetAccountId: Long? = null,
    val candidateId: String? = null,
    val additionalCategories: List<String> = emptyList(),
) {
    val categoryTags: List<String> get() = CategoryTags.normalize(listOf(category) + additionalCategories)
}

data class MonthlySummary(
    val expenseCents: Long,
    val incomeCents: Long,
) {
    val balanceCents: Long get() = Math.subtractExact(incomeCents, expenseCents)
}

data class MonthlyCategoryTotal(
    val category: String,
    val amountCents: Long,
)

object MoneyInput {
    private val plainAmount = Regex("^[0-9]+(?:\\.[0-9]{1,2})?$")
    private val groupedAmount = Regex("^[0-9]{1,3}(?:,[0-9]{3})+(?:\\.[0-9]{1,2})?$")

    /** Returns integer fen, or null for zero, malformed, over-precision, or overflow input. */
    fun parseCents(input: String): Long? {
        val trimmed = input.trim()
            .removePrefix("¥")
            .removePrefix("￥")
            .trim()
        if (trimmed.isEmpty() || (!plainAmount.matches(trimmed) && !groupedAmount.matches(trimmed))) {
            return null
        }

        val normalized = trimmed.replace(",", "")
        val parts = normalized.split('.', limit = 2)
        val yuan = parts[0].toLongOrNull() ?: return null
        val fractionalCents = parts.getOrNull(1)?.padEnd(2, '0')?.toLongOrNull() ?: 0L
        val cents = try {
            Math.addExact(Math.multiplyExact(yuan, 100L), fractionalCents)
        } catch (_: ArithmeticException) {
            return null
        }
        return cents.takeIf { it > 0L }
    }

    fun formatCents(cents: Long): String {
        val absolute = BigInteger.valueOf(cents).abs()
        val pieces = absolute.divideAndRemainder(BigInteger.valueOf(100L))
        val yuan = pieces[0].longValueExact()
        val fen = pieces[1].toInt()
        val sign = if (cents < 0L) "-" else ""
        return String.format(Locale.ROOT, "%s%,d.%02d", sign, yuan, fen)
    }
}
