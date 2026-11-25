package com.illustar0.teishucan.ui.component

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.toJavaDayOfWeek

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

/** [day] 在当前语言下以 [style] 写出的名称，如「星期一」或「周一」。 */
@Composable
@ReadOnlyComposable
fun localizedDayName(day: DayOfWeek, style: TextStyle): String =
    day.toJavaDayOfWeek().getDisplayName(style, LocalConfiguration.current.locales[0])

/** 课程时刻的格式，跟随系统的 12/24 小时制设置。 */
@Composable
fun rememberClassTimeFormatter(): DateTimeFormatter {
    val context = LocalContext.current
    val is24Hour = remember(context) { DateFormat.is24HourFormat(context) }
    return rememberLocalizedFormatter(if (is24Hour) "Hm" else "hm")
}
