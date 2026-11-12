package com.eric.personalledger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eric.personalledger.accessibility.PageProbeStore
import com.eric.personalledger.domain.MoneyInput
import com.eric.personalledger.domain.PaymentPageProbe
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun PageProbeScreen(
    accessEnabled: Boolean,
    errorMessage: String?,
    onOpenAccessibilitySettings: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val session by PageProbeStore.session.collectAsState()
    val connected by PageProbeStore.connected.collectAsState()
    val ink = Color(0xFF272824)
    Scaffold(containerColor = Color(0xFFFFFEFC)) { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = onBack) { Text("‹ 返回", color = ink) }
            Text("页面读取验证", color = ink, fontSize = 25.sp, fontWeight = FontWeight.Medium)
            Text(
                "验证淘宝、京东、小红书、抖音、微信和支付宝当前页面能否提供付款信息。支持范围仍待你的真机测试。",
                color = ink, fontSize = 14.sp,
            )
            Text(
                "开启服务并点击开始后，会读取这六个 App 的可见页面文字，可能接触聊天、订单等内容。仅在本机内存保留状态词与金额候选，最多二十条摘要；不保存原文、不上传。密码或验证页面跳过。",
                color = Color(0xFF66675F), fontSize = 13.sp,
            )
            Text(
                "每次最长五分钟，到时停止读取。切回购物 App 查看付款结果，再回这里停止并查看。测试期间请避免浏览无关页面。停止后摘要暂留，清除或进程结束后消失。",
                color = Color(0xFF66675F), fontSize = 13.sp,
            )
            Text(
                when {
                    !accessEnabled -> "系统服务：未授权"
                    !connected -> "系统服务：已授权，等待连接"
                    else -> "系统服务：已连接"
                }, color = ink,
            )
            Text(if (session.running) "验证进行中 · 五分钟内自动停止" else "验证已停止", color = ink)
            if (!errorMessage.isNullOrBlank()) Text(errorMessage, color = Color(0xFF9A4742))
            Button(onClick = onOpenAccessibilitySettings, modifier = Modifier.fillMaxWidth()) {
                Text(if (accessEnabled) "管理无障碍服务" else "前往系统开启服务")
            }
            if (session.running) {
                Button(onClick = { PageProbeStore.stop() }, modifier = Modifier.fillMaxWidth()) {
                    Text("停止读取并查看结果")
                }
            } else {
                Button(
                    onClick = { PageProbeStore.start() },
                    enabled = accessEnabled && connected,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("同意读取，开始五分钟验证") }
            }
            TextButton(onClick = { PageProbeStore.stop(clear = true) }, modifier = Modifier.fillMaxWidth()) {
                Text("停止并清除结果")
            }
            Text("读取结果（仅用于验证）", color = ink, fontWeight = FontWeight.SemiBold)
            Text(
                "成功词只是页面文字，金额可能是商品价格、原价或优惠金额，不能据此确定实付。本功能不会生成待确认记录或账目。",
                color = Color(0xFF66675F), fontSize = 13.sp,
            )
            if (session.samples.isEmpty()) {
                Text("尚无结果。开始后切换到目标 App；没有结果不代表没有付款。", color = ink)
            }
            session.samples.forEach { sample ->
                Surface(color = Color(0xFFF0F0EA), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val time = Instant.ofEpochMilli(sample.capturedAtMillis).atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                        Text("${PaymentPageProbe.supportedApps[sample.packageName]} · $time", color = ink, fontWeight = FontWeight.SemiBold)
                        when {
                            sample.unavailable -> Text("收到页面事件，但当前页面无法读取。", color = ink)
                            sample.hints.sensitivePage -> Text("检测到密码或验证页面，已跳过内容展示。", color = ink)
                            else -> {
                                Text("可见节点：${sample.readableNodeCount}${if (sample.limited) "（仅采样部分内容）" else ""}", color = ink)
                                Text("成功词：${sample.hints.successLabels.joinToString("、").ifBlank { "未读到" }}", color = ink)
                                Text("其他状态：${sample.hints.otherStateLabels.joinToString("、").ifBlank { "未读到" }}", color = ink)
                                val amounts = sample.hints.amountCandidates.joinToString("、") { "¥${MoneyInput.formatCents(it)}" }
                                Text("金额候选（最多八个）：${amounts.ifBlank { "未读到" }}", color = ink)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
