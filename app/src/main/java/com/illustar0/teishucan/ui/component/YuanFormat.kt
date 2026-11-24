package com.illustar0.teishucan.ui.component

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.byValue
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

private const val MaxRechargeAmountDigits = 6

val RechargeAmountInput = InputTransformation.byValue { _, proposed ->
    proposed.filter(Char::isDigit).take(MaxRechargeAmountDigits)
}

fun formatYuan(amount: Int): String =
    formatYuan(amount.toBigDecimal())

fun formatYuan(amount: BigDecimal): String =
    "¥" + NumberFormat.getNumberInstance(Locale.CHINA).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(amount)
