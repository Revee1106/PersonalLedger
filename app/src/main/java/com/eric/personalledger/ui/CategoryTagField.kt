package com.eric.personalledger.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eric.personalledger.domain.CategoryTags
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val TagInk = Color(0xFF30332D)
private val TagMuted = Color(0xFF85867F)
private val TagPaper = Color(0xFFFFFEFC)
internal val CategoryTagListSaver = listSaver<List<String>, String>(save = { it }, restore = { it.toList() })

/** Entry choices are drafts. Common preferences are saved independently and never replace entry choices. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CategoryTagField(
    selected: List<String>,
    options: List<String>,
    common: List<String>,
    onChange: (List<String>) -> Unit,
    onSaveCommon: suspend (List<String>) -> Unit = {},
) {
    var panel by rememberSaveable { mutableIntStateOf(0) }
    var currentCommon by rememberSaveable(common, stateSaver = CategoryTagListSaver) { mutableStateOf(common.toList()) }
    var draft by rememberSaveable(stateSaver = CategoryTagListSaver) { mutableStateOf(emptyList<String>()) }
    var created by rememberSaveable(stateSaver = CategoryTagListSaver) { mutableStateOf(emptyList<String>()) }
    var favorites by rememberSaveable(stateSaver = CategoryTagListSaver) { mutableStateOf(emptyList<String>()) }
    var search by rememberSaveable { mutableStateOf("") }
    var commonError by remember { mutableStateOf<String?>(null) }
    var savingCommon by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val rowScroll = rememberScrollState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("分类", color = TagMuted, fontSize = 13.sp)
            TextButton(onClick = {
                draft = selected.toList(); created = emptyList(); search = ""; commonError = null; panel = 1
            }, modifier = Modifier.testTag("category_options_button")) { Text("更多标签 ›", color = TagInk) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rowScroll).heightIn(min = 48.dp).testTag("category_tags_row"),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val display = CategoryTags.displayOrder(selected, currentCommon)
            display.forEach { tag ->
                TagChip(tag, tag in selected, "category_tag_$tag") {
                    onChange(if (tag in selected) selected - tag else selected + tag)
                }
            }
            if (display.isEmpty()) Text("从更多标签中添加", color = TagMuted, fontSize = 12.sp)
        }
    }

    if (panel != 0) {
        val editingCommon = panel == 2
        val query = search.trim()
        val catalog = (currentCommon + selected + options + created + draft).distinct()
        val matches = catalog.filter { query.isEmpty() || it.contains(query, ignoreCase = true) }
        val validName = query.isNotEmpty() && runCatching { CategoryTags.normalize(listOf(query)); true }.getOrDefault(false)
        val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
        ModalBottomSheet(
            onDismissRequest = { if (!savingCommon) panel = 0 },
            sheetState = sheetState,
            containerColor = TagPaper,
        ) {
            Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).imePadding().padding(horizontal = 22.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (editingCommon) "编辑常用" else "更多标签", color = TagInk, fontSize = 19.sp, fontWeight = FontWeight.Medium)
                    if (!editingCommon) TextButton(onClick = {
                        favorites = currentCommon.toList(); search = ""; commonError = null; panel = 2
                    }, modifier = Modifier.testTag("category_edit_common")) { Text("编辑常用", color = TagInk) }
                    else TextButton(enabled = !savingCommon, onClick = { search = ""; panel = 1 },
                        modifier = Modifier.testTag("common_back")) { Text("‹ 返回", color = TagInk) }
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
                    if (editingCommon) {
                        Text("最多固定 3 个，可调整顺序", color = TagMuted, fontSize = 12.sp)
                        favorites.forEachIndexed { index, tag ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(tag, color = TagInk, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                TextButton(enabled = index > 0 && !savingCommon, onClick = {
                                    favorites = favorites.toMutableList().apply { val previous = this[index - 1]; this[index - 1] = tag; this[index] = previous }.toList()
                                }, modifier = Modifier.testTag("common_up_$tag")) { Text("↑", color = TagInk) }
                                TextButton(enabled = index < favorites.lastIndex && !savingCommon, onClick = {
                                    favorites = favorites.toMutableList().apply { val next = this[index + 1]; this[index + 1] = tag; this[index] = next }.toList()
                                }, modifier = Modifier.testTag("common_down_$tag")) { Text("↓", color = TagInk) }
                                TextButton(enabled = !savingCommon, onClick = { favorites = favorites - tag; commonError = null },
                                    modifier = Modifier.testTag("common_remove_$tag")) { Text("×", color = TagInk) }
                            }
                        }
                        if (favorites.isEmpty()) Text("从下方添加常用标签", color = TagMuted, fontSize = 12.sp)
                        Spacer(Modifier.height(10.dp))
                    }
                    OutlinedTextField(value = search, onValueChange = { search = it.take(41) }, singleLine = true,
                        enabled = !savingCommon, placeholder = { Text(if (editingCommon) "搜索标签" else "搜索或新建标签") },
                        modifier = Modifier.fillMaxWidth().testTag("category_search"))
                    if (!editingCommon && query.isNotEmpty() && !validName) {
                        Text("标签限 1–40 个字符，不能含换行", color = TagMuted, fontSize = 12.sp)
                    }
                    if (!editingCommon && validName && query !in catalog) TextButton(onClick = {
                        created = (created + query).distinct(); draft = (draft + query).distinct(); search = ""; keyboard?.hide()
                    }, modifier = Modifier.testTag("category_create")) { Text("新建「$query」", color = TagInk) }
                    if (editingCommon) {
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            matches.forEach { tag -> TagChip(tag, tag in favorites, "common_option_$tag", enabled = !savingCommon) {
                                when {
                                    tag in favorites -> { favorites = favorites - tag; commonError = null }
                                    favorites.size < 3 -> { favorites = favorites + tag; commonError = null }
                                    else -> commonError = "最多固定 3 个，请先移除一个。"
                                }
                            } }
                        }
                    } else {
                        listOf("常用标签" to matches.filter { it in currentCommon }, "其他标签" to matches.filterNot { it in currentCommon })
                            .filter { it.second.isNotEmpty() }.forEach { (label, tags) ->
                                Text(label, color = TagMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    tags.forEach { tag -> TagChip(tag, tag in draft, "category_option_$tag") {
                                        draft = if (tag in draft) draft - tag else draft + tag
                                    } }
                                }
                            }
                    }
                    commonError?.let { Text(it, color = Color(0xFF9A4742), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)) }
                    Spacer(Modifier.height(12.dp))
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(enabled = !savingCommon, onClick = {
                        if (editingCommon) { panel = 1; search = ""; commonError = null } else panel = 0
                    }, modifier = Modifier.testTag(if (editingCommon) "common_cancel" else "category_cancel")) { Text("取消", color = TagMuted) }
                    if (editingCommon) TextButton(enabled = !savingCommon, modifier = Modifier.testTag("common_save"), onClick = {
                        scope.launch {
                            savingCommon = true; commonError = null
                            try {
                                val names = CategoryTags.normalizeCommon(favorites)
                                onSaveCommon(names)
                                currentCommon = names; panel = 1; search = ""
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { commonError = "常用标签保存失败，请重试。" }
                            finally { savingCommon = false }
                        }
                    }) { Text(if (savingCommon) "保存中…" else "保存常用", color = TagInk) }
                    else TextButton(enabled = draft.isNotEmpty(), onClick = {
                        onChange(CategoryTags.normalize(draft)); panel = 0; keyboard?.hide()
                        scope.launch { rowScroll.scrollTo(0) }
                    }, modifier = Modifier.testTag("category_done")) { Text("完成", color = TagInk) }
                }
            }
        }
    }
}

@Composable
private fun TagChip(tag: String, chosen: Boolean, testTag: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled,
        color = if (chosen) Color(0xFFE5E8DE) else Color.Transparent,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (chosen) Color(0xFFB7BEAD) else Color(0xFFD9DBD3)),
        modifier = Modifier.widthIn(min = 76.dp, max = 160.dp).height(44.dp).semantics {
            selected = chosen; role = Role.Checkbox
        }.testTag(testTag),
    ) {
        Box(contentAlignment = Alignment.CenterStart) {
            Text(tag, color = TagInk, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 14.dp, end = 26.dp, top = 10.dp, bottom = 10.dp))
            if (chosen) Text("✓", color = TagInk, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 6.dp))
        }
    }
}
