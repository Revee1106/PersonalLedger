package com.eric.personalledger.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.database.sqlite.SQLiteException
import android.app.Notification
import com.eric.personalledger.data.NotificationCandidateInput
import com.eric.personalledger.data.SqliteLedgerRepository
import com.eric.personalledger.domain.NotificationParseResult
import com.eric.personalledger.domain.NotificationParser
import com.eric.personalledger.domain.NotificationIdentity
import com.eric.personalledger.domain.ReviewReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Reads allowlisted payment notifications in memory and stores only a review candidate. */
class PaymentNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processingMutex = Mutex()

    override fun onListenerConnected() {
        super.onListenerConnected()
        val active = try {
            activeNotifications.orEmpty()
        } catch (_: SecurityException) {
            emptyArray()
        }
        active.forEach(::processNotification)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        processNotification(sbn)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun processNotification(sbn: StatusBarNotification) {
        if (sbn.packageName !in TRUSTED_PAYMENT_PACKAGES) return
        val notification = sbn.notification ?: return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val text = notificationText(notification.extras) ?: return
        val parsed = NotificationParser.parse(sbn.packageName, text)
        if (parsed == NotificationParseResult.NotRelevant) return

        val eventTime = notification.`when`.takeIf { it > 0L } ?: sbn.postTime
        if (eventTime <= 0L) return
        val candidateId = NotificationIdentity.idFor(sbn.packageName, sbn.key, eventTime)
        val receivedAt = sbn.postTime.takeIf { it > 0L } ?: System.currentTimeMillis()
        val input = when (parsed) {
            NotificationParseResult.NotRelevant -> return
            is NotificationParseResult.Candidate -> NotificationCandidateInput(
                id = candidateId,
                sourcePackage = sbn.packageName,
                amountCents = parsed.amountCents,
                reviewReason = null,
                receivedAtEpochMillis = receivedAt,
            )
            is NotificationParseResult.NeedsReview -> NotificationCandidateInput(
                id = candidateId,
                sourcePackage = sbn.packageName,
                amountCents = null,
                reviewReason = parsed.reason,
                receivedAtEpochMillis = receivedAt,
            )
        }

        serviceScope.launch {
            processingMutex.withLock {
                try {
                    SqliteLedgerRepository(applicationContext).use { repository ->
                        val isNewCandidate = repository.candidate(candidateId) == null
                        repository.upsertCandidate(input)
                        if (isNewCandidate) {
                            try {
                                PendingReviewNotifier.notifyNewCandidate(applicationContext, candidateId)
                            } catch (_: SecurityException) {
                                // The candidate remains available in-app when notification permission is denied.
                            }
                        }
                    }
                } catch (_: SQLiteException) {
                    // Notification text and transaction details are intentionally never logged.
                } catch (_: IllegalArgumentException) {
                    // A malformed notification is not allowed to interrupt the listener service.
                }
            }
        }
    }

    private fun notificationText(extras: android.os.Bundle): String? {
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()
        val text = listOfNotNull(title, body).distinct().joinToString(separator = " ")
        return text.takeIf(String::isNotBlank)?.take(MAX_TEXT_LENGTH + 1)
    }

    private companion object {
        const val MAX_TEXT_LENGTH = 4_096
        val TRUSTED_PAYMENT_PACKAGES = setOf("com.tencent.mm", "com.eg.android.AlipayGphone")
    }
}
