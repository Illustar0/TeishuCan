package com.illustar0.teishucan.ui.component

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.time.format.DateTimeFormatter

/** 带年份的日期，如学期起止。 */
const val DateWithYearSkeleton = "yMd"

/** 不带年份的日期，如一周的起止。 */
const val DateSkeleton = "Md"

/** 按当前语言习惯格式化 [skeleton]（[DateFormat.getBestDateTimePattern] 的写法）所含的字段。 */
@Composable
fun rememberLocalizedFormatter(skeleton: String): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(skeleton, locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    }
}

/** 课程时刻的格式，跟随系统的 12/24 小时制设置。 */
@Composable
fun rememberClassTimeFormatter(): DateTimeFormatter {
    val context = LocalContext.current
    val is24Hour = remember(context) { DateFormat.is24HourFormat(context) }
    return rememberLocalizedFormatter(if (is24Hour) "Hm" else "hm")
}
