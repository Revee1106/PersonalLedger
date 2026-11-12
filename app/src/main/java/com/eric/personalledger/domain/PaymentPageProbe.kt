package com.eric.personalledger.domain

/** Diagnostic hints only: these are neither a transaction nor proof of a payment. */
data class PaymentPageHints(
    val successLabels: List<String> = emptyList(),
    val otherStateLabels: List<String> = emptyList(),
    val amountCandidates: List<Long> = emptyList(),
    val sensitivePage: Boolean = false,
)

object PaymentPageProbe {
    val supportedApps = linkedMapOf(
        "com.taobao.taobao" to "淘宝",
        "com.jingdong.app.mall" to "京东",
        "com.xingin.xhs" to "小红书",
        "com.ss.android.ugc.aweme" to "抖音",
        "com.tencent.mm" to "微信",
        "com.eg.android.AlipayGphone" to "支付宝",
    )
    private val successLabels = listOf("支付成功", "付款成功", "交易成功", "订单支付成功", "订单付款成功")
    private val otherLabels = listOf("待付款", "等待付款", "支付失败", "付款失败", "交易关闭", "订单已取消", "取消支付", "退款成功", "退款中", "转账成功")
    private val sensitiveLabels = listOf("支付密码", "输入密码", "验证码", "人脸识别", "指纹验证", "安全验证")
    private val currencyAmount = Regex("[¥￥]\\s*([0-9]+(?:\\.[0-9]{1,2})?)(?![0-9.,])")
    private val labelledAmount = Regex("(?:实付款|实付金额|实付|付款金额|支付金额|合计)\\s*[:：]?\\s*[¥￥]?\\s*([0-9]+(?:\\.[0-9]{1,2})?)(?![0-9.,])")

    fun analyze(packageName: String, texts: List<String>, hasPasswordField: Boolean = false): PaymentPageHints {
        if (packageName !in supportedApps) return PaymentPageHints()
        // Text is bounded by the service, and is discarded as soon as hints are extracted.
        val bounded = texts.take(240).map { it.take(256) }
        if (hasPasswordField || bounded.any { text -> sensitiveLabels.any(text::contains) }) {
            return PaymentPageHints(sensitivePage = true)
        }
        val normalized = bounded.map { it.filterNot(Char::isWhitespace).trim('!', '！', '。', '.', '：', ':') }
        val joined = bounded.joinToString("\n")
        val amounts = (currencyAmount.findAll(joined) + labelledAmount.findAll(joined))
            .mapNotNull { MoneyInput.parseCents(it.groupValues[1]) }
            .distinct().take(8).toList()
        return PaymentPageHints(
            successLabels = successLabels.filter { it in normalized },
            otherStateLabels = otherLabels.filter { label -> normalized.any { it.contains(label) } },
            amountCandidates = amounts,
        )
    }
}

data class PageProbeSample(
    val packageName: String,
    val capturedAtMillis: Long,
    val readableNodeCount: Int,
    val hints: PaymentPageHints,
    val limited: Boolean = false,
    val unavailable: Boolean = false,
)

data class PageProbeSession(
    val generation: Long = 0,
    val running: Boolean = false,
    val endsAtElapsedMillis: Long = 0,
    val samples: List<PageProbeSample> = emptyList(),
) {
    fun isActive(nowElapsedMillis: Long) = running && nowElapsedMillis < endsAtElapsedMillis

    fun start(nowElapsedMillis: Long) = PageProbeSession(
        generation = generation + 1,
        running = true,
        endsAtElapsedMillis = nowElapsedMillis + 5 * 60_000L,
    )

    fun stop(clear: Boolean = false) = copy(running = false, samples = if (clear) emptyList() else samples)

    fun record(sessionGeneration: Long, nowElapsedMillis: Long, sample: PageProbeSample): PageProbeSession {
        if (sessionGeneration != generation || !isActive(nowElapsedMillis) ||
            sample.packageName !in PaymentPageProbe.supportedApps) return this
        val last = samples.firstOrNull()
        val repeated = last != null && last.copy(capturedAtMillis = sample.capturedAtMillis) == sample
        return copy(samples = (listOf(sample) + if (repeated) samples.drop(1) else samples).take(20))
    }
}
