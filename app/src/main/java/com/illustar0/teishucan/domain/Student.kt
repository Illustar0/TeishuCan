package com.illustar0.teishucan.domain

/** 支付一笔充值的方式，在网页 [checkoutUrl] 上完成。该地址含支付凭据，不得写入日志。 */
data class PaymentChannel(val name: String, val checkoutUrl: String)

data class RechargeOrder(val amountYuan: Int, val channels: List<PaymentChannel>)
