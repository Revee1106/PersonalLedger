package com.eric.personalledger.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.eric.personalledger.MainActivity

/** Posts a generic, redacted reminder that only opens the in-app pending review screen. */
object PendingReviewNotifier {
    const val CHANNEL_ID = "pending-review"

    fun notifyNewCandidate(context: Context, candidateId: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        ensureChannel(manager)
        val openPendingReview = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_PENDING_REVIEW
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_PENDING_REVIEW, true)
        }
        val clickIntent = PendingIntent.getActivity(
            context,
            0,
            openPendingReview,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val smallIcon = android.R.drawable.ic_menu_view
        val publicVersion = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle("个人账本")
            .setContentText("打开应用查看待确认记录")
            .build()
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle("有一笔记录待确认")
            .setContentText("打开个人账本核对后记账。")
            .setContentIntent(clickIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .build()
        manager.notify(candidateId, NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context, candidateId: String) {
        try {
            context.getSystemService(NotificationManager::class.java)
                .cancel(candidateId, NOTIFICATION_ID)
        } catch (_: SecurityException) {
            // System notification settings may change while the candidate is being reviewed.
        }
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "待确认提醒",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "提醒你打开应用核对本地待确认记录"
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                },
            )
        }
    }

    private const val NOTIFICATION_ID = 1
}
