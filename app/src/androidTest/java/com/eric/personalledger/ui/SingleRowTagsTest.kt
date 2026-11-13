package com.eric.personalledger.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.eric.personalledger.domain.CategoryTags
import com.eric.personalledger.ui.theme.PersonalLedgerTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SingleRowTagsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun threeInitialOptionsOnlyAndNewSelectionLeadsWithoutExtraRowOrCount() {
        compose.setContent { PersonalLedgerTheme {
            var selected by remember { mutableStateOf(listOf("餐饮")) }
            CategoryTagField(selected, CategoryTags.catalog, CategoryTags.catalog, { selected = it })
        } }
        compose.onAllNodesWithTag("category_tag_餐饮").assertCountEquals(1)
        compose.onNodeWithText("更多标签 ›").performClick()
        compose.onNodeWithTag("category_option_工资").assertDoesNotExist()
        compose.onNodeWithText("常用标签 · 点击可多选").assertDoesNotExist()
        compose.onNodeWithTag("category_search").performTextInput("朋友聚餐")
        compose.onNodeWithTag("category_create").performClick()
        compose.onNodeWithTag("category_done").performClick()
        val extra = compose.onNodeWithTag("category_tag_朋友聚餐").fetchSemanticsNode().boundsInRoot
        val food = compose.onNodeWithTag("category_tag_餐饮").fetchSemanticsNode().boundsInRoot
        assertTrue(extra.left < food.left)
        assertEquals(extra.top, food.top, 1f)
        compose.onNodeWithTag("category_tag_朋友聚餐").performClick()
        compose.onNodeWithTag("category_tag_朋友聚餐").assertDoesNotExist()
    }

    @Test fun editCommonSavesOrderAndDoesNotChangeTheEntrySelection() {
        var saved: List<String>? = null
        var selected = listOf("餐饮", "聚餐")
        compose.setContent { PersonalLedgerTheme {
            CategoryTagField(selected, CategoryTags.catalog + "聚餐", CategoryTags.catalog,
                { selected = it }, onSaveCommon = { saved = it })
        } }
        compose.onNodeWithText("更多标签 ›").performClick()
        compose.onNodeWithTag("category_edit_common").performClick()
        compose.onNodeWithTag("common_remove_购物").performClick()
        compose.onNodeWithTag("common_option_聚餐").performClick()
        compose.onNodeWithTag("common_up_聚餐").performClick()
        compose.onNodeWithTag("common_save").performClick()
        compose.waitUntil { saved != null }
        compose.runOnIdle {
            assertEquals(listOf("餐饮", "聚餐", "交通"), saved)
            assertEquals(listOf("餐饮", "聚餐"), selected)
        }
        compose.onNodeWithTag("category_cancel").performClick()
        compose.onNodeWithTag("category_tag_聚餐").assertExists()
    }

    @Test fun failedCommonSaveRetainsDraftAndCancelDoesNotCommitIt() {
        var attempts = 0
        compose.setContent { PersonalLedgerTheme {
            CategoryTagField(listOf("餐饮"), CategoryTags.catalog, CategoryTags.catalog, {},
                onSaveCommon = { attempts++; throw android.database.sqlite.SQLiteException("Test failure") })
        } }
        compose.onNodeWithText("更多标签 ›").performClick()
        compose.onNodeWithTag("category_edit_common").performClick()
        compose.onNodeWithTag("common_remove_购物").performClick()
        compose.onNodeWithTag("common_save").performClick()
        compose.onNodeWithText("常用标签保存失败，请重试。", substring = true).assertExists()
        compose.onNodeWithTag("common_cancel").performClick()
        compose.onNodeWithTag("category_cancel").performClick()
        compose.onNodeWithTag("category_tag_购物").assertExists()
        compose.runOnIdle { assertEquals(1, attempts) }
    }
}
