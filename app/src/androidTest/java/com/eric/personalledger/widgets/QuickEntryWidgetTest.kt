package com.eric.personalledger.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.view.View
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
        assertEquals(0, info!!.updatePeriodMillis)
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
