package com.sony.dtv.camera_tv.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * カレンダー表示用の月グリッド（純粋関数）。
 * 月曜始まり・6行×7列で、その月に属さないセルは null。
 */
object MonthGrid {

    const val ROWS = 6
    const val COLUMNS = 7

    /** 月曜始まりの曜日ラベル。 */
    val DAY_LABELS: List<String> = listOf("月", "火", "水", "木", "金", "土", "日")

    fun of(month: YearMonth): List<List<LocalDate?>> {
        // DayOfWeek.MONDAY.value == 1 なので -1 で 0 始まりの列に変換する
        val leading = month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value
        val lastDay = month.lengthOfMonth()

        return List(ROWS) { row ->
            List(COLUMNS) { col ->
                val day = row * COLUMNS + col - leading + 1
                if (day in 1..lastDay) month.atDay(day) else null
            }
        }
    }

    /** 指定日がグリッド上のどのセルにあるかを返す。月が違う場合は null。 */
    fun positionOf(month: YearMonth, date: LocalDate): Pair<Int, Int>? {
        if (YearMonth.from(date) != month) return null
        val leading = month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value
        val index = leading + date.dayOfMonth - 1
        return (index / COLUMNS) to (index % COLUMNS)
    }
}
