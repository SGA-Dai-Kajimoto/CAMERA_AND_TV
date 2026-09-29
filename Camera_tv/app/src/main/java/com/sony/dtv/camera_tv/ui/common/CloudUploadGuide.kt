package com.sony.dtv.camera_tv.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sony.dtv.camera_tv.R
import com.sony.dtv.camera_tv.ui.theme.TvColors
import com.sony.dtv.camera_tv.ui.theme.TvDimens
import com.sony.dtv.camera_tv.ui.theme.TvShapes
import com.sony.dtv.camera_tv.ui.theme.TvTextSizes

/** カメラから直接クラウドへアップロードする設定手順（ソニー サポート、動画つき）。 */
const val CLOUD_UPLOAD_HELP_URL =
    "https://support.sony.jp/electronics/support/articles/00353042"

/**
 * 上のページに埋め込まれている解説動画の YouTube ID。
 * 2026-09-14 にページの HTML を取得して実測（`<iframe src="https://www.youtube.com/embed/...">`）。
 */
const val CLOUD_UPLOAD_VIDEO_ID = "4O1MxhklXNA"

private const val QR_SIZE_PX = 480

/** スマートフォンから数十 cm 離れて読める大きさ。 */
private val QR_SIZE = 160.dp

/** カードの中に収めるときの大きさ。これより小さくすると TV での読み取りが不安定になる。 */
private val COMPACT_QR_SIZE = 96.dp

/**
 * 写真をクラウドへ取り込む設定への誘導。
 *
 * 解説動画は TV の YouTube アプリへ渡して見てもらう（[YouTubeLauncher]）。
 * ここの QR は手順を手元に置いておくためのもので、オフラインで生成する。
 *
 * @param compact カードの中に添えるときの小型版。URL 行を省き、高さを押さえる
 */
@Composable
fun CloudUploadGuide(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Row(
        modifier = modifier
            .clip(TvShapes.Medium)
            .background(TvColors.SurfaceVariant)
            .padding(if (compact) TvDimens.SpaceSm else TvDimens.SpaceMd),
        horizontalArrangement = Arrangement.spacedBy(TvDimens.SpaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QrPanel(url = CLOUD_UPLOAD_HELP_URL, size = if (compact) COMPACT_QR_SIZE else QR_SIZE)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(TvDimens.SpaceXs),
        ) {
            Text(
                text = stringResource(R.string.cloud_upload_title),
                color = TvColors.OnSurface,
                fontSize = TvTextSizes.Label,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.cloud_upload_body),
                color = TvColors.OnSurfaceMuted,
                fontSize = if (compact) TvTextSizes.Caption else TvTextSizes.Body,
            )
            if (!compact) {
                // QR を読めない環境でも辿れるよう URL も出す
                Text(
                    text = CLOUD_UPLOAD_HELP_URL,
                    color = TvColors.OnSurfaceDisabled,
                    fontSize = TvTextSizes.Caption,
                )
            }
        }
    }
}

/** QR は白背景でないと読み取り精度が落ちるため、白いパネルに載せる。 */
@Composable
private fun QrPanel(url: String, size: Dp) {
    val bitmap = remember(url) { generateQrBitmap(url, QR_SIZE_PX) }
    Box(
        modifier = Modifier
            .clip(TvShapes.Small)
            .background(Color.White)
            .padding(TvDimens.SpaceSm),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.cloud_upload_qr_description),
                modifier = Modifier.size(size),
            )
        } else {
            Text(
                text = stringResource(R.string.cloud_upload_qr_failed),
                color = Color.Black,
                fontSize = TvTextSizes.Caption,
                modifier = Modifier.size(size).padding(TvDimens.SpaceSm),
            )
        }
    }
}
