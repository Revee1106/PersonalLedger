package com.eric.personalledger.domain

import java.security.MessageDigest

sealed interface NotificationParseResult {
    data object NotRelevant : NotificationParseResult

    data class Candidate(val amountCents: Long) : NotificationParseResult

    data class NeedsReview(val reason: ReviewReason) : NotificationParseResult
}

enum class ReviewReason {
    AMOUNT_HIDDEN_OR_MISSING,
    MULTIPLE_AMOUNTS,
    INVALID_AMOUNT,
}

object NotificationParser {
    private val trustedPackages = setOf(
        "com.tencent.mm",
        "com.eg.android.AlipayGphone",
    )
    private val purchaseSignal = Regex(
        "(?:支付|付款|消费|扣款)(?:交易)?成功|已(?:完成)?(?:支付|付款|扣款)|已成功(?:支付|付款|扣款)",
    )
    private val nonExpenseSignal = Regex(
        "退款|退回|转账|收款|到账|红包|失败|未支付|待付款|已取消|已撤销|退款中",
    )
    private val explicitAmounts = Regex(
        "(?:[¥￥]\\s*|(?:RMB|CNY)\\s*)([0-9][0-9,]*(?:\\.[0-9]+)?)|([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*元",
        RegexOption.IGNORE_CASE,
    )

    fun parse(packageName: String, text: String): NotificationParseResult {
        if (packageName !in trustedPackages) return NotificationParseResult.NotRelevant
        if (text.length > MAX_NOTIFICATION_TEXT_LENGTH) return NotificationParseResult.NotRelevant
        if (nonExpenseSignal.containsMatchIn(text)) return NotificationParseResult.NotRelevant
        if (!purchaseSignal.containsMatchIn(text)) return NotificationParseResult.NotRelevant

        val amounts = explicitAmounts.findAll(text).toList()
        if (amounts.any { hasMalformedNumericPrefix(text, it.range.first) }) {
            return NotificationParseResult.NeedsReview(ReviewReason.INVALID_AMOUNT)
        }
        if (amounts.size > 1) {
            return NotificationParseResult.NeedsReview(ReviewReason.MULTIPLE_AMOUNTS)
        }
        val amount = amounts.singleOrNull()
            ?: return NotificationParseResult.NeedsReview(ReviewReason.AMOUNT_HIDDEN_OR_MISSING)
        val rawAmount = amount.groups[1]?.value ?: amount.groups[2]?.value
            ?: return NotificationParseResult.NeedsReview(ReviewReason.INVALID_AMOUNT)
        val firstSuffixCharacter = text.getOrNull(amount.range.last + 1)
        if (firstSuffixCharacter?.let { it.isAsciiLetterOrDigit() || it in ".,_" } == true) {
            return NotificationParseResult.NeedsReview(ReviewReason.INVALID_AMOUNT)
        }
        val cents = MoneyInput.parseCents(rawAmount)
            ?: return NotificationParseResult.NeedsReview(ReviewReason.INVALID_AMOUNT)
        return NotificationParseResult.Candidate(cents)
    }

    private const val MAX_NOTIFICATION_TEXT_LENGTH = 4_096

    private fun Char.isAsciiLetterOrDigit(): Boolean =
        this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

    /** Rejects matches that begin inside a malformed numeric token such as `1e3` or `28..50`. */
    private fun hasMalformedNumericPrefix(text: String, start: Int): Boolean {
        val previous = text.getOrNull(start - 1) ?: return false
        return previous.isAsciiLetterOrDigit() || previous in ".,_+-"
    }
}

object NotificationIdentity {
    /** Hashes one notification event; neither the source key nor the raw event tuple is persisted. */
    fun idFor(packageName: String, notificationKey: String, eventTimeEpochMillis: Long): String {
        require(packageName.isNotBlank()) { "Notification source package must not be blank." }
        require(notificationKey.isNotBlank()) { "Notification key must not be blank." }
        require(eventTimeEpochMillis > 0L) { "Notification event time must be positive." }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$packageName\u0000$notificationKey\u0000$eventTimeEpochMillis".toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
