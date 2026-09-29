package com.sony.dtv.camera_tv.domain

import com.sony.dtv.camera_tv.data.model.Content
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ContentGroupingTest {

    private fun content(id: String, date: String?, rating: Int? = null) = Content(
        contentId = id,
        recordedDate = date?.let { "${it}T12:00:00+09:00" },
        tags = rating?.let { listOf(ContentRating.toTag(it)) },
    )

    private val day1 = LocalDate.of(2026, 5, 1)
    private val day2 = LocalDate.of(2026, 5, 2)

    private val contents = listOf(
        content("a", "2026-05-01", rating = 3),
        content("b", "2026-05-02", rating = 5),
        content("c", "2026-05-01", rating = 1),
        content("d", date = null),
    )

    // ---- SortMode ---- //

    @Test
    fun `toggled は日付順と評価順を往復する`() {
        assertEquals(SortMode.RatingDesc, SortMode.DateDesc.toggled())
        assertEquals(SortMode.DateDesc, SortMode.RatingDesc.toggled())
    }

    // ---- availableDates ---- //

    @Test
    fun `availableDates は重複を除いた新しい順で日付不明を含まない`() {
        assertEquals(listOf(day2, day1), ContentGrouping.availableDates(contents))
    }

    @Test
    fun `availableDates は空リストで空を返す`() {
        assertEquals(emptyList<LocalDate>(), ContentGrouping.availableDates(emptyList()))
    }

    // ---- groupByDate ---- //

    @Test
    fun `groupByDate は日付ヘッダーを挟んで新しい順に並べる`() {
        val items = ContentGrouping.groupByDate(contents)

        val headers = items.filterIsInstance<ContentListItem.DateHeader>()
        assertEquals(listOf("2026年5月2日", "2026年5月1日", ContentGrouping.UNKNOWN_DATE_LABEL), headers.map { it.label })

        // 先頭は最新日のヘッダー、その直後にその日のコンテンツが並ぶ
        assertTrue(items.first() is ContentListItem.DateHeader)
        assertEquals("b", (items[1] as ContentListItem.ContentItem).content.contentId)
    }

    @Test
    fun `groupByDate は日付不明を最後にまとめる`() {
        val items = ContentGrouping.groupByDate(contents)
        val last = items.last() as ContentListItem.ContentItem
        assertEquals("d", last.content.contentId)
    }

    @Test
    fun `groupByDate は空リストで空を返す`() {
        assertEquals(emptyList<ContentListItem>(), ContentGrouping.groupByDate(emptyList()))
    }

    // ---- visibleContents ---- //

    @Test
    fun `DateDesc は選択日のコンテンツだけを返す`() {
        val visible = ContentGrouping.visibleContents(contents, SortMode.DateDesc, day1)
        assertEquals(listOf("a", "c"), visible.map { it.contentId })
    }

    @Test
    fun `DateDesc で選択日が無い場合は全件を返す`() {
        val visible = ContentGrouping.visibleContents(contents, SortMode.DateDesc, null)
        assertEquals(4, visible.size)
    }

    @Test
    fun `RatingDesc は選択日を無視して評価の高い順に全件を返す`() {
        val visible = ContentGrouping.visibleContents(contents, SortMode.RatingDesc, day1)
        assertEquals(listOf("b", "a", "c", "d"), visible.map { it.contentId })
    }

    @Test
    fun `RatingDesc の同評価は新しい日付が先になる`() {
        val same = listOf(
            content("old", "2026-05-01", rating = 3),
            content("new", "2026-05-02", rating = 3),
        )
        val visible = ContentGrouping.visibleContents(same, SortMode.RatingDesc, null)
        assertEquals(listOf("new", "old"), visible.map { it.contentId })
    }
}
