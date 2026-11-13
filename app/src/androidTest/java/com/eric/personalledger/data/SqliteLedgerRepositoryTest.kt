package com.eric.personalledger.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eric.personalledger.domain.EntryType
import com.eric.personalledger.domain.LedgerEntry
import com.eric.personalledger.domain.LedgerRules
import com.eric.personalledger.domain.NotificationIdentity
import com.eric.personalledger.domain.ReviewReason
import com.eric.personalledger.domain.WidgetExpenseTotals
import java.time.Instant
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SqliteLedgerRepositoryTest {
    private val databaseName = "ledger-test-${UUID.randomUUID()}.db"
    private val context by lazy { ApplicationProvider.getApplicationContext<android.content.Context>() }
    private lateinit var repository: SqliteLedgerRepository

    @Before
    fun setUp() {
        repository = SqliteLedgerRepository(context, databaseName)
    }

    @After
    fun tearDown() {
        repository.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun firstOpenHasNoLedgerEntriesAndSavedEntriesSurviveReopen() {
        assertTrue(repository.allEntries().isEmpty())
        val account = repository.accounts().first()
        val entry = LedgerEntry(
            id = "persistent-entry",
            type = EntryType.EXPENSE,
            amountCents = 30L,
            category = "餐饮",
            accountId = account.id,
            occurredAtEpochMillis = 1_767_000_000_000L,
            memo = "午餐",
        )
        repository.insertEntry(entry)
        repository.close()

        repository = SqliteLedgerRepository(context, databaseName)

        assertEquals(listOf(entry), repository.allEntries())
    }

    @Test
    fun editingAndDeletingAnEntryChangesStoredTotals() {
        val account = repository.accounts().first()
        val original = LedgerEntry(
            id = "editable-entry",
            type = EntryType.EXPENSE,
            amountCents = 1_250L,
            category = "餐饮",
            accountId = account.id,
            occurredAtEpochMillis = 1_767_000_000_000L,
        )
        repository.insertEntry(original)
        assertEquals(1_250L, LedgerRules.summarize(repository.allEntries()).expenseCents)

        assertTrue(repository.updateEntry(original.copy(amountCents = 1_500L)))
        assertEquals(1_500L, LedgerRules.summarize(repository.allEntries()).expenseCents)

        assertTrue(repository.deleteEntry(original.id))
        assertTrue(repository.allEntries().isEmpty())
        assertFalse(repository.deleteEntry(original.id))
    }

    @Test
    fun transferMovesAccountBalancesButDoesNotChangeIncomeOrExpense() {
        val accounts = repository.accounts()
        val transfer = LedgerEntry(
            id = "transfer-entry",
            type = EntryType.TRANSFER,
            amountCents = 7_500L,
            category = "转账",
            accountId = accounts[0].id,
            transferTargetAccountId = accounts[1].id,
            occurredAtEpochMillis = 1_767_000_000_000L,
        )

        repository.insertEntry(transfer)

        assertEquals(-7_500L, LedgerRules.accountBalanceCents(repository.allEntries(), accounts[0].id))
        assertEquals(7_500L, LedgerRules.accountBalanceCents(repository.allEntries(), accounts[1].id))
        assertEquals(0L, LedgerRules.summarize(repository.allEntries()).expenseCents)
        assertEquals(0L, LedgerRules.summarize(repository.allEntries()).incomeCents)
    }

    @Test
    fun candidateUpdatesDeduplicateAndCanOnlyBeConfirmedOnce() {
        val account = repository.accounts().first()
        val notificationEventTime = 1_767_000_000_000L
        val candidateId = NotificationIdentity.idFor(
            "com.tencent.mm",
            "stable-notification-key",
            notificationEventTime,
        )
        val candidate = NotificationCandidateInput(
            id = candidateId,
            sourcePackage = "com.tencent.mm",
            amountCents = 2_800L,
            reviewReason = null,
            receivedAtEpochMillis = notificationEventTime,
        )
        repository.upsertCandidate(candidate)
        val latestUpdate = candidate.copy(
            amountCents = 3_000L,
            receivedAtEpochMillis = candidate.receivedAtEpochMillis + 100L,
        )
        repository.upsertCandidate(latestUpdate)
        repository.upsertCandidate(candidate.copy(amountCents = 2_900L))

        assertEquals(1, repository.pendingCandidates().size)
        assertEquals(3_000L, repository.pendingCandidates().single().amountCents)

        val confirmedEntry = LedgerEntry(
            id = "confirmed-entry",
            type = EntryType.EXPENSE,
            amountCents = 3_000L,
            category = "餐饮",
            accountId = account.id,
            occurredAtEpochMillis = latestUpdate.receivedAtEpochMillis,
            candidateId = candidateId,
        )
        assertEquals(
            CandidateConfirmationResult.CONFIRMED,
            repository.confirmCandidate(candidateId, confirmedEntry),
        )
        assertEquals(
            CandidateConfirmationResult.ALREADY_PROCESSED,
            repository.confirmCandidate(candidateId, confirmedEntry.copy(id = "second-entry")),
        )
        assertEquals(1, repository.allEntries().size)
        assertEquals(CandidateStatus.CONFIRMED, repository.candidate(candidateId)?.status)
    }

    @Test
    fun replayAfterReopenIsIdempotentAndSameKeyNewEventCanBeConfirmedSeparately() {
        val account = repository.accounts().first()
        val sourcePackage = "com.tencent.mm"
        val notificationKey = "reused-payment-slot"
        val firstEventTime = 1_767_000_000_000L
        val firstId = NotificationIdentity.idFor(sourcePackage, notificationKey, firstEventTime)
        val firstCandidate = NotificationCandidateInput(
            id = firstId,
            sourcePackage = sourcePackage,
            amountCents = 2_800L,
            reviewReason = null,
            receivedAtEpochMillis = firstEventTime,
        )
        repository.upsertCandidate(firstCandidate)
        assertEquals(
            CandidateConfirmationResult.CONFIRMED,
            repository.confirmCandidate(
                firstId,
                LedgerEntry(
                    id = "first-same-key-entry",
                    type = EntryType.EXPENSE,
                    amountCents = 2_800L,
                    category = "其他",
                    accountId = account.id,
                    occurredAtEpochMillis = firstEventTime,
                    candidateId = firstId,
                ),
            ),
        )

        repository.close()
        repository = SqliteLedgerRepository(context, databaseName)
        val replayed = repository.upsertCandidate(firstCandidate)
        assertEquals(CandidateStatus.CONFIRMED, replayed.status)
        assertEquals(1, repository.allEntries().size)

        val secondEventTime = firstEventTime + 1_000L
        val secondId = NotificationIdentity.idFor(sourcePackage, notificationKey, secondEventTime)
        val secondCandidate = firstCandidate.copy(id = secondId, receivedAtEpochMillis = secondEventTime)
        repository.upsertCandidate(secondCandidate)
        assertEquals(1, repository.pendingCandidates().size)
        assertEquals(2_800L, repository.pendingCandidates().single().amountCents)
        assertEquals(
            CandidateConfirmationResult.CONFIRMED,
            repository.confirmCandidate(
                secondId,
                LedgerEntry(
                    id = "second-same-key-entry",
                    type = EntryType.EXPENSE,
                    amountCents = 2_800L,
                    category = "其他",
                    accountId = account.id,
                    occurredAtEpochMillis = secondEventTime,
                    candidateId = secondId,
                ),
            ),
        )
        assertEquals(2, repository.allEntries().size)
    }

    @Test
    fun ignoredAndIncompleteCandidatesNeverEnterTheLedger() {
        val candidateId = NotificationIdentity.idFor(
            "com.eg.android.AlipayGphone",
            "hidden-notification",
            1_767_000_000_000L,
        )
        repository.upsertCandidate(
            NotificationCandidateInput(
                id = candidateId,
                sourcePackage = "com.eg.android.AlipayGphone",
                amountCents = null,
                reviewReason = ReviewReason.AMOUNT_HIDDEN_OR_MISSING,
                receivedAtEpochMillis = 1_767_000_000_000L,
            ),
        )

        assertNotNull(repository.candidate(candidateId))
        assertNull(repository.candidate(candidateId)?.amountCents)
        assertTrue(repository.ignoreCandidate(candidateId))
        assertFalse(repository.ignoreCandidate(candidateId))
        assertTrue(repository.allEntries().isEmpty())
        assertEquals(0, repository.pendingCandidates().size)
    }

    @Test fun widgetTotalsFollowSavedChangesAndExcludePendingPayments() {
        val now = Instant.parse("2026-11-13T02:00:00Z")
        val zone = ZoneId.of("Asia/Taipei")
        val accounts = repository.accounts()
        val expense = LedgerEntry(
            id = "widget-today", type = EntryType.EXPENSE, amountCents = 1_250,
            category = "其他", accountId = accounts.first().id, occurredAtEpochMillis = now.toEpochMilli(),
        )
        assertEquals(WidgetExpenseTotals(0, 0), repository.widgetExpenseTotals(now, zone))
        repository.insertEntry(expense)
        repository.insertEntry(expense.copy(id = "widget-yesterday", amountCents = 700,
            occurredAtEpochMillis = Instant.parse("2026-11-12T02:00:00Z").toEpochMilli()))
        repository.insertEntry(expense.copy(id = "widget-income", type = EntryType.INCOME, amountCents = 9_000))
        repository.insertEntry(expense.copy(id = "widget-transfer", type = EntryType.TRANSFER,
            transferTargetAccountId = accounts.last().id))
        assertEquals(WidgetExpenseTotals(1_250, 1_950), repository.widgetExpenseTotals(now, zone))
        repository.updateEntry(expense.copy(amountCents = 2_300))
        assertEquals(WidgetExpenseTotals(2_300, 3_000), repository.widgetExpenseTotals(now, zone))
        repository.deleteEntry(expense.id)
        assertEquals(WidgetExpenseTotals(0, 700), repository.widgetExpenseTotals(now, zone))
        val candidateId = NotificationIdentity.idFor("com.tencent.mm", "widget-test", now.toEpochMilli())
        repository.upsertCandidate(NotificationCandidateInput(candidateId, "com.tencent.mm", 350, null, now.toEpochMilli()))
        assertEquals(WidgetExpenseTotals(0, 700), repository.widgetExpenseTotals(now, zone))
        repository.confirmCandidate(candidateId, expense.copy(id = "widget-confirmed", amountCents = 350, candidateId = candidateId))
        assertEquals(WidgetExpenseTotals(350, 1_050), repository.widgetExpenseTotals(now, zone))
    }

    @Test
    fun monthlyBudgetPersistsAndCanBeCleared() {
        val month = YearMonth.of(2026, 11)
        repository.setMonthlyBudget(month, 50_000L)
        repository.close()
        repository = SqliteLedgerRepository(context, databaseName)

        assertEquals(50_000L, repository.monthlyBudget(month))
        repository.setMonthlyBudget(month, null)
        assertNull(repository.monthlyBudget(month))
    }
}
