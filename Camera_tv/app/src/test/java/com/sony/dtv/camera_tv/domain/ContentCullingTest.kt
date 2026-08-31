package com.sony.dtv.camera_tv.domain

import com.sony.dtv.camera_tv.data.model.Content
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ContentCullingTest {

    private fun content(id: String, date: String, rating: Int? = null) = Content(
        contentId = id,
        recordedDate = "${date}T12:00:00+09:00",
        tags = rating?.let { listOf(ContentRating.toTag(it)) },
    )

    @Test
    fun `評価が付いていれば判定済みとみなす`() {
        assertTrue(content("a", "2026-05-01", rating = ContentCulling.PICK).isJudged())
        assertTrue(content("b", "2026-05-01", rating = ContentCulling.SKIP).isJudged())
    }

    @Test
    fun `評価が無ければ未判定`() {
        assertFalse(content("a", "2026-05-01").isJudged())
        assertFalse(content("b", "2026-05-01", rating = ContentRating.NONE).isJudged())
    }

    @Test
    fun `キューは判定済みも含めて全枚を並べる`() {
        // 日付で見た枚数と選別の枚数が合わないと混乱する
        val contents = listOf(
            content("judged", "2026-05-01", rating = ContentCulling.PICK),
            content("unjudged", "2026-05-01"),
            content("skipped", "2026-05-01", rating = ContentCulling.SKIP),
        )

        assertEquals(3, ContentCulling.queue(contents).size)
    }

    @Test
    fun `開始位置は最初の未判定`() {
        val queue = listOf(
            content("judged", "2026-05-03", rating = ContentCulling.PICK),
            content("unjudged", "2026-05-02"),
            content("other", "2026-05-01"),
        )

        assertEquals(1, ContentCulling.firstUnjudgedIndex(queue))
    }

    @Test
    fun `全て判定済みなら開始位置は先頭`() {
        val queue = listOf(
            content("a", "2026-05-01", rating = ContentCulling.PICK),
            content("b", "2026-05-02", rating = ContentCulling.SKIP),
        )

        assertEquals(0, ContentCulling.firstUnjudgedIndex(queue))
    }

    @Test
    fun `キューは新しい撮影日が先に来る`() {
        val contents = listOf(
            content("old", "2026-05-01"),
            content("new", "2026-05-03"),
            content("mid", "2026-05-02"),
        )

        assertEquals(listOf("new", "mid", "old"), ContentCulling.queue(contents).map { it.contentId })
    }

    @Test
    fun `日付不明の写真は最後に回る`() {
        val contents = listOf(
            Content(contentId = "unknown"),
            content("dated", "2026-05-01"),
        )

        assertEquals(listOf("dated", "unknown"), ContentCulling.queue(contents).map { it.contentId })
    }

    @Test
    fun `全て判定済みでもキューには残る`() {
        val contents = listOf(
            content("a", "2026-05-01", rating = ContentCulling.PICK),
            content("b", "2026-05-02", rating = ContentCulling.SKIP),
        )

        assertEquals(2, ContentCulling.queue(contents).size)
    }

    @Test
    fun `採用と見送りをそれぞれ取り出せる`() {
        val contents = listOf(
            content("picked", "2026-05-01", rating = ContentCulling.PICK),
            content("skipped", "2026-05-02", rating = ContentCulling.SKIP),
            content("unjudged", "2026-05-03"),
            content("star5", "2026-05-04", rating = 5),
        )

        // ★5 は採用側に含める。判定済みなのにどちらにも出ないと未判定に戻せなくなる
        assertEquals(listOf("star5", "picked"), ContentCulling.picked(contents).map { it.contentId })
        assertEquals(listOf("skipped"), ContentCulling.skipped(contents).map { it.contentId })
    }

    @Test
    fun `見直しの一覧も新しい撮影日が先に来る`() {
        val contents = listOf(
            content("old", "2026-05-01", rating = ContentCulling.PICK),
            content("new", "2026-05-03", rating = ContentCulling.PICK),
        )

        assertEquals(listOf("new", "old"), ContentCulling.picked(contents).map { it.contentId })
    }

    @Test
    fun `判定済みは必ず採用か見送りのどちらかに現れる`() {
        // 評価ダイアログで付けた ★2 や ★4 も判定済み扱いになる。
        // どちらのタブにも出ないと未判定に戻せず、選別キューから永久に消える
        val contents = (ContentRating.MIN..ContentRating.MAX).map { rating ->
            content("r$rating", "2026-05-01", rating = rating)
        }

        val picked = ContentCulling.picked(contents).map { it.contentId }
        val skipped = ContentCulling.skipped(contents).map { it.contentId }

        assertEquals(listOf("r1", "r2"), skipped.sorted())
        assertEquals(listOf("r3", "r4", "r5"), picked.sorted())
        assertEquals(contents.size, picked.size + skipped.size)
    }

    @Test
    fun `日付を指定するとその日の写真だけになる`() {
        val contents = listOf(
            content("d1a", "2026-05-01"),
            content("d2a", "2026-05-02"),
            content("d1b", "2026-05-01"),
            content("d1judged", "2026-05-01", rating = ContentCulling.PICK),
        )

        val queue = ContentCulling.queue(contents, LocalDate.of(2026, 5, 1))

        // 判定済みも含めてその日の 3 枚
        assertEquals(setOf("d1a", "d1b", "d1judged"), queue.map { it.contentId }.toSet())
    }

    @Test
    fun `日付を指定しなければ全期間が対象になる`() {
        val contents = listOf(
            content("d1", "2026-05-01"),
            content("d2", "2026-05-02"),
        )

        assertEquals(2, ContentCulling.queue(contents).size)
    }

    @Test
    fun `未判定が残っている日を新しい順で返す`() {
        val contents = listOf(
            content("a", "2026-05-01"),
            content("b", "2026-05-03"),
            content("c", "2026-05-03"),
            content("judged", "2026-05-05", rating = ContentCulling.SKIP),
        )

        assertEquals(
            listOf(LocalDate.of(2026, 5, 3), LocalDate.of(2026, 5, 1)),
            ContentCulling.unjudgedDates(contents),
        )
    }

    @Test
    fun `見送りは評価タグを残すので削除とは区別される`() {
        val judged = content("a", "2026-05-01").let {
            it.copy(tags = it.tagsWithRating(ContentCulling.SKIP))
        }

        assertTrue(judged.isJudged())
        assertEquals(ContentCulling.SKIP, judged.ratingValue())
    }
}
