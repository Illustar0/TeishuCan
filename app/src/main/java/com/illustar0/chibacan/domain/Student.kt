package com.illustar0.chibacan.domain

import java.math.BigDecimal
import kotlinx.serialization.Serializable

/** 支付一笔充值的方式，在网页 [checkoutUrl] 上完成。该地址含支付凭据，不得写入日志。 */
data class PaymentChannel(val name: String, val checkoutUrl: String)

/** 等待通过某个 [channels] 支付的校园卡充值订单。 */
data class RechargeOrder(val amountYuan: Int, val channels: List<PaymentChannel>)

/** 学生信息；各字段在首次查询完成之前为 null。 */
@Serializable
data class StudentData(
    @Serializable(with = BigDecimalAsStringSerializer::class)
    val campusCardBalanceYuan: BigDecimal? = null,
    val mailCount: Int? = null,
    val researchProjectCount: Int? = null,
)
