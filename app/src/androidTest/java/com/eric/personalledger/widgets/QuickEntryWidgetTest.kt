package com.eric.personalledger.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import android.content.ComponentName
import android.view.View
import android.widget.TextView
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eric.personalledger.MainActivity
import com.eric.personalledger.R
import com.eric.personalledger.domain.WidgetExpenseTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickEntryWidgetTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun coldWidgetLaunchFocusesAmountAndLoadsAccounts() {
        ActivityScenario.launch<MainActivity>(QuickEntryWidgetProvider.entryIntent(context)).use {
            awaitAmountFocus()
            compose.onNodeWithTag("entry_amount").performTextInput("12.50")
            compose.waitUntil(10_000) {
                runCatching { compose.onNodeWithTag("entry_save").assertIsEnabled(); true }.getOrDefault(false)
            }
        }
    }

    @Test fun warmPendingIntentResumesExistingDraftWithoutClearingAmount() {
        ActivityScenario.launch<MainActivity>(QuickEntryWidgetProvider.entryIntent(context)).use {
            returnToLedger()
            QuickEntryWidgetProvider.entryPendingIntent(context).send()
            awaitAmountFocus()
            compose.onNodeWithTag("entry_amount").performTextInput("23.45")
            QuickEntryWidgetProvider.entryPendingIntent(context).send()
            compose.onNodeWithTag("entry_amount").assertTextContains("23.45")
        }
    }

    @Test fun newEntryDraftSurvivesActivityRecreation() {
        ActivityScenario.launch<MainActivity>(QuickEntryWidgetProvider.entryIntent(context)).use { scenario ->
            compose.onNodeWithTag("entry_amount").performTextInput("34.56")
            scenario.recreate()
            compose.onNodeWithTag("entry_amount").assertTextContains("34.56")
        }
    }

    @Test fun installedWidgetInflatesAndItsCardOpensEntry() {
        val provider = ComponentName(context, QuickEntryWidgetProvider::class.java)
        val info = AppWidgetManager.getInstance(context).installedProviders.firstOrNull { it.provider == provider }
        assertNotNull(info)
        assertTrue(info!!.updatePeriodMillis >= 30 * 60 * 1000)
        assertEquals(AppWidgetProviderInfo.RESIZE_HORIZONTAL, info.resizeMode)
        if (Build.VERSION.SDK_INT >= 31) {
            assertEquals(4, info.targetCellWidth)
            assertEquals(1, info.targetCellHeight)
        }
        ActivityScenario.launch<MainActivity>(QuickEntryWidgetProvider.entryIntent(context)).use { scenario ->
            returnToLedger()
            scenario.onActivity { activity ->
                val view = QuickEntryWidgetProvider.createViews(context).apply(activity, null)
                val card = view.findViewById<View>(R.id.quick_entry_card)
                assertTrue(card.performClick())
            }
            awaitAmountFocus()
        }
    }

    @Test fun expenseTotalsRenderAtTheMinimumCardSize() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = QuickEntryWidgetProvider.createViews(context, WidgetExpenseTotals(1_030, 1_234_567))
                .apply(context, null)
            val density = context.resources.displayMetrics.density
            view.measure(
                View.MeasureSpec.makeMeasureSpec((250 * density).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((72 * density).toInt(), View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            val today = view.findViewById<TextView>(R.id.widget_today_amount)
            val month = view.findViewById<TextView>(R.id.widget_month_amount)
            assertEquals("¥10.30", today.text.toString())
            assertEquals("¥12,345.67", month.text.toString())
            assertTrue(today.width > 0 && month.width > 0)
            assertEquals(0, today.layout.getEllipsisCount(0))
            assertEquals(0, month.layout.getEllipsisCount(0))
        }
    }

    @Test fun unavailableTotalsShowDashesInsteadOfFalseZero() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = QuickEntryWidgetProvider.createViews(context).apply(context, null)
            assertEquals("—", view.findViewById<TextView>(R.id.widget_today_amount).text.toString())
            assertEquals("—", view.findViewById<TextView>(R.id.widget_month_amount).text.toString())
            assertTrue(view.contentDescription.toString().contains("暂不可用"))
        }
    }

    private fun returnToLedger() {
        compose.onNodeWithText("‹ 返回").performClick()
        compose.onNodeWithTag("entry_amount").assertDoesNotExist()
    }

    private fun awaitAmountFocus() {
        // Window focus arrives after the first frame on a cold launch.
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithTag("entry_amount").assertIsFocused(); true }.getOrDefault(false)
        }
    }
}
