package com.eric.personalledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eric.personalledger.domain.CategoryTags

private val TagInk = Color(0xFF30332D)
private val TagMuted = Color(0xFF85867F)

/** Selection is a draft: cancelling the picker never changes the form. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryTagField(
    selected: List<String>,
    options: List<String>,
    common: List<String>,
    onChange: (List<String>) -> Unit,
) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("分类", color = TagMuted, fontSize = 13.sp)
            TextButton(onClick = { pickerOpen = true }, modifier = Modifier.testTag("category_options_button")) {
                Text("选择标签 ›", color = TagInk)
            }
        }
        if (selected.isEmpty()) Text("至少选择一个标签", color = TagMuted, fontSize = 12.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            selected.forEach { tag ->
                InputChip(selected = true, onClick = { onChange(selected - tag) },
                    label = { Text(tag) }, trailingIcon = { Text("×") },
                    colors = InputChipDefaults.inputChipColors(selectedContainerColor = Color(0xFFE7E8E2)),
                    modifier = Modifier.testTag("category_selected_$tag"))
            }
        }
        Text("常用标签 · 点击可多选", color = TagMuted, fontSize = 12.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("category_common_tags")) {
            common.distinct().take(3).forEach { tag ->
                FilterChip(selected = tag in selected,
                    onClick = { onChange(if (tag in selected) selected - tag else selected + tag) },
                    label = { Text(tag) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFE7E8E2)),
                    modifier = Modifier.testTag("category_quick_$tag"))
            }
        }
    }
    if (pickerOpen) {
        var draft by rememberSaveable { mutableStateOf(ArrayList(selected)) }
        var created by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
        var search by rememberSaveable { mutableStateOf("") }
        val query = search.trim()
        val catalog = (selected + options + created).distinct()
        val validName = runCatching { CategoryTags.normalize(listOf(query)); true }.getOrDefault(false)
        AlertDialog(
            onDismissRequest = { pickerOpen = false },
            title = { Text("选择分类标签") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text("可选择多个标签", color = TagMuted, fontSize = 12.sp)
                    OutlinedTextField(value = search, onValueChange = { search = it }, singleLine = true,
                        placeholder = { Text("搜索或新建标签") }, modifier = Modifier.fillMaxWidth().testTag("category_search"))
                    if (query.isNotEmpty() && !validName) Text("标签限 1–40 个字符，不能含换行", color = TagMuted, fontSize = 12.sp)
                    if (validName && query !in catalog) {
                        TextButton(onClick = {
                            created = ArrayList(created + query)
                            draft = ArrayList((draft + query).distinct())
                            search = ""
                        }, modifier = Modifier.testTag("category_create")) { Text("新建「$query」", color = TagInk) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier
                        .fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                        catalog.filter { query.isEmpty() || it.contains(query, ignoreCase = true) }.forEach { tag ->
                            FilterChip(selected = tag in draft,
                                onClick = { draft = ArrayList(if (tag in draft) draft - tag else draft + tag) },
                                label = { Text(tag) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFE7E8E2)),
                                modifier = Modifier.testTag("category_option_$tag"))
                        }
                    }
                    Text("已选择 ${draft.size} 个", color = TagMuted, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(enabled = draft.isNotEmpty(), onClick = {
                    onChange(CategoryTags.normalize(draft)); pickerOpen = false
                }, modifier = Modifier.testTag("category_done")) { Text("完成", color = TagInk) }
            },
            dismissButton = { TextButton(onClick = { pickerOpen = false }) { Text("取消", color = TagMuted) } },
        )
    }
}
