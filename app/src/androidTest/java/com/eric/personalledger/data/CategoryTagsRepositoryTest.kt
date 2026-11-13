package com.eric.personalledger.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eric.personalledger.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CategoryTagsRepositoryTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun withDatabase(block: (String) -> Unit) {
        val name = "tags-test-${UUID.randomUUID()}.db"
        try { block(name) } finally { context.deleteDatabase(name) }
    }

    @Test fun tagsPersistInOrderUpdateAndDeleteWithoutDuplicatingTotals() = withDatabase { name ->
        val now = Instant.parse("2026-11-13T02:00:00Z")
        lateinit var entry: LedgerEntry
        SqliteLedgerRepository(context, name).use { repo ->
            assertTrue(repo.accounts().map { it.name }.containsAll(listOf("微信", "支付宝", "淘宝", "京东", "现金", "银行卡")))
            entry = LedgerEntry("tagged", EntryType.EXPENSE, 1250, "餐饮", repo.accounts().first().id,
                now.toEpochMilli(), additionalCategories = listOf("聚餐", "餐饮"))
            repo.insertEntry(entry)
            assertEquals(listOf("餐饮", "聚餐"), repo.allEntries().single().categoryTags)
            assertEquals(WidgetExpenseTotals(1250, 1250), repo.widgetExpenseTotals(now, ZoneId.of("Asia/Taipei")))
            assertTrue(repo.categoryTags().contains("聚餐"))
        }
        SqliteLedgerRepository(context, name).use { repo ->
            assertEquals(listOf("餐饮", "聚餐"), repo.entriesForMonth(YearMonth.of(2026, 11), ZoneId.of("UTC")).single().categoryTags)
            assertTrue(repo.updateEntry(entry.copy(category = "购物", additionalCategories = listOf("礼物"))))
            assertEquals(listOf("购物", "礼物"), repo.allEntries().single().categoryTags)
            assertEquals(listOf("礼物", "购物", "餐饮"), repo.commonCategoryTags(EntryType.EXPENSE))
            assertTrue(repo.deleteEntry(entry.id))
            assertTrue(repo.allEntries().isEmpty())
        }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT count(*) FROM entry_tags", null).use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        }
    }

    @Test fun failedWritesDoNotLeaveTagsOrEntriesAndConfirmationIsAtomic() = withDatabase { name ->
        SqliteLedgerRepository(context, name).use { repo ->
            val time = 1_767_000_000_000L
            val entry = LedgerEntry("valid", EntryType.EXPENSE, 100, "餐饮", repo.accounts().first().id, time,
                additionalCategories = listOf("自定义测试标签"))
            try { repo.insertEntry(entry.copy(accountId = Long.MAX_VALUE)); fail("Foreign key should reject the write") }
            catch (_: android.database.sqlite.SQLiteException) { }
            assertTrue(repo.allEntries().isEmpty())
            assertFalse(repo.categoryTags().contains("自定义测试标签"))
            assertFalse(repo.updateEntry(entry))
            assertFalse(repo.categoryTags().contains("自定义测试标签"))
            val id = NotificationIdentity.idFor("com.tencent.mm", "tags-candidate", time)
            repo.upsertCandidate(NotificationCandidateInput(id, "com.tencent.mm", 100, null, time))
            val confirmed = entry.copy(candidateId = id)
            assertEquals(CandidateConfirmationResult.CONFIRMED, repo.confirmCandidate(id, confirmed))
            assertEquals(CandidateConfirmationResult.ALREADY_PROCESSED, repo.confirmCandidate(id, confirmed.copy(id = "second")))
            assertEquals(confirmed, repo.allEntries().single())
        }
    }

    @Test fun versionOneMigrationPreservesIdsMoneyBudgetAndPendingCandidates() = withDatabase { name ->
        context.getDatabasePath(name).parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            val sql = InstrumentationRegistry.getInstrumentation().context.assets.open("ledger-v1.sql").bufferedReader().use { it.readText() }
            sql.split(';').map(String::trim).filter(String::isNotEmpty).forEach(db::execSQL)
            db.execSQL("INSERT INTO accounts(id,name,created_at) VALUES(17,'旧银行卡',123)")
            db.execSQL("INSERT INTO ledger_entries(id,type,amount_cents,category,account_id,occurred_at,memo) VALUES('old','EXPENSE',2345,'旧分类',17,1767000000000,'保留备注')")
            db.execSQL("INSERT INTO monthly_budgets VALUES('2026-11',100000)")
            db.execSQL("INSERT INTO notification_candidates VALUES(?, 'com.tencent.mm','微信支付',500,'其他',1767000000000,'PENDING',NULL)", arrayOf("a".repeat(64)))
            db.version = 1
        }
        SqliteLedgerRepository(context, name).use { repo ->
            val old = repo.allEntries().single()
            assertEquals("old", old.id)
            assertEquals(17L, old.accountId)
            assertEquals(2345L, old.amountCents)
            assertEquals(1_767_000_000_000L, old.occurredAtEpochMillis)
            assertEquals("保留备注", old.memo)
            assertEquals(listOf("旧分类"), old.categoryTags)
            assertEquals(100000L, repo.monthlyBudget(YearMonth.of(2026, 11)))
            assertEquals(1, repo.pendingCandidates().size)
            assertEquals("旧银行卡", repo.accounts().first { it.id == 17L }.name)
            assertTrue(repo.categoryTags().contains("旧分类"))
            assertTrue(repo.updateEntry(old.copy(additionalCategories = listOf("新标签"))))
        }
        SqliteLedgerRepository(context, name).use { repo ->
            assertEquals(listOf("旧分类", "新标签"), repo.allEntries().single().categoryTags)
            assertEquals(7, repo.accounts().size)
        }
    }
}
