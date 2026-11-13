package com.eric.personalledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eric.personalledger.domain.EntryType
import com.eric.personalledger.domain.LedgerAccount
import com.eric.personalledger.domain.LedgerEntry
import com.eric.personalledger.domain.MonthlyCategoryTotal
import com.eric.personalledger.domain.MonthlySummary
import com.eric.personalledger.domain.MoneyInput
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

private val PageBackground = Color(0xFFFFFEFC)
private val Ink = Color(0xFF272824)
private val MutedInk = Color(0xFF898A83)
private val SoftSurface = Color(0xFFF0F0EA)
private val Accent = Color(0xFF292A26)
private val BudgetInk = Color(0xFF777F6F)

enum class LedgerTab {
    LEDGER,
    STATISTICS,
}

sealed interface LedgerHomeState {
    data object Loading : LedgerHomeState
    data object Failed : LedgerHomeState
    data class Loaded(
        val summary: MonthlySummary,
        val entries: List<LedgerEntry>,
        val budgetCents: Long?,
        val pendingCount: Int,
        val accounts: List<LedgerAccount>,
        val expenseCategories: List<MonthlyCategoryTotal>,
        val incomeCategories: List<MonthlyCategoryTotal>,
        val categoryTags: List<String> = emptyList(),
        val commonTags: Map<EntryType, List<String>> = emptyMap(),
    ) : LedgerHomeState
}

@Composable
fun LedgerHomeScreen(
    month: YearMonth,
    state: LedgerHomeState,
    onMonthSelected: (YearMonth) -> Unit,
    onRetry: () -> Unit,
    onAddEntry: () -> Unit,
    onEditEntry: (LedgerEntry) -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPending: () -> Unit,
    onSaveBudget: (Long?) -> Unit,
) {
    var showBudgetEditor by remember(month) { mutableStateOf(false) }
    val loaded = state as? LedgerHomeState.Loaded
    Scaffold(
        containerColor = PageBackground,
        bottomBar = {
            LedgerBottomNavigation(
                selectedTab = LedgerTab.LEDGER,
                onLedger = {},
                onAddEntry = onAddEntry,
                onStatistics = onOpenStatistics,
            )
        },
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 12.dp),
        ) {
            Header(onOpenSettings)
            Spacer(Modifier.height(12.dp))
            MonthPicker(month = month, onMonthSelected = onMonthSelected)
            Spacer(Modifier.height(18.dp))

            when (state) {
                LedgerHomeState.Loading -> LoadingPanel()
                LedgerHomeState.Failed -> FailurePanel(onRetry)
                is LedgerHomeState.Loaded -> {
                    ExpenseOverview(state.summary)
                    Spacer(Modifier.height(21.dp))
                    BudgetStrip(
                        budgetCents = state.budgetCents,
                        expenseCents = state.summary.expenseCents,
                        onClick = { showBudgetEditor = true },
                    )
                    Spacer(Modifier.height(16.dp))
                    PendingCard(state.pendingCount, onOpenPending)
                    Spacer(Modifier.height(23.dp))
                    RecentEntries(state.entries, onEditEntry)
                }
            }
            Spacer(Modifier.height(22.dp))
        }
    }

    if (showBudgetEditor) {
        BudgetEditor(
            month = month,
            currentBudgetCents = loaded?.budgetCents,
            onDismiss = { showBudgetEditor = false },
            onSave = { amount ->
                onSaveBudget(amount)
                showBudgetEditor = false
            },
        )
    }
}

@Composable
fun LedgerBottomNavigation(
    selectedTab: LedgerTab,
    onLedger: () -> Unit,
    onAddEntry: () -> Unit,
    onStatistics: () -> Unit,
) {
    Surface(color = Color.White, shadowElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(65.dp)
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomItem(LedgerTab.LEDGER, "账本", selectedTab == LedgerTab.LEDGER, onLedger, Modifier.weight(1f))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onAddEntry),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(Accent, RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light)
                }
                Text("记一笔", color = MutedInk, fontSize = 10.sp)
            }
            BottomItem(LedgerTab.STATISTICS, "统计", selectedTab == LedgerTab.STATISTICS, onStatistics, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BottomItem(
    tab: LedgerTab,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BottomNavigationIcon(tab, selected)
        Text(label, color = if (selected) Ink else MutedInk, fontSize = 10.sp)
    }
}

@Composable
private fun BottomNavigationIcon(tab: LedgerTab, selected: Boolean) {
    val color = if (selected) Accent else MutedInk
    Canvas(
        modifier = Modifier
            .size(21.dp)
            .semantics { contentDescription = if (tab == LedgerTab.LEDGER) "账本" else "统计" },
    ) {
        val strokeWidth = 1.5.dp.toPx()
        val inset = 2.dp.toPx()
        if (tab == LedgerTab.LEDGER) {
            drawRect(
                color = color,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
                style = Stroke(width = strokeWidth),
            )
            drawLine(color, Offset(size.width / 3, inset), Offset(size.width / 3, size.height - inset), strokeWidth)
            drawLine(color, Offset(size.width * 2 / 3, inset), Offset(size.width * 2 / 3, size.height - inset), strokeWidth)
            drawLine(color, Offset(inset, size.height / 2), Offset(size.width - inset, size.height / 2), strokeWidth)
        } else {
            val bottom = size.height - inset
            drawLine(color, Offset(inset, inset), Offset(inset, bottom), strokeWidth)
            drawLine(color, Offset(inset, bottom), Offset(size.width - inset, bottom), strokeWidth)
            drawRect(color, Offset(size.width * .28f, size.height * .55f), androidx.compose.ui.geometry.Size(size.width * .14f, size.height * .3f))
            drawRect(color, Offset(size.width * .5f, size.height * .35f), androidx.compose.ui.geometry.Size(size.width * .14f, size.height * .5f))
            drawRect(color, Offset(size.width * .72f, size.height * .18f), androidx.compose.ui.geometry.Size(size.width * .14f, size.height * .67f))
        }
    }
}

@Composable
private fun Header(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("日常账本", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text("记下每一笔，留一点从容。", color = MutedInk, fontSize = 12.sp)
        }
        Box(
            modifier = Modifier
                .size(41.dp)
                .background(SoftSurface, CircleShape)
                .clickable(onClick = onOpenSettings),
            contentAlignment = Alignment.Center,
        ) {
            Text("账", color = Accent, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun MonthPicker(month: YearMonth, onMonthSelected: (YearMonth) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onMonthSelected(month.minusMonths(1)) }) {
                Text("‹", color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Light)
            }
            Text(
                text = "${month.year} 年 ${month.monthValue} 月",
                color = Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            TextButton(onClick = { onMonthSelected(month.plusMonths(1)) }) {
                Text("›", color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Light)
            }
        }
        Text("每天，都心中有数", color = MutedInk, fontSize = 11.sp)
    }
}

@Composable
private fun ExpenseOverview(summary: MonthlySummary) {
    Column(Modifier.fillMaxWidth()) {
        Text("本月支出", color = MutedInk, fontSize = 13.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            text = "¥ ${MoneyInput.formatCents(summary.expenseCents)}",
            color = Ink,
            fontSize = 40.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SecondaryAmount("本月收入", summary.incomeCents, Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(40.dp).background(Color(0xFFE9E9E3)))
            SecondaryAmount("本月结余", summary.balanceCents, Modifier.weight(1f).padding(start = 21.dp))
        }
    }
}

@Composable
private fun SecondaryAmount(label: String, amountCents: Long, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = MutedInk, fontSize = 12.sp)
        Spacer(Modifier.height(5.dp))
        Text(
            text = MoneyInput.formatCents(amountCents),
            color = Ink,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BudgetStrip(budgetCents: Long?, expenseCents: Long, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = budgetCents?.let { "月预算 ¥${MoneyInput.formatCents(it)}" } ?: "月预算  轻点设置",
                color = MutedInk,
                fontSize = 11.sp,
            )
            Text(
                text = budgetCents?.let { "还可支出 ¥${MoneyInput.formatCents(Math.subtractExact(it, expenseCents))}" }
                    ?: "设置预算 ›",
                color = Ink,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Color(0xFFE8E8E2), RoundedCornerShape(3.dp)),
        ) {
            if (budgetCents != null) {
                val progress = (expenseCents.toDouble() / budgetCents.toDouble()).coerceIn(0.0, 1.0).toFloat()
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(4.dp)
                        .background(BudgetInk, RoundedCornerShape(3.dp)),
                )
            }
        }
    }
}

@Composable
private fun PendingCard(count: Int, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = SoftSurface,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(25.dp)
                    .background(Accent, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(count.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (count == 0) "笔消费待确认" else "$count 笔消费待确认",
                color = Ink,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Text("去看看  ›", color = MutedInk, fontSize = 11.sp)
        }
    }
}

@Composable
private fun RecentEntries(entries: List<LedgerEntry>, onEditEntry: (LedgerEntry) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("最近记录", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(9.dp))
    if (entries.isEmpty()) {
        Surface(color = SoftSurface, shape = RoundedCornerShape(15.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("还没有记录", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(5.dp))
                Text("这是一本全新的本地账本", color = MutedInk, fontSize = 12.sp)
            }
        }
    } else {
        Column(Modifier.fillMaxWidth()) {
            entries.forEach { entry ->
                EntryRow(entry, onClick = { onEditEntry(entry) })
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFECECE7)))
            }
        }
    }
}

@Composable
private fun EntryRow(entry: LedgerEntry, onClick: () -> Unit) {
    val date = Instant.ofEpochMilli(entry.occurredAtEpochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    val prefix = when (entry.type) {
        EntryType.INCOME -> "+"
        EntryType.EXPENSE -> "−"
        EntryType.TRANSFER -> "↔"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.categoryTags.joinToString(" · "), color = Ink, fontSize = 13.sp,
                fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(
                text = "${date.monthValue}.${date.dayOfMonth.toString().padStart(2, '0')}${entry.memo.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()}",
                color = MutedInk,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "$prefix ${MoneyInput.formatCents(entry.amountCents)}",
            color = Ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun BudgetEditor(
    month: YearMonth,
    currentBudgetCents: Long?,
    onDismiss: () -> Unit,
    onSave: (Long?) -> Unit,
) {
    var amountText by remember(month) {
        mutableStateOf(currentBudgetCents?.let(MoneyInput::formatCents).orEmpty())
    }
    val parsed = MoneyInput.parseCents(amountText)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${month.monthValue} 月预算") },
        text = {
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.take(24) },
                label = { Text("金额") },
                prefix = { Text("¥ ") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(if (amountText.isBlank()) null else parsed) },
                enabled = amountText.isBlank() || parsed != null,
            ) { Text("保存") }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onSave(null) }) { Text("清除") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

@Composable
private fun LoadingPanel() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("正在读取本地账本…", color = MutedInk, fontSize = 14.sp)
    }
}

@Composable
private fun FailurePanel(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("暂时无法读取账本", color = Ink, fontSize = 15.sp)
        TextButton(onClick = onRetry) { Text("重试", color = Accent) }
    }
}
