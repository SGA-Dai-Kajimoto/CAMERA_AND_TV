package com.sony.dtv.camera_tv.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MonthGridTest {

    @Test
    fun `月曜始まりの先頭セルが正しく空く`() {
        // 2026年5月1日は金曜 → 月曜始まりでは 5 列目（index 4）
        val grid = MonthGrid.of(YearMonth.of(2026, 5))
        assertEquals(List(4) { null }, grid[0].take(4))
        assertEquals(LocalDate.of(2026, 5, 1), grid[0][4])
    }

    @Test
    fun `常に 6 行 7 列になる`() {
        val grid = MonthGrid.of(YearMonth.of(2026, 2))
        assertEquals(MonthGrid.ROWS, grid.size)
        grid.forEach { assertEquals(MonthGrid.COLUMNS, it.size) }
    }

    @Test
    fun `その月の日数だけ非 null セルがある`() {
        val month = YearMonth.of(2026, 5)
        val days = MonthGrid.of(month).flatten().filterNotNull()
        assertEquals(month.lengthOfMonth(), days.size)
        assertEquals(month.atDay(1), days.first())
        assertEquals(month.atEndOfMonth(), days.last())
    }

    @Test
    fun `うるう年の 2 月も扱える`() {
        val days = MonthGrid.of(YearMonth.of(2024, 2)).flatten().filterNotNull()
        assertEquals(29, days.size)
    }

    @Test
    fun `positionOf はグリッド上の位置と一致する`() {
        val month = YearMonth.of(2026, 5)
        val grid = MonthGrid.of(month)
        val date = LocalDate.of(2026, 5, 20)
        val (row, col) = MonthGrid.positionOf(month, date)!!
        assertEquals(date, grid[row][col])
    }

    @Test
    fun `positionOf は別の月なら null`() {
        assertNull(MonthGrid.positionOf(YearMonth.of(2026, 5), LocalDate.of(2026, 6, 1)))
    }

    @Test
    fun `曜日ラベルは月曜始まりの 7 個`() {
        assertEquals(MonthGrid.COLUMNS, MonthGrid.DAY_LABELS.size)
        assertEquals("月", MonthGrid.DAY_LABELS.first())
        assertEquals("日", MonthGrid.DAY_LABELS.last())
    }
}
