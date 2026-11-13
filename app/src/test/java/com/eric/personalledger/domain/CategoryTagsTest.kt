package com.eric.personalledger.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class CategoryTagsTest {
    @Test fun tagsAreTrimmedAndDeduplicatedInSelectionOrder() {
        assertEquals(listOf("餐饮", "聚餐"), CategoryTags.normalize(listOf(" 餐饮 ", "聚餐", "餐饮")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptySelectionIsRejected() { CategoryTags.normalize(emptyList()) }

    @Test(expected = IllegalArgumentException::class)
    fun blankTagIsRejected() { CategoryTags.normalize(listOf(" ")) }

    @Test(expected = IllegalArgumentException::class)
    fun overlongTagIsRejected() { CategoryTags.normalize(listOf("字".repeat(41))) }

    @Test fun multipleTagsOverlapInTagStatisticsButNeverInTotalOrWidget() {
        val now = Instant.parse("2026-11-13T02:00:00Z")
        val entry = LedgerEntry("tagged", EntryType.EXPENSE, 1250, "餐饮", 1, now.toEpochMilli(),
            additionalCategories = listOf("聚餐", "餐饮"))
        assertEquals(listOf("餐饮", "聚餐"), entry.categoryTags)
        assertEquals(mapOf("餐饮" to 1250L, "聚餐" to 1250L),
            LedgerRules.categoryTotals(listOf(entry), EntryType.EXPENSE).associate { it.category to it.amountCents })
        assertEquals(1250L, LedgerRules.summarize(listOf(entry)).expenseCents)
        assertEquals(WidgetExpenseTotals(1250, 1250),
            WidgetExpenseTotals.calculate(listOf(entry), now, ZoneId.of("Asia/Taipei")))
    }

    @Test fun commonTagsUseFrequencyThenRecencyAndFillThreeDefaults() {
        val old = LedgerEntry("old", EntryType.EXPENSE, 100, "聚餐", 1, 100, additionalCategories = listOf("餐饮"))
        val recent = old.copy(id = "recent", category = "咖啡", additionalCategories = listOf("聚餐"), occurredAtEpochMillis = 200)
        val income = old.copy(id = "income", type = EntryType.INCOME, category = "工资", additionalCategories = emptyList())
        assertEquals(listOf("聚餐", "咖啡", "餐饮"), CategoryTags.common(listOf(old, recent, income), EntryType.EXPENSE))
        assertEquals(listOf("工资", "奖金", "理财"), CategoryTags.common(emptyList(), EntryType.INCOME))
        assertEquals(3, CategoryTags.common(emptyList(), EntryType.TRANSFER).size)
    }
}
