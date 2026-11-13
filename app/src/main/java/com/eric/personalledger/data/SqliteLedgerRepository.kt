package com.eric.personalledger.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import com.eric.personalledger.domain.EntryType
import com.eric.personalledger.domain.CategoryTags
import com.eric.personalledger.domain.LedgerAccount
import com.eric.personalledger.domain.LedgerEntry
import com.eric.personalledger.domain.LedgerRules
import com.eric.personalledger.domain.ReviewReason
import com.eric.personalledger.domain.WidgetExpenseTotals
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

enum class CandidateStatus {
    PENDING,
    CONFIRMED,
    IGNORED,
}

data class NotificationCandidateInput(
    val id: String,
    val sourcePackage: String,
    val amountCents: Long?,
    val reviewReason: ReviewReason?,
    val receivedAtEpochMillis: Long,
)

data class PendingLedgerCandidate(
    val id: String,
    val sourcePackage: String,
    val sourceLabel: String,
    val amountCents: Long?,
    val category: String,
    val receivedAtEpochMillis: Long,
    val status: CandidateStatus,
    val reviewReason: ReviewReason?,
)

enum class CandidateConfirmationResult {
    CONFIRMED,
    ALREADY_PROCESSED,
    NOT_FOUND,
}

/** A local-only ledger backed by a versioned SQLite database. */
class SqliteLedgerRepository(
    context: Context,
    databaseName: String = DATABASE_NAME,
) : AutoCloseable {
    private val helper = LedgerDatabaseHelper(context.applicationContext, databaseName)

    fun categoryTags(): List<String> = helper.readableDatabase.rawQuery(
        "SELECT name FROM category_tags ORDER BY name ASC", null,
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    fun commonCategoryTags(type: EntryType): List<String> = CategoryTags.common(allEntries(), type)

    fun accounts(): List<LedgerAccount> = helper.readableDatabase.rawQuery(
        "SELECT id, name FROM accounts ORDER BY id ASC",
        null,
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(LedgerAccount(cursor.getLong(0), cursor.getString(1)))
        }
    }

    fun createAccount(name: String): LedgerAccount {
        val safeName = name.trim()
        require(safeName.isNotEmpty() && safeName.length <= MAX_ACCOUNT_NAME_LENGTH) {
            "Account name must contain 1 to $MAX_ACCOUNT_NAME_LENGTH characters."
        }
        val db = helper.writableDatabase
        val id = db.insertOrThrow(
            "accounts",
            null,
            ContentValues().apply {
                put("name", safeName)
                put("created_at", System.currentTimeMillis())
            },
        )
        return LedgerAccount(id, safeName)
    }

    fun renameAccount(accountId: Long, name: String): Boolean {
        val safeName = name.trim()
        require(safeName.isNotEmpty() && safeName.length <= MAX_ACCOUNT_NAME_LENGTH) {
            "Account name must contain 1 to $MAX_ACCOUNT_NAME_LENGTH characters."
        }
        return helper.writableDatabase.update(
            "accounts",
            ContentValues().apply { put("name", safeName) },
            "id = ?",
            arrayOf(accountId.toString()),
        ) == 1
    }

    fun allEntries(): List<LedgerEntry> = helper.readableDatabase.query(
        "ledger_entries",
        ENTRY_COLUMNS,
        null,
        null,
        null,
        null,
        "occurred_at DESC, id DESC",
    ).use { cursor -> cursor.readEntries() }

    fun entriesForMonth(month: YearMonth, zoneId: ZoneId): List<LedgerEntry> {
        val start = month.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return helper.readableDatabase.query(
            "ledger_entries",
            ENTRY_COLUMNS,
            "occurred_at >= ? AND occurred_at < ?",
            arrayOf(start.toString(), end.toString()),
            null,
            null,
            "occurred_at DESC, id DESC",
        ).use { cursor -> cursor.readEntries() }
    }

    fun widgetExpenseTotals(now: Instant, zoneId: ZoneId): WidgetExpenseTotals =
        WidgetExpenseTotals.calculate(entriesForMonth(YearMonth.from(now.atZone(zoneId)), zoneId), now, zoneId)

    fun insertEntry(entry: LedgerEntry) {
        validateEntry(entry)
        require(entry.candidateId == null) {
            "Notification entries must be created through confirmCandidate."
        }
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.insertOrThrow("ledger_entries", null, entry.toContentValues())
            replaceTags(db, entry)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun updateEntry(entry: LedgerEntry): Boolean {
        validateEntry(entry)
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val updated = db.update("ledger_entries", entry.toContentValues(), "id = ?", arrayOf(entry.id)) == 1
            if (updated) replaceTags(db, entry)
            db.setTransactionSuccessful()
            return updated
        } finally {
            db.endTransaction()
        }
    }

    fun deleteEntry(entryId: String): Boolean =
        helper.writableDatabase.delete("ledger_entries", "id = ?", arrayOf(entryId)) == 1

    fun accountBalanceCents(accountId: Long): Long =
        LedgerRules.accountBalanceCents(allEntries(), accountId)

    fun monthlyBudget(month: YearMonth): Long? = helper.readableDatabase.rawQuery(
        "SELECT amount_cents FROM monthly_budgets WHERE month = ?",
        arrayOf(month.toString()),
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

    fun setMonthlyBudget(month: YearMonth, amountCents: Long?) {
        require(amountCents == null || amountCents > 0L) { "A budget must be positive or unset." }
        val db = helper.writableDatabase
        if (amountCents == null) {
            db.delete("monthly_budgets", "month = ?", arrayOf(month.toString()))
            return
        }
        val values = ContentValues().apply {
            put("month", month.toString())
            put("amount_cents", amountCents)
        }
        db.insertWithOnConflict("monthly_budgets", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun pendingCandidates(): List<PendingLedgerCandidate> = helper.readableDatabase.query(
        "notification_candidates",
        CANDIDATE_COLUMNS,
        "status = ?",
        arrayOf(CandidateStatus.PENDING.name),
        null,
        null,
        "received_at DESC, id DESC",
    ).use { cursor -> cursor.readCandidates() }

    fun candidate(candidateId: String): PendingLedgerCandidate? =
        candidate(helper.readableDatabase, candidateId)

    private fun candidate(db: SQLiteDatabase, candidateId: String): PendingLedgerCandidate? = db.query(
        "notification_candidates",
        CANDIDATE_COLUMNS,
        "id = ?",
        arrayOf(candidateId),
        null,
        null,
        null,
        "1",
    ).use { cursor -> if (cursor.moveToFirst()) cursor.toCandidate() else null }

    /** Upserts updates to the same Android notification, without reviving handled candidates. */
    fun upsertCandidate(input: NotificationCandidateInput): PendingLedgerCandidate {
        validateCandidateInput(input)
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val existing = candidate(db, input.id)
            if (existing == null) {
                db.insertOrThrow(
                    "notification_candidates",
                    null,
                    input.toContentValues(),
                )
            } else if (
                existing.status == CandidateStatus.PENDING &&
                input.receivedAtEpochMillis >= existing.receivedAtEpochMillis
            ) {
                db.update(
                    "notification_candidates",
                    input.toContentValues(),
                    "id = ? AND status = ?",
                    arrayOf(input.id, CandidateStatus.PENDING.name),
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return checkNotNull(candidate(input.id))
    }

    /** Inserts the ledger entry and marks its candidate handled as one SQLite transaction. */
    fun confirmCandidate(candidateId: String, entry: LedgerEntry): CandidateConfirmationResult {
        require(entry.candidateId == candidateId) { "The entry must reference the confirmed candidate." }
        require(entry.type == EntryType.EXPENSE) { "A payment notification can only confirm an expense." }
        validateEntry(entry)

        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val candidate = candidate(db, candidateId)
                ?: return CandidateConfirmationResult.NOT_FOUND
            if (candidate.status != CandidateStatus.PENDING) {
                return CandidateConfirmationResult.ALREADY_PROCESSED
            }
            db.insertOrThrow("ledger_entries", null, entry.toContentValues())
            replaceTags(db, entry)
            val changed = db.update(
                "notification_candidates",
                ContentValues().apply { put("status", CandidateStatus.CONFIRMED.name) },
                "id = ? AND status = ?",
                arrayOf(candidateId, CandidateStatus.PENDING.name),
            )
            check(changed == 1) { "Candidate state changed during confirmation." }
            db.setTransactionSuccessful()
            return CandidateConfirmationResult.CONFIRMED
        } finally {
            db.endTransaction()
        }
    }

    fun ignoreCandidate(candidateId: String): Boolean = helper.writableDatabase.update(
        "notification_candidates",
        ContentValues().apply { put("status", CandidateStatus.IGNORED.name) },
        "id = ? AND status = ?",
        arrayOf(candidateId, CandidateStatus.PENDING.name),
    ) == 1

    override fun close() = helper.close()

    private fun validateEntry(entry: LedgerEntry) {
        require(entry.id.isNotBlank()) { "Ledger entry id must not be blank." }
        require(entry.amountCents > 0L) { "Ledger amount must be positive." }
        require(entry.category.isNotBlank() && entry.category.length <= MAX_CATEGORY_LENGTH) {
            "Category must contain 1 to $MAX_CATEGORY_LENGTH characters."
        }
        CategoryTags.normalize(listOf(entry.category) + entry.additionalCategories)
        when (entry.type) {
            EntryType.TRANSFER -> require(
                entry.transferTargetAccountId != null && entry.transferTargetAccountId != entry.accountId,
            ) { "A transfer requires two different accounts." }
            EntryType.EXPENSE, EntryType.INCOME -> require(entry.transferTargetAccountId == null) {
                "Income and expense entries cannot have a transfer destination."
            }
        }
        require(entry.memo.length <= MAX_MEMO_LENGTH) { "Memo is too long." }
    }

    private fun validateCandidateInput(input: NotificationCandidateInput) {
        require(CANDIDATE_ID_PATTERN.matches(input.id)) { "Candidate id must be a stable SHA-256 hash." }
        require(input.sourcePackage in TRUSTED_PAYMENT_PACKAGES) { "Notification source is not supported." }
        require(input.receivedAtEpochMillis > 0L) { "Candidate timestamp must be positive." }
        require(input.amountCents == null || input.amountCents > 0L) { "Candidate amount must be positive." }
        require(input.amountCents != null || input.reviewReason != null) {
            "Incomplete candidates must explain why they need review."
        }
    }

    private fun NotificationCandidateInput.toContentValues() = ContentValues().apply {
        put("id", id)
        put("source_package", sourcePackage)
        put("source_label", sourceLabelFor(sourcePackage))
        if (amountCents == null) putNull("amount_cents") else put("amount_cents", amountCents)
        put("category", DEFAULT_CATEGORY)
        put("received_at", receivedAtEpochMillis)
        put("status", CandidateStatus.PENDING.name)
        if (reviewReason == null) putNull("review_reason") else put("review_reason", reviewReason.name)
    }

    private fun sourceLabelFor(packageName: String): String = when (packageName) {
        "com.tencent.mm" -> "微信支付"
        "com.eg.android.AlipayGphone" -> "支付宝"
        else -> error("Untrusted payment source.")
    }

    private fun LedgerEntry.toContentValues() = ContentValues().apply {
        put("id", id)
        put("type", type.name)
        put("amount_cents", amountCents)
        put("category", categoryTags.first())
        put("account_id", accountId)
        if (transferTargetAccountId == null) putNull("target_account_id")
        else put("target_account_id", transferTargetAccountId)
        put("occurred_at", occurredAtEpochMillis)
        put("memo", memo)
        if (candidateId == null) putNull("candidate_id") else put("candidate_id", candidateId)
    }

    private fun toLedgerEntry(cursor: Cursor) = LedgerEntry(
        id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
        type = EntryType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("type"))),
        amountCents = cursor.getLong(cursor.getColumnIndexOrThrow("amount_cents")),
        category = cursor.getString(cursor.getColumnIndexOrThrow("category")),
        accountId = cursor.getLong(cursor.getColumnIndexOrThrow("account_id")),
        transferTargetAccountId = cursor.getNullableLong("target_account_id"),
        occurredAtEpochMillis = cursor.getLong(cursor.getColumnIndexOrThrow("occurred_at")),
        memo = cursor.getString(cursor.getColumnIndexOrThrow("memo")),
        candidateId = cursor.getNullableString("candidate_id"),
    )

    private fun Cursor.toCandidate() = PendingLedgerCandidate(
        id = getString(getColumnIndexOrThrow("id")),
        sourcePackage = getString(getColumnIndexOrThrow("source_package")),
        sourceLabel = getString(getColumnIndexOrThrow("source_label")),
        amountCents = getNullableLong("amount_cents"),
        category = getString(getColumnIndexOrThrow("category")),
        receivedAtEpochMillis = getLong(getColumnIndexOrThrow("received_at")),
        status = CandidateStatus.valueOf(getString(getColumnIndexOrThrow("status"))),
        reviewReason = getNullableString("review_reason")?.let(ReviewReason::valueOf),
    )

    private fun Cursor.getNullableLong(column: String): Long? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getLong(index)
    }

    private fun Cursor.getNullableString(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }

    private fun replaceTags(db: SQLiteDatabase, entry: LedgerEntry) {
        db.delete("entry_tags", "entry_id = ?", arrayOf(entry.id))
        entry.categoryTags.forEachIndexed { index, name ->
            db.execSQL("INSERT OR IGNORE INTO category_tags(name) VALUES (?)", arrayOf(name))
            db.execSQL("INSERT INTO entry_tags(entry_id,tag_name,position) VALUES (?,?,?)",
                arrayOf<Any>(entry.id, name, index))
        }
    }

    private fun Cursor.readEntries(): List<LedgerEntry> {
        val entries = buildList { while (moveToNext()) add(toLedgerEntry(this@readEntries)) }
        if (entries.isEmpty()) return entries
        // One bounded query for this cursor's entries avoids an extra query for each row.
        val tags = mutableMapOf<String, MutableList<String>>()
        helper.readableDatabase.rawQuery(
            "SELECT t.entry_id,t.tag_name FROM entry_tags t JOIN ledger_entries e ON e.id=t.entry_id " +
                "WHERE e.occurred_at >= ? AND e.occurred_at <= ? ORDER BY t.entry_id,t.position",
            arrayOf(entries.minOf { it.occurredAtEpochMillis }.toString(), entries.maxOf { it.occurredAtEpochMillis }.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) tags.getOrPut(cursor.getString(0)) { mutableListOf() }.add(cursor.getString(1))
        }
        return entries.map { entry ->
            val names = tags[entry.id].orEmpty()
            entry.copy(additionalCategories = names.filterNot { it == entry.category })
        }
    }

    private fun Cursor.readCandidates(): List<PendingLedgerCandidate> = buildList {
        while (moveToNext()) add(this@readCandidates.toCandidate())
    }

    companion object {
        private const val DATABASE_NAME = "personal-ledger.db"
        private const val MAX_ACCOUNT_NAME_LENGTH = 40
        private const val MAX_CATEGORY_LENGTH = 40
        private const val MAX_MEMO_LENGTH = 240
        private const val DEFAULT_CATEGORY = "其他"
        private val CANDIDATE_ID_PATTERN = Regex("[a-f0-9]{64}")
        private val TRUSTED_PAYMENT_PACKAGES = setOf("com.tencent.mm", "com.eg.android.AlipayGphone")
        private val ENTRY_COLUMNS = arrayOf(
            "id", "type", "amount_cents", "category", "account_id", "target_account_id",
            "occurred_at", "memo", "candidate_id",
        )
        private val CANDIDATE_COLUMNS = arrayOf(
            "id", "source_package", "source_label", "amount_cents", "category", "received_at",
            "status", "review_reason",
        )
    }
}

private class LedgerDatabaseHelper(context: Context, name: String) :
    SQLiteOpenHelper(context, name, null, DATABASE_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE accounts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE COLLATE NOCASE,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE notification_candidates (
                id TEXT PRIMARY KEY NOT NULL,
                source_package TEXT NOT NULL CHECK(source_package IN ('com.tencent.mm', 'com.eg.android.AlipayGphone')),
                source_label TEXT NOT NULL,
                amount_cents INTEGER CHECK(amount_cents IS NULL OR amount_cents > 0),
                category TEXT NOT NULL,
                received_at INTEGER NOT NULL,
                status TEXT NOT NULL CHECK(status IN ('PENDING', 'CONFIRMED', 'IGNORED')),
                review_reason TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE ledger_entries (
                id TEXT PRIMARY KEY NOT NULL,
                type TEXT NOT NULL CHECK(type IN ('EXPENSE', 'INCOME', 'TRANSFER')),
                amount_cents INTEGER NOT NULL CHECK(amount_cents > 0),
                category TEXT NOT NULL,
                account_id INTEGER NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
                target_account_id INTEGER REFERENCES accounts(id) ON DELETE RESTRICT,
                occurred_at INTEGER NOT NULL,
                memo TEXT NOT NULL DEFAULT '',
                candidate_id TEXT UNIQUE REFERENCES notification_candidates(id) ON DELETE RESTRICT,
                CHECK (
                    (type = 'TRANSFER' AND target_account_id IS NOT NULL AND target_account_id != account_id)
                    OR (type != 'TRANSFER' AND target_account_id IS NULL)
                )
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX ledger_entries_occurred_at_idx ON ledger_entries(occurred_at DESC)")
        db.execSQL("CREATE INDEX notification_candidates_status_idx ON notification_candidates(status, received_at DESC)")
        db.execSQL(
            """
            CREATE TABLE monthly_budgets (
                month TEXT PRIMARY KEY NOT NULL CHECK(length(month) = 7),
                amount_cents INTEGER NOT NULL CHECK(amount_cents > 0)
            )
            """.trimIndent(),
        )
        val now = System.currentTimeMillis()
        db.execSQL("INSERT INTO accounts(name, created_at) VALUES (?, ?)", arrayOf<Any>("现金", now))
        db.execSQL("INSERT INTO accounts(name, created_at) VALUES (?, ?)", arrayOf<Any>("银行卡", now))
        addTagsAndPlatforms(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion == 1 && newVersion == 2) {
            addTagsAndPlatforms(db)
            return
        }
        throw SQLiteException("No migration path from database version $oldVersion to $newVersion.")
    }

    private fun addTagsAndPlatforms(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE category_tags (name TEXT PRIMARY KEY NOT NULL)")
        db.execSQL("""
            CREATE TABLE entry_tags (
                entry_id TEXT NOT NULL REFERENCES ledger_entries(id) ON DELETE CASCADE,
                tag_name TEXT NOT NULL REFERENCES category_tags(name),
                position INTEGER NOT NULL CHECK(position >= 0),
                PRIMARY KEY(entry_id, tag_name), UNIQUE(entry_id, position)
            )
        """.trimIndent())
        CategoryTags.catalog.forEach { name ->
            db.execSQL("INSERT OR IGNORE INTO category_tags(name) VALUES (?)", arrayOf(name))
        }
        db.execSQL("INSERT OR IGNORE INTO category_tags(name) SELECT DISTINCT category FROM ledger_entries")
        db.execSQL("INSERT OR IGNORE INTO category_tags(name) SELECT DISTINCT category FROM notification_candidates")
        db.execSQL("INSERT INTO entry_tags(entry_id,tag_name,position) SELECT id,category,0 FROM ledger_entries")
        listOf("微信", "支付宝", "淘宝", "京东", "现金", "银行卡").forEach { name ->
            db.execSQL("INSERT OR IGNORE INTO accounts(name,created_at) VALUES (?,?)", arrayOf<Any>(name, System.currentTimeMillis()))
        }
    }

    private companion object {
        const val DATABASE_VERSION = 2
    }
}
