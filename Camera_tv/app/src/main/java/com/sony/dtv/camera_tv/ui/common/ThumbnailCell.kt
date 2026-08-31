package com.sony.dtv.camera_tv.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.sony.dtv.camera_tv.R
import com.sony.dtv.camera_tv.ui.theme.TvColors
import com.sony.dtv.camera_tv.ui.theme.TvDimens
import com.sony.dtv.camera_tv.ui.theme.TvShapes
import com.sony.dtv.camera_tv.ui.theme.TvTextSizes

/**
 * サムネイル1枚のセル。サムネイル帯と一覧グリッドで共有する。
 *
 * @param bytes サムネイル画像。未取得なら null（プレースホルダー表示）
 * @param index 0 始まりの位置。プレースホルダーとアクセシビリティ用
 * @param rating 評価（0 なら未表示）
 * @param isFocused リモコンのフォーカス位置
 * @param isCurrent 現在フルスクリーン表示中の写真
 * @param reqPx デコード時の要求解像度
 */
@Composable
fun ThumbnailCell(
    bytes: ByteArray?,
    index: Int,
    rating: Int,
    isFocused: Boolean,
    isCurrent: Boolean,
    reqPx: Int,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.04f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "thumbnailFocusScale",
    )

    Box(
        modifier = modifier
            // 拡大したセルが隣のセルの下に潜り込まないよう、フォーカス中は手前に描く
            .zIndex(if (isFocused) 1f else 0f)
            .scale(scale)
            .clip(TvShapes.Small)
            .background(TvColors.SurfaceVariant)
            // 枠はフォーカス中だけ。常時囲うと全部が選ばれているように見える
            .then(
                if (isFocused) {
                    Modifier.border(TvDimens.FocusBorder, TvColors.Focus, TvShapes.Small)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = rememberSampledBitmap(bytes, reqPx)
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(
                    R.string.gallery_thumbnail_content_description,
                    index + 1,
                ),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = "${index + 1}",
                color = TvColors.OnSurfaceDisabled,
                fontSize = TvTextSizes.Caption,
            )
        }
        RatingBadge(rating)
        // 現在表示中は枠ではなく小さな点で示す。枠だとフォーカスと見分けられない
        if (isCurrent && !isFocused) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(TvDimens.SpaceXs)
                    .size(8.dp)
                    .clip(TvShapes.Pill)
                    .background(TvColors.Accent),
            )
        }
    }
}

/** サムネイルに重ねる評価バッジ（★N）。評価なしのときは描画しない。 */
@Composable
private fun BoxScope.RatingBadge(rating: Int) {
    if (rating <= 0) return
    Row(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(TvDimens.SpaceXs)
            .clip(TvShapes.Pill)
            .background(TvColors.Scrim)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "★$rating",
            color = TvColors.Star,
            fontSize = TvTextSizes.Caption,
        )
    }
}
