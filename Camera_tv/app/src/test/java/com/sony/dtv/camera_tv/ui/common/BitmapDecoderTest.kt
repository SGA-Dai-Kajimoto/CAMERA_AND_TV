package com.sony.dtv.camera_tv.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [calculateInSampleSize] の単体テスト。
 * inSampleSize は 2 冪しか取れないため、要求解像度を割り込まないことを検証する。
 */
class BitmapDecoderTest {

    @Test
    fun `要求より小さくならない最大の2冪を選ぶ`() {
        // α7 IV (33MP, 長辺7008) を 2160px 要求。1/4 だと 1752px で不足するので 1/2。
        assertEquals(2, calculateInSampleSize(srcMaxDim = 7008, reqPx = 2160))
    }

    @Test
    fun `割り切れる場合はちょうどの2冪を選ぶ`() {
        // α1 (50MP, 長辺8640) を 2160px 要求。1/4 でちょうど 2160px。
        assertEquals(4, calculateInSampleSize(srcMaxDim = 8640, reqPx = 2160))
    }

    @Test
    fun `4K要求では控えめな縮小率になる`() {
        assertEquals(1, calculateInSampleSize(srcMaxDim = 7008, reqPx = 3840))
        assertEquals(2, calculateInSampleSize(srcMaxDim = 8640, reqPx = 3840))
    }

    @Test
    fun `元画像が要求より小さければ縮小しない`() {
        assertEquals(1, calculateInSampleSize(srcMaxDim = 1500, reqPx = 2160))
    }

    @Test
    fun `元画像と要求が同じなら縮小しない`() {
        assertEquals(1, calculateInSampleSize(srcMaxDim = 2160, reqPx = 2160))
    }

    @Test
    fun `サムネイル要求では大きく縮小する`() {
        // 長辺7008 を 400px 要求。1/16 で 438px、1/32 だと 219px で不足。
        assertEquals(16, calculateInSampleSize(srcMaxDim = 7008, reqPx = 400))
    }

    @Test
    fun `寸法が取得できない場合は縮小率1を返す`() {
        assertEquals(1, calculateInSampleSize(srcMaxDim = -1, reqPx = 2160))
        assertEquals(1, calculateInSampleSize(srcMaxDim = 7008, reqPx = 0))
    }
}
