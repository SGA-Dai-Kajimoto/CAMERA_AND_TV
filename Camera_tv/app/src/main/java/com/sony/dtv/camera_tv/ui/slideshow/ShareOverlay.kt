package com.sony.dtv.camera_tv.ui.slideshow

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sony.dtv.camera_tv.R
import com.sony.dtv.camera_tv.ui.common.KeyHint
import com.sony.dtv.camera_tv.ui.common.KeyInputSurface
import com.sony.dtv.camera_tv.ui.common.generateQrBitmap
import com.sony.dtv.camera_tv.ui.theme.TvColors
import com.sony.dtv.camera_tv.ui.theme.TvDimens
import com.sony.dtv.camera_tv.ui.theme.TvShapes
import com.sony.dtv.camera_tv.ui.theme.TvTextSizes

private const val QR_SIZE_PX = 600

/**
 * 共有オーバーレイ。
 * 事前署名済みダウンロード URL（約 10 分有効）を QR コード化して表示する。
 */
@Composable
internal fun ShareOverlay(
    url: String?,
    isLoading: Boolean,
    onClose: () -> Unit,
) {
    KeyInputSurface(
        modifier = Modifier.background(TvColors.ScrimStrong),
        onKey = { key ->
            when (key) {
                Key.Back, Key.Enter, Key.DirectionCenter -> { onClose(); true }
                else -> false
            }
        },
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TvDimens.SpaceMd),
        ) {
            Text(
                text = stringResource(R.string.share_title),
                color = TvColors.OnSurface,
                fontSize = TvTextSizes.Title,
                fontWeight = FontWeight.Bold,
            )

            val qrBitmap = remember(url) { url?.let { generateQrBitmap(it, QR_SIZE_PX) } }
            Box(
                modifier = Modifier
                    .size(340.dp)
                    .clip(TvShapes.Medium)
                    .background(Color.White)
                    .padding(TvDimens.SpaceMd),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isLoading -> CircularProgressIndicator(color = TvColors.Background)
                    qrBitmap != null -> Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.share_qr_content_description),
                        modifier = Modifier.fillMaxSize(),
                    )

                    else -> Text(
                        text = stringResource(R.string.share_qr_failed),
                        color = TvColors.Background,
                        fontSize = TvTextSizes.Body,
                    )
                }
            }

            Text(
                text = stringResource(R.string.share_expiry),
                color = TvColors.OnSurfaceMuted,
                fontSize = TvTextSizes.Body,
            )
            KeyHint(
                text = stringResource(R.string.share_hint),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}
