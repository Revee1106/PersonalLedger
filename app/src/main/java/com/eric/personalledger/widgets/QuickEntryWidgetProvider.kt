package com.eric.personalledger.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.eric.personalledger.MainActivity
import com.eric.personalledger.R

/** A static launcher entry. It never reads the ledger or runs periodic background work. */
class QuickEntryWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { manager.updateAppWidget(it, createViews(context)) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        manager.updateAppWidget(appWidgetId, createViews(context))
    }

    companion object {
        fun entryIntent(context: Context) = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_ADD_ENTRY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        fun entryPendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 7101, entryIntent(context), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        fun createViews(context: Context) = RemoteViews(context.packageName, R.layout.widget_quick_entry).apply {
            setOnClickPendingIntent(R.id.quick_entry_card, entryPendingIntent(context))
        }
    }
}
