package com.illustar0.chibacan.ui.component

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

private const val MaxRechargeAmountDigits = 6

fun formatYuan(amount: Int): String =
    formatYuan(amount.toBigDecimal())

fun formatYuan(amount: BigDecimal): String =
    "¥" + NumberFormat.getNumberInstance(Locale.CHINA).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(amount)

/** 把用户输入收敛成合法的充值金额文本。 */
fun rechargeAmountInput(input: String): String = input.filter(Char::isDigit).take(MaxRechargeAmountDigits)
