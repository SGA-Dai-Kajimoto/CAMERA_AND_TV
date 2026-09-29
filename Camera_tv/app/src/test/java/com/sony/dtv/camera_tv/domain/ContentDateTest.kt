package com.sony.dtv.camera_tv.domain

import com.sony.dtv.camera_tv.data.model.Content
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ContentDateTest {

    private fun content(
        recorded: String? = null,
        recordedLocal: String? = null,
        created: String? = null,
    ) = Content(
        contentId = "c1",
        recordedDate = recorded,
        recordedDateLocalTime = recordedLocal,
        createdDate = created,
    )

    @Test
    fun `recordedDate が最優先で使われる`() {
        val c = content(
            recorded = "2026-05-01T10:00:00+09:00",
            recordedLocal = "2026-04-02T10:00:00",
            created = "2026-03-03T10:00:00Z",
        )
        assertEquals(LocalDate.of(2026, 5, 1), c.recordedLocalDate())
    }

    @Test
    fun `recordedDate が無ければ recordedDateLocalTime を使う`() {
        val c = content(
            recordedLocal = "2026-04-02T10:00:00",
            created = "2026-03-03T10:00:00Z",
        )
        assertEquals(LocalDate.of(2026, 4, 2), c.recordedLocalDate())
    }

    @Test
    fun `どちらも無ければ createdDate へフォールバックする`() {
        val c = content(created = "2026-03-03T10:00:00Z")
        assertEquals(LocalDate.of(2026, 3, 3), c.recordedLocalDate())
    }

    @Test
    fun `解析できない文字列は次の候補へフォールバックする`() {
        val c = content(
            recorded = "not-a-date",
            recordedLocal = "2026-04-02T10:00:00",
        )
        assertEquals(LocalDate.of(2026, 4, 2), c.recordedLocalDate())
    }

    @Test
    fun `オフセット無しの文字列は recordedDate として解析されない`() {
        // recorded_date はタイムゾーン付きが前提。ローカル形式は local_time 側で解釈する
        val c = content(recorded = "2026-05-01T10:00:00")
        assertNull(c.recordedLocalDate())
    }

    @Test
    fun `全て null なら日付不明`() {
        assertNull(content().recordedLocalDate())
    }

    @Test
    fun `日付不明のソートキーは最小値になる`() {
        assertEquals(Long.MIN_VALUE, content().dateSortKey())
        assertEquals(
            LocalDate.of(2026, 3, 3).toEpochDay(),
            content(created = "2026-03-03T10:00:00Z").dateSortKey(),
        )
    }
}
