package com.illustar0.chibacan.ui.component

import java.text.NumberFormat

fun formatKilowattHours(kilowattHours: Double): String =
    NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }.format(kilowattHours)
