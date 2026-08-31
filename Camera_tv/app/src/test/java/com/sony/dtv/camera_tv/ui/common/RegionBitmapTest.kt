package com.sony.dtv.camera_tv.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [centerCropRegion] の単体テスト。
 * 等倍表示ではビューポート相当の矩形をちょうど切り出せることが前提になる。
 */
class RegionBitmapTest {

    @Test
    fun `原寸から中央のビューポート分を切り出す`() {
        // α7 IV 相当 7008x4672 を 3840x2160 のビューポートで等倍表示
        val region = centerCropRegion(7008, 4672, 3840, 2160)!!

        assertEquals(3840, region.width)
        assertEquals(2160, region.height)
        assertEquals((7008 - 3840) / 2, region.left)
        assertEquals((4672 - 2160) / 2, region.top)
    }

    @Test
    fun `元画像がビューポートより小さい辺はその辺いっぱいを使う`() {
        val region = centerCropRegion(1600, 1200, 3840, 2160)!!

        assertEquals(1600, region.width)
        assertEquals(1200, region.height)
        assertEquals(0, region.left)
        assertEquals(0, region.top)
    }

    @Test
    fun `縦方向だけ収まる場合は高さだけ切り詰めない`() {
        val region = centerCropRegion(7008, 1800, 3840, 2160)!!

        assertEquals(3840, region.width)
        assertEquals(1800, region.height)
        assertEquals(0, region.top)
    }

    @Test
    fun `倍率を上げると切り出す範囲が狭くなる`() {
        val x2 = centerCropRegion(7008, 4672, 3840, 2160, magnification = 2f)!!
        assertEquals(1920, x2.width)
        assertEquals(1080, x2.height)

        val x4 = centerCropRegion(7008, 4672, 3840, 2160, magnification = 4f)!!
        assertEquals(960, x4.width)
        assertEquals(540, x4.height)
    }

    @Test
    fun `倍率を変えても中央を見る`() {
        val region = centerCropRegion(7008, 4672, 3840, 2160, magnification = 4f)!!

        assertEquals((7008 - 960) / 2, region.left)
        assertEquals((4672 - 540) / 2, region.top)
    }

    @Test
    fun `見る位置を変えると切り出し位置が動く`() {
        val left = centerCropRegion(7008, 4672, 3840, 2160, magnification = 4f, centerX = 0.1f)!!
        val right = centerCropRegion(7008, 4672, 3840, 2160, magnification = 4f, centerX = 0.9f)!!

        assertTrue(left.left < right.left)
        assertEquals(left.width, right.width)
    }

    @Test
    fun `見る位置は元画像の外へはみ出さない`() {
        val topLeft = centerCropRegion(7008, 4672, 3840, 2160, magnification = 4f, centerX = 0f, centerY = 0f)!!
        assertEquals(0, topLeft.left)
        assertEquals(0, topLeft.top)

        val bottomRight =
            centerCropRegion(7008, 4672, 3840, 2160, magnification = 4f, centerX = 1f, centerY = 1f)!!
        assertEquals(7008, bottomRight.right)
        assertEquals(4672, bottomRight.bottom)
    }

    @Test
    fun `寸法が不正なら切り出せない`() {
        assertNull(centerCropRegion(0, 4672, 3840, 2160))
        assertNull(centerCropRegion(7008, 0, 3840, 2160))
        assertNull(centerCropRegion(7008, 4672, 0, 2160))
        assertNull(centerCropRegion(7008, 4672, 3840, 0))
        assertNull(centerCropRegion(7008, 4672, 3840, 2160, magnification = 0f))
    }
}
