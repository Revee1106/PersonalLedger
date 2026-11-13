CREATE TABLE accounts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE COLLATE NOCASE,
                created_at INTEGER NOT NULL
            );
CREATE TABLE notification_candidates (
                id TEXT PRIMARY KEY NOT NULL,
                source_package TEXT NOT NULL CHECK(source_package IN ('com.tencent.mm', 'com.eg.android.AlipayGphone')),
                source_label TEXT NOT NULL,
                amount_cents INTEGER CHECK(amount_cents IS NULL OR amount_cents > 0),
                category TEXT NOT NULL,
                received_at INTEGER NOT NULL,
                status TEXT NOT NULL CHECK(status IN ('PENDING', 'CONFIRMED', 'IGNORED')),
                review_reason TEXT
            );
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
            );
CREATE TABLE monthly_budgets (
                month TEXT PRIMARY KEY NOT NULL CHECK(length(month) = 7),
                amount_cents INTEGER NOT NULL CHECK(amount_cents > 0)
            );
CREATE INDEX ledger_entries_occurred_at_idx ON ledger_entries(occurred_at DESC);
CREATE INDEX notification_candidates_status_idx ON notification_candidates(status, received_at DESC);
INSERT INTO accounts(name,created_at) VALUES('现金',123);
INSERT INTO accounts(name,created_at) VALUES('银行卡',123);
