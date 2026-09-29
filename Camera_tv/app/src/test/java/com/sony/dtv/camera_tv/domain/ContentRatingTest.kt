package com.sony.dtv.camera_tv.domain

import com.sony.dtv.camera_tv.data.model.Content
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentRatingTest {

    private fun content(vararg tags: String) =
        Content(contentId = "c1", tags = tags.toList())

    // ---- parseTag ---- //

    @Test
    fun `parseTag は rating 接頭辞の 1 から 5 を受け付ける`() {
        assertEquals(1, ContentRating.parseTag("rating:1"))
        assertEquals(5, ContentRating.parseTag("rating:5"))
    }

    @Test
    fun `parseTag は範囲外や不正な値を null にする`() {
        assertNull(ContentRating.parseTag("rating:0"))
        assertNull(ContentRating.parseTag("rating:6"))
        assertNull(ContentRating.parseTag("rating:-1"))
        assertNull(ContentRating.parseTag("rating:abc"))
        assertNull(ContentRating.parseTag("rating:"))
    }

    @Test
    fun `parseTag は評価以外のタグを null にする`() {
        assertNull(ContentRating.parseTag("favorite:1"))
        assertNull(ContentRating.parseTag("family"))
    }

    // ---- clamp ---- //

    @Test
    fun `clamp は 0 から 5 に丸める`() {
        assertEquals(0, ContentRating.clamp(-3))
        assertEquals(3, ContentRating.clamp(3))
        assertEquals(5, ContentRating.clamp(99))
    }

    // ---- applyTo ---- //

    @Test
    fun `applyTo は評価以外のタグを保持する`() {
        val result = ContentRating.applyTo(listOf("family", "rating:2", "trip"), 4)
        assertTrue(result.containsAll(listOf("family", "trip")))
        assertTrue(result.contains("rating:4"))
        assertEquals(1, result.count { it.startsWith(ContentRating.TAG_PREFIX) })
    }

    @Test
    fun `applyTo に 0 を渡すと評価タグが取り除かれる`() {
        val result = ContentRating.applyTo(listOf("family", "rating:2"), ContentRating.NONE)
        assertEquals(listOf("family"), result)
    }

    @Test
    fun `applyTo は tags が null でも動く`() {
        assertEquals(listOf("rating:3"), ContentRating.applyTo(null, 3))
        assertEquals(listOf("rating:0"), ContentRating.applyTo(null, 0))
    }

    @Test
    fun `applyTo は結果を空にしない`() {
        // POST :setTags は空配列を 400 で拒否する（2026-08-25 実測）
        assertEquals(listOf("rating:0"), ContentRating.applyTo(listOf("rating:3"), ContentRating.NONE))
        assertEquals(listOf("rating:0"), ContentRating.applyTo(emptyList(), ContentRating.NONE))
    }

    @Test
    fun `rating 0 は評価なしとして読まれる`() {
        assertEquals(null, ContentRating.parseTag("rating:0"))
    }

    @Test
    fun `applyTo は範囲外の値を丸めてから付与する`() {
        assertEquals(listOf("rating:5"), ContentRating.applyTo(emptyList(), 9))
    }

    // ---- Content 拡張 ---- //

    @Test
    fun `ratingValue は評価タグが無ければ 0`() {
        assertEquals(ContentRating.NONE, content("family").ratingValue())
        assertEquals(ContentRating.NONE, Content(contentId = "c1").ratingValue())
    }

    @Test
    fun `ratingValue は複数ある場合に最大値を返す`() {
        assertEquals(4, content("rating:2", "rating:4").ratingValue())
    }

    @Test
    fun `tagsWithRating は既存タグを引き継ぐ`() {
        val result = content("family", "rating:1").tagsWithRating(3)
        assertEquals(listOf("family", "rating:3"), result)
    }
}
