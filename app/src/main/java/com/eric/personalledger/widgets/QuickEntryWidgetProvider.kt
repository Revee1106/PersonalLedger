package com.eric.personalledger.widgets

import android.app.PendingIntent
import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.widget.RemoteViews
import com.eric.personalledger.MainActivity
import com.eric.personalledger.R
import com.eric.personalledger.data.SqliteLedgerRepository
import com.eric.personalledger.domain.MoneyInput
import com.eric.personalledger.domain.WidgetExpenseTotals
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.Executors

/** Local expense summary and launcher entry; database work stays off the receiver thread. */
open class QuickEntryWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED -> refreshAsync(context)
            else -> super.onReceive(context, intent)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        refreshAsync(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        refreshAsync(context)
    }

    override fun onDisabled(context: Context) = refreshAsync(context)

    private fun refreshAsync(context: Context) {
        val pending = goAsync()
        val appContext = context.applicationContext
        updates.execute {
            try {
                updateWidgets(appContext)
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        private const val ACTION_REFRESH = "com.eric.personalledger.REFRESH_WIDGET"
        private val updates = Executors.newSingleThreadExecutor()

        fun requestUpdate(context: Context) {
            if (widgetGroups(context).values.all { it.isEmpty() }) return
            context.sendBroadcast(Intent(context, QuickEntryWidgetProvider::class.java).setAction(ACTION_REFRESH))
        }

        private fun widgetGroups(context: Context): Map<Boolean, IntArray> {
            val manager = AppWidgetManager.getInstance(context)
            return mapOf(
                false to manager.getAppWidgetIds(ComponentName(context, QuickEntryWidgetProvider::class.java)),
                true to manager.getAppWidgetIds(ComponentName(context, CompactEntryWidgetProvider::class.java)),
            )
        }

        private fun refreshPendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 7102, Intent(context, QuickEntryWidgetProvider::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun updateWidgets(context: Context) {
            val groups = widgetGroups(context)
            val alarms = context.getSystemService(AlarmManager::class.java)
            if (groups.values.all { it.isEmpty() }) {
                alarms.cancel(refreshPendingIntent(context))
                return
            }
            val now = Instant.now()
            val zone = ZoneId.systemDefault()
            val totals = try {
                SqliteLedgerRepository(context).use { it.widgetExpenseTotals(now, zone) }
            } catch (_: SQLiteException) {
                null
            } catch (_: ArithmeticException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
            val manager = AppWidgetManager.getInstance(context)
            groups.forEach { (compact, ids) ->
                if (ids.isNotEmpty()) manager.updateAppWidget(ids, createViews(context, totals, compact))
            }
            // Non-wakeup, inexact alarm: no exact-alarm permission or foreground service.
            alarms.set(AlarmManager.RTC, WidgetExpenseTotals.nextDayStart(now, zone).toEpochMilli(), refreshPendingIntent(context))
        }

        fun entryIntent(context: Context) = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_ADD_ENTRY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        fun entryPendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 7101, entryIntent(context), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        fun createViews(context: Context, totals: WidgetExpenseTotals? = null, compact: Boolean = false) = RemoteViews(
            context.packageName, if (compact) R.layout.widget_quick_entry_compact else R.layout.widget_quick_entry,
        ).apply {
            val today = totals?.let { MoneyInput.formatCents(it.todayCents) }
            val month = totals?.let { MoneyInput.formatCents(it.monthCents) }
            setTextViewText(R.id.widget_today_amount, today?.let { context.getString(R.string.widget_amount, it) }
                ?: context.getString(R.string.widget_amount_unavailable))
            setTextViewText(R.id.widget_month_amount, month?.let { context.getString(R.string.widget_amount, it) }
                ?: context.getString(R.string.widget_amount_unavailable))
            setContentDescription(R.id.quick_entry_card, if (totals == null) context.getString(R.string.widget_totals_unavailable)
                else context.getString(R.string.widget_totals_description, today, month))
            setOnClickPendingIntent(R.id.quick_entry_card, entryPendingIntent(context))
        }
    }
}
