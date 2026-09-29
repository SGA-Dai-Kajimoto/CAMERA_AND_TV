package com.sony.dtv.camera_tv.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.sony.dtv.camera_tv.ui.theme.TvDimens

/**
 * 要求サイズ [reqPx] を **下回らない** 最小の 2 冪ダウンサンプル率を返す。
 *
 * `inSampleSize` は 2 冪しか取れないため、要求を割り込むまで縮めると解像度が大幅に不足する
 * （例: 長辺 7008px を 2160px 要求で 1/4 すると 1752px しか残らない）。
 * ここでは要求以上を維持し、端数は [decodeSampledBitmap] の密度スケールで詰める。
 */
internal fun calculateInSampleSize(srcMaxDim: Int, reqPx: Int): Int {
    if (srcMaxDim <= 0 || reqPx <= 0) return 1
    var sample = 1
    while (srcMaxDim / (sample * 2) >= reqPx) sample *= 2
    return sample
}

/**
 * 長辺が [reqPx] になるようダウンサンプルしてデコードする。
 * 原寸のまま Bitmap 化すると GPU のテクスチャ上限とヒープを超えるため必須。
 * 元画像が [reqPx] より小さい場合は拡大しない。デコードできない場合は null。
 */
fun decodeSampledBitmap(bytes: ByteArray, reqPx: Int): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

    val srcMaxDim = maxOf(bounds.outWidth, bounds.outHeight)
    val sample = calculateInSampleSize(srcMaxDim, reqPx)
    val sampledMaxDim = srcMaxDim / sample

    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        // 2 冪で削りきれない残りを密度スケールで詰める（BitmapFactory 側で平滑化される）
        if (reqPx > 0 && sampledMaxDim > reqPx) {
            inScaled = true
            inDensity = sampledMaxDim
            inTargetDensity = reqPx
        }
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}.getOrNull()

/** 同じバイト列・同じ要求サイズならデコード結果を再利用する Composable ヘルパー。 */
@Composable
fun rememberSampledBitmap(bytes: ByteArray?, reqPx: Int): Bitmap? =
    remember(bytes, reqPx) { bytes?.let { decodeSampledBitmap(it, reqPx) } }

/**
 * 全画面表示用の要求ピクセル数。
 *
 * 固定値ではなく実際の描画面の解像度を使う。Android TV は機種によって UI レイヤーが
 * 1080p でレンダリングされ 4K へアップスケールされるため、決め打ちにすると
 * 過剰デコード（無駄なメモリ）か過少デコード（画質劣化）のどちらかになる。
 */
@Composable
fun rememberFullscreenReqPx(): Int {
    val context = LocalContext.current
    return remember(context) {
        val metrics = context.resources.displayMetrics
        maxOf(metrics.widthPixels, metrics.heightPixels)
            .coerceIn(1, TvDimens.FullscreenMaxPx)
    }
}
