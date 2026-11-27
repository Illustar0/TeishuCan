package com.illustar0.teishucan.ui.component

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.byValue
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

private const val MaxRechargeAmountDigits = 6

/** 只保留不带前导 0 的数字，最多 [maxDigits] 位。 */
fun positiveIntInput(maxDigits: Int): InputTransformation = InputTransformation.byValue { _, proposed ->
    proposed.filter { it in '0'..'9' }
        .dropWhile { it == '0' }
        .take(maxDigits)
}

val RechargeAmountInput = InputTransformation.byValue { _, proposed ->
    proposed.filter(Char::isDigit).take(MaxRechargeAmountDigits)
}

fun formatYuan(amount: Int): String =
    formatYuan(amount.toBigDecimal())

/** 校园卡以人民币计价，无论界面语言都按中国大陆的人民币格式显示。 */
fun formatYuan(amount: BigDecimal): String = NumberFormat.getCurrencyInstance(Locale.CHINA).format(amount)
