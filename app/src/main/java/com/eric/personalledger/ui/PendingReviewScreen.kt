package com.eric.personalledger.ui

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eric.personalledger.data.PendingLedgerCandidate
import com.eric.personalledger.domain.LedgerAccount
import com.eric.personalledger.domain.CategoryTags
import com.eric.personalledger.domain.EntryType
import com.eric.personalledger.domain.MoneyInput
import com.eric.personalledger.domain.ReviewReason
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Background = Color(0xFFFFFEFC)
private val Ink = Color(0xFF272824)
private val Muted = Color(0xFF85867F)
private val Card = Color(0xFFF0F0EA)
private val Error = Color(0xFF9A4742)
private val TimestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

@Composable
fun PendingReviewScreen(
    candidates: List<PendingLedgerCandidate>,
    accounts: List<LedgerAccount>,
    busyCandidateId: String?,
    loading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onConfirm: (PendingLedgerCandidate, Long, List<String>, Long, Long, String) -> Unit,
    onIgnore: (PendingLedgerCandidate) -> Unit,
    categoryOptions: List<String> = CategoryTags.catalog,
    commonTags: List<String> = CategoryTags.defaults(EntryType.EXPENSE),
    onSaveCommonTags: suspend (List<String>) -> Unit = {},
) {
    BackHandler(onBack = onBack)
    var candidateBeingConfirmed by remember { mutableStateOf<PendingLedgerCandidate?>(null) }
    Scaffold(containerColor = Background) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 12.dp),
        ) {
            TextButton(onClick = onBack, enabled = busyCandidateId == null) {
                Text("‹ 返回", color = Ink)
            }
            Spacer(Modifier.height(10.dp))
            Text("待确认", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(5.dp))
            Text("通知只提供线索。确认后才会写入本地账本。", color = Muted, fontSize = 12.sp)
            if (!errorMessage.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(errorMessage, color = Error, fontSize = 13.sp)
            }
            Spacer(Modifier.height(17.dp))
            if (loading) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = Ink)
                    Spacer(Modifier.height(10.dp))
                    Text("正在读取本地记录…", color = Muted, fontSize = 12.sp)
                }
            } else if (candidates.isEmpty()) {
                Surface(color = Card, shape = RoundedCornerShape(16.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 23.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("暂无待确认记录", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(5.dp))
                        Text("识别到的支付通知会显示在这里。", color = Muted, fontSize = 12.sp)
                    }
                }
            } else {
                candidates.forEach { candidate ->
                    PendingCandidateCard(
                        candidate = candidate,
                        isBusy = busyCandidateId != null,
                        onConfirm = { candidateBeingConfirmed = candidate },
                        onIgnore = { onIgnore(candidate) },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }

    candidateBeingConfirmed?.let { candidate ->
        CandidateConfirmationDialog(
            candidate = candidate,
            accounts = accounts,
            categoryOptions = categoryOptions,
            commonTags = commonTags,
            onSaveCommonTags = onSaveCommonTags,
            isBusy = busyCandidateId != null,
            onDismiss = { if (busyCandidateId == null) candidateBeingConfirmed = null },
            onConfirm = { amountCents, category, accountId, occurredAtEpochMillis, memo ->
                candidateBeingConfirmed = null
                onConfirm(candidate, amountCents, category, accountId, occurredAtEpochMillis, memo)
            },
        )
    }
}

@Composable
private fun PendingCandidateCard(
    candidate: PendingLedgerCandidate,
    isBusy: Boolean,
    onConfirm: () -> Unit,
    onIgnore: () -> Unit,
) {
    Surface(color = Card, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(15.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(candidate.sourceLabel, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(formatCandidateTime(candidate.receivedAtEpochMillis), color = Muted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = candidate.amountCents?.let { "¥ ${MoneyInput.formatCents(it)}" } ?: "金额待补充",
                color = Ink,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
            )
            candidate.reviewReason?.let { reason ->
                Spacer(Modifier.height(4.dp))
                Text(reason.displayText(), color = Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(11.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onIgnore, enabled = !isBusy) { Text("忽略", color = Muted) }
                TextButton(onClick = onConfirm, enabled = !isBusy) { Text("核对并记账", color = Ink) }
            }
        }
    }
}

@Composable
private fun CandidateConfirmationDialog(
    candidate: PendingLedgerCandidate,
    accounts: List<LedgerAccount>,
    categoryOptions: List<String>,
    commonTags: List<String>,
    onSaveCommonTags: suspend (List<String>) -> Unit,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Long, List<String>, Long, Long, String) -> Unit,
) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val eventTime = remember(candidate.id) {
        Instant.ofEpochMilli(candidate.receivedAtEpochMillis).atZone(zone)
    }
    var amountText by remember(candidate.id) {
        mutableStateOf(candidate.amountCents?.let(MoneyInput::formatCents).orEmpty())
    }
    var categories by rememberSaveable(candidate.id, stateSaver = CategoryTagListSaver) { mutableStateOf(listOf(candidate.category)) }
    var memo by remember(candidate.id) { mutableStateOf("") }
    var accountId by remember(candidate.id, accounts) { mutableStateOf(accounts.firstOrNull()?.id ?: 0L) }
    var selectedDate by remember(candidate.id) { mutableStateOf(eventTime.toLocalDate()) }
    val amountCents = MoneyInput.parseCents(amountText)
    val safeCategories = runCatching { CategoryTags.normalize(categories) }.getOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认这笔支出") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("来源：${candidate.sourceLabel}", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.take(24) },
                    label = { Text("金额") },
                    prefix = { Text("¥ ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Spacer(Modifier.height(9.dp))
                CategoryTagField(categories, categoryOptions, commonTags, { categories = it.toList() }, onSaveCommonTags)
                Spacer(Modifier.height(9.dp))
                Text("日期", color = Muted, fontSize = 12.sp)
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                    color = Color(0xFFF0F0EA),
                    shape = RoundedCornerShape(12.dp),
                    onClick = {
                        DatePickerDialog(
                            context,
                            { _, year, month, day -> selectedDate = LocalDate.of(year, month + 1, day) },
                            selectedDate.year,
                            selectedDate.monthValue - 1,
                            selectedDate.dayOfMonth,
                        ).show()
                    },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(selectedDate.toString(), color = Ink, fontSize = 13.sp)
                        Text("选择日期  ›", color = Muted, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it.take(240) },
                    label = { Text("备注（选填）") },
                    singleLine = true,
                )
                Spacer(Modifier.height(9.dp))
                Text("消费平台", color = Muted, fontSize = 12.sp)
                AccountPicker(accounts, accountId, { accountId = it })
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isBusy && amountCents != null && safeCategories != null && accounts.any { it.id == accountId },
                onClick = {
                    amountCents?.let {
                        val occurredAt = selectedDate.atTime(eventTime.toLocalTime())
                            .atZone(zone)
                            .toInstant()
                            .toEpochMilli()
                        onConfirm(it, safeCategories!!, accountId, occurredAt, memo.trim())
                    }
                },
            ) { Text(if (isBusy) "正在保存…" else "确认记账", color = Ink) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) { Text("取消", color = Muted) }
        },
    )
}

private fun formatCandidateTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(TimestampFormatter)

private fun ReviewReason.displayText(): String = when (this) {
    ReviewReason.AMOUNT_HIDDEN_OR_MISSING -> "通知未提供可确认的金额，请手动补充。"
    ReviewReason.MULTIPLE_AMOUNTS -> "通知包含多个金额，请核对后填写实际支出。"
    ReviewReason.INVALID_AMOUNT -> "金额格式无法确认，请核对后填写。"
}
