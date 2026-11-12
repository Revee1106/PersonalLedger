package com.eric.personalledger.ui

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eric.personalledger.domain.EntryType
import com.eric.personalledger.domain.LedgerAccount
import com.eric.personalledger.domain.LedgerEntry
import com.eric.personalledger.domain.MoneyInput
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

private val FormBackground = Color(0xFFF6F5F1)
private val FormInk = Color(0xFF272824)
private val FormMuted = Color(0xFF85867F)
private val FormAccent = Color(0xFF30332D)

@Composable
fun LedgerEntryFormScreen(
    accounts: List<LedgerAccount>,
    initialEntry: LedgerEntry?,
    errorMessage: String?,
    isSaving: Boolean,
    onBack: () -> Unit,
    onSave: (LedgerEntry) -> Unit,
    onDelete: (LedgerEntry) -> Unit,
) {
    BackHandler(enabled = !isSaving, onBack = onBack)
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val originalTimestamp = remember(initialEntry?.id) {
        initialEntry?.occurredAtEpochMillis ?: System.currentTimeMillis()
    }
    val originalLocalTime = remember(initialEntry?.id) {
        Instant.ofEpochMilli(originalTimestamp).atZone(zone).toLocalTime()
    }
    var selectedDate by remember(initialEntry?.id) {
        mutableStateOf(Instant.ofEpochMilli(originalTimestamp).atZone(zone).toLocalDate())
    }
    var type by remember(initialEntry?.id) {
        mutableStateOf(initialEntry?.type ?: EntryType.EXPENSE)
    }
    var amountText by remember(initialEntry?.id) {
        mutableStateOf(initialEntry?.amountCents?.let(MoneyInput::formatCents).orEmpty())
    }
    var category by remember(initialEntry?.id) {
        mutableStateOf(initialEntry?.category ?: "餐饮")
    }
    var memo by remember(initialEntry?.id) { mutableStateOf(initialEntry?.memo.orEmpty()) }
    var accountId by remember(initialEntry?.id, accounts) {
        mutableStateOf(initialEntry?.accountId ?: accounts.firstOrNull()?.id ?: 0L)
    }
    var targetAccountId by remember(initialEntry?.id, accounts) {
        mutableStateOf(
            initialEntry?.transferTargetAccountId
                ?: accounts.firstOrNull { it.id != accountId }?.id
                ?: 0L,
        )
    }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val parsedCents = MoneyInput.parseCents(amountText)
    val trimmedCategory = category.trim()
    val isTransferValid = type != EntryType.TRANSFER ||
        (targetAccountId != 0L && targetAccountId != accountId)
    val canSave = parsedCents != null &&
        trimmedCategory.isNotEmpty() &&
        trimmedCategory.length <= 40 &&
        accountId != 0L &&
        accounts.any { it.id == accountId } &&
        isTransferValid

    Scaffold(containerColor = FormBackground) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onBack, enabled = !isSaving) { Text("‹ 返回", color = FormInk, fontSize = 15.sp) }
                Text(
                    text = if (initialEntry == null) "记一笔" else "编辑记录",
                    color = FormInk,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (initialEntry != null) {
                    TextButton(onClick = { showDeleteConfirmation = true }, enabled = !isSaving) {
                        Text("删除", color = Color(0xFF9A4742), fontSize = 14.sp)
                    }
                } else {
                    Spacer(Modifier.height(1.dp))
                }
            }
            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EntryType.entries.forEach { option ->
                    FilterChip(
                        selected = type == option,
                        onClick = {
                            type = option
                            if (option == EntryType.TRANSFER) category = "转账"
                            else if (category == "转账") category = if (option == EntryType.INCOME) "工资" else "餐饮"
                        },
                        label = { Text(option.displayName()) },
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            Text("金额", color = FormMuted, fontSize = 13.sp)
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.take(24) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("0.00") },
                prefix = { Text("¥ ", color = FormMuted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(16.dp),
            )
            if (amountText.isNotBlank() && parsedCents == null) {
                Text(
                    "请输入大于 0 且最多两位小数的金额",
                    color = Color(0xFF9A4742),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 5.dp, top = 5.dp),
                )
            }

            Spacer(Modifier.height(18.dp))
            Text("日期", color = FormMuted, fontSize = 13.sp)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 7.dp),
                color = Color.White,
                shape = RoundedCornerShape(16.dp),
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
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(selectedDate.toString(), color = FormInk, fontSize = 14.sp)
                    Text("选择日期  ›", color = FormMuted, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("分类", color = FormMuted, fontSize = 13.sp)
            OutlinedTextField(
                value = category,
                onValueChange = { category = it.take(40) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                placeholder = { Text("例如：餐饮、交通、工资") },
            )

            Spacer(Modifier.height(18.dp))
            Text(if (type == EntryType.TRANSFER) "转出账户" else "账户", color = FormMuted, fontSize = 13.sp)
            AccountPicker(
                accounts = accounts,
                selectedId = accountId,
                onSelect = { selected ->
                    accountId = selected
                    if (targetAccountId == selected) {
                        targetAccountId = accounts.firstOrNull { it.id != selected }?.id ?: 0L
                    }
                },
            )

            if (type == EntryType.TRANSFER) {
                Spacer(Modifier.height(14.dp))
                Text("转入账户", color = FormMuted, fontSize = 13.sp)
                AccountPicker(
                    accounts = accounts.filterNot { it.id == accountId },
                    selectedId = targetAccountId,
                    onSelect = { targetAccountId = it },
                )
                if (accounts.size < 2) {
                    Text("请先添加另一个账户，才能记录转账。", color = FormMuted, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("备注", color = FormMuted, fontSize = 13.sp)
            OutlinedTextField(
                value = memo,
                onValueChange = { memo = it.take(240) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                placeholder = { Text("添加备注（选填）") },
            )

            if (!errorMessage.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(errorMessage, color = Color(0xFF9A4742), fontSize = 13.sp)
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    val amountCents = parsedCents ?: return@Button
                    onSave(
                        LedgerEntry(
                            id = initialEntry?.id ?: UUID.randomUUID().toString(),
                            type = type,
                            amountCents = amountCents,
                            category = trimmedCategory,
                            accountId = accountId,
                            transferTargetAccountId = if (type == EntryType.TRANSFER) targetAccountId else null,
                            occurredAtEpochMillis = selectedDate.atTime(originalLocalTime)
                                .atZone(zone)
                                .toInstant()
                                .toEpochMilli(),
                            memo = memo.trim(),
                            candidateId = initialEntry?.candidateId,
                        ),
                    )
                },
                enabled = canSave && !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(if (isSaving) "保存中…" else "保存", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }

    if (showDeleteConfirmation && initialEntry != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("删除这笔记录？") },
            text = { Text("删除后，本地账本中的这笔记录会立即移除。") },
            confirmButton = {
                TextButton(
                    enabled = !isSaving,
                    onClick = { showDeleteConfirmation = false; onDelete(initialEntry) },
                ) {
                    Text("删除", color = Color(0xFF9A4742))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun AccountPicker(
    accounts: List<LedgerAccount>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = accounts.firstOrNull { it.id == selectedId }?.name ?: "选择账户"
    Box {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 7.dp),
            color = Color.White,
            shape = RoundedCornerShape(16.dp),
            onClick = { if (accounts.isNotEmpty()) expanded = true },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(selectedName, color = FormInk, fontSize = 14.sp)
                Text("⌄", color = FormMuted, fontSize = 16.sp)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account.name) },
                    onClick = { onSelect(account.id); expanded = false },
                )
            }
        }
    }
}

private fun EntryType.displayName(): String = when (this) {
    EntryType.EXPENSE -> "支出"
    EntryType.INCOME -> "收入"
    EntryType.TRANSFER -> "转账"
}
