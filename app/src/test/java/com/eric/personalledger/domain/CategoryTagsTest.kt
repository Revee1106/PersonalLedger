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

    @Test fun freshCatalogAndEveryEntryTypeHaveOnlyThreeDefaultTags() {
        val defaults = listOf("餐饮", "交通", "购物")
        assertEquals(defaults, CategoryTags.catalog)
        EntryType.entries.forEach { assertEquals(defaults, CategoryTags.defaults(it)) }
    }

    @Test fun mostRecentlySelectedNoncommonTagsLeadAndCommonPositionsStayStable() {
        assertEquals(listOf("咖啡", "聚餐", "餐饮", "交通", "购物"),
            CategoryTags.displayOrder(listOf("餐饮", "聚餐", "咖啡"), listOf("餐饮", "交通", "购物")))
    }

    @Test fun deselectedExtrasDisappearAndCommonTagsAreNeverDuplicated() {
        assertEquals(listOf("聚餐", "餐饮", "交通", "购物"),
            CategoryTags.displayOrder(listOf("餐饮", "聚餐", "餐饮"), listOf("餐饮", "交通", "购物")))
        assertEquals(listOf("餐饮", "交通", "购物"),
            CategoryTags.displayOrder(emptyList(), listOf("餐饮", "交通", "购物")))
    }

    @Test fun commonPreferencesCanBeEmptyAndNormalizeWithoutFrequencyReplacement() {
        assertEquals(emptyList<String>(), CategoryTags.normalizeCommon(emptyList()))
        assertEquals(listOf("聚餐", "餐饮"), CategoryTags.normalizeCommon(listOf(" 聚餐 ", "餐饮", "聚餐")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun commonPreferencesRejectMoreThanThreeUniqueTags() {
        CategoryTags.normalizeCommon(listOf("甲", "乙", "丙", "丁"))
    }
}
