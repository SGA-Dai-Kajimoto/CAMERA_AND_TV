package com.sony.dtv.camera_tv.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.math.roundToInt

private const val TAG = "RegionBitmap"

/** 原寸画像から切り出す矩形。Android 非依存にして単体テストできるようにしている。 */
internal data class CropRegion(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * 倍率 [magnification] でビューポートを埋めるのに必要な、原寸画像の矩形を求める。
 *
 * 倍率は「画面ピクセル ÷ 元画像ピクセル」。1.0 が等倍で、2.0 なら元画像の半分の範囲を
 * 2 倍に引き伸ばして表示する。元画像がその範囲より小さい辺は、その辺いっぱいまでを使う。
 *
 * [centerX] / [centerY] は見たい位置。元画像全体を 0.0〜1.0 とした割合で、0.5 が中央。
 * 範囲外へははみ出さない。
 */
internal fun centerCropRegion(
    srcWidth: Int,
    srcHeight: Int,
    viewportWidth: Int,
    viewportHeight: Int,
    magnification: Float = 1f,
    centerX: Float = 0.5f,
    centerY: Float = 0.5f,
): CropRegion? {
    if (srcWidth <= 0 || srcHeight <= 0) return null
    if (viewportWidth <= 0 || viewportHeight <= 0) return null
    if (magnification <= 0f) return null

    val width = minOf((viewportWidth / magnification).roundToInt().coerceAtLeast(1), srcWidth)
    val height = minOf((viewportHeight / magnification).roundToInt().coerceAtLeast(1), srcHeight)
    val left = (centerX * srcWidth - width / 2f).roundToInt().coerceIn(0, srcWidth - width)
    val top = (centerY * srcHeight - height / 2f).roundToInt().coerceIn(0, srcHeight - height)
    return CropRegion(left, top, left + width, top + height)
}

/** [decodeCenterRegion] の結果。ミニマップ描画に元画像の寸法と切り出し位置が要る。 */
class ZoomedRegion internal constructor(
    val bitmap: Bitmap,
    val sourceWidth: Int,
    val sourceHeight: Int,
    internal val region: CropRegion,
) {
    /** 元画像全体に対する表示範囲。左上を (0,0)、右下を (1,1) とした割合。 */
    val visibleLeftRatio: Float get() = region.left.toFloat() / sourceWidth
    val visibleTopRatio: Float get() = region.top.toFloat() / sourceHeight
    val visibleWidthRatio: Float get() = region.width.toFloat() / sourceWidth
    val visibleHeightRatio: Float get() = region.height.toFloat() / sourceHeight
}

/**
 * 原寸バイト列の中央部分だけを切り出してデコードする。
 *
 * 全体を Bitmap 化すると GPU テクスチャ上限とヒープを超えるが、
 * 表示に必要な矩形だけなら全画面表示時と同じメモリで済む。
 * ピント確認は縮小すると意味が無いので、必ず原寸から切り出す。
 */
fun decodeCenterRegion(
    bytes: ByteArray,
    viewportWidth: Int,
    viewportHeight: Int,
    magnification: Float = 1f,
    centerX: Float = 0.5f,
    centerY: Float = 0.5f,
): ZoomedRegion? {
    if (viewportWidth <= 0 || viewportHeight <= 0) return null
    return runCatching {
        val decoder = BitmapRegionDecoder.newInstance(bytes, 0, bytes.size)
        try {
            val region = centerCropRegion(
                decoder.width,
                decoder.height,
                viewportWidth,
                viewportHeight,
                magnification,
                centerX,
                centerY,
            ) ?: return@runCatching null
            val bitmap = decoder.decodeRegion(
                Rect(region.left, region.top, region.right, region.bottom),
                BitmapFactory.Options().apply { inSampleSize = 1 },
            ) ?: return@runCatching null
            // 拡大で実際に解像度が上がっているかはここを見れば分かる。
            // out が viewport 以上で、src が out より十分大きければ原寸から切り出せている。
            Log.i(
                TAG,
                "zoom decode src=${decoder.width}x${decoder.height} " +
                    "region=${region.width}x${region.height} " +
                    "out=${bitmap.width}x${bitmap.height} " +
                    "viewport=${viewportWidth}x$viewportHeight mag=$magnification",
            )
            ZoomedRegion(bitmap, decoder.width, decoder.height, region)
        } finally {
            decoder.recycle()
        }
    }.getOrNull()
}

/** 同じ入力ならデコード結果を再利用する Composable ヘルパー。 */
@Composable
fun rememberZoomedRegion(
    bytes: ByteArray?,
    viewportWidth: Int,
    viewportHeight: Int,
    magnification: Float,
    centerX: Float = 0.5f,
    centerY: Float = 0.5f,
): ZoomedRegion? = remember(bytes, viewportWidth, viewportHeight, magnification, centerX, centerY) {
    bytes?.let {
        decodeCenterRegion(it, viewportWidth, viewportHeight, magnification, centerX, centerY)
    }
}
