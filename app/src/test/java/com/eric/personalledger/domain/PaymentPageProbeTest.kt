package com.eric.personalledger.domain

import org.junit.Assert.*
import org.junit.Test

class PaymentPageProbeTest {
    private val taobao = "com.taobao.taobao"

    @Test fun successAndSplitCurrencyAreOnlyHints() {
        val hints = PaymentPageProbe.analyze(taobao, listOf("支付成功", "￥", "12.50", "原价￥20.00"))
        assertEquals(listOf("支付成功"), hints.successLabels)
        assertEquals(listOf(1250L, 2000L), hints.amountCandidates)
    }

    @Test fun unrelatedPackagesAndUnlabelledNumbersAreIgnored() {
        assertEquals(PaymentPageHints(), PaymentPageProbe.analyze("other.app", listOf("支付成功", "￥12.50")))
        assertTrue(PaymentPageProbe.analyze(taobao, listOf("订单号123456", "12.50")).amountCandidates.isEmpty())
    }

    @Test fun instructionsAndUnpaidOrderAreNotSuccess() {
        val hints = PaymentPageProbe.analyze(taobao, listOf("支付成功后可查看订单", "待付款", "￥15.00"))
        assertTrue(hints.successLabels.isEmpty())
        assertEquals(listOf("待付款"), hints.otherStateLabels)
    }

    @Test fun passwordAndVerificationPagesHaveNoContentHints() {
        for (texts in listOf(listOf("支付密码", "￥12.50"), listOf("验证码", "支付成功"))) {
            assertEquals(PaymentPageHints(sensitivePage = true), PaymentPageProbe.analyze(taobao, texts))
        }
        assertEquals(PaymentPageHints(sensitivePage = true), PaymentPageProbe.analyze(taobao, listOf("￥12.50"), true))
    }

    @Test fun actualPaymentLabelsAndCurrencyCanSpanNodes() {
        val hints = PaymentPageProbe.analyze(taobao, listOf("付款成功！", "实付款", "12.50"))
        assertEquals(listOf("付款成功"), hints.successLabels)
        assertEquals(listOf(1250L), hints.amountCandidates)
    }

    @Test fun malformedAmountsAreNotPartiallyParsed() {
        val hints = PaymentPageProbe.analyze(taobao, listOf("￥12.345", "￥1,234.56", "￥0.00"))
        assertTrue(hints.amountCandidates.isEmpty())
    }

    @Test fun refundsAndTransfersRemainVisibleAsOtherStates() {
        for (state in listOf("退款成功", "转账成功", "支付失败", "交易关闭")) {
            val hints = PaymentPageProbe.analyze(taobao, listOf(state, "￥10.00"))
            assertTrue(hints.successLabels.isEmpty())
            assertEquals(listOf(state), hints.otherStateLabels)
        }
    }

    @Test fun stoppedExpiredAndOldSessionsRejectInFlightResults() {
        val session = PageProbeSession().start(1000)
        val sample = sample()
        assertTrue(session.stop().record(session.generation, 2000, sample).samples.isEmpty())
        assertTrue(session.record(session.generation, 301000, sample).samples.isEmpty())
        assertTrue(session.start(2000).record(session.generation, 3000, sample).samples.isEmpty())
        assertTrue(session.record(session.generation, 2000, sample.copy(packageName = "other.app")).samples.isEmpty())
    }

    @Test fun historyIsBoundedAndNewSessionOrClearRemovesPreviousAmounts() {
        var session = PageProbeSession().start(1000)
        repeat(30) { index ->
            session = session.record(session.generation, 2000, sample().copy(readableNodeCount = index))
        }
        assertEquals(20, session.samples.size)
        assertEquals(29, session.samples.first().readableNodeCount)
        assertTrue(session.start(3000).samples.isEmpty())
        assertTrue(session.stop(clear = true).samples.isEmpty())
    }

    private fun sample() = PageProbeSample(taobao, 1000, 3, PaymentPageHints(amountCandidates = listOf(1250)))
}
