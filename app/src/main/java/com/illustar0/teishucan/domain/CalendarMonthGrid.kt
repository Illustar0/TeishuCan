package com.illustar0.teishucan.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.yearMonth

const val CalendarGridRows: Int = 6
const val CalendarGridDaysPerWeek: Int = 7
const val CalendarGridCellCount: Int = CalendarGridRows * CalendarGridDaysPerWeek

data class CalendarDayCellData(
    val date: LocalDate,
    val isInDisplayedMonth: Boolean,
)

/**
 * 生成 [month] 的 6 行 × 7 列（周一至周日）月历，首尾用前后邻月补齐。
 * 固定 6 行后，5 行月与 6 行月之间翻页不会跳动高度。
 */
fun buildCalendarMonthGrid(month: YearMonth): List<CalendarDayCellData> {
    val firstOfMonth = month.firstDay
    val leadingDays = firstOfMonth.dayOfWeek.isoDayNumber - 1
    val gridStart = firstOfMonth.minus(leadingDays, DateTimeUnit.DAY)
    return List(CalendarGridCellCount) { offset ->
        val date = gridStart.plus(offset, DateTimeUnit.DAY)
        CalendarDayCellData(
            date = date,
            isInDisplayedMonth = date.yearMonth == month,
        )
    }
}
