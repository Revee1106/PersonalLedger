package com.eric.personalledger.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Background = Color(0xFFFFFEFC)
private val Ink = Color(0xFF272824)
private val Muted = Color(0xFF85867F)
private val Card = Color(0xFFF0F0EA)

@Composable
fun LedgerSettingsScreen(
    widgetMessage: String?,
    onAddDesktopWidget: () -> Unit,
    notificationAccessEnabled: Boolean,
    reminderPermissionGranted: Boolean,
    remindersEnabled: Boolean,
    errorMessage: String?,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppNotificationSettings: () -> Unit,
    onReminderPermissionResult: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { onReminderPermissionResult() }
    Scaffold(containerColor = Background) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 12.dp),
        ) {
            TextButton(onClick = onBack) { Text("‹ 返回", color = Ink) }
            Spacer(Modifier.height(12.dp))
            Text("设置", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Medium)
            if (!errorMessage.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(errorMessage, color = Color(0xFF9A4742), fontSize = 12.sp)
            }
            Spacer(Modifier.height(19.dp))
            Surface(color = Card, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(17.dp)) {
                    Text("桌面记账卡片", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "在手机桌面放一张「记一笔」卡片，点击直接输入金额，无需额外授权。",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onAddDesktopWidget, modifier = Modifier.fillMaxWidth()) {
                        Text("添加到桌面")
                    }
                    Text(
                        widgetMessage ?: "也可长按桌面空白处，从小组件或窗口小工具中选择本应用的「记一笔」。",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Surface(color = Card, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(17.dp)) {
                    Text("账户", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("本地模式", color = Ink, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "手机号登录和微信登录尚未配置，账本仍可在本机正常使用。",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Surface(color = Card, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(17.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("通知辅助记账", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (notificationAccessEnabled) "已开启" else "未开启",
                            color = if (notificationAccessEnabled) Ink else Muted,
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "作为补充渠道，仅尝试识别微信支付和支付宝已发出的系统通知。没有系统通知的付款无法通过此入口识别。识别结果先进入待确认列表。",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onOpenNotificationSettings,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (notificationAccessEnabled) "管理通知访问" else "前往系统设置开启")
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Surface(color = Card, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(17.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("应用内提醒", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                !reminderPermissionGranted -> "未授权"
                                remindersEnabled -> "已开启"
                                else -> "系统已关闭"
                            },
                            color = if (remindersEnabled) Ink else Muted,
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "发现待确认记录时发送一条通用提醒，锁屏不显示金额或交易内容。若不允许提醒，候选仍会保存在应用内。",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    if (Build.VERSION.SDK_INT >= 33 && !reminderPermissionGranted) {
                        Button(
                            onClick = {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("允许发送本地提醒")
                        }
                    }
                    TextButton(
                        onClick = onOpenAppNotificationSettings,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("管理提醒设置") }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "手动记账无需开启通知访问。应用不读取其他应用的页面或短信，不使用悬浮窗。",
                color = Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 3.dp),
            )
        }
    }
}
