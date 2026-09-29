package com.sony.dtv.camera_tv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * TV UI 全体で使う配色。
 * 個々の Composable で `Color(0xFF...)` を直書きしないよう、ここへ集約する。
 */
object TvColors {
    val Background = Color(0xFF0B0B0F)
    val Surface = Color(0xFF17171E)
    val SurfaceVariant = Color(0xFF242430)

    val Accent = Color(0xFF4C8DFF)
    val AccentDim = Color(0xFF4C8DFF).copy(alpha = 0.35f)
    val AccentFaint = Color(0xFF4C8DFF).copy(alpha = 0.16f)

    val OnSurface = Color(0xFFF3F3F6)
    val OnSurfaceMuted = Color(0xFFA6A6B3)
    val OnSurfaceDisabled = Color(0xFF5C5C68)

    val Star = Color(0xFFFFC64B)
    val Danger = Color(0xFFE5484D)

    /** 選別の「採用」。見送りは削除ではないので [Danger] を使わない。 */
    val Pick = Color(0xFF3DDC84)

    /** オーバーレイ背景（写真の上に敷く暗幕）。 */
    val Scrim = Color(0xB3000000)
    val ScrimStrong = Color(0xE6000000)

    /** キー説明全体の下地。写真の明るさに左右されず読める濃さにする。 */
    val HintSurface = Color(0x99000000)

    /** キー名の下地。説明より一段濃くしてキーキャップに見せる。 */
    val HintKeySurface = Color(0xE6000000)

    /** フォーカスリング。TV では白の縁取りが最も視認性が高い。 */
    val Focus = Color.White
}

/** 余白・サイズ。10-foot UI 向けにやや大きめ。 */
object TvDimens {
    val SpaceXs = 4.dp
    val SpaceSm = 8.dp
    val SpaceMd = 16.dp
    val SpaceLg = 24.dp
    val SpaceXl = 40.dp

    /** 画面端のセーフエリア（オーバースキャン対策）。 */
    val ScreenPadding = 48.dp

    val ThumbnailStripSize = 104.dp
    val MenuButtonHeight = 52.dp

    val FocusBorder = 3.dp
    val CurrentBorder = 2.dp

    /**
     * 全画面表示時のデコード解像度の上限（4K パネルの長辺）。
     * 実際の要求値は描画面の解像度から決めるため、ここはメモリと
     * GPU テクスチャ上限を守るためのキャップとして働く。
     */
    const val FullscreenMaxPx = 3840
}

/** 角丸。 */
object TvShapes {
    val Small = RoundedCornerShape(6.dp)
    val Medium = RoundedCornerShape(12.dp)
    val Large = RoundedCornerShape(20.dp)
    val Pill = RoundedCornerShape(percent = 50)
}

/** 文字サイズ。 */
object TvTextSizes {
    val Caption = 13.sp
    val Body = 16.sp
    val Label = 18.sp
    val Title = 24.sp
    val Headline = 32.sp
    val Display = 44.sp
}
