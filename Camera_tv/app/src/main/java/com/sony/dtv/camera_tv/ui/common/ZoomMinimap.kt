package com.sony.dtv.camera_tv.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.sony.dtv.camera_tv.ui.theme.TvColors
import com.sony.dtv.camera_tv.ui.theme.TvShapes

/** ミニマップの幅。全体像のどこを見ているかが分かればよいので小さくてよい。 */
private val MINIMAP_WIDTH = 200.dp

/**
 * 全体像のどこを拡大しているかを示すミニマップ。
 * 選別・見比べのどちらの拡大でも同じものを出す。
 */
@Composable
fun ZoomMinimap(region: ZoomedRegion, modifier: Modifier = Modifier) {
    val aspect = region.sourceWidth.toFloat() / region.sourceHeight
    Box(
        modifier = modifier
            .width(MINIMAP_WIDTH)
            .aspectRatio(aspect)
            .clip(TvShapes.Small)
            .background(TvColors.Scrim),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(color = TvColors.OnSurfaceDisabled, style = Stroke(width = 2f))
            drawRect(
                color = TvColors.Star,
                topLeft = Offset(
                    region.visibleLeftRatio * size.width,
                    region.visibleTopRatio * size.height,
                ),
                size = Size(
                    region.visibleWidthRatio * size.width,
                    region.visibleHeightRatio * size.height,
                ),
                style = Stroke(width = 3f),
            )
        }
    }
}
