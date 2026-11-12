package com.eric.personalledger.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

class LedgerRulesTest {
    @Test
    fun parsesYuanIntoExactMinorUnitsAndRejectsAmbiguousInput() {
        assertEquals(10L, MoneyInput.parseCents("0.10"))
        assertEquals(20L, MoneyInput.parseCents("0.20"))
        assertEquals(30L, MoneyInput.parseCents("0.30"))
        assertEquals(1_250L, MoneyInput.parseCents("12.5"))
        assertEquals("0.30", MoneyInput.formatCents(30L))
        assertEquals("12.50", MoneyInput.formatCents(1_250L))
        assertEquals("-0.10", MoneyInput.formatCents(-10L))
        assertNull(MoneyInput.parseCents(""))
        assertNull(MoneyInput.parseCents("0"))
        assertNull(MoneyInput.parseCents("-1.00"))
        assertNull(MoneyInput.parseCents("1.001"))
        assertNull(MoneyInput.parseCents("999999999999999999999.99"))
    }

    @Test
    fun summaryIncludesOnlyIncomeAndExpenseAndKeepsCentsExact() {
        val entries = listOf(
            entry("bread", EntryType.EXPENSE, 10L),
            entry("tea", EntryType.EXPENSE, 20L),
            entry("salary", EntryType.INCOME, 100L),
            entry("transfer", EntryType.TRANSFER, 50_000L, targetAccountId = 2L),
        )

        val summary = LedgerRules.summarize(entries)

        assertEquals(30L, summary.expenseCents)
        assertEquals(100L, summary.incomeCents)
        assertEquals(70L, summary.balanceCents)
    }

    @Test
    fun monthSelectionUsesLocalCalendarBoundaries() {
        val zone = ZoneId.of("Asia/Taipei")
        val firstDay = Instant.parse("2025-01-01T00:00:00Z").toEpochMilli()
        val lastDay = Instant.parse("2025-01-31T15:59:59.999Z").toEpochMilli()
        val nextMonth = Instant.parse("2025-01-31T16:00:00Z").toEpochMilli()
        val entries = listOf(
            entry("first", EntryType.EXPENSE, 1L, firstDay),
            entry("last", EntryType.EXPENSE, 2L, lastDay),
            entry("next", EntryType.EXPENSE, 4L, nextMonth),
        )

        val selected = LedgerRules.entriesInMonth(entries, YearMonth.of(2025, 1), zone)

        assertEquals(listOf("first", "last"), selected.map { it.id })
    }

    @Test
    fun transferMovesValueBetweenAccountsWithoutChangingIncomeOrExpense() {
        val entries = listOf(
            entry("expense", EntryType.EXPENSE, 10_000L),
            entry("income", EntryType.INCOME, 50_000L),
            entry("transfer", EntryType.TRANSFER, 20_000L, targetAccountId = 2L),
        )

        assertEquals(20_000L, LedgerRules.accountBalanceCents(entries, 1L))
        assertEquals(20_000L, LedgerRules.accountBalanceCents(entries, 2L))
        assertEquals(10_000L, LedgerRules.summarize(entries).expenseCents)
        assertEquals(50_000L, LedgerRules.summarize(entries).incomeCents)
    }

    private fun entry(
        id: String,
        type: EntryType,
        cents: Long,
        occurredAt: Long = 1L,
        targetAccountId: Long? = null,
    ) = LedgerEntry(
        id = id,
        type = type,
        amountCents = cents,
        category = "其他",
        accountId = 1L,
        occurredAtEpochMillis = occurredAt,
        transferTargetAccountId = targetAccountId,
    )
}

class NotificationParserTest {
    @Test
    fun identifiesOnlyClearPurchasesFromWhitelistedPackages() {
        val result = NotificationParser.parse(
            packageName = "com.tencent.mm",
            text = "微信支付凭证：支付成功，金额 ¥28.00",
        )

        assertTrue(result is NotificationParseResult.Candidate)
        assertEquals(2_800L, (result as NotificationParseResult.Candidate).amountCents)
    }

    @Test
    fun uncertainOrNonPurchaseNotificationsNeverBecomeReadyToPost() {
        val multiAmount = NotificationParser.parse(
            packageName = "com.eg.android.AlipayGphone",
            text = "付款成功，金额 ¥28.00，优惠 ¥5.00",
        )
        val hiddenAmount = NotificationParser.parse(
            packageName = "com.tencent.mm",
            text = "付款成功，金额已隐藏",
        )
        val refund = NotificationParser.parse(
            packageName = "com.tencent.mm",
            text = "退款成功 ¥28.00",
        )
        val transfer = NotificationParser.parse(
            packageName = "com.tencent.mm",
            text = "转账成功 ¥28.00",
        )
        val untrusted = NotificationParser.parse(
            packageName = "com.example.shop",
            text = "支付成功 ¥28.00",
        )

        assertTrue(multiAmount is NotificationParseResult.NeedsReview)
        assertTrue(hiddenAmount is NotificationParseResult.NeedsReview)
        assertEquals(NotificationParseResult.NotRelevant, refund)
        assertEquals(NotificationParseResult.NotRelevant, transfer)
        assertEquals(NotificationParseResult.NotRelevant, untrusted)
    }

    @Test
    fun rejectsTruncatedOverPrecisionAndMalformedCurrencyAmounts() {
        val overPrecisionWithSymbol = NotificationParser.parse(
            "com.tencent.mm",
            "支付成功 ¥28.001",
        )
        val overPrecisionWithUnit = NotificationParser.parse(
            "com.tencent.mm",
            "支付成功 28.001元",
        )
        val malformedGrouping = NotificationParser.parse(
            "com.tencent.mm",
            "支付成功 ¥12,34.50",
        )
        val invalidSuffix = NotificationParser.parse(
            "com.tencent.mm",
            "支付成功 ¥28.00abc",
        )
        val repeatedDecimal = NotificationParser.parse(
            "com.tencent.mm",
            "付款成功 28..50元",
        )
        val scientificNotation = NotificationParser.parse(
            "com.tencent.mm",
            "付款成功 1e3元",
        )
        val negativeAmount = NotificationParser.parse(
            "com.tencent.mm",
            "付款成功 -28元",
        )

        assertEquals(NotificationParseResult.NeedsReview(ReviewReason.INVALID_AMOUNT), overPrecisionWithSymbol)
        assertEquals(NotificationParseResult.NeedsReview(ReviewReason.INVALID_AMOUNT), overPrecisionWithUnit)
        assertEquals(NotificationParseResult.NeedsReview(ReviewReason.INVALID_AMOUNT), malformedGrouping)
        assertEquals(NotificationParseResult.NeedsReview(ReviewReason.INVALID_AMOUNT), invalidSuffix)
        assertEquals(NotificationParseResult.NeedsReview(ReviewReason.INVALID_AMOUNT), repeatedDecimal)
        assertEquals(NotificationParseResult.NeedsReview(ReviewReason.INVALID_AMOUNT), scientificNotation)
        assertEquals(NotificationParseResult.NeedsReview(ReviewReason.INVALID_AMOUNT), negativeAmount)
    }

    @Test
    fun acceptsCompleteDecimalAndWellGroupedAmounts() {
        val decimal = NotificationParser.parse("com.tencent.mm", "支付成功 ¥28.00")
        val grouped = NotificationParser.parse("com.eg.android.AlipayGphone", "付款成功 RMB 1,234.50")

        assertEquals(NotificationParseResult.Candidate(2_800L), decimal)
        assertEquals(NotificationParseResult.Candidate(123_450L), grouped)
    }

    @Test
    fun notificationIdentityIsStableForAnEventAndSeparatesSameKeyEvents() {
        val original = NotificationIdentity.idFor("com.tencent.mm", "0|com.tencent.mm|77|null|1000", 1_767_000_000_000L)
        val update = NotificationIdentity.idFor("com.tencent.mm", "0|com.tencent.mm|77|null|1000", 1_767_000_000_000L)
        val nextPayment = NotificationIdentity.idFor("com.tencent.mm", "0|com.tencent.mm|77|null|1000", 1_767_000_001_000L)

        assertEquals(original, update)
        assertTrue(original != nextPayment)
    }
}
