package com.eric.personalledger.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.eric.personalledger.domain.LedgerAccount
import com.eric.personalledger.domain.LedgerEntry
import com.eric.personalledger.ui.theme.PersonalLedgerTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CategoryTagFormTest {
    @get:Rule val compose = createComposeRule()
    private val accounts = listOf(LedgerAccount(1, "现金"), LedgerAccount(2, "银行卡"),
        LedgerAccount(3, "微信"), LedgerAccount(4, "支付宝"), LedgerAccount(5, "淘宝"), LedgerAccount(6, "京东"))

    @Test fun quickTagsAreMultiSelectAndPlatformIsSaved() {
        var saved: LedgerEntry? = null
        compose.setContent { PersonalLedgerTheme {
            LedgerEntryFormScreen(accounts, null, null, false, {}, { saved = it }, {})
        } }
        compose.onNodeWithTag("entry_amount").performTextInput("12.50")
        compose.onNodeWithTag("category_quick_交通").performScrollTo().performClick()
        compose.onNodeWithTag("platform_picker").performScrollTo().performClick()
        compose.onNodeWithText("支付宝").performClick()
        compose.onNodeWithTag("entry_save").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf("餐饮", "交通"), saved?.categoryTags)
            assertEquals(4L, saved?.accountId)
            assertEquals(1250L, saved?.amountCents)
        }
    }

    @Test fun newTagsAndSelectionsSurviveRestorationAndCancelDoesNotApply() {
        val restoration = StateRestorationTester(compose)
        var saved: LedgerEntry? = null
        restoration.setContent { PersonalLedgerTheme {
            LedgerEntryFormScreen(accounts, null, null, false, {}, { saved = it }, {})
        } }
        compose.onNodeWithTag("entry_amount").performTextInput("20")
        compose.onNodeWithTag("category_options_button").performScrollTo().performClick()
        compose.onNodeWithTag("category_search").performTextInput("朋友聚餐")
        compose.onNodeWithTag("category_create").performClick()
        compose.onNodeWithTag("category_done").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("category_selected_朋友聚餐").assertExists()
        compose.onNodeWithTag("category_options_button").performScrollTo().performClick()
        compose.onNodeWithTag("category_option_餐饮").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithTag("entry_save").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("餐饮", "朋友聚餐"), saved?.categoryTags) }
    }

    @Test fun removingLastTagDisablesSavingAndEditingKeepsAllTags() {
        compose.setContent { PersonalLedgerTheme {
            LedgerEntryFormScreen(accounts, LedgerEntry("old", com.eric.personalledger.domain.EntryType.EXPENSE,
                100, "旧标签", 2, System.currentTimeMillis(), additionalCategories = listOf("保留标签")),
                null, false, {}, {}, {})
        } }
        compose.onNodeWithTag("category_selected_旧标签").performScrollTo().performClick()
        compose.onNodeWithTag("category_selected_保留标签").performScrollTo().performClick()
        compose.onNodeWithTag("entry_save").performScrollTo().assertIsNotEnabled()
    }
}
