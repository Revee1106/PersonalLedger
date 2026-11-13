package com.eric.personalledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eric.personalledger.domain.EntryType
import com.eric.personalledger.domain.MonthlyCategoryTotal
import com.eric.personalledger.domain.MoneyInput
import java.time.YearMonth

private val StatisticsBackground = Color(0xFFFFFEFC)
private val StatisticsInk = Color(0xFF272824)
private val StatisticsMuted = Color(0xFF898A83)
private val StatisticsTrack = Color(0xFFE9E9E3)
private val StatisticsFill = Color(0xFF777F6F)

@Composable
fun LedgerStatisticsScreen(
    month: YearMonth,
    state: LedgerHomeState,
    onMonthSelected: (YearMonth) -> Unit,
    onRetry: () -> Unit,
    onOpenLedger: () -> Unit,
    onAddEntry: () -> Unit,
) {
    var selectedType by remember(month) { mutableStateOf(EntryType.EXPENSE) }
    Scaffold(
        containerColor = StatisticsBackground,
        bottomBar = {
            LedgerBottomNavigation(
                selectedTab = LedgerTab.STATISTICS,
                onLedger = onOpenLedger,
                onAddEntry = onAddEntry,
                onStatistics = {},
            )
        },
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 14.dp),
        ) {
            Text("收支统计", color = StatisticsInk, fontSize = 23.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onMonthSelected(month.minusMonths(1)) }) { Text("‹", fontSize = 22.sp) }
                    Text("${month.year} 年 ${month.monthValue} 月", color = StatisticsInk, fontSize = 15.sp)
                    TextButton(onClick = { onMonthSelected(month.plusMonths(1)) }) { Text("›", fontSize = 22.sp) }
                }
                Text("本地账本", color = StatisticsMuted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedType == EntryType.EXPENSE,
                    onClick = { selectedType = EntryType.EXPENSE },
                    label = { Text("支出") },
                )
                FilterChip(
                    selected = selectedType == EntryType.INCOME,
                    onClick = { selectedType = EntryType.INCOME },
                    label = { Text("收入") },
                )
            }
            Spacer(Modifier.height(17.dp))
            when (state) {
                LedgerHomeState.Loading -> Text("正在读取本地账本…", color = StatisticsMuted)
                LedgerHomeState.Failed -> TextButton(onClick = onRetry) { Text("读取失败，点击重试") }
                is LedgerHomeState.Loaded -> {
                    val amount = if (selectedType == EntryType.EXPENSE) {
                        state.summary.expenseCents
                    } else {
                        state.summary.incomeCents
                    }
                    val categories = if (selectedType == EntryType.EXPENSE) {
                        state.expenseCategories
                    } else {
                        state.incomeCategories
                    }
                    Surface(color = Color.White, shape = RoundedCornerShape(17.dp)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp)) {
                            Text(
                                if (selectedType == EntryType.EXPENSE) "本月累计支出" else "本月累计收入",
                                color = StatisticsMuted,
                                fontSize = 12.sp,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "¥ ${MoneyInput.formatCents(amount)}",
                                color = StatisticsInk,
                                fontSize = 31.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(18.dp))
                            Text("标签统计", color = StatisticsInk, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(6.dp))
                            Text("多标签记录会计入各标签，标签金额可能重叠；总收支不重复计算。",
                                color = StatisticsMuted, fontSize = 12.sp)
                            Spacer(Modifier.height(15.dp))
                            if (categories.isEmpty()) {
                                Text("本月还没有${if (selectedType == EntryType.EXPENSE) "支出" else "收入"}记录", color = StatisticsMuted, fontSize = 13.sp)
                            } else {
                                CategoryRows(categories)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun CategoryRows(categories: List<MonthlyCategoryTotal>) {
    val largest = categories.maxOfOrNull { it.amountCents }?.coerceAtLeast(1L) ?: 1L
    Column(verticalArrangement = Arrangement.spacedBy(17.dp)) {
        categories.forEach { category ->
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(category.category, color = StatisticsInk, fontSize = 13.sp)
                    Text("¥ ${MoneyInput.formatCents(category.amountCents)}", color = StatisticsInk, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(7.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .background(StatisticsTrack, RoundedCornerShape(4.dp)),
                ) {
                    val progress = (category.amountCents.toDouble() / largest.toDouble()).coerceIn(0.0, 1.0).toFloat()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(5.dp)
                            .background(StatisticsFill, RoundedCornerShape(4.dp)),
                    )
                }
            }
        }
    }
}
